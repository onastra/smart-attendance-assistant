package com.lianzhi.attendance.place.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 办公场所。
 *
 * <p><code>lng/lat</code> 为 WGS84 或业务方自定坐标系下的经纬度。</p>
 *
 * <p><strong>坐标采集诚实性</strong>：本系统未配置第三方地图服务 Key，
 * 坐标只能由管理员手动录入或批量导入，不存在"自动定位"能力。
 * 一旦 {@code lz.map.provider} 配置为具体厂商，应在地图上取点后回填此处，
 * 而不是谎称已自动采集。</p>
 */
@Data
public class Place {

    private Long id;

    @NotBlank(message = "场所名称不能为空")
    @Size(max = 100, message = "场所名称长度不能超过 100")
    private String placeName;

    private Long companyId;

    private String province;
    private String city;

    @Size(max = 255, message = "地址长度不能超过 255")
    private String address;

    /** 经度；decimal(10,7) */
    private BigDecimal lng;
    /** 纬度；decimal(10,7) */
    private BigDecimal lat;

    /** 场所业务编码（公司 ID 码），由服务端生成，扫码用 */
    private String placeCode;

    /** 允许的打卡距离（米），阶段 3 的围栏校验会使用 */
    private Integer coverRadiusM;

    /** 1 启用 0 停用 */
    private Integer status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ===== 展示用辅助字段 =====
    private String companyName;
    /** 已绑定的在职员工数 */
    private Long boundEmployeeCount;
}
