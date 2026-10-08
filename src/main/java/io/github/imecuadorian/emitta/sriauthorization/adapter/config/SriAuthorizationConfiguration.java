package io.github.imecuadorian.emitta.sriauthorization.adapter.config;

import io.github.imecuadorian.emitta.document.application.port.out.DocumentRepository;

import io.github.imecuadorian.emitta.sriauthorization.adapter.out.soap.SriSoapAuthorizationAdapter;
import io.github.imecuadorian.emitta.sriauthorization.application.port.in.QueryDocumentAuthorizationUseCase;
import io.github.imecuadorian.emitta.sriauthorization.application.port.out.SriAuthorizationPort;
import io.github.imecuadorian.emitta.sriauthorization.application.service.QueryDocumentAuthorizationService;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

@Configuration
@ConditionalOnProperty(
        prefix = "emitta.sri.authorization",
        name = "enabled",
        havingValue = "true"
)
@EnableConfigurationProperties(
        SriAuthorizationProperties.class
)
public class SriAuthorizationConfiguration {

    @Bean
    HttpClient sriAuthorizationHttpClient(
            SriAuthorizationProperties properties
    ) {

        return HttpClient.newBuilder()
                .connectTimeout(
                        properties.connectTimeout()
                )
                .build();
    }

    @Bean
    SriAuthorizationPort sriAuthorizationPort(
            @Qualifier("sriAuthorizationHttpClient")
            HttpClient httpClient,
            SriAuthorizationProperties properties
    ) {

        return new SriSoapAuthorizationAdapter(
                httpClient,
                properties.testEndpoint(),
                properties.productionEndpoint(),
                properties.requestTimeout()
        );
    }

    @Bean
    QueryDocumentAuthorizationUseCase queryDocumentAuthorizationUseCase(
            DocumentRepository documentRepository,
            SriAuthorizationPort sriAuthorizationPort
    ) {

        return new QueryDocumentAuthorizationService(
                documentRepository,
                sriAuthorizationPort
        );
    }
}