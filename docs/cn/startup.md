本文档介绍不开启接口认证(Token) 的使用步骤。开启 Token 参照 [配置接口认证（Token）指南](api_token.md)
 
1. 编译运行所有模块。
   ```
   cd /pcache/src
   ./build.sh

   // 编译环境 maven version: 3.9.9 Java version: 21.0.6
   
2. 运行 PMS Meta 服务。运行日志在 logs 目录下 pms.log, Meta 在 meta 目录下。
   ```	
   pc-pms/run.sh

   // pc-pms/run.sh 脚本主要环境变量介绍，
   // pms.data.loader ： Meta 存储方式，支持文件和 Mysql 数据库。默认是文件 file-loader。
   // pms.data.loader.file.path ：Meta 存放的位置，使用 file-loader 有效。
   // pc.enable.token=false ：不使用接口 Token 认证。
   ```

   启动后可通过 swagger-ui 查看或访问 PMS 接口。
   ```
   http://127.0.0.1:8080/swagger-ui/index.html
   
3. 运行 PCP 服务。如添加多个 PCP 节点，在各台机器上循环执行。
   ```
   pc-pcp/run.sh
   
   // 可通过 PMS 接口查看 PCP 缓存节点列表
   curl -X GET "http://127.0.0.1:8080/api/v1/pcp/list" -H "accept: application/json;charset=UTF-8"


4. 通过 PMS 接口，添加对象存储供应商和Bucket。
   以 Minio 为例，如果使用其他公有云，确保厂商分配的 AK/SK 上有STS相关权限。
   创建 Minio AK/SK 并绑定权限，参照 [Minio操作](minio_ops.md)
   ```
   // 添加 Minio 供应商信息到系统 ak=sts-user sk=sts-password
   curl -X POST "http://127.0.0.1:8080/api/v1/vendor/add" -H "accept: application/json;charset=UTF-8" -H "Content-Type: application/json" -d "{ \"accessKey\": \"sts-user\", \"accessSecret\": \"sts-password\", \"description\": \"description\", \"endpoint\": \"http://127.0.0.1:9000\", \"internalEndpoint\": \"http://127.0.0.1:9000\", \"name\": \"MINIO\", \"region\": \"local\", \"s3Endpoint\": \"http://127.0.0.1:9000\", \"stsEndpoint\": \"http://127.0.0.1:9000\"}"

   // 添加在 Minio 上创建的 Bucket 到系统，并记住返回的 id 号。
   curl -X POST "http://127.0.0.1:8080/api/v1/vendor/bucket/add" -H "accept: application/json;charset=UTF-8"  -H "X-TOKEN: ${PMS_TOKEN}" -H "Content-Type: application/json" -d "{ \"name\": \"minio-test\", \"permission\": \"private\", \"region\": \"local\", \"vendor\": \"MINIO\"}"
   

5. 创建 PBucket。名称为 test-minio 其中路由策略 policyRouting 是 json格式，类型是 OneRouter（对应一个供应商bucket），bucketIds 是上步添加供应商bucket 返回的 id，通过 id 绑定 PBucket 和 供应商 Bucket 关系。
   ```
   curl -X POST "http://127.0.0.1:8080/api/v1/pb/add" -H "accept: application/json;charset=UTF-8" -H "Content-Type: application/json" -d "{ \"description\": \"test local minio\", \"name\": \"test-minio\", \"policyPermission\": \"private\", \"policyRouting\": \"{\\\"router\\\":{\\\"type\\\":\\\"OneRouter\\\"},\\\"bucketIds\\\":[1]}\"}"
  
   
7. 运行测试程序，使用新创建的 PBucket 上传/下载文件。
	```
    pc-test/run.sh --ops put --bucket test-minio --local_file ~/tmp/200G.file --file_key tmp/200G.file
    pc-test/run.sh --ops get --bucket test-minio --local_file ~/tmp/200G_GET.file --file_key tmp/200G.file

    // log里会打印上传下载的统计信息。PCP:41(cache:41) 表示 从PCP 节点下载41个快，缓存命中 41
   	10:05:55.551 [main] INFO  com.cloud.pc.PBucket - successfully get file from test-minio/tmp/200G.file to /tmp/200G_GET.file stats: total 41 blocks, fail:0 PCP:41(cache:41) local:0 duration time:1087 ms rate:185MB/S
