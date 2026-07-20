/*
 * Copyright (c) 2023, 2025, Red Hat, Inc.
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

import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidKeySpecException;
import javax.crypto.BadPaddingException;
import javax.crypto.CipherSpi;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.PBEKeySpec;

import sun.security.jca.JCAUtil;
import static sun.security.pkcs11.wrapper.PKCS11Constants.*;
import sun.security.pkcs11.wrapper.PKCS11Exception;
import sun.security.util.PBEUtil;

final class P11PBECipher extends CipherSpi {
    private final Token token;
    private final String pbeAlg;
    private final P11Cipher cipher;
    private final int blkSize;
    private final P11SecretKeyFactory.PBEKeyInfo svcPbeKi;
    private final PBEUtil.PBES2Params pbes2Params = new PBEUtil.PBES2Params();

    P11PBECipher(Token token, String pbeAlg, long cipherMech)
                    throws PKCS11Exception, NoSuchAlgorithmException {
        super();
        String cipherTrans;
        if (cipherMech == CKM_AES_CBC_PAD || cipherMech == CKM_AES_CBC) {
            cipherTrans = "AES/CBC/PKCS5Padding";
        } else {
            throw new NoSuchAlgorithmException(
                    "Cipher transformation not supported.");
        }
        cipher = new P11Cipher(token, cipherTrans, cipherMech);
        blkSize = cipher.engineGetBlockSize();
        this.pbeAlg = pbeAlg;
        svcPbeKi = P11SecretKeyFactory.getPBEKeyInfo(pbeAlg);
        assert svcPbeKi != null : "algorithm must be in KeyInfo map";
        this.token = token;
    }

    // see JCE spec
    @Override
    protected void engineSetMode(String mode)
            throws NoSuchAlgorithmException {
        cipher.engineSetMode(mode);
    }

    // see JCE spec
    @Override
    protected void engineSetPadding(String padding)
            throws NoSuchPaddingException {
        cipher.engineSetPadding(padding);
    }

    // see JCE spec
    @Override
    protected int engineGetBlockSize() {
        return cipher.engineGetBlockSize();
    }

    // see JCE spec
    @Override
    protected int engineGetOutputSize(int inputLen) {
        return cipher.engineGetOutputSize(inputLen);
    }

    // see JCE spec
    @Override
    protected byte[] engineGetIV() {
        return cipher.engineGetIV();
    }

    // see JCE spec
    @Override
    protected AlgorithmParameters engineGetParameters() {
        return pbes2Params.getAlgorithmParameters(blkSize, pbeAlg,
                P11Util.getSunJceProvider(), JCAUtil.getSecureRandom());
    }

    // see JCE spec
    @Override
    protected void engineInit(int opmode, Key key,
            SecureRandom random) throws InvalidKeyException {
        try {
            engineInit(opmode, key, (AlgorithmParameterSpec) null, random);
        } catch (InvalidAlgorithmParameterException e) {
            throw new InvalidKeyException("requires PBE parameters", e);
        }
    }

    // see JCE spec
    @Override
    protected void engineInit(int opmode, Key key,
            AlgorithmParameterSpec params, SecureRandom random)
                    throws InvalidKeyException,
                    InvalidAlgorithmParameterException {
        // do key derivation, use P11SecretKeyFactory
        PBEKeySpec pbeSpec = pbes2Params.getPBEKeySpec(
                blkSize, svcPbeKi.keyLen, opmode, key, params, random);
        try {
            P11Key.P11PBKDFKey derivedKey =
                    P11SecretKeyFactory.derivePBEKey(
                    token, pbeSpec, svcPbeKi);
            // The internal Cipher service uses the token where the
            // derived key lives so there won't be any need to re-derive
            // and use the password. The key cannot be accessed out of this
            // class.
            derivedKey.clearPassword();
            key = derivedKey;
        } catch (InvalidKeySpecException e) {
            throw new InvalidKeyException(e);
        } finally {
            pbeSpec.clearPassword();
        }
        cipher.engineInit(opmode, key, pbes2Params.getIvSpec(), random);
    }

    // see JCE spec
    @Override
    protected void engineInit(int opmode, Key key,
            AlgorithmParameters params, SecureRandom random)
                    throws InvalidKeyException,
                    InvalidAlgorithmParameterException {
        engineInit(opmode, key, PBEUtil.PBES2Params.getParameterSpec(params),
                random);
    }

    // see JCE spec
    @Override
    protected byte[] engineUpdate(byte[] input, int inputOffset,
            int inputLen) {
        return cipher.engineUpdate(input, inputOffset, inputLen);
    }

    // see JCE spec
    @Override
    protected int engineUpdate(byte[] input, int inputOffset,
            int inputLen, byte[] output, int outputOffset)
                    throws ShortBufferException {
        return cipher.engineUpdate(input, inputOffset, inputLen,
                output, outputOffset);
    }

    // see JCE spec
    @Override
    protected byte[] engineDoFinal(byte[] input, int inputOffset,
            int inputLen)
                    throws IllegalBlockSizeException, BadPaddingException {
        return cipher.engineDoFinal(input, inputOffset, inputLen);
    }

    // see JCE spec
    @Override
    protected int engineDoFinal(byte[] input, int inputOffset,
            int inputLen, byte[] output, int outputOffset)
                    throws ShortBufferException, IllegalBlockSizeException,
                    BadPaddingException {
        return cipher.engineDoFinal(input, inputOffset, inputLen, output,
                outputOffset);
    }

    // see JCE spec
    @Override
    protected int engineGetKeySize(Key key) {
        // It's guaranteed that when engineInit succeeds, the key length
        // for the underlying cipher is equal to the PBE service key length.
        // Otherwise, initialization fails.
        return svcPbeKi.keyLen;
    }

}
