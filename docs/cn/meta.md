### 本文档介绍 Meta 存储方式，当前有 4 种 Meta 数据。
1. 角色信息（AK/SK），通过 IAM 授权管理。
2. 云厂商信息，接入对象存储厂商详细信息。
3. 云厂商桶信息，在云厂商上创建的数据痛。
4. 并行痛信息（PBucket），在 PCache上创建的虚拟桶信息。

### 系统支持 2 种 Meta 存储方式。
1. 文件（json格式）
	可通过一下环境变量配置，
	```
    -Dpms.meta.loader=file-loader        // Meta 保存方式
    -Dpms.data.loader.file.path=./meta/  // Meta 保存路径
    ```
    系统会在meta 目录创建 4 个 文件保存对应的 meta 信息。
    ```
    $ ll meta
    -rw-r--r--  1  356 Oct 15 17:42 pbucket
    -rw-r--r--  1  411 Oct 15 17:10 secret
    -rw-r--r--  1  385 Oct 15 17:40 vendor
    -rw-r--r--  1  277 Oct 15 17:40 vendorbucket
   
3. 数据库 （MySQL)
   可通过一下环境变量配置，
	```
	-Dpms.meta.loader=db-loader        // Meta 保存方式
    -Dspring.datasource.url = jdbc:mysql://localhost:3306/pcos
	-Dspring.datasource.username = root
	-Dspring.datasource.password = Mysql!23
	```
    共 4 张表保存对应的 Meta 信息。表的初始化脚本如下，
   
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

  
    

   
