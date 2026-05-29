# Skill Hub 开发文档

## 架构分层

```
Controller  ──→  Biz (interface)  ──→  BizImpl (@Service)  ──→  Repository / Util
  薄层             业务接口               全部业务逻辑               数据访问
```

**硬性规则：**
- Controller **只做**：参数校验、DTO 组装、调用 Biz、响应包装。**禁止**在里面写业务判断。
- Biz 层**必须**有 interface，`@Service` 打在 Impl 上。
- 所有业务异常抛 `BizException`，由 `GlobalExceptionHandler` 统一转换为 400 响应。
- 预留外部替换的组件（权限、通知、变更推送）放在 `service/`、`auth/`、`integration/` 包下，定义为 interface + Demo 实现。

## 开发新功能步骤

1. 在 `biz/` 下定义 interface 方法
2. 在 `biz/` 下写 Impl 实现
3. 在 `controller/` 下写 endpoint，注入 Biz，只做参数→调用→响应
4. 如需新实体：`entity/` → `repository/`
5. 跑 `gradlew test` 确认不破坏已有测试
6. 为新功能补测试用例

## 新增 Controller

```java
@RestController
@RequestMapping("/api/xxx")
@RequiredArgsConstructor
public class XxxController {
    private final XxxBiz xxxBiz;  // 注入 Biz，不直接注入 Repository

    @GetMapping
    public ResponseEntity<Map<String, Object>> list() {
        return ResponseEntity.ok(Map.of("success", true, "data", xxxBiz.list()));
    }
}
```

BizException 无需 try-catch，GlobalExceptionHandler 自动处理。

## 新增 Biz

```java
// interface
public interface XxxBiz {
    List<Xxx> list();
}

// impl
@Service
@RequiredArgsConstructor
public class XxxBizImpl implements XxxBiz {
    private final XxxRepository repo;

    @Override
    @Transactional
    public List<Xxx> list() {
        return repo.findAll();
    }
}
```

## 测试

```cmd
gradlew test --tests "com.skillhub.SkillHubIntegrationTest"
```

测试文件：`src/test/java/com/skillhub/SkillHubIntegrationTest.java`
使用 `@SpringBootTest` + `@AutoConfigureMockMvc`，按 `@Order` 顺序执行。
每个测试方法独立不依赖外部状态（H2 内存库自动重建）。

## 关键类速查

| 层 | 类 | 职责 |
|----|----|------|
| Biz | SkillBiz / SkillBizImpl | 技能 CRUD、审批流、下架恢复、导出 |
| Biz | NotificationBiz / NotificationBizImpl | 通知查询、已读 |
| Biz | IntegrationBiz / IntegrationBizImpl | batch-check、changelog |
| Config | GlobalExceptionHandler | BizException → 400 |
| Config | WebConfig | CORS、UserInterceptor |
| Auth | PermissionService / DemoPermissionService | 用户身份、管理员判定 |
| Integration | SkillChangeListener / LoggingSkillChangeListener | 变更事件推送 |
| Service | NotificationService / NotificationServiceImpl | 内部通知写入 |
| Service | SkillPackageValidator / DefaultSkillPackageValidator | Zip 格式校验 |
| Service | VisibilityService | 技能可见性判定 |

## 状态机

```
DRAFT → PENDING_REVIEW → PUBLISHED → DELISTED
  ↑          │               │          ↑
  └── REJECTED ←─────────────┘     (可恢复)
```

- DRAFT：仅自己可见，可编辑、可删除
- PENDING_REVIEW：审批中，不可再次提交
- PUBLISHED：对范围内用户可见
- REJECTED：审批未通过，可重新提交
- DELISTED：已下架，不可见，可恢复

## 并发安全

PublishRequest 存 `skillUpdatedAtSnapshot`。审批通过时比对：若 skill.updatedAt 已变（期间被编辑），自动拒绝并标记过期。

审批中执行编辑或保存操作，会调用 `cancelPending()` 作废该 skill 的所有待审批申请。