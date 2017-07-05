/*
 * Copyright 2003-2010 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.security.pkcs11;

import java.util.*;
import java.nio.ByteBuffer;

import java.security.*;

import javax.crypto.SecretKey;

import sun.nio.ch.DirectBuffer;

import sun.security.pkcs11.wrapper.*;
import static sun.security.pkcs11.wrapper.PKCS11Constants.*;

/**
 * MessageDigest implementation class. This class currently supports
 * MD2, MD5, SHA-1, SHA-256, SHA-384, and SHA-512.
 *
 * Note that many digest operations are on fairly small amounts of data
 * (less than 100 bytes total). For example, the 2nd hashing in HMAC or
 * the PRF in TLS. In order to speed those up, we use some buffering to
 * minimize number of the Java->native transitions.
 *
 * @author  Andreas Sterbenz
 * @since   1.5
 */
final class P11Digest extends MessageDigestSpi {

    /* unitialized, fields uninitialized, no session acquired */
    private final static int S_BLANK    = 1;

    // data in buffer, all fields valid, session acquired
    // but digest not initialized
    private final static int S_BUFFERED = 2;

    /* session initialized for digesting */
    private final static int S_INIT     = 3;

    private final static int BUFFER_SIZE = 96;

    // token instance
    private final Token token;

    // algorithm name
    private final String algorithm;

    // mechanism id
    private final long mechanism;

    // length of the digest in bytes
    private final int digestLength;

    // associated session, if any
    private Session session;

    // current state, one of S_* above
    private int state;

    // one byte buffer for the update(byte) method, initialized on demand
    private byte[] oneByte;

    // buffer to reduce number of JNI calls
    private final byte[] buffer;

    // offset into the buffer
    private int bufOfs;

    P11Digest(Token token, String algorithm, long mechanism) {
        super();
        this.token = token;
        this.algorithm = algorithm;
        this.mechanism = mechanism;
        switch ((int)mechanism) {
        case (int)CKM_MD2:
        case (int)CKM_MD5:
            digestLength = 16;
            break;
        case (int)CKM_SHA_1:
            digestLength = 20;
            break;
        case (int)CKM_SHA256:
            digestLength = 32;
            break;
        case (int)CKM_SHA384:
            digestLength = 48;
            break;
        case (int)CKM_SHA512:
            digestLength = 64;
            break;
        default:
            throw new ProviderException("Unknown mechanism: " + mechanism);
        }
        buffer = new byte[BUFFER_SIZE];
        state = S_BLANK;
        engineReset();
    }

    // see JCA spec
    protected int engineGetDigestLength() {
        return digestLength;
    }

    private void cancelOperation() {
        token.ensureValid();
        if (session == null) {
            return;
        }
        if ((state != S_INIT) || (token.explicitCancel == false)) {
            return;
        }
        // need to explicitly "cancel" active op by finishing it
        try {
            token.p11.C_DigestFinal(session.id(), buffer, 0, buffer.length);
        } catch (PKCS11Exception e) {
            throw new ProviderException("cancel() failed", e);
        } finally {
            state = S_BUFFERED;
        }
    }

    private void fetchSession() {
        token.ensureValid();
        if (state == S_BLANK) {
            engineReset();
        }
    }

    // see JCA spec
    protected void engineReset() {
        try {
            cancelOperation();
            bufOfs = 0;
            if (session == null) {
                session = token.getOpSession();
            }
            state = S_BUFFERED;
        } catch (PKCS11Exception e) {
            state = S_BLANK;
            throw new ProviderException("reset() failed, ", e);
        }
    }

    // see JCA spec
    protected byte[] engineDigest() {
        try {
            byte[] digest = new byte[digestLength];
            int n = engineDigest(digest, 0, digestLength);
            return digest;
        } catch (DigestException e) {
            throw new ProviderException("internal error", e);
        }
    }

    // see JCA spec
    protected int engineDigest(byte[] digest, int ofs, int len)
            throws DigestException {
        if (len < digestLength) {
            throw new DigestException("Length must be at least " + digestLength);
        }
        fetchSession();
        try {
            int n;
            if (state == S_BUFFERED) {
                n = token.p11.C_DigestSingle(session.id(),
                        new CK_MECHANISM(mechanism),
                        buffer, 0, bufOfs, digest, ofs, len);
            } else {
                if (bufOfs != 0) {
                    doUpdate(buffer, 0, bufOfs);
                }
                n = token.p11.C_DigestFinal(session.id(), digest, ofs, len);
            }
            if (n != digestLength) {
                throw new ProviderException("internal digest length error");
            }
            return n;
        } catch (PKCS11Exception e) {
            throw new ProviderException("digest() failed", e);
        } finally {
            state = S_BLANK;
            bufOfs = 0;
            session = token.releaseSession(session);
        }
    }

    // see JCA spec
    protected void engineUpdate(byte in) {
        if (oneByte == null) {
            oneByte = new byte[1];
        }
        oneByte[0] = in;
        engineUpdate(oneByte, 0, 1);
    }

    // see JCA spec
    protected void engineUpdate(byte[] in, int ofs, int len) {
        fetchSession();
        if (len <= 0) {
            return;
        }
        if ((bufOfs != 0) && (bufOfs + len > buffer.length)) {
            doUpdate(buffer, 0, bufOfs);
            bufOfs = 0;
        }
        if (bufOfs + len > buffer.length) {
            doUpdate(in, ofs, len);
        } else {
            System.arraycopy(in, ofs, buffer, bufOfs, len);
            bufOfs += len;
        }
    }

    // Called by SunJSSE via reflection during the SSL 3.0 handshake if
    // the master secret is sensitive. We may want to consider making this
    // method public in a future release.
    protected void implUpdate(SecretKey key) throws InvalidKeyException {
        fetchSession();
        if (bufOfs != 0) {
            doUpdate(buffer, 0, bufOfs);
            bufOfs = 0;
        }
        // SunJSSE calls this method only if the key does not have a RAW
        // encoding, i.e. if it is sensitive. Therefore, no point in calling
        // SecretKeyFactory to try to convert it. Just verify it ourselves.
        if (key instanceof P11Key == false) {
            throw new InvalidKeyException("Not a P11Key: " + key);
        }
        P11Key p11Key = (P11Key)key;
        if (p11Key.token != token) {
            throw new InvalidKeyException("Not a P11Key of this provider: " + key);
        }
        try {
            if (state == S_BUFFERED) {
                token.p11.C_DigestInit(session.id(), new CK_MECHANISM(mechanism));
                state = S_INIT;
            }
            token.p11.C_DigestKey(session.id(), p11Key.keyID);
        } catch (PKCS11Exception e) {
            throw new ProviderException("update(SecretKey) failed", e);
        }
    }

    // see JCA spec
    protected void engineUpdate(ByteBuffer byteBuffer) {
        fetchSession();
        int len = byteBuffer.remaining();
        if (len <= 0) {
            return;
        }
        if (byteBuffer instanceof DirectBuffer == false) {
            super.engineUpdate(byteBuffer);
            return;
        }
        long addr = ((DirectBuffer)byteBuffer).address();
        int ofs = byteBuffer.position();
        try {
            if (state == S_BUFFERED) {
                token.p11.C_DigestInit(session.id(), new CK_MECHANISM(mechanism));
                state = S_INIT;
                if (bufOfs != 0) {
                    doUpdate(buffer, 0, bufOfs);
                    bufOfs = 0;
                }
            }
            token.p11.C_DigestUpdate(session.id(), addr + ofs, null, 0, len);
            byteBuffer.position(ofs + len);
        } catch (PKCS11Exception e) {
            throw new ProviderException("update() failed", e);
        }
    }

    private void doUpdate(byte[] in, int ofs, int len) {
        if (len <= 0) {
            return;
        }
        try {
            if (state == S_BUFFERED) {
                token.p11.C_DigestInit(session.id(), new CK_MECHANISM(mechanism));
                state = S_INIT;
            }
            token.p11.C_DigestUpdate(session.id(), 0, in, ofs, len);
        } catch (PKCS11Exception e) {
            throw new ProviderException("update() failed", e);
        }
    }
}
