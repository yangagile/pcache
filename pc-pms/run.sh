#!/bin/bash

CUR_DIR=$(cd "$(dirname "$0")" && pwd)
PARENT_DIR=$(cd "$(dirname "$0")/.." && pwd)
META_DIR="${PARENT_DIR}/meta/"
LOG_DIR="${PARENT_DIR}/logs/"
mkdir -p "${META_DIR}"
mkdir -p "${LOG_DIR}"

VM_OPT="-Xms128m -Xmx256m"

ENVS_AUTH="-Dpms.enable.token=false -Dpms.ak=pms-admin -Dpms.sk=QPAAmgJVWUTzrRC9lGDMRJo6mCd4XWK6+tolzsWmgO4="
ENVS_META="-Dpms.data.loader.file.path=${META_DIR}"
ENVS_LOGS="-Dpms.log.dir=${LOG_DIR}"
ENVS_SERVER="-Dserver.address=0.0.0.0 -Dserver.port=8080"
LOG_OUT="${LOG_DIR}pms_out.log"

nohup java ${VM_OPT} ${ENVS_AUTH} ${ENVS_META} ${ENVS_LOGS} ${ENVS_SERVER} -jar ${CUR_DIR}/target/pc-pms-0.1.0.jar  > ${LOG_OUT} 2>&1 &

