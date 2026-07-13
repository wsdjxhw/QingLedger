package com.qingledger.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("persona_template")
public class PersonaTemplate {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 人设编码，如 "cute_pet"、"friendly_assistant" */
    private String code;

    /** 人设展示名称 */
    private String name;

    /** 人设描述 */
    private String description;

    /** AI system prompt 内容 */
    private String systemPrompt;

    /** 头像图标 URL */
    private String avatar;

    /** 是否启用：1-启用，0-禁用 */
    private Boolean isActive;

    /** 排序序号，越小越靠前 */
    private Integer sortOrder;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
