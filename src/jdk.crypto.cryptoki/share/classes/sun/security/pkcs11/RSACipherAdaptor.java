/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.SignatureSpi;
import java.security.InvalidKeyException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidParameterException;
import java.security.ProviderException;
import java.security.SignatureException;
import java.security.spec.AlgorithmParameterSpec;
import javax.crypto.Cipher;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import sun.security.pkcs11.wrapper.PKCS11Exception;

/**
 * NONEwithRSA Signature implementation using the RSA/ECB/PKCS1Padding Cipher
 * implementation from SunPKCS11.
 */
public final class RSACipherAdaptor extends SignatureSpi {

    private final P11RSACipher c;
    private ByteArrayOutputStream verifyBuf;

    public RSACipherAdaptor(Token token, long mechanism) {
        try {
            c = new P11RSACipher(token, "", mechanism);
            c.engineSetPadding("pkcs1padding");
        } catch (PKCS11Exception | NoSuchPaddingException e) {
            // should not happen, but wrap and re-throw if it were to happen
            throw new ProviderException(e);
        }
    }

    @Override
    protected void engineInitVerify(PublicKey publicKey)
            throws InvalidKeyException {
        c.engineInit(Cipher.DECRYPT_MODE, publicKey, null);
        if (verifyBuf == null) {
            verifyBuf = new ByteArrayOutputStream(128);
        } else {
            verifyBuf.reset();
        }
    }

    @Override
    protected void engineInitSign(PrivateKey privateKey)
            throws InvalidKeyException {
        c.engineInit(Cipher.ENCRYPT_MODE, privateKey, null);
        verifyBuf = null;
    }

    @Override
    protected void engineInitSign(PrivateKey privateKey, SecureRandom random)
            throws InvalidKeyException {
        c.engineInit(Cipher.ENCRYPT_MODE, privateKey, random);
        verifyBuf = null;
    }

    @Override
    protected void engineUpdate(byte b) throws SignatureException {
        engineUpdate(new byte[] {b}, 0, 1);
    }

    @Override
    protected void engineUpdate(byte[] b, int off, int len)
            throws SignatureException {
        if (verifyBuf != null) {
            verifyBuf.write(b, off, len);
        } else {
            byte[] out = c.engineUpdate(b, off, len);
            if ((out != null) && (out.length != 0)) {
                throw new SignatureException
                       ("Cipher unexpectedly returned data");
            }
        }
    }

    @Override
    protected byte[] engineSign() throws SignatureException {
        try {
            return c.engineDoFinal(null, 0, 0);
        } catch (IllegalBlockSizeException | BadPaddingException e) {
           throw new SignatureException("doFinal() failed", e);
        }
    }

    @Override
    protected boolean engineVerify(byte[] sigBytes) throws SignatureException {
        try {
            byte[] out = c.engineDoFinal(sigBytes, 0, sigBytes.length);
            byte[] data = verifyBuf.toByteArray();
            verifyBuf.reset();
            return MessageDigest.isEqual(out, data);
        } catch (BadPaddingException e) {
            // e.g. wrong public key used
            // return false rather than throwing exception
            return false;
        } catch (IllegalBlockSizeException e) {
            throw new SignatureException("doFinal() failed", e);
        }
    }

    @Override
    protected void engineSetParameter(AlgorithmParameterSpec params)
            throws InvalidAlgorithmParameterException {
        if (params != null) {
            throw new InvalidParameterException("Parameters not supported");
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void engineSetParameter(String param, Object value)
            throws InvalidParameterException {
        throw new InvalidParameterException("Parameters not supported");
    }

    @Override
    @SuppressWarnings("deprecation")
    protected Object engineGetParameter(String param)
            throws InvalidParameterException {
        throw new InvalidParameterException("Parameters not supported");
    }
}
