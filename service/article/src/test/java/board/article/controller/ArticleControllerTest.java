package board.article.controller;

import board.article.entity.WriterType;
import board.article.service.ArticleService;
import board.article.service.request.ArticleCreateRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleControllerTest {
    @Mock
    private ArticleService articleService;

    @InjectMocks
    private ArticleController articleController;

    @Test
    void guestWriterUsesRequestId() {
        ArticleCreateRequest request = mock(ArticleCreateRequest.class);
        when(request.getWriterId()).thenReturn(1787700908143518L);

        articleController.create(request, null);

        verify(articleService).create(request, 1787700908143518L, WriterType.GUEST, null);
    }

    @Test
    void memberWriterUsesVerifiedJwtClaims() {
        ArticleCreateRequest request = mock(ArticleCreateRequest.class);
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "HS256")
                .subject("42")
                .claim("displayName", "연우")
                .issuedAt(Instant.parse("2026-09-02T00:00:00Z"))
                .expiresAt(Instant.parse("2026-09-02T00:05:00Z"))
                .build();

        articleController.create(request, jwt);

        verify(articleService).create(request, 42L, WriterType.MEMBER, "연우");
    }
}
