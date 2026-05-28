package com.skillhub.service;

import com.skillhub.entity.Category;
import com.skillhub.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional
    public Category createCategory(String name, String description, Integer sortOrder) {
        Category category = new Category();
        category.setName(name);
        category.setDescription(description);
        category.setSortOrder(sortOrder != null ? sortOrder : 0);

        Category savedCategory = categoryRepository.save(category);
        log.info("创建分类成功: {}", name);
        return savedCategory;
    }

    public List<Category> getAllCategories() {
        return categoryRepository.findAllByOrderBySortOrderAsc();
    }

    public Category getCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("分类不存在"));
    }

    @Transactional
    public Category updateCategory(Long id, String name, String description, Integer sortOrder) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("分类不存在"));

        if (name != null && !name.isEmpty()) {
            category.setName(name);
        }
        if (description != null) {
            category.setDescription(description);
        }
        if (sortOrder != null) {
            category.setSortOrder(sortOrder);
        }

        Category updatedCategory = categoryRepository.save(category);
        log.info("更新分类成功: {}", updatedCategory.getName());
        return updatedCategory;
    }

    @Transactional
    public void deleteCategory(Long id) {
        categoryRepository.deleteById(id);
        log.info("删除分类成功: {}", id);
    }
}