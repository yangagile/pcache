#!/bin/bash

cd pc-common
mvn clean install -DskipTests
cd - 

cd pc-pms
mvn clean package -Dtar -DskipTests
cd - 

cd pc-pcp
mvn clean package -Pdist assembly:single -DskipTests -Dtar
cd -

cd sdk/pc-sdk-java
mvn clean install -DskipTests
cd -

cd pc-test
mvn clean package -Pdist assembly:single -DskipTests -Dtar
cd -


