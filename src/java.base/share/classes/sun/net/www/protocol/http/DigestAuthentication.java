/*
 * Copyright (c) 1997, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.net.PasswordAuthentication;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedAction;
import java.security.Security;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.BiConsumer;

import sun.net.NetProperties;
import sun.net.www.HeaderParser;
import sun.nio.cs.ISO_8859_1;
import sun.security.util.KnownOIDs;
import sun.util.logging.PlatformLogger;

import static sun.net.www.protocol.http.HttpURLConnection.HTTP_CONNECT;

/**
 * DigestAuthentication: Encapsulate an http server authentication using
 * the "Digest" scheme, as described in RFC2069 and updated in RFC2617
 *
 * @author Bill Foote
 */

class DigestAuthentication extends AuthenticationInfo {

    @java.io.Serial
    private static final long serialVersionUID = 100L;

    private String authMethod;

    private static final String propPrefix = "http.auth.digest.";

    private static final String compatPropName = propPrefix +
        "quoteParameters";

    // Takes a set and input string containing comma separated values. converts to upper
    // case, and trims each value, then applies given function to set and value
    // (either add or delete element from set)
    private static void processPropValue(String input,
        Set<String> theSet,
        BiConsumer<Set<String>,String> consumer)
    {
        if (input == null) {
            return;
        }
        String[] values = input.toUpperCase(Locale.ROOT).split(",");
        for (String v : values) {
            consumer.accept(theSet, v.trim());
        }
    }

    private static final String secPropName =
        propPrefix + "disabledAlgorithms";

    // A net property which overrides the disabled set above.
    private static final String enabledAlgPropName =
        propPrefix + "reEnabledAlgorithms";

    // Set of disabled message digest algorithms
    private static final Set<String> disabledDigests;

    // true if http.auth.digest.quoteParameters Net property is true
    private static final boolean delimCompatFlag;

    private static final PlatformLogger logger =
        HttpURLConnection.getHttpLogger();

    static {
        @SuppressWarnings("removal")
        Boolean b = AccessController.doPrivileged(
            (PrivilegedAction<Boolean>) () -> NetProperties.getBoolean(compatPropName)
        );
        delimCompatFlag = (b == null) ? false : b.booleanValue();

        @SuppressWarnings("removal")
        String secprops = AccessController.doPrivileged(
            (PrivilegedAction<String>) () -> Security.getProperty(secPropName)
        );

        Set<String> algs = new HashSet<>();

        // add the default insecure algorithms to set
        processPropValue(secprops, algs, (set, elem) -> set.add(elem));

        @SuppressWarnings("removal")
        String netprops = AccessController.doPrivileged(
            (PrivilegedAction<String>) () -> NetProperties.get(enabledAlgPropName)
        );
        // remove any algorithms from disabled set that were opted-in by user
        processPropValue(netprops, algs, (set, elem) -> set.remove(elem));
        disabledDigests = Set.copyOf(algs);
    }

    // Authentication parameters defined in RFC2617.
    // One instance of these may be shared among several DigestAuthentication
    // instances as a result of a single authorization (for multiple domains)

    // There don't appear to be any blocking IO calls performed from
    // within the synchronized code blocks in the Parameters class, so there don't
    // seem to be any need to migrate it to using java.util.concurrent.locks
    static class Parameters implements java.io.Serializable {
        private static final long serialVersionUID = -3584543755194526252L;

        private boolean serverQop; // server proposed qop=auth
        private String opaque;
        private String cnonce;
        private String nonce;
        private String algorithm;
        // Normally same as algorithm, but excludes the -SESS suffix if present
        private String digestName;
        private String charset;
        private int NCcount=0;

        // true if the server supports user hashing
        // in which case the username returned to server
        // will be H(unq(username) ":" unq(realm))
        // meaning the username doesn't appear in the clear
        private boolean userhash;

        // The H(A1) string used for XXX-sess
        private String  cachedHA1;

        // Force the HA1 value to be recalculated because the nonce has changed
        private boolean redoCachedHA1 = true;

        private static final int cnonceRepeat = 5;

        private static final int cnoncelen = 40; /* number of characters in cnonce */

        private static Random   random;

        static {
            random = new Random();
        }

        Parameters () {
            serverQop = false;
            opaque = null;
            algorithm = null;
            digestName = null;
            cachedHA1 = null;
            nonce = null;
            charset = null;
            setNewCnonce();
        }

        boolean authQop () {
            return serverQop;
        }
        synchronized void incrementNC() {
            NCcount ++;
        }
        synchronized int getNCCount () {
            return NCcount;
        }

        int cnonce_count = 0;

        /* each call increments the counter */
        synchronized String getCnonce () {
            if (cnonce_count >= cnonceRepeat) {
                setNewCnonce();
            }
            cnonce_count++;
            return cnonce;
        }
        synchronized void setNewCnonce () {
            byte bb[] = new byte [cnoncelen/2];
            char cc[] = new char [cnoncelen];
            random.nextBytes (bb);
            for (int  i=0; i<(cnoncelen/2); i++) {
                int x = bb[i] + 128;
                cc[i*2]= (char) ('A'+ x/16);
                cc[i*2+1]= (char) ('A'+ x%16);
            }
            cnonce = new String (cc, 0, cnoncelen);
            cnonce_count = 0;
            redoCachedHA1 = true;
        }

        synchronized boolean getUserhash() {
            return userhash;
        }

        synchronized void setUserhash(boolean userhash) {
            this.userhash = userhash;
        }

        synchronized Charset getCharset() {
            return "UTF-8".equals(charset)
                ? StandardCharsets.UTF_8
                : StandardCharsets.ISO_8859_1;
        }

        synchronized void setCharset(String charset) {
            this.charset = charset;
        }

        synchronized void setQop (String qop) {
            if (qop != null) {
                String items[] = qop.split(",");
                for (String item : items) {
                    if ("auth".equalsIgnoreCase(item.trim())) {
                        serverQop = true;
                        return;
                    }
                }
            }
            serverQop = false;
        }

        synchronized String getOpaque () { return opaque;}
        synchronized void setOpaque (String s) { opaque=s;}

        synchronized String getNonce () { return nonce;}

        synchronized void setNonce (String s) {
            if (nonce == null || !s.equals(nonce)) {
                nonce=s;
                NCcount = 0;
                redoCachedHA1 = true;
            }
        }

        synchronized String getCachedHA1 () {
            if (redoCachedHA1) {
                return null;
            } else {
                return cachedHA1;
            }
        }

        synchronized void setCachedHA1 (String s) {
            cachedHA1=s;
            redoCachedHA1=false;
        }

        synchronized String getAlgorithm () { return algorithm;}
        synchronized String getDigestName () {
            return digestName;
        }
        synchronized void setAlgorithm (String s) { algorithm=s;}
        synchronized void setDigestName (String s) {
            this.digestName = s;
        }
    }

    Parameters params;

    /**
     * Create a DigestAuthentication
     */
    public DigestAuthentication(boolean isProxy, URL url, String realm,
                                String authMethod, PasswordAuthentication pw,
                                Parameters params, String authenticatorKey) {
        super(isProxy ? PROXY_AUTHENTICATION : SERVER_AUTHENTICATION,
              AuthScheme.DIGEST,
              url,
              realm,
              Objects.requireNonNull(authenticatorKey));
        this.authMethod = authMethod;
        this.pw = pw;
        this.params = params;
    }

    public DigestAuthentication(boolean isProxy, String host, int port, String realm,
                                String authMethod, PasswordAuthentication pw,
                                Parameters params, String authenticatorKey) {
        super(isProxy ? PROXY_AUTHENTICATION : SERVER_AUTHENTICATION,
              AuthScheme.DIGEST,
              host,
              port,
              realm,
              Objects.requireNonNull(authenticatorKey));
        this.authMethod = authMethod;
        this.pw = pw;
        this.params = params;
    }

    /**
     * @return true if this authentication supports preemptive authorization
     */
    @Override
    public boolean supportsPreemptiveAuthorization() {
        return true;
    }

    /**
     * Recalculates the request-digest and returns it.
     *
     * <P> Used in the common case where the requestURI is simply the
     * abs_path.
     *
     * @param  url
     *         the URL
     *
     * @param  method
     *         the HTTP method
     *
     * @return the value of the HTTP header this authentication wants set
     */
    @Override
    public String getHeaderValue(URL url, String method) {
        return getHeaderValueImpl(url.getFile(), method);
    }

    /**
     * Recalculates the request-digest and returns it.
     *
     * <P> Used when the requestURI is not the abs_path. The exact
     * requestURI can be passed as a String.
     *
     * @param  requestURI
     *         the Request-URI from the HTTP request line
     *
     * @param  method
     *         the HTTP method
     *
     * @return the value of the HTTP header this authentication wants set
     */
    String getHeaderValue(String requestURI, String method) {
        return getHeaderValueImpl(requestURI, method);
    }

    /**
     * Check if the header indicates that the current auth. parameters are stale.
     * If so, then replace the relevant field with the new value
     * and return true. Otherwise return false.
     * returning true means the request can be retried with the same userid/password
     * returning false means we have to go back to the user to ask for a new
     * username password.
     */
    @Override
    public boolean isAuthorizationStale (String header) {
        HeaderParser p = new HeaderParser (header);
        String s = p.findValue ("stale");
        if (s == null || !s.equals("true"))
            return false;
        String newNonce = p.findValue ("nonce");
        if (newNonce == null || newNonce.isEmpty()) {
            return false;
        }
        params.setNonce (newNonce);
        return true;
    }

    /**
     * Set header(s) on the given connection.
     * @param conn The connection to apply the header(s) to
     * @param p A source of header values for this connection, if needed.
     * @param raw Raw header values for this connection, if needed.
     * @return true if all goes well, false if no headers were set.
     */
    @Override
    public boolean setHeaders(HttpURLConnection conn, HeaderParser p, String raw) {
        // no need to synchronize here:
        //   already locked by s.n.w.p.h.HttpURLConnection
        assert conn.isLockHeldByCurrentThread();

        params.setNonce (p.findValue("nonce"));
        params.setOpaque (p.findValue("opaque"));
        params.setQop (p.findValue("qop"));
        params.setUserhash (Boolean.valueOf(p.findValue("userhash")));
        String charset = p.findValue("charset");
        if (charset == null) {
            charset = "ISO_8859_1";
        } else if (!charset.equalsIgnoreCase("UTF-8")) {
            // UTF-8 is only valid value. ISO_8859_1 represents default behavior
            // when the parameter is not set.
            return false;
        }
        params.setCharset(charset.toUpperCase(Locale.ROOT));

        String uri="";
        String method;
        if (type == PROXY_AUTHENTICATION &&
                conn.tunnelState() == HttpURLConnection.TunnelState.SETUP) {
            uri = HttpURLConnection.connectRequestURI(conn.getURL());
            method = HTTP_CONNECT;
        } else {
            try {
                uri = conn.getRequestURI();
            } catch (IOException e) {}
            method = conn.getMethod();
        }

        if (params.nonce == null || authMethod == null || pw == null || realm == null) {
            return false;
        }
        if (authMethod.length() >= 1) {
            // Method seems to get converted to all lower case elsewhere.
            // It really does need to start with an upper case letter
            // here.
            authMethod = Character.toUpperCase(authMethod.charAt(0))
                        + authMethod.substring(1).toLowerCase();
        }

        if (!setAlgorithmNames(p, params))
            return false;

        // If authQop is true, then the server is doing RFC2617 and
        // has offered qop=auth. We do not support any other modes
        // and if auth is not offered we fallback to the RFC2069 behavior

        if (params.authQop()) {
            params.setNewCnonce();
        }

        String value = getHeaderValueImpl (uri, method);
        if (value != null) {
            conn.setAuthenticationProperty(getHeaderName(), value);
            return true;
        } else {
            return false;
        }
    }

    // Algorithm name is stored in two separate fields (of Paramaeters)
    // This allows for variations in digest algorithm name (aliases)
    // and also allow for the -sess variant defined in HTTP Digest protocol
    // returns false if algorithm not supported
    private static boolean setAlgorithmNames(HeaderParser p, Parameters params) {
        String algorithm = p.findValue("algorithm");
        String digestName = algorithm;
        if (algorithm == null || algorithm.isEmpty()) {
            algorithm = "MD5";  // The default, accoriding to rfc2069
            digestName = "MD5";
        } else {
            algorithm = algorithm.toUpperCase(Locale.ROOT);
            digestName = algorithm;
        }
        if (algorithm.endsWith("-SESS")) {
            digestName = algorithm.substring(0, algorithm.length() - 5);
            algorithm = digestName + "-sess"; // suffix lower case
        }
        if (digestName.equals("SHA-512-256")) {
            digestName = "SHA-512/256";
        }
        var oid = KnownOIDs.findMatch(digestName);
        if (oid == null) {
            log("unknown algorithm: " + algorithm);
            return false;
        }
        digestName = oid.stdName();
        params.setAlgorithm (algorithm);
        params.setDigestName (digestName);
        return true;
    }

    /* Calculate the Authorization header field given the request URI
     * and based on the authorization information in params
     */
    private String getHeaderValueImpl (String uri, String method) {
        String response;
        char[] passwd = pw.getPassword();
        boolean qop = params.authQop();
        String opaque = params.getOpaque();
        String cnonce = params.getCnonce ();
        String nonce = params.getNonce ();
        String algorithm = params.getAlgorithm ();
        String digest = params.getDigestName ();
        try {
            validateDigest(digest);
        } catch (IOException e) {
            return null;
        }
        Charset charset = params.getCharset();
        boolean userhash = params.getUserhash ();
        params.incrementNC ();
        int  nccount = params.getNCCount ();
        String ncstring=null;

        if (nccount != -1) {
            ncstring = Integer.toHexString (nccount).toLowerCase();
            int len = ncstring.length();
            if (len < 8)
                ncstring = zeroPad [len] + ncstring;
        }

        boolean session = algorithm.endsWith ("-sess");

        try {
            response = computeDigest(true, pw.getUserName(),passwd,realm,
                                        method, uri, nonce, cnonce, ncstring,
                                        digest, session, charset);
        } catch (CharacterCodingException | NoSuchAlgorithmException ex) {
            log(ex.getMessage());
            return null;
        }

        String ncfield = "\"";
        if (qop) {
            ncfield = "\", nc=" + ncstring;
        }

        String algoS, qopS;

        if (delimCompatFlag) {
            // Put quotes around these String value parameters
            algoS = ", algorithm=\"" + algorithm + "\"";
            qopS = ", qop=\"auth\"";
        } else {
            // Don't put quotes around them, per the RFC
            algoS = ", algorithm=" + algorithm;
            qopS = ", qop=auth";
        }

        String user = pw.getUserName();
        String userhashField = "";
        try {
            if (userhash) {
                user = computeUserhash(digest, user, realm, charset);
                userhashField = ", userhash=true";
            }
        } catch (CharacterCodingException | NoSuchAlgorithmException ex) {
            log(ex.getMessage());
            return null;
        }

        String value = authMethod
                        + " username=\"" + user
                        + "\", realm=\"" + realm
                        + "\", nonce=\"" + nonce
                        + ncfield
                        + userhashField
                        + ", uri=\"" + uri
                        + "\", response=\"" + response + "\""
                        + algoS;
        if (opaque != null) {
            value += ", opaque=\"" + opaque + "\"";
        }
        if (cnonce != null) {
            value += ", cnonce=\"" + cnonce + "\"";
        }
        if (qop) {
            value += qopS;
        }
        return value;
    }

    public void checkResponse (String header, String method, URL url)
                                                        throws IOException {
        checkResponse (header, method, url.getFile());
    }

    private static void log(String msg) {
        if (logger.isLoggable(PlatformLogger.Level.INFO)) {
            logger.info(msg);
        }
    }

    private void validateDigest(String name) throws IOException {
        if (getAuthType() == AuthCacheValue.Type.Server &&
                getProtocolScheme().equals("https")) {
            // HTTPS server authentication can use any algorithm
            return;
        }
        if (disabledDigests.contains(name)) {
            String msg = "Rejecting digest authentication with insecure algorithm: "
                + name;
            log(msg + " This constraint may be relaxed by setting " +
                     "the \"http.auth.digest.reEnabledAlgorithms\" system property.");
            throw new IOException(msg);
        }
    }

    public void checkResponse (String header, String method, String uri)
                                                        throws IOException {
        char[] passwd = pw.getPassword();
        String username = pw.getUserName();
        boolean qop = params.authQop();
        String opaque = params.getOpaque();
        String cnonce = params.cnonce;
        String nonce = params.getNonce ();
        String algorithm = params.getAlgorithm ();
        String digest = params.getDigestName ();
        Charset charset = params.getCharset();
        validateDigest(digest);
        int  nccount = params.getNCCount ();
        String ncstring=null;

        if (header == null) {
            throw new ProtocolException ("No authentication information in response");
        }

        boolean session = algorithm.endsWith ("-SESS");
        if (session) {
            algorithm = algorithm.substring(0, algorithm.length() - 5);
        }

        if (nccount != -1) {
            ncstring = Integer.toHexString (nccount).toUpperCase(Locale.ROOT);
            int len = ncstring.length();
            if (len < 8)
                ncstring = zeroPad [len] + ncstring;
        }
        try {
            String expected = computeDigest(false, username,passwd,realm, method, uri,
                                           nonce, cnonce, ncstring, digest,
                                           session, charset);
            HeaderParser p = new HeaderParser (header);
            String rspauth = p.findValue ("rspauth");
            if (rspauth == null) {
                throw new ProtocolException ("No digest in response");
            }
            if (!rspauth.equals (expected)) {
                throw new ProtocolException ("Response digest invalid");
            }
            /* Check if there is a nextnonce field */
            String nextnonce = p.findValue ("nextnonce");
            if (nextnonce != null && !nextnonce.isEmpty()) {
                params.setNonce (nextnonce);
            }

        } catch (NoSuchAlgorithmException ex) {
            throw new ProtocolException ("Unsupported algorithm in response");
        } catch (CharacterCodingException ex) {
            throw new ProtocolException ("Invalid characters in username or password");
        }
    }

    private String computeUserhash(String digest, String user,
                                   String realm, Charset charset)
        throws NoSuchAlgorithmException, CharacterCodingException
    {
        MessageDigest md = MessageDigest.getInstance(digest);
        String s = user + ":" + realm;
        return encode(s, null, md, charset);
    }

    private String computeDigest(
                        boolean isRequest, String userName, char[] password,
                        String realm, String connMethod,
                        String requestURI, String nonceString,
                        String cnonce, String ncValue,
                        String algorithm, boolean session,
                        Charset charset
                    ) throws NoSuchAlgorithmException, CharacterCodingException
    {

        String A1, HashA1;

        MessageDigest md = MessageDigest.getInstance(algorithm);

        if (session) {
            if ((HashA1 = params.getCachedHA1 ()) == null) {
                String s = userName + ":" + realm + ":";
                String s1 = encode (s, password, md, charset);
                A1 = s1 + ":" + nonceString + ":" + cnonce;
                HashA1 = encode(A1, null, md, charset);
                params.setCachedHA1 (HashA1);
            }
        } else {
            A1 = userName + ":" + realm + ":";
            HashA1 = encode(A1, password, md, charset);
        }

        String A2;
        if (isRequest) {
            A2 = connMethod + ":" + requestURI;
        } else {
            A2 = ":" + requestURI;
        }
        String HashA2 = encode(A2, null, md, ISO_8859_1.INSTANCE);
        String combo, finalHash;

        if (params.authQop()) { /* RRC2617 when qop=auth */
            combo = HashA1+ ":" + nonceString + ":" + ncValue + ":" +
                        cnonce + ":auth:" +HashA2;

        } else { /* for compatibility with RFC2069 */
            combo = HashA1 + ":" +
                       nonceString + ":" +
                       HashA2;
        }
        finalHash = encode(combo, null, md, ISO_8859_1.INSTANCE);
        return finalHash;
    }

    private static final char charArray[] = {
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    };

    private static final String zeroPad[] = {
        // 0         1          2         3        4       5      6     7
        "00000000", "0000000", "000000", "00000", "0000", "000", "00", "0"
    };

    private String encode(String src, char[] passwd, MessageDigest md, Charset charset)
        throws CharacterCodingException
    {
        boolean isUtf8 = charset.equals(StandardCharsets.UTF_8);

        if (isUtf8) {
            src = Normalizer.normalize(src, Normalizer.Form.NFC);
        }
        md.update(src.getBytes(charset));
        if (passwd != null) {
            byte[] passwdBytes;
            if (isUtf8) {
                passwdBytes = getUtf8Bytes(passwd);
            } else {
                passwdBytes = new byte[passwd.length];
                for (int i=0; i<passwd.length; i++)
                    passwdBytes[i] = (byte)passwd[i];
            }
            md.update(passwdBytes);
            Arrays.fill(passwdBytes, (byte)0x00);
        }
        byte[] digest = md.digest();
        StringBuilder res = new StringBuilder(digest.length * 2);
        for (int i = 0; i < digest.length; i++) {
            int hashchar = ((digest[i] >>> 4) & 0xf);
            res.append(charArray[hashchar]);
            hashchar = (digest[i] & 0xf);
            res.append(charArray[hashchar]);
        }
        return res.toString();
    }

    private static byte[] getUtf8Bytes(char[] passwd) throws CharacterCodingException {
        CharBuffer cb = CharBuffer.wrap(passwd);
        CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder();
        ByteBuffer bb = encoder.encode(cb);
        byte[] buf = new byte[bb.remaining()];
        bb.get(buf);
        if (bb.hasArray())
            Arrays.fill(bb.array(), bb.arrayOffset(), bb.capacity(), (byte)0);
        return buf;
    }
}
