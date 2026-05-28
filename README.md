# Skill Hub Demo

公司内部技能管理系统，基于 Java 17 + Spring Boot 3 + H2 内存数据库。

## 项目结构

```
skill-demo/
├── backend/                         # Java 后端
│   ├── src/main/java/com/skillhub/
│   │   ├── SkillHubApplication.java # 启动类
│   │   ├── entity/                  # Skill, SkillVersion, Category
│   │   ├── dto/request/             # 请求 DTO
│   │   ├── repository/             # JPA Repository
│   │   ├── service/                # SkillService, CategoryService
│   │   ├── controller/             # REST API
│   │   ├── config/                 # WebConfig (CORS)
│   │   └── util/                   # LocalStorageUtil, VersionUtil
│   └── src/main/resources/
│       ├── application.yml         # 配置
│       └── data.sql               # 初始分类数据
│
├── frontend/                       # 原生 JS 前端（用于测试）
│   ├── index.html
│   ├── css/style.css
│   └── js/app.js
│
└── storage/                        # 本地文件存储（图标、技能包）
    ├── icons/
    ├── packages/
    └── manifests/
```

## 启动

```cmd
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
cd backend
gradlew.bat bootRun
```

前端（另开终端）：
```cmd
cd frontend
npx http-server -p 3000
```

浏览器访问 `http://localhost:3000`。

## API

### 技能
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/skills` | 创建技能 |
| GET | `/api/skills` | 查询列表（?name=&status=&categoryId=） |
| GET | `/api/skills/{id}` | 技能详情 |
| PUT | `/api/skills/{id}` | 更新技能（JSON 或 multipart） |
| DELETE | `/api/skills/{id}` | 删除技能 |
| POST | `/api/skills/{id}/publish` | 发布新版本 |
| GET | `/api/skills/{id}/export` | 导出为 .zip（标准 skill 格式） |

### 版本
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/skills/{id}/versions` | 版本列表 |
| POST | `/api/skills/{id}/versions/{vid}/rollback` | 回滚到此版本 |
| DELETE | `/api/skills/{id}/versions/{vid}` | 删除版本 |

### 分类
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/categories` | 分类列表 |
| POST | `/api/categories` | 创建分类 |
| PUT | `/api/categories/{id}` | 更新分类 |
| DELETE | `/api/categories/{id}` | 删除分类 |

## 核心业务规则

- **创建**：名称必填且唯一，分类必填，版本默认 `1`
- **发布**：只有编辑过内容（`updatedAt > lastPublishedAt`）才能发布，每次发布版本号 +1
- **回滚**：恢复到目标版本的名称、描述、图标、技能包，并创建新版本记录
- **文件**：创建/编辑时上传，发布时沿用上一版本文件，导出时按标准 skill 目录结构打包
- **导出**：输出标准 skill ZIP 包（SKILL.md + scripts/ + resources/）

## 数据库

H2 内存数据库，`ddl-auto: create-drop`，每次重启数据重置。

H2 控制台：`http://localhost:8080/h2-console`，JDBC URL `jdbc:h2:mem:skillhub`，用户名 `sa`，密码空。

## 文件存储

上传的文件保存在项目根目录下的 `storage/` 目录，按类型分 icons / packages / manifests 三个子目录。