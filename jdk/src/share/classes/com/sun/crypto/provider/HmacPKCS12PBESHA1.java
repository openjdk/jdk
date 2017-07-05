/*
 * Copyright (c) 2003, 2009, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.crypto.provider;

import java.util.Arrays;
import java.nio.ByteBuffer;

import javax.crypto.MacSpi;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import java.security.*;
import java.security.spec.*;

/**
 * This is an implementation of the HMAC-PBESHA1 algorithm as defined
 * in PKCS#12 v1.0 standard.
 *
 * @author Valerie Peng
 */
public final class HmacPKCS12PBESHA1 extends MacSpi implements Cloneable {

    private HmacCore hmac = null;
    private static final int SHA1_BLOCK_LENGTH = 64;

    /**
     * Standard constructor, creates a new HmacSHA1 instance.
     */
    public HmacPKCS12PBESHA1() throws NoSuchAlgorithmException {
        this.hmac = new HmacCore(MessageDigest.getInstance("SHA1"),
                                 SHA1_BLOCK_LENGTH);
    }

    /**
     * Returns the length of the HMAC in bytes.
     *
     * @return the HMAC length in bytes.
     */
    protected int engineGetMacLength() {
        return hmac.getDigestLength();
    }

    /**
     * Initializes the HMAC with the given secret key and algorithm parameters.
     *
     * @param key the secret key.
     * @param params the algorithm parameters.
     *
     * @exception InvalidKeyException if the given key is inappropriate for
     * initializing this MAC.
     u* @exception InvalidAlgorithmParameterException if the given algorithm
     * parameters are inappropriate for this MAC.
     */
    protected void engineInit(Key key, AlgorithmParameterSpec params)
        throws InvalidKeyException, InvalidAlgorithmParameterException {
        char[] passwdChars;
        byte[] salt = null;
        int iCount = 0;
        if (key instanceof javax.crypto.interfaces.PBEKey) {
            javax.crypto.interfaces.PBEKey pbeKey =
                (javax.crypto.interfaces.PBEKey) key;
            passwdChars = pbeKey.getPassword();
            salt = pbeKey.getSalt(); // maybe null if unspecified
            iCount = pbeKey.getIterationCount(); // maybe 0 if unspecified
        } else if (key instanceof SecretKey) {
            byte[] passwdBytes = key.getEncoded();
            if ((passwdBytes == null) ||
                !(key.getAlgorithm().regionMatches(true, 0, "PBE", 0, 3))) {
                throw new InvalidKeyException("Missing password");
            }
            passwdChars = new char[passwdBytes.length];
            for (int i=0; i<passwdChars.length; i++) {
                passwdChars[i] = (char) (passwdBytes[i] & 0x7f);
            }
        } else {
            throw new InvalidKeyException("SecretKey of PBE type required");
        }
        if (params == null) {
            // generate default for salt and iteration count if necessary
            if (salt == null) {
                salt = new byte[20];
                SunJCE.RANDOM.nextBytes(salt);
            }
            if (iCount == 0) iCount = 100;
        } else if (!(params instanceof PBEParameterSpec)) {
            throw new InvalidAlgorithmParameterException
                ("PBEParameterSpec type required");
        } else {
            PBEParameterSpec pbeParams = (PBEParameterSpec) params;
            // make sure the parameter values are consistent
            if (salt != null) {
                if (!Arrays.equals(salt, pbeParams.getSalt())) {
                    throw new InvalidAlgorithmParameterException
                        ("Inconsistent value of salt between key and params");
                }
            } else {
                salt = pbeParams.getSalt();
            }
            if (iCount != 0) {
                if (iCount != pbeParams.getIterationCount()) {
                    throw new InvalidAlgorithmParameterException
                        ("Different iteration count between key and params");
                }
            } else {
                iCount = pbeParams.getIterationCount();
            }
        }
        // For security purpose, we need to enforce a minimum length
        // for salt; just require the minimum salt length to be 8-byte
        // which is what PKCS#5 recommends and openssl does.
        if (salt.length < 8) {
            throw new InvalidAlgorithmParameterException
                ("Salt must be at least 8 bytes long");
        }
        if (iCount <= 0) {
            throw new InvalidAlgorithmParameterException
                ("IterationCount must be a positive number");
        }
        byte[] derivedKey = PKCS12PBECipherCore.derive(passwdChars, salt,
            iCount, hmac.getDigestLength(), PKCS12PBECipherCore.MAC_KEY);
        SecretKey cipherKey = new SecretKeySpec(derivedKey, "HmacSHA1");
        hmac.init(cipherKey, null);
    }

    /**
     * Processes the given byte.
     *
     * @param input the input byte to be processed.
     */
    protected void engineUpdate(byte input) {
        hmac.update(input);
    }

    /**
     * Processes the first <code>len</code> bytes in <code>input</code>,
     * starting at <code>offset</code>.
     *
     * @param input the input buffer.
     * @param offset the offset in <code>input</code> where the input starts.
     * @param len the number of bytes to process.
     */
    protected void engineUpdate(byte input[], int offset, int len) {
        hmac.update(input, offset, len);
    }

    protected void engineUpdate(ByteBuffer input) {
        hmac.update(input);
    }

    /**
     * Completes the HMAC computation and resets the HMAC for further use,
     * maintaining the secret key that the HMAC was initialized with.
     *
     * @return the HMAC result.
     */
    protected byte[] engineDoFinal() {
        return hmac.doFinal();
    }

    /**
     * Resets the HMAC for further use, maintaining the secret key that the
     * HMAC was initialized with.
     */
    protected void engineReset() {
        hmac.reset();
    }

    /*
     * Clones this object.
     */
    public Object clone() {
        HmacPKCS12PBESHA1 that = null;
        try {
            that = (HmacPKCS12PBESHA1)super.clone();
            that.hmac = (HmacCore)this.hmac.clone();
        } catch (CloneNotSupportedException e) {
        }
        return that;
    }
}
