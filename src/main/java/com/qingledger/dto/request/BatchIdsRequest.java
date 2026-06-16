package com.qingledger.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BatchIdsRequest {
    //批量删除

    @NotEmpty(message = "ids 不能为空")
    private List<Long> ids;
}
