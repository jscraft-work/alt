# ALT-Web 작업 순서

이 디렉터리는 `spec2.md`와 `docs/01~07`을 개발 착수용 작업 문서로 다시 쪼갠 것이다.
각 문서는 "한 번의 AI 구현 세션에서 끝낼 수 있는 범위"를 목표로 한다.

사용 규칙:

- 문서는 번호 순서대로 진행한다.
- 선행 task의 테스트가 통과하기 전에는 다음 task로 넘어가지 않는다.
- 각 task의 완료 조건은 가능하면 테스트 코드 기준으로 닫는다.
- 외부 실계좌 연동이나 배포처럼 테스트 코드만으로 닫히지 않는 항목은 문서에 별도 수동 완료 조건을 적는다.

작업 순서:

1. [01-spring-boot-bootstrap.md](/Users/jongsoobae/workspace/alt-java/tasks/01-spring-boot-bootstrap.md)
2. [02-persistence-core-schema.md](/Users/jongsoobae/workspace/alt-java/tasks/02-persistence-core-schema.md)
3. [03-persistence-trading-schema.md](/Users/jongsoobae/workspace/alt-java/tasks/03-persistence-trading-schema.md)
4. [04-auth-session-core.md](/Users/jongsoobae/workspace/alt-java/tasks/04-auth-session-core.md)
5. [05-auth-blocking-and-audit.md](/Users/jongsoobae/workspace/alt-java/tasks/05-auth-blocking-and-audit.md)
6. [06-admin-catalog-crud.md](/Users/jongsoobae/workspace/alt-java/tasks/06-admin-catalog-crud.md)
7. [07-admin-strategy-instance-lifecycle.md](/Users/jongsoobae/workspace/alt-java/tasks/07-admin-strategy-instance-lifecycle.md)
8. [08-admin-prompt-and-watchlist.md](/Users/jongsoobae/workspace/alt-java/tasks/08-admin-prompt-and-watchlist.md)
9. [09-public-dashboard-api.md](/Users/jongsoobae/workspace/alt-java/tasks/09-public-dashboard-api.md)
10. [10-public-history-news-chart-api.md](/Users/jongsoobae/workspace/alt-java/tasks/10-public-history-news-chart-api.md)
11. [11-collector-marketdata.md](/Users/jongsoobae/workspace/alt-java/tasks/11-collector-marketdata.md)
12. [12-collector-news-disclosure-macro.md](/Users/jongsoobae/workspace/alt-java/tasks/12-collector-news-disclosure-macro.md)
13. [13-trading-cycle-scheduler.md](/Users/jongsoobae/workspace/alt-java/tasks/13-trading-cycle-scheduler.md)
14. [14-trading-decision-pipeline.md](/Users/jongsoobae/workspace/alt-java/tasks/14-trading-decision-pipeline.md)
15. [15-paper-order-execution.md](/Users/jongsoobae/workspace/alt-java/tasks/15-paper-order-execution.md)
16. [16-live-order-execution.md](/Users/jongsoobae/workspace/alt-java/tasks/16-live-order-execution.md)
17. [17-ops-release-and-e2e.md](/Users/jongsoobae/workspace/alt-java/tasks/17-ops-release-and-e2e.md)

