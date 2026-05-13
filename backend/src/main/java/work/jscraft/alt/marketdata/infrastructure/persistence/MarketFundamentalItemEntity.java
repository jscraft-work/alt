package work.jscraft.alt.marketdata.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import work.jscraft.alt.common.persistence.CreatedAtOnlyUuidEntity;

/**
 * KIS inquire-price 응답의 펀더멘털 부분 (PER/PBR/EPS/BPS, 52주·250일 고저, 외국인/신용/종목 상태 등).
 * 운영 수집은 일 1회. 컬럼명은 alt-fast.market_snapshots / KIS 응답 키와 동일하게 유지한다.
 */
@Entity
@Table(name = "market_fundamental_item")
public class MarketFundamentalItemEntity extends CreatedAtOnlyUuidEntity {

    @Column(name = "symbol_code", nullable = false, length = 40)
    private String symbolCode;

    @Column(name = "symbol_name", length = 200)
    private String symbolName;

    @Column(name = "snapshot_at", nullable = false)
    private OffsetDateTime snapshotAt;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "source_name", nullable = false, length = 40)
    private String sourceName;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "external_id", length = 128)
    private String externalId;

    // ---- 핵심 펀더멘털
    @Column(precision = 20, scale = 6) private BigDecimal per;
    @Column(precision = 20, scale = 6) private BigDecimal pbr;
    @Column(precision = 20, scale = 6) private BigDecimal eps;
    @Column(precision = 20, scale = 6) private BigDecimal bps;

    // ---- 회계 결산월/상장주식수/시가총액
    @Column(name = "stac_month", length = 64) private String stacMonth;
    @Column(name = "lstn_stcn", length = 128) private String lstnStcn;
    @Column(name = "hts_avls") private Long htsAvls;
    @Column(name = "cpfn", precision = 20, scale = 6) private BigDecimal cpfn;
    @Column(name = "stck_fcam") private Long stckFcam;

    // ---- 52주 고/저
    @Column(name = "w52_hgpr") private Integer w52Hgpr;
    @Column(name = "w52_hgpr_date") private LocalDate w52HgprDate;
    @Column(name = "w52_hgpr_vrss_prpr_ctrt", precision = 20, scale = 6) private BigDecimal w52HgprVrssPrprCtrt;
    @Column(name = "w52_lwpr") private Integer w52Lwpr;
    @Column(name = "w52_lwpr_date") private LocalDate w52LwprDate;
    @Column(name = "w52_lwpr_vrss_prpr_ctrt", precision = 20, scale = 6) private BigDecimal w52LwprVrssPrprCtrt;

    // ---- 250일 고/저
    @Column(name = "d250_hgpr") private Integer d250Hgpr;
    @Column(name = "d250_hgpr_date") private LocalDate d250HgprDate;
    @Column(name = "d250_hgpr_vrss_prpr_rate", precision = 20, scale = 6) private BigDecimal d250HgprVrssPrprRate;
    @Column(name = "d250_lwpr") private Integer d250Lwpr;
    @Column(name = "d250_lwpr_date") private LocalDate d250LwprDate;
    @Column(name = "d250_lwpr_vrss_prpr_rate", precision = 20, scale = 6) private BigDecimal d250LwprVrssPrprRate;

    // ---- 당해년도 고/저
    @Column(name = "stck_dryy_hgpr") private Integer stckDryyHgpr;
    @Column(name = "dryy_hgpr_date") private LocalDate dryyHgprDate;
    @Column(name = "dryy_hgpr_vrss_prpr_rate", precision = 20, scale = 6) private BigDecimal dryyHgprVrssPrprRate;
    @Column(name = "stck_dryy_lwpr") private Integer stckDryyLwpr;
    @Column(name = "dryy_lwpr_date") private LocalDate dryyLwprDate;
    @Column(name = "dryy_lwpr_vrss_prpr_rate", precision = 20, scale = 6) private BigDecimal dryyLwprVrssPrprRate;

    // ---- 외국인/기관/프로그램
    @Column(name = "hts_frgn_ehrt", precision = 20, scale = 6) private BigDecimal htsFrgnEhrt;
    @Column(name = "frgn_hldn_qty") private Long frgnHldnQty;
    @Column(name = "frgn_ntby_qty") private Long frgnNtbyQty;
    @Column(name = "pgtr_ntby_qty") private Long pgtrNtbyQty;
    @Column(name = "vol_tnrt", precision = 20, scale = 6) private BigDecimal volTnrt;

    // ---- 신용/대용
    @Column(name = "whol_loan_rmnd_rate", precision = 20, scale = 6) private BigDecimal wholLoanRmndRate;
    @Column(name = "marg_rate", precision = 20, scale = 6) private BigDecimal margRate;
    @Column(name = "crdt_able_yn", length = 4) private String crdtAbleYn;
    @Column(name = "ssts_yn", length = 4) private String sstsYn;

    // ---- 종목 상태 코드
    @Column(name = "iscd_stat_cls_code", length = 8) private String iscdStatClsCode;
    @Column(name = "mrkt_warn_cls_code", length = 8) private String mrktWarnClsCode;
    @Column(name = "invt_caful_yn", length = 4) private String invtCafulYn;
    @Column(name = "short_over_yn", length = 4) private String shortOverYn;
    @Column(name = "sltr_yn", length = 4) private String sltrYn;
    @Column(name = "mang_issu_cls_code", length = 8) private String mangIssuClsCode;
    @Column(name = "temp_stop_yn", length = 4) private String tempStopYn;
    @Column(name = "oprc_rang_cont_yn", length = 4) private String oprcRangContYn;
    @Column(name = "clpr_rang_cont_yn", length = 4) private String clprRangContYn;
    @Column(name = "grmn_rate_cls_code", length = 8) private String grmnRateClsCode;
    @Column(name = "new_hgpr_lwpr_cls_code", length = 8) private String newHgprLwprClsCode;
    @Column(name = "rprs_mrkt_kor_name", length = 60) private String rprsMrktKorName;
    @Column(name = "bstp_kor_isnm", length = 80) private String bstpKorIsnm;
    @Column(name = "vi_cls_code", length = 8) private String viClsCode;
    @Column(name = "ovtm_vi_cls_code", length = 8) private String ovtmViClsCode;
    @Column(name = "last_ssts_cntg_qty") private Long lastSstsCntgQty;
    @Column(name = "apprch_rate", precision = 20, scale = 6) private BigDecimal apprchRate;

    public String getSymbolCode() { return symbolCode; }
    public void setSymbolCode(String v) { this.symbolCode = v; }
    public String getSymbolName() { return symbolName; }
    public void setSymbolName(String v) { this.symbolName = v; }
    public OffsetDateTime getSnapshotAt() { return snapshotAt; }
    public void setSnapshotAt(OffsetDateTime v) { this.snapshotAt = v; }
    public LocalDate getBusinessDate() { return businessDate; }
    public void setBusinessDate(LocalDate v) { this.businessDate = v; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String v) { this.sourceName = v; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(OffsetDateTime v) { this.publishedAt = v; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String v) { this.externalId = v; }

    public BigDecimal getPer() { return per; } public void setPer(BigDecimal v) { this.per = v; }
    public BigDecimal getPbr() { return pbr; } public void setPbr(BigDecimal v) { this.pbr = v; }
    public BigDecimal getEps() { return eps; } public void setEps(BigDecimal v) { this.eps = v; }
    public BigDecimal getBps() { return bps; } public void setBps(BigDecimal v) { this.bps = v; }

    public String getStacMonth() { return stacMonth; } public void setStacMonth(String v) { this.stacMonth = v; }
    public String getLstnStcn() { return lstnStcn; } public void setLstnStcn(String v) { this.lstnStcn = v; }
    public Long getHtsAvls() { return htsAvls; } public void setHtsAvls(Long v) { this.htsAvls = v; }
    public BigDecimal getCpfn() { return cpfn; } public void setCpfn(BigDecimal v) { this.cpfn = v; }
    public Long getStckFcam() { return stckFcam; } public void setStckFcam(Long v) { this.stckFcam = v; }

    public Integer getW52Hgpr() { return w52Hgpr; } public void setW52Hgpr(Integer v) { this.w52Hgpr = v; }
    public LocalDate getW52HgprDate() { return w52HgprDate; } public void setW52HgprDate(LocalDate v) { this.w52HgprDate = v; }
    public BigDecimal getW52HgprVrssPrprCtrt() { return w52HgprVrssPrprCtrt; } public void setW52HgprVrssPrprCtrt(BigDecimal v) { this.w52HgprVrssPrprCtrt = v; }
    public Integer getW52Lwpr() { return w52Lwpr; } public void setW52Lwpr(Integer v) { this.w52Lwpr = v; }
    public LocalDate getW52LwprDate() { return w52LwprDate; } public void setW52LwprDate(LocalDate v) { this.w52LwprDate = v; }
    public BigDecimal getW52LwprVrssPrprCtrt() { return w52LwprVrssPrprCtrt; } public void setW52LwprVrssPrprCtrt(BigDecimal v) { this.w52LwprVrssPrprCtrt = v; }

    public Integer getD250Hgpr() { return d250Hgpr; } public void setD250Hgpr(Integer v) { this.d250Hgpr = v; }
    public LocalDate getD250HgprDate() { return d250HgprDate; } public void setD250HgprDate(LocalDate v) { this.d250HgprDate = v; }
    public BigDecimal getD250HgprVrssPrprRate() { return d250HgprVrssPrprRate; } public void setD250HgprVrssPrprRate(BigDecimal v) { this.d250HgprVrssPrprRate = v; }
    public Integer getD250Lwpr() { return d250Lwpr; } public void setD250Lwpr(Integer v) { this.d250Lwpr = v; }
    public LocalDate getD250LwprDate() { return d250LwprDate; } public void setD250LwprDate(LocalDate v) { this.d250LwprDate = v; }
    public BigDecimal getD250LwprVrssPrprRate() { return d250LwprVrssPrprRate; } public void setD250LwprVrssPrprRate(BigDecimal v) { this.d250LwprVrssPrprRate = v; }

    public Integer getStckDryyHgpr() { return stckDryyHgpr; } public void setStckDryyHgpr(Integer v) { this.stckDryyHgpr = v; }
    public LocalDate getDryyHgprDate() { return dryyHgprDate; } public void setDryyHgprDate(LocalDate v) { this.dryyHgprDate = v; }
    public BigDecimal getDryyHgprVrssPrprRate() { return dryyHgprVrssPrprRate; } public void setDryyHgprVrssPrprRate(BigDecimal v) { this.dryyHgprVrssPrprRate = v; }
    public Integer getStckDryyLwpr() { return stckDryyLwpr; } public void setStckDryyLwpr(Integer v) { this.stckDryyLwpr = v; }
    public LocalDate getDryyLwprDate() { return dryyLwprDate; } public void setDryyLwprDate(LocalDate v) { this.dryyLwprDate = v; }
    public BigDecimal getDryyLwprVrssPrprRate() { return dryyLwprVrssPrprRate; } public void setDryyLwprVrssPrprRate(BigDecimal v) { this.dryyLwprVrssPrprRate = v; }

    public BigDecimal getHtsFrgnEhrt() { return htsFrgnEhrt; } public void setHtsFrgnEhrt(BigDecimal v) { this.htsFrgnEhrt = v; }
    public Long getFrgnHldnQty() { return frgnHldnQty; } public void setFrgnHldnQty(Long v) { this.frgnHldnQty = v; }
    public Long getFrgnNtbyQty() { return frgnNtbyQty; } public void setFrgnNtbyQty(Long v) { this.frgnNtbyQty = v; }
    public Long getPgtrNtbyQty() { return pgtrNtbyQty; } public void setPgtrNtbyQty(Long v) { this.pgtrNtbyQty = v; }
    public BigDecimal getVolTnrt() { return volTnrt; } public void setVolTnrt(BigDecimal v) { this.volTnrt = v; }

    public BigDecimal getWholLoanRmndRate() { return wholLoanRmndRate; } public void setWholLoanRmndRate(BigDecimal v) { this.wholLoanRmndRate = v; }
    public BigDecimal getMargRate() { return margRate; } public void setMargRate(BigDecimal v) { this.margRate = v; }
    public String getCrdtAbleYn() { return crdtAbleYn; } public void setCrdtAbleYn(String v) { this.crdtAbleYn = v; }
    public String getSstsYn() { return sstsYn; } public void setSstsYn(String v) { this.sstsYn = v; }

    public String getIscdStatClsCode() { return iscdStatClsCode; } public void setIscdStatClsCode(String v) { this.iscdStatClsCode = v; }
    public String getMrktWarnClsCode() { return mrktWarnClsCode; } public void setMrktWarnClsCode(String v) { this.mrktWarnClsCode = v; }
    public String getInvtCafulYn() { return invtCafulYn; } public void setInvtCafulYn(String v) { this.invtCafulYn = v; }
    public String getShortOverYn() { return shortOverYn; } public void setShortOverYn(String v) { this.shortOverYn = v; }
    public String getSltrYn() { return sltrYn; } public void setSltrYn(String v) { this.sltrYn = v; }
    public String getMangIssuClsCode() { return mangIssuClsCode; } public void setMangIssuClsCode(String v) { this.mangIssuClsCode = v; }
    public String getTempStopYn() { return tempStopYn; } public void setTempStopYn(String v) { this.tempStopYn = v; }
    public String getOprcRangContYn() { return oprcRangContYn; } public void setOprcRangContYn(String v) { this.oprcRangContYn = v; }
    public String getClprRangContYn() { return clprRangContYn; } public void setClprRangContYn(String v) { this.clprRangContYn = v; }
    public String getGrmnRateClsCode() { return grmnRateClsCode; } public void setGrmnRateClsCode(String v) { this.grmnRateClsCode = v; }
    public String getNewHgprLwprClsCode() { return newHgprLwprClsCode; } public void setNewHgprLwprClsCode(String v) { this.newHgprLwprClsCode = v; }
    public String getRprsMrktKorName() { return rprsMrktKorName; } public void setRprsMrktKorName(String v) { this.rprsMrktKorName = v; }
    public String getBstpKorIsnm() { return bstpKorIsnm; } public void setBstpKorIsnm(String v) { this.bstpKorIsnm = v; }
    public String getViClsCode() { return viClsCode; } public void setViClsCode(String v) { this.viClsCode = v; }
    public String getOvtmViClsCode() { return ovtmViClsCode; } public void setOvtmViClsCode(String v) { this.ovtmViClsCode = v; }
    public Long getLastSstsCntgQty() { return lastSstsCntgQty; } public void setLastSstsCntgQty(Long v) { this.lastSstsCntgQty = v; }
    public BigDecimal getApprchRate() { return apprchRate; } public void setApprchRate(BigDecimal v) { this.apprchRate = v; }
}
