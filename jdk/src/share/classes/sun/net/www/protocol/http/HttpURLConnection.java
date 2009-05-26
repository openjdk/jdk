/*
 * Copyright 1995-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.net.www.protocol.http;

import java.net.URL;
import java.net.URLConnection;
import java.net.ProtocolException;
import java.net.HttpRetryException;
import java.net.PasswordAuthentication;
import java.net.Authenticator;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.SocketTimeoutException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.InetSocketAddress;
import java.net.CookieHandler;
import java.net.ResponseCache;
import java.net.CacheResponse;
import java.net.SecureCacheResponse;
import java.net.CacheRequest;
import java.net.Authenticator.RequestorType;
import java.io.*;
import java.util.Date;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import sun.net.*;
import sun.net.www.*;
import sun.net.www.http.HttpClient;
import sun.net.www.http.PosterOutputStream;
import sun.net.www.http.ChunkedInputStream;
import sun.net.www.http.ChunkedOutputStream;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.net.MalformedURLException;
import java.nio.ByteBuffer;

/**
 * A class to represent an HTTP connection to a remote object.
 */


public class HttpURLConnection extends java.net.HttpURLConnection {

    private static Logger logger = Logger.getLogger("sun.net.www.protocol.http.HttpURLConnection");

    static String HTTP_CONNECT = "CONNECT";

    static final String version;
    public static final String userAgent;

    /* max # of allowed re-directs */
    static final int defaultmaxRedirects = 20;
    static final int maxRedirects;

    /* Not all servers support the (Proxy)-Authentication-Info headers.
     * By default, we don't require them to be sent
     */
    static final boolean validateProxy;
    static final boolean validateServer;

    private StreamingOutputStream strOutputStream;
    private final static String RETRY_MSG1 =
        "cannot retry due to proxy authentication, in streaming mode";
    private final static String RETRY_MSG2 =
        "cannot retry due to server authentication, in streaming mode";
    private final static String RETRY_MSG3 =
        "cannot retry due to redirection, in streaming mode";

    /*
     * System properties related to error stream handling:
     *
     * sun.net.http.errorstream.enableBuffering = <boolean>
     *
     * With the above system property set to true (default is false),
     * when the response code is >=400, the HTTP handler will try to
     * buffer the response body (up to a certain amount and within a
     * time limit). Thus freeing up the underlying socket connection
     * for reuse. The rationale behind this is that usually when the
     * server responds with a >=400 error (client error or server
     * error, such as 404 file not found), the server will send a
     * small response body to explain who to contact and what to do to
     * recover. With this property set to true, even if the
     * application doesn't call getErrorStream(), read the response
     * body, and then call close(), the underlying socket connection
     * can still be kept-alive and reused. The following two system
     * properties provide further control to the error stream
     * buffering behaviour.
     *
     * sun.net.http.errorstream.timeout = <int>
     *     the timeout (in millisec) waiting the error stream
     *     to be buffered; default is 300 ms
     *
     * sun.net.http.errorstream.bufferSize = <int>
     *     the size (in bytes) to use for the buffering the error stream;
     *     default is 4k
     */


    /* Should we enable buffering of error streams? */
    private static boolean enableESBuffer = false;

    /* timeout waiting for read for buffered error stream;
     */
    private static int timeout4ESBuffer = 0;

    /* buffer size for buffered error stream;
    */
    private static int bufSize4ES = 0;

    static {
        maxRedirects = java.security.AccessController.doPrivileged(
            new sun.security.action.GetIntegerAction(
                "http.maxRedirects", defaultmaxRedirects)).intValue();
        version = java.security.AccessController.doPrivileged(
                    new sun.security.action.GetPropertyAction("java.version"));
        String agent = java.security.AccessController.doPrivileged(
                    new sun.security.action.GetPropertyAction("http.agent"));
        if (agent == null) {
            agent = "Java/"+version;
        } else {
            agent = agent + " Java/"+version;
        }
        userAgent = agent;
        validateProxy = java.security.AccessController.doPrivileged(
                new sun.security.action.GetBooleanAction(
                    "http.auth.digest.validateProxy")).booleanValue();
        validateServer = java.security.AccessController.doPrivileged(
                new sun.security.action.GetBooleanAction(
                    "http.auth.digest.validateServer")).booleanValue();

        enableESBuffer = java.security.AccessController.doPrivileged(
                new sun.security.action.GetBooleanAction(
                    "sun.net.http.errorstream.enableBuffering")).booleanValue();
        timeout4ESBuffer = java.security.AccessController.doPrivileged(
                new sun.security.action.GetIntegerAction(
                    "sun.net.http.errorstream.timeout", 300)).intValue();
        if (timeout4ESBuffer <= 0) {
            timeout4ESBuffer = 300; // use the default
        }

        bufSize4ES = java.security.AccessController.doPrivileged(
                new sun.security.action.GetIntegerAction(
                    "sun.net.http.errorstream.bufferSize", 4096)).intValue();
        if (bufSize4ES <= 0) {
            bufSize4ES = 4096; // use the default
        }


    }

    static final String httpVersion = "HTTP/1.1";
    static final String acceptString =
        "text/html, image/gif, image/jpeg, *; q=.2, */*; q=.2";

    // the following http request headers should NOT have their values
    // returned for security reasons.
    private static final String[] EXCLUDE_HEADERS = {
            "Proxy-Authorization",
            "Authorization"
    };
    protected HttpClient http;
    protected Handler handler;
    protected Proxy instProxy;

    private CookieHandler cookieHandler;
    private ResponseCache cacheHandler;

    // the cached response, and cached response headers and body
    protected CacheResponse cachedResponse;
    private MessageHeader cachedHeaders;
    private InputStream cachedInputStream;

    /* output stream to server */
    protected PrintStream ps = null;


    /* buffered error stream */
    private InputStream errorStream = null;

    /* User set Cookies */
    private boolean setUserCookies = true;
    private String userCookies = null;

    /* We only have a single static authenticator for now.
     * REMIND:  backwards compatibility with JDK 1.1.  Should be
     * eliminated for JDK 2.0.
     */
    private static HttpAuthenticator defaultAuth;

    /* all the headers we send
     * NOTE: do *NOT* dump out the content of 'requests' in the
     * output or stacktrace since it may contain security-sensitive
     * headers such as those defined in EXCLUDE_HEADERS.
     */
    private MessageHeader requests;

    /* The following two fields are only used with Digest Authentication */
    String domain;      /* The list of authentication domains */
    DigestAuthentication.Parameters digestparams;

    /* Current credentials in use */
    AuthenticationInfo  currentProxyCredentials = null;
    AuthenticationInfo  currentServerCredentials = null;
    boolean             needToCheck = true;
    private boolean doingNTLM2ndStage = false; /* doing the 2nd stage of an NTLM server authentication */
    private boolean doingNTLMp2ndStage = false; /* doing the 2nd stage of an NTLM proxy authentication */
    /* try auth without calling Authenticator */
    private boolean tryTransparentNTLMServer = NTLMAuthentication.supportsTransparentAuth();
    private boolean tryTransparentNTLMProxy = NTLMAuthentication.supportsTransparentAuth();
    Object authObj;

    /* Set if the user is manually setting the Authorization or Proxy-Authorization headers */
    boolean isUserServerAuth;
    boolean isUserProxyAuth;

    /* Progress source */
    protected ProgressSource pi;

    /* all the response headers we get back */
    private MessageHeader responses;
    /* the stream _from_ the server */
    private InputStream inputStream = null;
    /* post stream _to_ the server, if any */
    private PosterOutputStream poster = null;

    /* Indicates if the std. request headers have been set in requests. */
    private boolean setRequests=false;

    /* Indicates whether a request has already failed or not */
    private boolean failedOnce=false;

    /* Remembered Exception, we will throw it again if somebody
       calls getInputStream after disconnect */
    private Exception rememberedException = null;

    /* If we decide we want to reuse a client, we put it here */
    private HttpClient reuseClient = null;

    /* Tunnel states */
    enum TunnelState {
        /* No tunnel */
        NONE,

        /* Setting up a tunnel */
        SETUP,

        /* Tunnel has been successfully setup */
        TUNNELING
    }

    private TunnelState tunnelState = TunnelState.NONE;

    /* Redefine timeouts from java.net.URLConnection as we nee -1 to mean
     * not set. This is to ensure backward compatibility.
     */
    private int connectTimeout = -1;
    private int readTimeout = -1;

    /*
     * privileged request password authentication
     *
     */
    private static PasswordAuthentication
    privilegedRequestPasswordAuthentication(
                            final String host,
                            final InetAddress addr,
                            final int port,
                            final String protocol,
                            final String prompt,
                            final String scheme,
                            final URL url,
                            final RequestorType authType) {
        return java.security.AccessController.doPrivileged(
            new java.security.PrivilegedAction<PasswordAuthentication>() {
                public PasswordAuthentication run() {
                    return Authenticator.requestPasswordAuthentication(
                        host, addr, port, protocol,
                        prompt, scheme, url, authType);
                }
            });
    }

    /*
     * checks the validity of http message header and throws
     * IllegalArgumentException if invalid.
     */
    private void checkMessageHeader(String key, String value) {
        char LF = '\n';
        int index = key.indexOf(LF);
        if (index != -1) {
            throw new IllegalArgumentException(
                "Illegal character(s) in message header field: " + key);
        }
        else {
            if (value == null) {
                return;
            }

            index = value.indexOf(LF);
            while (index != -1) {
                index++;
                if (index < value.length()) {
                    char c = value.charAt(index);
                    if ((c==' ') || (c=='\t')) {
                        // ok, check the next occurrence
                        index = value.indexOf(LF, index);
                        continue;
                    }
                }
                throw new IllegalArgumentException(
                    "Illegal character(s) in message header value: " + value);
            }
        }
    }

    /* adds the standard key/val pairs to reqests if necessary & write to
     * given PrintStream
     */
    private void writeRequests() throws IOException {
        /* print all message headers in the MessageHeader
         * onto the wire - all the ones we've set and any
         * others that have been set
         */
        // send any pre-emptive authentication
        if (http.usingProxy && tunnelState() != TunnelState.TUNNELING) {
            setPreemptiveProxyAuthentication(requests);
        }
        if (!setRequests) {

            /* We're very particular about the order in which we
             * set the request headers here.  The order should not
             * matter, but some careless CGI programs have been
             * written to expect a very particular order of the
             * standard headers.  To name names, the order in which
             * Navigator3.0 sends them.  In particular, we make *sure*
             * to send Content-type: <> and Content-length:<> second
             * to last and last, respectively, in the case of a POST
             * request.
             */
            if (!failedOnce)
                requests.prepend(method + " " + http.getURLFile()+" "  +
                                 httpVersion, null);
            if (!getUseCaches()) {
                requests.setIfNotSet ("Cache-Control", "no-cache");
                requests.setIfNotSet ("Pragma", "no-cache");
            }
            requests.setIfNotSet("User-Agent", userAgent);
            int port = url.getPort();
            String host = url.getHost();
            if (port != -1 && port != url.getDefaultPort()) {
                host += ":" + String.valueOf(port);
            }
            requests.setIfNotSet("Host", host);
            requests.setIfNotSet("Accept", acceptString);

            /*
             * For HTTP/1.1 the default behavior is to keep connections alive.
             * However, we may be talking to a 1.0 server so we should set
             * keep-alive just in case, except if we have encountered an error
             * or if keep alive is disabled via a system property
             */

            // Try keep-alive only on first attempt
            if (!failedOnce && http.getHttpKeepAliveSet()) {
                if (http.usingProxy) {
                    requests.setIfNotSet("Proxy-Connection", "keep-alive");
                } else {
                    requests.setIfNotSet("Connection", "keep-alive");
                }
            } else {
                /*
                 * RFC 2616 HTTP/1.1 section 14.10 says:
                 * HTTP/1.1 applications that do not support persistent
                 * connections MUST include the "close" connection option
                 * in every message
                 */
                requests.setIfNotSet("Connection", "close");
            }
            // Set modified since if necessary
            long modTime = getIfModifiedSince();
            if (modTime != 0 ) {
                Date date = new Date(modTime);
                //use the preferred date format according to RFC 2068(HTTP1.1),
                // RFC 822 and RFC 1123
                SimpleDateFormat fo =
                  new SimpleDateFormat ("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);
                fo.setTimeZone(TimeZone.getTimeZone("GMT"));
                requests.setIfNotSet("If-Modified-Since", fo.format(date));
            }
            // check for preemptive authorization
            AuthenticationInfo sauth = AuthenticationInfo.getServerAuth(url);
            if (sauth != null && sauth.supportsPreemptiveAuthorization() ) {
                // Sets "Authorization"
                requests.setIfNotSet(sauth.getHeaderName(), sauth.getHeaderValue(url,method));
                currentServerCredentials = sauth;
            }

            if (!method.equals("PUT") && (poster != null || streaming())) {
                requests.setIfNotSet ("Content-type",
                        "application/x-www-form-urlencoded");
            }

            if (streaming()) {
                if (chunkLength != -1) {
                    requests.set ("Transfer-Encoding", "chunked");
                } else { /* fixed content length */
                    if (fixedContentLengthLong != -1) {
                        requests.set ("Content-Length",
                                      String.valueOf(fixedContentLengthLong));
                    } else if (fixedContentLength != -1) {
                        requests.set ("Content-Length",
                                      String.valueOf(fixedContentLength));
                    }
                }
            } else if (poster != null) {
                /* add Content-Length & POST/PUT data */
                synchronized (poster) {
                    /* close it, so no more data can be added */
                    poster.close();
                    requests.set("Content-Length",
                                 String.valueOf(poster.size()));
                }
            }

            // get applicable cookies based on the uri and request headers
            // add them to the existing request headers
            setCookieHeader();

            setRequests=true;
        }
        if(logger.isLoggable(Level.FINEST)) {
            logger.fine(requests.toString());
        }
        http.writeRequests(requests, poster);
        if (ps.checkError()) {
            String proxyHost = http.getProxyHostUsed();
            int proxyPort = http.getProxyPortUsed();
            disconnectInternal();
            if (failedOnce) {
                throw new IOException("Error writing to server");
            } else { // try once more
                failedOnce=true;
                if (proxyHost != null) {
                    setProxiedClient(url, proxyHost, proxyPort);
                } else {
                    setNewClient (url);
                }
                ps = (PrintStream) http.getOutputStream();
                connected=true;
                responses = new MessageHeader();
                setRequests=false;
                writeRequests();
            }
        }
    }


    /**
     * Create a new HttpClient object, bypassing the cache of
     * HTTP client objects/connections.
     *
     * @param url       the URL being accessed
     */
    protected void setNewClient (URL url)
    throws IOException {
        setNewClient(url, false);
    }

    /**
     * Obtain a HttpsClient object. Use the cached copy if specified.
     *
     * @param url       the URL being accessed
     * @param useCache  whether the cached connection should be used
     *        if present
     */
    protected void setNewClient (URL url, boolean useCache)
        throws IOException {
        http = HttpClient.New(url, null, -1, useCache, connectTimeout);
        http.setReadTimeout(readTimeout);
    }


    /**
     * Create a new HttpClient object, set up so that it uses
     * per-instance proxying to the given HTTP proxy.  This
     * bypasses the cache of HTTP client objects/connections.
     *
     * @param url       the URL being accessed
     * @param proxyHost the proxy host to use
     * @param proxyPort the proxy port to use
     */
    protected void setProxiedClient (URL url, String proxyHost, int proxyPort)
    throws IOException {
        setProxiedClient(url, proxyHost, proxyPort, false);
    }

    /**
     * Obtain a HttpClient object, set up so that it uses per-instance
     * proxying to the given HTTP proxy. Use the cached copy of HTTP
     * client objects/connections if specified.
     *
     * @param url       the URL being accessed
     * @param proxyHost the proxy host to use
     * @param proxyPort the proxy port to use
     * @param useCache  whether the cached connection should be used
     *        if present
     */
    protected void setProxiedClient (URL url,
                                           String proxyHost, int proxyPort,
                                           boolean useCache)
        throws IOException {
        proxiedConnect(url, proxyHost, proxyPort, useCache);
    }

    protected void proxiedConnect(URL url,
                                           String proxyHost, int proxyPort,
                                           boolean useCache)
        throws IOException {
        http = HttpClient.New (url, proxyHost, proxyPort, useCache, connectTimeout);
        http.setReadTimeout(readTimeout);
    }

    protected HttpURLConnection(URL u, Handler handler)
    throws IOException {
        // we set proxy == null to distinguish this case with the case
        // when per connection proxy is set
        this(u, null, handler);
    }

    public HttpURLConnection(URL u, String host, int port) {
        this(u, new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(host, port)));
    }

    /** this constructor is used by other protocol handlers such as ftp
        that want to use http to fetch urls on their behalf.*/
    public HttpURLConnection(URL u, Proxy p) {
        this(u, p, new Handler());
    }

    protected HttpURLConnection(URL u, Proxy p, Handler handler) {
        super(u);
        requests = new MessageHeader();
        responses = new MessageHeader();
        this.handler = handler;
        instProxy = p;
        cookieHandler = java.security.AccessController.doPrivileged(
            new java.security.PrivilegedAction<CookieHandler>() {
                public CookieHandler run() {
                return CookieHandler.getDefault();
            }
        });
        cacheHandler = java.security.AccessController.doPrivileged(
            new java.security.PrivilegedAction<ResponseCache>() {
                public ResponseCache run() {
                return ResponseCache.getDefault();
            }
        });
    }

    /**
     * @deprecated.  Use java.net.Authenticator.setDefault() instead.
     */
    public static void setDefaultAuthenticator(HttpAuthenticator a) {
        defaultAuth = a;
    }

    /**
     * opens a stream allowing redirects only to the same host.
     */
    public static InputStream openConnectionCheckRedirects(URLConnection c)
        throws IOException
    {
        boolean redir;
        int redirects = 0;
        InputStream in = null;

        do {
            if (c instanceof HttpURLConnection) {
                ((HttpURLConnection) c).setInstanceFollowRedirects(false);
            }

            // We want to open the input stream before
            // getting headers, because getHeaderField()
            // et al swallow IOExceptions.
            in = c.getInputStream();
            redir = false;

            if (c instanceof HttpURLConnection) {
                HttpURLConnection http = (HttpURLConnection) c;
                int stat = http.getResponseCode();
                if (stat >= 300 && stat <= 307 && stat != 306 &&
                        stat != HttpURLConnection.HTTP_NOT_MODIFIED) {
                    URL base = http.getURL();
                    String loc = http.getHeaderField("Location");
                    URL target = null;
                    if (loc != null) {
                        target = new URL(base, loc);
                    }
                    http.disconnect();
                    if (target == null
                        || !base.getProtocol().equals(target.getProtocol())
                        || base.getPort() != target.getPort()
                        || !hostsEqual(base, target)
                        || redirects >= 5)
                    {
                        throw new SecurityException("illegal URL redirect");
                    }
                    redir = true;
                    c = target.openConnection();
                    redirects++;
                }
            }
        } while (redir);
        return in;
    }


    //
    // Same as java.net.URL.hostsEqual
    //
    private static boolean hostsEqual(URL u1, URL u2) {
        final String h1 = u1.getHost();
        final String h2 = u2.getHost();

        if (h1 == null) {
            return h2 == null;
        } else if (h2 == null) {
            return false;
        } else if (h1.equalsIgnoreCase(h2)) {
            return true;
        }
        // Have to resolve addresses before comparing, otherwise
        // names like tachyon and tachyon.eng would compare different
        final boolean result[] = {false};

        java.security.AccessController.doPrivileged(
            new java.security.PrivilegedAction<Void>() {
                public Void run() {
                try {
                    InetAddress a1 = InetAddress.getByName(h1);
                    InetAddress a2 = InetAddress.getByName(h2);
                    result[0] = a1.equals(a2);
                } catch(UnknownHostException e) {
                } catch(SecurityException e) {
                }
                return null;
            }
        });

        return result[0];
    }

    // overridden in HTTPS subclass

    public void connect() throws IOException {
        plainConnect();
    }

    private boolean checkReuseConnection () {
        if (connected) {
            return true;
        }
        if (reuseClient != null) {
            http = reuseClient;
            http.setReadTimeout(getReadTimeout());
            http.reuse = false;
            reuseClient = null;
            connected = true;
            return true;
        }
        return false;
    }

    protected void plainConnect()  throws IOException {
        if (connected) {
            return;
        }
        // try to see if request can be served from local cache
        if (cacheHandler != null && getUseCaches()) {
            try {
                URI uri = ParseUtil.toURI(url);
                if (uri != null) {
                    cachedResponse = cacheHandler.get(uri, getRequestMethod(), requests.getHeaders(EXCLUDE_HEADERS));
                    if ("https".equalsIgnoreCase(uri.getScheme())
                        && !(cachedResponse instanceof SecureCacheResponse)) {
                        cachedResponse = null;
                    }
                    if (cachedResponse != null) {
                        cachedHeaders = mapToMessageHeader(cachedResponse.getHeaders());
                        cachedInputStream = cachedResponse.getBody();
                    }
                }
            } catch (IOException ioex) {
                // ignore and commence normal connection
            }
            if (cachedHeaders != null && cachedInputStream != null) {
                connected = true;
                return;
            } else {
                cachedResponse = null;
            }
        }
        try {
            /* Try to open connections using the following scheme,
             * return on the first one that's successful:
             * 1) if (instProxy != null)
             *        connect to instProxy; raise exception if failed
             * 2) else use system default ProxySelector
             * 3) is 2) fails, make direct connection
             */

            if (instProxy == null) { // no instance Proxy is set
                /**
                 * Do we have to use a proxy?
                 */
                ProxySelector sel =
                    java.security.AccessController.doPrivileged(
                        new java.security.PrivilegedAction<ProxySelector>() {
                            public ProxySelector run() {
                                     return ProxySelector.getDefault();
                                 }
                             });
                Proxy p = null;
                if (sel != null) {
                    URI uri = sun.net.www.ParseUtil.toURI(url);
                    Iterator<Proxy> it = sel.select(uri).iterator();
                    while (it.hasNext()) {
                        p = it.next();
                        try {
                            if (!failedOnce) {
                                http = getNewHttpClient(url, p, connectTimeout);
                                http.setReadTimeout(readTimeout);
                            } else {
                                // make sure to construct new connection if first
                                // attempt failed
                                http = getNewHttpClient(url, p, connectTimeout, false);
                                http.setReadTimeout(readTimeout);
                            }
                            break;
                        } catch (IOException ioex) {
                            if (p != Proxy.NO_PROXY) {
                                sel.connectFailed(uri, p.address(), ioex);
                                if (!it.hasNext()) {
                                    // fallback to direct connection
                                    http = getNewHttpClient(url, null, connectTimeout, false);
                                    http.setReadTimeout(readTimeout);
                                    break;
                                }
                            } else {
                                throw ioex;
                            }
                            continue;
                        }
                    }
                } else {
                    // No proxy selector, create http client with no proxy
                    if (!failedOnce) {
                        http = getNewHttpClient(url, null, connectTimeout);
                        http.setReadTimeout(readTimeout);
                    } else {
                        // make sure to construct new connection if first
                        // attempt failed
                        http = getNewHttpClient(url, null, connectTimeout, false);
                        http.setReadTimeout(readTimeout);
                    }
                }
            } else {
                if (!failedOnce) {
                    http = getNewHttpClient(url, instProxy, connectTimeout);
                    http.setReadTimeout(readTimeout);
                } else {
                    // make sure to construct new connection if first
                    // attempt failed
                    http = getNewHttpClient(url, instProxy, connectTimeout, false);
                    http.setReadTimeout(readTimeout);
                }
            }

            ps = (PrintStream)http.getOutputStream();
        } catch (IOException e) {
            throw e;
        }
        // constructor to HTTP client calls openserver
        connected = true;
    }

    // subclass HttpsClient will overwrite & return an instance of HttpsClient
    protected HttpClient getNewHttpClient(URL url, Proxy p, int connectTimeout)
        throws IOException {
        return HttpClient.New(url, p, connectTimeout);
    }

    // subclass HttpsClient will overwrite & return an instance of HttpsClient
    protected HttpClient getNewHttpClient(URL url, Proxy p,
                                          int connectTimeout, boolean useCache)
        throws IOException {
        return HttpClient.New(url, p, connectTimeout, useCache);
    }

    private void expect100Continue() throws IOException {
            // Expect: 100-Continue was set, so check the return code for
            // Acceptance
            int oldTimeout = http.getReadTimeout();
            boolean enforceTimeOut = false;
            boolean timedOut = false;
            if (oldTimeout <= 0) {
                // 5s read timeout in case the server doesn't understand
                // Expect: 100-Continue
                http.setReadTimeout(5000);
                enforceTimeOut = true;
            }

            try {
                http.parseHTTP(responses, pi, this);
            } catch (SocketTimeoutException se) {
                if (!enforceTimeOut) {
                    throw se;
                }
                timedOut = true;
                http.setIgnoreContinue(true);
            }
            if (!timedOut) {
                // Can't use getResponseCode() yet
                String resp = responses.getValue(0);
                // Parse the response which is of the form:
                // HTTP/1.1 417 Expectation Failed
                // HTTP/1.1 100 Continue
                if (resp != null && resp.startsWith("HTTP/")) {
                    String[] sa = resp.split("\\s+");
                    responseCode = -1;
                    try {
                        // Response code is 2nd token on the line
                        if (sa.length > 1)
                            responseCode = Integer.parseInt(sa[1]);
                    } catch (NumberFormatException numberFormatException) {
                    }
                }
                if (responseCode != 100) {
                    throw new ProtocolException("Server rejected operation");
                }
            }
            if (oldTimeout > 0) {
                http.setReadTimeout(oldTimeout);
            }
            responseCode = -1;
            responses.reset();
            // Proceed
    }

    /*
     * Allowable input/output sequences:
     * [interpreted as POST/PUT]
     * - get output, [write output,] get input, [read input]
     * - get output, [write output]
     * [interpreted as GET]
     * - get input, [read input]
     * Disallowed:
     * - get input, [read input,] get output, [write output]
     */

    @Override
    public synchronized OutputStream getOutputStream() throws IOException {

        try {
            if (!doOutput) {
                throw new ProtocolException("cannot write to a URLConnection"
                               + " if doOutput=false - call setDoOutput(true)");
            }

            if (method.equals("GET")) {
                method = "POST"; // Backward compatibility
            }
            if (!"POST".equals(method) && !"PUT".equals(method) &&
                "http".equals(url.getProtocol())) {
                throw new ProtocolException("HTTP method " + method +
                                            " doesn't support output");
            }

            // if there's already an input stream open, throw an exception
            if (inputStream != null) {
                throw new ProtocolException("Cannot write output after reading input.");
            }

            if (!checkReuseConnection())
                connect();

            boolean expectContinue = false;
            String expects = requests.findValue("Expect");
            if ("100-Continue".equalsIgnoreCase(expects)) {
                http.setIgnoreContinue(false);
                expectContinue = true;
            }

            if (streaming() && strOutputStream == null) {
                writeRequests();
            }

            if (expectContinue) {
                expect100Continue();
            }
            ps = (PrintStream)http.getOutputStream();
            if (streaming()) {
                if (strOutputStream == null) {
                    if (chunkLength != -1) { /* chunked */
                         strOutputStream = new StreamingOutputStream(
                               new ChunkedOutputStream(ps, chunkLength), -1L);
                    } else { /* must be fixed content length */
                        long length = 0L;
                        if (fixedContentLengthLong != -1) {
                            length = fixedContentLengthLong;
                        } else if (fixedContentLength != -1) {
                            length = fixedContentLength;
                        }
                        strOutputStream = new StreamingOutputStream(ps, length);
                    }
                }
                return strOutputStream;
            } else {
                if (poster == null) {
                    poster = new PosterOutputStream();
                }
                return poster;
            }
        } catch (RuntimeException e) {
            disconnectInternal();
            throw e;
        } catch (ProtocolException e) {
            // Save the response code which may have been set while enforcing
            // the 100-continue. disconnectInternal() forces it to -1
            int i = responseCode;
            disconnectInternal();
            responseCode = i;
            throw e;
        } catch (IOException e) {
            disconnectInternal();
            throw e;
        }
    }

    private boolean streaming () {
        return (fixedContentLength != -1) || (fixedContentLengthLong != -1) ||
               (chunkLength != -1);
    }

    /*
     * get applicable cookies based on the uri and request headers
     * add them to the existing request headers
     */
    private void setCookieHeader() throws IOException {
        if (cookieHandler != null) {
            // we only want to capture the user defined Cookies once, as
            // they cannot be changed by user code after we are connected,
            // only internally.
            if (setUserCookies) {
                int k = requests.getKey("Cookie");
                if ( k != -1)
                    userCookies = requests.getValue(k);
                setUserCookies = false;
            }

            // remove old Cookie header before setting new one.
            requests.remove("Cookie");

            URI uri = ParseUtil.toURI(url);
            if (uri != null) {
                Map<String, List<String>> cookies
                    = cookieHandler.get(
                        uri, requests.getHeaders(EXCLUDE_HEADERS));
                if (!cookies.isEmpty()) {
                    for (Map.Entry<String, List<String>> entry :
                             cookies.entrySet()) {
                        String key = entry.getKey();
                        // ignore all entries that don't have "Cookie"
                        // or "Cookie2" as keys
                        if (!"Cookie".equalsIgnoreCase(key) &&
                            !"Cookie2".equalsIgnoreCase(key)) {
                            continue;
                        }
                        List<String> l = entry.getValue();
                        if (l != null && !l.isEmpty()) {
                            StringBuilder cookieValue = new StringBuilder();
                            for (String value : l) {
                                cookieValue.append(value).append("; ");
                            }
                            // strip off the trailing '; '
                            try {
                                requests.add(key, cookieValue.substring(0, cookieValue.length() - 2));
                            } catch (StringIndexOutOfBoundsException ignored) {
                                // no-op
                            }
                        }
                    }
                }
            }
            if (userCookies != null) {
                int k;
                if ((k = requests.getKey("Cookie")) != -1)
                    requests.set("Cookie", requests.getValue(k) + ";" + userCookies);
                else
                    requests.set("Cookie", userCookies);
            }

        } // end of getting cookies
    }

    @Override
    @SuppressWarnings("empty-statement")
    public synchronized InputStream getInputStream() throws IOException {

        if (!doInput) {
            throw new ProtocolException("Cannot read from URLConnection"
                   + " if doInput=false (call setDoInput(true))");
        }

        if (rememberedException != null) {
            if (rememberedException instanceof RuntimeException)
                throw new RuntimeException(rememberedException);
            else {
                throw getChainedException((IOException)rememberedException);
            }
        }

        if (inputStream != null) {
            return inputStream;
        }

        if (streaming() ) {
            if (strOutputStream == null) {
                getOutputStream();
            }
            /* make sure stream is closed */
            strOutputStream.close ();
            if (!strOutputStream.writtenOK()) {
                throw new IOException ("Incomplete output stream");
            }
        }

        int redirects = 0;
        int respCode = 0;
        long cl = -1;
        AuthenticationInfo serverAuthentication = null;
        AuthenticationInfo proxyAuthentication = null;
        AuthenticationHeader srvHdr = null;

        /**
         * Failed Negotiate
         *
         * In some cases, the Negotiate auth is supported for the
         * remote host but the negotiate process still fails (For
         * example, if the web page is located on a backend server
         * and delegation is needed but fails). The authentication
         * process will start again, and we need to detect this
         * kind of failure and do proper fallback (say, to NTLM).
         *
         * In order to achieve this, the inNegotiate flag is set
         * when the first negotiate challenge is met (and reset
         * if authentication is finished). If a fresh new negotiate
         * challenge (no parameter) is found while inNegotiate is
         * set, we know there's a failed auth attempt recently.
         * Here we'll ignore the header line so that fallback
         * can be practiced.
         *
         * inNegotiateProxy is for proxy authentication.
         */
        boolean inNegotiate = false;
        boolean inNegotiateProxy = false;

        // If the user has set either of these headers then do not remove them
        isUserServerAuth = requests.getKey("Authorization") != -1;
        isUserProxyAuth = requests.getKey("Proxy-Authorization") != -1;

        try {
            do {
                if (!checkReuseConnection())
                    connect();

                if (cachedInputStream != null) {
                    return cachedInputStream;
                }

                // Check if URL should be metered
                boolean meteredInput = ProgressMonitor.getDefault().shouldMeterInput(url, method);

                if (meteredInput)   {
                    pi = new ProgressSource(url, method);
                    pi.beginTracking();
                }

                /* REMIND: This exists to fix the HttpsURLConnection subclass.
                 * Hotjava needs to run on JDK1.1FCS.  Do proper fix once a
                 * proper solution for SSL can be found.
                 */
                ps = (PrintStream)http.getOutputStream();

                if (!streaming()) {
                    writeRequests();
                }
                http.parseHTTP(responses, pi, this);
                if(logger.isLoggable(Level.FINEST)) {
                    logger.fine(responses.toString());
                }
                inputStream = http.getInputStream();

                respCode = getResponseCode();
                if (respCode == HTTP_PROXY_AUTH) {
                    if (streaming()) {
                        disconnectInternal();
                        throw new HttpRetryException (
                            RETRY_MSG1, HTTP_PROXY_AUTH);
                    }

                    // Read comments labeled "Failed Negotiate" for details.
                    boolean dontUseNegotiate = false;
                    Iterator iter = responses.multiValueIterator("Proxy-Authenticate");
                    while (iter.hasNext()) {
                        String value = ((String)iter.next()).trim();
                        if (value.equalsIgnoreCase("Negotiate") ||
                                value.equalsIgnoreCase("Kerberos")) {
                            if (!inNegotiateProxy) {
                                inNegotiateProxy = true;
                            } else {
                                dontUseNegotiate = true;
                                doingNTLMp2ndStage = false;
                                proxyAuthentication = null;
                            }
                            break;
                        }
                    }

                    // changes: add a 3rd parameter to the constructor of
                    // AuthenticationHeader, so that NegotiateAuthentication.
                    // isSupported can be tested.
                    // The other 2 appearances of "new AuthenticationHeader" is
                    // altered in similar ways.

                    AuthenticationHeader authhdr = new AuthenticationHeader (
                            "Proxy-Authenticate", responses,
                            http.getProxyHostUsed(), dontUseNegotiate
                    );

                    if (!doingNTLMp2ndStage) {
                        proxyAuthentication =
                            resetProxyAuthentication(proxyAuthentication, authhdr);
                        if (proxyAuthentication != null) {
                            redirects++;
                            disconnectInternal();
                            continue;
                        }
                    } else {
                        /* in this case, only one header field will be present */
                        String raw = responses.findValue ("Proxy-Authenticate");
                        reset ();
                        if (!proxyAuthentication.setHeaders(this,
                                                        authhdr.headerParser(), raw)) {
                            disconnectInternal();
                            throw new IOException ("Authentication failure");
                        }
                        if (serverAuthentication != null && srvHdr != null &&
                                !serverAuthentication.setHeaders(this,
                                                        srvHdr.headerParser(), raw)) {
                            disconnectInternal ();
                            throw new IOException ("Authentication failure");
                        }
                        authObj = null;
                        doingNTLMp2ndStage = false;
                        continue;
                    }
                }

                // cache proxy authentication info
                if (proxyAuthentication != null) {
                    // cache auth info on success, domain header not relevant.
                    proxyAuthentication.addToCache();
                }

                if (respCode == HTTP_UNAUTHORIZED) {
                    if (streaming()) {
                        disconnectInternal();
                        throw new HttpRetryException (
                            RETRY_MSG2, HTTP_UNAUTHORIZED);
                    }

                    // Read comments labeled "Failed Negotiate" for details.
                    boolean dontUseNegotiate = false;
                    Iterator iter = responses.multiValueIterator("WWW-Authenticate");
                    while (iter.hasNext()) {
                        String value = ((String)iter.next()).trim();
                        if (value.equalsIgnoreCase("Negotiate") ||
                                value.equalsIgnoreCase("Kerberos")) {
                            if (!inNegotiate) {
                                inNegotiate = true;
                            } else {
                                dontUseNegotiate = true;
                                doingNTLM2ndStage = false;
                                serverAuthentication = null;
                            }
                            break;
                        }
                    }

                    srvHdr = new AuthenticationHeader (
                            "WWW-Authenticate", responses,
                            url.getHost().toLowerCase(), dontUseNegotiate
                    );

                    String raw = srvHdr.raw();
                    if (!doingNTLM2ndStage) {
                        if ((serverAuthentication != null)&&
                            !(serverAuthentication instanceof NTLMAuthentication)) {
                            if (serverAuthentication.isAuthorizationStale (raw)) {
                                /* we can retry with the current credentials */
                                disconnectInternal();
                                redirects++;
                                requests.set(serverAuthentication.getHeaderName(),
                                            serverAuthentication.getHeaderValue(url, method));
                                currentServerCredentials = serverAuthentication;
                                setCookieHeader();
                                continue;
                            } else {
                                serverAuthentication.removeFromCache();
                            }
                        }
                        serverAuthentication = getServerAuthentication(srvHdr);
                        currentServerCredentials = serverAuthentication;

                        if (serverAuthentication != null) {
                            disconnectInternal();
                            redirects++; // don't let things loop ad nauseum
                            setCookieHeader();
                            continue;
                        }
                    } else {
                        reset ();
                        /* header not used for ntlm */
                        if (!serverAuthentication.setHeaders(this, null, raw)) {
                            disconnectInternal();
                            throw new IOException ("Authentication failure");
                        }
                        doingNTLM2ndStage = false;
                        authObj = null;
                        setCookieHeader();
                        continue;
                    }
                }
                // cache server authentication info
                if (serverAuthentication != null) {
                    // cache auth info on success
                    if (!(serverAuthentication instanceof DigestAuthentication) ||
                        (domain == null)) {
                        if (serverAuthentication instanceof BasicAuthentication) {
                            // check if the path is shorter than the existing entry
                            String npath = AuthenticationInfo.reducePath (url.getPath());
                            String opath = serverAuthentication.path;
                            if (!opath.startsWith (npath) || npath.length() >= opath.length()) {
                                /* npath is longer, there must be a common root */
                                npath = BasicAuthentication.getRootPath (opath, npath);
                            }
                            // remove the entry and create a new one
                            BasicAuthentication a =
                                (BasicAuthentication) serverAuthentication.clone();
                            serverAuthentication.removeFromCache();
                            a.path = npath;
                            serverAuthentication = a;
                        }
                        serverAuthentication.addToCache();
                    } else {
                        // what we cache is based on the domain list in the request
                        DigestAuthentication srv = (DigestAuthentication)
                            serverAuthentication;
                        StringTokenizer tok = new StringTokenizer (domain," ");
                        String realm = srv.realm;
                        PasswordAuthentication pw = srv.pw;
                        digestparams = srv.params;
                        while (tok.hasMoreTokens()) {
                            String path = tok.nextToken();
                            try {
                                /* path could be an abs_path or a complete URI */
                                URL u = new URL (url, path);
                                DigestAuthentication d = new DigestAuthentication (
                                                   false, u, realm, "Digest", pw, digestparams);
                                d.addToCache ();
                            } catch (Exception e) {}
                        }
                    }
                }

                // some flags should be reset to its initialized form so that
                // even after a redirect the necessary checks can still be
                // preformed.
                inNegotiate = false;
                inNegotiateProxy = false;

                //serverAuthentication = null;
                doingNTLMp2ndStage = false;
                doingNTLM2ndStage = false;
                if (!isUserServerAuth)
                    requests.remove("Authorization");
                if (!isUserProxyAuth)
                    requests.remove("Proxy-Authorization");

                if (respCode == HTTP_OK) {
                    checkResponseCredentials (false);
                } else {
                    needToCheck = false;
                }

                // a flag need to clean
                needToCheck = true;

                if (followRedirect()) {
                    /* if we should follow a redirect, then the followRedirects()
                     * method will disconnect() and re-connect us to the new
                     * location
                     */
                    redirects++;

                    // redirecting HTTP response may have set cookie, so
                    // need to re-generate request header
                    setCookieHeader();

                    continue;
                }

                try {
                    cl = Long.parseLong(responses.findValue("content-length"));
                } catch (Exception exc) { };

                if (method.equals("HEAD") || cl == 0 ||
                    respCode == HTTP_NOT_MODIFIED ||
                    respCode == HTTP_NO_CONTENT) {

                    if (pi != null) {
                        pi.finishTracking();
                        pi = null;
                    }
                    http.finished();
                    http = null;
                    inputStream = new EmptyInputStream();
                    connected = false;
                }

                if (respCode == 200 || respCode == 203 || respCode == 206 ||
                    respCode == 300 || respCode == 301 || respCode == 410) {
                    if (cacheHandler != null) {
                        // give cache a chance to save response in cache
                        URI uri = ParseUtil.toURI(url);
                        if (uri != null) {
                            URLConnection uconn = this;
                            if ("https".equalsIgnoreCase(uri.getScheme())) {
                                try {
                                // use reflection to get to the public
                                // HttpsURLConnection instance saved in
                                // DelegateHttpsURLConnection
                                uconn = (URLConnection)this.getClass().getField("httpsURLConnection").get(this);
                                } catch (IllegalAccessException iae) {
                                    // ignored; use 'this'
                                } catch (NoSuchFieldException nsfe) {
                                    // ignored; use 'this'
                                }
                            }
                            CacheRequest cacheRequest =
                                cacheHandler.put(uri, uconn);
                            if (cacheRequest != null && http != null) {
                                http.setCacheRequest(cacheRequest);
                                inputStream = new HttpInputStream(inputStream, cacheRequest);
                            }
                        }
                    }
                }

                if (!(inputStream instanceof HttpInputStream)) {
                    inputStream = new HttpInputStream(inputStream);
                }

                if (respCode >= 400) {
                    if (respCode == 404 || respCode == 410) {
                        throw new FileNotFoundException(url.toString());
                    } else {
                        throw new java.io.IOException("Server returned HTTP" +
                              " response code: " + respCode + " for URL: " +
                              url.toString());
                    }
                }
                poster = null;
                strOutputStream = null;
                return inputStream;
            } while (redirects < maxRedirects);

            throw new ProtocolException("Server redirected too many " +
                                        " times ("+ redirects + ")");
        } catch (RuntimeException e) {
            disconnectInternal();
            rememberedException = e;
            throw e;
        } catch (IOException e) {
            rememberedException = e;

            // buffer the error stream if bytes < 4k
            // and it can be buffered within 1 second
            String te = responses.findValue("Transfer-Encoding");
            if (http != null && http.isKeepingAlive() && enableESBuffer &&
                (cl > 0 || (te != null && te.equalsIgnoreCase("chunked")))) {
                errorStream = ErrorStream.getErrorStream(inputStream, cl, http);
            }
            throw e;
        } finally {
            if (respCode == HTTP_PROXY_AUTH && proxyAuthentication != null) {
                proxyAuthentication.endAuthRequest();
            }
            else if (respCode == HTTP_UNAUTHORIZED && serverAuthentication != null) {
                serverAuthentication.endAuthRequest();
            }
        }
    }

    /*
     * Creates a chained exception that has the same type as
     * original exception and with the same message. Right now,
     * there is no convenient APIs for doing so.
     */
    private IOException getChainedException(final IOException rememberedException) {
        try {
            final Object[] args = { rememberedException.getMessage() };
            IOException chainedException =
                java.security.AccessController.doPrivileged(
                    new java.security.PrivilegedExceptionAction<IOException>() {
                        public IOException run() throws Exception {
                            return (IOException)
                                rememberedException.getClass()
                                .getConstructor(new Class[] { String.class })
                                .newInstance(args);
                        }
                    });
            chainedException.initCause(rememberedException);
            return chainedException;
        } catch (Exception ignored) {
            return rememberedException;
        }
    }

    @Override
    public InputStream getErrorStream() {
        if (connected && responseCode >= 400) {
            // Client Error 4xx and Server Error 5xx
            if (errorStream != null) {
                return errorStream;
            } else if (inputStream != null) {
                return inputStream;
            }
        }
        return null;
    }

    /**
     * set or reset proxy authentication info in request headers
     * after receiving a 407 error. In the case of NTLM however,
     * receiving a 407 is normal and we just skip the stale check
     * because ntlm does not support this feature.
     */
    private AuthenticationInfo
        resetProxyAuthentication(AuthenticationInfo proxyAuthentication, AuthenticationHeader auth) {
        if ((proxyAuthentication != null )&& ! (proxyAuthentication instanceof
                                                        NTLMAuthentication)) {
            String raw = auth.raw();
            if (proxyAuthentication.isAuthorizationStale (raw)) {
                /* we can retry with the current credentials */
                String value;
                if (tunnelState() == TunnelState.SETUP &&
                      proxyAuthentication instanceof DigestAuthentication) {
                    value = ((DigestAuthentication)proxyAuthentication)
                            .getHeaderValue(connectRequestURI(url), HTTP_CONNECT);
                } else {
                    value = proxyAuthentication.getHeaderValue(url, method);
                }
                requests.set(proxyAuthentication.getHeaderName(), value);
                currentProxyCredentials = proxyAuthentication;
                return proxyAuthentication;
            } else {
                proxyAuthentication.removeFromCache();
            }
        }
        proxyAuthentication = getHttpProxyAuthentication(auth);
        currentProxyCredentials = proxyAuthentication;
        return  proxyAuthentication;
    }

    /**
     * Returns the tunnel state.
     *
     * @return  the state
     */
    TunnelState tunnelState() {
        return tunnelState;
    }

    /**
     * Set the tunneling status.
     *
     * @param  the state
     */
    void setTunnelState(TunnelState tunnelState) {
        this.tunnelState = tunnelState;
    }

    /**
     * establish a tunnel through proxy server
     */
    public synchronized void doTunneling() throws IOException {
        int retryTunnel = 0;
        String statusLine = "";
        int respCode = 0;
        AuthenticationInfo proxyAuthentication = null;
        String proxyHost = null;
        int proxyPort = -1;

        // save current requests so that they can be restored after tunnel is setup.
        MessageHeader savedRequests = requests;
        requests = new MessageHeader();

        // Read comments labeled "Failed Negotiate" for details.
        boolean inNegotiateProxy = false;

        try {
            /* Actively setting up a tunnel */
            setTunnelState(TunnelState.SETUP);

            do {
                if (!checkReuseConnection()) {
                    proxiedConnect(url, proxyHost, proxyPort, false);
                }
                // send the "CONNECT" request to establish a tunnel
                // through proxy server
                sendCONNECTRequest();
                responses.reset();

                // There is no need to track progress in HTTP Tunneling,
                // so ProgressSource is null.
                http.parseHTTP(responses, null, this);

                /* Log the response to the CONNECT */
                logger.fine(responses.toString());

                statusLine = responses.getValue(0);
                StringTokenizer st = new StringTokenizer(statusLine);
                st.nextToken();
                respCode = Integer.parseInt(st.nextToken().trim());
                if (respCode == HTTP_PROXY_AUTH) {
                    // Read comments labeled "Failed Negotiate" for details.
                    boolean dontUseNegotiate = false;
                    Iterator iter = responses.multiValueIterator("Proxy-Authenticate");
                    while (iter.hasNext()) {
                        String value = ((String)iter.next()).trim();
                        if (value.equalsIgnoreCase("Negotiate") ||
                                value.equalsIgnoreCase("Kerberos")) {
                            if (!inNegotiateProxy) {
                                inNegotiateProxy = true;
                            } else {
                                dontUseNegotiate = true;
                                doingNTLMp2ndStage = false;
                                proxyAuthentication = null;
                            }
                            break;
                        }
                    }

                    AuthenticationHeader authhdr = new AuthenticationHeader (
                            "Proxy-Authenticate", responses,
                            http.getProxyHostUsed(), dontUseNegotiate
                    );
                    if (!doingNTLMp2ndStage) {
                        proxyAuthentication =
                            resetProxyAuthentication(proxyAuthentication, authhdr);
                        if (proxyAuthentication != null) {
                            proxyHost = http.getProxyHostUsed();
                            proxyPort = http.getProxyPortUsed();
                            disconnectInternal();
                            retryTunnel++;
                            continue;
                        }
                    } else {
                        String raw = responses.findValue ("Proxy-Authenticate");
                        reset ();
                        if (!proxyAuthentication.setHeaders(this,
                                                authhdr.headerParser(), raw)) {
                            proxyHost = http.getProxyHostUsed();
                            proxyPort = http.getProxyPortUsed();
                            disconnectInternal();
                            throw new IOException ("Authentication failure");
                        }
                        authObj = null;
                        doingNTLMp2ndStage = false;
                        continue;
                    }
                }
                // cache proxy authentication info
                if (proxyAuthentication != null) {
                    // cache auth info on success, domain header not relevant.
                    proxyAuthentication.addToCache();
                }

                if (respCode == HTTP_OK) {
                    setTunnelState(TunnelState.TUNNELING);
                    break;
                }
                // we don't know how to deal with other response code
                // so disconnect and report error
                disconnectInternal();
                setTunnelState(TunnelState.NONE);
                break;
            } while (retryTunnel < maxRedirects);

            if (retryTunnel >= maxRedirects || (respCode != HTTP_OK)) {
                throw new IOException("Unable to tunnel through proxy."+
                                      " Proxy returns \"" +
                                      statusLine + "\"");
            }
        } finally  {
            if (respCode == HTTP_PROXY_AUTH && proxyAuthentication != null) {
                proxyAuthentication.endAuthRequest();
            }
        }

        // restore original request headers
        requests = savedRequests;

        // reset responses
        responses.reset();
    }

    static String connectRequestURI(URL url) {
        String host = url.getHost();
        int port = url.getPort();
        port = port != -1 ? port : url.getDefaultPort();

        return host + ":" + port;
    }

    /**
     * send a CONNECT request for establishing a tunnel to proxy server
     */
    private void sendCONNECTRequest() throws IOException {
        int port = url.getPort();

        // setRequests == true indicates the std. request headers
        // have been set in (previous) requests.
        // so the first one must be the http method (GET, etc.).
        // we need to set it to CONNECT soon, remove this one first.
        // otherwise, there may have 2 http methods in headers
        if (setRequests) requests.set(0, null, null);

        requests.prepend(HTTP_CONNECT + " " + connectRequestURI(url)
                         + " " + httpVersion, null);
        requests.setIfNotSet("User-Agent", userAgent);

        String host = url.getHost();
        if (port != -1 && port != url.getDefaultPort()) {
            host += ":" + String.valueOf(port);
        }
        requests.setIfNotSet("Host", host);

        // Not really necessary for a tunnel, but can't hurt
        requests.setIfNotSet("Accept", acceptString);

        setPreemptiveProxyAuthentication(requests);

         /* Log the CONNECT request */
        logger.fine(requests.toString());

        http.writeRequests(requests, null);
        // remove CONNECT header
        requests.set(0, null, null);
    }

    /**
     * Sets pre-emptive proxy authentication in header
     */
    private void setPreemptiveProxyAuthentication(MessageHeader requests) {
        AuthenticationInfo pauth
            = AuthenticationInfo.getProxyAuth(http.getProxyHostUsed(),
                                              http.getProxyPortUsed());
        if (pauth != null && pauth.supportsPreemptiveAuthorization()) {
            String value;
            if (tunnelState() == TunnelState.SETUP &&
                    pauth instanceof DigestAuthentication) {
                value = ((DigestAuthentication)pauth)
                        .getHeaderValue(connectRequestURI(url), HTTP_CONNECT);
            } else {
                value = pauth.getHeaderValue(url, method);
            }

            // Sets "Proxy-authorization"
            requests.set(pauth.getHeaderName(), value);
            currentProxyCredentials = pauth;
        }
    }

    /**
     * Gets the authentication for an HTTP proxy, and applies it to
     * the connection.
     */
    private AuthenticationInfo getHttpProxyAuthentication (AuthenticationHeader authhdr) {
        /* get authorization from authenticator */
        AuthenticationInfo ret = null;
        String raw = authhdr.raw();
        String host = http.getProxyHostUsed();
        int port = http.getProxyPortUsed();
        if (host != null && authhdr.isPresent()) {
            HeaderParser p = authhdr.headerParser();
            String realm = p.findValue("realm");
            String scheme = authhdr.scheme();
            char schemeID;
            if ("basic".equalsIgnoreCase(scheme)) {
                schemeID = BasicAuthentication.BASIC_AUTH;
            } else if ("digest".equalsIgnoreCase(scheme)) {
                schemeID = DigestAuthentication.DIGEST_AUTH;
            } else if ("ntlm".equalsIgnoreCase(scheme)) {
                schemeID = NTLMAuthentication.NTLM_AUTH;
                doingNTLMp2ndStage = true;
            } else if ("Kerberos".equalsIgnoreCase(scheme)) {
                schemeID = NegotiateAuthentication.KERBEROS_AUTH;
                doingNTLMp2ndStage = true;
            } else if ("Negotiate".equalsIgnoreCase(scheme)) {
                schemeID = NegotiateAuthentication.NEGOTIATE_AUTH;
                doingNTLMp2ndStage = true;
            } else {
                schemeID = 0;
            }
            if (realm == null)
                realm = "";
            ret = AuthenticationInfo.getProxyAuth(host, port, realm, schemeID);
            if (ret == null) {
                if (schemeID == BasicAuthentication.BASIC_AUTH) {
                    InetAddress addr = null;
                    try {
                        final String finalHost = host;
                        addr = java.security.AccessController.doPrivileged(
                            new java.security.PrivilegedExceptionAction<InetAddress>() {
                                public InetAddress run()
                                    throws java.net.UnknownHostException {
                                    return InetAddress.getByName(finalHost);
                                }
                            });
                    } catch (java.security.PrivilegedActionException ignored) {
                        // User will have an unknown host.
                    }
                    PasswordAuthentication a =
                        privilegedRequestPasswordAuthentication(
                                    host, addr, port, "http",
                                    realm, scheme, url, RequestorType.PROXY);
                    if (a != null) {
                        ret = new BasicAuthentication(true, host, port, realm, a);
                    }
                } else if (schemeID == DigestAuthentication.DIGEST_AUTH) {
                    PasswordAuthentication a =
                        privilegedRequestPasswordAuthentication(
                                    host, null, port, url.getProtocol(),
                                    realm, scheme, url, RequestorType.PROXY);
                    if (a != null) {
                        DigestAuthentication.Parameters params =
                            new DigestAuthentication.Parameters();
                        ret = new DigestAuthentication(true, host, port, realm,
                                                            scheme, a, params);
                    }
                } else if (schemeID == NTLMAuthentication.NTLM_AUTH) {
                    PasswordAuthentication a = null;
                    if (!tryTransparentNTLMProxy) {
                        a = privilegedRequestPasswordAuthentication(
                                            host, null, port, url.getProtocol(),
                                            "", scheme, url, RequestorType.PROXY);
                    }
                    /* If we are not trying transparent authentication then
                     * we need to have a PasswordAuthentication instance. For
                     * transparent authentication (Windows only) the username
                     * and password will be picked up from the current logged
                     * on users credentials.
                    */
                    if (tryTransparentNTLMProxy ||
                          (!tryTransparentNTLMProxy && a != null)) {
                        ret = new NTLMAuthentication(true, host, port, a);
                    }

                    tryTransparentNTLMProxy = false;
                } else if (schemeID == NegotiateAuthentication.NEGOTIATE_AUTH) {
                    ret = new NegotiateAuthentication(true, host, port, null, "Negotiate");
                } else if (schemeID == NegotiateAuthentication.KERBEROS_AUTH) {
                    ret = new NegotiateAuthentication(true, host, port, null, "Kerberos");
                }
            }
            // For backwards compatibility, we also try defaultAuth
            // REMIND:  Get rid of this for JDK2.0.

            if (ret == null && defaultAuth != null
                && defaultAuth.schemeSupported(scheme)) {
                try {
                    URL u = new URL("http", host, port, "/");
                    String a = defaultAuth.authString(u, scheme, realm);
                    if (a != null) {
                        ret = new BasicAuthentication (true, host, port, realm, a);
                        // not in cache by default - cache on success
                    }
                } catch (java.net.MalformedURLException ignored) {
                }
            }
            if (ret != null) {
                if (!ret.setHeaders(this, p, raw)) {
                    ret = null;
                }
            }
        }
        return ret;
    }

    /**
     * Gets the authentication for an HTTP server, and applies it to
     * the connection.
     * @param authHdr the AuthenticationHeader which tells what auth scheme is
     * prefered.
     */
    private AuthenticationInfo getServerAuthentication (AuthenticationHeader authhdr) {
        /* get authorization from authenticator */
        AuthenticationInfo ret = null;
        String raw = authhdr.raw();
        /* When we get an NTLM auth from cache, don't set any special headers */
        if (authhdr.isPresent()) {
            HeaderParser p = authhdr.headerParser();
            String realm = p.findValue("realm");
            String scheme = authhdr.scheme();
            char schemeID;
            if ("basic".equalsIgnoreCase(scheme)) {
                schemeID = BasicAuthentication.BASIC_AUTH;
            } else if ("digest".equalsIgnoreCase(scheme)) {
                schemeID = DigestAuthentication.DIGEST_AUTH;
            } else if ("ntlm".equalsIgnoreCase(scheme)) {
                schemeID = NTLMAuthentication.NTLM_AUTH;
                doingNTLM2ndStage = true;
            } else if ("Kerberos".equalsIgnoreCase(scheme)) {
                schemeID = NegotiateAuthentication.KERBEROS_AUTH;
                doingNTLM2ndStage = true;
            } else if ("Negotiate".equalsIgnoreCase(scheme)) {
                schemeID = NegotiateAuthentication.NEGOTIATE_AUTH;
                doingNTLM2ndStage = true;
            } else {
                schemeID = 0;
            }
            domain = p.findValue ("domain");
            if (realm == null)
                realm = "";
            ret = AuthenticationInfo.getServerAuth(url, realm, schemeID);
            InetAddress addr = null;
            if (ret == null) {
                try {
                    addr = InetAddress.getByName(url.getHost());
                } catch (java.net.UnknownHostException ignored) {
                    // User will have addr = null
                }
            }
            // replacing -1 with default port for a protocol
            int port = url.getPort();
            if (port == -1) {
                port = url.getDefaultPort();
            }
            if (ret == null) {
                if (schemeID == NegotiateAuthentication.KERBEROS_AUTH) {
                    URL url1;
                    try {
                        url1 = new URL (url, "/"); /* truncate the path */
                    } catch (Exception e) {
                        url1 = url;
                    }
                    ret = new NegotiateAuthentication(false, url1, null, "Kerberos");
                }
                if (schemeID == NegotiateAuthentication.NEGOTIATE_AUTH) {
                    URL url1;
                    try {
                        url1 = new URL (url, "/"); /* truncate the path */
                    } catch (Exception e) {
                        url1 = url;
                    }
                    ret = new NegotiateAuthentication(false, url1, null, "Negotiate");
                }
                if (schemeID == BasicAuthentication.BASIC_AUTH) {
                    PasswordAuthentication a =
                        privilegedRequestPasswordAuthentication(
                            url.getHost(), addr, port, url.getProtocol(),
                            realm, scheme, url, RequestorType.SERVER);
                    if (a != null) {
                        ret = new BasicAuthentication(false, url, realm, a);
                    }
                }

                if (schemeID == DigestAuthentication.DIGEST_AUTH) {
                    PasswordAuthentication a =
                        privilegedRequestPasswordAuthentication(
                            url.getHost(), addr, port, url.getProtocol(),
                            realm, scheme, url, RequestorType.SERVER);
                    if (a != null) {
                        digestparams = new DigestAuthentication.Parameters();
                        ret = new DigestAuthentication(false, url, realm, scheme, a, digestparams);
                    }
                }

                if (schemeID == NTLMAuthentication.NTLM_AUTH) {
                    URL url1;
                    try {
                        url1 = new URL (url, "/"); /* truncate the path */
                    } catch (Exception e) {
                        url1 = url;
                    }
                    PasswordAuthentication a = null;
                    if (!tryTransparentNTLMServer) {
                        a = privilegedRequestPasswordAuthentication(
                            url.getHost(), addr, port, url.getProtocol(),
                            "", scheme, url, RequestorType.SERVER);
                    }

                    /* If we are not trying transparent authentication then
                     * we need to have a PasswordAuthentication instance. For
                     * transparent authentication (Windows only) the username
                     * and password will be picked up from the current logged
                     * on users credentials.
                     */
                    if (tryTransparentNTLMServer ||
                          (!tryTransparentNTLMServer && a != null)) {
                        ret = new NTLMAuthentication(false, url1, a);
                    }

                    tryTransparentNTLMServer = false;
                }
            }

            // For backwards compatibility, we also try defaultAuth
            // REMIND:  Get rid of this for JDK2.0.

            if (ret == null && defaultAuth != null
                && defaultAuth.schemeSupported(scheme)) {
                String a = defaultAuth.authString(url, scheme, realm);
                if (a != null) {
                    ret = new BasicAuthentication (false, url, realm, a);
                    // not in cache by default - cache on success
                }
            }

            if (ret != null ) {
                if (!ret.setHeaders(this, p, raw)) {
                    ret = null;
                }
            }
        }
        return ret;
    }

    /* inclose will be true if called from close(), in which case we
     * force the call to check because this is the last chance to do so.
     * If not in close(), then the authentication info could arrive in a trailer
     * field, which we have not read yet.
     */
    private void checkResponseCredentials (boolean inClose) throws IOException {
        try {
            if (!needToCheck)
                return;
            if (validateProxy && currentProxyCredentials != null) {
                String raw = responses.findValue ("Proxy-Authentication-Info");
                if (inClose || (raw != null)) {
                    currentProxyCredentials.checkResponse (raw, method, url);
                    currentProxyCredentials = null;
                }
            }
            if (validateServer && currentServerCredentials != null) {
                String raw = responses.findValue ("Authentication-Info");
                if (inClose || (raw != null)) {
                    currentServerCredentials.checkResponse (raw, method, url);
                    currentServerCredentials = null;
                }
            }
            if ((currentServerCredentials==null) && (currentProxyCredentials == null)) {
                needToCheck = false;
            }
        } catch (IOException e) {
            disconnectInternal();
            connected = false;
            throw e;
        }
    }

    /* Tells us whether to follow a redirect.  If so, it
     * closes the connection (break any keep-alive) and
     * resets the url, re-connects, and resets the request
     * property.
     */
    private boolean followRedirect() throws IOException {
        if (!getInstanceFollowRedirects()) {
            return false;
        }

        int stat = getResponseCode();
        if (stat < 300 || stat > 307 || stat == 306
                                || stat == HTTP_NOT_MODIFIED) {
            return false;
        }
        String loc = getHeaderField("Location");
        if (loc == null) {
            /* this should be present - if not, we have no choice
             * but to go forward w/ the response we got
             */
            return false;
        }
        URL locUrl;
        try {
            locUrl = new URL(loc);
            if (!url.getProtocol().equalsIgnoreCase(locUrl.getProtocol())) {
                return false;
            }

        } catch (MalformedURLException mue) {
          // treat loc as a relative URI to conform to popular browsers
          locUrl = new URL(url, loc);
        }
        disconnectInternal();
        if (streaming()) {
            throw new HttpRetryException (RETRY_MSG3, stat, loc);
        }

        // clear out old response headers!!!!
        responses = new MessageHeader();
        if (stat == HTTP_USE_PROXY) {
            /* This means we must re-request the resource through the
             * proxy denoted in the "Location:" field of the response.
             * Judging by the spec, the string in the Location header
             * _should_ denote a URL - let's hope for "http://my.proxy.org"
             * Make a new HttpClient to the proxy, using HttpClient's
             * Instance-specific proxy fields, but note we're still fetching
             * the same URL.
             */
            String proxyHost = locUrl.getHost();
            int proxyPort = locUrl.getPort();

            SecurityManager security = System.getSecurityManager();
            if (security != null) {
                security.checkConnect(proxyHost, proxyPort);
            }

            setProxiedClient (url, proxyHost, proxyPort);
            requests.set(0, method + " " + http.getURLFile()+" "  +
                             httpVersion, null);
            connected = true;
        } else {
            // maintain previous headers, just change the name
            // of the file we're getting
            url = locUrl;
            if (method.equals("POST") && !Boolean.getBoolean("http.strictPostRedirect") && (stat!=307)) {
                /* The HTTP/1.1 spec says that a redirect from a POST
                 * *should not* be immediately turned into a GET, and
                 * that some HTTP/1.0 clients incorrectly did this.
                 * Correct behavior redirects a POST to another POST.
                 * Unfortunately, since most browsers have this incorrect
                 * behavior, the web works this way now.  Typical usage
                 * seems to be:
                 *   POST a login code or passwd to a web page.
                 *   after validation, the server redirects to another
                 *     (welcome) page
                 *   The second request is (erroneously) expected to be GET
                 *
                 * We will do the incorrect thing (POST-->GET) by default.
                 * We will provide the capability to do the "right" thing
                 * (POST-->POST) by a system property, "http.strictPostRedirect=true"
                 */

                requests = new MessageHeader();
                setRequests = false;
                setRequestMethod("GET");
                poster = null;
                if (!checkReuseConnection())
                    connect();
            } else {
                if (!checkReuseConnection())
                    connect();
                /* Even after a connect() call, http variable still can be
                 * null, if a ResponseCache has been installed and it returns
                 * a non-null CacheResponse instance. So check nullity before using it.
                 *
                 * And further, if http is null, there's no need to do anything
                 * about request headers because successive http session will use
                 * cachedInputStream/cachedHeaders anyway, which is returned by
                 * CacheResponse.
                 */
                if (http != null) {
                    requests.set(0, method + " " + http.getURLFile()+" "  +
                                 httpVersion, null);
                    int port = url.getPort();
                    String host = url.getHost();
                    if (port != -1 && port != url.getDefaultPort()) {
                        host += ":" + String.valueOf(port);
                    }
                    requests.set("Host", host);
                }
            }
        }
        return true;
    }

    /* dummy byte buffer for reading off socket prior to closing */
    byte[] cdata = new byte [128];

    /**
     * Reset (without disconnecting the TCP conn) in order to do another transaction with this instance
     */
    private void reset() throws IOException {
        http.reuse = true;
        /* must save before calling close */
        reuseClient = http;
        InputStream is = http.getInputStream();
        if (!method.equals("HEAD")) {
            try {
                /* we want to read the rest of the response without using the
                 * hurry mechanism, because that would close the connection
                 * if everything is not available immediately
                 */
                if ((is instanceof ChunkedInputStream) ||
                    (is instanceof MeteredStream)) {
                    /* reading until eof will not block */
                    while (is.read (cdata) > 0) {}
                } else {
                    /* raw stream, which will block on read, so only read
                     * the expected number of bytes, probably 0
                     */
                    int cl = 0, n=0;
                    try {
                        cl = Integer.parseInt (responses.findValue ("Content-Length"));
                    } catch (Exception e) {}
                    for (int i=0; i<cl; ) {
                        if ((n = is.read (cdata)) == -1) {
                            break;
                        } else {
                            i+= n;
                        }
                    }
                }
            } catch (IOException e) {
                http.reuse = false;
                reuseClient = null;
                disconnectInternal();
                return;
            }
            try {
                if (is instanceof MeteredStream) {
                    is.close();
                }
            } catch (IOException e) { }
        }
        responseCode = -1;
        responses = new MessageHeader();
        connected = false;
    }

    /**
     * Disconnect from the server (for internal use)
     */
    private void disconnectInternal() {
        responseCode = -1;
        inputStream = null;
        if (pi != null) {
            pi.finishTracking();
            pi = null;
        }
        if (http != null) {
            http.closeServer();
            http = null;
            connected = false;
        }
    }

    /**
     * Disconnect from the server (public API)
     */
    public void disconnect() {

        responseCode = -1;
        if (pi != null) {
            pi.finishTracking();
            pi = null;
        }

        if (http != null) {
            /*
             * If we have an input stream this means we received a response
             * from the server. That stream may have been read to EOF and
             * dependening on the stream type may already be closed or the
             * the http client may be returned to the keep-alive cache.
             * If the http client has been returned to the keep-alive cache
             * it may be closed (idle timeout) or may be allocated to
             * another request.
             *
             * In other to avoid timing issues we close the input stream
             * which will either close the underlying connection or return
             * the client to the cache. If there's a possibility that the
             * client has been returned to the cache (ie: stream is a keep
             * alive stream or a chunked input stream) then we remove an
             * idle connection to the server. Note that this approach
             * can be considered an approximation in that we may close a
             * different idle connection to that used by the request.
             * Additionally it's possible that we close two connections
             * - the first becuase it wasn't an EOF (and couldn't be
             * hurried) - the second, another idle connection to the
             * same server. The is okay because "disconnect" is an
             * indication that the application doesn't intend to access
             * this http server for a while.
             */

            if (inputStream != null) {
                HttpClient hc = http;

                // un-synchronized
                boolean ka = hc.isKeepingAlive();

                try {
                    inputStream.close();
                } catch (IOException ioe) { }

                // if the connection is persistent it may have been closed
                // or returned to the keep-alive cache. If it's been returned
                // to the keep-alive cache then we would like to close it
                // but it may have been allocated

                if (ka) {
                    hc.closeIdleConnection();
                }


            } else {
                // We are deliberatly being disconnected so HttpClient
                // should not try to resend the request no matter what stage
                // of the connection we are in.
                http.setDoNotRetry(true);

                http.closeServer();
            }

            //      poster = null;
            http = null;
            connected = false;
        }
        cachedInputStream = null;
        if (cachedHeaders != null) {
            cachedHeaders.reset();
        }
    }

    public boolean usingProxy() {
        if (http != null) {
            return (http.getProxyHostUsed() != null);
        }
        return false;
    }

    /**
     * Gets a header field by name. Returns null if not known.
     * @param name the name of the header field
     */
    @Override
    public String getHeaderField(String name) {
        try {
            getInputStream();
        } catch (IOException e) {}

        if (cachedHeaders != null) {
            return cachedHeaders.findValue(name);
        }

        return responses.findValue(name);
    }

    /**
     * Returns an unmodifiable Map of the header fields.
     * The Map keys are Strings that represent the
     * response-header field names. Each Map value is an
     * unmodifiable List of Strings that represents
     * the corresponding field values.
     *
     * @return a Map of header fields
     * @since 1.4
     */
    @Override
    public Map<String, List<String>> getHeaderFields() {
        try {
            getInputStream();
        } catch (IOException e) {}

        if (cachedHeaders != null) {
            return cachedHeaders.getHeaders();
        }

        return responses.getHeaders();
    }

    /**
     * Gets a header field by index. Returns null if not known.
     * @param n the index of the header field
     */
    @Override
    public String getHeaderField(int n) {
        try {
            getInputStream();
        } catch (IOException e) {}

        if (cachedHeaders != null) {
           return cachedHeaders.getValue(n);
        }
        return responses.getValue(n);
    }

    /**
     * Gets a header field by index. Returns null if not known.
     * @param n the index of the header field
     */
    @Override
    public String getHeaderFieldKey(int n) {
        try {
            getInputStream();
        } catch (IOException e) {}

        if (cachedHeaders != null) {
            return cachedHeaders.getKey(n);
        }

        return responses.getKey(n);
    }

    /**
     * Sets request property. If a property with the key already
     * exists, overwrite its value with the new value.
     * @param value the value to be set
     */
    @Override
    public void setRequestProperty(String key, String value) {
        if (connected)
            throw new IllegalStateException("Already connected");
        if (key == null)
            throw new NullPointerException ("key is null");

        checkMessageHeader(key, value);
        requests.set(key, value);
    }

    /**
     * Adds a general request property specified by a
     * key-value pair.  This method will not overwrite
     * existing values associated with the same key.
     *
     * @param   key     the keyword by which the request is known
     *                  (e.g., "<code>accept</code>").
     * @param   value  the value associated with it.
     * @see #getRequestProperties(java.lang.String)
     * @since 1.4
     */
    @Override
    public void addRequestProperty(String key, String value) {
        if (connected)
            throw new IllegalStateException("Already connected");
        if (key == null)
            throw new NullPointerException ("key is null");

        checkMessageHeader(key, value);
        requests.add(key, value);
    }

    //
    // Set a property for authentication.  This can safely disregard
    // the connected test.
    //
    void setAuthenticationProperty(String key, String value) {
        checkMessageHeader(key, value);
        requests.set(key, value);
    }

    @Override
    public String getRequestProperty (String key) {
        // don't return headers containing security sensitive information
        if (key != null) {
            for (int i=0; i < EXCLUDE_HEADERS.length; i++) {
                if (key.equalsIgnoreCase(EXCLUDE_HEADERS[i])) {
                    return null;
                }
            }
        }
        return requests.findValue(key);
    }

    /**
     * Returns an unmodifiable Map of general request
     * properties for this connection. The Map keys
     * are Strings that represent the request-header
     * field names. Each Map value is a unmodifiable List
     * of Strings that represents the corresponding
     * field values.
     *
     * @return  a Map of the general request properties for this connection.
     * @throws IllegalStateException if already connected
     * @since 1.4
     */
    @Override
    public Map<String, List<String>> getRequestProperties() {
        if (connected)
            throw new IllegalStateException("Already connected");

        // exclude headers containing security-sensitive info
        return requests.getHeaders(EXCLUDE_HEADERS);
    }

    @Override
    public void setConnectTimeout(int timeout) {
        if (timeout < 0)
            throw new IllegalArgumentException("timeouts can't be negative");
        connectTimeout = timeout;
    }


    /**
     * Returns setting for connect timeout.
     * <p>
     * 0 return implies that the option is disabled
     * (i.e., timeout of infinity).
     *
     * @return an <code>int</code> that indicates the connect timeout
     *         value in milliseconds
     * @see java.net.URLConnection#setConnectTimeout(int)
     * @see java.net.URLConnection#connect()
     * @since 1.5
     */
    @Override
    public int getConnectTimeout() {
        return (connectTimeout < 0 ? 0 : connectTimeout);
    }

    /**
     * Sets the read timeout to a specified timeout, in
     * milliseconds. A non-zero value specifies the timeout when
     * reading from Input stream when a connection is established to a
     * resource. If the timeout expires before there is data available
     * for read, a java.net.SocketTimeoutException is raised. A
     * timeout of zero is interpreted as an infinite timeout.
     *
     * <p> Some non-standard implementation of this method ignores the
     * specified timeout. To see the read timeout set, please call
     * getReadTimeout().
     *
     * @param timeout an <code>int</code> that specifies the timeout
     * value to be used in milliseconds
     * @throws IllegalArgumentException if the timeout parameter is negative
     *
     * @see java.net.URLConnectiongetReadTimeout()
     * @see java.io.InputStream#read()
     * @since 1.5
     */
    @Override
    public void setReadTimeout(int timeout) {
        if (timeout < 0)
            throw new IllegalArgumentException("timeouts can't be negative");
        readTimeout = timeout;
    }

    /**
     * Returns setting for read timeout. 0 return implies that the
     * option is disabled (i.e., timeout of infinity).
     *
     * @return an <code>int</code> that indicates the read timeout
     *         value in milliseconds
     *
     * @see java.net.URLConnection#setReadTimeout(int)
     * @see java.io.InputStream#read()
     * @since 1.5
     */
    @Override
    public int getReadTimeout() {
        return readTimeout < 0 ? 0 : readTimeout;
    }

    @Override
    protected void finalize() {
        // this should do nothing.  The stream finalizer will close
        // the fd
    }

    String getMethod() {
        return method;
    }

    private MessageHeader mapToMessageHeader(Map<String, List<String>> map) {
        MessageHeader headers = new MessageHeader();
        if (map == null || map.isEmpty()) {
            return headers;
        }
        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            String key = entry.getKey();
            List<String> values = entry.getValue();
            for (String value : values) {
                if (key == null) {
                    headers.prepend(key, value);
                } else {
                    headers.add(key, value);
                }
            }
        }
        return headers;
    }

    /* The purpose of this wrapper is just to capture the close() call
     * so we can check authentication information that may have
     * arrived in a Trailer field
     */
    class HttpInputStream extends FilterInputStream {
        private CacheRequest cacheRequest;
        private OutputStream outputStream;
        private boolean marked = false;
        private int inCache = 0;
        private int markCount = 0;

        public HttpInputStream (InputStream is) {
            super (is);
            this.cacheRequest = null;
            this.outputStream = null;
        }

        public HttpInputStream (InputStream is, CacheRequest cacheRequest) {
            super (is);
            this.cacheRequest = cacheRequest;
            try {
                this.outputStream = cacheRequest.getBody();
            } catch (IOException ioex) {
                this.cacheRequest.abort();
                this.cacheRequest = null;
                this.outputStream = null;
            }
        }

        /**
         * Marks the current position in this input stream. A subsequent
         * call to the <code>reset</code> method repositions this stream at
         * the last marked position so that subsequent reads re-read the same
         * bytes.
         * <p>
         * The <code>readlimit</code> argument tells this input stream to
         * allow that many bytes to be read before the mark position gets
         * invalidated.
         * <p>
         * This method simply performs <code>in.mark(readlimit)</code>.
         *
         * @param   readlimit   the maximum limit of bytes that can be read before
         *                      the mark position becomes invalid.
         * @see     java.io.FilterInputStream#in
         * @see     java.io.FilterInputStream#reset()
         */
        @Override
        public synchronized void mark(int readlimit) {
            super.mark(readlimit);
            if (cacheRequest != null) {
                marked = true;
                markCount = 0;
            }
        }

        /**
         * Repositions this stream to the position at the time the
         * <code>mark</code> method was last called on this input stream.
         * <p>
         * This method
         * simply performs <code>in.reset()</code>.
         * <p>
         * Stream marks are intended to be used in
         * situations where you need to read ahead a little to see what's in
         * the stream. Often this is most easily done by invoking some
         * general parser. If the stream is of the type handled by the
         * parse, it just chugs along happily. If the stream is not of
         * that type, the parser should toss an exception when it fails.
         * If this happens within readlimit bytes, it allows the outer
         * code to reset the stream and try another parser.
         *
         * @exception  IOException  if the stream has not been marked or if the
         *               mark has been invalidated.
         * @see        java.io.FilterInputStream#in
         * @see        java.io.FilterInputStream#mark(int)
         */
        @Override
        public synchronized void reset() throws IOException {
            super.reset();
            if (cacheRequest != null) {
                marked = false;
                inCache += markCount;
            }
        }

        @Override
        public int read() throws IOException {
            try {
                byte[] b = new byte[1];
                int ret = read(b);
                return (ret == -1? ret : (b[0] & 0x00FF));
            } catch (IOException ioex) {
                if (cacheRequest != null) {
                    cacheRequest.abort();
                }
                throw ioex;
            }
        }

        @Override
        public int read(byte[] b) throws IOException {
            return read(b, 0, b.length);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            try {
                int newLen = super.read(b, off, len);
                int nWrite;
                // write to cache
                if (inCache > 0) {
                    if (inCache >= newLen) {
                        inCache -= newLen;
                        nWrite = 0;
                    } else {
                        nWrite = newLen - inCache;
                        inCache = 0;
                    }
                } else {
                    nWrite = newLen;
                }
                if (nWrite > 0 && outputStream != null)
                    outputStream.write(b, off + (newLen-nWrite), nWrite);
                if (marked) {
                    markCount += newLen;
                }
                return newLen;
            } catch (IOException ioex) {
                if (cacheRequest != null) {
                    cacheRequest.abort();
                }
                throw ioex;
            }
        }

        @Override
        public void close () throws IOException {
            try {
                if (outputStream != null) {
                    if (read() != -1) {
                        cacheRequest.abort();
                    } else {
                        outputStream.close();
                    }
                }
                super.close ();
            } catch (IOException ioex) {
                if (cacheRequest != null) {
                    cacheRequest.abort();
                }
                throw ioex;
            } finally {
                HttpURLConnection.this.http = null;
                checkResponseCredentials (true);
            }
        }
    }

    class StreamingOutputStream extends FilterOutputStream {

        long expected;
        long written;
        boolean closed;
        boolean error;
        IOException errorExcp;

        /**
         * expectedLength == -1 if the stream is chunked
         * expectedLength > 0 if the stream is fixed content-length
         *    In the 2nd case, we make sure the expected number of
         *    of bytes are actually written
         */
        StreamingOutputStream (OutputStream os, long expectedLength) {
            super (os);
            expected = expectedLength;
            written = 0L;
            closed = false;
            error = false;
        }

        @Override
        public void write (int b) throws IOException {
            checkError();
            written ++;
            if (expected != -1L && written > expected) {
                throw new IOException ("too many bytes written");
            }
            out.write (b);
        }

        @Override
        public void write (byte[] b) throws IOException {
            write (b, 0, b.length);
        }

        @Override
        public void write (byte[] b, int off, int len) throws IOException {
            checkError();
            written += len;
            if (expected != -1L && written > expected) {
                out.close ();
                throw new IOException ("too many bytes written");
            }
            out.write (b, off, len);
        }

        void checkError () throws IOException {
            if (closed) {
                throw new IOException ("Stream is closed");
            }
            if (error) {
                throw errorExcp;
            }
            if (((PrintStream)out).checkError()) {
                throw new IOException("Error writing request body to server");
            }
        }

        /* this is called to check that all the bytes
         * that were supposed to be written were written
         * and that the stream is now closed().
         */
        boolean writtenOK () {
            return closed && ! error;
        }

        @Override
        public void close () throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            if (expected != -1L) {
                /* not chunked */
                if (written != expected) {
                    error = true;
                    errorExcp = new IOException ("insufficient data written");
                    out.close ();
                    throw errorExcp;
                }
                super.flush(); /* can't close the socket */
            } else {
                /* chunked */
                super.close (); /* force final chunk to be written */
                /* trailing \r\n */
                OutputStream o = http.getOutputStream();
                o.write ('\r');
                o.write ('\n');
                o.flush();
            }
        }
    }


    static class ErrorStream extends InputStream {
        ByteBuffer buffer;
        InputStream is;

        private ErrorStream(ByteBuffer buf) {
            buffer = buf;
            is = null;
        }

        private ErrorStream(ByteBuffer buf, InputStream is) {
            buffer = buf;
            this.is = is;
        }

        // when this method is called, it's either the case that cl > 0, or
        // if chunk-encoded, cl = -1; in other words, cl can't be 0
        public static InputStream getErrorStream(InputStream is, long cl, HttpClient http) {

            // cl can't be 0; this following is here for extra precaution
            if (cl == 0) {
                return null;
            }

            try {
                // set SO_TIMEOUT to 1/5th of the total timeout
                // remember the old timeout value so that we can restore it
                int oldTimeout = http.getReadTimeout();
                http.setReadTimeout(timeout4ESBuffer/5);

                long expected = 0;
                boolean isChunked = false;
                // the chunked case
                if (cl < 0) {
                    expected = bufSize4ES;
                    isChunked = true;
                } else {
                    expected = cl;
                }
                if (expected <= bufSize4ES) {
                    int exp = (int) expected;
                    byte[] buffer = new byte[exp];
                    int count = 0, time = 0, len = 0;
                    do {
                        try {
                            len = is.read(buffer, count,
                                             buffer.length - count);
                            if (len < 0) {
                                if (isChunked) {
                                    // chunked ended
                                    // if chunked ended prematurely,
                                    // an IOException would be thrown
                                    break;
                                }
                                // the server sends less than cl bytes of data
                                throw new IOException("the server closes"+
                                                      " before sending "+cl+
                                                      " bytes of data");
                            }
                            count += len;
                        } catch (SocketTimeoutException ex) {
                            time += timeout4ESBuffer/5;
                        }
                    } while (count < exp && time < timeout4ESBuffer);

                    // reset SO_TIMEOUT to old value
                    http.setReadTimeout(oldTimeout);

                    // if count < cl at this point, we will not try to reuse
                    // the connection
                    if (count == 0) {
                        // since we haven't read anything,
                        // we will return the underlying
                        // inputstream back to the application
                        return null;
                    }  else if ((count == expected && !(isChunked)) || (isChunked && len <0)) {
                        // put the connection into keep-alive cache
                        // the inputstream will try to do the right thing
                        is.close();
                        return new ErrorStream(ByteBuffer.wrap(buffer, 0, count));
                    } else {
                        // we read part of the response body
                        return new ErrorStream(
                                      ByteBuffer.wrap(buffer, 0, count), is);
                    }
                }
                return null;
            } catch (IOException ioex) {
                // ioex.printStackTrace();
                return null;
            }
        }

        @Override
        public int available() throws IOException {
            if (is == null) {
                return buffer.remaining();
            } else {
                return buffer.remaining()+is.available();
            }
        }

        public int read() throws IOException {
            byte[] b = new byte[1];
            int ret = read(b);
            return (ret == -1? ret : (b[0] & 0x00FF));
        }

        @Override
        public int read(byte[] b) throws IOException {
            return read(b, 0, b.length);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int rem = buffer.remaining();
            if (rem > 0) {
                int ret = rem < len? rem : len;
                buffer.get(b, off, ret);
                return ret;
            } else {
                if (is == null) {
                    return -1;
                } else {
                    return is.read(b, off, len);
                }
            }
        }

        @Override
        public void close() throws IOException {
            buffer = null;
            if (is != null) {
                is.close();
            }
        }
    }
}

/** An input stream that just returns EOF.  This is for
 * HTTP URLConnections that are KeepAlive && use the
 * HEAD method - i.e., stream not dead, but nothing to be read.
 */

class EmptyInputStream extends InputStream {

    @Override
    public int available() {
        return 0;
    }

    public int read() {
        return -1;
    }
}
