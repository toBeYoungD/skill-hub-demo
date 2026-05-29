# Skill Hub Demo — 项目当前状态与新需求文档

> 本文档供新开发者 / 智能体接手开发使用，无需阅读代码。

---

## 一、项目概览

公司内部技能管理系统后台 Demo。Java 17 + Spring Boot 3.1.5 + Gradle 8.x + H2 内存数据库 + 本地文件存储。前端是原生 JS 单页测试页面（非正式产品）。

**项目路径：** `C:\Project\skill-demo`

**启动命令：**
```cmd
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
cd C:\Project\skill-demo\backend
gradlew.bat bootRun
```
前端另开终端：`cd frontend && npx http-server -p 3000`，浏览器访问 `http://localhost:3000`。

---

## 二、目录结构

```
skill-demo/
├── README.md
├── API-DOC.md                          # 已有的接口开发文档
├── backend/
│   ├── build.gradle                    # 阿里云 Maven 镜像
│   ├── settings.gradle
│   ├── gradlew / gradlew.bat           # Gradle Wrapper（自带，无需安装）
│   ├── gradle/wrapper/
│   └── src/main/java/com/skillhub/
│       ├── SkillHubApplication.java
│       ├── config/WebConfig.java       # CORS 全局配置（无 @CrossOrigin）
│       ├── controller/
│       │   ├── SkillController.java    # 技能 CRUD、发布、导出
│       │   ├── SkillVersionController.java  # 版本管理、回滚
│       │   └── CategoryController.java # 分类 CRUD
│       ├── service/
│       │   ├── SkillService.java       # 核心业务（创建、编辑、发布、回滚、导出、删除）
│       │   └── CategoryService.java
│       ├── repository/
│       │   ├── SkillRepository.java        # JpaRepository + JpaSpecificationExecutor
│       │   ├── SkillVersionRepository.java
│       │   └── CategoryRepository.java
│       ├── entity/
│       │   ├── Skill.java              # 含 @Transient getCanPublish()
│       │   ├── SkillVersion.java       # @JsonIgnore on skill，含快照字段
│       │   └── Category.java
│       ├── dto/request/
│       │   ├── SkillCreateRequest.java
│       │   ├── SkillUpdateRequest.java
│       │   └── SkillQueryRequest.java
│       └── util/
│           ├── LocalStorageUtil.java   # 本地文件存储，可替换为 OSS
│           └── VersionUtil.java        # 版本号生成（整数递增）与比较
│   └── src/main/resources/
│       ├── application.yml             # 端口 8080，H2 内存库，文件 500MB 限制
│       └── data.sql                    # 4 个默认分类
├── frontend/
│   ├── index.html                      # 首页/技能管理/分类管理 三个 Tab
│   ├── css/style.css
│   └── js/app.js                       # 全部前端逻辑
└── storage/                            # 本地文件存储（gitignore）
    ├── icons/
    ├── packages/
    └── manifests/
```

---

## 三、数据模型

### 3.1 Skill（技能）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | PK 自增 |
| name | String | NOT NULL, unique |
| description | String | |
| iconUrl | String | 图标本地路径 |
| packageUrl | String | 技能包本地路径 |
| status | enum | `DRAFT` / `PUBLISHED` / `ARCHIVED` |
| categoryId | Long | FK → Category |
| developer | String | |
| downloadCount | Integer | 默认 0 |
| useCount | Integer | 默认 0 |
| createdAt | LocalDateTime | @CreationTimestamp |
| updatedAt | LocalDateTime | 手动维护（创建/编辑时设为 now） |
| lastPublishedAt | LocalDateTime | 发布/回滚时设为 now |
| versions | List\<SkillVersion\> | @OneToMany, cascade ALL, orphanRemoval |
| canPublish | boolean (@Transient) | 计算规则见 4.3 |

### 3.2 SkillVersion（技能版本）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | PK |
| skill | Skill | @ManyToOne, @JsonIgnore |
| version | String | 整数 "1","2","3"... |
| packageUrl | String | |
| manifestUrl | String | 保留 |
| changelog | String | |
| status | enum | `DRAFT` / `PUBLISHED` |
| isLatest | boolean | |
| isRollback | boolean | |
| rolledBackFrom | String | |
| skillNameSnapshot | String | 发布时的技能名称 |
| skillDescriptionSnapshot | String | 发布时的技能描述 |
| iconUrlSnapshot | String | 发布时的图标路径 |
| createdAt | LocalDateTime | @CreationTimestamp |

### 3.3 Category（分类）

| 字段 | 类型 |
|------|------|
| id | Long PK |
| name | String (NOT NULL) |
| description | String |
| sortOrder | Integer (默认 0) |
| createdAt | LocalDateTime (@CreationTimestamp) |
| updatedAt | LocalDateTime (@UpdateTimestamp) |

---

## 四、核心业务规则

### 4.1 创建技能
- name 唯一，categoryId 必填
- 默认版本 "1"
- 可上传 iconFile、packageFile
- 状态初始为 `DRAFT`
- `updatedAt` 手动设为 now

### 4.2 编辑技能
- 支持 JSON body（无文件时）和 multipart/form-data（有文件时）两种 PUT
- 上传新文件自动替换旧文件（删旧存新）
- `updatedAt` 手动设为 now
- 重名校验排除自身

### 4.3 发布判定（canPublish）
```java
getCanPublish():
  if status == ARCHIVED → false
  if lastPublishedAt == null → true (从未发布)
  if updatedAt == null → false
  return updatedAt > lastPublishedAt
```

### 4.4 发布执行
- 版本号 +1（"1"→"2"→"3"...）
- 快照当前 name, description, iconUrl 到 SkillVersion
- 文件 packageUrl 沿用上一版本（或 skill 当前值）
- `lastPublishedAt` 设为 now
- 状态变为 `PUBLISHED`

### 4.5 版本回滚
- 只可回滚到 PUBLISHED 版本
- 创建新版本（版本号 +1，标记 isRollback）
- 恢复目标版本的：name, description, iconUrl, packageUrl
- `lastPublishedAt` 更新为 now

### 4.6 导出
- `GET /api/skills/{id}/export` 返回 ZIP
- 标准 Claude Skill 格式：`SKILL.md` + `scripts/package` + `resources/icon`

---

## 五、现有 API 汇总

**技能（/api/skills）**
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | / | 创建 multipart；name/description/developer/categoryId 必填 |
| GET | / | 列表 ?name=&status=&categoryId= |
| GET | /published | 已发布列表 |
| GET | /{id} | 详情（含版本列表） |
| PUT | /{id} | 更新，支持 JSON 和 multipart |
| DELETE | /{id} | 删除 + 关联文件 |
| POST | /{id}/publish | 发布新版本，参数 changelog |
| GET | /{id}/export | 导出 ZIP |
| POST | /{id}/download | 下载计数 +1 |
| POST | /{id}/use | 使用计数 +1 |

**版本（/api/skills/{id}/versions）**
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | / | 列表 |
| GET | /{vid} | 详情 |
| POST | /{vid}/rollback | 回滚 |
| DELETE | /{vid} | 删除（不能删最新已发布） |

**分类（/api/categories）**
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | / | 列表 |
| POST | / | name 必填 |
| GET | /{id} | |
| PUT | /{id} | |
| DELETE | /{id} | |

---

## 六、关键技术点

- **CORS**：只通过 WebConfig 全局配置，所有 controller 无 @CrossOrigin
- **categoryId 解析**：SkillController 有 `parseLong()` 方法，过滤 `null`/`""`/`"undefined"`
- **JSON 循环引用**：SkillVersion.skill 加 `@JsonIgnore`
- **updatedAt 管理**：没加 `@UpdateTimestamp`，在 create/update 方法中手动设
- **文件管理**：LocalStorageUtil，UUID 重命名，存储路径在 storage/ 下
- **Gradle**：build.gradle 配置了阿里云 Maven 镜像加速；Gradle Wrapper 版本 8.5

---

## 七、新需求——发布审批管理台

### 7.1 需求描述

当前点击"发布"直接创建新版本。需改为审批流程：

1. 用户点击"发布" → 提交审批申请 → 状态变为 `PENDING_REVIEW`
2. 管理员在管理台看到待审批列表 → 审批
3. 通过 → 执行发布逻辑 → 状态变为 `PUBLISHED`
4. 拒绝 → 记录拒绝理由 → 状态退回 `DRAFT`（或保持在 `PUBLISHED`）
5. 用户查自己技能时可看到审批状态和拒绝理由
6. 审批中用户编辑技能 → 之前申请自动作废
7. 每个版本发布都需要审批

### 7.2 状态流转

```
DRAFT → PENDING_REVIEW ──→ APPROVED → PUBLISHED
    ↑         │
    └─ REJECTED（带理由）
              │
         (用户可编辑 → 重新提交审批)

审批中用户编辑技能：
  PENDING_REVIEW 的申请 → 自动标记 CANCELLED，技能退回原状态
```

### 7.3 并发处理

**问题：** 管理员点"通过"同时用户编辑了技能。

**方案：** PublishRequest 存 `skillUpdatedAtSnapshot`（提交审批时的 skill.updatedAt）。审批通过时原子比对：

```
if (request.skillUpdatedAtSnapshot ≠ skill.updatedAt) {
    request.status = EXPIRED;
    request.rejectReason = "技能内容已变更，请重新提交";
    skill.status = 退回原状态;
} else {
    执行发布;
}
```

### 7.4 新实体 PublishRequest

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | PK |
| skillId | Long | |
| version | String | 申请的版本号 |
| changelog | String | |
| status | enum | `PENDING` / `APPROVED` / `REJECTED` / `CANCELLED` / `EXPIRED` |
| applicant | String | 申请人 |
| reviewer | String | 审批人 |
| rejectReason | String | 拒绝/作废/过期理由 |
| skillUpdatedAtSnapshot | LocalDateTime | 提交时的 skill.updatedAt（并发校验用） |
| createdAt | LocalDateTime | |
| reviewedAt | LocalDateTime | |

### 7.5 新增 API

```
# 用户端
POST /api/skills/{id}/submit-review        # 提交审批（替代原来的 publish）
GET  /api/skills/{id}/review-history       # 审批历史

# 管理台
GET  /api/admin/reviews?status=PENDING     # 待审列表
POST /api/admin/reviews/{id}/approve       # 通过
POST /api/admin/reviews/{id}/reject        # 拒绝（body: { reason: "xxx" }）
```

### 7.6 预留接口

**权限（demo 不限制，留接口给真实落地）：**
```java
// com.skillhub.auth.PermissionService (interface)
String currentUser();     // 获取当前用户
boolean isAdmin();        // 是否管理员
```
Demo 实现返回固定的 `"demo"` 和 `true`。

**消息推送（demo 只打日志）：**
```java
// com.skillhub.notify.NotificationService (interface)
void onReviewApproved(Long skillId, Long requestId);
void onReviewRejected(Long skillId, Long requestId, String reason);
```
Demo 实现只打 SLF4J 日志。

### 7.7 前端改动

- 现有三个 Tab（首页/技能管理/分类管理）新增第四个：**管理台**
- 管理台展示待审批列表，每条有"通过"/"拒绝"按钮
- 拒绝弹窗输入理由
- 技能卡片 `PENDING_REVIEW` 状态显示"审批中"，隐藏发布按钮
- 技能详情中显示审批历史

### 7.8 改动文件清单

| 文件 | 类型 | 内容 |
|------|:--:|------|
| `entity/PublishRequest.java` | 新 | 审批记录实体 |
| `repository/PublishRequestRepository.java` | 新 | |
| `service/PublishRequestService.java` | 新 | submit, approve, reject |
| `controller/ReviewController.java` | 新 | 管理台 API |
| `auth/PermissionService.java` | 新 | 权限预留接口 |
| `auth/DemoPermissionService.java` | 新 | Demo 实现 |
| `notify/NotificationService.java` | 新 | 通知预留接口 |
| `notify/LogNotificationService.java` | 新 | 日志实现 |
| `entity/Skill.java` | 改 | SkillStatus 加 PENDING_REVIEW；canPublish 加判断 |
| `service/SkillService.java` | 改 | 编辑时作废审批；canPublish 逻辑调整 |
| `controller/SkillController.java` | 改 | 发布接口改为提交审批 |
| `frontend/index.html` | 改 | 加管理台 Tab |
| `frontend/js/app.js` | 改 | 审批相关前端逻辑 |

---

## 八、注意事项

- H2 内存数据库，`ddl-auto: create-drop`，重启数据全部丢失
- 文件存储在 `storage/` 目录，gitignore 已排除
- 无需考虑分布式锁，单机 @Transactional 即可
- 版本号是纯整数字符串 "1","2","3"...，不是语义化版本
- 所有 API 返回统一格式 `{ "success": boolean, "message": "...", "data": ... }`
