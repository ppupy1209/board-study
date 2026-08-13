package board.article.controller;

import board.article.service.InvalidSearchRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ArticleExceptionHandler {

    @ExceptionHandler(InvalidSearchRequestException.class)
    ProblemDetail handleInvalidSearchRequest(InvalidSearchRequestException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("잘못된 검색 요청");
        return problem;
    }
}
