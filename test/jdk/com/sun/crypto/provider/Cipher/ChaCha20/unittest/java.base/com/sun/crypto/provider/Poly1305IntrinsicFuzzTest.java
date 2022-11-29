/*
 * Copyright (c) 2022, Intel Corporation. All rights reserved.
 *
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

package com.sun.crypto.provider;

import java.nio.ByteBuffer;
import java.util.Arrays;

import javax.crypto.spec.SecretKeySpec;

// This test case relies on the fact that single-byte Poly1305.engineUpdate(byte) does not have an intrinsic
// In this way we can compare if the intrinsic and pure java produce same result
// This test case is NOT entirely deterministic, it uses a random seed for pseudo-random number generator
// If a failure occurs, hardcode the seed to make the test case deterministic
public class Poly1305IntrinsicFuzzTest {
        public static void main(String[] args) throws Exception {
                //Note: it might be useful to increase this number during development of new Poly1305 intrinsics
                final int repeat = 100;
                for (int i = 0; i < repeat; i++) {
                        run();
                }
                System.out.println("Fuzz Success");
        }

        public static void run() throws Exception {
                java.util.Random rnd = new java.util.Random();
                long seed = rnd.nextLong();
                rnd.setSeed(seed);

                byte[] key = new byte[32];
                rnd.nextBytes(key);
                int msgLen = rnd.nextInt(128, 4096); // x86_64 intrinsic requires 256 bytes minimum
                byte[] message = new byte[msgLen];

                Poly1305 authenticator = new Poly1305();
                Poly1305 authenticatorSlow = new Poly1305();
                if (authenticator.engineGetMacLength() != 16) {
                        throw new RuntimeException("The length of Poly1305 MAC must be 16-bytes.");
                }

                authenticator.engineInit(new SecretKeySpec(key, 0, 32, "Poly1305"), null);
                authenticatorSlow.engineInit(new SecretKeySpec(key, 0, 32, "Poly1305"), null);

                if (rnd.nextBoolean()) {
                        // Prime just the buffer and/or accumulator (buffer can keep at most 16 bytes from previous engineUpdate)
                        int initDataLen = rnd.nextInt(8, 24);
                        authenticator.engineUpdate(message, 0, initDataLen);
                        slowUpdate(authenticatorSlow, message, 0, initDataLen);
                }

                if (rnd.nextBoolean()) {
                        // Multiple calls to engineUpdate
                        authenticator.engineUpdate(message, 0, message.length);
                        slowUpdate(authenticatorSlow, message, 0, message.length);
                }

                authenticator.engineUpdate(message, 0, message.length);
                slowUpdate(authenticatorSlow, message, 0, message.length);

                byte[] tag = authenticator.engineDoFinal();
                byte[] tagSlow = authenticatorSlow.engineDoFinal();

                if (!Arrays.equals(tag, tagSlow)) {
                        throw new RuntimeException("[Seed "+seed+"] Tag mismatch: " + Arrays.toString(tag) + " != " + Arrays.toString(tagSlow));
                }
        }

        static void slowUpdate(Poly1305 authenticator, byte[] message, int offset, int len) {
                len = Math.min(message.length, offset + len);
                for (int i = offset; i < len; i++) {
                        authenticator.engineUpdate(message[i]);
                }
        }
}
