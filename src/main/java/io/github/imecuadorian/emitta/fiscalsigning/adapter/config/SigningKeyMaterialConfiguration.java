package io.github.imecuadorian.emitta.fiscalsigning.adapter.config;

import io.github.imecuadorian.emitta.fiscalsigning.adapter.out.crypto.AesGcmSecretCipher;
import io.github.imecuadorian.emitta.fiscalsigning.adapter.out.persistence.PostgreSqlSigningKeyMaterialAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@ConditionalOnExpression("${emitta.signing.enabled:false} or ${emitta.certificates.administration.enabled:false}")
public class SigningKeyMaterialConfiguration {
    @Bean AesGcmSecretCipher certificateSecretCipher(@Value("${EMITTA_CERTIFICATE_MASTER_KEY_B64}") String key) {
        return new AesGcmSecretCipher(key);
    }
    @Bean PostgreSqlSigningKeyMaterialAdapter signingKeyMaterialPort(JdbcClient jdbc,
            PlatformTransactionManager manager,AesGcmSecretCipher cipher) {
        return new PostgreSqlSigningKeyMaterialAdapter(jdbc,new TransactionTemplate(manager),cipher);
    }
}
