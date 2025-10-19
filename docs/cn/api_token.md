本文档介绍如何配置 PCache 的API Token。
可为每个模块（PMS/PCP/PBucket）分配 AK/SK，并通过 Iam 授予权限。是独立于后端对象存储的独立权限管理系统。
   
1. 配置 PMS 元数据服务的 AK/SK。
   启动 PMS 前，全局变量 pc.pms.ak 和 pc.pms.sk 配置 PMS 的 AK/SK，其中 SK 为44位随机字符。配置 pc.enable.token 为 true， 例如
   ```	
   -Dpc.pms.ak=pms-admin 
   -Dpc.pms.sk=QPAAmgJVWUTzrRC9lGDMRJo6mCd4XWK6+tolzsWmgO4=
   -Dpc.enable.token=true

2. 生成 PMS_TOKEN
   Token开启状态下，任何 PMS 操作都需要 PMS 的 AK 和 Token，PMS_TOKEN 可由以下接口生成
   ```
   curl -X POST "http://127.0.0.1:8080/api/v1/secret/token" -H "accept: application/json;charset=UTF-8" -H "Content-Type: application/json" -d "{ \"accessKey\": \"ak-pms-admin\", \"expirationMs\": 3000000, \"secretKey\": \"${PMS_SK}"}"
   
4. 配置 PCP 缓存节点 AK/SK
   ```
   // 使用生成的 PMS AK/token 创建 PCP 的 AK/SK，AK 为 "ak-pcp-admin" IAM 为 "pcp:admin"。
   curl -X POST "http://127.0.0.1:8080/api/v1/secret/add" -H "accept: application/json;charset=UTF-8" -H "X-AK: ak-pms-admin" -H "X-TOKEN: ${PMS_TOKEN}" -H "Content-Type: application/json" -d "{ \"accessKey\": \"ak-pcp-admin\", \"description\": \"PCP Administrator 6\", \"iam\": \"{\\\"Version\\\":\\\"2025-08-05\\\",\\\"Statement\\\":[{\\\"Effect\\\":\\\"Allow\\\",\\\"Action\\\":[\\\"pcp:admin\\\"],\\\"Resource\\\":[\\\"*\\\"]}]}\"}"
   
   // 通过环境变量配置获取的 AK/SK 配置到 PCP 里，并启服务。
   -Dpc.pcp.ak=ak-pcp-admin  
   -Dpc.pcp.sk=${PCP_SK}"


5. 配置 PBucket 的 AK/SK。
   ```
   // 使用 PMS 的 AK/TOKEN 创建 AK/SK 并通过 IAM 指定对 PBucket test-minio 的访问权限。
   OST "http://127.0.0.1:8080/api/v1/secret/add" -H "accept: application/json;charset=UTF-8" -H "X-AK: ak-pms-admin" -H "X-TOKEN: ${PMS_TOKEN}" -H "Content-Type: application/json" -d "{ \"accessKey\": \"ak-pc-test\", \"description\": \"ak for pc-test \", \"iam\": \"{\\\"Version\\\":\\\"2025-08-05\\\",\\\"Statement\\\":[{\\\"Effect\\\":\\\"Allow\\\",\\\"Action\\\":[\\\"s3:PutObject\\\",\\\"s3:GetObject\\\",\\\"s3:ListObject\\\"],\\\"Resource\\\":[\\\"arn:aws:s3:::test-minio/*\\\"]}]}\"}"

   // 通过环境变量配置获取的 AK/SK 配置到 SDK 里，访问对应的 PBucket。
   -Dpc.ak=ak-pc-test  
   -Dpc.sk=${PBucket_SK}"
