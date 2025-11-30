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
   ```
7. Run the 'pcmd' command line tools，using the newly created PBucket to put/get/sync files。[pcmd usage](../../pcmd/README.md)
   ```
    // put one local file to pbucket 'test-minio' with key "test/pcom/awscliv2.zip"
    ./pcmd put /tmp/awscliv2.zip s3://test-minio/test/pcom/awscliv2.zip
    
    // get one local file form pbucket 'test-minio' wich key 'test/pcom/awscliv2.zip'
    ./pcmd get /tmp/awscliv2.zip s3://test-minio/test/pcom/awscliv2.zip
    
    // sync local folder to pbucket 'test-minio' prefix 'test/pcom/sync/meta'
    ./pcmd sync /tmp/meta s3://test-minio/test/pcom/sync/meta
    
    // sync back
    ./pcmd sync s3://test-minio/test/pcom/sync/meta /tmp/meta1
    
    // From running log, The FileStats represents file information, while BlockStats refers to block information.
    // It displays whether the block was uploaded or downloaded from PCP or locally, and whether it was a cache hit.
    FileStats: Count(total:2 ok:2 fail:0) Size(total:20971520 avg:10485760 max:10485760 min:10485760)bytes Time(avg:84 max:
    84 min:84)ms
    BlockStats: Count(total:4 ok_pcp_cache:0 ok_pcp_local:4 ok_local:0 ok_local_pcp_fail:0 fail:0) Time(avg:72 max:75 min:
    69)ms
   ```  