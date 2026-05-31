package com.qingledger.service.category.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.qingledger.common.BusinessException;
import com.qingledger.dto.request.CreateCategoryRequest;
import com.qingledger.dto.request.UpdateCategoryRequest;
import com.qingledger.entity.Category;
import com.qingledger.enums.CategoryType;
import com.qingledger.mapper.CategoryMapper;
import com.qingledger.mapper.TransactionMapper;
import com.qingledger.service.category.CategoryService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class CategoryServiceImpl implements CategoryService {

    private static final String MSG_NOT_FOUND = "分类不存在";
    private static final String MSG_NO_PERMISSION = "无权操作该分类";
    private static final String MSG_SYSTEM_READ_ONLY = "系统预设分类不可修改";
    private static final String MSG_SYSTEM_CANNOT_DELETE = "系统预设分类不可删除";
    private static final String MSG_REFERENCED = "该分类下有交易记录，无法删除";
    private static final String MSG_DUPLICATED = "同类型下分类名称已存在";

    private final CategoryMapper categoryMapper;
    private final TransactionMapper transactionMapper;

    public CategoryServiceImpl(CategoryMapper categoryMapper, TransactionMapper transactionMapper) {
        this.categoryMapper = categoryMapper;
        this.transactionMapper = transactionMapper;
    }

    @Override
    public List<Category> listCategories(Long userId, CategoryType type) {
        QueryWrapper<Category> wrapper = Wrappers.query();
        wrapper.eq("type", type)
                .and(w -> w.isNull("user_id").or().eq("user_id", userId))
                .orderByAsc("sort_order", "id");
        return categoryMapper.selectList(wrapper);
    }

    @Override
    @Transactional
    public Category createCategory(Long userId, CreateCategoryRequest req) {
        validateUniqueName(userId, req.getType(), req.getName(), null);

        Category category = new Category();
        category.setName(req.getName());
        category.setType(req.getType());
        category.setIcon(normalizeNullable(req.getIcon()));
        category.setColor(normalizeNullable(req.getColor()));
        category.setSortOrder(0);
        category.setIsSystem(false);
        category.setUserId(userId);

        categoryMapper.insert(category);
        return categoryMapper.selectById(category.getId());
    }

    @Override
    @Transactional
    public Category updateCategory(Long userId, Integer categoryId, UpdateCategoryRequest req) {
        Category existing = categoryMapper.selectById(categoryId);
        if (existing == null) {
            throw new BusinessException(400, MSG_NOT_FOUND);
        }
        if (Boolean.TRUE.equals(existing.getIsSystem())) {
            throw new BusinessException(400, MSG_SYSTEM_READ_ONLY);
        }
        if (!Objects.equals(userId, existing.getUserId())) {
            throw new BusinessException(400, MSG_NO_PERMISSION);
        }

        String newName = req.getName();
        if (newName != null && !newName.equals(existing.getName())) {
            validateUniqueName(userId, existing.getType(), newName, categoryId);
        }

        boolean noChange = req.getName() == null && req.getIcon() == null && req.getColor() == null;
        if (noChange) {
            return existing;
        }

        UpdateWrapper<Category> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", categoryId)
                .eq("user_id", userId)
                .eq("is_system", 0);

        if (req.getName() != null) {
            wrapper.set("name", req.getName());
        }
        if (req.getIcon() != null) {
            wrapper.set("icon", normalizeNullable(req.getIcon()));
        }
        if (req.getColor() != null) {
            wrapper.set("color", normalizeNullable(req.getColor()));
        }

        int affected = categoryMapper.update(null, wrapper);
        if (affected <= 0) {
            throw new BusinessException(400, MSG_NO_PERMISSION);
        }
        return categoryMapper.selectById(categoryId);
    }

    @Override
    @Transactional
    public void deleteCategory(Long userId, Integer categoryId) {
        Category existing = categoryMapper.selectById(categoryId);
        if (existing == null) {
            throw new BusinessException(400, MSG_NOT_FOUND);
        }
        if (Boolean.TRUE.equals(existing.getIsSystem())) {
            throw new BusinessException(400, MSG_SYSTEM_CANNOT_DELETE);
        }
        if (!Objects.equals(userId, existing.getUserId())) {
            throw new BusinessException(400, MSG_NO_PERMISSION);
        }

        Long referencedCount = transactionMapper.countByCategoryId(categoryId);
        if (referencedCount != null && referencedCount > 0) {
            throw new BusinessException(400, MSG_REFERENCED);
        }

        UpdateWrapper<Category> deleteWrapper = new UpdateWrapper<>();
        deleteWrapper.eq("id", categoryId)
                .eq("user_id", userId)
                .eq("is_system", 0);

        try {
            int affected = categoryMapper.delete(deleteWrapper);
            if (affected <= 0) {
                throw new BusinessException(400, MSG_NO_PERMISSION);
            }
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(400, MSG_REFERENCED);
        }
    }

    private void validateUniqueName(Long userId, CategoryType type, String name, Integer excludeId) {
        QueryWrapper<Category> wrapper = Wrappers.query();
        wrapper.eq("name", name)
                .eq("type", type)
                .and(w -> w.isNull("user_id").or().eq("user_id", userId));
        if (excludeId != null) {
            wrapper.ne("id", excludeId);
        }
        Long count = categoryMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(400, MSG_DUPLICATED);
        }
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
