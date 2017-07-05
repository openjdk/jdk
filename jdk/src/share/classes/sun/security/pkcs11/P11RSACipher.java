/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.security.*;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.*;

import java.util.Locale;

import javax.crypto.*;
import javax.crypto.spec.*;

import static sun.security.pkcs11.TemplateManager.*;
import sun.security.pkcs11.wrapper.*;
import static sun.security.pkcs11.wrapper.PKCS11Constants.*;

/**
 * RSA Cipher implementation class. We currently only support
 * PKCS#1 v1.5 padding on top of CKM_RSA_PKCS.
 *
 * @author  Andreas Sterbenz
 * @since   1.5
 */
final class P11RSACipher extends CipherSpi {

    // minimum length of PKCS#1 v1.5 padding
    private final static int PKCS1_MIN_PADDING_LENGTH = 11;

    // constant byte[] of length 0
    private final static byte[] B0 = new byte[0];

    // mode constant for public key encryption
    private final static int MODE_ENCRYPT = 1;
    // mode constant for private key decryption
    private final static int MODE_DECRYPT = 2;
    // mode constant for private key encryption (signing)
    private final static int MODE_SIGN    = 3;
    // mode constant for public key decryption (verifying)
    private final static int MODE_VERIFY  = 4;

    // padding type constant for NoPadding
    private final static int PAD_NONE = 1;
    // padding type constant for PKCS1Padding
    private final static int PAD_PKCS1 = 2;

    // token instance
    private final Token token;

    // algorithm name (always "RSA")
    private final String algorithm;

    // mechanism id
    private final long mechanism;

    // associated session, if any
    private Session session;

    // mode, one of MODE_* above
    private int mode;

    // padding, one of PAD_* above
    private int padType;

    private byte[] buffer;
    private int bufOfs;

    // key, if init() was called
    private P11Key p11Key;

    // flag indicating whether an operation is initialized
    private boolean initialized;

    // maximum input data size allowed
    // for decryption, this is the length of the key
    // for encryption, length of the key minus minimum padding length
    private int maxInputSize;

    // maximum output size. this is the length of the key
    private int outputSize;

    P11RSACipher(Token token, String algorithm, long mechanism)
            throws PKCS11Exception {
        super();
        this.token = token;
        this.algorithm = "RSA";
        this.mechanism = mechanism;
    }

    // modes do not make sense for RSA, but allow ECB
    // see JCE spec
    protected void engineSetMode(String mode) throws NoSuchAlgorithmException {
        if (mode.equalsIgnoreCase("ECB") == false) {
            throw new NoSuchAlgorithmException("Unsupported mode " + mode);
        }
    }

    protected void engineSetPadding(String padding)
            throws NoSuchPaddingException {
        String lowerPadding = padding.toLowerCase(Locale.ENGLISH);
        if (lowerPadding.equals("pkcs1padding")) {
            padType = PAD_PKCS1;
        } else if (lowerPadding.equals("nopadding")) {
            padType = PAD_NONE;
        } else {
            throw new NoSuchPaddingException("Unsupported padding " + padding);
        }
    }

    // return 0 as block size, we are not a block cipher
    // see JCE spec
    protected int engineGetBlockSize() {
        return 0;
    }

    // return the output size
    // see JCE spec
    protected int engineGetOutputSize(int inputLen) {
        return outputSize;
    }

    // no IV, return null
    // see JCE spec
    protected byte[] engineGetIV() {
        return null;
    }

    // no parameters, return null
    // see JCE spec
    protected AlgorithmParameters engineGetParameters() {
        return null;
    }

    // see JCE spec
    protected void engineInit(int opmode, Key key, SecureRandom random)
            throws InvalidKeyException {
        implInit(opmode, key);
    }

    // see JCE spec
    protected void engineInit(int opmode, Key key,
            AlgorithmParameterSpec params, SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        if (params != null) {
            throw new InvalidAlgorithmParameterException
                ("Parameters not supported");
        }
        implInit(opmode, key);
    }

    // see JCE spec
    protected void engineInit(int opmode, Key key, AlgorithmParameters params,
            SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        if (params != null) {
            throw new InvalidAlgorithmParameterException
                ("Parameters not supported");
        }
        implInit(opmode, key);
    }

    private void implInit(int opmode, Key key) throws InvalidKeyException {
        cancelOperation();
        p11Key = P11KeyFactory.convertKey(token, key, algorithm);
        boolean encrypt;
        if (opmode == Cipher.ENCRYPT_MODE) {
            encrypt = true;
        } else if (opmode == Cipher.DECRYPT_MODE) {
            encrypt = false;
        } else if (opmode == Cipher.WRAP_MODE) {
            if (p11Key.isPublic() == false) {
                throw new InvalidKeyException
                                ("Wrap has to be used with public keys");
            }
            // No further setup needed for C_Wrap(). We'll initialize later if
            // we can't use C_Wrap().
            return;
        } else if (opmode == Cipher.UNWRAP_MODE) {
            if (p11Key.isPrivate() == false) {
                throw new InvalidKeyException
                                ("Unwrap has to be used with private keys");
            }
            // No further setup needed for C_Unwrap(). We'll initialize later
            // if we can't use C_Unwrap().
            return;
        } else {
            throw new InvalidKeyException("Unsupported mode: " + opmode);
        }
        if (p11Key.isPublic()) {
            mode = encrypt ? MODE_ENCRYPT : MODE_VERIFY;
        } else if (p11Key.isPrivate()) {
            mode = encrypt ? MODE_SIGN : MODE_DECRYPT;
        } else {
            throw new InvalidKeyException("Unknown key type: " + p11Key);
        }
        int n = (p11Key.length() + 7) >> 3;
        outputSize = n;
        buffer = new byte[n];
        maxInputSize = ((padType == PAD_PKCS1 && encrypt) ?
                            (n - PKCS1_MIN_PADDING_LENGTH) : n);
        try {
            initialize();
        } catch (PKCS11Exception e) {
            throw new InvalidKeyException("init() failed", e);
        }
    }

    private void cancelOperation() {
        token.ensureValid();
        if (initialized == false) {
            return;
        }
        initialized = false;
        if ((session == null) || (token.explicitCancel == false)) {
            return;
        }
        if (session.hasObjects() == false) {
            session = token.killSession(session);
            return;
        }
        try {
            PKCS11 p11 = token.p11;
            int inLen = maxInputSize;
            int outLen = buffer.length;
            switch (mode) {
            case MODE_ENCRYPT:
                p11.C_Encrypt
                        (session.id(), buffer, 0, inLen, buffer, 0, outLen);
                break;
            case MODE_DECRYPT:
                p11.C_Decrypt
                        (session.id(), buffer, 0, inLen, buffer, 0, outLen);
                break;
            case MODE_SIGN:
                byte[] tmpBuffer = new byte[maxInputSize];
                p11.C_Sign
                        (session.id(), tmpBuffer);
                break;
            case MODE_VERIFY:
                p11.C_VerifyRecover
                        (session.id(), buffer, 0, inLen, buffer, 0, outLen);
                break;
            default:
                throw new ProviderException("internal error");
            }
        } catch (PKCS11Exception e) {
            // XXX ensure this always works, ignore error
        }
    }

    private void ensureInitialized() throws PKCS11Exception {
        token.ensureValid();
        if (initialized == false) {
            initialize();
        }
    }

    private void initialize() throws PKCS11Exception {
        if (session == null) {
            session = token.getOpSession();
        }
        PKCS11 p11 = token.p11;
        CK_MECHANISM ckMechanism = new CK_MECHANISM(mechanism);
        switch (mode) {
        case MODE_ENCRYPT:
            p11.C_EncryptInit(session.id(), ckMechanism, p11Key.keyID);
            break;
        case MODE_DECRYPT:
            p11.C_DecryptInit(session.id(), ckMechanism, p11Key.keyID);
            break;
        case MODE_SIGN:
            p11.C_SignInit(session.id(), ckMechanism, p11Key.keyID);
            break;
        case MODE_VERIFY:
            p11.C_VerifyRecoverInit(session.id(), ckMechanism, p11Key.keyID);
            break;
        default:
            throw new AssertionError("internal error");
        }
        bufOfs = 0;
        initialized = true;
    }

    private void implUpdate(byte[] in, int inOfs, int inLen) {
        try {
            ensureInitialized();
        } catch (PKCS11Exception e) {
            throw new ProviderException("update() failed", e);
        }
        if ((inLen == 0) || (in == null)) {
            return;
        }
        if (bufOfs + inLen > maxInputSize) {
            bufOfs = maxInputSize + 1;
            return;
        }
        System.arraycopy(in, inOfs, buffer, bufOfs, inLen);
        bufOfs += inLen;
    }

    private int implDoFinal(byte[] out, int outOfs, int outLen)
            throws BadPaddingException, IllegalBlockSizeException {
        if (bufOfs > maxInputSize) {
            throw new IllegalBlockSizeException("Data must not be longer "
                + "than " + maxInputSize + " bytes");
        }
        try {
            ensureInitialized();
            PKCS11 p11 = token.p11;
            int n;
            switch (mode) {
            case MODE_ENCRYPT:
                n = p11.C_Encrypt
                        (session.id(), buffer, 0, bufOfs, out, outOfs, outLen);
                break;
            case MODE_DECRYPT:
                n = p11.C_Decrypt
                        (session.id(), buffer, 0, bufOfs, out, outOfs, outLen);
                break;
            case MODE_SIGN:
                byte[] tmpBuffer = new byte[bufOfs];
                System.arraycopy(buffer, 0, tmpBuffer, 0, bufOfs);
                tmpBuffer = p11.C_Sign(session.id(), tmpBuffer);
                if (tmpBuffer.length > outLen) {
                    throw new BadPaddingException("Output buffer too small");
                }
                System.arraycopy(tmpBuffer, 0, out, outOfs, tmpBuffer.length);
                n = tmpBuffer.length;
                break;
            case MODE_VERIFY:
                n = p11.C_VerifyRecover
                        (session.id(), buffer, 0, bufOfs, out, outOfs, outLen);
                break;
            default:
                throw new ProviderException("internal error");
            }
            return n;
        } catch (PKCS11Exception e) {
            throw (BadPaddingException)new BadPaddingException
                ("doFinal() failed").initCause(e);
        } finally {
            initialized = false;
            session = token.releaseSession(session);
        }
    }

    // see JCE spec
    protected byte[] engineUpdate(byte[] in, int inOfs, int inLen) {
        implUpdate(in, inOfs, inLen);
        return B0;
    }

    // see JCE spec
    protected int engineUpdate(byte[] in, int inOfs, int inLen,
            byte[] out, int outOfs) throws ShortBufferException {
        implUpdate(in, inOfs, inLen);
        return 0;
    }

    // see JCE spec
    protected byte[] engineDoFinal(byte[] in, int inOfs, int inLen)
            throws IllegalBlockSizeException, BadPaddingException {
        implUpdate(in, inOfs, inLen);
        int n = implDoFinal(buffer, 0, buffer.length);
        byte[] out = new byte[n];
        System.arraycopy(buffer, 0, out, 0, n);
        return out;
    }

    // see JCE spec
    protected int engineDoFinal(byte[] in, int inOfs, int inLen,
            byte[] out, int outOfs) throws ShortBufferException,
            IllegalBlockSizeException, BadPaddingException {
        implUpdate(in, inOfs, inLen);
        return implDoFinal(out, outOfs, out.length - outOfs);
    }

    private byte[] doFinal() throws BadPaddingException,
            IllegalBlockSizeException {
        byte[] t = new byte[2048];
        int n = implDoFinal(t, 0, t.length);
        byte[] out = new byte[n];
        System.arraycopy(t, 0, out, 0, n);
        return out;
    }

    // see JCE spec
    protected byte[] engineWrap(Key key) throws InvalidKeyException,
            IllegalBlockSizeException {
        String keyAlg = key.getAlgorithm();
        P11Key sKey = null;
        try {
            // The conversion may fail, e.g. trying to wrap an AES key on
            // a token that does not support AES, or when the key size is
            // not within the range supported by the token.
            sKey = P11SecretKeyFactory.convertKey(token, key, keyAlg);
        } catch (InvalidKeyException ike) {
            byte[] toBeWrappedKey = key.getEncoded();
            if (toBeWrappedKey == null) {
                throw new InvalidKeyException
                        ("wrap() failed, no encoding available", ike);
            }
            // Directly encrypt the key encoding when key conversion failed
            implInit(Cipher.ENCRYPT_MODE, p11Key);
            implUpdate(toBeWrappedKey, 0, toBeWrappedKey.length);
            try {
                return doFinal();
            } catch (BadPaddingException bpe) {
                // should not occur
                throw new InvalidKeyException("wrap() failed", bpe);
            } finally {
                // Restore original mode
                implInit(Cipher.WRAP_MODE, p11Key);
            }
        }
        Session s = null;
        try {
            s = token.getOpSession();
            return token.p11.C_WrapKey(s.id(), new CK_MECHANISM(mechanism),
                p11Key.keyID, sKey.keyID);
        } catch (PKCS11Exception e) {
            throw new InvalidKeyException("wrap() failed", e);
        } finally {
            token.releaseSession(s);
        }
    }

    // see JCE spec
    protected Key engineUnwrap(byte[] wrappedKey, String algorithm,
            int type) throws InvalidKeyException, NoSuchAlgorithmException {

        // XXX implement unwrap using C_Unwrap() for all keys
        implInit(Cipher.DECRYPT_MODE, p11Key);
        if (wrappedKey.length > maxInputSize) {
            throw new InvalidKeyException("Key is too long for unwrapping");
        }
        implUpdate(wrappedKey, 0, wrappedKey.length);
        try {
            byte[] encoded = doFinal();
            return ConstructKeys.constructKey(encoded, algorithm, type);
        } catch (BadPaddingException e) {
            // should not occur
            throw new InvalidKeyException("Unwrapping failed", e);
        } catch (IllegalBlockSizeException e) {
            // should not occur, handled with length check above
            throw new InvalidKeyException("Unwrapping failed", e);
        }
    }

    // see JCE spec
    protected int engineGetKeySize(Key key) throws InvalidKeyException {
        int n = P11KeyFactory.convertKey(token, key, algorithm).length();
        return n;
    }
}

final class ConstructKeys {
    /**
     * Construct a public key from its encoding.
     *
     * @param encodedKey the encoding of a public key.
     *
     * @param encodedKeyAlgorithm the algorithm the encodedKey is for.
     *
     * @return a public key constructed from the encodedKey.
     */
    private static final PublicKey constructPublicKey(byte[] encodedKey,
            String encodedKeyAlgorithm)
            throws InvalidKeyException, NoSuchAlgorithmException {
        try {
            KeyFactory keyFactory =
                KeyFactory.getInstance(encodedKeyAlgorithm);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encodedKey);
            return keyFactory.generatePublic(keySpec);
        } catch (NoSuchAlgorithmException nsae) {
            throw new NoSuchAlgorithmException("No installed providers " +
                                               "can create keys for the " +
                                               encodedKeyAlgorithm +
                                               "algorithm", nsae);
        } catch (InvalidKeySpecException ike) {
            throw new InvalidKeyException("Cannot construct public key", ike);
        }
    }

    /**
     * Construct a private key from its encoding.
     *
     * @param encodedKey the encoding of a private key.
     *
     * @param encodedKeyAlgorithm the algorithm the wrapped key is for.
     *
     * @return a private key constructed from the encodedKey.
     */
    private static final PrivateKey constructPrivateKey(byte[] encodedKey,
            String encodedKeyAlgorithm) throws InvalidKeyException,
            NoSuchAlgorithmException {
        try {
            KeyFactory keyFactory =
                KeyFactory.getInstance(encodedKeyAlgorithm);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encodedKey);
            return keyFactory.generatePrivate(keySpec);
        } catch (NoSuchAlgorithmException nsae) {
            throw new NoSuchAlgorithmException("No installed providers " +
                                               "can create keys for the " +
                                               encodedKeyAlgorithm +
                                               "algorithm", nsae);
        } catch (InvalidKeySpecException ike) {
            throw new InvalidKeyException("Cannot construct private key", ike);
        }
    }

    /**
     * Construct a secret key from its encoding.
     *
     * @param encodedKey the encoding of a secret key.
     *
     * @param encodedKeyAlgorithm the algorithm the secret key is for.
     *
     * @return a secret key constructed from the encodedKey.
     */
    private static final SecretKey constructSecretKey(byte[] encodedKey,
            String encodedKeyAlgorithm) {
        return new SecretKeySpec(encodedKey, encodedKeyAlgorithm);
    }

    static final Key constructKey(byte[] encoding, String keyAlgorithm,
            int keyType) throws InvalidKeyException, NoSuchAlgorithmException {
        switch (keyType) {
        case Cipher.SECRET_KEY:
            return constructSecretKey(encoding, keyAlgorithm);
        case Cipher.PRIVATE_KEY:
            return constructPrivateKey(encoding, keyAlgorithm);
        case Cipher.PUBLIC_KEY:
            return constructPublicKey(encoding, keyAlgorithm);
        default:
            throw new InvalidKeyException("Unknown keytype " + keyType);
        }
    }
}
