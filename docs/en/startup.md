This document describes the usage steps without enabling interface authentication (Token). For enabling Token, please refer to the [Configure Interface Authentication (Token) Guide](api_token.md).
 
1. Compile and run all modules.
   
   Environment references: Maven (version: 3.9.9), Java (version: 21.0.6), GO (version: 1.21.13) 
   ```
   cd /pcache
   ./build.sh
   ```
2. Configure Object Storage

   Obtain the Access Key (AK) and Secret Key (SK) from the object vendor, and create a vendor bucket. Supported object storage types include Minio, AWS S3, Baidu BOS, and Alibaba OSS.

   This article uses a self‑built Minio as an example. Refer to [Minio Operations](minio_ops.md) to create Minio AK/SK and bind permissions, then create a bucket named "minio‑test" in Minio.

   Run the command‑line "pcmd meta ... " command to initialize the meta. (Alternatively, you can add items one by one through 
   the API after PMS starts. Refer to: [Meta interface](meta.md)

   ```
   pcmd/pcmd meta -pb-name pb-minio -vendor-bucket minio-test -vendor MINIO -vendor-ak sts-user -vendor-sk sts-password \  
   -vendor-endpoint https://s3.bj.bcebos.com init  

   // This command will associate the pbucket "pb‑minio" with the Minio bucket "minio‑test", and add Minio related information to the system.  
   // It creates four files in the current ./meta directory. For a detailed explanation, refer to the command "pcmd/pcmd meta -h".  
   ```
   
3. Run PMS service. 
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

4. Run the PCP service. To add multiple PCP nodes, execute the following command cyclically on each machine.
   ```
   pc-pcp/run.sh
   ```
   You can view the list of PCP cache nodes via the PMS API:
   ```
   curl -X GET "http://127.0.0.1:8080/api/v1/pcp/list" -H "accept: application/json;charset=UTF-8"


5. Run the 'pcmd' command line tool，using the newly created PBucket to put/get/sync files。[pcmd usage](../../pcmd/README.md)
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