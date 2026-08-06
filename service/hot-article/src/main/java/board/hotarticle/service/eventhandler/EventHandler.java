package board.hotarticle.service.eventhandler;

import board.common.event.Event;
import board.common.event.EventPayload;

import java.time.Duration;

public interface EventHandler<T extends EventPayload>{
    void handle(Event<T> event);
    default void handle(Event<T> event, Duration ttl) {
        handle(event);
    }
    boolean supports(Event<T> event);
    Long findArticleId(Event<T> event);
}
