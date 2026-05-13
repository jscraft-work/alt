-- 박스 단타 같은 박스권 전략에서 LLM이 매 사이클 추정한 박스 상/하단을 구조화 저장.
-- summary/rationale 자연어에 묻혀 있던 정보를 컬럼으로 끌어내, 다음 사이클 컨텍스트에
-- 깔끔하게 주입할 수 있게 한다.

ALTER TABLE trade_decision_log
    ADD COLUMN box_low NUMERIC(19, 8),
    ADD COLUMN box_high NUMERIC(19, 8),
    ADD COLUMN box_confidence NUMERIC(5, 4);
