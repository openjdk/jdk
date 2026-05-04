/*
 * Copyright (c) 2003, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.ByteBuffer;

import java.security.*;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.MacSpi;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

import jdk.internal.access.JavaNioAccess;
import jdk.internal.access.SharedSecrets;
import sun.nio.ch.DirectBuffer;

import sun.security.pkcs11.wrapper.*;
import static sun.security.pkcs11.wrapper.PKCS11Constants.*;
import static sun.security.pkcs11.wrapper.PKCS11Exception.RV.*;
import sun.security.util.PBEUtil;

/**
 * MAC implementation class. This class currently supports HMAC using
 * MD5, SHA-1, SHA-2 family (SHA-224, SHA-256, SHA-384, and SHA-512),
 * SHA-3 family (SHA3-224, SHA3-256, SHA3-384, and SHA3-512), and the
 * SSL3 MAC using MD5 and SHA-1.
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

    private static final JavaNioAccess NIO_ACCESS = SharedSecrets.getJavaNioAccess();

    // token instance
    private final Token token;

    // algorithm name
    private final String algorithm;

    // PBEKeyInfo if algorithm is PBE-related, otherwise null
    private final P11SecretKeyFactory.PBEKeyInfo svcPbeKi;

    // mechanism object
    private final CK_MECHANISM ckMechanism;

    // length of the MAC in bytes
    private final int macLength;

    // key instance used, if operation active
    private P11Key p11Key;

    // associated session, if any
    private Session session;

    // initialization status
    private boolean initialized;

    // one byte buffer for the update(byte) method, initialized on demand
    private byte[] oneByte;

    P11Mac(Token token, String algorithm, long mechanism) {
        super();
        this.token = token;
        this.algorithm = algorithm;
        this.svcPbeKi = P11SecretKeyFactory.getPBEKeyInfo(algorithm);
        if (svcPbeKi != null) {
            macLength = svcPbeKi.keyLen / 8;
        } else {
            P11SecretKeyFactory.HMACKeyInfo svcKi =
                    P11SecretKeyFactory.getHMACKeyInfo(algorithm);
            if (svcKi == null) {
                throw new ProviderException("Unknown mechanism: " + mechanism);
            }
            macLength = svcKi.keyLen / 8;
        }
        ckMechanism = new CK_MECHANISM(mechanism, mechanism == CKM_SSL3_MD5_MAC
                || mechanism == CKM_SSL3_SHA1_MAC ? Long.valueOf(macLength) :
                null);
    }

    // reset the states to the pre-initialized values
    private void reset(boolean doCancel) {
        if (!initialized) {
            return;
        }
        initialized = false;

        try {
            if (session == null) {
                return;
            }

            if (doCancel && token.explicitCancel) {
                cancelOperation();
            }
        } finally {
            p11Key.releaseKeyID();
            session = token.releaseSession(session);
        }
    }

    private void cancelOperation() {
        token.ensureValid();

        if (P11Util.trySessionCancel(token, session, CKF_SIGN)) {
            return;
        }

        // cancel by finishing operations; avoid killSession as some
        // hardware vendors may require re-login
        try {
            token.p11.C_SignFinal(session.id(), 0);
        } catch (PKCS11Exception e) {
            if (e.match(CKR_OPERATION_NOT_INITIALIZED)) {
                // Cancel Operation may be invoked after an error on a PKCS#11
                // call. If the operation inside the token was already cancelled,
                // do not fail here. This is part of a defensive mechanism for
                // PKCS#11 libraries that do not strictly follow the standard.
                return;
            }
            throw new ProviderException("Cancel failed", e);
        }
    }

    private void ensureInitialized() throws PKCS11Exception {
        if (!initialized) {
            initialize();
        }
    }

    private void initialize() throws PKCS11Exception {
        if (p11Key == null) {
            throw new ProviderException(
                    "Operation cannot be performed without calling engineInit first");
        }
        token.ensureValid();
        long p11KeyID = p11Key.getKeyID();
        try {
            if (session == null) {
                session = token.getOpSession();
            }
            token.p11.C_SignInit(session.id(), ckMechanism, p11KeyID);
        } catch (PKCS11Exception e) {
            p11Key.releaseKeyID();
            session = token.releaseSession(session);
            throw e;
        }
        initialized = true;
    }

    // see JCE spec
    protected int engineGetMacLength() {
        return macLength;
    }

    // see JCE spec
    protected void engineReset() {
        reset(true);
    }

    // see JCE spec
    protected void engineInit(Key key, AlgorithmParameterSpec params)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        reset(true);
        p11Key = null;
        if (svcPbeKi != null) {
            // Do key derivation using P11SecretKeyFactory, then store the
            // derived key to p11Key
            PBEKeySpec pbeKeySpec = PBEUtil.getPBAKeySpec(key, params);
            try {
                P11Key.P11PBKDFKey derivedKey =
                        P11SecretKeyFactory.derivePBEKey(token,
                        pbeKeySpec, svcPbeKi);
                // This Mac service uses the token where the derived key
                // lives so there won't be any need to re-derive and use
                // the password. The p11Key cannot be accessed out of this
                // class.
                derivedKey.clearPassword();
                p11Key = derivedKey;
            } catch (InvalidKeySpecException e) {
                throw new InvalidKeyException(e);
            } finally {
                pbeKeySpec.clearPassword();
            }
            if (params instanceof PBEParameterSpec pbeParams) {
                // For PBE services, reassign params to the underlying
                // service params. Notice that Mac services expect this
                // value to be null.
                params = pbeParams.getParameterSpec();
            }
        } else { // for the non-PBE case
            p11Key = P11SecretKeyFactory.convertKey(token, key, algorithm);
        }
        if (params != null) {
            throw new InvalidAlgorithmParameterException(
                    "Parameters not supported");
        }
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
            return token.p11.C_SignFinal(session.id(), 0);
        } catch (PKCS11Exception e) {
            // As per the PKCS#11 standard, C_SignFinal may only
            // keep the operation active on CKR_BUFFER_TOO_SMALL errors or
            // successful calls to determine the output length. However,
            // these cases are handled at OpenJDK's libj2pkcs11 native
            // library. Thus, P11Mac::reset can be called with a 'false'
            // doCancel argument from here.
            throw new ProviderException("doFinal() failed", e);
        } finally {
            reset(false);
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
            if (!(byteBuffer instanceof DirectBuffer)) {
                super.engineUpdate(byteBuffer);
                return;
            }
            int ofs = byteBuffer.position();
            NIO_ACCESS.acquireSession(byteBuffer);
            try  {
                final long address = NIO_ACCESS.getBufferAddress(byteBuffer);
                token.p11.C_SignUpdate(session.id(), address + ofs, null, 0, len);
            } finally {
                NIO_ACCESS.releaseSession(byteBuffer);
            }
            byteBuffer.position(ofs + len);
        } catch (PKCS11Exception e) {
            throw new ProviderException("update() failed", e);
        }
    }
}
