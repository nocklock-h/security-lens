# ==============================
# 1. BUILD STAGE
# ==============================
FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /app

COPY gradlew .
COPY gradle ./gradle
COPY build.gradle .
COPY settings.gradle .

RUN chmod +x gradlew

COPY src ./src

RUN ./gradlew clean bootJar -x test


# ==============================
# 2. RUNTIME STAGE
# ==============================
FROM eclipse-temurin:17-jre-alpine-3.22

WORKDIR /app
 # Security Lens가 사용하는 OCR 언어 데이터
COPY tessdata ./tessdata
 # Tess4J가 필요로 하는 Linux native Tesseract library
RUN apk add --no-cache \
     tesseract-ocr \
     tesseract-ocr-dev \
     leptonica \
     leptonica-dev

COPY --from=builder /app/build/libs/*.jar /tmp/

RUN find /tmp \
        -maxdepth 1 \
        -type f \
        -name "*.jar" \
        ! -name "*-plain.jar" \
        -exec cp {} /app/app.jar \; \
    && test -f /app/app.jar

ENV PORT=8080

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
