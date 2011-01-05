/*
 * Copyright (c) 1995, 2010, Oracle and/or its affiliates. All rights reserved.
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

package sun.net.www.protocol.http;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.util.HashMap;

import sun.net.www.HeaderParser;


/**
 * AuthenticationInfo: Encapsulate the information needed to
 * authenticate a user to a server.
 *
 * @author Jon Payne
 * @author Herb Jellinek
 * @author Bill Foote
 */
// REMIND:  It would be nice if this class understood about partial matching.
//      If you're authorized for foo.com, chances are high you're also
//      authorized for baz.foo.com.
// NB:  When this gets implemented, be careful about the uncaching
//      policy in HttpURLConnection.  A failure on baz.foo.com shouldn't
//      uncache foo.com!

public abstract class AuthenticationInfo extends AuthCacheValue implements Cloneable {

    // Constants saying what kind of authroization this is.  This determines
    // the namespace in the hash table lookup.
    public static final char SERVER_AUTHENTICATION = 's';
    public static final char PROXY_AUTHENTICATION = 'p';

    /**
     * If true, then simultaneous authentication requests to the same realm/proxy
     * are serialized, in order to avoid a user having to type the same username/passwords
     * repeatedly, via the Authenticator. Default is false, which means that this
     * behavior is switched off.
     */
    static boolean serializeAuth;

    static {
        serializeAuth = java.security.AccessController.doPrivileged(
            new sun.security.action.GetBooleanAction(
                "http.auth.serializeRequests")).booleanValue();
    }

    /* AuthCacheValue: */

    transient protected PasswordAuthentication pw;

    public PasswordAuthentication credentials() {
        return pw;
    }

    public AuthCacheValue.Type getAuthType() {
        return type == SERVER_AUTHENTICATION ?
            AuthCacheValue.Type.Server:
            AuthCacheValue.Type.Proxy;
    }

    AuthScheme getAuthScheme() {
        return authScheme;
    }

    public String getHost() {
        return host;
    }
    public int getPort() {
        return port;
    }
    public String getRealm() {
        return realm;
    }
    public String getPath() {
        return path;
    }
    public String getProtocolScheme() {
        return protocol;
    }

    /**
     * requests is used to ensure that interaction with the
     * Authenticator for a particular realm is single threaded.
     * ie. if multiple threads need to get credentials from the user
     * at the same time, then all but the first will block until
     * the first completes its authentication.
     */
    static private HashMap<String,Thread> requests = new HashMap<>();

    /* check if a request for this destination is in progress
     * return false immediately if not. Otherwise block until
     * request is finished and return true
     */
    static private boolean requestIsInProgress (String key) {
        if (!serializeAuth) {
            /* behavior is disabled. Revert to concurrent requests */
            return false;
        }
        synchronized (requests) {
            Thread t, c;
            c = Thread.currentThread();
            if ((t = requests.get(key)) == null) {
                requests.put (key, c);
                return false;
            }
            if (t == c) {
                return false;
            }
            while (requests.containsKey(key)) {
                try {
                    requests.wait ();
                } catch (InterruptedException e) {}
            }
        }
        /* entry may be in cache now. */
        return true;
    }

    /* signal completion of an authentication (whether it succeeded or not)
     * so that other threads can continue.
     */
    static private void requestCompleted (String key) {
        synchronized (requests) {
            Thread thread = requests.get(key);
            if (thread != null && thread == Thread.currentThread()) {
                boolean waspresent = requests.remove(key) != null;
                assert waspresent;
            }
            requests.notifyAll();
        }
    }

    //public String toString () {
        //return ("{"+type+":"+authScheme+":"+protocol+":"+host+":"+port+":"+realm+":"+path+"}");
    //}

    // REMIND:  This cache just grows forever.  We should put in a bounded
    //          cache, or maybe something using WeakRef's.

    /** The type (server/proxy) of authentication this is.  Used for key lookup */
    char type;

    /** The authentication scheme (basic/digest). Also used for key lookup */
    AuthScheme authScheme;

    /** The protocol/scheme (i.e. http or https ). Need to keep the caches
     *  logically separate for the two protocols. This field is only used
     *  when constructed with a URL (the normal case for server authentication)
     *  For proxy authentication the protocol is not relevant.
     */
    String protocol;

    /** The host we're authenticating against. */
    String host;

    /** The port on the host we're authenticating against. */
    int port;

    /** The realm we're authenticating against. */
    String realm;

    /** The shortest path from the URL we authenticated against. */
    String path;

    /** Use this constructor only for proxy entries */
    public AuthenticationInfo(char type, AuthScheme authScheme, String host, int port, String realm) {
        this.type = type;
        this.authScheme = authScheme;
        this.protocol = "";
        this.host = host.toLowerCase();
        this.port = port;
        this.realm = realm;
        this.path = null;
    }

    public Object clone() {
        try {
            return super.clone ();
        } catch (CloneNotSupportedException e) {
            // Cannot happen because Cloneable implemented by AuthenticationInfo
            return null;
        }
    }

    /*
     * Constructor used to limit the authorization to the path within
     * the URL. Use this constructor for origin server entries.
     */
    public AuthenticationInfo(char type, AuthScheme authScheme, URL url, String realm) {
        this.type = type;
        this.authScheme = authScheme;
        this.protocol = url.getProtocol().toLowerCase();
        this.host = url.getHost().toLowerCase();
        this.port = url.getPort();
        if (this.port == -1) {
            this.port = url.getDefaultPort();
        }
        this.realm = realm;

        String urlPath = url.getPath();
        if (urlPath.length() == 0)
            this.path = urlPath;
        else {
            this.path = reducePath (urlPath);
        }

    }

    /*
     * reduce the path to the root of where we think the
     * authorization begins. This could get shorter as
     * the url is traversed up following a successful challenge.
     */
    static String reducePath (String urlPath) {
        int sepIndex = urlPath.lastIndexOf('/');
        int targetSuffixIndex = urlPath.lastIndexOf('.');
        if (sepIndex != -1)
            if (sepIndex < targetSuffixIndex)
                return urlPath.substring(0, sepIndex+1);
            else
                return urlPath;
        else
            return urlPath;
    }

    /**
     * Returns info for the URL, for an HTTP server auth.  Used when we
     * don't yet know the realm
     * (i.e. when we're preemptively setting the auth).
     */
    static AuthenticationInfo getServerAuth(URL url) {
        int port = url.getPort();
        if (port == -1) {
            port = url.getDefaultPort();
        }
        String key = SERVER_AUTHENTICATION + ":" + url.getProtocol().toLowerCase()
                + ":" + url.getHost().toLowerCase() + ":" + port;
        return getAuth(key, url);
    }

    /**
     * Returns info for the URL, for an HTTP server auth.  Used when we
     * do know the realm (i.e. when we're responding to a challenge).
     * In this case we do not use the path because the protection space
     * is identified by the host:port:realm only
     */
    static String getServerAuthKey(URL url, String realm, AuthScheme scheme) {
        int port = url.getPort();
        if (port == -1) {
            port = url.getDefaultPort();
        }
        String key = SERVER_AUTHENTICATION + ":" + scheme + ":" + url.getProtocol().toLowerCase()
                     + ":" + url.getHost().toLowerCase() + ":" + port + ":" + realm;
        return key;
    }

    static AuthenticationInfo getServerAuth(String key) {
        AuthenticationInfo cached = getAuth(key, null);
        if ((cached == null) && requestIsInProgress (key)) {
            /* check the cache again, it might contain an entry */
            cached = getAuth(key, null);
        }
        return cached;
    }


    /**
     * Return the AuthenticationInfo object from the cache if it's path is
     * a substring of the supplied URLs path.
     */
    static AuthenticationInfo getAuth(String key, URL url) {
        if (url == null) {
            return (AuthenticationInfo)cache.get (key, null);
        } else {
            return (AuthenticationInfo)cache.get (key, url.getPath());
        }
    }

    /**
     * Returns a firewall authentication, for the given host/port.  Used
     * for preemptive header-setting. Note, the protocol field is always
     * blank for proxies.
     */
    static AuthenticationInfo getProxyAuth(String host, int port) {
        String key = PROXY_AUTHENTICATION + "::" + host.toLowerCase() + ":" + port;
        AuthenticationInfo result = (AuthenticationInfo) cache.get(key, null);
        return result;
    }

    /**
     * Returns a firewall authentication, for the given host/port and realm.
     * Used in response to a challenge. Note, the protocol field is always
     * blank for proxies.
     */
    static String getProxyAuthKey(String host, int port, String realm, AuthScheme scheme) {
        String key = PROXY_AUTHENTICATION + ":" + scheme + "::" + host.toLowerCase()
                        + ":" + port + ":" + realm;
        return key;
    }

    static AuthenticationInfo getProxyAuth(String key) {
        AuthenticationInfo cached = (AuthenticationInfo) cache.get(key, null);
        if ((cached == null) && requestIsInProgress (key)) {
            /* check the cache again, it might contain an entry */
            cached = (AuthenticationInfo) cache.get(key, null);
        }
        return cached;
    }


    /**
     * Add this authentication to the cache
     */
    void addToCache() {
        String key = cacheKey(true);
        cache.put(key, this);
        if (supportsPreemptiveAuthorization()) {
            cache.put(cacheKey(false), this);
        }
        endAuthRequest(key);
    }

    static void endAuthRequest (String key) {
        if (!serializeAuth) {
            return;
        }
        synchronized (requests) {
            requestCompleted(key);
        }
    }

    /**
     * Remove this authentication from the cache
     */
    void removeFromCache() {
        cache.remove(cacheKey(true), this);
        if (supportsPreemptiveAuthorization()) {
            cache.remove(cacheKey(false), this);
        }
    }

    /**
     * @return true if this authentication supports preemptive authorization
     */
    public abstract boolean supportsPreemptiveAuthorization();

    /**
     * @return the name of the HTTP header this authentication wants set.
     *          This is used for preemptive authorization.
     */
    public String getHeaderName() {
        if (type == SERVER_AUTHENTICATION) {
            return "Authorization";
        } else {
            return "Proxy-authorization";
        }
    }

    /**
     * Calculates and returns the authentication header value based
     * on the stored authentication parameters. If the calculation does not depend
     * on the URL or the request method then these parameters are ignored.
     * @param url The URL
     * @param method The request method
     * @return the value of the HTTP header this authentication wants set.
     *          Used for preemptive authorization.
     */
    public abstract String getHeaderValue(URL url, String method);

    /**
     * Set header(s) on the given connection.  Subclasses must override
     * This will only be called for
     * definitive (i.e. non-preemptive) authorization.
     * @param conn The connection to apply the header(s) to
     * @param p A source of header values for this connection, if needed.
     * @param raw The raw header field (if needed)
     * @return true if all goes well, false if no headers were set.
     */
    public abstract boolean setHeaders(HttpURLConnection conn, HeaderParser p, String raw);

    /**
     * Check if the header indicates that the current auth. parameters are stale.
     * If so, then replace the relevant field with the new value
     * and return true. Otherwise return false.
     * returning true means the request can be retried with the same userid/password
     * returning false means we have to go back to the user to ask for a new
     * username password.
     */
    public abstract boolean isAuthorizationStale (String header);

    /**
     * Give a key for hash table lookups.
     * @param includeRealm if you want the realm considered.  Preemptively
     *          setting an authorization is done before the realm is known.
     */
    String cacheKey(boolean includeRealm) {
        // This must be kept in sync with the getXXXAuth() methods in this
        // class.
        if (includeRealm) {
            return type + ":" + authScheme + ":" + protocol + ":"
                        + host + ":" + port + ":" + realm;
        } else {
            return type + ":" + protocol + ":" + host + ":" + port;
        }
    }

    String s1, s2;  /* used for serialization of pw */

    private void readObject(ObjectInputStream s)
        throws IOException, ClassNotFoundException
    {
        s.defaultReadObject ();
        pw = new PasswordAuthentication (s1, s2.toCharArray());
        s1 = null; s2= null;
    }

    private synchronized void writeObject(java.io.ObjectOutputStream s)
        throws IOException
    {
        s1 = pw.getUserName();
        s2 = new String (pw.getPassword());
        s.defaultWriteObject ();
    }
}
