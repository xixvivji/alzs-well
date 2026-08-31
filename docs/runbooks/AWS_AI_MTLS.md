# 업무 EC2 ↔ AI EC2 mTLS 운영 절차

업무 EC2와 AI EC2 사이의 검색·AI 지원 요청은 사설망이라는 이유만으로 평문 HTTP를 허용하지
않는다. AI EC2는 8443 mTLS gateway만 host에 공개하고 FastAPI 8000은 Docker internal network에만
노출한다.

## 인증서 계약

- 서버 인증서 SAN: 사설 Route 53 이름 `ai.internal`
- 서버 인증서 EKU: `serverAuth`
- 업무 EC2 client 인증서 EKU: `clientAuth`
- 운영·staging CA와 인증서를 분리하고 개인키를 저장소·AMI·user-data에 넣지 않음
- Spring client keystore와 truststore: PKCS#12
- AI gateway: PEM server certificate/key와 client CA

AWS Private CA 또는 조직이 승인한 사설 PKI로 발급한다. 자체서명 인증서는 로컬 검증에서만
사용한다. Secrets Manager 또는 승인된 secret 배포 단계가 파일을 인스턴스의 제한된 경로에
내리고, Compose에는 경로와 비밀번호만 주입한다.

## 배포 확인

1. server 인증서의 SAN과 만료일, client 인증서의 EKU를 확인한다.
2. 업무 EC2에서 client 인증서 없이 8443 연결이 거절되는지 확인한다.
3. 승인 client 인증서로 `/health`와 `/readiness`를 호출한다.
4. FastAPI 8000이 EC2 host와 다른 보안그룹에서 직접 접근되지 않는지 확인한다.
5. 잘못된 CA·만료 인증서·회수된 client 인증서가 모두 거절되는지 확인한다.
6. Spring `/api/v1/system/ai-readiness`와 core readiness 분리를 확인한다.

## 회전과 복구

만료 30일 전 새 인증서를 발급하고 trust overlap 기간에 새·기존 CA를 함께 신뢰한다. 업무
EC2 client를 먼저 교체하고 AI gateway server 인증서를 교체한 뒤 기존 CA를 제거한다. 교체
실패 시 직전 Secrets Manager version과 immutable image digest로 복구한다. 인증서 원문·비밀번호는
CloudWatch와 배포 artifact에 남기지 않고, serial·issuer·notAfter·배포시각만 감사 증적으로 남긴다.
