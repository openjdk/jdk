/*
 * Copyright (c) 2004, 2007, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 5008156
 * @run main NISTWrapKAT
 * @summary Verify that the "AESWrap" key wrap cipher work as
 * expected using NIST test vectors.
 * @author Valerie Peng
 */
import java.security.Key;
import java.security.AlgorithmParameters;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.util.Arrays;
import java.math.BigInteger;

public class NISTWrapKAT {

    private static final String KEK =
        "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f";
    private static final String DATA =
        "00112233445566778899aabbccddeeff000102030405060708090a0b0c0d0e0f";

    private static String AES128_128 =
        "1fa68b0a8112b447aef34bd8fb5a7b829d3e862371d2cfe5";
    private static String AES192_128 =
        "96778b25ae6ca435f92b5b97c050aed2468ab8a17ad84e5d";
    private static String AES192_192 =
        "031d33264e15d33268f24ec260743edce1c6c7ddee725a936ba814915c6762d2";
    private static String AES256_128 =
        "64e8c3f9ce0f5ba263e9777905818a2a93c8191e7d6e8ae7";
    private static String AES256_192 =
        "a8f9bc1612c68b3ff6e6f4fbe30e71e4769c8b80a32cb8958cd5d17d6b254da1";
    private static String AES256_256 =
        "28c9f404c4b810f4cbccb35cfb87f8263f5786e2d80ed326cbc7f0e71a99f43bfb988b9b7a02dd21";

    public static void testKeyWrap(int keyLen, int dataLen,
                                   String expected) throws Exception {
        System.out.println("Testing AESWrap Cipher with " +
            dataLen + "-byte data with " + 8*keyLen + "-bit key");
        Cipher c = Cipher.getInstance("AESWrap", "SunJCE");
        byte[] keyVal = new byte[keyLen];
        byte[] dataVal = new byte[dataLen];

        // setup the key encryption key and the to-be-wrapped key
        BigInteger temp = new BigInteger(KEK.substring(0, keyLen*2), 16);
        byte[] val = temp.toByteArray();
        System.arraycopy(val, 0, keyVal, keyVal.length-val.length,
                         val.length);
        temp = new BigInteger(DATA.substring(0, dataLen*2), 16);
        val = temp.toByteArray();
        System.arraycopy(val, 0, dataVal, dataVal.length-val.length,
                         val.length);

        SecretKey cipherKey = new SecretKeySpec(keyVal, "AES");
        c.init(Cipher.WRAP_MODE, cipherKey);
        SecretKey toBeWrappedKey = new SecretKeySpec(dataVal, "AES");

        // first test WRAP with known values
        byte[] wrapped = c.wrap(toBeWrappedKey);
        byte[] expectedVal = new BigInteger(expected, 16).toByteArray();
        // need to add offset since BigInteger may pad "0x00" in the beginning
        int offset = expectedVal.length - wrapped.length;
        for (int i=0; i<wrapped.length; i++) {
            if (wrapped[i] != expectedVal[offset + i]) {
                throw new Exception("Wrap failed; got different result");
            }
        }

        // then test UNWRAP and compare with the initial values
        c.init(Cipher.UNWRAP_MODE, cipherKey);
        Key unwrapped = c.unwrap(wrapped, "AES", Cipher.SECRET_KEY);
        if (!Arrays.equals(unwrapped.getEncoded(), dataVal)) {
            throw new Exception("Unwrap failed; got different result");
        }
    }

    public static void main(String[] argv) throws Exception {
        testKeyWrap(16, 16, AES128_128);
        // only run the tests on longer key lengths if unlimited version
        // of JCE jurisdiction policy files are installed
        int allowed = Cipher.getMaxAllowedKeyLength("AES");
        if (allowed >= 24*8) {
            testKeyWrap(24, 16, AES192_128);
            testKeyWrap(24, 24, AES192_192);
        }
        if (allowed >= 32*8) {
            testKeyWrap(32, 16, AES256_128);
            testKeyWrap(32, 24, AES256_192);
            testKeyWrap(32, 32, AES256_256);
        }
        System.out.println("All Tests Passed");
    }
}
