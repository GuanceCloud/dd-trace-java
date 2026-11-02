# 观测云插桩构建遗漏排查

排查基于 `guance` 的 `fdcf9a2c1e`（`v1.68.1-ext`），比较合并提交与合并前的观测云父提交。

## 历史记录

2025-09-15，合并提交 `27cea6f949`（`build 1.53.0 ok`）切换到
`settings.gradle.kts`，没有保留原 `settings.gradle` 中以下七项注册。
首个包含该提交的发布 tag 是 `v1.53.0-ext`。

| 模块 | 后续补回 | 当前缺失所对应的移除提交 | 处理 |
| --- | --- | --- | --- |
| `axis-1` | 2025-10-14，`ee9ec339d2` | 2025-11-05，`db2c230eb6`：注册被注释 | 恢复注册 |
| `powerjob` | 2025-10-27，`ccfaebd872` | 2025-11-05，`db2c230eb6`：注册被删除 | 恢复注册 |
| `disruptor` | 2025-10-27，`ccfaebd872` | 2026-03-18，`21438f6496`：注册遗漏 | 恢复注册 |
| `graalvmjs` | 2025-10-27，`ccfaebd872` | 2026-03-18，`21438f6496`：注册遗漏 | 恢复注册 |
| `javax-script` | 2025-10-27，`ccfaebd872` | 2026-03-18，`21438f6496`：注册遗漏 | 恢复注册 |
| `mqtt:mqttv5` | 2025-10-27，`ccfaebd872` | 2026-03-18，`21438f6496`：注册遗漏 | 恢复注册 |
| `all-method` | 未恢复独立模块；2026-06-29，`74a5a064c4` 在 `trace-annotation` 中接入按包追踪 | 2025-09-15，`27cea6f949` | 保留现有实现，避免重复插桩 |

`db2c230eb6` 的提交说明为 `merge 1.55`，首个受影响发布 tag 为 `v1.55.0-ext`。
该变更于 2025-11-07 经 PR #135（`c78a2e580a`）进入 `guance`。
`21438f6496` 的提交说明为 `merge guance`，首个受影响发布 tag 为 `v1.60.1-ext`。
这些注册变化均发生在合并提交中；提交说明没有解释停用原因。

可用以下命令查看直接证据，注意选择合并前的观测云父提交：

```shell
git diff 27cea6f949^2 27cea6f949 -- settings.gradle settings.gradle.kts
git diff db2c230eb6^1 db2c230eb6 -- settings.gradle.kts
git diff 21438f6496^2 21438f6496 -- settings.gradle.kts
git show 74a5a064c4
```

## 恢复内容

- 在 `settings.gradle.kts` 中恢复六项注册。
- 适配已删除的 `startSpan` 重载和 `AgentSpan.context()`，保留原操作名与父子关系。
- 更新 `javax-script` 的插件标识及 IAST 测试依赖路径。
- 为六个模块生成依赖锁文件。
- 把 `axis1`、`disruptor`、`graalvm-js`、`javax-script`、`mqtt`、`powerjob`
  加入 `metadata/agent-jar-checks.properties` 的期望集成列表。
- 将 agent 大小预算调整到 35 MiB；原有 jar 已超过旧的 34 MiB 预算。

## 验证方式

`verifyAgentJarIntegrations` 从最终 jar 执行 `--list-integrations`，同时检查模块加载错误。
六个模块再次从构建或打包链路中消失时，现有校验会因集成列表缺项而失败。

```shell
./gradlew :dd-java-agent:verifyAgentJarContents :dd-java-agent:verifyAgentJarIntegrations
```

恢复后的六个模块编译、上述两个最终 jar 校验任务、六个模块的 `spotlessCheck`
以及根项目的 `spotlessKotlinGradleCheck` 均已通过。

另以独立 Java 程序配置 `dd.trace.method.packages=example.packageprobe`，确认现有
`trace-annotation` 对目标方法生成且仅生成一个 span，并采集入参。
此验证说明按包追踪能力已存在，不应再同时启用旧的 `all-method` 模块。

以同一个 `ScriptEngine` 示例对比恢复前后的 agent，恢复前没有脚本 span，恢复后生成
一个 `javax-script/eval` span，确认恢复后的方法插桩能实际执行。

本次构建与加载验证不替代六个框架各自的完整业务场景回归。
