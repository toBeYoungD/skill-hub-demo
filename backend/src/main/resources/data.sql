-- 创建默认分类
INSERT INTO category (name, description, sort_order, created_at, updated_at) VALUES
('工具类', '各类实用工具', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('数据处理', '数据处理和分析工具', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('界面增强', '界面美化和增强', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('开发辅助', '开发相关的辅助工具', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);