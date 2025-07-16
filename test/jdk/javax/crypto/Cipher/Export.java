/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8325513
 * @library /test/lib
 * @modules java.base/javax.crypto:+open
 * @summary Try out the export method
 */

import jdk.test.lib.Asserts;

import javax.crypto.Cipher;
import javax.crypto.CipherSpi;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.Key;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;

public class Export {
    public static void main(String[] args) throws Exception {

        SecretKey sk = new SecretKeySpec(s2b("key"), "X");

        Cipher c1 = newCipher();
        c1.init(Cipher.ENCRYPT_MODE, sk);
        SecretKey sk11 = c1.exportKey("X", s2b("hi"), 32);
        SecretKey sk12 = c1.exportKey("X", s2b("ho"), 32);
        byte[] b11 = c1.exportData(s2b("hi"), 32);
        byte[] b12 = c1.exportData(s2b("ho"), 32);

        Cipher c2 = newCipher();
        c2.init(Cipher.ENCRYPT_MODE, sk);
        SecretKey sk21 = c2.exportKey("X", s2b("hi"), 32);
        byte[] b21 = c2.exportData(s2b("hi"), 32);

        Asserts.assertEqualsByteArray(sk11.getEncoded(), sk21.getEncoded());
        Asserts.assertNotEqualsByteArray(sk11.getEncoded(), sk12.getEncoded());
        Asserts.assertEqualsByteArray(b11, b21);
        Asserts.assertNotEqualsByteArray(b11, b12);
    }

    static class CipherImpl extends CipherSpi {

        protected void engineSetMode(String mode) { }
        protected void engineSetPadding(String padding) { }
        protected int engineGetBlockSize() { return 0; }
        protected int engineGetOutputSize(int inputLen) { return 0; }
        protected byte[] engineGetIV() { return new byte[0]; }
        protected AlgorithmParameters engineGetParameters() { return null; }
        protected void engineInit(int opmode, Key key, AlgorithmParameterSpec params, SecureRandom random) { }
        protected void engineInit(int opmode, Key key, AlgorithmParameters params, SecureRandom random) { }
        protected byte[] engineUpdate(byte[] input, int inputOffset, int inputLen) { return new byte[0]; }
        protected int engineUpdate(byte[] input, int inputOffset, int inputLen, byte[] output, int outputOffset) { return 0; }
        protected byte[] engineDoFinal(byte[] input, int inputOffset, int inputLen) { return new byte[0]; }
        protected int engineDoFinal(byte[] input, int inputOffset, int inputLen, byte[] output, int outputOffset) { return 0; }

        byte[] keyBytes;
        protected void engineInit(int opmode, Key key, SecureRandom random) {
            keyBytes = key.getEncoded();
        }

        @Override
        protected SecretKey engineExportKey(String algorithm, byte[] context, int length) {
            return new SecretKeySpec(engineExportData(context, length), algorithm);
        }

        @Override
        protected byte[] engineExportData(byte[] context, int length) {
            byte[] output = new byte[length];
            for (int i = 0; i < length; i++) {
                output[i] = (byte)(context[i % context.length] ^ keyBytes[i % keyBytes.length]);
            }
            return output;
        }
    }

    static Cipher newCipher() throws Exception {
        var ctor = Cipher.class.getDeclaredConstructor(CipherSpi.class, String.class);
        ctor.setAccessible(true);
        return ctor.newInstance(new CipherImpl(), "X");
    }

    static byte[] s2b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
