/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.security.spec.AlgorithmParameterSpec;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * This is the common code for the AES/GCM and ChaCha20-Poly1305 performance
 * tests.  Encryption and decryption use byte[] as input and output buffers for
 * single and multi-part testing.
 *
 * The IV rotates through a set buffer and creates a new AlgorithmParameterSpec
 * for each encrypt benchmark operation.
 */

public abstract class BenchBase extends CryptoBase {
    // Defined by the test
    String algorithm;
    int keyLength = 256;

    // Default data sizes for full tests
    @Param({"1024", "1500", "4096", "16384"})
    int dataSize;

    static final int IV_BUFFER_SIZE = 36;
    public byte[] iv;
    public int iv_index = 0;
    private int updateLen = 0;

    private Cipher encryptCipher, decryptCipher;
    private byte[] encryptedData, in, out;
    private SecretKeySpec ks;
    // Used for decryption to avoid repeated getParameter() calls
    private AlgorithmParameterSpec spec;

    abstract AlgorithmParameterSpec getNewSpec();

    // Configure setup with particular test parameters
    public void init(String algorithm, int keyLength) throws Exception {
        this.algorithm = algorithm;
        this.keyLength = keyLength;
        init();
    }

    // Configure setup with particular test parameters
    public void init(String algorithm, int keyLength, int dataSize)
        throws Exception {
        this.dataSize = dataSize;
        init(algorithm, keyLength);
    }

    // Initalize test setup
    private void init() throws Exception {
        setupProvider();

        // Setup key material
        iv = fillSecureRandom(new byte[IV_BUFFER_SIZE]);
        spec = getNewSpec();
        // CC20 doesn't care about the algorithm name on the key, but AES does.
        ks = new SecretKeySpec(fillSecureRandom(new byte[keyLength / 8]),
            "AES");

        // Setup Cipher classes
        encryptCipher = makeCipher(prov, algorithm);
        encryptCipher.init(Cipher.ENCRYPT_MODE, ks, spec);
        decryptCipher = makeCipher(prov, algorithm);
        decryptCipher.init(Cipher.DECRYPT_MODE, ks, spec);

        // Setup input/output buffers
        in = fillRandom(new byte[dataSize]);
        encryptedData = new byte[encryptCipher.getOutputSize(in.length)];
        out = new byte[encryptedData.length];
        encryptCipher.doFinal(in, 0, in.length, encryptedData, 0);
        updateLen = in.length / 2;

    }

    @Benchmark
    public void encrypt() throws Exception {
        encryptCipher.init(Cipher.ENCRYPT_MODE, ks, getNewSpec());
        encryptCipher.doFinal(in, 0, in.length, out, 0);
    }

    @Benchmark
    public void encryptMultiPart() throws Exception {
        encryptCipher.init(Cipher.ENCRYPT_MODE, ks, getNewSpec());
        int outOfs = encryptCipher.update(in, 0, updateLen, out, 0);
        encryptCipher.doFinal(in, updateLen, in.length - updateLen,
            out, outOfs);
    }

    @Benchmark
    public void decrypt() throws Exception {
        decryptCipher.init(Cipher.DECRYPT_MODE, ks, spec);
        decryptCipher.doFinal(encryptedData, 0, encryptedData.length, out, 0);
    }

    @Benchmark
    public void decryptMultiPart() throws Exception {
        decryptCipher.init(Cipher.DECRYPT_MODE, ks, spec);
        decryptCipher.update(encryptedData, 0, updateLen, out, 0);
        decryptCipher.doFinal(encryptedData, updateLen,
            encryptedData.length - updateLen, out, 0);
    }
}
