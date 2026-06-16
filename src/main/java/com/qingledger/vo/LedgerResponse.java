package com.qingledger.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class LedgerResponse {

    private Long id;

    private String name;

    private String description;

    private String type;

    private String icon;

    private String color;

    private Integer status;

    private List<MemberResponse> members;

    private Long ownerId;

    private LocalDateTime createdAt;
}
