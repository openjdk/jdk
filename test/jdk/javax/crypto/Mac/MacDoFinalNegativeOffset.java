/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 9999901
 * @summary Mac.doFinal(byte[], int) should reject null output and negative
 *          outOffset with IllegalArgumentException, consistent with
 *          Cipher.doFinal() and Mac.update()
 * @run main MacDoFinalNegativeOffset
 */

import javax.crypto.Mac;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.SecretKeySpec;

public class MacDoFinalNegativeOffset {

    public static void main(String[] args) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        byte[] keyBytes = new byte[32];
        SecretKeySpec key = new SecretKeySpec(keyBytes, "HmacSHA256");

        int macLen = mac.getMacLength();
        byte[] output = new byte[macLen + 64];

        // Test 1: offset = -1 should throw IllegalArgumentException
        mac.init(key);
        mac.update(new byte[]{1, 2, 3});
        try {
            mac.doFinal(output, -1);
            throw new RuntimeException("Expected IllegalArgumentException for offset=-1");
        } catch (IllegalArgumentException e) {
            System.out.println("PASS: offset=-1 threw IllegalArgumentException");
        }

        // Test 2: offset = Integer.MIN_VALUE should throw IllegalArgumentException
        mac.init(key);
        mac.update(new byte[]{1, 2, 3});
        try {
            mac.doFinal(output, Integer.MIN_VALUE);
            throw new RuntimeException("Expected IllegalArgumentException for MIN_VALUE");
        } catch (IllegalArgumentException e) {
            System.out.println("PASS: offset=MIN_VALUE threw IllegalArgumentException");
        }

        // Test 3: null output should throw IllegalArgumentException
        mac.init(key);
        mac.update(new byte[]{1, 2, 3});
        try {
            mac.doFinal(null, 0);
            throw new RuntimeException("Expected IllegalArgumentException for null output");
        } catch (IllegalArgumentException e) {
            System.out.println("PASS: null output threw IllegalArgumentException");
        }

        // Test 4: buffer too small should throw ShortBufferException
        mac.init(key);
        mac.update(new byte[]{1, 2, 3});
        try {
            mac.doFinal(new byte[macLen - 1], 0);
            throw new RuntimeException("Expected ShortBufferException for small buffer");
        } catch (ShortBufferException e) {
            System.out.println("PASS: small buffer threw ShortBufferException");
        }

        // Test 5: valid offset = 0 works
        mac.init(key);
        mac.update(new byte[]{1, 2, 3});
        mac.doFinal(output, 0);
        System.out.println("PASS: offset=0 succeeded normally");

        // Test 6: valid offset at end of buffer works
        mac.init(key);
        mac.update(new byte[]{1, 2, 3});
        mac.doFinal(output, 64);
        System.out.println("PASS: offset=64 succeeded normally");
    }
}
