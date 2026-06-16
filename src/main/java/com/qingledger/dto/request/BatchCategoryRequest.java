package com.qingledger.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class BatchCategoryRequest {

    //批量改分类
    @NotEmpty(message = "ids 不能为空")
    private List<Long> ids;

    @NotNull(message = "categoryId 不能为空")
    private Integer categoryId;
}
