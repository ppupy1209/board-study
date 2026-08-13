package board.article.service;

public class InvalidSearchRequestException extends RuntimeException {
    public InvalidSearchRequestException(String message) {
        super(message);
    }
}
