/*
 * Copyright (c) 1996, 2007, Oracle and/or its affiliates. All rights reserved.
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

/**
 * This class computes the "Message Authentication Code" (MAC) for each
 * SSL message.  This is essentially a shared-secret signature, used to
 * provide integrity protection for SSL messages.  The MAC is actually
 * one of several keyed hashes, as associated with the cipher suite and
 * protocol version.  (SSL v3.0 uses one construct, TLS uses another.)
 *
 * <P>NOTE: MAC computation is the only place in the SSL protocol that the
 * sequence number is used.  It's also reset to zero with each change of
 * a cipher spec, so this is the only place this state is needed.
 *
 * @author David Brownell
 * @author Andreas Sterbenz
 */
final class MAC {

    final static MAC NULL = new MAC();

    // Value of the null MAC is fixed
    private static final byte nullMAC[] = new byte[0];

    // internal identifier for the MAC algorithm
    private final MacAlg        macAlg;

    // stuff defined by the kind of MAC algorithm
    private final int           macSize;

    // JCE Mac object
    private final Mac mac;

    // byte array containing the additional information we MAC in each record
    // (see below)
    private final byte[] block;

    // sequence number + record type + + record length
    private static final int BLOCK_SIZE_SSL = 8 + 1 + 2;

    // sequence number + record type + protocol version + record length
    private static final int BLOCK_SIZE_TLS = 8 + 1 + 2 + 2;

    // offset of record type in block
    private static final int BLOCK_OFFSET_TYPE    = 8;

    // offset of protocol version number in block (TLS only)
    private static final int BLOCK_OFFSET_VERSION = 8 + 1;

    private MAC() {
        macSize = 0;
        macAlg = M_NULL;
        mac = null;
        block = null;
    }

    /**
     * Set up, configured for the given SSL/TLS MAC type and version.
     */
    MAC(MacAlg macAlg, ProtocolVersion protocolVersion, SecretKey key)
            throws NoSuchAlgorithmException, InvalidKeyException {
        this.macAlg = macAlg;
        this.macSize = macAlg.size;

        String algorithm;
        boolean tls = (protocolVersion.v >= ProtocolVersion.TLS10.v);

        if (macAlg == M_MD5) {
            algorithm = tls ? "HmacMD5" : "SslMacMD5";
        } else if (macAlg == M_SHA) {
            algorithm = tls ? "HmacSHA1" : "SslMacSHA1";
        } else {
            throw new RuntimeException("Unknown Mac " + macAlg);
        }

        mac = JsseJce.getMac(algorithm);
        mac.init(key);

        if (tls) {
            block = new byte[BLOCK_SIZE_TLS];
            block[BLOCK_OFFSET_VERSION]   = protocolVersion.major;
            block[BLOCK_OFFSET_VERSION+1] = protocolVersion.minor;
        } else {
            block = new byte[BLOCK_SIZE_SSL];
        }
    }

    /**
     * Returns the length of the MAC.
     */
    int MAClen() {
        return macSize;
    }

    /**
     * Computes and returns the MAC for the data in this byte array.
     *
     * @param type record type
     * @param buf compressed record on which the MAC is computed
     * @param offset start of compressed record data
     * @param len the size of the compressed record
     */
    final byte[] compute(byte type, byte buf[], int offset, int len) {
        return compute(type, null, buf, offset, len);
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
     */
    final byte[] compute(byte type, ByteBuffer bb) {
        return compute(type, bb, null, 0, bb.remaining());
    }

    // increment the sequence number in the block array
    // it is a 64-bit number stored in big-endian format
    private void incrementSequenceNumber() {
        int k = 7;
        while ((k >= 0) && (++block[k] == 0)) {
            k--;
        }
    }

    /*
     * Compute based on either buffer type, either bb.position/limit
     * or buf/offset/len.
     */
    private byte[] compute(byte type, ByteBuffer bb, byte[] buf, int offset, int len) {

        if (macSize == 0) {
            return nullMAC;
        }

        block[BLOCK_OFFSET_TYPE] = type;
        block[block.length - 2]  = (byte)(len >> 8);
        block[block.length - 1]  = (byte)(len     );

        mac.update(block);
        incrementSequenceNumber();

        // content
        if (bb != null) {
            mac.update(bb);
        } else {
            mac.update(buf, offset, len);
        }

        return mac.doFinal();
    }

}
