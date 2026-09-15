# Security Lens

Wanted AI Championship 2026 출품을 목표로 하는
AI 기반 개인정보 노출 탐지 및 보호 서비스다.

## 기술 스택

- Java 17
- Spring Boot 4.1.1
- Gradle
- REST API
- Responsive Web

## 핵심 사용자 흐름

이미지 업로드
→ 개인정보 탐지
→ 개인정보 위험 분석
→ 개인정보 보호 처리

## 개발 원칙

- API 계약은 `docs/API-CONTRACT.md`를 기준으로 한다.
- API 필드명과 구조를 임의로 변경하지 않는다.
- 현재 MVP에 필요하지 않은 기능은 구현하지 않는다.
- 과도한 추상화나 디자인 패턴을 추가하지 않는다.
- Controller에는 비즈니스 로직을 작성하지 않는다.
- 개인정보 원문을 로그에 기록하지 않는다.
- 사용자가 요청하지 않은 기능을 임의로 추가하지 않는다.
- 구현 전 관련 문서를 먼저 확인한다.
- Domain 및 API가 특정 AI/OCR Provider SDK에 직접 종속되지 않도록 한다.
- 외부 Provider 객체를 Controller 또는 API Response로 노출하지 않는다.
- Provider 교체를 위한 최소한의 Interface만 사용하고 과도한 추상화는 금지한다.

## 프로젝트 목표

현재 MVP의 핵심 경험은 하나다.

"사진을 올리면 AI가 내가 무엇을 노출하고 있는지 보여주고,
안전한 사진으로 바꿔준다."

이 경험에 직접 필요하지 않은 기능은 Championship 이후로 미룬다.

## API

현재 MVP의 주요 API는 다음 두 개다.

- `POST /api/v1/images/analyze`
- `POST /api/v1/images/protect`

상세 계약은 `docs/API-CONTRACT.md`를 따른다.

## Cost Safety Rules

- 개발 단계에서는 가능한 경우 Mock 응답을 사용한다.
- 외부 AI API 호출은 필요한 경우에만 수행한다.
- 하나의 사용자 분석 요청에서 불필요한 반복 AI 호출을 금지한다.
- LLM output token 수에 상한을 둔다.
- Public API에는 rate limit을 적용한다.
- API Key를 source code 또는 Git에 저장하지 않는다.
- 외부 AI Provider의 자동 충전(auto-reload)을 사용하지 않는다.
- 비용이 발생하는 새로운 외부 API를 추가하기 전에 반드시 확인한다.