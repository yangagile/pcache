#!/bin/bash

ENVS_PMS="-Dpms.url=http://127.0.0.1:8080"
ENVS_AKSK="-Dpc.ak=ak-pc-test -Dpc.sk=KWBTBJJZTmZWb1F00lK1psg+2RMvRApY5uSDt7u1wpg="

java ${ENVS_PMS} ${ENVS_AKSK} -classpath pc-test/target/pc-test-0.1.0-jar-with-dependencies.jar com.cloud.pc.PBucketTest $@

