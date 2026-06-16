package com.qingledger.controller;

import com.qingledger.common.BusinessException;
import com.qingledger.common.Result;
import com.qingledger.dto.request.CreateCategoryRequest;
import com.qingledger.dto.request.UpdateCategoryRequest;
import com.qingledger.entity.Category;
import com.qingledger.enums.CategoryType;
import com.qingledger.service.category.CategoryService;
import com.qingledger.utils.UserContext;
import com.qingledger.vo.CategoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "分类接口", description = "分类管理")
@RestController
@RequestMapping("/api/v1/category")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Operation(summary = "查询分类列表", description = "查询当前用户可见分类", security = @SecurityRequirement(name = "JWT"))
    @GetMapping
    public Result<List<CategoryResponse>> listCategories(@RequestParam @NotNull String type) {
        Long userId = getCurrentUserId();
        CategoryType categoryType = CategoryType.fromCode(type);
        List<CategoryResponse> responses = categoryService.listCategories(userId, categoryType)
                .stream()
                .map(this::toResponse)
                .toList();
        return Result.ok(responses);
    }

    @Operation(summary = "创建分类", description = "创建用户自定义分类", security = @SecurityRequirement(name = "JWT"))
    @PostMapping
    public Result<CategoryResponse> createCategory(@Valid @RequestBody CreateCategoryRequest req) {
        Long userId = getCurrentUserId();
        Category created = categoryService.createCategory(userId, req);
        return Result.ok(toResponse(created));
    }

    @Operation(summary = "修改分类", description = "修改用户自定义分类", security = @SecurityRequirement(name = "JWT"))
    @PutMapping("/{id}")
    public Result<CategoryResponse> updateCategory(@PathVariable Integer id,
                                                   @Valid @RequestBody UpdateCategoryRequest req) {
        Long userId = getCurrentUserId();
        Category updated = categoryService.updateCategory(userId, id, req);
        return Result.ok(toResponse(updated));
    }

    @Operation(summary = "删除分类", description = "删除用户自定义分类", security = @SecurityRequirement(name = "JWT"))
    @DeleteMapping("/{id}")
    public Result<Void> deleteCategory(@PathVariable Integer id) {
        Long userId = getCurrentUserId();
        categoryService.deleteCategory(userId, id);
        return Result.ok();
    }

    private Long getCurrentUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录或登录已失效");
        }
        return userId;
    }

    private CategoryResponse toResponse(Category category) {
        CategoryResponse response = new CategoryResponse();
        response.setId(category.getId());
        response.setName(category.getName());
        response.setType(category.getType() == null ? null : category.getType().getCode());
        response.setIcon(category.getIcon());
        response.setColor(category.getColor());
        response.setSortOrder(category.getSortOrder());
        response.setIsSystem(category.getIsSystem());
        response.setUserId(category.getUserId());
        return response;
    }
}
