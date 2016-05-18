/*
 * Copyright (c) 2002, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.security.*;
import java.util.Locale;
import java.nio.ByteBuffer;

/**
 * Abstraction for the SSL/TLS hash of all handshake messages that is
 * maintained to verify the integrity of the negotiation. Internally,
 * it consists of an MD5 and an SHA1 digest. They are used in the client
 * and server finished messages and in certificate verify messages (if sent).
 *
 * This class transparently deals with cloneable and non-cloneable digests.
 *
 * This class now supports TLS 1.2 also. The key difference for TLS 1.2
 * is that you cannot determine the hash algorithms for CertificateVerify
 * at a early stage. On the other hand, it's simpler than TLS 1.1 (and earlier)
 * that there is no messy MD5+SHA1 digests.
 *
 * You need to obey these conventions when using this class:
 *
 * 1. protocolDetermined(version) should be called when the negotiated
 * protocol version is determined.
 *
 * 2. Before protocolDetermined() is called, only update(), and reset()
 * and setFinishedAlg() can be called.
 *
 * 3. After protocolDetermined() is called, reset() cannot be called.
 *
 * 4. After protocolDetermined() is called, if the version is pre-TLS 1.2,
 * getFinishedHash() cannot be called. Otherwise,
 * getMD5Clone() and getSHAClone() cannot be called.
 *
 * 5. getMD5Clone() and getSHAClone() can only be called after
 * protocolDetermined() is called and version is pre-TLS 1.2.
 *
 * 6. getFinishedHash() can only be called after protocolDetermined()
 * and setFinishedAlg() have been called and the version is TLS 1.2.
 *
 * Suggestion: Call protocolDetermined() and setFinishedAlg()
 * as early as possible.
 *
 * Example:
 * <pre>
 * HandshakeHash hh = new HandshakeHash(...)
 * hh.protocolDetermined(ProtocolVersion.TLS12);
 * hh.update(clientHelloBytes);
 * hh.setFinishedAlg("SHA-256");
 * hh.update(serverHelloBytes);
 * ...
 * hh.update(CertificateVerifyBytes);
 * ...
 * hh.update(finished1);
 * byte[] finDigest1 = hh.getFinishedHash();
 * hh.update(finished2);
 * byte[] finDigest2 = hh.getFinishedHash();
 * </pre>
 */
final class HandshakeHash {

    // Common

    // -1:  unknown
    //  1:  <=TLS 1.1
    //  2:  TLS 1.2
    private int version = -1;
    private ByteArrayOutputStream data = new ByteArrayOutputStream();

    // For TLS 1.1
    private MessageDigest md5, sha;
    private final int clonesNeeded;    // needs to be saved for later use

    // For TLS 1.2
    private MessageDigest finMD;

    // Cache for input record handshake hash computation
    private ByteArrayOutputStream reserve = new ByteArrayOutputStream();

    /**
     * Create a new HandshakeHash. needCertificateVerify indicates whether
     * a hash for the certificate verify message is required.
     */
    HandshakeHash(boolean needCertificateVerify) {
        clonesNeeded = needCertificateVerify ? 4 : 3;
    }

    void reserve(ByteBuffer input) {
        if (input.hasArray()) {
            reserve.write(input.array(),
                    input.position() + input.arrayOffset(), input.remaining());
        } else {
            int inPos = input.position();
            byte[] holder = new byte[input.remaining()];
            input.get(holder);
            input.position(inPos);
            reserve.write(holder, 0, holder.length);
        }
    }

    void reserve(byte[] b, int offset, int len) {
        reserve.write(b, offset, len);
    }

    void reload() {
        if (reserve.size() != 0) {
            byte[] bytes = reserve.toByteArray();
            reserve.reset();
            update(bytes, 0, bytes.length);
        }
    }

    void update(ByteBuffer input) {

        // reload if there are reserved messages.
        reload();

        int inPos = input.position();
        switch (version) {
            case 1:
                md5.update(input);
                input.position(inPos);

                sha.update(input);
                input.position(inPos);

                break;
            default:
                if (finMD != null) {
                    finMD.update(input);
                    input.position(inPos);
                }
                if (input.hasArray()) {
                    data.write(input.array(),
                            inPos + input.arrayOffset(), input.remaining());
                } else {
                    byte[] holder = new byte[input.remaining()];
                    input.get(holder);
                    input.position(inPos);
                    data.write(holder, 0, holder.length);
                }
                break;
        }
    }

    void update(byte handshakeType, byte[] handshakeBody) {

        // reload if there are reserved messages.
        reload();

        switch (version) {
            case 1:
                md5.update(handshakeType);
                sha.update(handshakeType);

                md5.update((byte)((handshakeBody.length >> 16) & 0xFF));
                sha.update((byte)((handshakeBody.length >> 16) & 0xFF));
                md5.update((byte)((handshakeBody.length >> 8) & 0xFF));
                sha.update((byte)((handshakeBody.length >> 8) & 0xFF));
                md5.update((byte)(handshakeBody.length & 0xFF));
                sha.update((byte)(handshakeBody.length & 0xFF));

                md5.update(handshakeBody);
                sha.update(handshakeBody);
                break;
            default:
                if (finMD != null) {
                    finMD.update(handshakeType);
                    finMD.update((byte)((handshakeBody.length >> 16) & 0xFF));
                    finMD.update((byte)((handshakeBody.length >> 8) & 0xFF));
                    finMD.update((byte)(handshakeBody.length & 0xFF));
                    finMD.update(handshakeBody);
                }
                data.write(handshakeType);
                data.write((byte)((handshakeBody.length >> 16) & 0xFF));
                data.write((byte)((handshakeBody.length >> 8) & 0xFF));
                data.write((byte)(handshakeBody.length & 0xFF));
                data.write(handshakeBody, 0, handshakeBody.length);
                break;
        }
    }

    void update(byte[] b, int offset, int len) {

        // reload if there are reserved messages.
        reload();

        switch (version) {
            case 1:
                md5.update(b, offset, len);
                sha.update(b, offset, len);
                break;
            default:
                if (finMD != null) {
                    finMD.update(b, offset, len);
                }
                data.write(b, offset, len);
                break;
        }
    }

    /**
     * Reset the remaining digests. Note this does *not* reset the number of
     * digest clones that can be obtained. Digests that have already been
     * cloned and are gone remain gone.
     */
    void reset() {
        if (version != -1) {
            throw new RuntimeException(
                    "reset() can be only be called before protocolDetermined");
        }
        data.reset();
    }


    void protocolDetermined(ProtocolVersion pv) {

        // Do not set again, will ignore
        if (version != -1) {
            return;
        }

        if (pv.maybeDTLSProtocol()) {
            version = pv.compareTo(ProtocolVersion.DTLS12) >= 0 ? 2 : 1;
        } else {
            version = pv.compareTo(ProtocolVersion.TLS12) >= 0 ? 2 : 1;
        }
        switch (version) {
            case 1:
                // initiate md5, sha and call update on saved array
                try {
                    md5 = CloneableDigest.getDigest("MD5", clonesNeeded);
                    sha = CloneableDigest.getDigest("SHA", clonesNeeded);
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException
                                ("Algorithm MD5 or SHA not available", e);
                }
                byte[] bytes = data.toByteArray();
                update(bytes, 0, bytes.length);
                break;
            case 2:
                break;
        }
    }

    /////////////////////////////////////////////////////////////
    // Below are old methods for pre-TLS 1.1
    /////////////////////////////////////////////////////////////

    /**
     * Return a new MD5 digest updated with all data hashed so far.
     */
    MessageDigest getMD5Clone() {
        if (version != 1) {
            throw new RuntimeException(
                    "getMD5Clone() can be only be called for TLS 1.1");
        }
        return cloneDigest(md5);
    }

    /**
     * Return a new SHA digest updated with all data hashed so far.
     */
    MessageDigest getSHAClone() {
        if (version != 1) {
            throw new RuntimeException(
                    "getSHAClone() can be only be called for TLS 1.1");
        }
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

    /////////////////////////////////////////////////////////////
    // Below are new methods for TLS 1.2
    /////////////////////////////////////////////////////////////

    private static String normalizeAlgName(String alg) {
        alg = alg.toUpperCase(Locale.US);
        if (alg.startsWith("SHA")) {
            if (alg.length() == 3) {
                return "SHA-1";
            }
            if (alg.charAt(3) != '-') {
                return "SHA-" + alg.substring(3);
            }
        }
        return alg;
    }
    /**
     * Specifies the hash algorithm used in Finished. This should be called
     * based in info in ServerHello.
     * Can be called multiple times.
     */
    void setFinishedAlg(String s) {
        if (s == null) {
            throw new RuntimeException(
                    "setFinishedAlg's argument cannot be null");
        }

        // Can be called multiple times, but only set once
        if (finMD != null) return;

        try {
            finMD = CloneableDigest.getDigest(normalizeAlgName(s), 2);
        } catch (NoSuchAlgorithmException e) {
            throw new Error(e);
        }
        finMD.update(data.toByteArray());
    }

    byte[] getAllHandshakeMessages() {
        return data.toByteArray();
    }

    /**
     * Calculates the hash in Finished. Must be called after setFinishedAlg().
     * This method can be called twice, for Finished messages of the server
     * side and client side respectively.
     */
    byte[] getFinishedHash() {
        try {
            return cloneDigest(finMD).digest();
        } catch (Exception e) {
            throw new Error("Error during hash calculation", e);
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

    @Override
    protected int engineGetDigestLength() {
        checkState();
        return digests[0].getDigestLength();
    }

    @Override
    protected void engineUpdate(byte b) {
        checkState();
        for (int i = 0; (i < digests.length) && (digests[i] != null); i++) {
            digests[i].update(b);
        }
    }

    @Override
    protected void engineUpdate(byte[] b, int offset, int len) {
        checkState();
        for (int i = 0; (i < digests.length) && (digests[i] != null); i++) {
            digests[i].update(b, offset, len);
        }
    }

    @Override
    protected byte[] engineDigest() {
        checkState();
        byte[] digest = digests[0].digest();
        digestReset();
        return digest;
    }

    @Override
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

    @Override
    protected void engineReset() {
        checkState();
        for (int i = 0; (i < digests.length) && (digests[i] != null); i++) {
            digests[i].reset();
        }
    }

    @Override
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
