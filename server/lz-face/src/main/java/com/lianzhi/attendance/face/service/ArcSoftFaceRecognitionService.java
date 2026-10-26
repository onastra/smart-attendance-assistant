package com.lianzhi.attendance.face.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 虹软 SDK 接入点（当前无授权，装配后处于"不可用"状态）。
 *
 * <p><strong>为什么这样写</strong>：虹软 SDK 需要授权文件与 AppId，本项目当前不可得，
 * 依赖里也没有 SDK jar。如果把 SDK 类写进代码，编译就直接失败；
 * 如果干脆不写接入点，"真实 SDK 怎么接"就只剩口头说明。</p>
 *
 * <p>折中的做法是：代码里保留实现类与配置项，运行时用反射探测 SDK 类是否存在。
 * 把 {@code lz.face.provider} 切成 arcsoft 后，应用<strong>依然能正常启动</strong>，
 * 调用相关接口时返回明确的"缺少 SDK / 缺少授权"提示而不是崩溃（需求 TC-G-04）。</p>
 *
 * <p>拿到授权后的改造清单见 {@code docs/face-sdk-integration.md}。</p>
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "lz.face", name = "provider", havingValue = "arcsoft")
public class ArcSoftFaceRecognitionService implements FaceRecognitionService {

    /** 虹软 SDK 的核心类名，用于运行时探测；不参与编译，因此缺依赖也能通过编译 */
    private static final String SDK_ENGINE_CLASS = "com.arcsoft.face.FaceEngine";

    private final boolean sdkPresent;

    @Value("${lz.face.app-id:}")
    private String appId;

    @Value("${lz.face.sdk-key:}")
    private String sdkKey;

    public ArcSoftFaceRecognitionService() {
        this.sdkPresent = detectSdk();
        log.warn("[FACE] provider=arcsoft，SDK 类{}", sdkPresent ? "已检测到" : "未检测到");
    }

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
        // 只有 SDK 类在 classpath 且授权参数齐备时才算可用
        return sdkPresent && appId != null && !appId.isBlank() && sdkKey != null && !sdkKey.isBlank();
    }

    @Override
    public String unavailableReason() {
        return buildReason();
    }

    @Override
    public String extractFeature(byte[] imageBytes) {
        throw unavailable();
    }

    @Override
    public double similarity(String featureA, String featureB) {
        throw unavailable();
    }

    @Override
    public double threshold() {
        return 0.8D;
    }

    private RuntimeException unavailable() {
        return new IllegalStateException("虹软人脸识别不可用：" + buildReason());
    }

    private static boolean detectSdk() {
        try {
            Class.forName(SDK_ENGINE_CLASS);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private String buildReason() {
        if (!sdkPresent) {
            return "classpath 中未检测到虹软 SDK（" + SDK_ENGINE_CLASS + "）。"
                    + "需引入本地安装后的 SDK jar 并提供授权文件，详见 docs/face-sdk-integration.md。"
                    + "当前请改用 provider=noop 的模拟实现，或直接使用二维码打卡。";
        }
        return "已检测到虹软 SDK，但缺少授权参数（lz.face.app-id / lz.face.sdk-key），请补齐后重启。";
    }
}
