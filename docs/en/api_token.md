This document explains how to configure the API Token for PCache.
AK/SK can be assigned to each module (PMS/PCP/PBucket), and permissions are granted through IAM. This is an independent permission management system separate from the backend object storage.
   
1. Configure the AK/SK for the PMS metadata service.
Before starting PMS, configure the global variables “pc.pms.ak" and “pc.pms.sk" with the PMS AK and SK respectively. The SK should be a 44-character random string. Set “pc.enable.token" to true. For example:
   ```	
   -Dpc.pms.ak=pms-admin 
   -Dpc.pms.sk=QPAAmgJVWUTzrRC9lGDMRJo6mCd4XWK6+tolzsWmgO4=
   -Dpc.enable.token=true

2. Generate PMS_TOKEN
   When Token is enabled, any PMS operation requires the PMS AK and Token. The PMS_TOKEN can be generated using the following interface:
   ```
   curl -X POST "http://127.0.0.1:8080/api/v1/secret/token" -H "accept: application/json;charset=UTF-8" -H "Content-Type: application/json" -d "{ \"accessKey\": \"ak-pms-admin\", \"expirationMs\": 3000000, \"secretKey\": \"${PMS_SK}"}"
   
3. Configure the AK/SK for the PCP 
   ```
   // Use the generated PMS AK/Token to create the PCP AK/SK. Let the AK be "ak-pcp-admin" and the IAM be "pcp:admin".
   curl -X POST "http://127.0.0.1:8080/api/v1/secret/add" -H "accept: application/json;charset=UTF-8" -H "X-AK: ak-pms-admin" -H "X-TOKEN: ${PMS_TOKEN}" -H "Content-Type: application/json" -d "{ \"accessKey\": \"ak-pcp-admin\", \"description\": \"PCP Administrator 6\", \"iam\": \"{\\\"Version\\\":\\\"2025-08-05\\\",\\\"Statement\\\":[{\\\"Effect\\\":\\\"Allow\\\",\\\"Action\\\":[\\\"pcp:admin\\\"],\\\"Resource\\\":[\\\"*\\\"]}]}\"}"
   
   //  Configure the obtained AK/SK in the PCP via environment variables and start the service.
   -Dpc.pcp.ak=ak-pcp-admin  
   -Dpc.pcp.sk=${PCP_SK}"


4. Configure the AK/SK for the PBucket.
   ```
   // Use the PMS AK/TOKEN to create an AK/SK and specify access permissions for the PBucket `test-minio` via IAM.
   OST "http://127.0.0.1:8080/api/v1/secret/add" -H "accept: application/json;charset=UTF-8" -H "X-AK: ak-pms-admin" -H "X-TOKEN: ${PMS_TOKEN}" -H "Content-Type: application/json" -d "{ \"accessKey\": \"ak-pc-test\", \"description\": \"ak for pc-test \", \"iam\": \"{\\\"Version\\\":\\\"2025-08-05\\\",\\\"Statement\\\":[{\\\"Effect\\\":\\\"Allow\\\",\\\"Action\\\":[\\\"s3:PutObject\\\",\\\"s3:GetObject\\\",\\\"s3:ListObject\\\"],\\\"Resource\\\":[\\\"arn:aws:s3:::test-minio/*\\\"]}]}\"}"

   // Configure the obtained AK/SK in the SDK via environment variables to access the corresponding PBucket.
   -Dpc.ak=ak-pc-test  
   -Dpc.sk=${PBucket_SK}"
