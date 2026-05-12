package work.jscraft.alt;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import work.jscraft.alt.news.application.NewsAssessmentEngine;

class FakeNewsAssessmentEngine implements NewsAssessmentEngine {

    private UUID modelProfileId;
    private final Map<String, UsefulnessStatus> byTitle = new HashMap<>();
    private UsefulnessStatus defaultStatus = UsefulnessStatus.UNCLASSIFIED;
    private RuntimeException failureToRaise;

    void resetAll() {
        byTitle.clear();
        defaultStatus = UsefulnessStatus.UNCLASSIFIED;
        failureToRaise = null;
        modelProfileId = null;
    }

    void primeModelProfileId(UUID modelProfileId) {
        this.modelProfileId = modelProfileId;
    }

    void primeVerdict(String title, UsefulnessStatus status) {
        byTitle.put(title, status);
    }

    void primeDefault(UsefulnessStatus status) {
        defaultStatus = status;
    }

    void primeFailure(RuntimeException ex) {
        failureToRaise = ex;
    }

    UUID modelProfileId() {
        return modelProfileId;
    }

    @Override
    public Verdict assess(AssessmentRequest request) {
        if (failureToRaise != null) {
            throw failureToRaise;
        }
        UsefulnessStatus status = byTitle.getOrDefault(request.title(), defaultStatus);
        return new Verdict(status, modelProfileId);
    }
}
