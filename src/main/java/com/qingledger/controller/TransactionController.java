package com.qingledger.controller;

import com.qingledger.common.BusinessException;
import com.qingledger.common.Result;
import com.qingledger.dto.request.*;
import com.qingledger.service.transaction.TransactionService;
import com.qingledger.utils.UserContext;
import com.qingledger.vo.PageResult;
import com.qingledger.vo.TransactionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@Tag(name = "交易记录接口", description = "交易记录管理")
@RestController
@RequestMapping("/api/v1/transaction")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @Operation(summary = "创建交易", description = "在账本中新增一条交易记录", security = @SecurityRequirement(name = "JWT"))
    @PostMapping
    public Result<TransactionResponse> createTransaction(@Valid @RequestBody CreateTransactionRequest req) {
        // TODO
        throw new UnsupportedOperationException("待实现");
    }

    @Operation(summary = "获取交易详情", description = "查询单条交易记录", security = @SecurityRequirement(name = "JWT"))
    @GetMapping("/{id}")
    public Result<TransactionResponse> getTransaction(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        return Result.ok(transactionService.getTransaction(userId, id));
    }

    @Operation(summary = "交易列表", description = "分页查询交易记录，支持按账本、分类、日期、金额、标签筛选", security = @SecurityRequirement(name = "JWT"))
    @GetMapping
    public Result<PageResult<TransactionResponse>> listTransactions(
            @RequestParam Long ledgerId,
            @Valid TransactionListQuery query) {
        Long userId = getCurrentUserId();
        return Result.ok(transactionService.listTransactions(userId, ledgerId, query));
    }

    @Operation(summary = "修改交易", description = "修改自己的交易记录", security = @SecurityRequirement(name = "JWT"))
    @PutMapping("/{id}")
    public Result<TransactionResponse> updateTransaction(@PathVariable Long id,
                                                         @Valid @RequestBody UpdateTransactionRequest req) {
        Long userId = getCurrentUserId();
        transactionService.updateTransaction(userId, id, req);
        return Result.ok(transactionService.getTransaction(userId, id));
    }

    @Operation(summary = "删除交易", description = "删除自己的交易记录", security = @SecurityRequirement(name = "JWT"))
    @DeleteMapping("/{id}")
    public Result<Void> deleteTransaction(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        transactionService.deleteTransaction(userId, id);
        return Result.ok();
    }

    @Operation(summary = "批量删除", description = "批量删除自己的交易记录", security = @SecurityRequirement(name = "JWT"))
    @DeleteMapping("/batch")
    public Result<Integer> batchDelete(@Valid @RequestBody BatchIdsRequest req) {
        Long userId = getCurrentUserId();
        return Result.ok(transactionService.batchDelete(userId, req));
    }

    @Operation(summary = "批量改分类", description = "批量修改交易记录的所属分类", security = @SecurityRequirement(name = "JWT"))
    @PutMapping("/batch/category")
    public Result<Integer> batchUpdateCategory(@Valid @RequestBody BatchCategoryRequest req) {
        Long userId = getCurrentUserId();
        return Result.ok(transactionService.batchUpdateCategory(userId, req));
    }

    private Long getCurrentUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录或登录已失效");
        }
        return userId;
    }

}
