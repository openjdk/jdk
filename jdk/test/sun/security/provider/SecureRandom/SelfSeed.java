/*
 * Copyright (c) 1998, 2003, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4168409
 * @summary SecureRandom forces all instances to self-seed, even if a seed is
 *                      provided
 */

import java.security.SecureRandom;

public class SelfSeed {

    private static final int NUM_BYTES = 5;
    private static byte seed[] = { (byte)0xaa, (byte)0x11, (byte)0xa1 };

    public static void main(String[] args) {

        try {
            SecureRandom sr1 = SecureRandom.getInstance("SHA1PRNG");
            sr1.setSeed(seed);
            byte randomBytes[] = new byte[NUM_BYTES];
            sr1.nextBytes(randomBytes);

            SecureRandom sr2 = new SecureRandom(seed);
            if (sr2.getAlgorithm().equals("SHA1PRNG") == false) {
                System.out.println("Default PRNG is not SHA1PRNG, skipping test");
                return;
            }
            byte otherRandomBytes[] = new byte[NUM_BYTES];
            sr2.nextBytes(otherRandomBytes);

            // make sure the random bytes generated are the same
            for (int i = 0; i < NUM_BYTES; i++) {
                if (randomBytes[i] != otherRandomBytes[i])
                    throw new SecurityException("FAILURE: " +
                                        "Returned bytes not equal");
            }

            // success
        } catch (Exception e) {
            throw new SecurityException("FAILURE: " + e.toString());
        }
    }
}
