/*
 * Copyright (c) 2008, 2025, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 4898461 6604496 8330842
 * @summary basic test for symmetric ciphers with padding
 * @author Valerie Peng
 * @library /test/lib ..
 * @key randomness
 * @modules jdk.crypto.cryptoki
 * @run main/othervm TestSymmCiphers ARCFOUR ARCFOUR 400
 */

/*
 * @test
 * @bug 4898461 6604496 8330842
 * @summary basic test for symmetric ciphers with padding
 * @author Valerie Peng
 * @library /test/lib ..
 * @key randomness
 * @modules jdk.crypto.cryptoki
 * @run main/othervm TestSymmCiphers RC4 RC4 401
 */

/*
 * @test
 * @bug 4898461 6604496 8330842
 * @summary basic test for symmetric ciphers with padding
 * @author Valerie Peng
 * @library /test/lib ..
 * @key randomness
 * @modules jdk.crypto.cryptoki
 * @run main/othervm TestSymmCiphers DES/CBC/NoPadding DES 400
 */

/*
 * @test
 * @bug 4898461 6604496 8330842
 * @summary basic test for symmetric ciphers with padding
 * @author Valerie Peng
 * @library /test/lib ..
 * @key randomness
 * @modules jdk.crypto.cryptoki
 * @run main/othervm TestSymmCiphers DESede/CBC/NoPadding DESede 160
 */

/*
 * @test
 * @bug 4898461 6604496 8330842
 * @summary basic test for symmetric ciphers with padding
 * @author Valerie Peng
 * @library /test/lib ..
 * @key randomness
 * @modules jdk.crypto.cryptoki
 * @run main/othervm TestSymmCiphers AES/CBC/NoPadding AES 4800
 */

/*
 * @test
 * @bug 4898461 6604496 8330842
 * @summary basic test for symmetric ciphers with padding
 * @author Valerie Peng
 * @library /test/lib ..
 * @key randomness
 * @modules jdk.crypto.cryptoki
 * @run main/othervm TestSymmCiphers Blowfish/CBC/NoPadding Blowfish 24
 */

/*
 * @test
 * @bug 4898461 6604496 8330842
 * @summary basic test for symmetric ciphers with padding
 * @author Valerie Peng
 * @library /test/lib ..
 * @key randomness
 * @modules jdk.crypto.cryptoki
 * @run main/othervm TestSymmCiphers DES/cbc/PKCS5Padding DES 6401
 */

/*
 * @test
 * @bug 4898461 6604496 8330842
 * @summary basic test for symmetric ciphers with padding
 * @author Valerie Peng
 * @library /test/lib ..
 * @key randomness
 * @modules jdk.crypto.cryptoki
 * @run main/othervm TestSymmCiphers DESede/CBC/PKCS5Padding DESede 402
 */

/*
 * @test
 * @bug 4898461 6604496 8330842
 * @summary basic test for symmetric ciphers with padding
 * @author Valerie Peng
 * @library /test/lib ..
 * @key randomness
 * @modules jdk.crypto.cryptoki
 * @run main/othervm TestSymmCiphers AES/CBC/PKCS5Padding AES 30
 */

/*
 * @test
 * @bug 4898461 6604496 8330842
 * @summary basic test for symmetric ciphers with padding
 * @author Valerie Peng
 * @library /test/lib ..
 * @key randomness
 * @modules jdk.crypto.cryptoki
 * @run main/othervm TestSymmCiphers Blowfish/CBC/PKCS5Padding Blowfish 19
 */

/*
 * @test
 * @bug 4898461 6604496 8330842
 * @summary basic test for symmetric ciphers with padding
 * @author Valerie Peng
 * @library /test/lib ..
 * @key randomness
 * @modules jdk.crypto.cryptoki
 * @run main/othervm TestSymmCiphers DES/ECB/NoPadding DES 400
 */

/*
 * @test
 * @bug 4898461 6604496 8330842
 * @summary basic test for symmetric ciphers with padding
 * @author Valerie Peng
 * @library /test/lib ..
 * @key randomness
 * @modules jdk.crypto.cryptoki
 * @run main/othervm TestSymmCiphers DESede/ECB/NoPadding DESede 160
 */

/*
 * @test
 * @bug 4898461 6604496 8330842
 * @summary basic test for symmetric ciphers with padding
 * @author Valerie Peng
 * @library /test/lib ..
 * @key randomness
 * @modules jdk.crypto.cryptoki
 * @run main/othervm TestSymmCiphers AES/ECB/NoPadding AES 4800
 */

/*
 * @test
 * @bug 4898461 6604496 8330842
 * @summary basic test for symmetric ciphers with padding
 * @author Valerie Peng
 * @library /test/lib ..
 * @key randomness
 * @modules jdk.crypto.cryptoki
 * @run main/othervm TestSymmCiphers DES/ECB/PKCS5Padding DES 32
 */

/*
 * @test
 * @bug 4898461 6604496 8330842
 * @summary basic test for symmetric ciphers with padding
 * @author Valerie Peng
 * @library /test/lib ..
 * @key randomness
 * @modules jdk.crypto.cryptoki
 * @run main/othervm TestSymmCiphers DES/ECB/PKCS5Padding DES 6400
 */

/*
 * @test
 * @bug 4898461 6604496 8330842
 * @summary basic test for symmetric ciphers with padding
 * @author Valerie Peng
 * @library /test/lib ..
 * @key randomness
 * @modules jdk.crypto.cryptoki
 * @run main/othervm TestSymmCiphers DESede/ECB/PKCS5Padding DESede 400
 */

/*
 * @test
 * @bug 4898461 6604496 8330842
 * @summary basic test for symmetric ciphers with padding
 * @author Valerie Peng
 * @library /test/lib ..
 * @key randomness
 * @modules jdk.crypto.cryptoki
 * @run main/othervm TestSymmCiphers AES/ECB/PKCS5Padding AES 64
 */

/*
 * @test
 * @bug 4898461 6604496 8330842
 * @summary basic test for symmetric ciphers with padding
 * @author Valerie Peng
 * @library /test/lib ..
 * @key randomness
 * @modules jdk.crypto.cryptoki
 * @run main/othervm TestSymmCiphers DES DES 6400
 */
/*
 * @test
 * @bug 4898461 6604496 8330842
 * @summary basic test for symmetric ciphers with padding
 * @author Valerie Peng
 * @library /test/lib ..
 * @key randomness
 * @modules jdk.crypto.cryptoki
 * @run main/othervm TestSymmCiphers DESede DESede 408
 */

/*
 * @test
 * @bug 4898461 6604496 8330842
 * @summary basic test for symmetric ciphers with padding
 * @author Valerie Peng
 * @library /test/lib ..
 * @key randomness
 * @modules jdk.crypto.cryptoki
 * @run main/othervm TestSymmCiphers AES AES 128
 */

/*
 * @test
 * @bug 4898461 6604496 8330842
 * @summary basic test for symmetric ciphers with padding
 * @author Valerie Peng
 * @library /test/lib ..
 * @key randomness
 * @modules jdk.crypto.cryptoki
 * @run main/othervm TestSymmCiphers AES/CTR/NoPadding AES 3200
 */

/*
 * @test
 * @bug 4898461 6604496 8330842
 * @summary basic test for symmetric ciphers with padding
 * @author Valerie Peng
 * @library /test/lib ..
 * @key randomness
 * @modules jdk.crypto.cryptoki
 * @run main/othervm TestSymmCiphers AES/CTS/NoPadding AES 3200
 */

import jtreg.SkippedException;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.security.AlgorithmParameters;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.util.Random;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public class TestSymmCiphers extends PKCS11Test {

    private static final StringBuffer debugBuf = new StringBuffer();

    private final String transformation;
    private final String keyAlgo;
    private final int dataSize;

    public TestSymmCiphers(String transformation,
                           String keyAlgo,
                           int dataSize) {
        this.transformation = transformation;
        this.keyAlgo = keyAlgo;
        this.dataSize = dataSize;
    }

    @Override
    public void main(Provider p) throws Exception {
        // NSS reports CKR_DEVICE_ERROR when the data passed to
        // its EncryptUpdate/DecryptUpdate is not multiple of blocks
        int firstBlkSize = 16;
        Random random = new Random();
        try {
            System.out.println("===" + transformation + "===");
            try {
                KeyGenerator kg =
                        KeyGenerator.getInstance(keyAlgo, p);
                SecretKey key = kg.generateKey();
                Cipher c1 = Cipher.getInstance(transformation, p);
                Cipher c2 = Cipher.getInstance(transformation,
                        System.getProperty("test.provider.name", "SunJCE"));

                byte[] plainTxt = new byte[dataSize];
                random.nextBytes(plainTxt);
                System.out.println("Testing inLen = " + plainTxt.length);

                c2.init(Cipher.ENCRYPT_MODE, key);
                AlgorithmParameters params = c2.getParameters();
                byte[] answer = c2.doFinal(plainTxt);
                System.out.println("Encryption tests: START");
                test(c1, Cipher.ENCRYPT_MODE, key, params, firstBlkSize,
                        plainTxt, answer);
                System.out.println("Encryption tests: DONE");
                c2.init(Cipher.DECRYPT_MODE, key, params);
                byte[] answer2 = c2.doFinal(answer);
                System.out.println("Decryption tests: START");
                test(c1, Cipher.DECRYPT_MODE, key, params, firstBlkSize,
                        answer, answer2);
                System.out.println("Decryption tests: DONE");
            } catch (NoSuchAlgorithmException nsae) {
                throw new SkippedException("Skipping unsupported algorithm: " +
                                           nsae);
            }
        } catch (Exception ex) {
            // print out debug info when exception is encountered
            System.out.println(debugBuf);
            throw ex;
        }
    }

    private static void test(Cipher cipher, int mode, SecretKey key,
                             AlgorithmParameters params, int firstBlkSize,
                             byte[] in, byte[] answer) throws Exception {
        // test setup
        long startTime, endTime;
        cipher.init(mode, key, params);
        int outLen = cipher.getOutputSize(in.length);

        // test data preparation
        ByteBuffer inBuf = ByteBuffer.allocate(in.length);
        inBuf.put(in);
        inBuf.position(0);
        ByteBuffer inDirectBuf = ByteBuffer.allocateDirect(in.length);
        inDirectBuf.put(in);
        inDirectBuf.position(0);
        ByteBuffer outBuf = ByteBuffer.allocate(outLen);
        ByteBuffer outDirectBuf = ByteBuffer.allocateDirect(outLen);

        // test#1: byte[] in + byte[] out
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        startTime = System.nanoTime();
        byte[] temp = cipher.update(in, 0, firstBlkSize);
        if (temp != null && temp.length > 0) {
            baos.write(temp, 0, temp.length);
        }
        temp = cipher.doFinal(in, firstBlkSize, in.length - firstBlkSize);
        if (temp != null && temp.length > 0) {
            baos.write(temp, 0, temp.length);
        }
        byte[] testOut1 = baos.toByteArray();
        endTime = System.nanoTime();
        perfOut("stream InBuf + stream OutBuf", endTime - startTime);
        match(testOut1, answer);

        // test#2: Non-direct Buffer in + non-direct Buffer out
        startTime = System.nanoTime();
        cipher.update(inBuf, outBuf);
        cipher.doFinal(inBuf, outBuf);
        endTime = System.nanoTime();
        perfOut("non-direct InBuf + non-direct OutBuf", endTime - startTime);
        match(outBuf, answer);

        // test#3: Direct Buffer in + direc Buffer out
        startTime = System.nanoTime();
        cipher.update(inDirectBuf, outDirectBuf);
        cipher.doFinal(inDirectBuf, outDirectBuf);
        endTime = System.nanoTime();
        perfOut("direct InBuf + direct OutBuf", endTime - startTime);

        match(outDirectBuf, answer);

        // test#4: Direct Buffer in + non-direct Buffer out
        inDirectBuf.position(0);
        outBuf.position(0);

        startTime = System.nanoTime();
        cipher.update(inDirectBuf, outBuf);
        cipher.doFinal(inDirectBuf, outBuf);
        endTime = System.nanoTime();
        perfOut("direct InBuf + non-direct OutBuf", endTime - startTime);
        match(outBuf, answer);

        // test#5: Non-direct Buffer in + direct Buffer out
        inBuf.position(0);
        outDirectBuf.position(0);

        startTime = System.nanoTime();
        cipher.update(inBuf, outDirectBuf);
        cipher.doFinal(inBuf, outDirectBuf);
        endTime = System.nanoTime();
        perfOut("non-direct InBuf + direct OutBuf", endTime - startTime);

        match(outDirectBuf, answer);

        debugBuf.setLength(0);
    }

    private static void perfOut(String msg, long elapsed) {
        debugOut("PERF> " + msg + ", elapsed: " + elapsed + " ns\n");
    }

    private static void debugOut(String msg) {
        debugBuf.append(msg);
    }

    private static void match(byte[] b1, byte[] b2) throws Exception {
        if (b1.length != b2.length) {
            debugOut("got len   : " + b1.length + "\n");
            debugOut("expect len: " + b2.length + "\n");
            throw new Exception("mismatch - different length! got: " + b1.length + ", expect: " + b2.length + "\n");
        } else {
            for (int i = 0; i < b1.length; i++) {
                if (b1[i] != b2[i]) {
                    debugOut("got   : " + toString(b1) + "\n");
                    debugOut("expect: " + toString(b2) + "\n");
                    throw new Exception("mismatch");
                }
            }
        }
    }

    private static void match(ByteBuffer bb, byte[] answer) throws Exception {
        byte[] bbTemp = new byte[bb.position()];
        bb.position(0);
        bb.get(bbTemp, 0, bbTemp.length);
        match(bbTemp, answer);
    }

    public static void main(String[] args) throws Exception {
        main(new TestSymmCiphers(args[0], args[1], Integer.parseInt(args[2])), args);
    }
}
