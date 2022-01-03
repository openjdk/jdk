/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8248268 8268621 8271745
 * @summary Verify general properties of the AES/KW/NoPadding,
 *     AES/KW/PKCS5Padding, and AES/KWP/NoPadding impls of SunJCE provider.
 * @run main TestGeneral
 */
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.security.Key;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.InvalidAlgorithmParameterException;
import java.security.AlgorithmParameters;
import javax.crypto.*;
import javax.crypto.spec.*;

public class TestGeneral {

    private static final byte[] DATA_32 =
            Arrays.copyOf("1234567890123456789012345678901234".getBytes(), 32);
    private static final SecretKey KEY =
            new SecretKeySpec(DATA_32, 0, 16, "AES");
    private static final int KW_IV_LEN = 8;
    private static final int KWP_IV_LEN = 4;
    private static final int MAX_KW_PKCS5PAD_LEN = 8; // 1-8
    private static final int MAX_KWP_PAD_LEN = 7; // 0-7

    public static void testEnc(Cipher c, byte[] in, int startLen, int inc,
            IvParameterSpec[] ivs, int maxPadLen) throws Exception {

        System.out.println("testEnc, input len=" + startLen + " w/ inc=" +
                inc);

        for (IvParameterSpec iv : ivs) {
            System.out.print("\t=> w/ iv=" + iv);

            for (int inLen = startLen; inLen < in.length; inLen+=inc) {
                c.init(Cipher.ENCRYPT_MODE, KEY, iv);

                int estOutLen = c.getOutputSize(inLen);
                System.out.println(", inLen=" + inLen);
                byte[] out = c.doFinal(in, 0, inLen);

                // check the length of encryption output
                if (estOutLen != out.length || (out.length % 8 != 0) ||
                        (out.length - inLen < 8)) {
                    System.out.println("=> estimated: " + estOutLen);
                    System.out.println("=> actual: " + out.length);
                    throw new RuntimeException("Failed enc output len check");
                }

                c.init(Cipher.DECRYPT_MODE, KEY, iv);
                estOutLen = c.getOutputSize(out.length);
                byte[] recovered = new byte[estOutLen];

                // do decryption using ByteBuffer and multi-part
                ByteBuffer outBB = ByteBuffer.wrap(out);
                ByteBuffer recoveredBB = ByteBuffer.wrap(recovered);
                int len = c.update(outBB, recoveredBB);
                len += c.doFinal(outBB, recoveredBB);

                // check the length of decryption output
                if (estOutLen < len || (estOutLen - len) > maxPadLen) {
                    System.out.println("=> estimated: " + estOutLen);
                    System.out.println("=> actual: " + len);
                    throw new RuntimeException("Failed dec output len check");
                }

                if (!Arrays.equals(in, 0, inLen, recovered, 0, len)) {
                    throw new RuntimeException("Failed decrypted data check");
                }
            }
        }
    }

    public static void testWrap(Cipher c, Key[] inKeys, IvParameterSpec[] ivs,
            int maxPadLen) throws Exception {

        for (Key inKey : inKeys) {
            System.out.println("testWrap, key: " + inKey);
            for (IvParameterSpec iv : ivs) {
                System.out.println("\t=> w/ iv " + iv);

                c.init(Cipher.WRAP_MODE, KEY, iv);

                byte[] out = c.wrap(inKey);

                // output should always be multiple of cipher block size
                if (out.length % c.getBlockSize() != 0) {
                    throw new RuntimeException("Invalid wrap len: " +
                            out.length);
                }

                c.init(Cipher.UNWRAP_MODE, KEY, iv);

                // SecretKey or PrivateKey
                int keyType = (inKey instanceof SecretKey? Cipher.SECRET_KEY :
                        Cipher.PRIVATE_KEY);

                int estOutLen = c.getOutputSize(out.length);
                Key key2 = c.unwrap(out, inKey.getAlgorithm(), keyType);

                if ((keyType == Cipher.SECRET_KEY &&
                        !(key2 instanceof SecretKey)) ||
                        (keyType == Cipher.PRIVATE_KEY &&
                        !(key2 instanceof PrivateKey))) {
                    throw new RuntimeException("Failed unwrap type check");
                }

                byte[] in2 = key2.getEncoded();
                // check decryption output length
                if (estOutLen < in2.length ||
                        (estOutLen - in2.length) > maxPadLen) {
                    System.out.println("=> estimated: " + estOutLen);
                    System.out.println("=> actual: " + in2.length);
                    throw new RuntimeException("Failed unwrap len check");
                }

                if (!Arrays.equals(inKey.getEncoded(), in2) ||
                        !(inKey.getAlgorithm().equalsIgnoreCase
                        (key2.getAlgorithm()))) {
                    throw new RuntimeException("Failed unwrap key check");
                }
            }
        }
    }

    public static void testIv(Cipher c, int defIvLen, boolean allowCustomIv)
            throws Exception {

        System.out.println("testIv: defIvLen = " + defIvLen +
                " allowCustomIv = " + allowCustomIv);

        // get a fresh Cipher instance so we can test iv with pre-init state
        c = Cipher.getInstance(c.getAlgorithm(), c.getProvider());
        if (c.getIV() != null) {
            throw new RuntimeException("Expects null iv");
        }

        AlgorithmParameters ivParams = c.getParameters();
        if (ivParams == null) {
            throw new RuntimeException("Expects non-null default parameters");
        }
        IvParameterSpec ivSpec =
                ivParams.getParameterSpec(IvParameterSpec.class);
        byte[] iv = ivSpec.getIV();
        // try through all opmodes
        c.init(Cipher.ENCRYPT_MODE, KEY);
        c.init(Cipher.DECRYPT_MODE, KEY);
        c.init(Cipher.WRAP_MODE, KEY);
        c.init(Cipher.UNWRAP_MODE, KEY);

        byte[] defIv = c.getIV();

        // try again through all opmodes
        c.init(Cipher.ENCRYPT_MODE, KEY);
        c.init(Cipher.DECRYPT_MODE, KEY);
        c.init(Cipher.WRAP_MODE, KEY);
        c.init(Cipher.UNWRAP_MODE, KEY);

        byte[] defIv2 = c.getIV();
        if (iv.length != defIvLen || !Arrays.equals(iv, defIv) ||
                !Arrays.equals(defIv, defIv2)) {
            throw new RuntimeException("Failed default iv check");
        }
        if (defIv == defIv2) {
            throw new RuntimeException("Failed getIV copy check");
        }

        // try init w/ an iv w/ invalid length
        try {
            c.init(Cipher.ENCRYPT_MODE, KEY, new IvParameterSpec(defIv, 0,
                    defIv.length/2));
            throw new RuntimeException("Invalid iv accepted");
        } catch (InvalidAlgorithmParameterException iape) {
            System.out.println("Invalid IV rejected as expected");
        }

        if (allowCustomIv) {
            Arrays.fill(defIv, (byte) 0xFF);
            // try through all opmodes
            c.init(Cipher.ENCRYPT_MODE, KEY, new IvParameterSpec(defIv));
            c.init(Cipher.DECRYPT_MODE, KEY, new IvParameterSpec(defIv));
            c.init(Cipher.WRAP_MODE, KEY, new IvParameterSpec(defIv));
            c.init(Cipher.UNWRAP_MODE, KEY, new IvParameterSpec(defIv));

            if (!Arrays.equals(defIv, c.getIV())) {
                throw new RuntimeException("Failed set iv check");
            }
        }
    }

    public static void main(String[] argv) throws Exception {
        byte[] data = DATA_32;

        SecretKey aes256 = new SecretKeySpec(DATA_32, "AES");
        SecretKey any256 = new SecretKeySpec(DATA_32, "ANY");
        PrivateKey priv = KeyPairGenerator.getInstance
                ("RSA", "SunRsaSign").generateKeyPair().getPrivate();

        String[] algos = {
            "AES/KW/PKCS5Padding", "AES/KW/NoPadding", "AES/KWP/NoPadding"
        };

        for (String a : algos) {
            System.out.println("Testing " + a);
            Cipher c = Cipher.getInstance(a, "SunJCE");

            int blkSize = c.getBlockSize();

            // set the default based on AES/KWP/NoPadding, the other two
            // override as needed
            int startLen = data.length - blkSize;
            int inc = 1;
            IvParameterSpec[] ivs = new IvParameterSpec[] { null };
            int padLen = MAX_KWP_PAD_LEN;
            Key[] keys = new Key[] { aes256, any256, priv };
            int ivLen = KWP_IV_LEN;
            boolean allowCustomIv = false;

            switch (a) {
            case "AES/KW/PKCS5Padding":
                ivs = new IvParameterSpec[] {
                        null, new IvParameterSpec(DATA_32, 0, KW_IV_LEN) };
                padLen = MAX_KW_PKCS5PAD_LEN;
                ivLen = KW_IV_LEN;
                allowCustomIv = true;
                break;
            case "AES/KW/NoPadding":
                startLen = data.length >> 1;
                inc = blkSize;
                ivs = new IvParameterSpec[] {
                        null, new IvParameterSpec(DATA_32, 0, KW_IV_LEN) };
                padLen = 0;
                keys = new Key[] { aes256, any256 };
                ivLen = KW_IV_LEN;
                allowCustomIv = true;
                break;
            }

            // now test based on the configured arguments
            testEnc(c, data, startLen, inc, ivs, padLen);
            testWrap(c, keys, ivs, padLen);
            testIv(c, ivLen, allowCustomIv);
        }
        System.out.println("All Tests Passed");
    }
}
