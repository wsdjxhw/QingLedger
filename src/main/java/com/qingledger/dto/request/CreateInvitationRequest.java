package com.qingledger.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class CreateInvitationRequest {

    @Min(value = 1, message = "最大使用次数不能少于1")
    @Max(value = 100, message = "最大使用次数不能超过100")
    private int maxUses = 1;
}
