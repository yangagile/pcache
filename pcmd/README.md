
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
// for help
./pcmd -h

// put one local file to pbucket 'test-minio' with key "test/pcom/awscliv2.zip"
./pcmd put /tmp/awscliv2.zip s3://test-minio/test/pcom/awscliv2.zip

// get one local file form pbucket 'test-minio' wich key 'test/pcom/awscliv2.zip'
./pcmd get /tmp/awscliv2.zip s3://test-minio/test/pcom/awscliv2.zip

// sync local folder '/tmp/meta' to pbucket 'test-minio' prefix 'test/pcom/sync/meta'
./pcmd sync /tmp/meta s3://test-minio/test/pcom/sync/meta

// sync pbucket 'test-minio' prefix 'test/pcom/sync/meta' to local folder '/tmp/meta'
./pcmd sync s3://test-minio/test/pcom/sync/meta /tmp/meta

```
## more options from command "pcmd help sync"
```
$ ./pcmd help sync
Command: sync
Description: sync between local folder and bucket prefix
Usage: app pcmd sync [FLAGS] folder s3://bucket/prefix | s3://bucket/prefix folder
Options:
  -checksum string
    	checksum file for verify or compare, crc32 or md5
  -debug
    	debug mode
  -dry
    	dry run mode
  -skip-existing
    	skip existing file or object
  -skip-unchanged
    	skip unchanged file or object with size for checksum
  -small-file
    	size is less than block size, will take special method for performance.
  -block-thead-number int
    	thread number of block worker (default 8)
  -file-thread-number int
    	thread number of file worker (default 8)
  -http-timeout-factor float 
    	http client timeout factor (default 1.0)

```
