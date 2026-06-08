# 贡献指南

[English](CONTRIBUTING.md)

感谢你考虑为本项目做出贡献！以下指南将帮助你快速上手。

## 开发环境

- **JDK**: 1.8+
- **构建工具**: Maven 3.6+
- **IDE**: 推荐 IntelliJ IDEA

```bash
git clone https://github.com/DiCraft-Jerry/dicraft-distributed-lock.git
cd dicraft-distributed-lock
mvn clean test
```

## 分支策略

| 分支 | 用途 |
|------|------|
| `master` | 仅用于稳定版本发布 |
| `develop` | 主开发分支 |
| `feature/*` | 新功能，从 `develop` 分出 |
| `fix/*` | Bug 修复，从 `develop` 分出 |

## 如何贡献

1. **Fork** 本仓库
2. 从 `develop` 创建功能分支：`git checkout -b feature/your-feature develop`
3. 编写代码并补充测试
4. 运行 `mvn clean test` 确保所有测试通过
5. 使用规范的提交信息提交（见下方）
6. 推送到你的 Fork 并向 `develop` 发起 **Pull Request**

## 提交信息规范

遵循 [Conventional Commits](https://www.conventionalcommits.org/) 格式：

```
<类型>: <简短描述>

<可选的详细说明>
```

**类型**: `feat`（新功能）、`fix`（修复）、`docs`（文档）、`refactor`（重构）、`test`（测试）、`chore`（杂项）

示例：
- `feat: add lock timeout callback support`
- `fix: resolve SpEL parsing failure for nested objects`
- `test: add concurrency tests for tryLock`

## 代码规范

- 遵循标准 Java 编码规范
- 所有公开 API 必须使用英文编写 Javadoc
- 注释和日志信息使用英文
- 避免引入不必要的依赖
- 线程安全的单例对象（如 `SpelExpressionParser`、`DefaultParameterNameDiscoverer`）应缓存为 `static final` 字段，避免每次调用重复创建

## 反馈问题

- 通过 [GitHub Issues](https://github.com/dicraft/dicraft-distributed-lock/issues) 报告 Bug 或提出功能建议
- Bug 报告请包含复现步骤、预期行为和实际行为
- 提交前请先搜索是否已有相同的 Issue

## 许可证

参与贡献即表示你同意你的贡献将按照 [Apache License 2.0](LICENSE) 许可证发布。
