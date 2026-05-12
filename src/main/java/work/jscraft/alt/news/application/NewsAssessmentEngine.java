package work.jscraft.alt.news.application;

import java.util.UUID;

public interface NewsAssessmentEngine {

    Verdict assess(AssessmentRequest request);

    record AssessmentRequest(String title, String summary, String bodyText) {
    }

    record Verdict(UsefulnessStatus status, UUID modelProfileId) {
    }

    enum UsefulnessStatus {
        USEFUL("useful"),
        NOT_USEFUL("not_useful"),
        UNCLASSIFIED("unclassified");

        private final String wireValue;

        UsefulnessStatus(String wireValue) {
            this.wireValue = wireValue;
        }

        public String wireValue() {
            return wireValue;
        }
    }
}
