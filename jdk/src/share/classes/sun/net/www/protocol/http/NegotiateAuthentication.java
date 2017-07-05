/*
 * Copyright 2005-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.util.HashMap;

import sun.net.www.HeaderParser;
import sun.misc.BASE64Decoder;
import sun.misc.BASE64Encoder;
import sun.util.logging.PlatformLogger;

import java.net.URL;
import java.io.IOException;
import java.net.Authenticator.RequestorType;
import java.lang.reflect.Constructor;
import static sun.net.www.protocol.http.AuthScheme.NEGOTIATE;
import static sun.net.www.protocol.http.AuthScheme.KERBEROS;

/**
 * NegotiateAuthentication:
 *
 * @author weijun.wang@sun.com
 * @since 1.6
 */

class NegotiateAuthentication extends AuthenticationInfo {

    private static final long serialVersionUID = 100L;

    final private HttpCallerInfo hci;

    // These maps are used to manage the GSS availability for diffrent
    // hosts. The key for both maps is the host name.
    // <code>supported</code> is set when isSupported is checked,
    // if it's true, a cached Negotiator is put into <code>cache</code>.
    // the cache can be used only once, so after the first use, it's cleaned.
    static HashMap <String, Boolean> supported = null;
    static HashMap <String, Negotiator> cache = null;

    // The HTTP Negotiate Helper
    private Negotiator negotiator = null;

   /**
    * Constructor used for both WWW and proxy entries.
    * @param hci a schemed object.
    */
    public NegotiateAuthentication(HttpCallerInfo hci) {
        super(RequestorType.PROXY==hci.authType ? PROXY_AUTHENTICATION : SERVER_AUTHENTICATION,
              hci.scheme.equalsIgnoreCase("Negotiate") ? NEGOTIATE : KERBEROS,
              hci.url,
              "");
        this.hci = hci;
    }

    /**
     * @return true if this authentication supports preemptive authorization
     */
    boolean supportsPreemptiveAuthorization() {
        return false;
    }

    /**
     * Find out if the HttpCallerInfo supports Negotiate protocol. In order to
     * find out yes or no, an initialization of a Negotiator object against it
     * is tried. The generated object will be cached under the name of ths
     * hostname at a success try.<br>
     *
     * If this method is called for the second time on an HttpCallerInfo with
     * the same hostname, the answer is retrieved from cache.
     *
     * @return true if supported
     */
    synchronized public static boolean isSupported(HttpCallerInfo hci) {
        if (supported == null) {
            supported = new HashMap <String, Boolean>();
            cache = new HashMap <String, Negotiator>();
        }
        String hostname = hci.host;
        hostname = hostname.toLowerCase();
        if (supported.containsKey(hostname)) {
            return supported.get(hostname);
        }

        try {
            Negotiator neg = Negotiator.getSupported(hci);
            supported.put(hostname, true);
            // the only place cache.put is called. here we can make sure
            // the object is valid and the oneToken inside is not null
            cache.put(hostname, neg);
            return true;
        } catch(Exception e) {
            supported.put(hostname, false);
            return false;
        }
    }

    /**
     * @return the name of the HTTP header this authentication wants to set
     */
    String getHeaderName() {
        if (type == SERVER_AUTHENTICATION) {
            return "Authorization";
        } else {
            return "Proxy-Authorization";
        }
    }

    /**
     * Not supported. Must use the setHeaders() method
     */
    String getHeaderValue(URL url, String method) {
        throw new RuntimeException ("getHeaderValue not supported");
    }

    /**
     * Check if the header indicates that the current auth. parameters are stale.
     * If so, then replace the relevant field with the new value
     * and return true. Otherwise return false.
     * returning true means the request can be retried with the same userid/password
     * returning false means we have to go back to the user to ask for a new
     * username password.
     */
    boolean isAuthorizationStale (String header) {
        return false; /* should not be called for Negotiate */
    }

    /**
     * Set header(s) on the given connection.
     * @param conn The connection to apply the header(s) to
     * @param p A source of header values for this connection, not used because
     *          HeaderParser converts the fields to lower case, use raw instead
     * @param raw The raw header field.
     * @return true if all goes well, false if no headers were set.
     */
    synchronized boolean setHeaders(HttpURLConnection conn, HeaderParser p, String raw) {

        try {
            String response;
            byte[] incoming = null;
            String[] parts = raw.split("\\s+");
            if (parts.length > 1) {
                incoming = new BASE64Decoder().decodeBuffer(parts[1]);
            }
            response = hci.scheme + " " + new B64Encoder().encode(
                        incoming==null?firstToken():nextToken(incoming));

            conn.setAuthenticationProperty(getHeaderName(), response);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * return the first token.
     * @returns the token
     * @throws IOException if <code>Negotiator.getSupported()</code> or
     *                     <code>Negotiator.firstToken()</code> failed.
     */
    private byte[] firstToken() throws IOException {
        negotiator = null;
        if (cache != null) {
            synchronized(cache) {
                negotiator = cache.get(getHost());
                if (negotiator != null) {
                    cache.remove(getHost()); // so that it is only used once
                }
            }
        }
        if (negotiator == null) {
            try {
                negotiator = Negotiator.getSupported(hci);
            } catch(Exception e) {
                IOException ioe = new IOException("Cannot initialize Negotiator");
                ioe.initCause(e);
                throw ioe;
            }
        }

        return negotiator.firstToken();
    }

    /**
     * return more tokens
     * @param token the token to be fed into <code>negotiator.nextToken()</code>
     * @returns the token
     * @throws IOException if <code>negotiator.nextToken()</code> throws Exception.
     *  May happen if the input token is invalid.
     */
    private byte[] nextToken(byte[] token) throws IOException {
        return negotiator.nextToken(token);
    }

    class B64Encoder extends BASE64Encoder {
        protected int bytesPerLine () {
            return 100000;  // as big as it can be, maybe INT_MAX
        }
    }

    // MS will send a final WWW-Authenticate even if the status is already
    // 200 OK. The token can be fed into initSecContext() again to determine
    // if the server can be trusted. This is not the same concept as Digest's
    // Authentication-Info header.
    //
    // Currently we ignore this header.

}

/**
 * This abstract class is a bridge to connect NegotiteAuthentication and
 * NegotiatorImpl, so that JAAS and JGSS calls can be made
 */
abstract class Negotiator {
    static Negotiator getSupported(HttpCallerInfo hci)
                throws Exception {

        // These lines are equivalent to
        //     return new NegotiatorImpl(hci);
        // The current implementation will make sure NegotiatorImpl is not
        // directly referenced when compiling, thus smooth the way of building
        // the J2SE platform where HttpURLConnection is a bootstrap class.
        //
        // Makes NegotiatorImpl, and the security classes it references, a
        // runtime dependency rather than a static one.

        Class clazz;
        Constructor c;
        try {
            clazz = Class.forName("sun.net.www.protocol.http.NegotiatorImpl", true, null);
            c = clazz.getConstructor(HttpCallerInfo.class);
        } catch (ClassNotFoundException cnfe) {
            finest(cnfe);
            throw cnfe;
        } catch (ReflectiveOperationException roe) {
            // if the class is there then something seriously wrong if
            // the constructor is not.
            throw new AssertionError(roe);
        }

        try {
            return (Negotiator) (c.newInstance(hci));
        } catch (ReflectiveOperationException roe) {
            finest(roe);
            Throwable t = roe.getCause();
            if (t != null && t instanceof Exception)
                finest((Exception)t);
            throw roe;
        }
    }

    abstract byte[] firstToken() throws IOException;

    abstract byte[] nextToken(byte[] in) throws IOException;

    static void finest(Exception e) {
        PlatformLogger logger = HttpURLConnection.getHttpLogger();
        logger.finest("NegotiateAuthentication: " + e);
    }
}
