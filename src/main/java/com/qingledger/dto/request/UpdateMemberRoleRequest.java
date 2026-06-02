package com.qingledger.dto.request;

import com.qingledger.enums.MemberRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateMemberRoleRequest {

    @NotNull(message = "角色不能为空")
    private MemberRole role;
}
