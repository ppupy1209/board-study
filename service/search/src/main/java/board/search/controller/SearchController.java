package board.search.controller;

import board.search.document.ArticleDocument;
import board.search.service.ArticleSearchService;
import board.search.service.SearchReindexService;
import board.search.service.SearchReindexService.ReindexStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/search")
@RequiredArgsConstructor
public class SearchController {
    private final ArticleSearchService articleSearchService;
    private final SearchReindexService reindexService;

    @GetMapping("/articles")
    public List<ArticleDocument> search(
            @RequestParam("boardId") Long boardId,
            @RequestParam("q") String query,
            @RequestParam(value = "limit", defaultValue = "20") int limit
    ) {
        return articleSearchService.search(boardId, query, limit);
    }

    @PostMapping("/admin/reindex")
    public ReindexStatus reindex(@RequestParam(value = "reset", defaultValue = "false") boolean reset) {
        return reindexService.start(reset);
    }

    @GetMapping("/admin/reindex")
    public ReindexStatus reindexStatus() {
        return reindexService.status();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(IllegalArgumentException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("잘못된 검색 요청");
        return problem;
    }
}
