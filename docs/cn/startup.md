本文档介绍不开启接口认证(Token) 的使用步骤。开启 Token 参照 [配置接口认证（Token）指南](api_token.md)
 
1. 编译运行所有模块。

   环境参照 Maven（version: 3.9.9） Java（version: 21.0.6） GO（version：1.21.13）
   ```
   cd /pcache
   ./build.sh
   
2. 配置对象存储

   从供应商获取AK/SK，并创建供应商 Bucket，支持的对象存储类型有Minio,AWS S3, 百度 BOS，阿里 OSS。
   
   本文以自建 Minio 为例。 参照 [Minio操作](minio_ops.md) 创建 Minio AK/SK 并绑定权限，在 Minio 商创建 Bucket “minio-test”。

   运行命令行 pcmd meta 命令初始化 meta 数据。（也可在 PMS 启动后通过接口逐条添加， 参照：[Meta](meta.md)）Meta 接口访问部分
   ```	
   pcmd/pcmd meta -pb-name pb-minio -vendor-bucket minio-test -vendor MINIO -vendor-ak sts-user vendor-sk sts-password \
   -vendor-endpoint https://s3.bj.bcebos.com init
   
   // 这个命令将创建关联 pbucket：pb-minio 到 Minio bucket：minio-test， 并添加 Minio相关的信息到系统。
   // 在当前 ./meta 目录下创建四个文件，详细说明参见命令 “pcmd/pcmd meta -h”
   ```	

3. 运行 PMS Meta 服务。
   ```	
   pc-pms/run.sh

   // pc-pms/run.sh 脚本主要环境变量介绍，
   // pms.data.loader ： Meta 存储方式，支持文件和 Mysql 数据库。默认是文件 file-loader。
   // pms.data.loader.file.path ：Meta 存放的位置，使用 file-loader 有效。
   // pc.enable.token=false ：不使用接口 Token 认证。
   ```
   启动后可通过 swagger-ui 查看或访问 PMS 接口
   ```
   http://127.0.0.1:8080/swagger-ui/index.html
   
4. 运行 PCP 服务。如添加多个 PCP 节点，在各台机器上循环执行。
   ```
   pc-pcp/run.sh
   
   // 可通过 PMS 接口查看 PCP 缓存节点列表
   curl -X GET "http://127.0.0.1:8080/api/v1/pcp/list" -H "accept: application/json;charset=UTF-8"


5. 配置运行命令行工具（pcmd），使用新创建的 PBucket 上传/下载文件。[pcmd 使用方式](../../pcmd/README.md)
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