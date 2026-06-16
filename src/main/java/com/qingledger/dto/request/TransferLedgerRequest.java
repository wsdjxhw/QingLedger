package com.qingledger.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TransferLedgerRequest {

    @NotNull(message = "目标用户ID不能为空")
    private Long targetUserId;
}
