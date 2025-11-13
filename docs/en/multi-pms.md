
### This document describes how to configure multiple PMS
1. Start the first PMS service and ensure it runs properly.

2. Configure the second PMS by defining the address of an already running PMS through an environment variable. If the entered address is invalid, the second PMS service will fail to start. After startup, it will synchronize Meta information upon receiving the first heartbeat.

3. Multiple PMS instances can be started following the same steps.

```
pms.urls=${PMS1_URL}
// Note: If testing on the same machine, it is necessary to modify the port (server.port) of the second PMS and the file path for storing Meta (pms.data.loader.file.path).

