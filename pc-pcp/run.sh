#!/bin/bash

CUR_DIR=$(cd "$(dirname "$0")" && pwd)
PARENT_DIR=$(cd "$(dirname "$0")/.." && pwd)
LOG_DIR="${PARENT_DIR}/logs/"
mkdir -p "${LOG_DIR}"

VM_OPT="-Xms512m -Xmx1024m"

ENVS_AUTH="-Dserver.port=8081 -Dpcp.ak=ak-pcp-admin  -Dpcp.sk=yWlt32Rw6uImzTcAKJ5AZO5Bqw9rPS1YSZKZfgyv3ao="
ENVS_LOGS="-Dpcp.log.dir=${LOG_DIR}"
ENVS_DATA="-Dpcp.data.dir=${PARENT_DIR}/data/"
ENVS_PMS_URL="-Dpcp.pms.url=http://127.0.0.1:8080/"

LOG_OUT="${LOG_DIR}pcp_out.log"


nohup java ${VM_OPT} ${ENVS_AUTH} ${ENVS_LOGS} ${ENVS_DATA} ${ENVS_PMS_URL} -classpath ${CUR_DIR}/target/pc-pcp-0.1.0-jar-with-dependencies.jar com.cloud.pc.PcpMain > ${LOG_OUT} 2>&1 &





