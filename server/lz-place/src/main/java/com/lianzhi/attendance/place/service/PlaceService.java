package com.lianzhi.attendance.place.service;

import com.lianzhi.attendance.common.exception.BusinessException;
import com.lianzhi.attendance.common.page.PageResult;
import com.lianzhi.attendance.common.result.ResultCode;
import com.lianzhi.attendance.framework.cache.CacheService;
import com.lianzhi.attendance.place.entity.Place;
import com.lianzhi.attendance.place.entity.PlaceEmployee;
import com.lianzhi.attendance.place.mapper.PlaceMapper;
import com.lianzhi.attendance.place.query.PlaceQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 办公场所。
 *
 * <p>删除统一转为停用：场所会被打卡记录引用，物理删除会让历史打卡失去地点归属。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final PlaceMapper placeMapper;
    private final CacheService cacheService;

    /** 场所详情缓存秒数；写操作会主动失效，TTL 只是兜底 */
    @Value("${lz.cache.ttl.place-detail-seconds:600}")
    private long placeTtlSeconds;

    /** 地图服务厂商；未配置时为 none，此时坐标只能手动录入 */
    @Value("${lz.map.provider:none}")
    private String mapProvider;

    /** 地图开放平台 Key；只允许出现在 application-local.yml（gitignored）或环境变量，绝不入库 */
    @Value("${lz.map.api-key:}")
    private String mapApiKey;

    /** 高德 JS API 2.0 配套安全密钥；2021-12 后申请的 Key 必须配套使用 */
    @Value("${lz.map.security-code:}")
    private String mapSecurityCode;

    public PageResult<Place> page(PlaceQuery query) {
        long total = placeMapper.countByQuery(query);
        if (total == 0) {
            return PageResult.empty(query);
        }
        return PageResult.of(query, total, placeMapper.selectByQuery(query));
    }

    public Place detail(Long id) {
        String cacheKey = "place:detail:" + id;
        Place cached = cacheService.get(cacheKey, Place.class);
        if (cached != null) {
            return cached;
        }
        Place place = placeMapper.selectById(id);
        if (place == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "场所不存在");
        }
        cacheService.put(cacheKey, place, Duration.ofSeconds(placeTtlSeconds));
        return place;
    }

    @Transactional
    public Long create(Place place) {
        place.setPlaceCode(generatePlaceCode(place.getCompanyId()));
        if (place.getCoverRadiusM() == null) {
            place.setCoverRadiusM(200);
        }
        if (place.getStatus() == null) {
            place.setStatus(1);
        }
        placeMapper.insert(place);
        log.info("[PLACE] 新建场所 id={} name={} code={}", place.getId(), place.getPlaceName(), place.getPlaceCode());
        return place.getId();
    }

    @Transactional
    public void update(Place place) {
        requirePlace(place.getId());
        // place_code 一经生成不允许改动，否则已分发出去的旧二维码无法定位场所
        place.setPlaceCode(null);
        placeMapper.updateById(place);
        cacheService.evict("place:detail:" + place.getId());
        log.info("[PLACE] 编辑场所 id={}", place.getId());
    }

    @Transactional
    public void changeStatus(Long id, Integer status) {
        requirePlace(id);
        if (status != null && status == 0 && placeMapper.countActiveBindingsByPlaceId(id) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "该场所下仍有已绑定员工，请先解绑");
        }
        Place patch = new Place();
        patch.setId(id);
        patch.setStatus(status);
        placeMapper.updateById(patch);
        cacheService.evict("place:detail:" + id);
        log.info("[PLACE] 变更场所状态 id={} status={}", id, status);
    }

    public List<PlaceEmployee> employeesOf(Long placeId) {
        requirePlace(placeId);
        return placeMapper.selectEmployeesByPlaceId(placeId);
    }

    @Transactional
    public void bindEmployees(Long placeId, List<Long> employeeIds) {
        requirePlace(placeId);
        if (employeeIds == null || employeeIds.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请至少选择一名员工");
        }
        for (Long employeeId : employeeIds) {
            if (placeMapper.countEmployeeById(employeeId) == 0) {
                throw new BusinessException(ResultCode.NOT_FOUND, "员工 " + employeeId + " 不存在，已终止本次绑定");
            }
            if (placeMapper.countBinding(employeeId, placeId) == 0) {
                placeMapper.insertBinding(employeeId, placeId);
            }
        }
        log.info("[PLACE] 绑定员工 placeId={} employeeCount={}", placeId, employeeIds.size());
    }

    @Transactional
    public void unbindEmployee(Long placeId, Long employeeId) {
        requirePlace(placeId);
        placeMapper.disableBinding(employeeId, placeId);
        log.info("[PLACE] 解绑员工 placeId={} employeeId={}", placeId, employeeId);
    }

    /**
     * 重新生成场所码。
     *
     * <p>场景：场所码可能已经随二维码分发出去，重发会导致旧码失效。
     * 这里不自动清理绑定关系，由调用方自行决定是否先解绑。</p>
     */
    @Transactional
    public String regenerateCode(Long id) {
        Place place = requirePlace(id);
        String newCode = generatePlaceCode(place.getCompanyId());
        Place patch = new Place();
        patch.setId(id);
        patch.setPlaceCode(newCode);
        placeMapper.updateById(patch);
        cacheService.evict("place:detail:" + id);
        log.info("[PLACE] 重新生成场所码 id={} newCode={}", id, newCode);
        return newCode;
    }

    /**
     * 地图能力状态。
     *
     * <p>未配置时明确上报没有接入任何地图服务，坐标必须手动录入；
     * 配置后携带 Key 供前端动态加载 JS SDK——Key 的唯一来源是本方法，
     * 前端源码里不允许出现硬编码 Key（公开仓库防泄漏）。
     * 这是为了杜绝"界面上看着像能自动定位、实际数据是假的"这种情况。</p>
     *
     * <p>Key 只会暴露在登录管理员的浏览器网络面板里，与 JS API Key
     * 本身"前端可见"的属性一致；真正防线是开放平台控制台的域名白名单，
     * 部署文档中要求配置。</p>
     */
    public Map<String, Object> mapStatus() {
        Map<String, Object> status = new HashMap<>();
        // 占位符（未替换的模板值）不算已配置：宁可上报"未接入"手动录入，
        // 也不能下发一个假 Key 让前端地图加载失败还显示"已接入"
        boolean keyPresent = StringUtils.hasText(mapApiKey)
                && !mapApiKey.contains("请填入") && !mapApiKey.contains("your_");
        boolean configured = StringUtils.hasText(mapProvider) && !"none".equalsIgnoreCase(mapProvider)
                && keyPresent;
        status.put("enabled", configured);
        status.put("provider", configured ? mapProvider : "none");
        status.put("coordinateSource", configured ? "map-picker" : "manual-input");
        if (configured) {
            status.put("apiKey", mapApiKey);
            if (StringUtils.hasText(mapSecurityCode)) {
                status.put("securityJsCode", mapSecurityCode);
            }
        }
        status.put("note", configured
                ? "已接入地图服务（" + mapProvider + "），可在地图上搜索、取点选址"
                : "未配置地图服务 Key，坐标需由管理员手动录入（不存在自动定位）");
        return status;
    }

    /**
     * 生成场所码：PLACE-<公司ID>-<8位随机>。
     *
     * <p>去掉了易混淆字符 I/O/0/1，避免人工抄写场所码时出错。</p>
     */
    private String generatePlaceCode(Long companyId) {
        StringBuilder sb = new StringBuilder("PLACE-");
        sb.append(companyId == null ? 0 : companyId).append("-");
        for (int i = 0; i < 8; i++) {
            sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        String candidate = sb.toString();
        if (placeMapper.countByPlaceCode(candidate, null) > 0) {
            return generatePlaceCode(companyId);
        }
        return candidate;
    }

    private Place requirePlace(Long id) {
        Place place = placeMapper.selectById(id);
        if (place == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "场所不存在");
        }
        return place;
    }
}
