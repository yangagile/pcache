This document provides some Minio operation examples for user demonstration.
1. Set up a local Minio test environment and assign the alias myminio.
   ```
   // Download or install the latest Minio package
   // Set the Minio account and password
   export MINIO_ROOT_USER="yang@minio"
   export MINIO_ROOT_PASSWORD="yun@minio"

   // Start the Minio service and run it in the background
   nohup minio server "~/minio-data" --console-address ":9001" > minio.log 2>&1 &

   // Assign the alias 'myminio'
   mc alias set myminio http://localhost:9000 yang@minio yun@minio

2. Use the mc command to create a user (AK/SK) and bind the relevant policies to the user.
   ```
   mc admin user add myminio ak-minio-user sk-minio-sts-password

   // Bind the policy
   mc admin policy create myminio sts-policy minio-policy.json
   mc admin policy attach myminio sts-policy --user ak-minio-sts

   // Example IAM policy file minio-policy.json
   {
    "Version": "2012-10-17",
    "Statement": [
      {
        "Effect": "Allow",
        "Action": [
          "s3:ListBucket",
          "s3:GetObject",
          "s3:PutObject"
        ],
        "Resource": [
          "arn:aws:s3:::*"
        ]
      }
    ]
   }
   

3. Create the Minio Bucket "minio-test".
   ```
   mc mb myminio/minio-test
