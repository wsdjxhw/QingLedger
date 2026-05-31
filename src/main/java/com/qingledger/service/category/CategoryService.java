package com.qingledger.service.category;

import com.qingledger.dto.request.CreateCategoryRequest;
import com.qingledger.dto.request.UpdateCategoryRequest;
import com.qingledger.entity.Category;
import com.qingledger.enums.CategoryType;

import java.util.List;

public interface CategoryService {

    List<Category> listCategories(Long userId, CategoryType type);

    Category createCategory(Long userId, CreateCategoryRequest req);

    Category updateCategory(Long userId, Integer categoryId, UpdateCategoryRequest req);

    void deleteCategory(Long userId, Integer categoryId);
}
