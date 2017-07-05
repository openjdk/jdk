/*
 * Copyright (c) 2005, 2008, Oracle and/or its affiliates. All rights reserved.
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

package sun.net.www.protocol.http.ntlm;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.PasswordAuthentication;
import java.net.UnknownHostException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;

import sun.net.www.HeaderParser;
import sun.net.www.protocol.http.AuthenticationInfo;
import sun.net.www.protocol.http.AuthScheme;
import sun.net.www.protocol.http.HttpURLConnection;

/**
 * NTLMAuthentication:
 *
 * @author Michael McMahon
 */

/*
 * NTLM authentication is nominally based on the framework defined in RFC2617,
 * but differs from the standard (Basic & Digest) schemes as follows:
 *
 * 1. A complete authentication requires three request/response transactions
 *    as shown below:
 *            REQ ------------------------------->
 *            <---- 401 (signalling NTLM) --------
 *
 *            REQ (with type1 NTLM msg) --------->
 *            <---- 401 (with type 2 NTLM msg) ---
 *
 *            REQ (with type3 NTLM msg) --------->
 *            <---- OK ---------------------------
 *
 * 2. The scope of the authentication is the TCP connection (which must be kept-alive)
 *    after the type2 response is received. This means that NTLM does not work end-to-end
 *    through a proxy, rather between client and proxy, or between client and server (with no proxy)
 */

public class NTLMAuthentication extends AuthenticationInfo {
    private static final long serialVersionUID = -2403849171106437142L;

    private byte[] type1;
    private byte[] type3;

    private SecretKeyFactory fac;
    private Cipher cipher;
    private MessageDigest md4;
    private String hostname;
    private static String defaultDomain; /* Domain to use if not specified by user */

    static {
        defaultDomain = java.security.AccessController.doPrivileged(
            new sun.security.action.GetPropertyAction("http.auth.ntlm.domain",
                                                      "domain"));
    };

    public static boolean supportsTransparentAuth () {
        return false;
    }

    private void init0() {
        type1 = new byte[256];
        type3 = new byte[256];
        System.arraycopy (new byte[] {'N','T','L','M','S','S','P',0,1}, 0, type1, 0, 9);
        type1[12] = (byte) 3;
        type1[13] = (byte) 0xb2;
        type1[28] = (byte) 0x20;
        System.arraycopy (new byte[] {'N','T','L','M','S','S','P',0,3}, 0, type3, 0, 9);
        type3[12] = (byte) 0x18;
        type3[14] = (byte) 0x18;
        type3[20] = (byte) 0x18;
        type3[22] = (byte) 0x18;
        type3[32] = (byte) 0x40;
        type3[60] = (byte) 1;
        type3[61] = (byte) 0x82;

        try {
            hostname = java.security.AccessController.doPrivileged(
                new java.security.PrivilegedAction<String>() {
                public String run() {
                    String localhost;
                    try {
                        localhost = InetAddress.getLocalHost().getHostName().toUpperCase();
                    } catch (UnknownHostException e) {
                         localhost = "localhost";
                    }
                    return localhost;
                }
            });
            int x = hostname.indexOf ('.');
            if (x != -1) {
                hostname = hostname.substring (0, x);
            }
            fac = SecretKeyFactory.getInstance ("DES");
            cipher = Cipher.getInstance ("DES/ECB/NoPadding");
            md4 = sun.security.provider.MD4.getInstance();
        } catch (NoSuchPaddingException e) {
            assert false;
        } catch (NoSuchAlgorithmException e) {
            assert false;
        }
    };

    PasswordAuthentication pw;
    String username;
    String ntdomain;
    String password;

    /**
     * Create a NTLMAuthentication:
     * Username may be specified as domain<BACKSLASH>username in the application Authenticator.
     * If this notation is not used, then the domain will be taken
     * from a system property: "http.auth.ntlm.domain".
     */
    public NTLMAuthentication(boolean isProxy, URL url, PasswordAuthentication pw) {
        super(isProxy ? PROXY_AUTHENTICATION : SERVER_AUTHENTICATION,
                AuthScheme.NTLM,
                url,
                "");
        init (pw);
    }

    private void init (PasswordAuthentication pw) {
        this.pw = pw;
        String s = pw.getUserName();
        int i = s.indexOf ('\\');
        if (i == -1) {
            username = s;
            ntdomain = defaultDomain;
        } else {
            ntdomain = s.substring (0, i).toUpperCase();
            username = s.substring (i+1);
        }
        password = new String (pw.getPassword());
        init0();
    }

   /**
    * Constructor used for proxy entries
    */
    public NTLMAuthentication(boolean isProxy, String host, int port,
                                PasswordAuthentication pw) {
        super(isProxy ? PROXY_AUTHENTICATION : SERVER_AUTHENTICATION,
                AuthScheme.NTLM,
                host,
                port,
                "");
        init (pw);
    }

    /**
     * @return true if this authentication supports preemptive authorization
     */
    @Override
    public boolean supportsPreemptiveAuthorization() {
        return false;
    }

    /**
     * Not supported. Must use the setHeaders() method
     */
    @Override
    public String getHeaderValue(URL url, String method) {
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
    @Override
    public boolean isAuthorizationStale (String header) {
        return false; /* should not be called for ntlm */
    }

    /**
     * Set header(s) on the given connection.
     * @param conn The connection to apply the header(s) to
     * @param p A source of header values for this connection, not used because
     *          HeaderParser converts the fields to lower case, use raw instead
     * @param raw The raw header field.
     * @return true if all goes well, false if no headers were set.
     */
    @Override
    public synchronized boolean setHeaders(HttpURLConnection conn, HeaderParser p, String raw) {

        try {
            String response;
            if (raw.length() < 6) { /* NTLM<sp> */
                response = buildType1Msg ();
            } else {
                String msg = raw.substring (5); /* skip NTLM<sp> */
                response = buildType3Msg (msg);
            }
            conn.setAuthenticationProperty(getHeaderName(), response);
            return true;
        } catch (IOException e) {
            return false;
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    private void copybytes (byte[] dest, int destpos, String src, String enc) {
        try {
            byte[] x = src.getBytes(enc);
            System.arraycopy (x, 0, dest, destpos, x.length);
        } catch (UnsupportedEncodingException e) {
            assert false;
        }
    }

    private String buildType1Msg () {
        int dlen = ntdomain.length();
        type1[16]= (byte) (dlen % 256);
        type1[17]= (byte) (dlen / 256);
        type1[18] = type1[16];
        type1[19] = type1[17];

        int hlen = hostname.length();
        type1[24]= (byte) (hlen % 256);
        type1[25]= (byte) (hlen / 256);
        type1[26] = type1[24];
        type1[27] = type1[25];

        copybytes (type1, 32, hostname, "ISO8859_1");
        copybytes (type1, hlen+32, ntdomain, "ISO8859_1");
        type1[20] = (byte) ((hlen+32) % 256);
        type1[21] = (byte) ((hlen+32) / 256);

        byte[] msg = new byte [32 + hlen + dlen];
        System.arraycopy (type1, 0, msg, 0, 32 + hlen + dlen);
        String result = "NTLM " + (new B64Encoder()).encode (msg);
        return result;
    }


    /* Convert a 7 byte array to an 8 byte array (for a des key with parity)
     * input starts at offset off
     */
    private byte[] makeDesKey (byte[] input, int off) {
        int[] in = new int [input.length];
        for (int i=0; i<in.length; i++ ) {
            in[i] = input[i]<0 ? input[i]+256: input[i];
        }
        byte[] out = new byte[8];
        out[0] = (byte)in[off+0];
        out[1] = (byte)(((in[off+0] << 7) & 0xFF) | (in[off+1] >> 1));
        out[2] = (byte)(((in[off+1] << 6) & 0xFF) | (in[off+2] >> 2));
        out[3] = (byte)(((in[off+2] << 5) & 0xFF) | (in[off+3] >> 3));
        out[4] = (byte)(((in[off+3] << 4) & 0xFF) | (in[off+4] >> 4));
        out[5] = (byte)(((in[off+4] << 3) & 0xFF) | (in[off+5] >> 5));
        out[6] = (byte)(((in[off+5] << 2) & 0xFF) | (in[off+6] >> 6));
        out[7] = (byte)((in[off+6] << 1) & 0xFF);
        return out;
    }

    private byte[] calcLMHash () throws GeneralSecurityException {
        byte[] magic = {0x4b, 0x47, 0x53, 0x21, 0x40, 0x23, 0x24, 0x25};
        byte[] pwb = password.toUpperCase ().getBytes();
        byte[] pwb1 = new byte [14];
        int len = password.length();
        if (len > 14)
            len = 14;
        System.arraycopy (pwb, 0, pwb1, 0, len); /* Zero padded */

        DESKeySpec dks1 = new DESKeySpec (makeDesKey (pwb1, 0));
        DESKeySpec dks2 = new DESKeySpec (makeDesKey (pwb1, 7));

        SecretKey key1 = fac.generateSecret (dks1);
        SecretKey key2 = fac.generateSecret (dks2);
        cipher.init (Cipher.ENCRYPT_MODE, key1);
        byte[] out1 = cipher.doFinal (magic, 0, 8);
        cipher.init (Cipher.ENCRYPT_MODE, key2);
        byte[] out2 = cipher.doFinal (magic, 0, 8);

        byte[] result = new byte [21];
        System.arraycopy (out1, 0, result, 0, 8);
        System.arraycopy (out2, 0, result, 8, 8);
        return result;
    }

    private byte[] calcNTHash () throws GeneralSecurityException {
        byte[] pw = null;
        try {
            pw = password.getBytes ("UnicodeLittleUnmarked");
        } catch (UnsupportedEncodingException e) {
            assert false;
        }
        byte[] out = md4.digest (pw);
        byte[] result = new byte [21];
        System.arraycopy (out, 0, result, 0, 16);
        return result;
    }

    /* key is a 21 byte array. Split it into 3 7 byte chunks,
     * Convert each to 8 byte DES keys, encrypt the text arg with
     * each key and return the three results in a sequential []
     */
    private byte[] calcResponse (byte[] key, byte[] text)
    throws GeneralSecurityException {
        assert key.length == 21;
        DESKeySpec dks1 = new DESKeySpec (makeDesKey (key, 0));
        DESKeySpec dks2 = new DESKeySpec (makeDesKey (key, 7));
        DESKeySpec dks3 = new DESKeySpec (makeDesKey (key, 14));
        SecretKey key1 = fac.generateSecret (dks1);
        SecretKey key2 = fac.generateSecret (dks2);
        SecretKey key3 = fac.generateSecret (dks3);
        cipher.init (Cipher.ENCRYPT_MODE, key1);
        byte[] out1 = cipher.doFinal (text, 0, 8);
        cipher.init (Cipher.ENCRYPT_MODE, key2);
        byte[] out2 = cipher.doFinal (text, 0, 8);
        cipher.init (Cipher.ENCRYPT_MODE, key3);
        byte[] out3 = cipher.doFinal (text, 0, 8);
        byte[] result = new byte [24];
        System.arraycopy (out1, 0, result, 0, 8);
        System.arraycopy (out2, 0, result, 8, 8);
        System.arraycopy (out3, 0, result, 16, 8);
        return result;
    }

    private String buildType3Msg (String challenge) throws GeneralSecurityException,
                                                           IOException  {
        /* First decode the type2 message to get the server nonce */
        /* nonce is located at type2[24] for 8 bytes */

        byte[] type2 = (new sun.misc.BASE64Decoder()).decodeBuffer (challenge);
        byte[] nonce = new byte [8];
        System.arraycopy (type2, 24, nonce, 0, 8);

        int ulen = username.length()*2;
        type3[36] = type3[38] = (byte) (ulen % 256);
        type3[37] = type3[39] = (byte) (ulen / 256);
        int dlen = ntdomain.length()*2;
        type3[28] = type3[30] = (byte) (dlen % 256);
        type3[29] = type3[31] = (byte) (dlen / 256);
        int hlen = hostname.length()*2;
        type3[44] = type3[46] = (byte) (hlen % 256);
        type3[45] = type3[47] = (byte) (hlen / 256);

        int l = 64;
        copybytes (type3, l, ntdomain, "UnicodeLittleUnmarked");
        type3[32] = (byte) (l % 256);
        type3[33] = (byte) (l / 256);
        l += dlen;
        copybytes (type3, l, username, "UnicodeLittleUnmarked");
        type3[40] = (byte) (l % 256);
        type3[41] = (byte) (l / 256);
        l += ulen;
        copybytes (type3, l, hostname, "UnicodeLittleUnmarked");
        type3[48] = (byte) (l % 256);
        type3[49] = (byte) (l / 256);
        l += hlen;

        byte[] lmhash = calcLMHash();
        byte[] lmresponse = calcResponse (lmhash, nonce);
        byte[] nthash = calcNTHash();
        byte[] ntresponse = calcResponse (nthash, nonce);
        System.arraycopy (lmresponse, 0, type3, l, 24);
        type3[16] = (byte) (l % 256);
        type3[17] = (byte) (l / 256);
        l += 24;
        System.arraycopy (ntresponse, 0, type3, l, 24);
        type3[24] = (byte) (l % 256);
        type3[25] = (byte) (l / 256);
        l += 24;
        type3[56] = (byte) (l % 256);
        type3[57] = (byte) (l / 256);

        byte[] msg = new byte [l];
        System.arraycopy (type3, 0, msg, 0, l);
        String result = "NTLM " + (new B64Encoder()).encode (msg);
        return result;
    }

}


class B64Encoder extends sun.misc.BASE64Encoder {
    /* to force it to to the entire encoding in one line */
    protected int bytesPerLine () {
        return 1024;
    }
}
