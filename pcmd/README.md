
### This document describes how to use pcmd command line to put/get/sync files.
## build pcmd
```
cd pcmd
go build
```
## cnfig pcmd
modify config file "~/.pcmd.cfg"
```
# address of PMS
endpoint=http://127.0.0.1:8080
# ak/sk
ak=ak-pc-test
sk=KWBTBJJZTmZWb1F00lK1psg+2RMvRApY5uSDt7u1wpg=

```
## run pcmd
```
# for help
./pcmd -h

# put one local file to pbucket 'test-minio' with key "test/pcom/awscliv2.zip"
./pcmd put /tmp/awscliv2.zip s3://test-minio/test/pcom/awscliv2.zip

# get one local file form pbucket 'test-minio' wich key 'test/pcom/awscliv2.zip'
./pcmd get /tmp/awscliv2.zip s3://test-minio/test/pcom/awscliv2.zip

# sync local folder to pbucket 'test-minio' prefix 'test/pcom/sync/meta'
./pcmd sync /tmp/meta s3://test-minio/test/pcom/sync/meta

# sync back
./pcmd sync s3://test-minio/test/pcom/sync/meta /tmp/meta1 

```