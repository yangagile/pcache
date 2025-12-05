本文档介绍不开启接口认证(Token) 的使用步骤。开启 Token 参照 [配置接口认证（Token）指南](api_token.md)
 
1. 编译运行所有模块。
   环境参照 Maven（version: 3.9.9） Java（version: 21.0.6） GO（version：1.21.13）
   ```
   cd /pcache/src
   ./build.sh
   
2. 运行 PMS Meta 服务。运行日志在 logs 目录下 pms_out.log。
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

4. 通过 PMS 接口，添加对象存储供应商和供应商提供的Bucket信息。
   以 Minio 为例，如果使用其他公有云，确保厂商分配的 AK/SK 上有STS相关权限。
   创建 Minio AK/SK 并绑定权限，参照 [Minio操作](minio_ops.md)
   供应商和供应商提供的Bucket添加有两种添加方式
   
   4.1 通过 PMS 接口添加
   ```
   // 添加 Minio 供应商信息到系统 ak=sts-user sk=sts-password
   curl -X POST "http://127.0.0.1:8080/api/v1/vendor/add" -H "accept: application/json;charset=UTF-8" -H "Content-Type: application/json" -d "{ \"accessKey\": \"sts-user\", \"accessSecret\": \"sts-password\", \"description\": \"description\", \"endpoint\": \"http://127.0.0.1:9000\", \"internalEndpoint\": \"http://127.0.0.1:9000\", \"name\": \"MINIO\", \"region\": \"local\", \"s3Endpoint\": \"http://127.0.0.1:9000\", \"stsEndpoint\": \"http://127.0.0.1:9000\"}"

   // 添加在 Minio 上创建的 Bucket 到系统，并记住返回的 id 号。
   curl -X POST "http://127.0.0.1:8080/api/v1/vendor/bucket/add" -H "accept: application/json;charset=UTF-8"  -H "X-TOKEN: ${PMS_TOKEN}" -H "Content-Type: application/json" -d "{ \"name\": \"minio-test\", \"permission\": \"private\", \"region\": \"local\", \"vendor\": \"MINIO\"}"
   ```
   
   4.2 通过修改Meta文件添加，

   在 PMS meta 目录下创建或添加 vendor 文件，内容是json格式，输入对应的供应商名称（当前支持 MINIO，S3，OOS，BOS）和对应的AK/SK/Endpoint 等信息。
   ```
   {"items":
    [
        {"id":1,
         "name":"MINIO",
         "region":"local",
         "accessKey":"sts-access-key",
         "accessSecret":"sts-access-secret",
         "endpoint":"http://127.0.0.1:9000",
         "stsEndpoint":"http://127.0.0.1:9000"
         }
    ]
   }
   ```
   
   在 PMS meta 目录下创建或添加 vendorbucket 文件，输入供应商提供的bucket 信息，bucket名字，供应商，和区域
   ```
   {"items":[
    {
        "id":1,
        "name":"minio-bucket-name",
        "vendor":"MINIO",
        "region":"local"
    }
   ]}
   ```

5. 创建 PBucket。名称为 test-minio 其中路由策略 policyRouting 是 json格式，类型是 OneRouter（对应一个供应商bucket），bucketIds 是上步添加供应商bucket 返回的 id，通过 id 绑定 PBucket 和 供应商 Bucket 关系。
   PBucket添加也有两种添加方式

   5.1 通过 PMS 接口添加
   ```
   curl -X POST "http://127.0.0.1:8080/api/v1/pb/add" -H "accept: application/json;charset=UTF-8" -H "Content-Type: application/json" -d "{ \"description\": \"test local minio\", \"name\": \"test-minio\", \"policyPermission\": \"private\", \"policyRouting\": \"{\\\"router\\\":{\\\"type\\\":\\\"OneRouter\\\"},\\\"bucketIds\\\":[1]}\"}"
   ```
   
   5.2 通过又该meta 文件添加，修改创建 pbucket json格式的文件，输入PBucket信息，名称，路由策略，通过bucketIds 和供应商的Bucket建立联系。
   ```
   {"items":[
    {
        "id":1,
        "name":"test-minio",  
        "policyRouting":"{\"router\":{\"type\":\"OneRouter\"},\"bucketIds\":[1]}"
    }
   ]}
   ```
   
6. 配置运行命令行工具（pcmd），使用新创建的 PBucket 上传/下载文件。[pcmd 使用方式](../../pcmd/README.md)
	```
   // put one local file to pbucket 'test-minio' with key "test/pcom/awscliv2.zip"
   ./pcmd put /tmp/awscliv2.zip s3://test-minio/test/pcom/awscliv2.zip
   
   // get one local file form pbucket 'test-minio' wich key 'test/pcom/awscliv2.zip'
   ./pcmd get /tmp/awscliv2.zip s3://test-minio/test/pcom/awscliv2.zip
   
   // sync local folder '/tmp/meta' to pbucket 'test-minio' prefix 'test/pcom/sync/meta'
   ./pcmd sync /tmp/meta s3://test-minio/test/pcom/sync/meta
   
   // sync pbucket 'test-minio' prefix 'test/pcom/sync/meta' to local folder '/tmp/meta'
   ./pcmd sync s3://test-minio/test/pcom/sync/meta /tmp/meta

    ```  