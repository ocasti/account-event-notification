package co.cobre.notifications.infrastructure.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class RestTestSecurityConfig {
    private static final KeyPair KEY_PAIR;
    private static final RSAPublicKey PUBLIC_KEY;
    private static final RSAPrivateKey PRIVATE_KEY;
    private static final String AUDIENCE = "account-event-notification";
    private static Path PEM_FILE;

    static {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KEY_PAIR = generator.generateKeyPair();
            PUBLIC_KEY = (RSAPublicKey) KEY_PAIR.getPublic();
            PRIVATE_KEY = (RSAPrivateKey) KEY_PAIR.getPrivate();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate RSA key pair", e);
        }
    }

    public static String token(String clientId) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(clientId)
                .audience(List.of(AUDIENCE))
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(1200)))
                .jwtID(UUID.randomUUID().toString())
                .build();

            SignedJWT jwt = new SignedJWT(
                new JWSHeader(JWSAlgorithm.RS256),
                claims
            );
            jwt.sign(new RSASSASigner(PRIVATE_KEY));

            return jwt.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException("Failed to sign JWT", e);
        }
    }

    public static Path publicKeyFile() {
        if (PEM_FILE != null) {
            return PEM_FILE;
        }

        try {
            String encoded = Base64.getEncoder().encodeToString(PUBLIC_KEY.getEncoded());
            StringBuilder pem = new StringBuilder();
            pem.append("-----BEGIN PUBLIC KEY-----\n");

            for (int i = 0; i < encoded.length(); i += 64) {
                int end = Math.min(i + 64, encoded.length());
                pem.append(encoded, i, end).append("\n");
            }

            pem.append("-----END PUBLIC KEY-----\n");

            PEM_FILE = Files.createTempFile("test-jwt", ".pem");
            Files.write(PEM_FILE, pem.toString().getBytes(StandardCharsets.UTF_8));
            PEM_FILE.toFile().deleteOnExit();

            return PEM_FILE;
        } catch (IOException e) {
            throw new RuntimeException("Failed to create PEM file", e);
        }
    }
}
