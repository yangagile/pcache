#!/bin/bash

CUR_DIR=$(cd "$(dirname "$0")" && pwd)
PARENT_DIR=$(cd "$(dirname "$0")/.." && pwd)
META_DIR="${PARENT_DIR}/meta/"
LOG_DIR="${PARENT_DIR}/logs/"

VM_OPT="-Xms512m -Xmx1024m"

ENVS_AUTH="-Dpms.enable.token=false -Dpms.ak=pms-admin -Dpms.sk=QPAAmgJVWUTzrRC9lGDMRJo6mCd4XWK6+tolzsWmgO4="
ENVS_MINIO="-Dminio.iam.role.policy=arn:minio:iam::minio:role/sts-policy1"
ENVS_META="-Dpms.data.loader.file.path=${META_DIR}"
ENVS_LOGS="-Dpms.log.dir=${LOG_DIR}"
LOG_OUT="${LOG_DIR}pms_out.log"

nohup java ${VM_OPT} ${ENVS_AUTH} ${ENVS_META} ${ENVS_LOGS} ${ENVS_MINIO} -jar ${CUR_DIR}/target/pc-pms-0.1.0.jar  > ${LOG_OUT} 2>&1 &

