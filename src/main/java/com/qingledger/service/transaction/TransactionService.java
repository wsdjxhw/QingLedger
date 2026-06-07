package com.qingledger.service.transaction;

import com.qingledger.dto.request.*;
import com.qingledger.entity.Transaction;
import com.qingledger.vo.PageResult;
import com.qingledger.vo.TransactionResponse;

public interface TransactionService {

    Transaction createTransaction(Long userId, CreateTransactionRequest req);

    TransactionResponse getTransaction(Long userId, Long id);

    PageResult<TransactionResponse> listTransactions(Long userId, Long ledgerId, TransactionListQuery query);

    Transaction updateTransaction(Long userId, Long id, UpdateTransactionRequest req);

    void deleteTransaction(Long userId, Long id);

    int batchDelete(Long userId, BatchIdsRequest req);

    int batchUpdateCategory(Long userId, BatchCategoryRequest req);
}
