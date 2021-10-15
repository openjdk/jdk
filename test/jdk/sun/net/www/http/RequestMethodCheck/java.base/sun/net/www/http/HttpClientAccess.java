package sun.net.www.http;

public class HttpClientAccess {
    public KeepAliveCache getKeepAliveCache () {
        // kac is a protected static field in HttpClient
        return HttpClient.kac;
    }
}
