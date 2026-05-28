package com.skillhub.controller;

import com.skillhub.entity.Category;
import com.skillhub.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Slf4j
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> createCategory(
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "sortOrder", required = false) Integer sortOrder) {

        try {
            Category category = categoryService.createCategory(name, description, sortOrder);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "分类创建成功");
            response.put("data", category);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("创建分类失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "创建分类失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllCategories() {
        try {
            List<Category> categories = categoryService.getAllCategories();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", categories);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取分类列表失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取分类列表失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getCategory(@PathVariable Long id) {
        try {
            Category category = categoryService.getCategory(id);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", category);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取分类失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取分类失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateCategory(
            @PathVariable Long id,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) Integer sortOrder) {

        try {
            Category category = categoryService.updateCategory(id, name, description, sortOrder);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "分类更新成功");
            response.put("data", category);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("更新分类失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "更新分类失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteCategory(@PathVariable Long id) {
        try {
            categoryService.deleteCategory(id);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "分类删除成功");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("删除分类失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "删除分类失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}