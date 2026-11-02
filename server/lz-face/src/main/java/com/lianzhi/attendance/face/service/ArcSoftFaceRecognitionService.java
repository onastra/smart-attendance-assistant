package com.lianzhi.attendance.face.service;

import com.arcsoft.face.EngineConfiguration;
import com.arcsoft.face.FaceEngine;
import com.arcsoft.face.FaceFeature;
import com.arcsoft.face.FaceInfo;
import com.arcsoft.face.FaceSimilar;
import com.arcsoft.face.FunctionConfiguration;
import com.arcsoft.face.Rect;
import com.arcsoft.face.enums.DetectMode;
import com.arcsoft.face.enums.DetectOrient;
import com.arcsoft.face.enums.ErrorInfo;
import com.arcsoft.face.toolkit.ImageFactory;
import com.arcsoft.face.toolkit.ImageInfo;
import com.lianzhi.attendance.common.exception.BusinessException;
import com.lianzhi.attendance.common.result.ResultCode;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 虹软 ArcFace 真实人脸识别实现（V3.0 / Windows x64 / Java）。
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li><strong>懒加载 + 在线激活</strong>：引擎首次调用时才创建并激活。离线或授权异常时
 *       应用照常启动，接口返回明确的不可用原因（不吞异常、不假装能用）。</li>
 *   <li><strong>失败短时缓存</strong>：激活失败后 60 秒内不重复重试，避免每次请求都卡在网络超时；
 *       冷却期过后自动重试，网络恢复无需重启。</li>
 *   <li><strong>只认单张人脸</strong>：检测到 0 张或多张直接拒绝——多人画面下"随便挑一张脸"
 *       可能比中别人，这是身份核验里最危险的行为。</li>
 *   <li><strong>人脸质量门槛</strong>：人脸框太小（距离远、画面糊）时拒绝并要求重拍，
 *       否则会把低质量特征存进库，之后永远核验不过。</li>
 *   <li><strong>特征只存向量</strong>：1032 字节特征 Base64 后存库，不落原始人脸图片。</li>
 * </ul>
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "lz.face", name = "provider", havingValue = "arcsoft")
public class ArcSoftFaceRecognitionService implements FaceRecognitionService {

    /** 激活失败的冷却时间（毫秒），期间不重复尝试网络激活 */
    private static final long RETRY_COOLDOWN_MS = 60_000L;

    @Value("${lz.face.app-id:}")
    private String appId;

    @Value("${lz.face.sdk-key:}")
    private String sdkKey;

    /** 原生库目录：需包含 libarcsoft_face.dll / libarcsoft_face_engine.dll / libarcsoft_face_engine_jni.dll */
    @Value("${lz.face.sdk-lib-path:}")
    private String sdkLibPath;

    /** 1:1 比对通过阈值（虹软官方建议 0.8，实测可微调） */
    @Value("${lz.face.threshold:0.8}")
    private double threshold;

    /** 人脸框短边最小像素，低于此值视为太小/太远 */
    @Value("${lz.face.min-face-pixels:80}")
    private int minFacePixels;

    /** 人脸框面积占画面最小比例（0.01 = 1%），兜底防止极端比例图片 */
    @Value("${lz.face.min-face-ratio:0.01}")
    private double minFaceRatio;

    private volatile FaceEngine engine;
    private volatile String initError;
    private volatile long lastAttemptAt;

    // ---------------- FaceRecognitionService ----------------

    @Override
    public String provider() {
        return "arcsoft";
    }

    @Override
    public boolean mock() {
        return false;
    }

    @Override
    public boolean available() {
        return engine() != null;
    }

    @Override
    public String unavailableReason() {
        return engine() != null ? null : initError;
    }

    @Override
    public double threshold() {
        return threshold;
    }

    @Override
    public String extractFeature(byte[] imageBytes) {
        FaceEngine fe = requireEngine();
        ImageInfo imageInfo = decode(imageBytes);

        List<FaceInfo> faces = new ArrayList<>();
        int code = fe.detectFaces(imageInfo.getImageData(), imageInfo.getWidth(), imageInfo.getHeight(),
                imageInfo.getImageFormat(), faces);
        checkCode(code, "人脸检测");

        if (faces.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "未检测到人脸，请正对镜头、保证光线充足后重拍");
        }
        if (faces.size() > 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "画面中检测到 " + faces.size() + " 张人脸，请确保镜头内只有本人");
        }

        FaceInfo face = faces.get(0);
        assertQuality(face, imageInfo.getWidth(), imageInfo.getHeight());

        FaceFeature feature = new FaceFeature();
        code = fe.extractFaceFeature(imageInfo.getImageData(), imageInfo.getWidth(), imageInfo.getHeight(),
                imageInfo.getImageFormat(), face, feature);
        checkCode(code, "特征提取");

        byte[] data = feature.getFeatureData();
        if (data == null || data.length == 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "人脸特征提取失败，请重拍");
        }
        Rect rect = face.getRect();
        log.info("[FACE] 特征提取成功，维度={} 字节，人脸框={}x{}", data.length,
                rect.getRight() - rect.getLeft(), rect.getBottom() - rect.getTop());
        return Base64.getEncoder().encodeToString(data);
    }

    @Override
    public double similarity(String featureA, String featureB) {
        FaceEngine fe = requireEngine();
        FaceFeature a = new FaceFeature();
        a.setFeatureData(Base64.getDecoder().decode(featureA));
        FaceFeature b = new FaceFeature();
        b.setFeatureData(Base64.getDecoder().decode(featureB));

        FaceSimilar similar = new FaceSimilar();
        int code = fe.compareFaceFeature(a, b, similar);
        checkCode(code, "特征比对");
        return similar.getScore();
    }

    // ---------------- 引擎生命周期 ----------------

    /**
     * 懒加载引擎：授权参数校验 → 在线激活 → 初始化功能。
     * 任一环节失败都记录原因并返回 null（调用方据此给出可读提示）。
     */
    private FaceEngine engine() {
        FaceEngine local = engine;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (engine != null) {
                return engine;
            }
            if (initError != null && System.currentTimeMillis() - lastAttemptAt < RETRY_COOLDOWN_MS) {
                return null;
            }
            lastAttemptAt = System.currentTimeMillis();
            try {
                engine = createEngine();
                initError = null;
            } catch (Exception e) {
                initError = e.getMessage();
                log.error("[FACE] 虹软引擎不可用：{}", initError);
            }
            return engine;
        }
    }

    private FaceEngine createEngine() {
        if (isBlank(appId) || isBlank(sdkKey)) {
            throw new IllegalStateException("缺少授权参数 lz.face.app-id / lz.face.sdk-key");
        }
        if (isBlank(sdkLibPath)) {
            throw new IllegalStateException("缺少原生库目录 lz.face.sdk-lib-path（应指向含 WIN64 dll 的目录）");
        }

        FaceEngine fe = new FaceEngine(sdkLibPath.replace('\\', '/'));

        int code = fe.activeOnline(appId, sdkKey);
        if (code != ErrorInfo.MOK.getValue() && code != ErrorInfo.MERR_ASF_ALREADY_ACTIVATED.getValue()) {
            throw new IllegalStateException("在线激活失败，错误码=" + code
                    + "（检查 APP_ID/SDK_KEY 是否匹配、网络是否可达、终端激活数是否已用尽）");
        }

        EngineConfiguration config = new EngineConfiguration();
        // 考勤是"人脸对准镜头"的静态场景，图片模式精度更高；将来做实时视频流再切 VIDEO 模式
        config.setDetectMode(DetectMode.ASF_DETECT_MODE_IMAGE);
        config.setDetectFaceOrientPriority(DetectOrient.ASF_OP_ALL_OUT);
        config.setDetectFaceMaxNum(5);
        config.setDetectFaceScaleVal(16);

        FunctionConfiguration function = new FunctionConfiguration();
        function.setSupportFaceDetect(true);
        function.setSupportFaceRecognition(true);
        // 活体检测需单独授权「单目 RGB 活体」SDK；未授权时开启会导致初始化失败，故默认关闭
        function.setSupportLiveness(false);
        function.setSupportIRLiveness(false);
        config.setFunctionConfiguration(function);

        code = fe.init(config);
        if (code != ErrorInfo.MOK.getValue()) {
            throw new IllegalStateException("引擎初始化失败，错误码=" + code);
        }
        log.info("[FACE] 虹软引擎激活并初始化成功，provider=arcsoft，阈值={}", threshold);
        return fe;
    }

    @PreDestroy
    public void destroy() {
        FaceEngine fe = engine;
        if (fe != null) {
            fe.unInit();
            log.info("[FACE] 虹软引擎已释放");
        }
    }

    // ---------------- 内部工具 ----------------

    private FaceEngine requireEngine() {
        FaceEngine fe = engine();
        if (fe == null) {
            throw new BusinessException(ResultCode.DEPENDENCY_NOT_CONFIGURED,
                    "人脸识别当前不可用：" + initError);
        }
        return fe;
    }

    private static ImageInfo decode(byte[] imageBytes) {
        try {
            ImageInfo info = ImageFactory.getRGBData(imageBytes);
            if (info == null || info.getImageData() == null) {
                throw new IllegalArgumentException("图片解码结果为空");
            }
            return info;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "无法解析人脸图片，请上传 JPG/PNG 格式照片");
        }
    }

    /** 质量门槛：人脸太小说明距离远/图糊，特征不可靠，宁可让用户重拍也不存废特征 */
    private void assertQuality(FaceInfo face, int imageWidth, int imageHeight) {
        Rect rect = face.getRect();
        int w = rect.getRight() - rect.getLeft();
        int h = rect.getBottom() - rect.getTop();
        int shortSide = Math.min(w, h);
        if (shortSide < minFacePixels) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "人脸太小（" + shortSide + "px，需≥" + minFacePixels + "px），请靠近镜头重拍");
        }
        double ratio = (double) w * h / ((double) imageWidth * imageHeight);
        if (ratio < minFaceRatio) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "人脸占画面比例过小（" + String.format("%.2f%%", ratio * 100) + "），请靠近镜头重拍");
        }
    }

    private static void checkCode(int code, String action) {
        if (code != ErrorInfo.MOK.getValue()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, action + "失败，错误码=" + code);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
