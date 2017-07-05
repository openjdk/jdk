/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     7088989
 * @summary Ensure the various message digests works correctly
 */
import java.io.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

public class TestDigest extends UcryptoTest {

    private static final String[] MD_ALGOS = {
        "MD5",
        "SHA",
        "SHA-256",
        "SHA-384",
        "SHA-512"
    };

    public static void main(String[] args) throws Exception {
        main(new TestDigest(), null);
    }

    public void doTest(Provider p) {
        boolean testPassed = true;
        byte[] msg = new byte[200];
        (new SecureRandom()).nextBytes(msg);
        String interopProvName = "SUN";

        for (String a : MD_ALGOS) {
            try {
                MessageDigest md, md2;
                try {
                    md = MessageDigest.getInstance(a, p);
                } catch (NoSuchAlgorithmException nsae) {
                    System.out.println("Skipping Unsupported MD algo: " + a);
                    continue;
                }
                md2 = MessageDigest.getInstance(a, interopProvName);
                // Test Interoperability for update+digest calls
                for (int i = 0; i < 3; i++) {
                    md.update(msg);
                    byte[] digest = md.digest();
                    md2.update(msg);
                    byte[] digest2 = md2.digest();
                    if (!Arrays.equals(digest, digest2)) {
                        System.out.println("DIFF1 FAILED for: " + a + " at iter " + i);
                        testPassed = false;
                    }
                }

                // Test Interoperability for digest calls
                md = MessageDigest.getInstance(a, p);
                md2 = MessageDigest.getInstance(a, interopProvName);

                for (int i = 0; i < 3; i++) {
                    byte[] digest = md.digest();
                    byte[] digest2 = md2.digest();
                    if (!Arrays.equals(digest, digest2)) {
                        System.out.println("DIFF2 FAILED for: " + a + " at iter " + i);
                        testPassed = false;
                    }
                }

                // Test Cloning functionality
                md = MessageDigest.getInstance(a, p);
                md2 = (MessageDigest) md.clone(); // clone right after construction
                byte[] digest = md.digest();
                byte[] digest2 = md2.digest();
                if (!Arrays.equals(digest, digest2)) {
                    System.out.println("DIFF-3.1 FAILED for: " + a);
                    testPassed = false;
                }
                md.update(msg);
                md2 = (MessageDigest) md.clone(); // clone again after update call
                digest = md.digest();
                digest2 = md2.digest();
                if (!Arrays.equals(digest, digest2)) {
                    System.out.println("DIFF-3.2 FAILED for: " + a);
                    testPassed = false;
                }
                md2 = (MessageDigest) md.clone(); // clone after digest
                digest = md.digest();
                digest2 = md2.digest();
                if (!Arrays.equals(digest, digest2)) {
                    System.out.println("DIFF-3.3 FAILED for: " + a);
                    testPassed = false;
                }
            } catch(Exception ex) {
                System.out.println("Unexpected Exception: " + a);
                ex.printStackTrace();
                testPassed = false;
            }
        }
        if (!testPassed) {
            throw new RuntimeException("One or more MD test failed!");
        } else {
            System.out.println("MD Tests Passed");
        }
    }
}
