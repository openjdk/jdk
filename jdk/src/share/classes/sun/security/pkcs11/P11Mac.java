/*
 * Copyright (c) 2003, 2012, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.pkcs11;

import java.util.*;
import java.nio.ByteBuffer;

import java.security.*;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.MacSpi;

import sun.nio.ch.DirectBuffer;

import sun.security.pkcs11.wrapper.*;
import static sun.security.pkcs11.wrapper.PKCS11Constants.*;

/**
 * MAC implementation class. This class currently supports HMAC using
 * MD5, SHA-1, SHA-224, SHA-256, SHA-384, and SHA-512 and the SSL3 MAC
 * using MD5 and SHA-1.
 *
 * Note that unlike other classes (e.g. Signature), this does not
 * composite various operations if the token only supports part of the
 * required functionality. The MAC implementations in SunJCE already
 * do exactly that by implementing an MAC on top of MessageDigests. We
 * could not do any better than they.
 *
 * @author  Andreas Sterbenz
 * @since   1.5
 */
final class P11Mac extends MacSpi {

    /* unitialized, all fields except session have arbitrary values */
    private final static int S_UNINIT   = 1;

    /* session initialized, no data processed yet */
    private final static int S_RESET    = 2;

    /* session initialized, data processed */
    private final static int S_UPDATE   = 3;

    /* transitional state after doFinal() before we go to S_UNINIT */
    private final static int S_DOFINAL  = 4;

    // token instance
    private final Token token;

    // algorithm name
    private final String algorithm;

    // mechanism id
    private final long mechanism;

    // mechanism object
    private final CK_MECHANISM ckMechanism;

    // length of the MAC in bytes
    private final int macLength;

    // key instance used, if operation active
    private P11Key p11Key;

    // associated session, if any
    private Session session;

    // state, one of S_* above
    private int state;

    // one byte buffer for the update(byte) method, initialized on demand
    private byte[] oneByte;

    P11Mac(Token token, String algorithm, long mechanism)
            throws PKCS11Exception {
        super();
        this.token = token;
        this.algorithm = algorithm;
        this.mechanism = mechanism;
        Long params = null;
        switch ((int)mechanism) {
        case (int)CKM_MD5_HMAC:
            macLength = 16;
            break;
        case (int)CKM_SHA_1_HMAC:
            macLength = 20;
            break;
        case (int)CKM_SHA224_HMAC:
            macLength = 28;
            break;
        case (int)CKM_SHA256_HMAC:
            macLength = 32;
            break;
        case (int)CKM_SHA384_HMAC:
            macLength = 48;
            break;
        case (int)CKM_SHA512_HMAC:
            macLength = 64;
            break;
        case (int)CKM_SSL3_MD5_MAC:
            macLength = 16;
            params = Long.valueOf(16);
            break;
        case (int)CKM_SSL3_SHA1_MAC:
            macLength = 20;
            params = Long.valueOf(20);
            break;
        default:
            throw new ProviderException("Unknown mechanism: " + mechanism);
        }
        ckMechanism = new CK_MECHANISM(mechanism, params);
        state = S_UNINIT;
        initialize();
    }

    private void ensureInitialized() throws PKCS11Exception {
        token.ensureValid();
        if (state == S_UNINIT) {
            initialize();
        }
    }

    private void cancelOperation() {
        token.ensureValid();
        if (state == S_UNINIT) {
            return;
        }
        state = S_UNINIT;
        if ((session == null) || (token.explicitCancel == false)) {
            return;
        }
        try {
            token.p11.C_SignFinal(session.id(), 0);
        } catch (PKCS11Exception e) {
            throw new ProviderException("Cancel failed", e);
        }
    }

    private void initialize() throws PKCS11Exception {
        if (state == S_RESET) {
            return;
        }
        if (session == null) {
            session = token.getOpSession();
        }
        if (p11Key != null) {
            token.p11.C_SignInit
                (session.id(), ckMechanism, p11Key.keyID);
            state = S_RESET;
        } else {
            state = S_UNINIT;
        }
    }

    // see JCE spec
    protected int engineGetMacLength() {
        return macLength;
    }

    // see JCE spec
    protected void engineReset() {
        // the framework insists on calling reset() after doFinal(),
        // but we prefer to take care of reinitialization ourselves
        if (state == S_DOFINAL) {
            state = S_UNINIT;
            return;
        }
        cancelOperation();
        try {
            initialize();
        } catch (PKCS11Exception e) {
            throw new ProviderException("reset() failed, ", e);
        }
    }

    // see JCE spec
    protected void engineInit(Key key, AlgorithmParameterSpec params)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        if (params != null) {
            throw new InvalidAlgorithmParameterException
                ("Parameters not supported");
        }
        cancelOperation();
        p11Key = P11SecretKeyFactory.convertKey(token, key, algorithm);
        try {
            initialize();
        } catch (PKCS11Exception e) {
            throw new InvalidKeyException("init() failed", e);
        }
    }

    // see JCE spec
    protected byte[] engineDoFinal() {
        try {
            ensureInitialized();
            byte[] mac = token.p11.C_SignFinal(session.id(), 0);
            state = S_DOFINAL;
            return mac;
        } catch (PKCS11Exception e) {
            throw new ProviderException("doFinal() failed", e);
        } finally {
            session = token.releaseSession(session);
        }
    }

    // see JCE spec
    protected void engineUpdate(byte input) {
        if (oneByte == null) {
           oneByte = new byte[1];
        }
        oneByte[0] = input;
        engineUpdate(oneByte, 0, 1);
    }

    // see JCE spec
    protected void engineUpdate(byte[] b, int ofs, int len) {
        try {
            ensureInitialized();
            token.p11.C_SignUpdate(session.id(), 0, b, ofs, len);
            state = S_UPDATE;
        } catch (PKCS11Exception e) {
            throw new ProviderException("update() failed", e);
        }
    }

    // see JCE spec
    protected void engineUpdate(ByteBuffer byteBuffer) {
        try {
            ensureInitialized();
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
            token.p11.C_SignUpdate(session.id(), addr + ofs, null, 0, len);
            byteBuffer.position(ofs + len);
            state = S_UPDATE;
        } catch (PKCS11Exception e) {
            throw new ProviderException("update() failed", e);
        }
    }
}
