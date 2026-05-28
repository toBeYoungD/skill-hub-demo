# Skill Hub 接口开发文档

## 1. 项目概述

公司内部技能管理系统后台，基于 Java 17 + Spring Boot 3.1.5，前端仅作功能验证用途。核心功能包括技能 CRUD、版本发布与回滚、分类管理、文件存储和标准格式导出。

### 技术栈

| 层级 | 技术 |
|------|------|
| 语言 | Java 17 |
| 框架 | Spring Boot 3.1.5 + Spring Data JPA |
| 数据库 | H2 内存数据库（可替换为 MySQL） |
| 文件存储 | 本地文件系统（可替换为 OSS） |
| 构建 | Gradle 8.x（Gradle Wrapper 自带，无需手动安装） |

### 启动

```cmd
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
cd backend
gradlew.bat bootRun
```

服务启动在 `http://localhost:8080`。前端测试页单独启动：

```cmd
cd frontend
npx http-server -p 3000
```

---

## 2. 数据模型

### 2.1 Skill（技能）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键，自增 |
| name | String | 技能名称，唯一 |
| description | String | 描述 |
| iconUrl | String | 图标文件相对路径 |
| packageUrl | String | 技能包文件相对路径 |
| status | enum | DRAFT / PUBLISHED / ARCHIVED |
| categoryId | Long | 关联分类 ID |
| developer | String | 开发者 |
| downloadCount | Integer | 下载次数 |
| useCount | Integer | 使用次数 |
| createdAt | LocalDateTime | 创建时间（自动） |
| updatedAt | LocalDateTime | 最后编辑时间（手动维护） |
| lastPublishedAt | LocalDateTime | 最后发布时间 |
| versions | List\<SkillVersion\> | 版本列表（一对多，级联） |
| canPublish | boolean | `@Transient` 计算字段，不作为列存储 |

### 2.2 SkillVersion（技能版本）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| skill | Skill | 所属技能（ManyToOne，序列化时忽略） |
| version | String | 版本号，整数（1, 2, 3...） |
| packageUrl | String | 该版本技能包路径 |
| manifestUrl | String | 保留字段 |
| changelog | String | 更新日志 |
| status | enum | DRAFT / PUBLISHED |
| isLatest | boolean | 是否最新版本 |
| isRollback | boolean | 是否回滚版本 |
| rolledBackFrom | String | 回滚来源版本号 |
| skillNameSnapshot | String | 发布时的技能名称快照 |
| skillDescriptionSnapshot | String | 发布时的技能描述快照 |
| iconUrlSnapshot | String | 发布时的图标快照 |
| createdAt | LocalDateTime | 创建时间 |

### 2.3 Category（分类）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| name | String | 分类名称 |
| description | String | 描述 |
| sortOrder | Integer | 排序 |
| createdAt / updatedAt | LocalDateTime | 自动时间戳 |

---

## 3. API 接口

基础路径：`http://localhost:8080/api`

所有接口统一返回格式：

```json
{ "success": true, "message": "xxx", "data": {...} }
// 或
{ "success": false, "message": "错误信息" }
```

### 3.1 技能管理

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
| categoryId | Long | ✅ | 分类 ID |
| version | String | ❌ | 默认 "1" |
| iconFile | File | ❌ | 图标 |
| packageFile | File | ❌ | 技能包 |

创建后状态为 `DRAFT`，不会生成版本记录。

**响应示例：**

```json
{
  "success": true,
  "message": "技能创建成功",
  "data": {
    "id": 1,
    "name": "数据转换工具",
    "description": "格式转换插件",
    "status": "DRAFT",
    "developer": "张三",
    "iconUrl": "icons/icon_xxxx.png",
    "packageUrl": "packages/package_xxxx.zip",
    "versions": [],
    "canPublish": true
  }
}
```

#### 查询技能列表

```
GET /api/skills?name=&status=&categoryId=
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| name | String | ❌ | 模糊搜索 |
| status | String | ❌ | DRAFT / PUBLISHED / ARCHIVED |
| categoryId | Long | ❌ | 分类筛选 |

#### 获取技能详情

```
GET /api/skills/{id}
```

返回完整数据，包括所有版本历史。

#### 更新技能

```
PUT /api/skills/{id}
```

支持两种请求方式：

**无文件更新（JSON）：**

```
Content-Type: application/json
```

```json
{ "name": "xxx", "description": "xxx", "categoryId": "1" }
```

**有文件更新（multipart）：**

```
Content-Type: multipart/form-data
```

参数同创建接口，全部可选。上传新文件会自动替换旧文件。

> 更新成功后 `updatedAt` 被修改，`canPublish` 变为 `true`。

#### 删除技能

```
DELETE /api/skills/{id}
```

同时删除关联的本地文件。

#### 发布技能

```
POST /api/skills/{id}/publish
Content-Type: multipart/form-data
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| changelog | String | ❌ | 更新日志 |

发布逻辑：
1. 版本号自动 +1（如 1 → 2 → 3）
2. 快照当前技能的名称、描述、图标
3. 文件沿用上一版本
4. `lastPublishedAt` 设为当前时间，`canPublish` 变为 `false`

> 只有 `canPublish = true` 时才能发布（即上次发布后有编辑过）。

#### 导出技能

```
GET /api/skills/{id}/export
```

返回 ZIP 文件，目录结构：

```
skill-name/
├── SKILL.md          # YAML frontmatter + Markdown 正文
├── scripts/
│   └── package       # 技能包文件
└── resources/
    └── icon          # 图标文件
```

#### 其他

```
POST /api/skills/{id}/download    # 下载次数 +1
POST /api/skills/{id}/use         # 使用次数 +1
GET  /api/skills/published        # 获取所有已发布技能
```

### 3.2 版本管理

基础路径：`/api/skills/{skillId}/versions`

#### 版本列表

```
GET /api/skills/{skillId}/versions
```

已发布版本在前，按版本号降序排列。

#### 版本详情

```
GET /api/skills/{skillId}/versions/{versionId}
```

#### 回滚到指定版本

```
POST /api/skills/{skillId}/versions/{versionId}/rollback
```

回滚逻辑：
1. 只能回滚到已发布版本
2. 创建新版本，版本号 +1，标记 `isRollback = true`
3. 恢复目标版本的：名称、描述、图标、技能包
4. 新版本文件链接指向目标版本的文件
5. `lastPublishedAt` 更新

#### 删除版本

```
DELETE /api/skills/{skillId}/versions/{versionId}
```

不能删除当前最新且已发布的版本。

### 3.3 分类管理

基础路径：`/api/categories`

```
GET    /api/categories              # 列表（按 sortOrder 升序）
POST   /api/categories              # 创建（name 必填，description/sortOrder 可选）
GET    /api/categories/{id}         # 详情
PUT    /api/categories/{id}         # 更新
DELETE /api/categories/{id}         # 删除
```

---

## 4. 业务规则

| 规则 | 实现位置 |
|------|----------|
| 名称唯一性 | `SkillRepository.existsByName()`, service 中校验 |
| 分类必填 | Controller 中 `@RequestParam("categoryId")` 不带 `required=false` |
| 默认版本 "1" | Controller 中 `defaultValue = "1"` |
| 发布判定 | `Skill.getCanPublish()` — `updatedAt > lastPublishedAt` 或从未发布 |
| 时间戳管理 | `updatedAt` 在 create/update 中手动设，发布不变；`lastPublishedAt` 仅在发布时设 |
| 版本号递增 | `VersionUtil.generateNextVersion()` — 当前版本 +1 |
| 回滚快照 | 发布时存 name/description/iconUrl 到版本；回滚时写回 skill |
| 文件沿用 | 发布时新版 `packageUrl` 复制自上一版或 skill 当前值 |

---

## 5. 文件存储

`LocalStorageUtil` 管理文件，路径规则：

```
storage/
├── icons/       icon_{uuid}.ext     # 技能图标
├── packages/    package_{uuid}.ext  # 技能包
└── manifests/   manifest_{uuid}.ext # 保留
```

上传时生成 UUID 文件名，保存相对路径到实体。删除技能或版本时同步删除文件。

### 替换为 OSS

改动仅需修改 `LocalStorageUtil`，保持接口签名不变：

```java
public String uploadIcon(MultipartFile file)   → OSS 上传
public String uploadPackage(MultipartFile file) → OSS 上传
public void deleteFile(String relativePath)     → OSS 删除
public InputStream getFileInputStream(String path) → OSS 下载
```

---

## 6. 数据库

默认使用 H2 内存数据库，`ddl-auto: create-drop`，启动自动建表，`data.sql` 插入 4 个默认分类。

### 切换为 MySQL

修改 `application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/skill_hub?useUnicode=true&characterEncoding=utf8
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: root
    password: your_password
  jpa:
    hibernate:
      ddl-auto: update   # 不删表
```

并添加 MySQL 驱动依赖到 `build.gradle`。

---

## 7. 项目扩展指南

### 字段 add/replace 表

所有以 `skill_` 为前缀的属性在 Skill 实体（`com.skillhub.entity.Skill`），以 `version_` 为前缀的在 SkillVersion 实体。

### 接入权限系统

在 Controller 方法上添加自定义注解即可：

```java
@GetMapping("/{id}")
@RequirePermission("skill:view")   // 示例
public ResponseEntity<...> getSkill(@PathVariable Long id) { ... }
```

### 添加搜索功能

预留方式：`SkillRepository` 已继承 `JpaSpecificationExecutor`，可在 Service 中构建复杂查询条件，无需改 Controller。

### 接入 Redis 缓存

在 Service 层对 `getSkillDetail`、`getPublishedSkills` 等方法加 `@Cacheable` 注解即可。

---

## 8. 常见错误码

| 错误 | 原因 |
|------|------|
| `技能名称已存在` | 创建/编辑时名称重复 |
| `分类不存在` | categoryId 指向不存在的分类 |
| `不能删除最新的已发布版本` | 版本保护规则 |
| `只能回滚到已发布的版本` | 回滚目标必须是 PUBLISHED 状态 |
| `Maximum upload size exceeded` | 文件超过 500MB 限制，改 `application.yml` 中 `max-file-size` |