package work.jscraft.alt.strategy.application;

import java.util.List;

import org.springframework.stereotype.Component;

import work.jscraft.alt.strategy.application.PromptVocabulary.GlobalVariable;
import work.jscraft.alt.strategy.application.PromptVocabulary.SourceCatalog;
import work.jscraft.alt.strategy.application.PromptVocabulary.SourceParameter;
import work.jscraft.alt.strategy.application.PromptVocabulary.StockField;
import work.jscraft.alt.strategy.application.PromptVocabulary.StocksCollection;
import work.jscraft.alt.strategy.application.PromptVocabulary.SystemVariable;

@Component
public class PromptVocabularyProvider {

    private final PromptVocabulary vocabulary = build();

    public PromptVocabulary vocabulary() {
        return vocabulary;
    }

    private static PromptVocabulary build() {
        List<SystemVariable> systemVariables = List.of(
                new SystemVariable(
                        "current_time",
                        "사이클 시작 KST 시각 ISO-8601 문자열",
                        "2026-05-13T14:30:00+09:00"),
                new SystemVariable(
                        "cash_amount",
                        "현금 잔고 포맷팅된 문자열",
                        "9,824,500 KRW"),
                new SystemVariable(
                        "held_positions",
                        "보유 종목 요약 XML",
                        "<position code=\"005930\" name=\"삼성전자\" qty=10 avg=75000/>"));

        StocksCollection stocksCollection = new StocksCollection(
                "stocks",
                "selected 종목 컬렉션 — {% for s in stocks %} ... {% endfor %} 로 순회. scope에 따라 watchlist 또는 watchlist+held 교집합.",
                List.of(
                        new StockField("code", "종목 코드 (예: 005930)", null),
                        new StockField("name", "종목명 (예: 삼성전자)", null),
                        new StockField("minute_bars", "분봉 텍스트", "minute_bar"),
                        new StockField("fundamental", "PER/PBR/52주/외인 요약", "fundamental"),
                        new StockField("news", "종목별 최근 useful 뉴스", "news"),
                        new StockField("disclosures", "종목별 최근 공시", "disclosure"),
                        new StockField("orderbook", "호가 1~5단계 (KIS WS 활성 필요)", "orderbook")));

        List<GlobalVariable> globalVariables = List.of(
                new GlobalVariable("macro", "매크로 지수 요약", "macro"));

        List<SourceCatalog> sources = List.of(
                new SourceCatalog(
                        "minute_bar",
                        "종목별 분봉 lookback. stocks.minute_bars 에 주입.",
                        List.of(new SourceParameter(
                                "lookback_minutes",
                                "int",
                                "60",
                                "과거 N분 분봉 lookback"))),
                new SourceCatalog(
                        "fundamental",
                        "PER/PBR/EPS/BPS/52주·250일 고저/외국인/신용/종목 상태 (최신 1행). stocks.fundamental 에 주입.",
                        List.of()),
                new SourceCatalog(
                        "news",
                        "종목별 최근 뉴스, usefulnessStatus=USEFUL 만 노출. stocks.news 에 주입.",
                        List.of(new SourceParameter(
                                "lookback_hours",
                                "int",
                                "12",
                                "과거 N시간 뉴스 lookback"))),
                new SourceCatalog(
                        "disclosure",
                        "종목별 최근 공시. stocks.disclosures 에 주입.",
                        List.of(new SourceParameter(
                                "lookback_hours",
                                "int",
                                "24",
                                "과거 N시간 공시 lookback"))),
                new SourceCatalog(
                        "macro",
                        "최신 매크로 지수 row. 글로벌 macro 변수에 주입.",
                        List.of()),
                new SourceCatalog(
                        "orderbook",
                        "호가 1~5단계. KIS WS disabled 상태면 빈 문자열 (v1 미구현). stocks.orderbook 에 주입.",
                        List.of()));

        return new PromptVocabulary(systemVariables, stocksCollection, globalVariables, sources);
    }
}
