# Skill Hub Demo

技能管理系统后台，Java 17 + Spring Boot 3.1.5 + H2 内存数据库。

## 快速启动

```cmd
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
cd backend && gradlew.bat bootRun
```
前端：`cd frontend && npx http-server -p 3000` → `http://localhost:3000`

## 项目架构

```
controller/     ← 薄层：参数 → DTO → Biz → 响应包装
biz/            ← 业务核心：接口 + 实现，全部逻辑在此
domain/         ← 领域实体
dto/            ← 请求 DTO
dao/            ← MyBatis Mapper 接口
resources/dao/maps/ ← MyBatis XML 映射文件
service/        ← 可替换服务（接口 + Demo）
config/         ← Spring 配置、异常拦截
security/       ← 权限（接口 + Demo）
integration/    ← 变更推送（接口 + Demo）
common/exception/ ← 业务异常
util/           ← 工具类
```

## 状态机

```
DRAFT ──发布──→ PENDING_REVIEW ──通过──→ PUBLISHED ──下架──→ DELISTED
  ↑                │                    │                      │
  └── REJECTED ←──拒绝──┘               └──── 恢复 ───────────┘
```

## 文档目录

| 文档 | 用途 |
|------|------|
| [API-DOC.md](./API-DOC.md) | 完整接口文档：数据模型、全部 API、参数说明 |
| [DEVELOPMENT.md](./DEVELOPMENT.md) | 开发指南：分层规范、替换点详解、代码模板、状态机 |
| [TEST-CASES.md](./TEST-CASES.md) | 手动测试用例 |

## 核心业务规则

- **创建**：名称唯一，默认版本 1，可上传 packageFile（zip，需含 SKILL.md + YAML frontmatter）
- **发布 = 提交审批**：状态 → PENDING_REVIEW，创建 PublishRequest
- **审批通过**：版本号 +1，快照 name/description，文件沿用上一版本
- **审批拒绝**：记录理由，通知申请人，可重新提交
- **并发安全**：审批时比对 skillUpdatedAtSnapshot，不一致则自动过期
- **审批中编辑/保存**：自动作废旧审批
- **删除 vs 下架**：从未发布 → 物理删除；已发布 → 只能下架（逻辑删除，可恢复）
- **保存**：设为草稿，本已是草稿时不刷新 updatedAt

## 扩展点

- `PermissionService` → 替换 Demo 实现，对接真实认证框架
- `NotificationService` → 替换 Demo 实现，对接消息推送通道
- `SkillChangeListener` → 替换 Demo 实现，对接消息队列
- `SkillPackageValidator` → 替换 Demo 实现，自定义校验规则

## 测试

```cmd
gradlew test --tests "com.skillhub.SkillHubIntegrationTest"
```

18 个集成测试覆盖：创建、重名校验、编辑、保存、审批通过/拒绝、下架/恢复、导出、物理删除、并发校验、审批中保存作废。