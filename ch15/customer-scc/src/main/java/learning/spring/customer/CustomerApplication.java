package learning.spring.customer;

import com.fasterxml.jackson.datatype.jodamoney.JodaMoneyModule;
import learning.spring.customer.integration.BinaryTeaClient;
import learning.spring.customer.support.JwtClientHttpRequestInitializer;
import learning.spring.customer.support.JwtClientHttpRequestInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HTTP;
import org.apache.http.ssl.SSLContextBuilder;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
@Slf4j
@EnableFeignClients
public class CustomerApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder()
                .sources(CustomerApplication.class)
                .web(WebApplicationType.NONE)
                .run(args);
    }

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .requestFactory(this::requestFactory)
                .setConnectTimeout(Duration.ofSeconds(1)) // ????????????
                .setReadTimeout(Duration.ofSeconds(5)) // ????????????
                .additionalRequestCustomizers(jwtClientHttpRequestInitializer())
                .build();
    }

    @Bean
    public JwtClientHttpRequestInitializer jwtClientHttpRequestInitializer() {
        return new JwtClientHttpRequestInitializer();
    }

    @Bean
    public JwtClientHttpRequestInterceptor jwtClientHttpRequestInterceptor() {
        return new JwtClientHttpRequestInterceptor();
    }

    @Bean
    public BinaryTeaClient binaryTeaClient(CircuitBreakerFactory circuitBreakerFactory) {
        return new BinaryTeaClient(circuitBreakerFactory);
    }

    @Bean
    public JodaMoneyModule jodaMoneyModule() {
        return new JodaMoneyModule();
    }

    /**
     * ????????????????????????wait???????????????0???????????????1
     */
    @Bean
    public ExitCodeGenerator waitExitCodeGenerator(ApplicationArguments args) {
        return () -> (args.containsOption("wait") ? 0 : 1);
    }

    @Bean
    public ClientHttpRequestFactory requestFactory() {
        HttpClientBuilder builder = HttpClientBuilder.create()
                .disableAutomaticRetries() // ???????????????????????????????????????
                .evictIdleConnections(10, TimeUnit.MINUTES) // ????????????10????????????
                .setConnectionTimeToLive(30, TimeUnit.SECONDS) // ?????????TTLS??????
                .setMaxConnTotal(200) // ???????????????
                .setMaxConnPerRoute(20); // ??????????????????????????????

        // ??????Keep-Alive??????
        builder.setKeepAliveStrategy((response, context) ->
                Arrays.asList(response.getHeaders(HTTP.CONN_KEEP_ALIVE))
                        .stream()
                        .filter(h -> StringUtils.equalsIgnoreCase(h.getName(), "timeout")
                                && StringUtils.isNumeric(h.getValue()))
                        .findFirst()
                        .map(h -> NumberUtils.toLong(h.getValue(), 300L))
                        .orElse(300L) * 1000);

        SSLContext sslContext = null;
        try {
            sslContext = SSLContextBuilder.create()
                    // ????????????????????????
                    .loadTrustMaterial(null, (certificate, authType) -> true)
                    .build();
        } catch (GeneralSecurityException e) {
            log.error("Can NOT create SSLContext", e);
        }
        if (sslContext != null) {
            builder.setSSLContext(sslContext) // ??????SSLContext
                    .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE); // ??????????????????
        }

        return new HttpComponentsClientHttpRequestFactory(builder.build());
    }
}
