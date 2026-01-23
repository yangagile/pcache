# pcos-sdk-go

## Usage
Refer to the way of PCache command-line tool (pcmd) project.

## System Testing Method
The system test files are located in the "test" directory. Follow the steps below:

1. Configure and start PMS/PCP, check the logs, and confirm that the services are running normally.

2. Modify the test configuration file "test_config.yaml", replacing the PMS address, pbucket information, local temporary directory, and other details with those of the current environment.
```
pms:
    url: "http://127.0.0.1:8080"

bucket:
    ak: "ak-test"
    sk: "3ewGHUIayI8cZ8qgAkoJ31gXvGqAzKmmsTLqMhTrhyM="
    name: "pb-minio"
    prefix: "test/pcache/go/sdk/"

local:
    root: "/tmp/pcache/test/pb_minio/"
```

3. Run all tests with below command.
```
cd sdk/pc-sdk-go/test
go test ./...
```

