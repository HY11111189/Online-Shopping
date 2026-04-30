#!/bin/sh
set -e

# Install the shared module once so service-local spring-boot:run can resolve it.
mvn -f pom.xml -pl shared-lib -am install -DskipTests
