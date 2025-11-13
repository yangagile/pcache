### 本文档介绍如何配置多个PMS
1. 启动第一个 PMS 服务，确保服务正常。
2. 配置第二个 PMS 通过环境变量定义一个已运行的 PMS 地址，如果输入地址无效，第二个 PMS 服务启动不起来。启动后收到第一个心跳后将同步 Meta 信息。
3. 同样步骤可启动多个。
```
pms.urls=${PMS1_URL}
// 注意，如果在同一台机器上测试，需要修改第二个 PMS 的端口(server.port) 和存储 Meta 的路径（pms.data.loader.file.path）
```

