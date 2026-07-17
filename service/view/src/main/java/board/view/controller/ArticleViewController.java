package board.view.controller;

import board.view.service.ArticleViewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ArticleViewController {
    private final ArticleViewService articleViewService;

    @PostMapping("/v1/article-views/articles/{articleId}/users/{userId}")
    public Long increase(
            @PathVariable Long articleId,
            @PathVariable Long userId
    ) {
        return articleViewService.increase(articleId, userId);
    }

    /**
     * 여러 게시글의 조회수를 한 번에 반환한다.
     * article-read 목록 조회가 건당 호출 30회를 하지 않도록 하기 위한 엔드포인트다.
     */
    @GetMapping("/v1/article-views/articles/counts")
    public Map<Long, Long> countAll(@RequestParam("articleIds") List<Long> articleIds) {
        return articleViewService.countAll(articleIds);
    }

    @GetMapping("/v1/article-views/articles/{articleId}/count")
    public Long count(@PathVariable Long articleId) {
        return articleViewService.count(articleId);
    }
}
