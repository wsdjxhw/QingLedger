package com.qingledger.vo;

import lombok.Data;

@Data
public class CategoryResponse {

    private Integer id;

    private String name;

    private String type;

    private String icon;

    private String color;

    private Integer sortOrder;

    private Boolean isSystem;

    private Long userId;
}
