package com.qingledger.vo;

import lombok.Data;

@Data
public class PersonaResponse {

    private Integer id;

    /** 人设编码 */
    private String code;

    /** 人设名称 */
    private String name;

    /** 人设描述 */
    private String description;

    /** 头像图标 URL */
    private String avatar;

    /** 排序序号 */
    private Integer sortOrder;
}
