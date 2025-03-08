/*
 * Copyright (c) 2002, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4628062 4963723 8267319 8288050 8348432
 * @summary Verify that AES and Hmac KeyGenerator supports default
 *      initialization when init is not called
 * @author Valerie Peng
 */
import java.security.*;
import javax.crypto.*;
import java.util.*;

public class Test4628062 {

    // valid key sizes (in bytes); first value is the default key size
    private static final int[] AES_SIZES = { 32, 16, 24 };
    private static final int[] HMACSHA224AND256_SIZES = { 64, 48 };
    private static final int[] HMACSHA384AND512_SIZES = { 128, 112 };
    private static final int[] HMACSHA3_224_SIZES = { 144, 128 };
    private static final int[] HMACSHA3_256_SIZES = { 136, 110 };
    private static final int[] HMACSHA3_384_SIZES = { 104, 88 };
    private static final int[] HMACSHA3_512_SIZES = { 72, 64 };

    record TestData(String algo, int[] validSizes) {
    }

    private static final TestData[] TEST_DATUM = {
        new TestData("AES", AES_SIZES),
        new TestData("HmacSHA224", HMACSHA224AND256_SIZES),
        new TestData("HmacSHA256", HMACSHA224AND256_SIZES),
        new TestData("HmacSHA384", HMACSHA384AND512_SIZES),
        new TestData("HmacSHA512", HMACSHA384AND512_SIZES),
        new TestData("HmacSHA512/224", HMACSHA384AND512_SIZES),
        new TestData("HmacSHA512/256", HMACSHA384AND512_SIZES),
        new TestData("HmacSHA3-224", HMACSHA3_224_SIZES),
        new TestData("HmacSHA3-256", HMACSHA3_256_SIZES),
        new TestData("HmacSHA3-384", HMACSHA3_384_SIZES),
        new TestData("HmacSHA3-512", HMACSHA3_512_SIZES)
    };

    public void execute(String algo, int[] keySizes) throws Exception {
        System.out.println("Testing " + algo);

        KeyGenerator kg = KeyGenerator.getInstance(algo,
                System.getProperty("test.provider.name", "SunJCE"));

        // TEST FIX 4628062
        Key keyWithDefaultSize = kg.generateKey();
        byte[] encoding = keyWithDefaultSize.getEncoded();
        int defKeyLen = encoding.length ;
        if (defKeyLen == 0) {
            throw new Exception("default key length is 0!");
        } else if (defKeyLen != keySizes[0]) {
            throw new Exception("default key length mismatch!");
        }

        // BONUS TESTS
        // 1. call init(int keysize) with various valid key sizes
        //    and see if the generated key is the right size.
        for (int ks : keySizes) {
            kg.init(ks*8); // in bits
            Key key = kg.generateKey();
            if (key.getEncoded().length != ks) {
                throw new Exception("key is generated with the wrong length!");
            }
        }
        // 2. for AES, call init(int keysize) with invalid key size and see
        // if the expected InvalidParameterException is thrown.
        if (algo.equals("AES")) {
            try {
                kg.init(keySizes[0]*8+1);
                throw new Exception("expected IPE not thrown");
            } catch (InvalidParameterException ex) {
                // expected
            } catch (Exception ex) {
                ex.printStackTrace();
                throw new Exception("Wrong exception is thrown instead of IPE!");
            }
        }
        System.out.println("=> Passed!");
    }

    public static void main (String[] args) throws Exception {
        Test4628062 test = new Test4628062();
        for (TestData td : TEST_DATUM) {
            test.execute(td.algo, td.validSizes);
        }
    }
}
