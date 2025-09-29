package jdk.internal.net.http;

import java.net.http.HttpClient;

public class HttpClientAccess {

    public static AltServicesRegistry getRegistry(HttpClient client) {
        return ((HttpClientFacade)client).impl.registry();
    }
}
