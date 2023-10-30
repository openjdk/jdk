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
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;

/**
 * This performance tests runs AES/GCM encryption and decryption using heap and
 * direct ByteBuffers as input and output buffers for single and multi-part
 * operations.
 *
 * This test rotates the IV and creates a new GCMParameterSpec for each encrypt
 * benchmark operation
 */

public class CC20P1305ByteBuffer extends CryptoBase {

    @Param({"256"})
    private int keyLength;

    @Param({"1024", "1500", "4096", "16384"})
    private int dataSize;

    @Param({"direct", "heap"})
    private String dataMethod;

    private Cipher encryptCipher, decryptCipher;
    byte[] data, iv;
    ByteBuffer encryptedData, in, out;
    SecretKeySpec ks;
    AlgorithmParameterSpec spec;

    private final String algorithm = "ChaCha20-Poly1305/None/NoPadding";
    private static final int IV_BUFFER_SIZE = 32;
    private static final int IV_MODULO = IV_BUFFER_SIZE - 16;
    int iv_index = 0;
    int updateLen = 0;

    private AlgorithmParameterSpec getNewSpec() {
        iv_index = (iv_index + 1) % IV_MODULO;
        return new IvParameterSpec(iv, iv_index, 12);
    }

    @Setup
    public void setup() throws Exception {
        setupProvider();

        // Setup key material
        iv = fillSecureRandom(new byte[IV_BUFFER_SIZE]);
        spec = getNewSpec();
        ks = new SecretKeySpec(fillSecureRandom(new byte[keyLength / 8]), "AES");

        // Setup Cipher classes
        encryptCipher = makeCipher(prov, algorithm);
        encryptCipher.init(Cipher.ENCRYPT_MODE, ks, spec);
        decryptCipher = makeCipher(prov, algorithm);
        decryptCipher.init(Cipher.DECRYPT_MODE, ks,
            encryptCipher.getParameters(). getParameterSpec(spec.getClass()));

        // Setup input/output buffers
        data = fillRandom(new byte[dataSize]);
        if (dataMethod.equalsIgnoreCase("direct")) {
            in = ByteBuffer.allocateDirect(data.length);
            in.put(data);
            in.flip();
            encryptedData = ByteBuffer.allocateDirect(
                encryptCipher.getOutputSize(data.length));
            out = ByteBuffer.allocateDirect(encryptedData.capacity());
        } else if (dataMethod.equalsIgnoreCase("heap")) {
            in = ByteBuffer.wrap(data);
            encryptedData = ByteBuffer.allocate(
                encryptCipher.getOutputSize(data.length));
            out = ByteBuffer.allocate(encryptedData.capacity());
        }

        encryptCipher.doFinal(in, encryptedData);
        encryptedData.flip();
        in.flip();
        updateLen = in.remaining() / 2;
    }

    @Benchmark
    public void encrypt() throws Exception {
        encryptCipher.init(Cipher.ENCRYPT_MODE, ks, getNewSpec());
        encryptCipher.doFinal(in, out);
        out.flip();
        in.flip();
    }

    @Benchmark
    public void encryptMultiPart() throws Exception {
        encryptCipher.init(Cipher.ENCRYPT_MODE, ks, getNewSpec());
        in.limit(updateLen);
        encryptCipher.update(in, out);
        in.limit(in.capacity());
        encryptCipher.doFinal(in, out);
        out.flip();
        in.flip();
    }

    @Benchmark
    public void decrypt() throws Exception {
        decryptCipher.init(Cipher.DECRYPT_MODE, ks,
            encryptCipher.getParameters().getParameterSpec(spec.getClass()));
        decryptCipher.doFinal(encryptedData, out);
        encryptedData.flip();
        out.flip();
    }

    @Benchmark
    public void decryptMultiPart() throws Exception {
        decryptCipher.init(Cipher.DECRYPT_MODE, ks,
            encryptCipher.getParameters().getParameterSpec(spec.getClass()));

        int len = encryptedData.remaining();
        encryptedData.limit(updateLen);
        decryptCipher.update(encryptedData, out);
        encryptedData.limit(len);

        decryptCipher.doFinal(encryptedData, out);
        encryptedData.flip();
        out.flip();
    }
}
