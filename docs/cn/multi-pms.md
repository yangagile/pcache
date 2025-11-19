### 本文档介绍如何配置多个PMS
## 启动方式
1. 启动第一个 PMS 服务，确保服务正常。
2. 配置第二个 PMS 通过环境变量定义一个已运行的 PMS 地址，如果输入地址无效，第二个 PMS 服务启动不起来。启动后收到第一个心跳后将同步 Meta 信息。
3. 同样步骤可启动多个。
```
pms.existing.url=${PMS1_URL}
// 注意，如果在同一台机器上测试，需要修改第二个 PMS 的端口(server.port) 和存储 Meta 的路径（pms.data.loader.file.path）
```

## 多个PMS一致性
由于 PMS 管理的 meta 比较少，主要是一些配置信息，当前采取简单一致方案。
默认第一个启动的 PMS 服务是主服务，一些写操作只能在主服务上运行，如添加AK/SK， 创建PBucket等。
如果主服务挂了，不会影响当前已运行的业务，可通过接口升级一个从服务成主，做一些 Meta 添加操作。
```
curl -X GET "http://127.0.0.1:8081/api/v1/pms/leader/enable?enableWrite=true" -H "accept: application/json;charset=UTF-8"



