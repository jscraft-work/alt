package work.jscraft.alt.collector.application.marketdata;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import work.jscraft.alt.collector.application.MarketDataOpsEventRecorder;
import work.jscraft.alt.marketdata.application.MarketDataException;
import work.jscraft.alt.marketdata.application.MarketDataGateway;
import work.jscraft.alt.marketdata.application.MarketDataSnapshots.FundamentalSnapshot;
import work.jscraft.alt.marketdata.infrastructure.persistence.MarketFundamentalItemEntity;
import work.jscraft.alt.marketdata.infrastructure.persistence.MarketFundamentalItemRepository;

@Component
public class KisFundamentalCollector {

    public static final String EVENT_TYPE = "marketdata.fundamental.collect";
    private static final Logger log = LoggerFactory.getLogger(KisFundamentalCollector.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter KIS_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final MarketDataGateway gateway;
    private final MarketFundamentalItemRepository repository;
    private final MarketDataOpsEventRecorder opsEventRecorder;

    public KisFundamentalCollector(
            MarketDataGateway gateway,
            MarketFundamentalItemRepository repository,
            MarketDataOpsEventRecorder opsEventRecorder) {
        this.gateway = gateway;
        this.repository = repository;
        this.opsEventRecorder = opsEventRecorder;
    }

    public void collect(String symbolCode) {
        try {
            FundamentalSnapshot snap = gateway.fetchFundamental(symbolCode);
            LocalDate businessDate = snap.snapshotAt().atZoneSameInstant(KST).toLocalDate();

            Optional<MarketFundamentalItemEntity> existing = repository
                    .findBySymbolCodeAndBusinessDateAndSourceName(symbolCode, businessDate, snap.sourceName());
            MarketFundamentalItemEntity entity = existing.orElseGet(MarketFundamentalItemEntity::new);

            entity.setSymbolCode(snap.symbolCode());
            entity.setSnapshotAt(snap.snapshotAt());
            entity.setBusinessDate(businessDate);
            entity.setSourceName(snap.sourceName());
            applyOutput(entity, snap.rawOutput());

            repository.saveAndFlush(entity);
            opsEventRecorder.recordSuccess(
                    MarketDataOpsEventRecorder.SERVICE_MARKETDATA, EVENT_TYPE, symbolCode);
        } catch (MarketDataException ex) {
            opsEventRecorder.recordFailure(
                    MarketDataOpsEventRecorder.SERVICE_MARKETDATA, EVENT_TYPE,
                    symbolCode, ex.getMessage(), ex.getCategory().name());
            throw ex;
        }
    }

    private void applyOutput(MarketFundamentalItemEntity e, JsonNode o) {
        e.setSymbolName(asText(o, "hts_kor_isnm"));
        e.setPer(asDecimal(o, "per"));
        e.setPbr(asDecimal(o, "pbr"));
        e.setEps(asDecimal(o, "eps"));
        e.setBps(asDecimal(o, "bps"));

        e.setStacMonth(asText(o, "stac_month"));
        e.setLstnStcn(asText(o, "lstn_stcn"));
        e.setHtsAvls(asLong(o, "hts_avls"));
        e.setCpfn(asDecimal(o, "cpfn"));
        e.setStckFcam(asLong(o, "stck_fcam"));

        e.setW52Hgpr(asInt(o, "w52_hgpr"));
        e.setW52HgprDate(asDate(o, "w52_hgpr_date"));
        e.setW52HgprVrssPrprCtrt(asDecimal(o, "w52_hgpr_vrss_prpr_ctrt"));
        e.setW52Lwpr(asInt(o, "w52_lwpr"));
        e.setW52LwprDate(asDate(o, "w52_lwpr_date"));
        e.setW52LwprVrssPrprCtrt(asDecimal(o, "w52_lwpr_vrss_prpr_ctrt"));

        e.setD250Hgpr(asInt(o, "d250_hgpr"));
        e.setD250HgprDate(asDate(o, "d250_hgpr_date"));
        e.setD250HgprVrssPrprRate(asDecimal(o, "d250_hgpr_vrss_prpr_rate"));
        e.setD250Lwpr(asInt(o, "d250_lwpr"));
        e.setD250LwprDate(asDate(o, "d250_lwpr_date"));
        e.setD250LwprVrssPrprRate(asDecimal(o, "d250_lwpr_vrss_prpr_rate"));

        e.setStckDryyHgpr(asInt(o, "stck_dryy_hgpr"));
        e.setDryyHgprDate(asDate(o, "dryy_hgpr_date"));
        e.setDryyHgprVrssPrprRate(asDecimal(o, "dryy_hgpr_vrss_prpr_rate"));
        e.setStckDryyLwpr(asInt(o, "stck_dryy_lwpr"));
        e.setDryyLwprDate(asDate(o, "dryy_lwpr_date"));
        e.setDryyLwprVrssPrprRate(asDecimal(o, "dryy_lwpr_vrss_prpr_rate"));

        e.setHtsFrgnEhrt(asDecimal(o, "hts_frgn_ehrt"));
        e.setFrgnHldnQty(asLong(o, "frgn_hldn_qty"));
        e.setFrgnNtbyQty(asLong(o, "frgn_ntby_qty"));
        e.setPgtrNtbyQty(asLong(o, "pgtr_ntby_qty"));
        e.setVolTnrt(asDecimal(o, "vol_tnrt"));

        e.setWholLoanRmndRate(asDecimal(o, "whol_loan_rmnd_rate"));
        e.setMargRate(asDecimal(o, "marg_rate"));
        e.setCrdtAbleYn(asText(o, "crdt_able_yn"));
        e.setSstsYn(asText(o, "ssts_yn"));

        e.setIscdStatClsCode(asText(o, "iscd_stat_cls_code"));
        e.setMrktWarnClsCode(asText(o, "mrkt_warn_cls_code"));
        e.setInvtCafulYn(asText(o, "invt_caful_yn"));
        e.setShortOverYn(asText(o, "short_over_yn"));
        e.setSltrYn(asText(o, "sltr_yn"));
        e.setMangIssuClsCode(asText(o, "mang_issu_cls_code"));
        e.setTempStopYn(asText(o, "temp_stop_yn"));
        e.setOprcRangContYn(asText(o, "oprc_rang_cont_yn"));
        e.setClprRangContYn(asText(o, "clpr_rang_cont_yn"));
        e.setGrmnRateClsCode(asText(o, "grmn_rate_cls_code"));
        e.setNewHgprLwprClsCode(asText(o, "new_hgpr_lwpr_cls_code"));
        e.setRprsMrktKorName(asText(o, "rprs_mrkt_kor_name"));
        e.setBstpKorIsnm(asText(o, "bstp_kor_isnm"));
        e.setViClsCode(asText(o, "vi_cls_code"));
        e.setOvtmViClsCode(asText(o, "ovtm_vi_cls_code"));
        e.setLastSstsCntgQty(asLong(o, "last_ssts_cntg_qty"));
        e.setApprchRate(asDecimal(o, "apprch_rate"));
    }

    private static String asText(JsonNode n, String key) {
        JsonNode v = n.get(key);
        if (v == null || v.isNull()) return null;
        String s = v.asText("").trim();
        return s.isEmpty() ? null : s;
    }

    private static BigDecimal asDecimal(JsonNode n, String key) {
        String s = asText(n, key);
        if (s == null) return null;
        try { return new BigDecimal(s); } catch (NumberFormatException ex) { return null; }
    }

    private static Integer asInt(JsonNode n, String key) {
        String s = asText(n, key);
        if (s == null) return null;
        try { return Integer.parseInt(s); } catch (NumberFormatException ex) { return null; }
    }

    private static Long asLong(JsonNode n, String key) {
        String s = asText(n, key);
        if (s == null) return null;
        try { return Long.parseLong(s); } catch (NumberFormatException ex) { return null; }
    }

    private static LocalDate asDate(JsonNode n, String key) {
        String s = asText(n, key);
        if (s == null || s.length() != 8) return null;
        try { return LocalDate.parse(s, KIS_DATE); } catch (Exception ex) { return null; }
    }
}
