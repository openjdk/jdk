/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.incubator.http;

import java.io.IOException;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.LinkedList;
import java.util.Objects;
import java.util.WeakHashMap;
import jdk.incubator.http.internal.common.Utils;
import static java.net.Authenticator.RequestorType.PROXY;
import static java.net.Authenticator.RequestorType.SERVER;
import static java.nio.charset.StandardCharsets.ISO_8859_1;

/**
 * Implementation of Http Basic authentication.
 */
class AuthenticationFilter implements HeaderFilter {
    volatile MultiExchange<?,?> exchange;
    private static final Base64.Encoder encoder = Base64.getEncoder();

    static final int DEFAULT_RETRY_LIMIT = 3;

    static final int retry_limit = Utils.getIntegerNetProperty(
            "jdk.httpclient.auth.retrylimit", DEFAULT_RETRY_LIMIT);

    static final int UNAUTHORIZED = 401;
    static final int PROXY_UNAUTHORIZED = 407;

    // A public no-arg constructor is required by FilterFactory
    public AuthenticationFilter() {}

    private PasswordAuthentication getCredentials(String header,
                                                  boolean proxy,
                                                  HttpRequestImpl req)
        throws IOException
    {
        HttpClientImpl client = exchange.client();
        java.net.Authenticator auth =
                client.authenticator()
                      .orElseThrow(() -> new IOException("No authenticator set"));
        URI uri = req.uri();
        HeaderParser parser = new HeaderParser(header);
        String authscheme = parser.findKey(0);

        String realm = parser.findValue("realm");
        java.net.Authenticator.RequestorType rtype = proxy ? PROXY : SERVER;

        // needs to be instance method in Authenticator
        return auth.requestPasswordAuthenticationInstance(uri.getHost(),
                                                          null,
                                                          uri.getPort(),
                                                          uri.getScheme(),
                                                          realm,
                                                          authscheme,
                                                          uri.toURL(),
                                                          rtype
        );
    }

    private URI getProxyURI(HttpRequestImpl r) {
        InetSocketAddress proxy = r.proxy();
        if (proxy == null) {
            return null;
        }

        // our own private scheme for proxy URLs
        // eg. proxy.http://host:port/
        String scheme = "proxy." + r.uri().getScheme();
        try {
            return new URI(scheme,
                           null,
                           proxy.getHostString(),
                           proxy.getPort(),
                           null,
                           null,
                           null);
        } catch (URISyntaxException e) {
            throw new InternalError(e);
        }
    }

    @Override
    public void request(HttpRequestImpl r, MultiExchange<?,?> e) throws IOException {
        // use preemptive authentication if an entry exists.
        Cache cache = getCache(e);
        this.exchange = e;

        // Proxy
        if (exchange.proxyauth == null) {
            URI proxyURI = getProxyURI(r);
            if (proxyURI != null) {
                CacheEntry ca = cache.get(proxyURI, true);
                if (ca != null) {
                    exchange.proxyauth = new AuthInfo(true, ca.scheme, null, ca);
                    addBasicCredentials(r, true, ca.value);
                }
            }
        }

        // Server
        if (exchange.serverauth == null) {
            CacheEntry ca = cache.get(r.uri(), false);
            if (ca != null) {
                exchange.serverauth = new AuthInfo(true, ca.scheme, null, ca);
                addBasicCredentials(r, false, ca.value);
            }
        }
    }

    // TODO: refactor into per auth scheme class
    private static void addBasicCredentials(HttpRequestImpl r,
                                            boolean proxy,
                                            PasswordAuthentication pw) {
        String hdrname = proxy ? "Proxy-Authorization" : "Authorization";
        StringBuilder sb = new StringBuilder(128);
        sb.append(pw.getUserName()).append(':').append(pw.getPassword());
        String s = encoder.encodeToString(sb.toString().getBytes(ISO_8859_1));
        String value = "Basic " + s;
        r.setSystemHeader(hdrname, value);
    }

    // Information attached to a HttpRequestImpl relating to authentication
    static class AuthInfo {
        final boolean fromcache;
        final String scheme;
        int retries;
        PasswordAuthentication credentials; // used in request
        CacheEntry cacheEntry; // if used

        AuthInfo(boolean fromcache,
                 String scheme,
                 PasswordAuthentication credentials) {
            this.fromcache = fromcache;
            this.scheme = scheme;
            this.credentials = credentials;
            this.retries = 1;
        }

        AuthInfo(boolean fromcache,
                 String scheme,
                 PasswordAuthentication credentials,
                 CacheEntry ca) {
            this(fromcache, scheme, credentials);
            assert credentials == null || (ca != null && ca.value == null);
            cacheEntry = ca;
        }

        AuthInfo retryWithCredentials(PasswordAuthentication pw) {
            // If the info was already in the cache we need to create a new
            // instance with fromCache==false so that it's put back in the
            // cache if authentication succeeds
            AuthInfo res = fromcache ? new AuthInfo(false, scheme, pw) : this;
            res.credentials = Objects.requireNonNull(pw);
            res.retries = retries;
            return res;
        }

    }

    @Override
    public HttpRequestImpl response(Response r) throws IOException {
        Cache cache = getCache(exchange);
        int status = r.statusCode();
        HttpHeaders hdrs = r.headers();
        HttpRequestImpl req = r.request();

        if (status != UNAUTHORIZED && status != PROXY_UNAUTHORIZED) {
            // check if any authentication succeeded for first time
            if (exchange.serverauth != null && !exchange.serverauth.fromcache) {
                AuthInfo au = exchange.serverauth;
                cache.store(au.scheme, req.uri(), false, au.credentials);
            }
            if (exchange.proxyauth != null && !exchange.proxyauth.fromcache) {
                AuthInfo au = exchange.proxyauth;
                cache.store(au.scheme, req.uri(), false, au.credentials);
            }
            return null;
        }

        boolean proxy = status == PROXY_UNAUTHORIZED;
        String authname = proxy ? "Proxy-Authenticate" : "WWW-Authenticate";
        String authval = hdrs.firstValue(authname).orElseThrow(() -> {
            return new IOException("Invalid auth header");
        });
        HeaderParser parser = new HeaderParser(authval);
        String scheme = parser.findKey(0);

        // TODO: Need to generalise from Basic only. Delegate to a provider class etc.

        if (!scheme.equalsIgnoreCase("Basic")) {
            return null;   // error gets returned to app
        }

        AuthInfo au = proxy ? exchange.proxyauth : exchange.serverauth;
        if (au == null) {
            PasswordAuthentication pw = getCredentials(authval, proxy, req);
            if (pw == null) {
                throw new IOException("No credentials provided");
            }
            // No authentication in request. Get credentials from user
            au = new AuthInfo(false, "Basic", pw);
            if (proxy) {
                exchange.proxyauth = au;
            } else {
                exchange.serverauth = au;
            }
            addBasicCredentials(req, proxy, pw);
            return req;
        } else if (au.retries > retry_limit) {
            throw new IOException("too many authentication attempts. Limit: " +
                    Integer.toString(retry_limit));
        } else {
            // we sent credentials, but they were rejected
            if (au.fromcache) {
                cache.remove(au.cacheEntry);
            }
            // try again
            PasswordAuthentication pw = getCredentials(authval, proxy, req);
            if (pw == null) {
                throw new IOException("No credentials provided");
            }
            au = au.retryWithCredentials(pw);
            if (proxy) {
                exchange.proxyauth = au;
            } else {
                exchange.serverauth = au;
            }
            addBasicCredentials(req, proxy, au.credentials);
            au.retries++;
            return req;
        }
    }

    // Use a WeakHashMap to make it possible for the HttpClient to
    // be garbaged collected when no longer referenced.
    static final WeakHashMap<HttpClientImpl,Cache> caches = new WeakHashMap<>();

    static synchronized Cache getCache(MultiExchange<?,?> exchange) {
        HttpClientImpl client = exchange.client();
        Cache c = caches.get(client);
        if (c == null) {
            c = new Cache();
            caches.put(client, c);
        }
        return c;
    }

    // Note: Make sure that Cache and CacheEntry do not keep any strong
    //       reference to the HttpClient: it would prevent the client being
    //       GC'ed when no longer referenced.
    static class Cache {
        final LinkedList<CacheEntry> entries = new LinkedList<>();

        synchronized CacheEntry get(URI uri, boolean proxy) {
            for (CacheEntry entry : entries) {
                if (entry.equalsKey(uri, proxy)) {
                    return entry;
                }
            }
            return null;
        }

        synchronized void remove(String authscheme, URI domain, boolean proxy) {
            for (CacheEntry entry : entries) {
                if (entry.equalsKey(domain, proxy)) {
                    entries.remove(entry);
                }
            }
        }

        synchronized void remove(CacheEntry entry) {
            entries.remove(entry);
        }

        synchronized void store(String authscheme,
                                URI domain,
                                boolean proxy,
                                PasswordAuthentication value) {
            remove(authscheme, domain, proxy);
            entries.add(new CacheEntry(authscheme, domain, proxy, value));
        }
    }

    static class CacheEntry {
        final String root;
        final String scheme;
        final boolean proxy;
        final PasswordAuthentication value;

        CacheEntry(String authscheme,
                   URI uri,
                   boolean proxy,
                   PasswordAuthentication value) {
            this.scheme = authscheme;
            this.root = uri.resolve(".").toString(); // remove extraneous components
            this.proxy = proxy;
            this.value = value;
        }

        public PasswordAuthentication value() {
            return value;
        }

        public boolean equalsKey(URI uri, boolean proxy) {
            if (this.proxy != proxy) {
                return false;
            }
            String other = uri.toString();
            return other.startsWith(root);
        }
    }
}
