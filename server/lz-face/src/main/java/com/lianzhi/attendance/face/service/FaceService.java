package com.lianzhi.attendance.face.service;

import com.lianzhi.attendance.common.exception.BusinessException;
import com.lianzhi.attendance.common.page.PageQuery;
import com.lianzhi.attendance.common.page.PageResult;
import com.lianzhi.attendance.common.result.ResultCode;
import com.lianzhi.attendance.face.dto.FaceEnrollResult;
import com.lianzhi.attendance.face.dto.FaceStatusResult;
import com.lianzhi.attendance.face.dto.FaceVerifyResult;
import com.lianzhi.attendance.face.entity.FaceFeature;
import com.lianzhi.attendance.face.mapper.FaceFeatureMapper;
import com.lianzhi.attendance.framework.auth.AuthUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 人脸业务服务：录入、校验、状态查询。
 *
 * <p>所有出入口都强制带 {@code mock} 标记：模拟实现产生的数据如果被当作真实生物特征使用，
 * 后果比"没有这个功能"严重得多，所以标记必须在服务层写死，不能靠调用方自觉。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FaceService {

    /** 与考勤模块一致：以「账号管理」权限作为系统管理员标识 */
    private static final String ADMIN_MARKER = "system:user:list";

    private static final double MIN_IMAGE_BYTES = 16D;

    private final FaceRecognitionService recognition;
    private final FaceFeatureMapper featureMapper;

    @Value("${lz.face.store-image:false}")
    private boolean storeImage;

    public FaceEnrollResult enroll(Long employeeId, byte[] image, AuthUser user) {
        assertAvailable();
        Long target = resolveTargetEmployee(employeeId, user);
        assertImage(image);

        String feature = recognition.extractFeature(image);
        int mockFlag = recognition.mock() ? 1 : 0;

        FaceFeature existing = featureMapper.selectOne(target, recognition.provider());
        if (existing == null) {
            FaceFeature featureRow = new FaceFeature();
            featureRow.setEmployeeId(target);
            featureRow.setProvider(recognition.provider());
            featureRow.setFeatureHash(feature);
            featureRow.setMockFlag(mockFlag);
            featureRow.setStatus(1);
            featureMapper.insert(featureRow);
        } else {
            featureMapper.updateFeature(target, recognition.provider(), feature, mockFlag);
        }

        if (recognition.mock()) {
            log.info("[MOCK] 人脸录入 employeeId={} provider={} —— 模拟实现，非真实生物特征", target,
                    recognition.provider());
        }

        FaceEnrollResult result = new FaceEnrollResult();
        result.setEmployeeId(target);
        result.setProvider(recognition.provider());
        result.setMock(recognition.mock());
        result.setFeatureHashPreview(feature.substring(0, 16) + "…");
        result.setStoredImage(storeImage);
        result.setNotice(recognition.mock()
                ? "本次录入由模拟实现完成（provider=noop），仅保存图片指纹的哈希，不是真实人脸特征，不能作为身份凭证。"
                : "已录入真实人脸特征。");
        return result;
    }

    public FaceVerifyResult verify(Long employeeId, byte[] image, AuthUser user) {
        assertAvailable();
        Long target = resolveTargetEmployee(employeeId, user);
        assertImage(image);

        FaceFeature stored = featureMapper.selectOne(target, recognition.provider());
        FaceVerifyResult result = new FaceVerifyResult();
        result.setEmployeeId(target);
        result.setProvider(recognition.provider());
        result.setMock(recognition.mock());
        result.setThreshold(recognition.threshold());

        if (stored == null || stored.getFeatureHash() == null) {
            result.setPassed(false);
            result.setSimilarity(0D);
            result.setReason("NOT_ENROLLED");
            result.setNotice("该员工尚未录入人脸，请先录入。");
            return result;
        }

        double similarity = recognition.similarity(stored.getFeatureHash(), recognition.extractFeature(image));
        boolean passed = similarity >= recognition.threshold();
        result.setSimilarity(round(similarity));
        result.setPassed(passed);
        result.setReason(passed ? "OK" : "BELOW_THRESHOLD");
        result.setNotice(recognition.mock()
                ? "模拟比对：仅比较图片指纹哈希的字符相同比例，不是人脸相似度；同一张图必然通过，换张图必然不通过。"
                : "真实人脸特征比对。");

        if (recognition.mock()) {
            log.info("[MOCK] 人脸校验 employeeId={} passed={} similarity={} —— 模拟实现，不作身份凭证",
                    target, passed, result.getSimilarity());
        }
        return result;
    }

    public FaceStatusResult status(AuthUser user) {
        Long employeeId = user == null ? null : user.getEmployeeId();
        FaceStatusResult result = new FaceStatusResult();
        result.setProvider(recognition.provider());
        result.setMock(recognition.mock());
        result.setAvailable(recognition.available());
        result.setStoreImage(storeImage);
        result.setEnrolled(employeeId != null
                && featureMapper.selectOne(employeeId, recognition.provider()) != null);
        result.setNotice(recognition.available() ? null : recognition.unavailableReason());
        result.setPrivacyNote("默认只保存特征值的哈希，不保存原始人脸图片（lz.face.store-image="
                + storeImage + "）。生物特征不可更换，能不存就不存。");
        return result;
    }

    public PageResult<FaceFeature> list(String keyword, int pageNum, int pageSize, AuthUser user) {
        if (user == null || !user.hasPermission(ADMIN_MARKER)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有系统管理员可以查看全员人脸录入情况");
        }
        PageQuery query = new PageQuery();
        query.setPageNum(Math.max(1, pageNum));
        query.setPageSize(pageSize);
        int offset = (query.getPageNum() - 1) * query.getPageSize();
        return PageResult.of(query, featureMapper.countPage(keyword),
                featureMapper.selectPage(keyword, offset, query.getPageSize()));
    }

    // ---------------- 内部实现 ----------------

    private void assertAvailable() {
        if (!recognition.available()) {
            throw new BusinessException(ResultCode.DEPENDENCY_NOT_CONFIGURED,
                    "人脸识别当前不可用：" + recognition.unavailableReason());
        }
    }

    /**
     * 目标员工解析：默认本人，指定他人需要管理员权限。
     *
     * <p>人脸是个人生物特征，允许任意账号替他人录入等于给了一个绕过身份核验的后门。</p>
     */
    private Long resolveTargetEmployee(Long employeeId, AuthUser user) {
        Long own = user == null ? null : user.getEmployeeId();
        if (employeeId == null) {
            if (own == null) {
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        "当前账号未关联员工档案，请显式指定 employeeId");
            }
            return own;
        }
        if (own != null && own.equals(employeeId)) {
            return own;
        }
        if (user == null || !user.hasPermission(ADMIN_MARKER)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能录入/校验本人的人脸");
        }
        return employeeId;
    }

    private void assertImage(byte[] image) {
        if (image == null || image.length < MIN_IMAGE_BYTES) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "人脸图片为空或内容过小");
        }
    }

    private static double round(double value) {
        return Math.round(value * 10000D) / 10000D;
    }
}
