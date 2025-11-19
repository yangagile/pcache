
### This document describes how to configure multiple PMS
## Startup Procedure
1. Start the first PMS service and ensure it runs properly.

2. Configure the second PMS by defining the address of an already running PMS through an environment variable. If the entered address is invalid, the second PMS service will fail to start. After startup, it will synchronize Meta information upon receiving the first heartbeat.

3. Multiple PMS instances can be started following the same steps.

```
pms.existing.url=${PMS1_URL}
// Note: If testing on the same machine, it is necessary to modify the port (server.port) of the second PMS and the file path for storing Meta (pms.data.loader.file.path).
```

## Multi-PMS Consistency
Since the amount of Meta managed by PMS is relatively small (primarily configuration information), a simple consistency solution is currently adopted.
By default, the first started PMS service is the primary service. Certain write operations, such as adding AK/SK or creating a PBucket, can only be performed on the primary service.
If the primary service fails, it will not affect currently running operations. A secondary service can be promoted to primary via an API to perform Meta addition operations.
```
curl -X GET "http://127.0.0.1:8081/api/v1/pms/leader/enable?enableWrite=true" -H "accept: application/json;charset=UTF-8"
