package com.qingledger.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MemberResponse {

    private Long userId;

    private String nickname;

    private String avatar;

    private String role;

    private LocalDateTime joinedAt;
}
