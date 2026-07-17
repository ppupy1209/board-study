package board.articleread.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class FanoutConfig {

    /**
     * 조회 모델 miss를 메울 때 원본 서비스를 병렬 호출하기 위한 executor.
     *
     * <p>{@code CompletableFuture.supplyAsync(...)}를 executor 없이 쓰면 common ForkJoinPool로 간다.
     * 그 풀은 CPU 바운드 작업 기준(코어 수 - 1)으로 크기가 잡혀 있어서, 여기처럼 <b>블로킹 HTTP 호출</b>을
     * 얹으면 스레드가 응답을 기다리는 동안 풀 전체가 막힌다. 같은 JVM의 다른 parallel stream 작업까지 함께 굶는다.
     *
     * <p>가상 스레드는 블로킹되면 캐리어 스레드를 반납하므로 이 용도에 맞는다.
     * 요청마다 스레드를 새로 만들어도 비용이 작아 풀 크기를 미리 정할 필요가 없다.
     * (Java 21 기준. 이 프로젝트는 Temurin 21을 쓴다.)
     *
     * <p>Tomcat 전체를 가상 스레드로 바꾸는 {@code spring.threads.virtual.enabled}는 켜지 않았다.
     * 변경 범위를 팬아웃 경로로 한정하기 위해서다.
     */
    @Bean(destroyMethod = "close")
    public ExecutorService articleReadFanoutExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
