# 15. paper 주문 실행과 포트폴리오 갱신

목적:

- `paper` 인스턴스의 end-to-end 사이클을 완성한다.
- 판단 결과가 주문, 체결, 포트폴리오, 대시보드 현재값으로 이어지게 한다.

선행조건:

- `14-trading-decision-pipeline.md`

참조 문서:

- `spec2.md`
- `docs/05-trading-cycle-design.md`

이번 작업 범위:

- `paper` 주문 실행기
- `MARKET` 주문 체결가를 최신 스냅샷 기준으로 결정
- `LIMIT` 주문 즉시 체결 정책 구현
- `trade_order` 생성
- `portfolio` 갱신
- `portfolio_position` 갱신
- `trade_order.portfolio_after_json` 저장
- 판단 로그와 주문 이력 연결

이번 작업에서 제외:

- KIS 실계좌 주문
- reconcile

필수 테스트 코드:

- `PaperOrderExecutorTest`: BUY/SELL, MARKET/LIMIT 실행 규칙 검증
- `PortfolioUpdateServiceTest`: 현금, 평균단가, 평가손익, 실현손익 계산 검증
- `PaperTradingCycleE2ETest`: 판단부터 주문, 포트폴리오 갱신까지 한 사이클 검증
- `PaperOrderLinkageTest`: decision log, order intent, trade order 연결 검증
- `DashboardPortfolioProjectionTest`: 대시보드 조회값이 최신 portfolio와 일치하는지 검증

완료 조건:

- `./gradlew test --tests "*PaperOrderExecutorTest" --tests "*PortfolioUpdateServiceTest" --tests "*PaperTradingCycleE2ETest" --tests "*PaperOrderLinkageTest" --tests "*DashboardPortfolioProjectionTest"`가 통과한다.
- `paper` 인스턴스는 테스트 환경에서 완전한 사이클을 끝낼 수 있다.

