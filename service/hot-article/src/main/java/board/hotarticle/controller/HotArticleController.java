package board.hotarticle.controller;

import board.hotarticle.service.HotArticleService;
import board.hotarticle.service.response.HotArticleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class HotArticleController {
    private final HotArticleService hotArticleService;

    @GetMapping("/v1/hot-articles/articles")
    public List<HotArticleResponse> readAll() {
        return hotArticleService.readAll();
    }
}
