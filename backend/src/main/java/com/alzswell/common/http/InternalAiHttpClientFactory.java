package com.alzswell.common.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.time.Duration;
import java.util.Arrays;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InternalAiHttpClientFactory {

    private final boolean requireHttps;
    private final String keyStorePath;
    private final String keyStorePassword;
    private final String keyStoreType;
    private final String trustStorePath;
    private final String trustStorePassword;
    private final String trustStoreType;

    public InternalAiHttpClientFactory(
            @Value("${app.ai-retrieval.tls.require-https:false}") boolean requireHttps,
            @Value("${app.ai-retrieval.tls.key-store:}") String keyStorePath,
            @Value("${app.ai-retrieval.tls.key-store-password:}") String keyStorePassword,
            @Value("${app.ai-retrieval.tls.key-store-type:PKCS12}") String keyStoreType,
            @Value("${app.ai-retrieval.tls.trust-store:}") String trustStorePath,
            @Value("${app.ai-retrieval.tls.trust-store-password:}") String trustStorePassword,
            @Value("${app.ai-retrieval.tls.trust-store-type:PKCS12}") String trustStoreType
    ) {
        this.requireHttps = requireHttps;
        this.keyStorePath = keyStorePath.trim();
        this.keyStorePassword = keyStorePassword;
        this.keyStoreType = requiredStoreType(keyStoreType, "key store");
        this.trustStorePath = trustStorePath.trim();
        this.trustStorePassword = trustStorePassword;
        this.trustStoreType = requiredStoreType(trustStoreType, "trust store");
        validateStorePair(this.keyStorePath, this.keyStorePassword, "key store");
        validateStorePair(this.trustStorePath, this.trustStorePassword, "trust store");
    }

    public Transport create(String baseUrl, long connectTimeoutMs) {
        URI baseUri = validatedBaseUri(baseUrl);
        HttpClient.Builder builder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(positiveDuration(connectTimeoutMs))
                .followRedirects(HttpClient.Redirect.NEVER);
        if ("https".equals(baseUri.getScheme()) && (!keyStorePath.isBlank() || !trustStorePath.isBlank())) {
            builder.sslContext(loadSslContext());
        }
        return new Transport(builder.build(), baseUri);
    }

    public URI endpoint(URI baseUri, String path) {
        if (path == null || !path.startsWith("/") || path.startsWith("//")) {
            throw new IllegalStateException("Invalid internal AI endpoint path");
        }
        return URI.create(baseUri.toString().replaceAll("/+$", "") + path);
    }

    private URI validatedBaseUri(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl);
            boolean supportedScheme = "https".equals(uri.getScheme())
                    || (!requireHttps && "http".equals(uri.getScheme()));
            if (!supportedScheme || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("invalid internal AI base URL");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    requireHttps
                            ? "Internal AI base URL must use HTTPS"
                            : "Invalid internal AI base URL",
                    exception
            );
        }
    }

    private SSLContext loadSslContext() {
        char[] keyPassword = keyStorePassword.toCharArray();
        char[] trustPassword = trustStorePassword.toCharArray();
        try {
            KeyManagerFactory keyManagers = null;
            if (!keyStorePath.isBlank()) {
                KeyStore keyStore = loadStore(keyStorePath, keyStoreType, keyPassword);
                keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                keyManagers.init(keyStore, keyPassword);
            }
            TrustManagerFactory trustManagers = null;
            if (!trustStorePath.isBlank()) {
                KeyStore trustStore = loadStore(trustStorePath, trustStoreType, trustPassword);
                trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trustManagers.init(trustStore);
            }
            SSLContext context = SSLContext.getInstance("TLSv1.3");
            context.init(
                    keyManagers == null ? null : keyManagers.getKeyManagers(),
                    trustManagers == null ? null : trustManagers.getTrustManagers(),
                    null
            );
            return context;
        } catch (GeneralSecurityException | IOException exception) {
            throw new IllegalStateException("Internal AI TLS material could not be loaded", exception);
        } finally {
            Arrays.fill(keyPassword, '\0');
            Arrays.fill(trustPassword, '\0');
        }
    }

    private KeyStore loadStore(String location, String type, char[] password)
            throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance(type);
        try (InputStream input = Files.newInputStream(Path.of(location))) {
            store.load(input, password);
        }
        return store;
    }

    private static Duration positiveDuration(long millis) {
        if (millis < 1 || millis > 30_000) {
            throw new IllegalStateException("Invalid internal AI connect timeout");
        }
        return Duration.ofMillis(millis);
    }

    private static void validateStorePair(String path, String password, String label) {
        if (path.isBlank() != password.isBlank()) {
            throw new IllegalStateException("Internal AI " + label + " path and password must be configured together");
        }
    }

    private static String requiredStoreType(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Internal AI " + label + " type is required");
        }
        return value.trim();
    }

    public record Transport(HttpClient client, URI baseUri) {
    }
}
