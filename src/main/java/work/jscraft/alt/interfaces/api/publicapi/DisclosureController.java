package work.jscraft.alt.interfaces.api.publicapi;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import work.jscraft.alt.common.dto.ApiPagedResponse;
import work.jscraft.alt.disclosure.application.DisclosureQueryService;
import work.jscraft.alt.disclosure.application.DisclosureViews.DisclosureListItem;

@RestController
@RequestMapping("/api/disclosures")
public class DisclosureController {

    private final DisclosureQueryService disclosureQueryService;

    public DisclosureController(DisclosureQueryService disclosureQueryService) {
        this.disclosureQueryService = disclosureQueryService;
    }

    @GetMapping
    public ApiPagedResponse<DisclosureListItem> listDisclosures(
            @RequestParam(required = false) String symbolCode,
            @RequestParam(required = false) String dartCorpCode,
            @RequestParam(required = false) UUID strategyInstanceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return disclosureQueryService.listDisclosures(
                symbolCode, dartCorpCode, strategyInstanceId, dateFrom, dateTo, page, size);
    }
}
