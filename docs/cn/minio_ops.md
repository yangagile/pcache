
本文档介绍一些用户演示的 Minio 操作实例
1. 搭建本地 Minio 测试环境，并命别名为 myminio
   ```
   // 下载或安装最新 Minio 包
   // 设置MinIO的账号和密码 
   export MINIO_ROOT_USER="yang@minio"
   export MINIO_ROOT_PASSWORD="yun@minio"

   // 启动MinIO服务并将其放入后台
   nohup minio server "~/minio-data" --console-address ":9001" > minio.log 2>&1 &

   // 命别名为 myminio
   mc alias set myminio http://localhost:9000 yang@minio yun@minio

2. 通过 mc 命令创建用户（AK/SK），用户并绑定相关权限。。
   ```
   // myminio 是
   mc admin user add myminio ak-minio-user sk-minio-sts-password

   // 绑定权限
   mc admin policy create myminio sts-policy minio-policy.json
   mc admin policy attach myminio sts-policy --user ak-minio-sts

   // 示例 IAM 文件 minio-policy.json
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
   

3. 创建 Minio Bucket "minio-test"
   ```
   mc mb myminio/minio-test
