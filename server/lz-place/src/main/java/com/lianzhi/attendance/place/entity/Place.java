package com.lianzhi.attendance.place.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 办公场所。
 *
 * <p><code>lng/lat</code> 统一按 <strong>WGS84</strong> 存储：H5 打卡页浏览器定位上报的
 * 就是 WGS84，围栏校验（GeoDistanceUtil）两侧同坐标系才不会出现几百米偏差。
 * 管理端高德地图选点得到的是 GCJ-02（火星坐标），前端回填表单前已转换为 WGS84；
 * 反向展示（WGS84→GCJ-02）发生在前端地图渲染时，库内永远只有 WGS84。</p>
 *
 * <p><strong>坐标采集诚实性</strong>：{@code lz.map.provider} 为 none 时坐标只能手动录入；
 * 配置为具体厂商后，由前端地图选点回填，来源见 PlaceService#mapStatus。</p>
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
