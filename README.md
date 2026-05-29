# Skill Hub Demo

公司内部技能管理系统，Java 17 + Spring Boot 3.1.5 + H2。

## 启动

```cmd
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
cd backend && gradlew.bat bootRun
```
前端：`cd frontend && npx http-server -p 3000` → `http://localhost:3000`

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
| POST | / | 创建（name/description/developer 必填，packageFile 可选） |
| GET | / | 列表（?name=&status=） |
| GET | /{id} | 详情 |
| PUT | /{id} | 更新（JSON 或 multipart） |
| DELETE | /{id} | 删除（仅 DRAFT/REJECTED） |
| POST | /{id}/publish | 提交审批 |
| GET | /{id}/review-history | 审批历史 |
| GET | /{id}/export | 导出 ZIP |

### 管理台 `/api/admin`
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /reviews | 审批列表（?status=PENDING） |
| POST | /reviews/{id}/approve | 通过 |
| POST | /reviews/{id}/reject | 拒绝 `{reason}` |
| POST | /skills/{id}/delist | 下架 `{reason}` |
| POST | /skills/{id}/restore | 恢复 |

### 通知 `/api/notifications`
| GET | / | 列表 | POST | /{id}/read | 已读 | POST | /read-all |

### 集成 `/api/integration`
| POST | /batch-check | 批量检测下架/升级 |
| GET | /changelog?since= | 增量变更日志 |

## 预留接口

- `PermissionService` — 权限控制，Demo 实现从 `X-User-Id` header 读取，所有人均为管理员
- `NotificationService` — 消息推送，Demo 存数据库
- `SkillChangeListener` — 变更事件，Demo 打日志
- `SkillPackageValidator` — 上传格式校验（zip 须含 SKILL.md + YAML frontmatter）

## 文件存储

`storage/` 目录，分 icons/packages/manifests 子目录。UUID 重命名。