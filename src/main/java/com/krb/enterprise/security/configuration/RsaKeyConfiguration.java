package com.krb.enterprise.security.configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RsaKeyConfiguration {

        private final Path privateKeyPath;
        private final Path publicKeyPath;

        public RsaKeyConfiguration(
                        @Value("${krb.security.jwt.private-key-path:${user.home}/.krb-enterprise/secrets/private-key.pem}") String privateKeyPath,
                        @Value("${krb.security.jwt.public-key-path:${user.home}/.krb-enterprise/secrets/public-key.pem}") String publicKeyPath) {

                this.privateKeyPath = Path.of(privateKeyPath);
                this.publicKeyPath = Path.of(publicKeyPath);
        }

        @Bean
        public RSAPrivateKey rsaPrivateKey() throws Exception {

                String key = Files.readString(privateKeyPath)
                                .replace("-----BEGIN PRIVATE KEY-----", "")
                                .replace("-----END PRIVATE KEY-----", "")
                                .replaceAll("\\s", "");

                byte[] decoded = Base64.getDecoder().decode(key);

                PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);

                return (RSAPrivateKey) KeyFactory
                                .getInstance("RSA")
                                .generatePrivate(spec);
        }

        @Bean
        public RSAPublicKey rsaPublicKey() throws Exception {

                String key = Files.readString(publicKeyPath)
                                .replace("-----BEGIN PUBLIC KEY-----", "")
                                .replace("-----END PUBLIC KEY-----", "")
                                .replaceAll("\\s", "");

                byte[] decoded = Base64.getDecoder().decode(key);

                X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);

                return (RSAPublicKey) KeyFactory
                                .getInstance("RSA")
                                .generatePublic(spec);
        }
}