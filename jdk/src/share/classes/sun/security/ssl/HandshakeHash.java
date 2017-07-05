/*
 * Copyright (c) 2002, 2007, Oracle and/or its affiliates. All rights reserved.
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

import java.security.*;

/**
 * Abstraction for the SSL/TLS hash of all handshake messages that is
 * maintained to verify the integrity of the negotiation. Internally,
 * it consists of an MD5 and an SHA1 digest. They are used in the client
 * and server finished messages and in certificate verify messages (if sent).
 *
 * This class transparently deals with cloneable and non-cloneable digests.
 *
 */
final class HandshakeHash {

    private final MessageDigest md5, sha;

    /**
     * Create a new HandshakeHash. needCertificateVerify indicates whether
     * a hash for the certificate verify message is required.
     */
    HandshakeHash(boolean needCertificateVerify) {
        int n = needCertificateVerify ? 3 : 2;
        try {
            md5 = CloneableDigest.getDigest("MD5", n);
            sha = CloneableDigest.getDigest("SHA", n);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException
                        ("Algorithm MD5 or SHA not available", e);

        }
    }

    void update(byte b) {
        md5.update(b);
        sha.update(b);
    }

    void update(byte[] b, int offset, int len) {
        md5.update(b, offset, len);
        sha.update(b, offset, len);
    }

    /**
     * Reset the remaining digests. Note this does *not* reset the numbe of
     * digest clones that can be obtained. Digests that have already been
     * cloned and are gone remain gone.
     */
    void reset() {
        md5.reset();
        sha.reset();
    }

    /**
     * Return a new MD5 digest updated with all data hashed so far.
     */
    MessageDigest getMD5Clone() {
        return cloneDigest(md5);
    }

    /**
     * Return a new SHA digest updated with all data hashed so far.
     */
    MessageDigest getSHAClone() {
        return cloneDigest(sha);
    }

    private static MessageDigest cloneDigest(MessageDigest digest) {
        try {
            return (MessageDigest)digest.clone();
        } catch (CloneNotSupportedException e) {
            // cannot occur for digests generated via CloneableDigest
            throw new RuntimeException("Could not clone digest", e);
        }
    }

}

/**
 * A wrapper for MessageDigests that simulates cloning of non-cloneable
 * digests. It uses the standard MessageDigest API and therefore can be used
 * transparently in place of a regular digest.
 *
 * Note that we extend the MessageDigest class directly rather than
 * MessageDigestSpi. This works because MessageDigest was originally designed
 * this way in the JDK 1.1 days which allows us to avoid creating an internal
 * provider.
 *
 * It can be "cloned" a limited number of times, which is specified at
 * construction time. This is achieved by internally maintaining n digests
 * in parallel. Consequently, it is only 1/n-th times as fast as the original
 * digest.
 *
 * Example:
 *   MessageDigest md = CloneableDigest.getDigest("SHA", 2);
 *   md.update(data1);
 *   MessageDigest md2 = (MessageDigest)md.clone();
 *   md2.update(data2);
 *   byte[] d1 = md2.digest(); // digest of data1 || data2
 *   md.update(data3);
 *   byte[] d2 = md.digest();  // digest of data1 || data3
 *
 * This class is not thread safe.
 *
 */
final class CloneableDigest extends MessageDigest implements Cloneable {

    /**
     * The individual MessageDigests. Initially, all elements are non-null.
     * When clone() is called, the non-null element with the maximum index is
     * returned and the array element set to null.
     *
     * All non-null element are always in the same state.
     */
    private final MessageDigest[] digests;

    private CloneableDigest(MessageDigest digest, int n, String algorithm)
            throws NoSuchAlgorithmException {
        super(algorithm);
        digests = new MessageDigest[n];
        digests[0] = digest;
        for (int i = 1; i < n; i++) {
            digests[i] = JsseJce.getMessageDigest(algorithm);
        }
    }

    /**
     * Return a MessageDigest for the given algorithm that can be cloned the
     * specified number of times. If the default implementation supports
     * cloning, it is returned. Otherwise, an instance of this class is
     * returned.
     */
    static MessageDigest getDigest(String algorithm, int n)
            throws NoSuchAlgorithmException {
        MessageDigest digest = JsseJce.getMessageDigest(algorithm);
        try {
            digest.clone();
            // already cloneable, use it
            return digest;
        } catch (CloneNotSupportedException e) {
            return new CloneableDigest(digest, n, algorithm);
        }
    }

    /**
     * Check if this object is still usable. If it has already been cloned the
     * maximum number of times, there are no digests left and this object can no
     * longer be used.
     */
    private void checkState() {
        // XXX handshaking currently doesn't stop updating hashes...
        // if (digests[0] == null) {
        //     throw new IllegalStateException("no digests left");
        // }
    }

    protected int engineGetDigestLength() {
        checkState();
        return digests[0].getDigestLength();
    }

    protected void engineUpdate(byte b) {
        checkState();
        for (int i = 0; (i < digests.length) && (digests[i] != null); i++) {
            digests[i].update(b);
        }
    }

    protected void engineUpdate(byte[] b, int offset, int len) {
        checkState();
        for (int i = 0; (i < digests.length) && (digests[i] != null); i++) {
            digests[i].update(b, offset, len);
        }
    }

    protected byte[] engineDigest() {
        checkState();
        byte[] digest = digests[0].digest();
        digestReset();
        return digest;
    }

    protected int engineDigest(byte[] buf, int offset, int len)
            throws DigestException {
        checkState();
        int n = digests[0].digest(buf, offset, len);
        digestReset();
        return n;
    }

    /**
     * Reset all digests after a digest() call. digests[0] has already been
     * implicitly reset by the digest() call and does not need to be reset
     * again.
     */
    private void digestReset() {
        for (int i = 1; (i < digests.length) && (digests[i] != null); i++) {
            digests[i].reset();
        }
    }

    protected void engineReset() {
        checkState();
        for (int i = 0; (i < digests.length) && (digests[i] != null); i++) {
            digests[i].reset();
        }
    }

    public Object clone() {
        checkState();
        for (int i = digests.length - 1; i >= 0; i--) {
            if (digests[i] != null) {
                MessageDigest digest = digests[i];
                digests[i] = null;
                return digest;
            }
        }
        // cannot occur
        throw new InternalError();
    }

}
