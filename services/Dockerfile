FROM maven:3.9.9-eclipse-temurin-17 AS build

ARG MODULE
WORKDIR /workspace

COPY pom.xml pom.xml
COPY shared-lib shared-lib
COPY account-service account-service
COPY item-service item-service
COPY order-service order-service
COPY payment-service payment-service
COPY gateway-service gateway-service

RUN mvn -q -pl ${MODULE} -am -DskipTests package

FROM eclipse-temurin:17-jre

ARG MODULE
WORKDIR /app

COPY --from=build /workspace/${MODULE}/target/${MODULE}-0.0.1-SNAPSHOT.jar /app/app.jar
COPY --from=build /workspace/account-service/src/main/resources/certificates /app/certificates

ENTRYPOINT ["java","-jar","/app/app.jar"]
