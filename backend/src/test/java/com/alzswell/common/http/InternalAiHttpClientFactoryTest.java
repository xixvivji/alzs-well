package com.alzswell.common.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InternalAiHttpClientFactoryTest {

    @TempDir
    Path tempDirectory;

    @Test
    void productionRequiresHttpsButDevelopmentAllowsLocalHttp() {
        InternalAiHttpClientFactory secure = factory(true, "", "", "", "");
        assertThatThrownBy(() -> secure.create("http://127.0.0.1:8000", 500))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTPS");
        assertThat(secure.create("https://ai.internal.example", 500).baseUri().getScheme())
                .isEqualTo("https");

        InternalAiHttpClientFactory development = factory(false, "", "", "", "");
        assertThat(development.create("http://127.0.0.1:8000", 500).baseUri().getScheme())
                .isEqualTo("http");
    }

    @Test
    void explicitlyLoadsConfiguredKeyAndTrustStores() throws Exception {
        Path keyStore = emptyPkcs12("client.p12", "client-password");
        Path trustStore = emptyPkcs12("trust.p12", "trust-password");
        InternalAiHttpClientFactory factory = factory(
                true, keyStore.toString(), "client-password",
                trustStore.toString(), "trust-password");

        assertThat(factory.create("https://ai.internal.example", 500).client().sslContext())
                .isNotNull();
    }

    @Test
    void rejectsPartialTlsMaterialConfiguration() {
        assertThatThrownBy(() -> factory(
                false, "/tmp/client.p12", "", "", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("configured together");
    }

    private InternalAiHttpClientFactory factory(
            boolean requireHttps,
            String keyStore,
            String keyStorePassword,
            String trustStore,
            String trustStorePassword
    ) {
        return new InternalAiHttpClientFactory(
                requireHttps, keyStore, keyStorePassword, "PKCS12",
                trustStore, trustStorePassword, "PKCS12");
    }

    private Path emptyPkcs12(String name, String password) throws Exception {
        Path path = tempDirectory.resolve(name);
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, password.toCharArray());
        try (OutputStream output = Files.newOutputStream(path)) {
            store.store(output, password.toCharArray());
        }
        return path;
    }
}
