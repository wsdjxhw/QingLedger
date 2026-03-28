# QingLedger 智能记账系统

> 一款融合传统记账与AI对话的创新记账应用

## ✨ 项目亮点

- 🤖 **AI对话记账** - 用自然语言记录消费,AI自动解析并记账
- 🎭 **智能反馈** - 4种人设可选(萌宠/理财顾问/损友/暖心陪伴)
- 👥 **多账本协作** - 支持个人账本和多人共享账本
- 🔐 **混合认证** - 手机号验证码 + 微信OAuth双登录
- 🔍 **智能搜索** - 基于Elasticsearch的复杂账目搜索

## 🛠️ 技术栈

- **后端框架**: Spring Boot 3.2 + Spring Security + MyBatis-Plus
- **数据库**: MySQL 8.0 + Redis 7.0
- **消息队列**: RabbitMQ 3.12
- **搜索引擎**: Elasticsearch 8.x
- **LLM集成**: DeepSeek + 智谱AI (混合方案)
- **API文档**: Knife4j 4.5
- **部署**: Docker + Docker Compose

## 📦 快速开始

### 环境要求

- JDK 17+
- Maven 3.6+
- MySQL 8.0+
- Redis 7.0+
- RabbitMQ 3.12+ (可选)

### 数据库初始化

```bash
# 1. 创建数据库
mysql -u root -p
CREATE DATABASE qing_ledger DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# 2. 执行初始化脚本
mysql -u root -p qing_ledger < database/migrations/V1.0.0__init_database.sql
```

### 配置修改

编辑 `src/main/resources/application.yml`,修改以下配置:

```yaml
spring:
  datasource:
    username: your_username
    password: your_password
  data:
    redis:
      host: localhost
      port: 6379
```

### 启动项目

```bash
# Maven构建
mvn clean install

# 运行应用
mvn spring-boot:run

# 或直接运行JAR
java -jar target/qing-ledger-1.0.0.jar
```

### 访问API文档

启动成功后,访问: http://localhost:8080/doc.html

## 📁 项目结构

```
QingLedger/
├── src/main/java/com/qingledger/
│   ├── config/          # 配置类
│   ├── controller/      # REST接口层
│   ├── service/         # 业务逻辑层
│   ├── mapper/          # MyBatis Mapper
│   ├── entity/          # 实体类
│   ├── common/          # 通用工具类
│   └── security/        # 安全相关
├── src/main/resources/
│   ├── mapper/          # MyBatis XML
│   └── application.yml  # 配置文件
├── database/migrations/  # 数据库迁移SQL
└── PROJECT_MEMORY.md    # 项目记忆文档
```

## 🚀 开发计划

- [x] 项目架构设计
- [x] 数据库表结构设计
- [ ] 用户认证系统
- [ ] 传统记账功能
- [ ] 对话式记账
- [ ] AI反馈系统
- [ ] 数据统计分析

## 📄 开源协议

MIT License

---

**开发者**: QingLedger Team
**项目记忆**: 详见 [PROJECT_MEMORY.md](./PROJECT_MEMORY.md)
