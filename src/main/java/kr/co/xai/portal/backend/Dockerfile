# ==========================================
# 1단계: 빌드 (Builder Stage)
# ==========================================
FROM maven:3.8.5-openjdk-17 AS builder

# 작업 디렉토리 설정
WORKDIR /app

# 라이브러리 설치에 필요한 파일만 먼저 복사 (캐시 효율화)
COPY pom.xml .

# 소스 코드 복사
COPY src ./src

# Maven 빌드 실행 (테스트 건너뛰기, 로그 줄이기)
# eGovFrame 리포지토리 설정이 pom.xml에 있으므로 의존성 다운로드 포함
RUN mvn clean package -DskipTests

# ==========================================
# 2단계: 실행 (Runner Stage)
# ==========================================
FROM openjdk:17-jdk-slim

# 작업 디렉토리 설정
WORKDIR /app

# 빌드 단계에서 생성된 WAR 파일만 복사하여 이름을 app.war로 변경
# backend-1.0.0.war 등의 이름이 변경되어도 대응 가능하도록 와일드카드 사용
COPY --from=builder /app/target/*.war app.war

# 보안을 위해 설정 파일이 위치할 외부 마운트 포인트 지정 (선택 사항)
VOLUME /tmp

# 컨테이너 외부로 노출할 포트 (Spring Boot 기본 포트)
EXPOSE 8080

# 애플리케이션 실행 명령어
# Jasypt 암호화 키 등은 실행 시 환경변수(-e)로 주입받도록 설정
ENTRYPOINT ["java", "-jar", "app.war"]