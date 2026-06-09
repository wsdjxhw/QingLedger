package com.qingledger.service.transaction.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qingledger.common.BusinessException;
import com.qingledger.dto.request.*;
import com.qingledger.entity.Category;
import com.qingledger.entity.Ledger;
import com.qingledger.entity.LedgerMember;
import com.qingledger.entity.Transaction;
import com.qingledger.enums.TransactionType;
import com.qingledger.mapper.CategoryMapper;
import com.qingledger.mapper.LedgerMapper;
import com.qingledger.mapper.LedgerMemberMapper;
import com.qingledger.mapper.TransactionMapper;
import com.qingledger.service.transaction.TransactionService;
import com.qingledger.vo.PageResult;
import com.qingledger.vo.TransactionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TransactionServiceImpl implements TransactionService {

    private static final String MSG_NOT_FOUND = "交易不存在";
    private static final String MSG_CATEGORY_NOT_FOUND = "分类不存在";
    private static final String MSG_TYPE_MISMATCH = "交易类型与分类类型不匹配";
    private static final String MSG_CATEGORY_NOT_VISIBLE = "分类不存在或不可用";
    private static final String MSG_INVALID_DATE = "日期格式错误";
    private static final String MSG_INVALID_AMOUNT = "金额必须大于0";
    private static final String MSG_LEDGER_ARCHIVED = "账本已归档";

    private final TransactionMapper transactionMapper;
    private final CategoryMapper categoryMapper;
    private final LedgerMemberMapper ledgerMemberMapper;
    private final LedgerMapper ledgerMapper;

    public TransactionServiceImpl(TransactionMapper transactionMapper,
                                  CategoryMapper categoryMapper,
                                  LedgerMemberMapper ledgerMemberMapper,
                                  LedgerMapper ledgerMapper) {
        this.transactionMapper = transactionMapper;
        this.categoryMapper = categoryMapper;
        this.ledgerMemberMapper = ledgerMemberMapper;
        this.ledgerMapper = ledgerMapper;
    }

    // ==================== 创建 ====================

    @Override
    @Transactional
    public Long createTransaction(Long userId, CreateTransactionRequest req) {
        // 1. 校验用户是账本成员
        if (!isMember(req.getLedgerId(), userId)) {
            throw new BusinessException(404, MSG_NOT_FOUND);
        }
        // 1.1 校验账本未归档
        checkLedgerNotArchived(req.getLedgerId());
        // 2. 如果传了 categoryId，校验分类对用户可见且 type 匹配
        if (req.getCategoryId() != null) {
            validateCategoryForUpdate(req.getType(), req.getCategoryId(), userId);
        }
        // 3. tags normalize：trim、转小写、去重、过滤空值
        String tags = normalizeTags(req.getTags());
        // 4. 解析 occurDate
        LocalDate occurDate;
        try {
            occurDate = LocalDate.parse(req.getOccurDate(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            throw new BusinessException(400, MSG_INVALID_DATE);
        }
        // 5. 构建 Transaction 对象并 insert
        Transaction txn = new Transaction();
        txn.setLedgerId(req.getLedgerId());
        txn.setUserId(userId);
        txn.setType(req.getType());
        txn.setCategoryId(req.getCategoryId());
        txn.setAmount(req.getAmount());
        txn.setTags(tags);
        txn.setNote(req.getNote());
        txn.setOccurDate(occurDate);

        transactionMapper.insert(txn);
        // 6. 返回重新查询的完整记录
        return txn.getId();
    }

    // ==================== 查询单条 ====================

    @Override
    public TransactionResponse getTransaction(Long userId, Long id) {
        Transaction txn = transactionMapper.selectById(id);
        if (txn == null || !isMember(txn.getLedgerId(), userId)) {
            throw new BusinessException(404, MSG_NOT_FOUND);
        }
        checkLedgerNotArchived(txn.getLedgerId());
        return toResponse(txn);
    }

    // ==================== 列表查询 ====================

    @Override
    public PageResult<TransactionResponse> listTransactions(Long userId, Long ledgerId, TransactionListQuery query) {
        if (!isMember(ledgerId, userId)) {
            throw new BusinessException(404, MSG_NOT_FOUND);
        }
        checkLedgerNotArchived(ledgerId);

        QueryWrapper<Transaction> wrapper = buildListQueryWrapper(ledgerId, query);

        // 分页查询
        IPage<Transaction> pageResult = transactionMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()),
                wrapper
        );

        // 批量获取分类名称
        Map<Integer, String> categoryNameMap = buildCategoryNameMap(pageResult.getRecords());

        // 构造响应
        List<TransactionResponse> records = pageResult.getRecords().stream()
                .map(txn -> toResponse(txn, categoryNameMap))
                .collect(Collectors.toList());

        // 汇总（忽略分页）
        PageResult.TransactionSummary summary = querySummary(ledgerId, query);

        PageResult<TransactionResponse> result = new PageResult<>();
        result.setRecords(records);
        result.setPage((int) pageResult.getCurrent());
        result.setSize((int) pageResult.getSize());
        result.setTotal(pageResult.getTotal());
        result.setSummary(summary);
        return result;
    }

    // ==================== 修改 ====================

    @Override
    @Transactional
    public Transaction updateTransaction(Long userId, Long id, UpdateTransactionRequest req) {
        Transaction txn = transactionMapper.selectById(id);
        if (txn == null || !Objects.equals(txn.getUserId(), userId) || !isMember(txn.getLedgerId(), userId)) {
            throw new BusinessException(404, MSG_NOT_FOUND);
        }
        checkLedgerNotArchived(txn.getLedgerId());

        TransactionType finalType = txn.getType();
        Integer finalCategoryId = txn.getCategoryId();
        boolean changed = false;

        UpdateWrapper<Transaction> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id).eq("user_id", userId);

        if (req.getType() != null) {
            wrapper.set("type", req.getType());
            finalType = req.getType();
            changed = true;
        }
        if (req.getAmount() != null) {
            if (req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(400, MSG_INVALID_AMOUNT);
            }
            wrapper.set("amount", req.getAmount());
            changed = true;
        }
        if (req.getCategoryId() != null) {
            wrapper.set("category_id", req.getCategoryId());
            finalCategoryId = req.getCategoryId();
            changed = true;
        }
        if (req.getTags() != null) {
            wrapper.set("tags", normalizeTags(req.getTags()));
            changed = true;
        }
        if (req.getOccurDate() != null) {
            try {
                wrapper.set("occur_date", LocalDate.parse(req.getOccurDate(), java.time.format.DateTimeFormatter.ISO_LOCAL_DATE));
            } catch (DateTimeParseException e) {
                throw new BusinessException(400, MSG_INVALID_DATE);
            }
            changed = true;
        }
        if (req.getNote() != null) {
            wrapper.set("note", req.getNote());
            changed = true;
        }

        if (!changed) {
            return txn;
        }

        // 校验最终 type 与分类一致性 + 分类可见性
        validateCategoryForUpdate(finalType, finalCategoryId, userId);

        int rows = transactionMapper.update(null, wrapper);
        if (rows == 0) {
            throw new BusinessException(404, MSG_NOT_FOUND);
        }
        return transactionMapper.selectById(id);
    }

    // ==================== 删除 ====================

    @Override
    @Transactional
    public void deleteTransaction(Long userId, Long id) {
        Transaction txn = transactionMapper.selectById(id);
        if (txn == null || !Objects.equals(txn.getUserId(), userId) || !isMember(txn.getLedgerId(), userId)) {
            throw new BusinessException(404, MSG_NOT_FOUND);
        }
        checkLedgerNotArchived(txn.getLedgerId());

        int rows = transactionMapper.delete(
                new UpdateWrapper<Transaction>()
                        .eq("id", id)
                        .eq("user_id", userId)
        );
        if (rows == 0) {
            throw new BusinessException(404, MSG_NOT_FOUND);
        }
    }

    // ==================== 批量删除 ====================

    @Override
    @Transactional
    public int batchDelete(Long userId, BatchIdsRequest req) {
        List<Transaction> myTxns = transactionMapper.selectList(
                new QueryWrapper<Transaction>()
                        .in("id", req.getIds())
                        .eq("user_id", userId)
        );
        if (myTxns.isEmpty()) {
            return 0;
        }

        // 过滤掉已退出账本的记录，只处理仍有成员身份的
        myTxns = filterAccessibleTxns(myTxns, userId);
        if (myTxns.isEmpty()) {
            return 0;
        }

        List<Long> myIds = myTxns.stream().map(Transaction::getId).collect(Collectors.toList());
        return transactionMapper.delete(
                new QueryWrapper<Transaction>()
                        .in("id", myIds)
                        .eq("user_id", userId)
        );
    }

    // ==================== 批量改分类 ====================

    @Override
    @Transactional
    public int batchUpdateCategory(Long userId, BatchCategoryRequest req) {
        List<Transaction> myTxns = transactionMapper.selectList(
                new QueryWrapper<Transaction>()
                        .in("id", req.getIds())
                        .eq("user_id", userId)
        );
        if (myTxns.isEmpty()) {
            return 0;
        }

        // 过滤掉已退出账本的记录
        myTxns = filterAccessibleTxns(myTxns, userId);
        if (myTxns.isEmpty()) {
            return 0;
        }

        // 校验新分类可见性
        Category newCategory = categoryMapper.selectById(req.getCategoryId());
        if (newCategory == null) {
            throw new BusinessException(400, MSG_CATEGORY_NOT_FOUND);
        }
        if (newCategory.getUserId() != null && !Objects.equals(newCategory.getUserId(), userId)) {
            throw new BusinessException(400, MSG_CATEGORY_NOT_VISIBLE);
        }

        // 按 type 分组校验一致性
        for (Transaction txn : myTxns) {
            if (!Objects.equals(txn.getType().getCode(), newCategory.getType().getCode())) {
                throw new BusinessException(400, MSG_TYPE_MISMATCH);
            }
        }

        List<Long> myIds = myTxns.stream().map(Transaction::getId).collect(Collectors.toList());
        return transactionMapper.update(null,
                new UpdateWrapper<Transaction>()
                        .in("id", myIds)
                        .eq("user_id", userId)
                        .set("category_id", req.getCategoryId())
        );
    }

    // ==================== 私有方法 ====================

    private void validateCategoryForUpdate(TransactionType type, Integer categoryId, Long userId) {
        if (categoryId == null) {
            return;
        }
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BusinessException(400, MSG_CATEGORY_NOT_FOUND);
        }
        // 可见性校验：系统分类或自己的分类
        if (category.getUserId() != null && !Objects.equals(category.getUserId(), userId)) {
            throw new BusinessException(400, MSG_CATEGORY_NOT_VISIBLE);
        }
        // type 一致性校验
        if (!Objects.equals(category.getType().getCode(), type.getCode())) {
            throw new BusinessException(400, MSG_TYPE_MISMATCH);
        }
    }

    private QueryWrapper<Transaction> buildListQueryWrapper(Long ledgerId, TransactionListQuery query) {
        QueryWrapper<Transaction> wrapper = Wrappers.query();
        wrapper.eq("ledger_id", ledgerId);

        if (query.getType() != null) {
            wrapper.eq("type", query.getType());
        }
        if (query.getCategoryId() != null) {
            wrapper.eq("category_id", query.getCategoryId());
        }
        if (query.getTag() != null && !query.getTag().isBlank()) {
            wrapper.apply("FIND_IN_SET({0}, tags)", query.getTag().toLowerCase().trim());
        }
        if (query.getStartDate() != null && !query.getStartDate().isBlank()) {
            try {
                LocalDate.parse(query.getStartDate(), java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException e) {
                throw new BusinessException(400, MSG_INVALID_DATE);
            }
            wrapper.ge("occur_date", query.getStartDate());
        }
        if (query.getEndDate() != null && !query.getEndDate().isBlank()) {
            try {
                LocalDate.parse(query.getEndDate(), java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException e) {
                throw new BusinessException(400, MSG_INVALID_DATE);
            }
            wrapper.le("occur_date", query.getEndDate());
        }
        if (query.getMinAmount() != null) {
            wrapper.ge("amount", query.getMinAmount());
        }
        if (query.getMaxAmount() != null) {
            wrapper.le("amount", query.getMaxAmount());
        }

        String sortColumn = "occur_date".equals(query.getSortBy()) ? "occur_date" : "amount";
        boolean asc = "asc".equalsIgnoreCase(query.getSortOrder());
        wrapper.orderBy(true, asc, sortColumn);

        return wrapper;
    }

    private Map<Integer, String> buildCategoryNameMap(List<Transaction> txns) {
        Set<Integer> categoryIds = txns.stream()
                .map(Transaction::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (categoryIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Category> categories = categoryMapper.selectBatchIds(categoryIds);
        return categories.stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
    }

    private List<Transaction> filterAccessibleTxns(List<Transaction> txns, Long userId) {
        return txns.stream()
                .filter(txn -> isMember(txn.getLedgerId(), userId) && !isArchived(txn.getLedgerId()))
                .collect(Collectors.toList());
    }

    private boolean isArchived(Long ledgerId) {
        Ledger ledger = ledgerMapper.selectById(ledgerId);
        return ledger == null || (ledger.getStatus() != null && ledger.getStatus() == 0);
    }

    private void checkLedgerNotArchived(Long ledgerId) {
        if (isArchived(ledgerId)) {
            throw new BusinessException(400, MSG_LEDGER_ARCHIVED);
        }
    }

    private boolean isMember(Long ledgerId, Long userId) {
        Long count = ledgerMemberMapper.selectCount(
                Wrappers.<LedgerMember>query()
                        .eq("ledger_id", ledgerId)
                        .eq("user_id", userId)
        );
        return count != null && count > 0;
    }

    private PageResult.TransactionSummary querySummary(Long ledgerId, TransactionListQuery query) {
        QueryWrapper<Transaction> wrapper = buildListQueryWrapper(ledgerId, query);

        List<Object> incomeResult = transactionMapper.selectObjs(
                wrapper.clone().select("COALESCE(SUM(CASE WHEN type = 'income' THEN amount ELSE 0 END), 0)")
        );
        List<Object> expenseResult = transactionMapper.selectObjs(
                wrapper.clone().select("COALESCE(SUM(CASE WHEN type = 'expense' THEN amount ELSE 0 END), 0)")
        );

        BigDecimal totalIncome = incomeResult.isEmpty() || incomeResult.get(0) == null
                ? BigDecimal.ZERO : new BigDecimal(incomeResult.get(0).toString());
        BigDecimal totalExpense = expenseResult.isEmpty() || expenseResult.get(0) == null
                ? BigDecimal.ZERO : new BigDecimal(expenseResult.get(0).toString());

        return new PageResult.TransactionSummary(totalIncome, totalExpense);
    }

    private String normalizeTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return null;
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.joining(","));
    }

    private TransactionResponse toResponse(Transaction txn) {
        return toResponse(txn, null);
    }

    private TransactionResponse toResponse(Transaction txn, Map<Integer, String> categoryNameMap) {
        TransactionResponse resp = new TransactionResponse();
        resp.setId(txn.getId());
        resp.setLedgerId(txn.getLedgerId());
        resp.setUserId(txn.getUserId());
        resp.setType(txn.getType() == null ? null : txn.getType().getCode());
        resp.setAmount(txn.getAmount());
        resp.setCategoryId(txn.getCategoryId());

        if (categoryNameMap != null && txn.getCategoryId() != null) {
            resp.setCategoryName(categoryNameMap.get(txn.getCategoryId()));
        } else if (txn.getCategoryId() != null) {
            Category category = categoryMapper.selectById(txn.getCategoryId());
            resp.setCategoryName(category == null ? null : category.getName());
        }

        resp.setTags(txn.getTags());
        resp.setOccurDate(txn.getOccurDate());
        resp.setNote(txn.getNote());
        resp.setCreatedAt(txn.getCreatedAt());
        return resp;
    }
}
