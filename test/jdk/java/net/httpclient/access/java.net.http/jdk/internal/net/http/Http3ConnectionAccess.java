package jdk.internal.net.http;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import jdk.internal.net.http.common.MinimalFuture;
import jdk.internal.net.http.http3.ConnectionSettings;

public class Http3ConnectionAccess {

    private Http3ConnectionAccess() {
        throw new AssertionError();
    }

    static HttpClientImpl impl(HttpClient client) {
        if (client instanceof HttpClientImpl impl) return impl;
        if (client instanceof HttpClientFacade facade) return facade.impl;
        return null;
    }

    static HttpRequestImpl impl(HttpRequest request) {
        if (request instanceof HttpRequestImpl impl) return impl;
        return null;
    }

    public static CompletableFuture<ConnectionSettings> peerSettings(HttpClient client, HttpResponse<?> resp) {
        try {
            Http3Connection conn = impl(client)
                    .client3()
                    .get()
                    .findPooledConnectionFor(impl(resp.request()), null);
            if (conn == null) {
                return MinimalFuture.failedFuture(new NoSuchElementException("no connection found"));
            }
            return conn.peerSettingsCF();
        } catch (Exception ex) {
            return MinimalFuture.failedFuture(ex);
        }
    }
}
