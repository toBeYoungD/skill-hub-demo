# Skill Hub 接口开发文档

## 1. 项目概述

技能管理系统后台，Java 17 + Spring Boot 3.1.5 + H2 内存数据库。

### 项目架构

```
controller/     ← 薄层：参数→DTO→Biz→响应包装，无业务逻辑
biz/            ← 业务核心：接口+实现，全部逻辑在此
domain/         ← 领域实体（Skill, SkillVersion, PublishRequest, Notification, SkillChangeLog）
dto/            ← 请求/响应 DTO
repository/     ← JPA 数据访问
service/        ← 可替换服务接口+实现
config/         ← Spring 配置、CORS、拦截器、全局异常处理
security/       ← 权限接口+Demo 实现（可替换）
integration/    ← 变更推送接口+Demo 实现（可替换）
common/exception/ ← 特定异常
util/           ← 工具类
```

### 启动

```cmd
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
cd backend && gradlew.bat bootRun
```

前端测试页：`cd frontend && npx http-server -p 3000` → `http://localhost:3000`

---

## 2. 数据模型

### 2.1 Skill（技能） — `com.skillhub.domain.Skill`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键，自增 |
| name | String | 名称，唯一 |
| description | String | 描述 |
| packageUrl | String | 技能包本地路径 |
| status | enum | DRAFT / PENDING_REVIEW / PUBLISHED / REJECTED / DELISTED |
| developer | String | 开发者 |
| downloadCount | Integer | 下载次数 |
| useCount | Integer | 使用次数 |
| visibilityType | String | PUBLIC / USER_LIST / DEPARTMENT / ROLE |
| visibilityConfig | String | JSON 配置 |
| delisted | Boolean | 逻辑删除标记 |
| delistedReason | String | 下架原因 |
| delistedAt | LocalDateTime | 下架时间 |
| delistedBy | String | 下架操作人 |
| createdAt | LocalDateTime | 创建时间（@CreationTimestamp） |
| updatedAt | LocalDateTime | 更新时间（手动维护） |
| lastPublishedAt | LocalDateTime | 最近发布时间 |
| versions | List\<SkillVersion\> | 版本列表（一对多，级联） |
| canPublish | boolean (@Transient) | 计算字段：updatedAt > lastPublishedAt 或从未发布 |

### 2.2 SkillVersion（技能版本） — `com.skillhub.domain.SkillVersion`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| skill | Skill | 所属技能（@JsonIgnore） |
| version | String | 版本号，整数（1, 2, 3...） |
| packageUrl | String | 该版本技能包路径 |
| changelog | String | 更新日志 |
| status | enum | DRAFT / PUBLISHED |
| isLatest | boolean | 是否最新 |
| isRollback | boolean | 是否回滚版本 |
| rolledBackFrom | String | 回滚来源版本号 |
| skillNameSnapshot | String | 发布时的名称快照 |
| skillDescriptionSnapshot | String | 发布时的描述快照 |
| createdAt | LocalDateTime | @CreationTimestamp |

### 2.3 PublishRequest（审批记录） — `com.skillhub.domain.PublishRequest`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| skillId | Long | 技能 ID |
| skillName | String | 技能名称快照 |
| changelog | String | 变更说明 |
| status | enum | PENDING / APPROVED / REJECTED |
| applicant | String | 申请人 |
| reviewer | String | 审批人 |
| rejectReason | String | 拒绝理由 |
| skillUpdatedAtSnapshot | LocalDateTime | 提交时 skill.updatedAt（并发校验） |
| createdAt | LocalDateTime | @CreationTimestamp |
| reviewedAt | LocalDateTime | 审批时间 |

### 2.4 Notification（通知） — `com.skillhub.domain.Notification`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| userId | String | 目标用户 |
| event | String | APPROVED / REJECTED / DELISTED |
| skillId | Long | 技能 ID |
| skillName | String | 技能名称 |
| message | String | 通知内容 |
| read | Boolean | 是否已读 |
| createdAt | LocalDateTime | @CreationTimestamp |

### 2.5 SkillChangeLog（变更日志） — `com.skillhub.domain.SkillChangeLog`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| skillName | String | 技能名称 |
| changeType | String | DELISTED / UPGRADED |
| details | String (JSON) | 变更详情 |
| createdAt | LocalDateTime | @CreationTimestamp |

---

## 3. API 接口

基础路径：`http://localhost:8080/api`

### 3.1 技能管理 — `SkillController` → `SkillBiz`

#### 创建技能
```
POST /api/skills
Content-Type: multipart/form-data
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| name | String | ✅ | 名称，不可重复 |
| description | String | ✅ | 描述 |
| developer | String | ✅ | 开发者 |
| version | String | ❌ | 版本号，默认 "1" |
| packageFile | File | ❌ | 技能包（.zip，须含 SKILL.md） |
| visibilityType | String | ❌ | 默认 PUBLIC |
| visibilityConfig | String | ❌ | |

创建后状态为 `DRAFT`，不会生成版本记录。上传的 zip 会经过 SkillPackageValidator 校验。

#### 查询技能列表
```
GET /api/skills?name=&status=
```

| 参数 | 说明 |
|------|------|
| name | 模糊搜索 |
| status | DRAFT / PENDING_REVIEW / PUBLISHED / REJECTED / DELISTED |

#### 获取技能详情
```
GET /api/skills/{id}
```

返回完整数据，包括所有版本历史、审批历史。

#### 更新技能
```
PUT /api/skills/{id}
```

支持两种 Content-Type：
- `application/json`：`{"name":"xxx","description":"xxx"}`
- `multipart/form-data`：支持 name、description、packageFile，全部可选

若技能处于 `PENDING_REVIEW`，编辑后自动作废旧审批申请。

#### 保存为草稿
```
POST /api/skills/{id}/save
```

若本已是 `DRAFT`，不刷新 `updatedAt`。若处于 `PENDING_REVIEW`，自动作废旧审批。

#### 提交审批（发布）
```
POST /api/skills/{id}/publish
Content-Type: multipart/form-data
```

| 参数 | 说明 |
|------|------|
| changelog | 更新日志（可选） |

状态变为 `PENDING_REVIEW`，创建 PublishRequest。只有 `canPublish=true` 时才能提交。

#### 查看审批历史
```
GET /api/skills/{id}/review-history
```

#### 删除技能
```
DELETE /api/skills/{id}
```

只能物理删除从未发布过的 DRAFT/REJECTED 状态技能。已发布过的只能下架。

#### 导出技能
```
GET /api/skills/{id}/export
```

返回 ZIP，结构：`SKILL.md + scripts/package`。

#### 其他
```
POST /api/skills/{id}/download    # 下载计数 +1
POST /api/skills/{id}/use         # 使用计数 +1
GET  /api/skills/published        # 已发布且未下架的技能
```

### 3.2 版本管理 — `SkillVersionController` → `SkillBiz`

基础路径：`/api/skills/{skillId}/versions`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | / | 版本列表（按版本号降序） |
| GET | /{versionId} | 版本详情 |
| POST | /{versionId}/rollback | 回滚到此版本（创建新版本，恢复快照数据） |
| DELETE | /{versionId} | 删除版本（不能删最新已发布版本） |

### 3.3 管理台 — `ReviewController` → `SkillBiz`

基础路径：`/api/admin`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /reviews?status=PENDING | 审批列表 |
| POST | /reviews/{id}/approve | 审批通过（执行发布逻辑，版本号+1） |
| POST | /reviews/{id}/reject | 审批拒绝（body: `{"reason":"xxx"}`） |
| POST | /skills/{id}/delist | 下架技能（body: `{"reason":"xxx"}`） |
| POST | /skills/{id}/restore | 恢复下架技能 |

### 3.4 通知 — `NotificationController` → `NotificationBiz`

基础路径：`/api/notifications`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | / | 当前用户通知列表 |
| GET | /unread-count | 未读数量 |
| POST | /{id}/read | 标记已读 |
| POST | /read-all | 全部已读 |

### 3.5 外部集成 — `IntegrationController` → `IntegrationBiz`

基础路径：`/api/integration`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /batch-check | 批量检测下架/可升级 |
| GET | /changelog?since=ISO时间戳 | 增量变更日志 |

---

## 4. 状态机与业务规则

```
DRAFT ──发布──→ PENDING_REVIEW ──通过──→ PUBLISHED ──下架──→ DELISTED
  ↑                │                    │                      │
  └── REJECTED ←──拒绝──┘               └──── 恢复 ───────────┘
```

| 规则 | 说明 |
|------|------|
| 名称唯一性 | 创建/编辑时校验，编辑排除自身 |
| 发布判定 | canPublish = updatedAt > lastPublishedAt 或从未发布 |
| 版本递增 | 审批通过时版本号 +1（1→2→3） |
| 文件沿用 | 发布时新版 packageUrl 复制自上一版本或 skill 当前值 |
| 快照 | 发布时存 name/description 到 SkillVersion，回滚时恢复 |
| 并发安全 | 审批时比对 skillUpdatedAtSnapshot，不一致则自动过期 |
| 审批中编辑/保存 | 自动作废旧审批，技能回到 DRAFT |
| 删除 vs 下架 | 从未发布→物理删除；发布过→只能下架 |
| 下架 | 逻辑删除（delisted=true），写变更日志，通知开发者 |

---

## 5. 预留接口

| 接口 | 包 | 说明 |
|------|-----|------|
| PermissionService | security/ | 权限控制，Demo 从 X-User-Id header 获取用户 |
| NotificationService | service/ | 消息推送，Demo 存数据库 |
| SkillChangeListener | integration/ | 变更事件，Demo 打日志 |
| SkillPackageValidator | service/ | 上传格式校验 |

实现这些接口，替换掉 Demo 实现即可。

---

## 6. 文件存储

`LocalStorageUtil`（`util/` 包），路径规则：

```
storage/
├── icons/       icon_{uuid}.ext
├── packages/    package_{uuid}.ext
└── manifests/   manifest_{uuid}.ext
```

替换为 OSS 只需修改此类，接口签名不变。

---

## 7. 数据库

H2 内存数据库，`ddl-auto: create-drop`，每次重启数据重置。

切换 MySQL：修改 `application.yml` 中 `datasource` 配置，`ddl-auto` 改为 `update`。

---

## 8. 异常处理

- `BizException`（`biz/` 包）：业务通用异常，由 GlobalExceptionHandler 统一抓取返回 400
- `SkillPackageValidationException`（`common/exception/` 包）：技能包校验失败异常

Controller 层不需要自己 try-catch。

---

## 9. 测试

```cmd
gradlew test --tests "com.skillhub.SkillHubIntegrationTest"
```

18 个集成测试覆盖：创建、重名校验、编辑、保存、审批通过/拒绝、下架/恢复、导出、物理删除、并发校验、审批中保存作废。