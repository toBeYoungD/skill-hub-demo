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
controller/          # 薄层：参数 → DTO → Biz → 响应包装，无业务逻辑
biz/                 # 业务核心：接口 + 实现，全部逻辑在此
  SkillBiz / SkillBizImpl
  NotificationBiz / NotificationBizImpl
  IntegrationBiz / IntegrationBizImpl
  BizException
config/              # CORS、Encoding、异常拦截、拦截器
  GlobalExceptionHandler  # BizException → 400, 其他 → 500
auth/                # 权限接口 + Demo 实现（可替换）
  PermissionService / DemoPermissionService
integration/         # 变更推送接口 + Demo 实现（可替换）
  SkillChangeListener / LoggingSkillChangeListener
service/             # 可替换服务接口 + 实现
  NotificationService / NotificationServiceImpl
  SkillPackageValidator / DefaultSkillPackageValidator
  VisibilityService
entity/  repository/  util/  dto/
```

## 状态机

```
DRAFT → PENDING_REVIEW → PUBLISHED ─→ DELISTED
  ↑          │               │
  └── REJECTED ←─────────────┘
```

## API

### 技能 `/api/skills`
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | / | 创建 |
| GET | / | 列表 (?name=&status=) |
| GET | /{id} | 详情 |
| PUT | /{id} | 更新（JSON 或 multipart） |
| DELETE | /{id} | 物理删除（仅未发布过的 DRAFT/REJECTED） |
| POST | /{id}/save | 保存为草稿 |
| POST | /{id}/publish | 提交审批 |
| GET | /{id}/review-history | 审批历史 |
| GET | /{id}/export | 导出 ZIP（SKILL.md + scripts/package） |

### 版本 `/api/skills/{id}/versions`
| GET | / | 列表 | POST | /{vid}/rollback | 回滚 | DELETE | /{vid} | 删除 |

### 管理台 `/api/admin`
| GET | /reviews | 审批列表 | POST | /reviews/{id}/approve | 通过 | POST | /reviews/{id}/reject | 拒绝 | POST | /skills/{id}/delist | 下架 | POST | /skills/{id}/restore | 恢复 |

### 通知 `/api/notifications` | 集成 `/api/integration`

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