/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package org.openjdk.bench.javax.crypto.full;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidParameterSpecException;


public class AESGCMBench extends CryptoBase {

    @Param({"AES/GCM/NoPadding"})
    private String algorithm;

    @Param({"128"})
    private int keyLength;

    @Param({""+16*1024})
    private int dataSize;

    byte[] data;
    byte[] encryptedData;
    private Cipher encryptCipher;
    private Cipher decryptCipher;
    SecretKeySpec ks;
    GCMParameterSpec gcm_spec;
    byte[] aad;
    byte[] iv;

    public static final int IV_BUFFER_SIZE = 32;
    public static final int IV_MODULO = IV_BUFFER_SIZE - 16;
    int iv_index = 0;

    private int next_iv_index() {
        int r = iv_index;
        iv_index = (iv_index + 1) % IV_MODULO;
        return r;
    }

    @Setup
    public void setup() throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException, InvalidAlgorithmParameterException, InvalidParameterSpecException {
        setupProvider();
        assert algorithm.split("/")[1].compareToIgnoreCase("GCM") == 0;

        byte[] keystring = fillSecureRandom(new byte[keyLength / 8]);
        ks = new SecretKeySpec(keystring, "AES");
        iv = fillSecureRandom(new byte[IV_BUFFER_SIZE]);
        gcm_spec = new GCMParameterSpec(96, iv, next_iv_index(), 16);
        aad = fillSecureRandom(new byte[5]);
        encryptCipher = makeCipher(prov, algorithm);
        encryptCipher.init(Cipher.ENCRYPT_MODE, ks, gcm_spec);
        encryptCipher.updateAAD(aad);
        decryptCipher = makeCipher(prov, algorithm);
        data = fillRandom(new byte[dataSize]);
        encryptedData = encryptCipher.doFinal(data);
    }

    @Benchmark
    public byte[] encrypt() throws BadPaddingException, IllegalBlockSizeException, InvalidAlgorithmParameterException, InvalidKeyException {
        gcm_spec = new GCMParameterSpec(96, iv, next_iv_index(), 16);
        encryptCipher.init(Cipher.ENCRYPT_MODE, ks, gcm_spec);
        encryptCipher.updateAAD(aad);
        return encryptCipher.doFinal(data);
    }

    @Benchmark
    public byte[] decrypt() throws BadPaddingException, IllegalBlockSizeException, InvalidParameterSpecException, InvalidAlgorithmParameterException, InvalidKeyException {
        decryptCipher.init(Cipher.DECRYPT_MODE, ks, encryptCipher.getParameters().getParameterSpec(GCMParameterSpec.class));
        decryptCipher.updateAAD(aad);
        return decryptCipher.doFinal(encryptedData);
    }

}
