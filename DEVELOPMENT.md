# 项目开发指南

> 包含完整的分层规范、集成替换指引、代码模板、配置说明。

---

## 一、项目全貌

### 1.1 这是什么

一个 Skill Hub 技能管理系统后台。技能由开发者创建并上传技能包（zip），经管理员审批后发布，供用户安装使用。

### 1.2 技术栈

| 层 | 技术 |
|---|------|
| 语言 | Java 17 |
| 框架 | Spring Boot 3.1.5 |
| 数据库 | H2（可替换为 MySQL） |
| ORM | MyBatis 3.x + XML 映射 |
| 构建 | Gradle 8.x（Gradle Wrapper 自带） |
| 前端 | 原生 HTML/JS（仅用于开发期功能验证） |

### 1.3 目录结构

```
backend/src/main/java/com/skillhub/
├── SkillHubApplication.java        # 启动类
├── biz/                             # ★ 业务核心层 — 接口+实现
│   ├── SkillBiz.java               # 技能业务接口
│   ├── SkillBizImpl.java           # 技能业务实现（全部核心逻辑）
│   ├── NotificationBiz.java         # 通知业务接口
│   ├── NotificationBizImpl.java     # 通知业务实现
│   ├── IntegrationBiz.java          # 外部集成业务接口
│   ├── IntegrationBizImpl.java      # 外部集成业务实现
│   └── BizException.java            # 业务异常
├── controller/                      # ★ 控制层 — 薄层，只做参数→响应
│   ├── SkillController.java
│   ├── SkillVersionController.java
│   ├── ReviewController.java        # 管理台
│   ├── NotificationController.java
│   └── IntegrationController.java
├── domain/                          # ★ 领域实体
│   ├── Skill.java
│   ├── SkillVersion.java
│   ├── PublishRequest.java          # 审批记录
│   ├── Notification.java            # 通知记录
│   └── SkillChangeLog.java          # 变更日志
├── dto/                             # 请求 DTO
│   ├── SkillCreateRequest.java
│   ├── SkillUpdateRequest.java
│   ├── SkillQueryRequest.java
│   ├── DelistRequest.java
│   ├── RejectReviewRequest.java
│   ├── BatchCheckRequest.java
│   └── BatchCheckSkillItem.java
├── dao/                              # ★ MyBatis Mapper 接口
│   ├── SkillMapper.java
│   ├── SkillVersionMapper.java
│   ├── PublishRequestMapper.java
│   ├── NotificationMapper.java
│   └── SkillChangeLogMapper.java
├── service/                         # ★ 可替换服务 — 接口 + Demo 实现
│   ├── SkillPackageValidator.java       # 技能包校验接口
│   ├── DefaultSkillPackageValidator.java # 技能包校验 Demo 实现
│   ├── NotificationService.java        # 消息推送接口
│   ├── NotificationServiceImpl.java    # 消息推送 Demo 实现
│   └── VisibilityService.java          # 可见性判断
├── security/                        # ★ 权限 — 接口 + Demo 实现
│   ├── PermissionService.java
│   └── DemoPermissionService.java
├── integration/                     # ★ 外部推送 — 接口 + Demo 实现
│   ├── SkillChangeListener.java
│   └── LoggingSkillChangeListener.java
├── config/                          # Spring 配置
│   ├── WebConfig.java               # CORS + Interceptor
│   ├── UserInterceptor.java         # 请求拦截器
│   ├── GlobalExceptionHandler.java  # 统一异常处理
│   ├── TomcatConfig.java            # Tomcat 编码配置
│   └── MultipartConfig.java         # 文件上传配置
├── common/exception/                # 异常类
│   └── SkillPackageValidationException.java
└── util/                            # 工具
    ├── LocalStorageUtil.java        # 文件存储（可替换 OSS）
    └── VersionUtil.java             # 版本号工具
```

### 1.4 核心分层规则

```
Controller  →  Biz (interface)  →  BizImpl (@Service)  →  Repository / Util
  薄层           业务接口              全部业务逻辑             数据访问
```

**硬性规则：**
1. Controller 只做三件事：收参数、组装 DTO、调用 Biz。**禁止**在 Controller 中写任何业务判断。
2. 所有"可替换"的能力必须定义为 interface，放在 `service/`、`security/`、`integration/` 包下，提供 Demo 实现供开发调试。
3. 业务异常抛 `BizException`，由 `GlobalExceptionHandler` 统一转为 400 响应。Controller 不需要 try-catch。
4. 新增功能在 `biz/` 下定义接口和实现，在 `controller/` 下只写接入代码。

### 1.5 新增 Controller 模板

```java
@RestController
@RequestMapping("/api/xxx")
@RequiredArgsConstructor
public class XxxController {
    private final XxxBiz xxxBiz;  // 注入 Biz

    @GetMapping
    public ResponseEntity<Map<String, Object>> list() {
        return ResponseEntity.ok(Map.of("success", true, "data", xxxBiz.list()));
    }
}
```

### 1.6 新增 Biz 模板

```java
// 接口
public interface XxxBiz { List<Xxx> list(); }

// 实现
@Service @RequiredArgsConstructor
public class XxxBizImpl implements XxxBiz {
    private final XxxMapper xxxMapper;   // 注入 MyBatis Mapper

    @Override @Transactional
    public List<Xxx> list() { return xxxMapper.selectAll(); }
}
```

---

## 二、已有的完整功能清单

以下功能已**完全实现并可运行**，无需改动：

| 功能 | 涉及文件 |
|------|---------|
| 技能 CRUD | SkillBizImpl, SkillController |
| 创建时重名校验 | SkillBizImpl.create |
| 创建/更新时 zip 格式校验 | DefaultSkillPackageValidator |
| 保存草稿 + 审批中自动作废 | SkillBizImpl.saveAsDraft |
| 提交审批 | SkillBizImpl.submitReview |
| 审批通过（版本+1、快照、发布） | SkillBizImpl.approveReview |
| 审批拒绝（带理由、通知） | SkillBizImpl.rejectReview |
| 并发安全校验 | SkillBizImpl.approveReview 中的 snapshot 比对 |
| 审批中编辑/保存自动作废 | SkillBizImpl.update / saveAsDraft |
| 下架（逻辑删除 + 变更日志） | SkillBizImpl.delist |
| 恢复下架 | SkillBizImpl.restore |
| 物理删除（仅未发布过的） | SkillBizImpl.delete |
| 版本回滚（恢复快照） | SkillBizImpl.rollback |
| 删除版本（不能删最新已发布） | SkillBizImpl.deleteVersion |
| 导出标准 skill zip | SkillBizImpl.export |
| 审批历史查询 | SkillBizImpl.reviewHistory |
| 通知列表/未读/已读 | NotificationBizImpl |
| 批量检测下架/可升级 | IntegrationBizImpl.batchCheck |
| 增量变更日志 | IntegrationBizImpl.changelog |
| 文件存储（本地） | LocalStorageUtil |
| 版本号生成与比较 | VersionUtil |
| CORS + 请求拦截 | WebConfig, UserInterceptor |
| 全局异常处理 | GlobalExceptionHandler |
| 18 个集成测试 | SkillHubIntegrationTest |

---

## 三、Demo 实现说明（替换点）

以下是当前为 Demo 模式提供的默认实现，均标注了如何替换。

### 3.1 PermissionService — 权限

**接口：** `com.skillhub.security.PermissionService`

```java
public interface PermissionService {
    String currentUserId();       // 获取当前登录用户 ID
    boolean isAdmin();            // 是否管理员
    boolean isVisibleTo(String userId, String visibilityType, String visibilityConfig);
}
```

**当前 Demo 实现：** `DemoPermissionService`
- `currentUserId()` → 从请求头 `X-User-Id` 读取，无则返回 `"demo-user"`
- `isAdmin()` → 永远返回 `true`
- `isVisibleTo()` → `PUBLIC` 返回 true，`USER_LIST` 简单字符串匹配

**替换方法：**
1. 新建 `com.skillhub.security.ProductionPermissionService implements PermissionService`
2. `currentUserId()` → 对接认证框架（如 SSO Filter、JWT Token 等获取当前用户 ID）
3. `isAdmin()` → 对接角色系统
4. `isVisibleTo()` → 对接组织架构 API
5. 把 `@Service` 注解放到新实现上，删除或去除 `DemoPermissionService` 的 `@Service`

### 3.2 NotificationService — 消息推送

**接口：** `com.skillhub.service.NotificationService`

```java
public interface NotificationService {
    void notify(String userId, String event, Long skillId, String skillName, String message);
}
```

**当前 Demo 实现：** `NotificationServiceImpl`
- 写入 `notification` 表 + SLF4J 日志

**替换方法：**
1. 实现 `NotificationService`，调用邮件/IM/消息队列
2. 调用方（`SkillBizImpl`）无需改动，它只注入 `NotificationService` 接口
3. 如果通知不再需要从数据库查询，可删除 `NotificationController` 和 `NotificationRepository`

### 3.3 SkillChangeListener — 变更事件推送

**接口：** `com.skillhub.integration.SkillChangeListener`

```java
public interface SkillChangeListener {
    void onSkillDelisted(String skillName, String reason);
    void onSkillUpgraded(String skillName, String latestVersion);
}
```

**当前 Demo 实现：** `LoggingSkillChangeListener`
- 仅打 SLF4J 日志

**替换方法：**
1. 实现 `SkillChangeListener`，将事件发布到消息队列（Kafka/RocketMQ 等）
2. 下游服务消费这些事件，实时感知技能变化
3. `/api/integration/changelog` API 可保留作为兜底查询

### 3.4 SkillPackageValidator — 技能包校验

**接口：** `com.skillhub.service.SkillPackageValidator`

```java
public interface SkillPackageValidator {
    void validate(MultipartFile file) throws SkillPackageValidationException;
}
```

**当前 Demo 实现：** `DefaultSkillPackageValidator`
- 校验 zip 包含 SKILL.md + YAML frontmatter + name/description 字段

**替换方法：**
1. 如果内部技能包格式不同，实现新的 `SkillPackageValidator`
2. 在校验逻辑中加入自定义规则（如禁止特定关键字、检查文件大小等）

### 3.5 LocalStorageUtil — 文件存储

**位置：** `com.skillhub.util.LocalStorageUtil`

当前将文件存储在本地 `storage/` 目录。替换为 OSS 时：
1. 修改 `uploadIcon/uploadPackage/uploadManifest` 方法调用 OSS SDK
2. 修改 `deleteFile` 方法调用 OSS 删除
3. 修改 `getFileInputStream` 方法调用 OSS 下载
4. 接口签名保持不变，所有调用方无需改动

---

## 四、配置项清单

`application.yml` 中可配置的项：

```yaml
server.port: 8080                            # 服务端口
spring.datasource.url                         # 数据库连接（当前 H2，切换 MySQL 改这里）
spring.servlet.multipart.max-file-size: 500MB # 上传文件大小限制
storage.base-path: ../storage                 # 文件存储根目录
skill.visibility.enabled: false               # 可见性开关（true 时启用按部门/角色过滤）
qwenpaw.api-base-url: http://localhost:8088   # 快速测试代理地址
```

---

## 五、数据库

### 5.1 当前表

| 表名 | 对应实体 | 说明 |
|------|---------|------|
| skill | Skill | 技能主表 |
| skill_version | SkillVersion | 技能版本 |
| publish_request | PublishRequest | 审批记录 |
| notification | Notification | 通知记录 |
| skill_change_log | SkillChangeLog | 变更日志 |

### 5.2 切换 MySQL

1. 添加依赖到 `build.gradle`：`runtimeOnly 'com.mysql:mysql-connector-j'`
2. 修改 `application.yml`：
```yaml
spring.datasource:
  url: jdbc:mysql://localhost:3306/skill_hub?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
  driver-class-name: com.mysql.cj.jdbc.Driver
  username: xxx
  password: xxx
spring.sql.init.schema-locations: classpath:schema/schema-mysql.sql   # 使用 MySQL 方言的 DDL
```

### 5.3 H2 控制台

开发时访问 `http://localhost:8080/h2-console`，JDBC URL: `jdbc:h2:mem:skillhub`，用户 `sa`，密码空。

---

## 六、测试

### 运行测试

```cmd
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
cd backend
gradlew test --tests "com.skillhub.SkillHubIntegrationTest"
```

### 测试文件

`backend/src/test/java/com/skillhub/SkillHubIntegrationTest.java`

18 个测试，按 `@Order(1-18)` 顺序执行，覆盖：
创建、重名校验、编辑、保存、提交审批、防重复提交、审批拒绝+通知、审批通过+版本生成、未编辑不可发布、审批历史、下架、恢复、batch-check、导出、已发布者防物理删除、从不发布者可删除、审批中编辑过期、审批中保存过期。

### 添加新测试

在 `skillId` 变量指向的技能上追加 `@Test @Order(N)` 方法。

---

## 七、状态机

```
DRAFT ──发布──→ PENDING_REVIEW ──通过──→ PUBLISHED ──下架──→ DELISTED
  ↑                │                    │                      │
  └── REJECTED ←──拒绝──┘               └──── 恢复 ───────────┘
```

| 状态 | 可执行操作 |
|------|----------|
| DRAFT | 编辑、保存、发布（提交审批）、删除 |
| PENDING_REVIEW | 编辑/保存（会作废旧审批） |
| PUBLISHED | 编辑后发布（重新审批）、下架 |
| REJECTED | 编辑、重新发布 |
| DELISTED | 恢复 |

---

## 八、快速上手流程

### 若要在此基础上添加新功能：

1. 如需新实体 → 在 `domain/` 创建
2. 如需数据访问 → 在 `dao/` 创建 Mapper 接口 + 在 `resources/dao/maps/` 创建同名 XML
3. 如需业务逻辑 → 在 `biz/` 创建 `XxxBiz.java` + `XxxBizImpl.java`
4. 如需暴露 API → 在 `controller/` 创建 Controller，注入 Biz
5. 跑 `gradlew test` 确保不破坏已有测试
6. 如需新配置 → 加到 `application.yml`

### 跑起来验证：

```cmd
# 后端
cd backend && gradlew.bat bootRun

# 前端（另开终端）
cd frontend && npx http-server -p 3000

# 测试
gradlew test --tests "com.skillhub.SkillHubIntegrationTest"
```

浏览器打开 `http://localhost:3000` 即可看到完整界面。
