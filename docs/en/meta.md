### This document describes the Meta storage methods. Currently, there are four types of Meta data:

1. Role information (AK/SK), managed through IAM authorization.

2. Cloud vendor information, detailing the object storage vendors integrated.

3. Cloud vendor bucket information, data buckets created on cloud vendors.

4. Parallel bucket information (PBucket), virtual bucket information created on PCache.

### Meta Interface
You can operate the meta through the PMS service interface. For details of the interfaces, refer to swagger-ui (http://127.0.0.1:8080/swagger-ui/index.html).

1. Add vendor and vendor bucket information via the PMS interface
   ```
   // Add Minio vendor information to the system with ak=sts-user, sk=sts-password 
   curl -X POST "http://127.0.0.1:8080/api/v1/vendor/add" -H "accept: application/json;charset=UTF-8" -H "Content-Type: application/json" -d "{ \"accessKey\": \"sts-user\", \"accessSecret\": \"sts-password\", \"description\": \"description\", \"endpoint\": \"http://127.0.0.1:9000\", \"internalEndpoint\": \"http://127.0.0.1:9000\", \"name\": \"MINIO\", \"region\": \"local\", \"s3Endpoint\": \"http://127.0.0.1:9000\", \"stsEndpoint\": \"http://127.0.0.1:9000\"}"

   // Add the bucket created on Minio to the system, and remember the returned ID.
   curl -X POST "http://127.0.0.1:8080/api/v1/vendor/bucket/add" -H "accept: application/json;charset=UTF-8"  -H "X-TOKEN: ${PMS_TOKEN}" -H "Content-Type: application/json" -d "{ \"name\": \"minio-test\", \"permission\": \"private\", \"region\": \"local\", \"vendor\": \"MINIO\"}"
   ```


2. Create a PBucket and associate it with the vendor bucket.

   ```
   // The PBucket name is test-minio, the routing policy is OneRouter (corresponding to one vendor bucket). The bucketIds are the IDs returned when adding the vendor bucket in the previous step. The relationship between the PBucket and the vendor bucket is bound through these IDs.  
   curl -X POST "http://127.0.0.1:8080/api/v1/pb/add" -H "accept: application/json;charset=UTF-8" -H "Content-Type: application/json" -d "{ \"description\": \"test local minio\", \"name\": \"test-minio\", \"policyPermission\": \"private\", \"policyRouting\": \"{\\\"router\\\":{\\\"type\\\":\\\"OneRouter\\\"},\\\"bucketIds\\\":[1]}\"}"
   ```
   
### The system supports two Meta storage methods:

1. File (in JSON format)
	It can be configured via the following environment variables:
	```
    -Dpms.meta.loader=file-loader        // Meta storage method 
    -Dpms.data.loader.file.path=./meta/  // Meta storage path  
    ```
    The system will create four files in the meta directory to store the corresponding Meta information:
    ```
    $ ll meta
    -rw-r--r--  1  356 Oct 15 17:42 pbucket
    -rw-r--r--  1  411 Oct 15 17:10 secret
    -rw-r--r--  1  385 Oct 15 17:40 vendor
    -rw-r--r--  1  277 Oct 15 17:40 vendorbucket
   
2. Database (MySQL)
	It can be configured via the following environment variables:
	```
	-Dpms.meta.loader=db-loader        // Meta 保存方式
    -Dspring.datasource.url = jdbc:mysql://localhost:3306/pcos
	-Dspring.datasource.username = root
	-Dspring.datasource.password = Mysql!23
	```
    
    A total of four tables store the corresponding Meta information. The table initialization script is as follows:
   
    ```
    CREATE TABLE `secret` (
      `id` int NOT NULL AUTO_INCREMENT,
      `access_key` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
      `mail` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
      `phone` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
      `description` varchar(256) DEFAULT NULL,
      `iam` varchar(4096) DEFAULT NULL,
      `secret_key` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
      `created_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
      `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
      `access_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
      PRIMARY KEY (`id`)
  	) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

    CREATE TABLE `vendor` (
      `id` int NOT NULL AUTO_INCREMENT,
      `name` varchar(128) NOT NULL,
      `region` varchar(128) DEFAULT NULL,
      `description` varchar(256) DEFAULT NULL,
      `access_key` varchar(256) DEFAULT NULL,
      `access_secret` varchar(256) DEFAULT NULL,
      `s3_endpoint` varchar(256) DEFAULT NULL,
      `endpoint` varchar(256) DEFAULT NULL,
      `internal_endpoint` varchar(256) DEFAULT NULL,
      `sts_endpoint` varchar(256) DEFAULT NULL,
      `cdn_endpoint` varchar(256) DEFAULT NULL,
      `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
      `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
  	) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
  
    CREATE TABLE `vendorbucket` (
      `id` int NOT NULL AUTO_INCREMENT,
      `name` varchar(256) NOT NULL,
      `vendor` varchar(128) NOT NULL,
      `region` varchar(128) DEFAULT NULL,
      `permission` varchar(1024) NOT NULL,
      `endpoint` varchar(256) DEFAULT NULL,
      `cdn_endPoint` varchar(256) DEFAULT NULL,
      `quota_capacity` bigint DEFAULT '-1',
      `quota_bandwidth` int DEFAULT '-1',
      `quota_qps` int DEFAULT '-1',
      `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
      `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
      `access_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
      `version` int DEFAULT '0',
      PRIMARY KEY (`id`)
    ) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
  
    CREATE TABLE `pbucket` (
      `id` int NOT NULL AUTO_INCREMENT,
      `name` varchar(256) NOT NULL,
      `description` varchar(512) NOT NULL,
      `prefix` varchar(256) DEFAULT NULL,
      `feature_flags` int DEFAULT '0',
      `quota_capacity` bigint DEFAULT '-1',
      `quota_bandwidth` int DEFAULT '-1',
      `quota_qps` int DEFAULT '-1',
      `policy_permission` varchar(1024) NOT NULL,
      `policy_ttl` varchar(1024) NOT NULL,
      `policy_routing` varchar(1024) NOT NULL,
      `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
      `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
      `access_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
      `version` int DEFAULT '0',
      PRIMARY KEY (`id`)
    ) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
    

   
