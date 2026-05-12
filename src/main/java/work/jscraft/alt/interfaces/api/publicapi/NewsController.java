package work.jscraft.alt.interfaces.api.publicapi;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import work.jscraft.alt.common.dto.ApiPagedResponse;
import work.jscraft.alt.news.application.NewsQueryService;
import work.jscraft.alt.news.application.NewsViews.NewsListItem;

@RestController
@RequestMapping("/api/news")
public class NewsController {

    private final NewsQueryService newsQueryService;

    public NewsController(NewsQueryService newsQueryService) {
        this.newsQueryService = newsQueryService;
    }

    @GetMapping
    public ApiPagedResponse<NewsListItem> listNews(
            @RequestParam(required = false) String symbolCode,
            @RequestParam(required = false) UUID strategyInstanceId,
            @RequestParam(required = false) String usefulnessStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return newsQueryService.listNews(symbolCode, strategyInstanceId, usefulnessStatus, dateFrom, dateTo, q, page, size);
    }
}
