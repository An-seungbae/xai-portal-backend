# 1. 빌드 (Builder Stage)
# Maven과 JDK 17이 포함된 안정적인 이미지 사용
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# 2. 실행 (Runner Stage)
# 가볍고 실행에 최적화된 JRE 17 이미지 사용
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/target/*.war app.war
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.war"]
