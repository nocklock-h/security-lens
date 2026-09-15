# Security Lens API Contract v0.1

Security Lens MVP의 Frontend ↔ Backend 간 API 계약을 정의한다.

이 문서에 정의된 Endpoint, Request/Response 구조, 필드명, Enum 값은
명시적인 설계 변경 없이 임의로 변경하지 않는다.

Wanted AI Championship 2026 MVP에서는
최소한의 API만 구현하고 과도한 기능 확장을 하지 않는다.

---

# 1. MVP 핵심 사용자 흐름

Security Lens의 핵심 사용자 경험은 다음과 같다.

```text
이미지 업로드
    ↓
개인정보 탐지
    ↓
Metadata 탐지
    ↓
Privacy Risk 분석
    ↓
AI 위험 추론
    ↓
분석 결과 확인
    ↓
안전한 이미지 만들기
    ↓
개인정보 마스킹
    ↓
Metadata 제거
    ↓
보호된 이미지 반환

MVP에서는 이 흐름을 완성하는 것에 집중한다.

2. API 목록

MVP에서는 다음 두 개의 API를 사용한다.

POST /api/v1/images/analyze
POST /api/v1/images/protect

각 API의 역할은 다음과 같다.

API	역할
POST /api/v1/images/analyze	이미지 개인정보 및 위험 분석
POST /api/v1/images/protect	개인정보 마스킹 및 Metadata 제거
3. 공통 규칙
3.1 Naming Convention

Java 클래스명:

PascalCase

예:

AnalysisResponse
DetectedItem
BoundingBox
MetadataResult

Java 필드명 및 JSON 필드명:

camelCase

예:

riskScore
riskLevel
detectedItems
boundingBox
maskedValue
4. 이미지 분석 API
4.1 Endpoint
POST /api/v1/images/analyze
4.2 Content-Type
multipart/form-data
4.3 Request

Multipart Field:

image

Spring Boot 기준:

@RequestPart("image") MultipartFile image

또는

@RequestParam("image") MultipartFile image

중 하나를 사용한다.

MVP에서는 구현 시 한 가지 방식으로 통일한다.

4.4 지원 이미지 형식

MVP 지원 형식:

JPG
JPEG
PNG

지원 MIME Type:

image/jpeg
image/png
4.5 최대 파일 크기
10MB

10MB를 초과하는 이미지 요청은 실패 처리한다.

5. 이미지 분석 성공 Response

HTTP Status:

200 OK

Content-Type:

application/json

Response Example:

{
  "riskScore": 82,
  "riskLevel": "CRITICAL",

  "image": {
    "width": 1920,
    "height": 1080
  },

  "detectedItems": [
    {
      "id": "pii-1",
      "type": "PHONE",
      "maskedValue": "010-****-1234",
      "confidence": 0.98,
      "boundingBox": {
        "x": 0.10,
        "y": 0.22,
        "width": 0.25,
        "height": 0.04
      }
    },
    {
      "id": "pii-2",
      "type": "ADDRESS",
      "maskedValue": "서울특별시 서초구 ***",
      "confidence": 0.93,
      "boundingBox": {
        "x": 0.08,
        "y": 0.34,
        "width": 0.42,
        "height": 0.05
      }
    }
  ],

  "metadata": {
    "gpsDetected": true,
    "capturedAtDetected": true,
    "deviceInfoDetected": false
  },

  "inferences": [
    {
      "type": "RESIDENCE_LOCATION",
      "level": "HIGH",
      "description": "주소와 위치정보가 함께 포함되어 있어 거주지역이 추정될 가능성이 높습니다."
    }
  ],

  "summary": "이 이미지는 개인정보 노출 위험이 높습니다."
}
6. AnalysisResponse

분석 API의 최상위 Response 객체다.

AnalysisResponse

- int riskScore
- RiskLevel riskLevel
- ImageInfo image
- List<DetectedItem> detectedItems
- MetadataResult metadata
- List<InferenceResult> inferences
- String summary

Java 구조 예시:

public record AnalysisResponse(
        int riskScore,
        RiskLevel riskLevel,
        ImageInfo image,
        List<DetectedItem> detectedItems,
        MetadataResult metadata,
        List<InferenceResult> inferences,
        String summary
) {
}

Record 사용 여부는 구현 단계에서 결정하되
API Response 구조는 변경하지 않는다.

7. Risk Score

riskScore는 이미지 전체의 개인정보 노출 위험도를 나타낸다.

범위:

0 ~ 100

등급:

Score	RiskLevel
0 ~ 24	LOW
25 ~ 49	MEDIUM
50 ~ 74	HIGH
75 ~ 100	CRITICAL

예:

{
  "riskScore": 82,
  "riskLevel": "CRITICAL"
}
8. Risk Score 계산 원칙

Risk Score는 LLM이 직접 결정하지 않는다.

기본 처리 구조:

Detected PII
     +
Metadata
     +
Risk Weight
     ↓
RiskService
     ↓
riskScore
     ↓
riskLevel

LLM의 주요 역할은 다음으로 제한한다.

탐지된 정보 조합 해석
+
추가 개인정보 위험 추론
+
사용자 친화적인 위험 설명
+
Summary 생성

이를 통해 같은 이미지 분석 시
Risk Score가 지나치게 달라지는 문제를 방지한다.

9. ImageInfo

분석한 원본 이미지의 기본 크기 정보다.

{
  "width": 1920,
  "height": 1080
}

DTO:

ImageInfo

- int width
- int height
10. DetectedItem

이미지에서 탐지된 하나의 개인정보 항목이다.

예:

{
  "id": "pii-1",
  "type": "PHONE",
  "maskedValue": "010-****-1234",
  "confidence": 0.98,
  "boundingBox": {
    "x": 0.10,
    "y": 0.22,
    "width": 0.25,
    "height": 0.04
  }
}

DTO:

DetectedItem

- String id
- PiiType type
- String maskedValue
- double confidence
- BoundingBox boundingBox
11. PiiType

MVP P0 대상:

PHONE
EMAIL
ADDRESS
PERSON_NAME
RRN
ACCOUNT_NUMBER

추후 P1:

LICENSE_PLATE
QR_CODE
BARCODE

Java Enum:

public enum PiiType {

    PHONE,
    EMAIL,
    ADDRESS,
    PERSON_NAME,
    RRN,
    ACCOUNT_NUMBER,

    LICENSE_PLATE,
    QR_CODE,
    BARCODE
}

MVP 초기 개발에서는 P0 기능을 우선 구현한다.

12. 개인정보 원문 처리 원칙

API Response에는 가능한 한 개인정보 원문을 반환하지 않는다.

잘못된 예:

010-1234-5678

올바른 예:

010-****-5678

따라서 필드명도 다음을 사용한다.

maskedValue

다음 이름은 사용하지 않는다.

value
rawValue
originalValue

개인정보 원문은 서버 로그에도 기록하지 않는다.

예:

// 금지
log.info("Detected phone: {}", phoneNumber);

대신 필요한 경우 다음처럼 기록한다.

log.info("PHONE detected");
13. confidence

각 개인정보 탐지 결과의 신뢰도를 나타낸다.

범위:

0.0 ~ 1.0

예:

{
  "confidence": 0.98
}

MVP에서는 탐지 방식에 따라
고정값 또는 분석 결과 기반 값을 사용할 수 있다.

14. BoundingBox

이미지에서 개인정보가 발견된 위치를 나타낸다.

{
  "x": 0.10,
  "y": 0.22,
  "width": 0.25,
  "height": 0.04
}

DTO:

BoundingBox

- double x
- double y
- double width
- double height

좌표 범위:

0.0 ~ 1.0

Pixel 좌표를 직접 반환하지 않는다.

예:

x = 0.10

의 의미:

이미지 전체 Width의 10% 위치에서 시작

예:

width = 0.25

의 의미:

이미지 전체 Width의 25% 크기

이 방식을 사용하는 이유는
PC와 모바일에서 이미지가 서로 다른 크기로 표시되어도
동일한 개인정보 영역에 BoundingBox를 표시하기 위함이다.

15. Metadata

이미지 내부에 숨어 있는 Metadata 존재 여부를 분석한다.

Response:

{
  "gpsDetected": true,
  "capturedAtDetected": true,
  "deviceInfoDetected": false
}

DTO:

MetadataResult

- boolean gpsDetected
- boolean capturedAtDetected
- boolean deviceInfoDetected

MVP에서는 다음 세 가지 Metadata를 우선 분석한다.

GPS
촬영 시각
촬영 기기 정보

초기 MVP에서는 개인정보 최소화를 위해
Metadata의 상세 원문보다 존재 여부를 중심으로 반환한다.

16. AI Inference

Security Lens의 Attacker Lens에 해당하는 결과다.

단순히 개인정보를 탐지하는 것이 아니라

이 정보를 본 상대방이
추가적으로 무엇을 추론할 수 있는가?

를 분석한다.

Response Example:

{
  "type": "RESIDENCE_LOCATION",
  "level": "HIGH",
  "description": "주소와 위치정보가 함께 포함되어 있어 거주지역이 추정될 가능성이 높습니다."
}

DTO:

InferenceResult

- String type
- InferenceLevel level
- String description
17. InferenceLevel

AI 추론 결과의 신뢰 또는 위험 수준을 나타낸다.

public enum InferenceLevel {
    LOW,
    MEDIUM,
    HIGH
}

RiskLevel과 InferenceLevel은 서로 다른 개념이므로
별도의 Enum을 사용한다.

RiskLevel:

이미지 전체 위험도

InferenceLevel:

특정 추론 결과의 위험 또는 가능성 수준
18. AI 추론 표현 원칙

AI 추론 결과는 사실처럼 단정하지 않는다.

잘못된 표현:

사용자의 거주지는 서울 서초구입니다.

올바른 표현:

주소와 위치정보를 통해 거주지역이 추정될 가능성이 높습니다.

잘못된 표현:

이 사용자는 OO 회사 직원입니다.

올바른 표현:

회사 로고와 사원증 정보로 인해 소속 조직이 추정될 가능성이 있습니다.

Security Lens는 추론 결과와 사실을 명확히 구분한다.

19. summary

사용자가 분석 결과를 빠르게 이해할 수 있도록
전체 위험을 한 문장으로 설명한다.

예:

{
  "summary": "이 이미지는 개인정보 노출 위험이 높습니다."
}

또는:

{
  "summary": "전화번호와 위치정보가 포함되어 있어 공유 전 보호 처리를 권장합니다."
}

과도하게 긴 설명을 반환하지 않는다.

20. 개인정보 보호 API

사용자가 분석 결과를 확인한 뒤

안전한 이미지 만들기

버튼을 누르면 호출한다.

Endpoint:

POST /api/v1/images/protect
21. Protect Content-Type
multipart/form-data

Multipart Field는 두 개를 사용한다.

image
request
22. Protect Request - image

원본 이미지 파일이다.

Field Name: image
Type: File
Required: true
23. Protect Request - request

보호 처리 설정이다.

Content-Type:

application/json

Example:

{
  "regions": [
    {
      "x": 0.10,
      "y": 0.22,
      "width": 0.25,
      "height": 0.04
    },
    {
      "x": 0.08,
      "y": 0.34,
      "width": 0.42,
      "height": 0.05
    }
  ],
  "removeMetadata": true
}

DTO:

ProtectRequest

- List<BoundingBox> regions
- boolean removeMetadata
24. Protect 처리 흐름
원본 이미지
    ↓
regions 확인
    ↓
BoundingBox 영역 마스킹
    ↓
removeMetadata 확인
    ↓
EXIF Metadata 제거
    ↓
새로운 이미지 생성
    ↓
사용자에게 반환

MVP에서는 개인정보 마스킹 방법을 한 가지로 통일한다.

우선순위:

Blur 또는 Mosaic

구현 난이도를 고려하여 하나를 선택한다.

25. Protect 성공 Response

HTTP Status:

200 OK

Content-Type:

image/png

Response Body:

보호 처리된 이미지 Binary

JSON Response를 반환하지 않는다.

Frontend는 반환된 이미지 Binary를 Blob으로 받아
화면에 표시하거나 사용자가 저장할 수 있도록 한다.

26. Error Response

모든 JSON 기반 API 오류는 다음 구조를 사용한다.

{
  "code": "UNSUPPORTED_FILE_TYPE",
  "message": "지원하지 않는 이미지 형식입니다."
}

DTO:

ApiErrorResponse

- String code
- String message
27. Error Code

MVP에서는 다음 Error Code를 사용한다.

INVALID_REQUEST

EMPTY_IMAGE

UNSUPPORTED_FILE_TYPE

FILE_TOO_LARGE

IMAGE_ANALYSIS_FAILED

AI_ANALYSIS_FAILED

IMAGE_PROTECTION_FAILED

INTERNAL_SERVER_ERROR
28. HTTP Status
400 Bad Request

잘못된 Request

예:

이미지 미첨부
잘못된 Request 형식
413 Payload Too Large
10MB를 초과한 이미지
415 Unsupported Media Type
지원하지 않는 이미지 형식
500 Internal Server Error
서버 내부 처리 실패
이미지 처리 실패
502 Bad Gateway
외부 AI API 호출 실패
29. Java DTO 목록

MVP에서 사용할 주요 DTO:

AnalysisResponse

ImageInfo

DetectedItem

BoundingBox

MetadataResult

InferenceResult

ProtectRequest

ApiErrorResponse
30. Enum 목록
RiskLevel

InferenceLevel

PiiType
31. RiskLevel
public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
32. InferenceLevel
public enum InferenceLevel {
    LOW,
    MEDIUM,
    HIGH
}
33. PiiType
public enum PiiType {

    PHONE,
    EMAIL,
    ADDRESS,
    PERSON_NAME,
    RRN,
    ACCOUNT_NUMBER,

    LICENSE_PLATE,
    QR_CODE,
    BARCODE
}
34. Backend 기본 구조

MVP의 기본 Backend 구조는 다음과 같다.

controller

    ImageController


service

    ImageAnalysisService
    OcrService
    PiiDetectionService
    MetadataService
    RiskService
    AiInferenceService
    ImageProtectionService


dto

    AnalysisResponse
    ImageInfo
    DetectedItem
    BoundingBox
    MetadataResult
    InferenceResult
    ProtectRequest
    ApiErrorResponse


domain

    RiskLevel
    InferenceLevel
    PiiType


client

    AiClient


exception

    GlobalExceptionHandler

구현 과정에서 반드시 필요한 경우가 아니라면
추가 계층이나 패턴을 만들지 않는다.

35. Analyze 처리 흐름
ImageController
       ↓
ImageAnalysisService
       │
       ├── OcrService
       │
       ├── PiiDetectionService
       │
       └── MetadataService
       ↓
RiskService
       ↓
AiInferenceService
       ↓
AnalysisResponse
36. Protect 처리 흐름
ImageController
       ↓
ImageProtectionService
       │
       ├── 개인정보 영역 마스킹
       │
       └── Metadata 제거
       ↓
Protected Image
37. Controller 구현 원칙

Controller는 다음 역할만 한다.

HTTP Request 수신

Request 검증

Service 호출

HTTP Response 반환

Controller 내부에서 다음 작업을 직접 수행하지 않는다.

OCR

Regex 개인정보 탐지

EXIF 분석

LLM 호출

Risk 계산

이미지 마스킹
38. 개인정보 처리 원칙

Security Lens 자체가 개인정보 보호 서비스이므로
다음 원칙을 지킨다.

원본 이미지

MVP에서는 분석을 위해 받은 원본 이미지를
영구 저장하지 않는다.

로그

개인정보 원문을 로그에 기록하지 않는다.

Response

필요 이상의 개인정보 원문을 반환하지 않는다.

AI

외부 AI API로 전달되는 개인정보를 가능한 한 최소화한다.

39. MVP 범위

현재 MVP의 핵심 경험은 다음 하나다.

사진을 올리면

→ 개인정보 위험을 보여주고

→ 상대방이 무엇을 알 수 있는지 설명하고

→ 안전한 사진으로 바꿔준다.

이 경험에 직접적으로 필요하지 않은 기능은
Wanted AI Championship 이후로 미룬다.

현재 MVP 범위에서 제외한다.

회원가입

로그인

사용자 프로필

분석 기록 저장

Dashboard

Google 계정 연결

SNS 연결

Dark Web 탐색

PDF 분석

Word 분석

모바일 Native App

Browser Extension

기업용 DLP
40. API 변경 원칙

API Contract를 변경해야 하는 경우
다음 순서로 진행한다.

문제 확인
    ↓
API 변경 필요성 판단
    ↓
API-CONTRACT.md 수정
    ↓
DTO 수정
    ↓
Backend 수정
    ↓
Frontend 수정
    ↓
Test

코드를 먼저 수정하고
API Contract를 나중에 맞추지 않는다.

41. MVP 개발 우선순위

구현 순서는 다음을 기본으로 한다.

1. Mock Analyze API

2. Metadata 분석

3. OCR

4. 개인정보 탐지

5. Risk Engine

6. AI Inference

7. 개인정보 BoundingBox 표시

8. Protect API

9. 개인정보 마스킹

10. Metadata 제거

11. UI/UX 개선

12. 배포 및 테스트
42. 현재 첫 번째 구현 목표

현재 개발 단계에서는 실제 분석 기능을 구현하지 않는다.

첫 번째 목표:

Frontend 또는 Postman

    ↓

이미지 업로드

    ↓

POST /api/v1/images/analyze

    ↓

Spring Boot

    ↓

Mock AnalysisResponse

    ↓

HTTP 200

이 흐름이 정상적으로 동작하는 것을 먼저 확인한다.

이후 Mock 구현을 실제 기능으로 하나씩 교체한다.

43. 최종 원칙

새로운 기능을 구현하기 전에 다음 질문을 한다.

이 기능이
"사진을 올리고 → 위험을 확인하고 → 안전하게 만든다"
라는 Security Lens MVP의 핵심 경험에 반드시 필요한가?

필요하지 않다면 Championship 이후로 미룬다.

`RiskLevel`과 AI 추론의 `level`을 분리해서,

```text
RiskLevel
→ 이미지 전체 위험도

InferenceLevel
→ AI가 추론한 특정 위험의 수준

# 외부 서비스 종속성 원칙

Security Lens의 Domain 및 API Contract는
특정 AI, OCR, Cloud 서비스 제공자에게 종속되지 않는다.

외부 서비스의 Request/Response 객체를
Controller, Domain, API Response에서 직접 사용하지 않는다.

외부 서비스 결과는 반드시 Security Lens 내부 모델로 변환한다.

예:

External OCR Response
→ OcrResult
→ DetectedItem

External LLM Response
→ AiInferenceResult
→ InferenceResult

MVP에서는 각 기능당 하나의 실제 Provider만 구현한다.

다른 Provider로의 교체 가능성을 위해
필요한 최소 수준의 Interface만 사용하며,
과도한 Factory 또는 추상화 계층은 만들지 않는다.