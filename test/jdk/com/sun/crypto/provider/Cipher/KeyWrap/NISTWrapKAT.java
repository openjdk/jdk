/*
 * Copyright (c) 2004, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 5008156 8248268
 * @run testng NISTWrapKAT
 * @summary Verify that the AES-Key-Wrap and AES-Key-Wrap-Pad ciphers
 * work as expected using NIST test vectors.
 * @author Valerie Peng
 */
import java.security.Key;
import java.security.AlgorithmParameters;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.util.Arrays;
import java.math.BigInteger;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.Assert;

public class NISTWrapKAT {

    private static final String KEK =
        "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f";
    private static final String DATA =
        "00112233445566778899aabbccddeeff000102030405060708090a0b0c0d0e0f";
    // from RFC 3394 sec4
    private static String KW_AES128_128 =
        "1fa68b0a8112b447aef34bd8fb5a7b829d3e862371d2cfe5";
    private static String KW_AES192_128 =
        "96778b25ae6ca435f92b5b97c050aed2468ab8a17ad84e5d";
    private static String KW_AES192_192 =
        "031d33264e15d33268f24ec260743edce1c6c7ddee725a936ba814915c6762d2";
    private static String KW_AES256_128 =
        "64e8c3f9ce0f5ba263e9777905818a2a93c8191e7d6e8ae7";
    private static String KW_AES256_192 =
        "a8f9bc1612c68b3ff6e6f4fbe30e71e4769c8b80a32cb8958cd5d17d6b254da1";
    private static String KW_AES256_256 =
        "28c9f404c4b810f4cbccb35cfb87f8263f5786e2d80ed326cbc7f0e71a99f43bfb988b9b7a02dd21";

    private static String KWP_AES128_56 = "1B1D4BC2A90B1FA389412B3D40FECB20";
    private static String KWP_AES128_112 =
            "EA0BFDE8AF063E8918E811A05D2A4C23A367B45315716B5B";
    private static String KWP_AES192_56 = "87CE2C5C2D7196E09381056B319D91E9";
    private static String KWP_AES192_112 =
            "900484950F84EB6ED74CE81DCDACA26E72BB29D4A6F7AC74";
    private static String KWP_AES192_168 =
            "A402348F1956DB968FDDFD8976420F9DDEB7183CF16B91B0AEB74CAB196C343E";
    private static String KWP_AES256_56 = "809BB1864A18938529E97EFCD9544E9A";
    private static String KWP_AES256_112 =
            "C68168173F141E6D5767611574A941259090DA78D7DF9DF7";
    private static String KWP_AES256_168 =
            "308D49692B5F8CF638D54BB4B985633504237329964C76EBB3F669870A708DBC";
    private static String KWP_AES256_224 =
            "0942747DB07032A3F04CDB2E7DE1CBA038F92BC355393AE9A0E4AE8C901912AC3D3AF0F16D240607";
     // from RFC 5649 sec6
     private static String KEK2 = "5840DF6E29B02AF1AB493B705BF16EA1AE8338F4DCC176A8";

    private static byte[] toBytes(String hex, int hexLen) {
        if (hexLen < hex.length()) {
            hex = hex.substring(0, hexLen);
        } else {
            hexLen = hex.length();
        }
        int outLen = hexLen >> 1;
        BigInteger temp = new BigInteger(hex, 16);
        byte[] val = temp.toByteArray();
        if (val.length == outLen) {
            return val;
        } else {
            byte[] out = new byte[outLen];
            if (val.length < outLen) {
                // enlarge
                System.arraycopy(val, 0, out, outLen - val.length, val.length);
            } else {
                // truncate
                System.arraycopy(val, val.length - outLen, out, 0, outLen);
            }
            return out;
        }
    }

    @DataProvider
    public Object[][] testData() {
        return new Object[][] {
            { "AESWrap", KEK, 16, DATA, 16, KW_AES128_128 },
            { "AESWrap", KEK, 24, DATA, 16, KW_AES192_128 },
            { "AESWrap", KEK, 24, DATA, 24, KW_AES192_192 },
            { "AESWrap", KEK, 32, DATA, 16, KW_AES256_128 },
            { "AESWrap", KEK, 32, DATA, 24, KW_AES256_192 },
            { "AESWrap", KEK, 32, DATA, 32, KW_AES256_256 },
            { "AES/KW/NoPadding", KEK, 16, DATA, 16, KW_AES128_128 },
            { "AES/KW/NoPadding", KEK, 24, DATA, 16, KW_AES192_128 },
            { "AES/KW/NoPadding", KEK, 24, DATA, 24, KW_AES192_192 },
            { "AES/KW/NoPadding", KEK, 32, DATA, 16, KW_AES256_128 },
            { "AES/KW/NoPadding", KEK, 32, DATA, 24, KW_AES256_192 },
            { "AES/KW/NoPadding", KEK, 32, DATA, 32, KW_AES256_256 },
            { "AES/KWP/NoPadding", KEK, 16, DATA, 7, KWP_AES128_56 },
            { "AES/KWP/NoPadding", KEK, 16, DATA, 14, KWP_AES128_112 },
            { "AES/KWP/NoPadding", KEK, 24, DATA, 7, KWP_AES192_56 },
            { "AES/KWP/NoPadding", KEK, 24, DATA, 14, KWP_AES192_112 },
            { "AES/KWP/NoPadding", KEK, 24, DATA, 21, KWP_AES192_168 },
            { "AES/KWP/NoPadding", KEK, 32, DATA, 7, KWP_AES256_56 },
            { "AES/KWP/NoPadding", KEK, 32, DATA, 14, KWP_AES256_112 },
            { "AES/KWP/NoPadding", KEK, 32, DATA, 21, KWP_AES256_168 },
            { "AES/KWP/NoPadding", KEK, 32, DATA, 28, KWP_AES256_224 },
            { "AES/KWP/NoPadding", KEK2, 24, "466F7250617369", 7,
              "AFBEB0F07DFBF5419200F2CCB50BB24F" },
            { "AES/KWP/NoPadding", KEK2, 24,
              "C37B7E6492584340BED12207808941155068F738", 20,
              "138BDEAA9B8FA7FC61F97742E72248EE5AE6AE5360D1AE6A5F54F373FA543B6A" },
        };
    }

    @Test(dataProvider = "testData")
    public void testKeyWrap(String algo, String key, int keyLen,
            String data, int dataLen, String expected) throws Exception {
        System.out.println("Testing " +  algo + " Cipher with wrapping " +
            dataLen + "-byte key with " + 8*keyLen + "-bit KEK");
        int allowed = Cipher.getMaxAllowedKeyLength("AES");
        if (keyLen > allowed) {
            System.out.println("=> skip, exceeds max allowed size " + allowed);
            return;
        }
        Cipher c = Cipher.getInstance(algo, "SunJCE");
        byte[] keyVal = toBytes(key, keyLen << 1);
        byte[] dataVal = toBytes(data, dataLen << 1);

        SecretKey cipherKey = new SecretKeySpec(keyVal, "AES");
        c.init(Cipher.WRAP_MODE, cipherKey);
        SecretKey toBeWrappedKey = new SecretKeySpec(dataVal, "AES");

        // first test WRAP with known values
        byte[] wrapped = c.wrap(toBeWrappedKey);
        byte[] expectedVal = toBytes(expected, expected.length());

        if (!Arrays.equals(wrapped, expectedVal)) {
            throw new Exception("Wrap failed; got different result");
        }

        // then test UNWRAP and compare with the initial values
        c.init(Cipher.UNWRAP_MODE, cipherKey);
        Key unwrapped = c.unwrap(wrapped, "AES", Cipher.SECRET_KEY);
        if (!Arrays.equals(unwrapped.getEncoded(), dataVal)) {
            throw new Exception("Unwrap failed; got different result");
        }
    }

    @Test(dataProvider = "testData")
    public void testEnc(String algo, String key, int keyLen, String data, int dataLen, String expected)
            throws Exception {
        System.out.println("Testing " +  algo + " Cipher with enc " +
            dataLen + "-byte data with " + 8*keyLen + "-bit KEK");
        int allowed = Cipher.getMaxAllowedKeyLength("AES");
        if (keyLen > allowed) {
            System.out.println("=> skip, exceeds max allowed size " + allowed);
            return;
        }
        Cipher c = Cipher.getInstance(algo, "SunJCE");

        byte[] keyVal = toBytes(key, keyLen << 1);
        byte[] dataVal = toBytes(data, dataLen << 1);

        SecretKey cipherKey = new SecretKeySpec(keyVal, "AES");
        c.init(Cipher.ENCRYPT_MODE, cipherKey);

        // first test encryption with known values
        byte[] ct11 = c.update(dataVal);
        byte[] ct12 = c.doFinal();
        byte[] ct2 = c.doFinal(dataVal);
        byte[] expectedVal = toBytes(expected, expected.length());

        if (ct11 != null || !Arrays.equals(ct12, ct2) ||
            !Arrays.equals(ct2, expectedVal)) {
            throw new Exception("Encryption failed; got different result");
        }

        // then test decryption and compare with the initial values
        c.init(Cipher.DECRYPT_MODE, cipherKey);
        byte[] pt11 = c.update(ct12);
        byte[] pt12 = c.doFinal();
        byte[] pt2 = c.doFinal(ct2);
        if (pt11 != null || !Arrays.equals(pt12, pt2) ||
            !Arrays.equals(pt2, dataVal)) {
            throw new Exception("Decryption failed; got different result");
        }
    }
}
