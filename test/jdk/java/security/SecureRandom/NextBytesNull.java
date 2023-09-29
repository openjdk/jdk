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
 * @summary check NPE is thrown for various methods of SecureRandom class,
 *     e.g. SecureRandom(byte[]), nextBytes(byte[]), and setSeed(byte[]).
 * @run main NextBytesNull
 */

import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.SecureRandomSpi;

public class NextBytesNull {

    public static void main(String[] args) throws Exception {
        String test = "SecureRandom(null)";
        try {
            new SecureRandom(null);
            throw new RuntimeException("Error: NPE not thrown for " + test);
        } catch (NullPointerException e) {
            System.out.println("OK, expected NPE thrown for " + test);
        }

        // verify with an Spi impl which does not throw NPE
        SecureRandom sr = SecureRandom.getInstance("S1", new P());
        try {
            sr.nextBytes(null);
            throw new RuntimeException("Error: NPE not thrown");
        } catch (NullPointerException npe) {
            System.out.println("OK, expected NPE thrown for " + test);
        }
        try {
            sr.setSeed(null);
            throw new RuntimeException("Error: NPE not thrown for " + test);
        } catch (NullPointerException npe) {
            System.out.println("OK, expected NPE thrown for " + test);
        }
    }

    public static final class P extends Provider {
        public P() {
            super("P", 1.0d, "Test Provider without Null Check");
            put("SecureRandom.S1", S.class.getName());
        }
    }

    public static final class S extends SecureRandomSpi {
        @Override
        protected void engineSetSeed(byte[] seed) {
        }
        @Override
        protected void engineNextBytes(byte[] bytes) {
        }
        @Override
        protected byte[] engineGenerateSeed(int numBytes) {
            return new byte[numBytes];
        }
    }
}
