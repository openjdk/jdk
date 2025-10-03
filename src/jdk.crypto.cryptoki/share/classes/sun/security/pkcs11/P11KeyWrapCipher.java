/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Locale;

import java.security.*;
import java.security.spec.*;

import javax.crypto.*;
import javax.crypto.spec.*;

import java.util.HexFormat;

import jdk.internal.access.JavaNioAccess;
import jdk.internal.access.SharedSecrets;
import sun.nio.ch.DirectBuffer;
import sun.security.jca.JCAUtil;
import sun.security.pkcs11.wrapper.*;
import static sun.security.pkcs11.wrapper.PKCS11Constants.*;
import static sun.security.pkcs11.wrapper.PKCS11Exception.RV.*;
import static sun.security.pkcs11.TemplateManager.*;

/**
 * P11 KeyWrap Cipher implementation class for native impl which only support
 * single part encryption/decryption through C_Encrypt/C_Decrypt() and
 * key wrap/unwrap through C_WrapKey/C_UnwrapKey() calls.
 * This class currently supports only AES cipher in KW and KWP modes.
 *
 * For multi-part encryption/decryption, this class has to buffer data until
 * doFinal() is called.
 *
 * @since 18
 */
final class P11KeyWrapCipher extends CipherSpi {

    private static final JavaNioAccess NIO_ACCESS = SharedSecrets.getJavaNioAccess();

    private static final int BLK_SIZE = 8;

    // supported mode and padding with AES cipher
    private enum KeyWrapType {
        KW_NOPADDING("KW", "NOPADDING"),
        KW_PKCS5PADDING("KW", "PKCS5PADDING"),
        KWP_NOPADDING("KWP", "NOPADDING");

        private final String mode;
        private final String padding;
        private final byte[] defIv;

        KeyWrapType(String mode, String padding) {
            this.mode = mode;
            this.padding = padding;
            if (mode.equalsIgnoreCase("KW")) {
                this.defIv = new byte[] {
                        (byte)0xA6, (byte)0xA6, (byte)0xA6, (byte)0xA6,
                        (byte)0xA6, (byte)0xA6, (byte)0xA6, (byte)0xA6
                };
            } else {
                this.defIv = new byte[] {
                        (byte)0xA6, (byte)0x59, (byte)0x59, (byte)0xA6
                };
            }
        }
    }

    // token instance
    private final Token token;

    // mechanism id
    private final long mechanism;

    // type of this KeyWrap cipher, one of Transformation enum above
    private final KeyWrapType type;

    // acceptable key size in bytes, -1 if more than 1 key sizes are accepted
    private final int fixedKeySize;

    // associated session, if any
    private Session session = null;

    // key, if init() was called
    private P11Key p11Key = null;

    // flag indicating whether an operation is initialized
    private boolean initialized = false;

    private int opmode = Cipher.ENCRYPT_MODE;

    // parameters
    private byte[] iv = null;
    private SecureRandom random = JCAUtil.getSecureRandom();

    // dataBuffer for storing enc/dec data; cleared upon doFinal calls
    private final ByteArrayOutputStream dataBuffer = new ByteArrayOutputStream();

    P11KeyWrapCipher(Token token, String algorithm, long mechanism)
            throws PKCS11Exception, NoSuchAlgorithmException {
        super();
        this.token = token;
        this.mechanism = mechanism;

        // javax.crypto.Cipher ensures algoParts.length == 3
        String[] algoParts = algorithm.split("/");
        if (algoParts[0].startsWith("AES")) {
            int index = algoParts[0].indexOf('_');
            fixedKeySize = (index == -1 ? -1 :
                // should be well-formed since we specify what we support
                Integer.parseInt(algoParts[0].substring(index+1)) >> 3);
            try {
                this.type = KeyWrapType.valueOf(algoParts[1].toUpperCase() +
                        "_" + algoParts[2].toUpperCase());
            } catch (IllegalArgumentException iae) {
                throw new NoSuchAlgorithmException("Unsupported algorithm " +
                        algorithm);
            }
        } else {
            throw new NoSuchAlgorithmException("Unsupported algorithm " +
                    algorithm);
        }
    }

    @Override
    protected void engineSetMode(String mode) throws NoSuchAlgorithmException {
        if (!mode.toUpperCase(Locale.ENGLISH).equals(type.mode)) {
            throw new NoSuchAlgorithmException("Unsupported mode " + mode);
        }
    }

    // see JCE spec
    @Override
    protected void engineSetPadding(String padding)
            throws NoSuchPaddingException {
        if (!padding.toUpperCase(Locale.ENGLISH).equals(type.padding)) {
            throw new NoSuchPaddingException("Unsupported padding " + padding);
        }
    }

    // see JCE spec
    @Override
    protected int engineGetBlockSize() {
        return BLK_SIZE;
    }

    // see JCE spec
    @Override
    protected int engineGetOutputSize(int inputLen) {
        return doFinalLength(inputLen);
    }

    // see JCE spec
    @Override
    protected byte[] engineGetIV() {
        return (iv == null) ? null : iv.clone();
    }

    // see JCE spec
    protected AlgorithmParameters engineGetParameters() {
        // KW and KWP uses but not require parameters, return the default
        // IV when no IV is supplied by caller
        byte[] iv = (this.iv == null ? type.defIv : this.iv);

        AlgorithmParameterSpec spec = new IvParameterSpec(iv);
        try {
            AlgorithmParameters params = AlgorithmParameters.getInstance("AES");
            params.init(spec);
            return params;
        } catch (GeneralSecurityException e) {
            // NoSuchAlgorithmException, NoSuchProviderException
            // InvalidParameterSpecException
            throw new ProviderException("Could not encode parameters", e);
        }
    }

    // see JCE spec
    protected void engineInit(int opmode, Key key, SecureRandom sr)
            throws InvalidKeyException {
        try {
            implInit(opmode, key, null, sr);
        } catch (InvalidAlgorithmParameterException e) {
            throw new InvalidKeyException("init() failed", e);
        }
    }

    // see JCE spec
    protected void engineInit(int opmode, Key key,
            AlgorithmParameterSpec params, SecureRandom sr)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        if (params != null && !(params instanceof IvParameterSpec)) {
            throw new InvalidAlgorithmParameterException
                    ("Only IvParameterSpec is supported");
        }

        byte[] ivValue = (params == null ? null :
                ((IvParameterSpec)params).getIV());

        implInit(opmode, key, ivValue, sr);
    }

    // see JCE spec
    protected void engineInit(int opmode, Key key, AlgorithmParameters params,
            SecureRandom sr)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        AlgorithmParameterSpec paramSpec = null;
        if (params != null) {
            try {
                paramSpec = params.getParameterSpec(IvParameterSpec.class);
            } catch (InvalidParameterSpecException ex) {
                throw new InvalidAlgorithmParameterException(ex);
            }
        }
        engineInit(opmode, key, paramSpec, sr);
    }

    // actual init() implementation
    private void implInit(int opmode, Key key, byte[] iv, SecureRandom sr)
        throws InvalidKeyException, InvalidAlgorithmParameterException {
        reset(true);
        if (fixedKeySize != -1) {
            int keySize;
            if (key instanceof P11Key) {
                keySize = ((P11Key) key).length() >> 3;
            } else {
                byte[] encoding = key.getEncoded();
                Arrays.fill(encoding, (byte) 0);
                keySize = encoding.length;
            }
            if (keySize != fixedKeySize) {
                throw new InvalidKeyException("Key size is invalid");
            }
        }

        P11Key newKey = P11SecretKeyFactory.convertKey(token, key, "AES");
        this.opmode = opmode;

        if (iv == null) {
            iv = type.defIv;
        } else {
            if (type == KeyWrapType.KWP_NOPADDING &&
                    !Arrays.equals(iv, type.defIv)) {
                throw new InvalidAlgorithmParameterException
                        ("For KWP mode, IV must has value 0x" +
                        HexFormat.of().withUpperCase().formatHex(type.defIv));
            } else if (iv.length != type.defIv.length) {
                throw new InvalidAlgorithmParameterException
                        ("Wrong IV length, expected " + type.defIv.length +
                        " but got " + iv.length);
            }
        }
        this.iv = iv;
        this.p11Key = newKey;
        if (sr != null) {
            this.random = sr;
        }
        try {
            initialize();
        } catch (PKCS11Exception e) {
            if (e.match(CKR_MECHANISM_PARAM_INVALID)) {
                throw new InvalidAlgorithmParameterException("Bad params", e);
            }
            throw new InvalidKeyException("Could not initialize cipher", e);
        }
    }

    private void cancelOperation() {
        token.ensureValid();

        if (P11Util.trySessionCancel(token, session,
                (opmode == Cipher.ENCRYPT_MODE ? CKF_ENCRYPT : CKF_DECRYPT))) {
            return;
        }

        // cancel by finishing operations; avoid killSession as some
        // hardware vendors may require re-login
        byte[] in = dataBuffer.toByteArray();
        int inLen = in.length;
        int bufLen = doFinalLength(0);
        byte[] buffer = new byte[bufLen];

        try {
            if (opmode == Cipher.ENCRYPT_MODE) {
                token.p11.C_Encrypt(session.id(), 0, in, 0, inLen,
                        0, buffer, 0, bufLen);
            } else if (opmode == Cipher.DECRYPT_MODE) {
                token.p11.C_Decrypt(session.id(), 0, in, 0, inLen,
                        0, buffer, 0, bufLen);
            }
        } catch (PKCS11Exception e) {
            if (e.match(CKR_OPERATION_NOT_INITIALIZED)) {
                // Cancel Operation may be invoked after an error on a PKCS#11
                // call. If the operation inside the token was already
                // cancelled, do not fail here. This is part of a defensive
                // mechanism for PKCS#11 libraries that do not strictly follow
                // the standard.
                return;
            }
            // ignore failure for en/decryption since it's likely to fail
            // due to the minimum length requirement
        }
    }

    private void ensureInitialized() throws PKCS11Exception {
        if (!initialized) {
            initialize();
        }
    }

    private void initialize() throws PKCS11Exception {
        if (p11Key == null) {
            throw new ProviderException("Operation cannot be performed without"
                    + " calling engineInit first");
        }

        token.ensureValid();
        dataBuffer.reset();

        if (opmode == Cipher.ENCRYPT_MODE || opmode == Cipher.DECRYPT_MODE) {
            long p11KeyID = p11Key.getKeyID();
            try {
                CK_MECHANISM mechWithParams = new CK_MECHANISM(mechanism, iv);

                if (session == null) {
                    session = token.getOpSession();
                }
                switch (opmode) {
                    case Cipher.ENCRYPT_MODE -> token.p11.C_EncryptInit(session.id(), mechWithParams,
                            p11KeyID);
                    case Cipher.DECRYPT_MODE -> token.p11.C_DecryptInit(session.id(), mechWithParams,
                            p11KeyID);
                }
            } catch (PKCS11Exception e) {
                session = token.releaseSession(session);
                throw e;
            } finally {
                p11Key.releaseKeyID();
            }
        }
        initialized = true;
    }

    // if doFinal(inLen) is called, how big does the output buffer have to be?
    private int doFinalLength(int inLen) {
        if (inLen < 0) {
            throw new ProviderException("Invalid negative input length");
        }

        int result = inLen + dataBuffer.size();
        boolean encrypt = (opmode == Cipher.ENCRYPT_MODE ||
            opmode == Cipher.WRAP_MODE);
        if (encrypt) {
            if (type == KeyWrapType.KW_PKCS5PADDING) {
                // add potential pad length, i.e. 1-8
                result += (BLK_SIZE - (result & (BLK_SIZE - 1)));
            } else if (type == KeyWrapType.KWP_NOPADDING &&
                    (result & (BLK_SIZE - 1)) != 0) {
                // add potential pad length, i.e. 0-7
                result += (BLK_SIZE - (result & (BLK_SIZE - 1)));
            }
            result += BLK_SIZE; // add the leading block including the ICV
        } else {
            result -= BLK_SIZE; // minus the leading block including the ICV
        }
        return (Math.max(result, 0));
    }

    // reset the states to the pre-initialized values
    // set initialized to false, cancel operation, release session, and
    // reset dataBuffer
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
            session = token.releaseSession(session);
            dataBuffer.reset();
        }
    }

    // see JCE spec
    protected byte[] engineUpdate(byte[] in, int inOfs, int inLen) {
        int n = implUpdate(in, inOfs, inLen);
        return new byte[0];
    }

    // see JCE spec
    protected int engineUpdate(byte[] in, int inOfs, int inLen, byte[] out,
            int outOfs) throws ShortBufferException {
        implUpdate(in, inOfs, inLen);
        return 0;
    }

    // see JCE spec
    @Override
    protected int engineUpdate(ByteBuffer inBuffer, ByteBuffer outBuffer)
            throws ShortBufferException {
        implUpdate(inBuffer);
        return 0;
    }

    // see JCE spec
    protected byte[] engineDoFinal(byte[] in, int inOfs, int inLen)
            throws IllegalBlockSizeException, BadPaddingException {
        int maxOutLen = doFinalLength(inLen);
        try {
            byte[] out = new byte[maxOutLen];
            int n = engineDoFinal(in, inOfs, inLen, out, 0);
            return P11Util.convert(out, 0, n);
        } catch (ShortBufferException e) {
            // convert since the output length is calculated by doFinalLength()
            throw new ProviderException(e);
        }
    }
    // see JCE spec
    protected int engineDoFinal(byte[] in, int inOfs, int inLen, byte[] out,
            int outOfs) throws ShortBufferException, IllegalBlockSizeException,
            BadPaddingException {
        return implDoFinal(in, inOfs, inLen, out, outOfs, out.length - outOfs);
    }

    // see JCE spec
    @Override
    protected int engineDoFinal(ByteBuffer inBuffer, ByteBuffer outBuffer)
            throws ShortBufferException, IllegalBlockSizeException,
            BadPaddingException {
        return implDoFinal(inBuffer, outBuffer);
    }

    private int implUpdate(byte[] in, int inOfs, int inLen) {
        if (inLen > 0) {
            try {
                ensureInitialized();
            } catch (PKCS11Exception e) {
                reset(false);
                throw new ProviderException("update() failed", e);
            }
            dataBuffer.write(in, inOfs, inLen);
        }
        // always 0 as NSS only supports single-part encryption/decryption
        return 0;
    }

    private int implUpdate(ByteBuffer inBuf) {
        int inLen = inBuf.remaining();
        if (inLen > 0) {
            try {
                ensureInitialized();
            } catch (PKCS11Exception e) {
                reset(false);
                throw new ProviderException("update() failed", e);
            }
            byte[] data = new byte[inLen];
            inBuf.get(data);
            dataBuffer.write(data, 0, data.length);
        }
        // always 0 as NSS only supports single-part encryption/decryption
        return 0;
    }

    private int implDoFinal(byte[] in, int inOfs, int inLen,
            byte[] out, int outOfs, int outLen)
            throws ShortBufferException, IllegalBlockSizeException,
            BadPaddingException {
        int requiredOutLen = doFinalLength(inLen);
        if (outLen < requiredOutLen) {
            throw new ShortBufferException();
        }

        boolean doCancel = true;
        int k = 0;
        try {
            ensureInitialized();

            if (dataBuffer.size() > 0) {
                if (in != null && inLen > 0) {
                    dataBuffer.write(in, inOfs, inLen);
                }
                in = dataBuffer.toByteArray();
                inOfs = 0;
                inLen = in.length;
            }

            if (opmode == Cipher.ENCRYPT_MODE) {
                k = token.p11.C_Encrypt(session.id(), 0, in, inOfs, inLen,
                        0, out, outOfs, outLen);
                doCancel = false;
            } else {
                // Special handling to match SunJCE provider behavior
                if (inLen == 0) {
                    return 0;
                }
                k = token.p11.C_Decrypt(session.id(), 0, in, inOfs, inLen,
                        0, out, outOfs, outLen);
                doCancel = false;
            }
        } catch (PKCS11Exception e) {
            // As per the PKCS#11 standard, C_Encrypt and C_Decrypt may only
            // keep the operation active on CKR_BUFFER_TOO_SMALL errors or
            // successful calls to determine the output length. However,
            // these cases are not expected here because the output length
            // is checked in the OpenJDK side before making the PKCS#11 call.
            // Thus, doCancel can safely be 'false'.
            doCancel = false;
            handleEncException("doFinal() failed", e);
        } finally {
            reset(doCancel);
        }
        return k;
    }

    private int implDoFinal(ByteBuffer inBuffer, ByteBuffer outBuffer)
            throws ShortBufferException, IllegalBlockSizeException,
            BadPaddingException {
        int outLen = outBuffer.remaining();
        int inLen = inBuffer.remaining();

        int requiredOutLen = doFinalLength(inLen);
        if (outLen < requiredOutLen) {
            throw new ShortBufferException();
        }

        boolean doCancel = true;
        int k = 0;
        NIO_ACCESS.acquireSession(inBuffer);
        try {
            NIO_ACCESS.acquireSession(outBuffer);
            try {
                try {
                    ensureInitialized();

                    long inAddr = 0;
                    byte[] in = null;
                    int inOfs = 0;

                    if (dataBuffer.size() > 0) {
                        if (inBuffer != null && inLen > 0) {
                            byte[] temp = new byte[inLen];
                            inBuffer.get(temp);
                            dataBuffer.write(temp, 0, temp.length);
                        }

                        in = dataBuffer.toByteArray();
                        inOfs = 0;
                        inLen = in.length;
                    } else {
                        if (inBuffer instanceof DirectBuffer) {
                            inAddr = NIO_ACCESS.getBufferAddress(inBuffer);
                            inOfs = inBuffer.position();
                        } else {
                            if (inBuffer.hasArray()) {
                                in = inBuffer.array();
                                inOfs = inBuffer.position() + inBuffer.arrayOffset();
                            } else {
                                in = new byte[inLen];
                                inBuffer.get(in);
                            }
                        }
                    }
                    long outAddr = 0;
                    byte[] outArray = null;
                    int outOfs = 0;
                    if (outBuffer instanceof DirectBuffer) {
                        outAddr = NIO_ACCESS.getBufferAddress(outBuffer);
                        outOfs = outBuffer.position();
                    } else {
                        if (outBuffer.hasArray()) {
                            outArray = outBuffer.array();
                            outOfs = outBuffer.position() + outBuffer.arrayOffset();
                        } else {
                            outArray = new byte[outLen];
                        }
                    }

                    if (opmode == Cipher.ENCRYPT_MODE) {
                        k = token.p11.C_Encrypt(session.id(), inAddr, in, inOfs, inLen,
                                outAddr, outArray, outOfs, outLen);
                        doCancel = false;
                    } else {
                        // Special handling to match SunJCE provider behavior
                        if (inLen == 0) {
                            return 0;
                        }
                        k = token.p11.C_Decrypt(session.id(), inAddr, in, inOfs, inLen,
                                outAddr, outArray, outOfs, outLen);
                        doCancel = false;
                    }
                    inBuffer.position(inBuffer.limit());
                    outBuffer.position(outBuffer.position() + k);
                } catch (PKCS11Exception e) {
                    // As per the PKCS#11 standard, C_Encrypt and C_Decrypt may only
                    // keep the operation active on CKR_BUFFER_TOO_SMALL errors or
                    // successful calls to determine the output length. However,
                    // these cases are not expected here because the output length
                    // is checked in the OpenJDK side before making the PKCS#11 call.
                    // Thus, doCancel can safely be 'false'.
                    doCancel = false;
                    handleEncException("doFinal() failed", e);
                } finally {
                    reset(doCancel);
                }
            } finally {
                NIO_ACCESS.releaseSession(outBuffer);
            }
        } finally {
            NIO_ACCESS.releaseSession(inBuffer);
        }
        return k;
    }

    private void handleEncException(String msg, PKCS11Exception e)
            throws IllegalBlockSizeException, ShortBufferException,
            ProviderException {
        if (e.match(CKR_DATA_LEN_RANGE) ||
                e.match(CKR_ENCRYPTED_DATA_LEN_RANGE)) {
            throw (IllegalBlockSizeException)
                    (new IllegalBlockSizeException(msg).initCause(e));
        } else if (e.match(CKR_BUFFER_TOO_SMALL)) {
            throw (ShortBufferException)
                    (new ShortBufferException(msg).initCause(e));
        } else {
            throw new ProviderException(msg, e);
        }
    }

    // see JCE spec
    protected byte[] engineWrap(Key tbwKey) throws IllegalBlockSizeException,
            InvalidKeyException {
        try {
            ensureInitialized();
        } catch (PKCS11Exception e) {
            reset(false);
            throw new ProviderException("wrap() failed", e);
        }

        // convert the specified key into P11Key handle
        P11Key tbwP11Key = null;
        if (!(tbwKey instanceof P11Key)) {
            try {
                tbwP11Key = (tbwKey instanceof SecretKey ?
                        P11SecretKeyFactory.convertKey(token, tbwKey,
                                tbwKey.getAlgorithm()) :
                        P11KeyFactory.convertKey(token, tbwKey,
                                tbwKey.getAlgorithm()));
            } catch (ProviderException pe) {
                throw new InvalidKeyException("Cannot convert to PKCS11 key",
                        pe);
            } catch (InvalidKeyException ike) {
                // could be algorithms NOT supported by PKCS11 library
                // try single part encryption instead
            }
        } else {
            tbwP11Key = (P11Key) tbwKey;
        }

        long p11KeyID = 0;
        try {
            if (session == null) {
                session = token.getOpSession();
            }

            p11KeyID = p11Key.getKeyID();
            CK_MECHANISM mechWithParams = new CK_MECHANISM(mechanism, iv);
            if (tbwP11Key != null) {
                long tbwP11KeyID = tbwP11Key.getKeyID();
                try {
                    return token.p11.C_WrapKey(session.id(), mechWithParams,
                            p11KeyID, tbwP11KeyID);
                } finally {
                    tbwP11Key.releaseKeyID();
                }
            } else {
                byte[] in = tbwKey.getEncoded();
                try {
                    token.p11.C_EncryptInit(session.id(), mechWithParams,
                            p11KeyID);

                    int bufLen = doFinalLength(in.length);
                    byte[] buffer = new byte[bufLen];

                    token.p11.C_Encrypt(session.id(), 0, in, 0, in.length,
                            0, buffer, 0, bufLen);
                    return buffer;
                } finally {
                    Arrays.fill(in, (byte)0);
                }
            }
        } catch (PKCS11Exception e) {
            String msg = "wrap() failed";
            if (e.match(CKR_KEY_SIZE_RANGE) || e.match(CKR_DATA_LEN_RANGE)) {
                throw (IllegalBlockSizeException)
                        (new IllegalBlockSizeException(msg).initCause(e));
            } else if (e.match(CKR_KEY_NOT_WRAPPABLE) ||
                    e.match(CKR_KEY_UNEXTRACTABLE) ||
                    e.match(CKR_KEY_HANDLE_INVALID)) {
                throw new InvalidKeyException(msg, e);
            } else if (e.match(CKR_MECHANISM_INVALID)) {
                throw new UnsupportedOperationException(msg, e);
            } else {
                throw new ProviderException(msg, e);
            }
        } finally {
            if (p11KeyID != 0) p11Key.releaseKeyID();
            reset(false);
        }
    }

    // see JCE spec
    protected Key engineUnwrap(byte[] wrappedKey, String wrappedKeyAlgo,
            int wrappedKeyType)
            throws InvalidKeyException, NoSuchAlgorithmException {

        try {
            ensureInitialized();
        } catch (PKCS11Exception e) {
            reset(false);
            throw new ProviderException("unwrap() failed", e);
        }

        long keyClass;
        long keyType;
        switch (wrappedKeyType) {
            case Cipher.PRIVATE_KEY -> {
                keyClass = CKO_PRIVATE_KEY;
                keyType = P11KeyFactory.getPKCS11KeyType(wrappedKeyAlgo);
            }
            case Cipher.SECRET_KEY -> {
                keyClass = CKO_SECRET_KEY;
                keyType = P11SecretKeyFactory.getPKCS11KeyType(wrappedKeyAlgo);
            }
            case Cipher.PUBLIC_KEY -> throw new UnsupportedOperationException
                    ("cannot unwrap public keys");
            default -> // should never happen
                    throw new AssertionError();
        }

        CK_ATTRIBUTE[] attributes;
        try {
            attributes = new CK_ATTRIBUTE[] {
                    new CK_ATTRIBUTE(CKA_CLASS, keyClass),
                    new CK_ATTRIBUTE(CKA_KEY_TYPE, keyType),
            };
            attributes = token.getAttributes
                    (O_IMPORT, keyClass, keyType, attributes);
        } catch (PKCS11Exception e) {
            reset(false);
            throw new ProviderException("unwrap() failed", e);
        }

        CK_MECHANISM mechParams = new CK_MECHANISM(mechanism, iv);

        long p11KeyID = 0;
        try {
            if (session == null) {
                session = token.getOpSession();
            }

            p11KeyID = p11Key.getKeyID();
            long unwrappedKeyID = token.p11.C_UnwrapKey(session.id(),
                mechParams, p11KeyID, wrappedKey, attributes);

            return (switch(wrappedKeyType) {
                case Cipher.PRIVATE_KEY -> P11Key.privateKey
                    (session, unwrappedKeyID, wrappedKeyAlgo, -1, attributes);
                case Cipher.SECRET_KEY ->  P11Key.secretKey
                    (session, unwrappedKeyID, wrappedKeyAlgo, -1, attributes);
                default -> null;
            });
        } catch (PKCS11Exception e) {
            String msg = "unwrap() failed";
            if (e.match(CKR_UNWRAPPING_KEY_SIZE_RANGE) ||
                    e.match(CKR_WRAPPED_KEY_INVALID) ||
                    e.match(CKR_WRAPPED_KEY_LEN_RANGE) ||
                    e.match(CKR_UNWRAPPING_KEY_HANDLE_INVALID) ||
                    e.match(CKR_UNWRAPPING_KEY_TYPE_INCONSISTENT)) {
                throw new InvalidKeyException(msg, e);
            } else if (e.match(CKR_MECHANISM_INVALID)) {
                throw new UnsupportedOperationException(msg, e);
            } else {
                throw new ProviderException(msg, e);
            }
        } finally {
            if (p11KeyID != 0) p11Key.releaseKeyID();
            reset(false);
        }
    }

    // see JCE spec
    @Override
    protected int engineGetKeySize(Key key) throws InvalidKeyException {
        return P11SecretKeyFactory.convertKey(token, key, "AES").length();
    }
}
