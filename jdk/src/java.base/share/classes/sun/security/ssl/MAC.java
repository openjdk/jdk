/*
 * Copyright (c) 1996, 2015, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.ssl;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import java.nio.ByteBuffer;

import javax.crypto.Mac;
import javax.crypto.SecretKey;

import sun.security.ssl.CipherSuite.MacAlg;
import static sun.security.ssl.CipherSuite.*;
import static sun.security.ssl.CipherSuite.MacAlg.*;

/**
 * This class computes the "Message Authentication Code" (MAC) for each
 * SSL stream and block cipher message.  This is essentially a shared-secret
 * signature, used to provide integrity protection for SSL messages.  The
 * MAC is actually one of several keyed hashes, as associated with the cipher
 * suite and protocol version. (SSL v3.0 uses one construct, TLS uses another.)
 *
 * @author David Brownell
 * @author Andreas Sterbenz
 */
final class MAC extends Authenticator {

    static final MAC TLS_NULL = new MAC(false);

    // Value of the null MAC is fixed
    private static final byte[] nullMAC = new byte[0];

    // internal identifier for the MAC algorithm
    private final MacAlg macAlg;

    // JCE Mac object
    private final Mac mac;

    MAC(boolean isDTLS) {
        super(isDTLS);

        macAlg = M_NULL;
        mac = null;
    }

    /**
     * Set up, configured for the given MAC type and version.
     */
    MAC(MacAlg macAlg, ProtocolVersion protocolVersion, SecretKey key)
            throws NoSuchAlgorithmException, InvalidKeyException {
        super(protocolVersion);
        this.macAlg = macAlg;

        String algorithm;

        // using SSL MAC computation?
        boolean useSSLMac = (protocolVersion.v < ProtocolVersion.TLS10.v);

        if (macAlg == M_MD5) {
            algorithm = useSSLMac ? "SslMacMD5" : "HmacMD5";
        } else if (macAlg == M_SHA) {
            algorithm = useSSLMac ? "SslMacSHA1" : "HmacSHA1";
        } else if (macAlg == M_SHA256) {
            algorithm = "HmacSHA256";    // TLS 1.2+
        } else if (macAlg == M_SHA384) {
            algorithm = "HmacSHA384";    // TLS 1.2+
        } else {
            throw new RuntimeException("Unknown Mac " + macAlg);
        }

        mac = JsseJce.getMac(algorithm);
        mac.init(key);
    }

    /**
     * Returns the length of the MAC.
     */
    int MAClen() {
        return macAlg.size;
    }

    /**
     * Returns the hash function block length of the MAC alorithm.
     */
    int hashBlockLen() {
        return macAlg.hashBlockSize;
    }

    /**
     * Returns the hash function minimal padding length of the MAC alorithm.
     */
    int minimalPaddingLen() {
        return macAlg.minimalPaddingSize;
    }

    /**
     * Computes and returns the MAC for the data in this byte array.
     *
     * @param type record type
     * @param buf compressed record on which the MAC is computed
     * @param offset start of compressed record data
     * @param len the size of the compressed record
     * @param isSimulated if true, simulate the MAC computation
     *
     * @return the MAC result
     */
    final byte[] compute(byte type, byte buf[],
            int offset, int len, boolean isSimulated) {
        if (macAlg.size == 0) {
            return nullMAC;
        }

        if (!isSimulated) {
            // Uses the implicit sequence number for the computation.
            byte[] additional = acquireAuthenticationBytes(type, len, null);
            mac.update(additional);
        }
        mac.update(buf, offset, len);

        return mac.doFinal();
    }

    /**
     * Compute and returns the MAC for the remaining data
     * in this ByteBuffer.
     *
     * On return, the bb position == limit, and limit will
     * have not changed.
     *
     * @param type record type
     * @param bb a ByteBuffer in which the position and limit
     *          demarcate the data to be MAC'd.
     * @param isSimulated if true, simulate the MAC computation
     * @param sequence the explicit sequence number, or null if using
     *        the implicit sequence number for the computation
     *
     * @return the MAC result
     */
    final byte[] compute(byte type, ByteBuffer bb,
            byte[] sequence, boolean isSimulated) {

        if (macAlg.size == 0) {
            return nullMAC;
        }

        if (!isSimulated) {
            // Uses the explicit sequence number for the computation.
            byte[] additional =
                    acquireAuthenticationBytes(type, bb.remaining(), sequence);
            mac.update(additional);
        }
        mac.update(bb);

        return mac.doFinal();
    }

    /**
     * Compute and returns the MAC for the remaining data
     * in this ByteBuffer.
     *
     * On return, the bb position == limit, and limit will
     * have not changed.
     *
     * @param type record type
     * @param bb a ByteBuffer in which the position and limit
     *        demarcate the data to be MAC'd.
     * @param isSimulated if true, simulate the the MAC computation
     *
     * @return the MAC result
     */
    final byte[] compute(byte type, ByteBuffer bb, boolean isSimulated) {
        // Uses the implicit sequence number for the computation.
        return compute(type, bb, null, isSimulated);
    }
}
