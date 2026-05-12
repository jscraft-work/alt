package work.jscraft.alt.news.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import work.jscraft.alt.common.persistence.CreatedAtOnlyUuidEntity;

@Entity
@Table(name = "news_item")
public class NewsItemEntity extends CreatedAtOnlyUuidEntity {

    @Column(name = "provider_name", nullable = false, length = 40)
    private String providerName;

    @Column(name = "external_news_id", length = 200)
    private String externalNewsId;

    @Column(nullable = false, columnDefinition = "text")
    private String title;

    @Column(name = "article_url", nullable = false, columnDefinition = "text")
    private String articleUrl;

    @Column(name = "published_at", nullable = false)
    private OffsetDateTime publishedAt;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(name = "usefulness_status", nullable = false, length = 30)
    private String usefulnessStatus;

    @Column(name = "usefulness_assessed_at")
    private OffsetDateTime usefulnessAssessedAt;

    @Column(name = "usefulness_model_profile_id")
    private UUID usefulnessModelProfileId;

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getExternalNewsId() {
        return externalNewsId;
    }

    public void setExternalNewsId(String externalNewsId) {
        this.externalNewsId = externalNewsId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getArticleUrl() {
        return articleUrl;
    }

    public void setArticleUrl(String articleUrl) {
        this.articleUrl = articleUrl;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(OffsetDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getUsefulnessStatus() {
        return usefulnessStatus;
    }

    public void setUsefulnessStatus(String usefulnessStatus) {
        this.usefulnessStatus = usefulnessStatus;
    }

    public OffsetDateTime getUsefulnessAssessedAt() {
        return usefulnessAssessedAt;
    }

    public void setUsefulnessAssessedAt(OffsetDateTime usefulnessAssessedAt) {
        this.usefulnessAssessedAt = usefulnessAssessedAt;
    }

    public UUID getUsefulnessModelProfileId() {
        return usefulnessModelProfileId;
    }

    public void setUsefulnessModelProfileId(UUID usefulnessModelProfileId) {
        this.usefulnessModelProfileId = usefulnessModelProfileId;
    }
}
