# Security Lens

Wanted AI Championship 2026 출품을 첫 번째 목표로 하는  
AI 기반 개인정보 노출 탐지 및 보호 서비스다.

Championship 이후에는 실제 사용 가능한 개인정보·보안 서비스로
확장하는 것을 목표로 한다.

---

# 1. Tech Stack

- Java 17
- Spring Boot 4.1.1
- Gradle
- REST API
- Responsive Web
- AI / LLM
- OCR

---

# 2. Product Goal

현재 MVP의 핵심 사용자 경험은 하나다.

> "사진을 올리면 AI가 내가 무엇을 노출하고 있는지 보여주고,
> 안전한 사진으로 바꿔준다."

핵심 흐름은 다음과 같다.

```text
이미지 업로드
→ 이미지 검증
→ Metadata 분석
→ OCR
→ 개인정보 탐지
→ 개인정보 위험 분석
→ AI 기반 추론
→ 개인정보 보호 처리
```

이 경험에 직접 필요하지 않은 기능은
Wanted AI Championship 이후로 미룬다.

---

# 3. MVP Scope Rules

현재 최우선 목표는
Wanted AI Championship 2026에서 동작 가능한 MVP를 완성하는 것이다.

새로운 기능을 구현하기 전에 항상 다음을 확인한다.

> "이 기능이 사진을 분석하고,
> 개인정보 노출 위험을 보여주고,
> 안전한 이미지로 만들어주는 핵심 경험에 필요한가?"

필요하지 않다면 MVP 범위에서 제외한다.

현재 우선순위는 다음과 같다.

```text
1. 이미지 업로드
2. 이미지 검증
3. Metadata 분석
4. OCR
5. PII 탐지
6. 위험도 계산
7. AI / LLM 추론
8. 분석 결과 제공
9. 개인정보 보호 이미지 생성
```

다음과 같은 기능은 핵심 기능보다 먼저 구현하지 않는다.

- 관리자 페이지
- 복잡한 회원 시스템
- 불필요한 통계 시스템
- 과도한 인프라
- 미래 확장만을 위한 추상화
- 현재 사용하지 않는 기능
- Championship MVP와 무관한 기능

사용자가 요청하지 않은 기능을 임의로 추가하지 않는다.

---

# 4. API Contract

API 계약은 반드시 다음 문서를 기준으로 한다.

```text
docs/API-CONTRACT.md
```

현재 MVP의 주요 API는 다음과 같다.

```text
POST /api/v1/images/analyze
POST /api/v1/images/protect
```

다음 사항을 지킨다.

- API 필드명을 임의로 변경하지 않는다.
- API Response 구조를 이유 없이 변경하지 않는다.
- 기존 API Contract에 영향을 주는 변경은 반드시 명시한다.
- 외부 Provider의 Response 객체를 API Response로 직접 노출하지 않는다.
- API 변경 전에 `docs/API-CONTRACT.md`를 확인한다.

---

# 5. Architecture Rules

기본적인 요청 흐름은 다음 구조를 따른다.

```text
Controller
    ↓
Service
    ↓
Analyzer / Provider / Domain Logic
    ↓
DTO / Response
```

현재 Security Lens의 대표적인 흐름은 다음과 같다.

```text
ImageController
      ↓
ImageAnalysisService
      ↓
├─ ImageMetadataService
├─ OcrService
├─ PiiAnalyzer
├─ AiInferenceAnalyzer
└─ RiskScoreCalculator
```

단, 아직 필요하지 않은 클래스는 미리 만들지 않는다.

## Controller

Controller는 HTTP 요청과 응답 처리를 담당한다.

Controller에는 다음과 같은 로직을 작성하지 않는다.

- OCR 구현
- 개인정보 탐지
- 위험도 계산
- AI 추론
- 복잡한 이미지 처리
- 핵심 비즈니스 로직

## Service

Service는 Use Case와 전체 처리 흐름을 조정한다.

예:

```text
Metadata 분석
→ OCR
→ 개인정보 탐지
→ 위험도 계산
→ AI 추론
→ Response 생성
```

Service 하나에 모든 세부 알고리즘을 몰아넣지 않는다.

## Analyzer

Analyzer는 특정 분석 책임을 담당한다.

예:

```text
PiiAnalyzer
AiInferenceAnalyzer
```

하나의 Analyzer가 여러 독립적인 책임을 가지지 않도록 한다.

## DTO

DTO는 데이터 전달 목적으로 사용한다.

DTO에 복잡한 비즈니스 로직을 넣지 않는다.

적절한 경우 Java `record`를 사용한다.

---

# 6. Provider Independence

Domain과 Public API는
특정 OCR / AI Provider SDK에 직접 종속되지 않도록 한다.

예:

```text
Tesseract
OpenAI
Google Vision
AWS Rekognition
기타 외부 AI Provider
```

Provider 전용 객체를 다음 계층으로 직접 전달하지 않는다.

```text
Controller
API Response
Domain Model
```

Provider 교체가 실제로 필요한 경우
최소한의 Interface를 사용할 수 있다.

단, 미래에 교체할 수도 있다는 이유만으로
불필요한 다음 구조를 추가하지 않는다.

```text
Factory
Adapter
Strategy
Interface
Abstract Layer
```

과도한 추상화를 금지한다.

---

# 7. Clean Code Rules

Java 17 / Spring Boot 기준으로
가독성과 유지보수성을 우선한다.

다음 원칙을 적용한다.

- 클래스는 명확한 책임을 가진다.
- 메서드는 가능한 하나의 책임을 가진다.
- 이름만 읽어도 역할을 이해할 수 있도록 작성한다.
- 의미 없는 축약어를 피한다.
- 중복 코드를 줄인다.
- Magic Number를 피한다.
- 불필요한 중첩을 줄인다.
- 적절한 경우 Early Return을 사용한다.
- 지나치게 긴 메서드는 책임 단위로 분리한다.
- 사용하지 않는 코드는 제거한다.
- 주석 처리된 죽은 코드를 남기지 않는다.
- 단순한 해결책을 우선한다.
- Premature Optimization을 피한다.
- 불필요한 디자인 패턴을 사용하지 않는다.
- 테스트하기 쉬운 구조를 선호한다.

새로운 클래스나 Interface는
실제 책임 분리가 필요할 때만 추가한다.

---

# 8. Java / Spring Rules

- Java 17 기능을 상황에 맞게 적절하게 사용한다.
- DTO에는 적절한 경우 `record`를 사용한다.
- Dependency Injection은 생성자 주입을 기본으로 한다.
- 의존성 필드는 가능한 `final`로 선언한다.
- Field Injection은 사용하지 않는다.
- Controller에 비즈니스 로직을 넣지 않는다.
- Service와 Analyzer의 책임을 명확히 한다.
- 예외 처리를 일관된 구조로 관리한다.
- API 예외는 가능한 `GlobalExceptionHandler`를 통해 처리한다.
- `Optional`을 반환값이나 의미 있는 경우에 사용하며 남용하지 않는다.
- 단순히 NullPointerException을 피하기 위한 목적으로 Optional을 무분별하게 사용하지 않는다.
- Deprecated API는 특별한 이유가 없다면 사용하지 않는다.
- 외부 라이브러리 메서드는 현재 프로젝트 Dependency 버전에서 지원되는지 확인한다.

---

# 9. Configuration & Properties Rules

환경별로 달라질 수 있거나
운영 중 조정할 가능성이 있는 값을 Java Source에 직접 하드코딩하지 않는다.

Spring Boot의 Externalized Configuration을 활용한다.

가능한 경우 다음을 사용한다.

```text
application.yml
application.properties
환경변수
@ConfigurationProperties
```

설정값의 예:

```text
OCR datapath
OCR language
OCR DPI
OCR page segmentation mode
AI Provider URL
AI Model
Timeout
파일 최대 크기
Token 제한
Rate Limit
외부 API URL
```

이미 해당 영역의 Properties 클래스가 존재한다면
새로운 설정 방식을 만들지 않고 기존 클래스를 우선 확장한다.

예:

```text
OcrProperties가 이미 존재한다면

OCR 관련 설정은 가능한 한
OcrProperties를 통해 관리한다.
```

다음과 같은 구현은 피한다.

```java
//tesseract.setVariable("user_defined_dpi", "300");
//tesseract.setPageSegMode(6);
```

튜닝 가능한 값이라면 다음과 같이 관리한다.

```java
/**tesseract.setVariable(
        "user_defined_dpi",
        String.valueOf(ocrProperties.getDpi())
);

tesseract.setPageSegMode(
        ocrProperties.getPageSegMode()
);
**/
```

단, 모든 값이나 상수를 무조건 Properties로 이동하지 않는다.

다음과 같은 값은 Domain Rule 또는 코드 상수로 관리할 수 있다.

```text
PiiType
RiskLevel
전화번호 탐지 규칙
위험도 산정 규칙
상태 Enum
```

다음 원칙을 구분한다.

```text
환경 / 운영 / 튜닝 값
→ Properties

비즈니스 규칙
→ Domain / Service / Analyzer

불변 코드 상수
→ Constant / Enum
```

Secret은 Properties 파일에 평문으로 저장하지 않는다.

---

# 10. Comments Rules

주석을 많이 작성하는 것이 목표가 아니다.

코드 자체가 가능한 한
WHAT을 설명하도록 작성한다.

주석은 주로 WHY를 설명한다.

즉:

> 왜 이런 구현을 선택했는가?

를 설명한다.

다음과 같은 주석은 작성하지 않는다.

```java
// 이름을 가져온다.
String name = user.getName();
```

코드만 읽어도 이해할 수 있기 때문이다.

다음과 같은 경우에는 주석을 작성할 수 있다.

- 보안상 중요한 처리
- 외부 라이브러리의 제약
- Provider 특성
- 일반적이지 않은 구현
- 성능상의 선택
- 호환성 처리
- Workaround
- 복잡한 알고리즘
- 오해하기 쉬운 코드

좋은 예:

```java
// BufferedImage 변환 과정에서 DPI 정보가 유실될 수 있으므로
// Tesseract가 저해상도로 판단하지 않도록 OCR DPI를 명시한다.
/*
tesseract.setVariable(
        "user_defined_dpi",
        String.valueOf(ocrProperties.getDpi())
);
 */
```

나쁜 예:

```java
// DPI를 설정한다.
/*
tesseract.setVariable(
        "user_defined_dpi",
        String.valueOf(ocrProperties.getDpi())
);
 */
```

TODO 또는 FIXME를 작성하는 경우
왜 필요한지와 향후 작업 내용을 명확하게 작성한다.

---

# 11. Security Rules

Security Lens는 개인정보와 이미지를 다루는 서비스이므로
일반적인 웹 서비스보다 보안을 엄격하게 고려한다.

설계와 구현 시 다음 기준을 참고한다.

```text
OWASP Top 10
OWASP ASVS
OWASP API Security Top 10
CWE
SEI CERT Oracle Coding Standard for Java
NIST Security Guidelines
ISO/IEC 27001 계열 보안 원칙
ISO/IEC 27701 개인정보보호 원칙
```

위 기준은 설계와 구현 시 참고 기준이다.

실제 인증이나 공식 적합성 검증 없이
다음과 같은 표현을 사용하지 않는다.

```text
ISO 인증 완료
ISO/IEC 27001 인증 서비스
공식적으로 ISO 규격을 준수하는 서비스
```

보안 구현 시 최소한 다음을 검토한다.

```text
입력값 검증
파일 업로드 검증
MIME Type 검증
확장자 검증
파일 크기
Path Traversal
Injection
API 인증 / 인가
Rate Limiting
Secret 관리
예외 메시지 정보 노출
개인정보 Masking
```

---

# 12. Privacy Rules

Security Lens는 개인정보 보호 서비스이므로
개인정보 최소수집 원칙을 따른다.

분석에 필요하지 않은 개인정보는
가능한 수집하거나 저장하지 않는다.

개인정보를 영구 저장하는 기능은
명확한 요구사항 없이 추가하지 않는다.

특히 다음 정보를 민감정보로 취급한다.

```text
전화번호
주소
이메일
이름
주민등록번호
계좌번호
위치정보
GPS 좌표
OCR 원문
사용자가 업로드한 이미지
```

개인정보를 API Response로 반환해야 하는 경우
가능한 Masking된 값을 사용한다.

---

# 13. Logging Rules

개인정보 원문을 로그에 기록하지 않는다.

다음 데이터는 로그 출력 금지 대상으로 취급한다.

```text
OCR 전체 원문
전화번호 원문
주소 원문
이메일 원문
주민등록번호
계좌번호
개인 이름
GPS 좌표
이미지 Binary
이미지 전체 내용
```

다음과 같은 코드는
테스트 종료 후 실제 코드에 남기지 않는다.

```java
//System.out.println(extractedText);
```

개발 중 Mock 또는 테스트 데이터를 확인하기 위해
일시적으로 출력한 경우 테스트 완료 후 제거한다.

운영 로그에는 가능한 다음 정보만 기록한다.

```text
requestId
분석 성공 여부
탐지된 항목 수
처리 시간
오류 코드
Provider 호출 성공 여부
```

예:

```text
Image analysis completed.
detectedCount=3
processingTimeMs=482
```

민감정보를 로그에 출력해야만 디버깅할 수 있는 구조를 만들지 않는다.

---

# 14. File Upload Security

사용자가 업로드한 파일은 항상 신뢰하지 않는다.

최소한 다음 항목을 검토한다.

```text
파일 존재 여부
파일 크기
MIME Type
확장자
허용 이미지 포맷
실제 이미지 디코딩 가능 여부
손상 이미지
비정상 입력
```

확장자만으로 실제 이미지 여부를 판단하지 않는다.

업로드한 원본 파일을
필요 이상으로 서버에 저장하지 않는다.

임시 파일을 생성한 경우
처리가 끝난 뒤 안전하게 제거한다.

---

# 15. Secret Rules

API Key, Password, Token, Secret 등을
Java Source에 직접 작성하지 않는다.

다음 장소에도 Secret을 저장하지 않는다.

```text
Git
README.md
CLAUDE.md
Frontend Source
Test Source
Public Documentation
```

Secret은 환경변수 또는
적절한 Secret 관리 방법을 사용한다.

Secret이 포함된 파일은
Git에 Commit되지 않도록 한다.

---

# 16. Cost Safety Rules

개발 단계에서는 가능한 경우 Mock을 사용한다.

비용이 발생하는 외부 AI API는
실제로 필요한 경우에만 호출한다.

하나의 사용자 분석 요청에서
불필요하게 동일한 AI 요청을 반복하지 않는다.

LLM 사용 시 다음을 고려한다.

```text
Input Token 최소화
Output Token 상한
Timeout
Retry 제한
Context 최소화
중복 호출 방지
```

Public API에는 적절한 Rate Limit을 적용한다.

외부 AI Provider의 자동 충전 기능은
기본적으로 사용하지 않는다.

비용이 발생하는 새로운 외부 API나 서비스를 추가하기 전에
사용자에게 반드시 확인한다.

---

# 17. AI / LLM Rules

모든 분석을 LLM으로 처리하지 않는다.

명확한 규칙으로 안정적으로 처리할 수 있는 문제는
우선 Deterministic Logic을 사용한다.

예:

```text
전화번호 패턴
이메일 패턴
주민등록번호 후보
정형화된 번호 패턴
```

LLM은 다음과 같은 문제에 우선 활용한다.

```text
문맥 기반 개인정보 추론
간접적인 신원 추론
복수 정보 결합 위험 분석
위험 설명 생성
사용자 친화적인 Summary 생성
```

LLM 결과는 항상 신뢰하지 않는다.

가능한 경우 다음을 적용한다.

```text
Output Schema 검증
Enum 제한
길이 제한
Timeout
Fallback
예외 처리
Hallucination 방어
```

LLM의 응답 하나만을
중대한 보안 판단의 유일한 근거로 사용하지 않는다.

---

# 18. OCR Rules

OCR 결과는 항상 정확하지 않을 수 있다는 것을 전제로 한다.

다음 오인식 가능성을 고려한다.

```text
0 ↔ O
1 ↔ I ↔ l
한글 유사 글자
하이픈 누락
공백 삽입
특수문자 오인식
영문 / 한글 혼동
저해상도 이미지
회전 이미지
배경 노이즈
작은 글자
```

PII 탐지는 OCR이 완벽하다는 가정으로 구현하지 않는다.

OCR 설정은 가능한 `OcrProperties`에서 관리한다.

예:

```text
datapath
language
dpi
pageSegMode
```

Tesseract 전용 객체나 설정 객체를
Domain 또는 Public API에 노출하지 않는다.

MVP 단계에서 OCR 정확도를 100%로 만들기 위해
과도한 이미지 처리 파이프라인을 먼저 구현하지 않는다.

핵심 개인정보 탐지가 가능한 수준을 달성한 후
필요에 따라 정확도를 개선한다.

---

# 19. Error Handling Rules

예외를 무조건 하나의 `Exception`으로 처리하지 않는다.

가능한 경우 다음을 구분한다.

```text
사용자 입력 오류
지원하지 않는 이미지
OCR 실패
Metadata 분석 실패
외부 Provider 실패
서버 내부 오류
```

API Response에는 다음을 노출하지 않는다.

```text
Stack Trace
Local File Path
API Key
내부 Class 구조
DB Credential
Provider Secret
```

사용자에게는 이해 가능한
오류 코드와 메시지를 제공한다.

예:

```json
{
  "code": "UNSUPPORTED_IMAGE",
  "message": "지원하지 않는 이미지 형식입니다."
}
```

---

# 20. Testing Rules

기능은 다음 순서로 개발하고 검증한다.

```text
문제 정의
→ 설계
→ 구현
→ 단위 테스트
→ 통합 테스트
→ 실제 입력 테스트
→ 배포
```

핵심 기능은 정상 케이스만 테스트하지 않는다.

다음도 고려한다.

```text
빈 입력
잘못된 입력
경계값
지원하지 않는 파일
손상된 이미지
개인정보 없는 이미지
OCR 실패
Metadata 없는 이미지
Provider 실패
Timeout
예상하지 못한 응답
```

버그가 발견되면 가능한 경우
해당 버그를 재현하는 테스트를 추가한다.

---

# 21. Dependency Rules

새로운 Dependency를 추가하기 전에 확인한다.

```text
현재 기능에 실제로 필요한가?
Java / Spring 기본 기능으로 해결 가능한가?
기존 Dependency로 해결 가능한가?
라이선스 문제가 없는가?
현재 유지보수되는 라이브러리인가?
알려진 보안 취약점 위험은 없는가?
```

작은 기능 하나 때문에
불필요하게 큰 Framework를 추가하지 않는다.

사용하지 않는 Dependency는 제거한다.

---

# 22. Existing Code First

새로운 코드를 작성하기 전에
기존 프로젝트에 같은 역할을 하는 코드가 있는지 확인한다.

이미 존재하는 기능을 확인하지 않고
동일 기능을 새로 구현하지 않는다.

예:

```text
ImageMetadataService가 이미 존재한다면

MetadataAnalyzer를 새로 만들기 전에
기존 ImageMetadataService의 역할을 확인한다.
```

새로운 Service / Analyzer / DTO / Properties를 만들기 전에
기존 구현을 먼저 확인한다.

기존 클래스를 확장할 수 있다면
불필요하게 새로운 클래스를 추가하지 않는다.

---

# 23. Code Change Rules

변경 범위는 현재 요구사항을 해결하는
최소 범위로 유지한다.

사용자의 요청 없이
대규모 Refactoring을 동시에 진행하지 않는다.

코드 수정 시 다음을 먼저 확인한다.

```text
현재 호출 흐름
기존 API Contract
기존 DTO
기존 Properties
기존 Exception
관련 테스트
```

API Contract가 변경되는 경우 반드시 알려준다.

기존 동작을 변경하는 경우
무엇이 변경되는지 설명한다.

---

# 24. Development Explanation

중요한 코드나 구조를 작성 또는 수정할 때
다음 내용을 간결하게 설명한다.

```text
1. 이 코드의 역할
2. 왜 이렇게 설계했는지
3. 적용된 Clean Code 원칙
4. Java / Spring 핵심 개념
5. 보안상 고려한 부분
6. 향후 개선 가능한 부분
```

사용자가 프로젝트를 개발하면서

```text
Java
Spring Boot
REST API
Clean Code
Architecture
Security
AI / LLM
```

을 함께 학습할 수 있도록
중요한 개념을 생략하지 않는다.

단순한 다음 작업에는
불필요하게 긴 설명을 작성하지 않는다.

```text
import 추가
오타 수정
Formatting
단순 변수명 변경
반복적인 기계적 수정
```

개발 속도를 방해하지 않는 수준에서 설명한다.

---

# 25. Development Workflow

모든 기능은 다음 흐름으로 관리한다.

```text
문제 정의
→ 설계
→ 구현
→ 테스트
→ 배포
```

코드를 먼저 작성한 뒤
문제를 정의하는 방식으로 개발하지 않는다.

구현 전에 다음을 짧게라도 확인한다.

```text
무슨 문제를 해결하는가?
현재 MVP에 필요한가?
기존 코드로 해결 가능한가?
API Contract에 영향이 있는가?
보안상 문제가 없는가?
```

---

# 26. Final Engineering Principle

Security Lens는 다음 세 가지를 동시에 만족하는 코드를 목표로 한다.

```text
동작하는 코드
+
이해할 수 있는 코드
+
안전한 코드
```

Wanted AI Championship을 위한 MVP를 빠르게 완성하되,
일회성 Demo 코드만 만드는 것을 목표로 하지 않는다.

Championship 이후 실제 서비스로 발전할 수 있는
최소한의 품질을 유지한다.

하지만 미래 확장을 이유로
현재 MVP를 과도하게 복잡하게 만들지 않는다.

항상 다음 순서를 우선한다.

```text
Correctness
→ Security
→ Readability
→ Testability
→ Performance
→ Future Extensibility
```