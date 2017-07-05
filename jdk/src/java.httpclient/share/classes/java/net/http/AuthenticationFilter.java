/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
 */
package java.net.http;

import java.io.IOException;
import static java.net.Authenticator.RequestorType.PROXY;
import static java.net.Authenticator.RequestorType.SERVER;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedList;
import static java.nio.charset.StandardCharsets.ISO_8859_1;

/**
 * Implementation of Http Basic authentication.
 */
class AuthenticationFilter implements HeaderFilter {

    static private final Base64.Encoder encoder = Base64.getEncoder();

    static final int DEFAULT_RETRY_LIMIT = 3;

    static final int retry_limit = Utils.getIntegerNetProperty(
            "sun.net.httpclient.auth.retrylimit", DEFAULT_RETRY_LIMIT);

    static final int UNAUTHORIZED = 401;
    static final int PROXY_UNAUTHORIZED = 407;

    private PasswordAuthentication getCredentials(String header,
                                                  boolean proxy,
                                                  HttpRequestImpl req)
        throws IOException
    {
        HttpClientImpl client = req.client();
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
    public void request(HttpRequestImpl r) throws IOException {
        // use preemptive authentication if an entry exists.
        Cache cache = getCache(r);

        // Proxy
        if (r.exchange.proxyauth == null) {
            URI proxyURI = getProxyURI(r);
            if (proxyURI != null) {
                CacheEntry ca = cache.get(proxyURI, true);
                if (ca != null) {
                    r.exchange.proxyauth = new AuthInfo(true, ca.scheme, null, ca);
                    addBasicCredentials(r, true, ca.value);
                }
            }
        }

        // Server
        if (r.exchange.serverauth == null) {
            CacheEntry ca = cache.get(r.uri(), false);
            if (ca != null) {
                r.exchange.serverauth = new AuthInfo(true, ca.scheme, null, ca);
                addBasicCredentials(r, false, ca.value);
            }
        }
    }

    // TODO: refactor into per auth scheme class
    static private void addBasicCredentials(HttpRequestImpl r,
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
    }

    @Override
    public HttpRequestImpl response(HttpResponseImpl r) throws IOException {
        Cache cache = getCache(r.request);
        int status = r.statusCode();
        HttpHeaders hdrs = r.headers();
        HttpRequestImpl req = r.request();

        if (status != UNAUTHORIZED && status != PROXY_UNAUTHORIZED) {
            // check if any authentication succeeded for first time
            if (req.exchange.serverauth != null && !req.exchange.serverauth.fromcache) {
                AuthInfo au = req.exchange.serverauth;
                cache.store(au.scheme, req.uri(), false, au.credentials);
            }
            if (req.exchange.proxyauth != null && !req.exchange.proxyauth.fromcache) {
                AuthInfo au = req.exchange.proxyauth;
                cache.store(au.scheme, req.uri(), false, au.credentials);
            }
            return null;
        }

        boolean proxy = status == PROXY_UNAUTHORIZED;
        String authname = proxy ? "Proxy-Authentication" : "WWW-Authenticate";
        String authval = hdrs.firstValue(authname).orElseThrow(() -> {
            return new IOException("Invalid auth header");
        });
        HeaderParser parser = new HeaderParser(authval);
        String scheme = parser.findKey(0);

        // TODO: Need to generalise from Basic only. Delegate to a provider class etc.

        if (!scheme.equalsIgnoreCase("Basic")) {
            return null;   // error gets returned to app
        }

        String realm = parser.findValue("realm");
        AuthInfo au = proxy ? req.exchange.proxyauth : req.exchange.serverauth;
        if (au == null) {
            PasswordAuthentication pw = getCredentials(authval, proxy, req);
            if (pw == null) {
                throw new IOException("No credentials provided");
            }
            // No authentication in request. Get credentials from user
            au = new AuthInfo(false, "Basic", pw);
            if (proxy)
                req.exchange.proxyauth = au;
            else
                req.exchange.serverauth = au;
            addBasicCredentials(req, proxy, pw);
            return req;
        } else if (au.retries > retry_limit) {
            throw new IOException("too many authentication attempts");
        } else {
            // we sent credentials, but they were rejected
            if (au.fromcache) {
                cache.remove(au.cacheEntry);
            }
            // try again
            au.credentials = getCredentials(authval, proxy, req);
            addBasicCredentials(req, proxy, au.credentials);
            au.retries++;
            return req;
        }
    }

    static final HashMap<HttpClientImpl,Cache> caches = new HashMap<>();

    static synchronized Cache getCache(HttpRequestImpl req) {
        HttpClientImpl client = req.client();
        Cache c = caches.get(client);
        if (c == null) {
            c = new Cache();
            caches.put(client, c);
        }
        return c;
    }

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
