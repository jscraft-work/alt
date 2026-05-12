package work.jscraft.alt.disclosure.infrastructure.persistence;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import work.jscraft.alt.common.persistence.CreatedAtOnlyUuidEntity;

@Entity
@Table(name = "disclosure_item")
public class DisclosureItemEntity extends CreatedAtOnlyUuidEntity {

    @Column(name = "dart_corp_code", nullable = false, length = 20)
    private String dartCorpCode;

    @Column(name = "disclosure_no", nullable = false, length = 120)
    private String disclosureNo;

    @Column(nullable = false, columnDefinition = "text")
    private String title;

    @Column(name = "published_at", nullable = false)
    private OffsetDateTime publishedAt;

    @Column(name = "preview_text", columnDefinition = "text")
    private String previewText;

    @Column(name = "document_url", nullable = false, columnDefinition = "text")
    private String documentUrl;

    public String getDartCorpCode() {
        return dartCorpCode;
    }

    public void setDartCorpCode(String dartCorpCode) {
        this.dartCorpCode = dartCorpCode;
    }

    public String getDisclosureNo() {
        return disclosureNo;
    }

    public void setDisclosureNo(String disclosureNo) {
        this.disclosureNo = disclosureNo;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(OffsetDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getPreviewText() {
        return previewText;
    }

    public void setPreviewText(String previewText) {
        this.previewText = previewText;
    }

    public String getDocumentUrl() {
        return documentUrl;
    }

    public void setDocumentUrl(String documentUrl) {
        this.documentUrl = documentUrl;
    }
}
