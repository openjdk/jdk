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

/*
 * @test
 * @bug 8155191
 * @summary check NPE is thrown for nextBytes(byte[]) and setSeed(byte[])
 *     when using the SecureRandom impls from the SUN provider
 * @run main/othervm NextBytesNull
 */

import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.SecureRandom;

public class NextBytesNull {

    private static String[] SECURE_RANDOM_ALGOS = {
        "NativePRNG", "NativePRNGBlocking", "NativePRNGNonBlocking",
        "DRBG", "SHA1PRNG"
    };

    public static void main(String[] args) throws Exception {
        // check NPE for SecureRandom(byte[])
        try {
            new SecureRandom(null);
            System.out.print("new SecureRandom(null) passed");
        } catch (NullPointerException e) {
            System.out.println("NPE thrown for new SecureRandom(null)");
        }

        for (String srAlg : SECURE_RANDOM_ALGOS) {
            System.out.print("Testing " + srAlg);
            SecureRandom random;
            try {
                random = SecureRandom.getInstance(srAlg, "SUN");
            } catch (NoSuchAlgorithmException e) {
                System.out.println("No support for " + srAlg +
                    "=> skip");
                e.printStackTrace();
                continue;
            }
            // check NPE for nextBytes(byte[])
            try {
                random.nextBytes(null);
                throw new RuntimeException("Fail: should throw NPE");
            } catch (NullPointerException npe) {
                System.out.println(" OK, expected NPE thrown");
            }

            // check NPE for setSeed(byte[])
            try {
                random.setSeed(null);
                throw new RuntimeException("Fail: should throw NPE");
            } catch (NullPointerException npe) {
                System.out.println(" OK, expected NPE thrown");
            }
        }
    }
}
