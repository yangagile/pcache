This document describes the usage steps without enabling interface authentication (Token). For enabling Token, please refer to the [Configure Interface Authentication (Token) Guide](api_token.md).
 
1. Compile and run all modules.
   
   Environment references: Maven (version: 3.9.9), Java (version: 21.0.6), GO (version: 1.21.13) 
   ```
   cd /pcache/src
   ./build.sh
   ```
2. Run PMS service. 
   ```
   pc-pms/run.sh

   // Introduction to the main environment variables in the pc-pms/run.sh script:
   // pms.data.loader: Meta storage method, supports file and MySQL database. Default is file (file-loader).
   // pms.data.loader.file.path: The storage location for Meta, effective when using file-loader.
   // pms.enable.token=false: Do not use API Token authentication.
   // pms.log.dir : logging dir
   ```
   
   After startup, you can view or access PMS APIs via swagger-ui.
   ```
   http://127.0.0.1:8080/swagger-ui/index.html
   ```

3. Run the PCP service. To add multiple PCP nodes, execute the following command cyclically on each machine.
   ```
   pc-pcp/run.sh
   ```
   You can view the list of PCP cache nodes via the PMS API:
   ```
   curl -X GET "http://127.0.0.1:8080/api/v1/pcp/list" -H "accept: application/json;charset=UTF-8"

4. Add object storage vendors and the Bucket information provided by vendors.

   Taking Minio as an example. If using other public clouds, ensure the AK/SK assigned by the vendor has STS-related permissions. Create Minio AK/SK and bind permissions, refer to [Minio Operations](minio_ops.md).
   
   There are two methods to add vendors and vendor-provided Buckets.

   4.1 Add via the PMS interface
   ```
   // Add Minio vendor information to the system. ak=sts-user, sk=sts-password
   curl -X POST "http://127.0.0.1:8080/api/v1/vendor/add" -H "accept: application/json;charset=UTF-8" -H "Content-Type: application/json" -d "{ \"accessKey\": \"sts-user\", \"accessSecret\": \"sts-password\", \"description\": \"description\", \"endpoint\": \"http://127.0.0.1:9000\", \"internalEndpoint\": \"http://127.0.0.1:9000\", \"name\": \"MINIO\", \"region\": \"local\", \"s3Endpoint\": \"http://127.0.0.1:9000\", \"stsEndpoint\": \"http://127.0.0.1:9000\"}"

   // Add the Bucket created on Minio to the system, and remember the returned id.
   curl -X POST "http://127.0.0.1:8080/api/v1/vendor/bucket/add" -H "accept: application/json;charset=UTF-8" -H "Content-Type: application/json" -d "{ \"name\": \"minio-test\", \"permission\": \"private\", \"region\": \"local\", \"vendor\": \"MINIO\"}"
   
   ```
   
   4.2 Add by modifying Meta files,

   Create or add a vendor file in the PMS meta directory. The content is in JSON format, input the corresponding vendor name (currently supports MINIO, S3, OOS, BOS) and the corresponding AK/SK/Endpoint information.
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
   Create or add a vendorbucket file in the PMS meta directory, input the bucket information provided by the vendor, including bucket name, vendor, and region.
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
6. Create a PBucket. The name is test-minio. The routing policy policyRouting is in JSON format, of type OneRouter (corresponding to one vendor bucket). bucketIds is the id returned when adding the vendor bucket in the previous step. This id binds the relationship between the PBucket and the vendor Bucket.

   There are also two methods to add a PBucket.

   5.1 Add via the PMS interface
   ```
   curl -X POST "http://127.0.0.1:8080/api/v1/pb/add" -H "accept: application/json;charset=UTF-8" -H "Content-Type: application/json" -d "{ \"description\": \"test local minio\", \"name\": \"test-minio\", \"policyPermission\": \"private\", \"policyRouting\": \"{\\\"router\\\":{\\\"type\\\":\\\"OneRouter\\\"},\\\"bucketIds\\\":[1]}\"}"
   ```
   5.2 Add by modifying the meta file, create or modify a pbucket JSON format file, input PBucket information: name, routing policy, and establish the connection with the vendor's Bucket via bucketIds.
   ```
   {"items":[
    {
        "id":1,
        "name":"test-minio",  
        "policyRouting":"{\"router\":{\"type\":\"OneRouter\"},\"bucketIds\":[1]}"
    }
   ]}   
   ```
7. Run the 'pcmd' command line tool，using the newly created PBucket to put/get/sync files。[pcmd usage](../../pcmd/README.md)
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