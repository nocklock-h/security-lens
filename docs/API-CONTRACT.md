
# Security Lens — API Contract

> Current implementation reference | Updated: 2026-10-21

이 문서는 Security Lens의 현재 구현을 기준으로 이미지 분석 API 및 브라우저 보호 처리의 책임을 설명합니다.

초기 Wanted AI Championship MVP 설계 문서와 달리, 현재 시스템은 Fast Scan / Deep Analysis를 구분하며 이미지 마스킹 및 저장은 브라우저에서 처리합니다.

## 1. Architecture

```text
Browser
  │
  ├── POST /api/v1/images/analyze/fast
  │        └── OCR + Rule-based PII + EXIF
  │
  ├── POST /api/v1/images/analyze/deep
  │        └── AI-assisted contextual analysis
  │
  └── Canvas API
           └── PII Masking + Image Re-encoding
```

Backend는 이미지 분석과 위험 평가를 담당합니다.

Frontend는 탐지 결과 표시, 마스킹 영역 선택, 보호 이미지 생성을 담당합니다.

## 2. API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/v1/images/analyze` | 전체 이미지 분석 |
| POST | `/api/v1/images/analyze/fast` | OCR, 규칙 기반 개인정보 탐지, 메타데이터 분석 |
| POST | `/api/v1/images/analyze/deep` | AI를 활용한 심화 이미지 분석 |
| GET | `/api/v1/ui/messages` | 한국어/영어 UI 메시지 조회 |

### 2.1 Image Analysis Request

세 이미지 분석 API는 `multipart/form-data` 요청을 사용합니다.

```http
POST /api/v1/images/analyze/fast
Content-Type: multipart/form-data
```

Multipart field:

```text
image: File (required)
```

Controller에서는 다음 방식으로 파일을 수신합니다.

```java
@RequestPart("image") MultipartFile image
```

### 2.2 Supported Files

- JPEG (`.jpg`, `.jpeg`)
- PNG (`.png`)
- 최대 업로드 파일 크기: 30MB
- 최대 가로 또는 세로 크기: 8,000px
- 최대 전체 픽셀 수: 40,000,000

이미지 검증 과정에서는 파일명 확장자, Content-Type, 파일 시그니처 및 디코딩 가능한 이미지 구조를 확인합니다.

## 3. Image Analysis Response

이미지 분석 API는 `AnalysisResponse` 구조를 반환합니다.

```java
public record AnalysisResponse(
    int riskScore,
    RiskLevel riskLevel,
    ImageInfo image,
    List<DetectedItem> detectedItems,
    MetadataResult metadata,
    List<InferenceResult> inferences,
    String summary
) {}
```

주요 필드:

| Field | Description |
|---|---|
| `riskScore` | 개인정보 노출 위험 점수 |
| `riskLevel` | LOW / MEDIUM / HIGH / CRITICAL |
| `image` | 이미지 크기 정보 |
| `detectedItems` | 탐지된 개인정보 항목 |
| `metadata` | EXIF 등 메타데이터 분석 결과 |
| `inferences` | 개인정보 위험 추론 결과 |
| `summary` | 분석 결과 설명 |

탐지된 개인정보 항목은 다음 구조를 사용합니다.

```java
public record DetectedItem(
    String id,
    PiiType type,
    String maskedValue,
    double confidence,
    BoundingBox boundingBox
) {}
```

메타데이터 결과는 GPS, 촬영 시각, 촬영 기기 정보를 포함합니다.

```java
public record MetadataResult(
    GpsInfo gps,
    String capturedAt,
    DeviceInfo device
) {}
```

## 4. Fast Scan and Deep Analysis

### Fast Scan

Endpoint:

```http
POST /api/v1/images/analyze/fast
```

OCR, 규칙 기반 개인정보 탐지 및 이미지 메타데이터 분석을 통해 초기 결과를 제공합니다.

### Deep Analysis

Endpoint:

```http
POST /api/v1/images/analyze/deep
```

AI 기반 OCR 문맥 분석 및 이미지·문서 분석을 이용해 개인정보 탐지 결과를 보완합니다.

외부 AI API 사용에는 `OPENAI_API_KEY` 환경변수 설정이 필요합니다.

AI 분석에는 외부 서비스의 응답 지연이나 오류가 발생할 수 있습니다.

## 5. Image Protection

현재 구현에는 다음 서버 엔드포인트가 존재하지 않습니다.

```http
POST /api/v1/images/protect
```

보호 이미지 생성은 브라우저의 Canvas API로 수행합니다.

```text
Analysis Result
    ↓
Detected Bounding Boxes
    ↓
User Selection
    ↓
Canvas Masking
    ↓
Image Re-encoding
    ↓
Protected Image Download
```

선택한 개인정보 영역을 불투명하게 마스킹하고, 이미지를 다시 인코딩하여 기존 EXIF 메타데이터가 결과 이미지에 포함되지 않도록 구성합니다.

## 6. UI Messages

Endpoint:

```http
GET /api/v1/ui/messages?lang=ko
```

지원 언어:

- `ko`
- `en`

응답은 다음 구조를 사용합니다.

```json
{
  "locale": "ko",
  "messages": {
    "ui.example": "예시 메시지"
  }
}
```

위 JSON은 응답 구조를 보여주는 예시이며 실제 메시지 키는 `messages_ko.properties` 및 `messages_en.properties`에 정의되어 있습니다.

## 7. Security Considerations

- 이미지 파일에 대해 형식 및 크기 검증을 수행합니다.
- API Key는 코드에 하드코딩하지 않고 환경변수로 주입합니다.
- 개인정보 분석 결과는 UI에 표시되므로 실제 개인정보를 포함한 테스트 파일 사용에 주의해야 합니다.
- 외부 AI 분석의 정확도를 보장하지 않으며 사용자가 최종 보호 영역을 확인하도록 구성합니다.
- 서비스의 인증, 사용량 제한, 로그 보안 및 외부 AI 비용 통제는 운영 환경에서 별도로 점검해야 합니다.

## 8. Source of Truth

실제 동작과 이 문서 사이에 차이가 발생하면 다음 소스코드를 우선 확인합니다.

- `ImageController.java`
- `UiMessageController.java`
- `ImageUploadValidator.java`
- `ImageAnalysisService.java`
- `AnalysisResponse.java`
- `src/main/resources/static/app.js`

이 문서는 실제 구현과 함께 유지·갱신하는 것을 목표로 합니다.
