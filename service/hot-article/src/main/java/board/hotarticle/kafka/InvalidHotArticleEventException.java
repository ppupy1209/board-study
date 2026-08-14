package board.hotarticle.kafka;

public class InvalidHotArticleEventException extends RuntimeException {
    public InvalidHotArticleEventException(String message) {
        super(message);
    }

    public InvalidHotArticleEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
