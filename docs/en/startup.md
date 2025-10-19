This document describes the usage steps without enabling interface authentication (Token). For enabling Token, please refer to the [Configure Interface Authentication (Token) Guide](api_token.md).
 
1. Compile and run all modules.
  ```
  cd /pcache/src
  ./build.sh

  // Build environment: maven version: 3.9.9, Java version: 21.0.6
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

4. Add object storage vendors and Buckets via the PMS API.
   Taking Minio as an example. If using other public clouds, ensure the AK/SK assigned by the vendor has STS-related permissions. Create Minio AK/SK and bind permissions, refer to [Minio Operations](minio_ops.md).
   ```
   // Add Minio vendor information to the system. ak=sts-user, sk=sts-password
   curl -X POST "http://127.0.0.1:8080/api/v1/vendor/add" -H "accept: application/json;charset=UTF-8" -H "Content-Type: application/json" -d "{ \"accessKey\": \"sts-user\", \"accessSecret\": \"sts-password\", \"description\": \"description\", \"endpoint\": \"http://127.0.0.1:9000\", \"internalEndpoint\": \"http://127.0.0.1:9000\", \"name\": \"MINIO\", \"region\": \"local\", \"s3Endpoint\": \"http://127.0.0.1:9000\", \"stsEndpoint\": \"http://127.0.0.1:9000\"}"

   // Add the Bucket created on Minio to the system, and remember the returned id.
   curl -X POST "http://127.0.0.1:8080/api/v1/vendor/bucket/add" -H "accept: application/json;charset=UTF-8" -H "Content-Type: application/json" -d "{ \"name\": \"minio-test\", \"permission\": \"private\", \"region\": \"local\", \"vendor\": \"MINIO\"}"
  
6. Create a PBucket. The name is test-minio. The routing policy policyRouting is in JSON format, of type OneRouter (corresponding to one vendor bucket). bucketIds is the id returned when adding the vendor bucket in the previous step. This id binds the relationship between the PBucket and the vendor Bucket.
   ```
   curl -X POST "http://127.0.0.1:8080/api/v1/pb/add" -H "accept: application/json;charset=UTF-8" -H "Content-Type: application/json" -d "{ \"description\": \"test local minio\", \"name\": \"test-minio\", \"policyPermission\": \"private\", \"policyRouting\": \"{\\\"router\\\":{\\\"type\\\":\\\"OneRouter\\\"},\\\"bucketIds\\\":[1]}\"}"

7. Run the test program to upload/download files using the newly created PBucket.
   ```  
   // Modify run.sh to use the AK/SK created in step 4
   ./run.sh --ops put --bucket test-minio --local_file ~/tmp/200G.file --file_key tmp/200G.file
   ./run.sh --ops get --bucket test-minio --local_file ~/tmp/200G_GET.file --file_key tmp/200G.file

   // The log will print statistics for upload and download operations. PCP:41(cache:41) indicates that 41 blocks were downloaded from PCP nodes, with 41 cache hits.
   10:05:55.551 [main] INFO  com.cloud.pc.PBucket - successfully get file from test-minio/tmp/200G.file to /tmp/200G_GET.file stats: total 41 blocks, fail:0 PCP:41(cache:41) local:0 duration time:1087 ms rate:185MB/S
