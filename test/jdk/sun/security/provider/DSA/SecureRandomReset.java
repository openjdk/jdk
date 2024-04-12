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
 * @bug 8308474
 * @summary Test that calling initSign resets RNG
 */

import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.util.Arrays;
import java.util.Random;


public class SecureRandomReset {

    public static void main(String[] args) throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("DSA");
        PrivateKey sk = g.generateKeyPair().getPrivate();
        Signature s = Signature.getInstance("SHA256withDSA");

        // Initialize deterministic RNG and sign
        s.initSign(sk, deterministic());
        byte[] sig1 = s.sign();

        // Re-initialize deterministic RNG and sign
        s.initSign(sk, deterministic());
        byte[] sig2 = s.sign();

        if (!Arrays.equals(sig1,sig2)) {
            System.out.println("Expected equal signatures");
            throw new RuntimeException("initSign not properly resetting RNG");
        }
    }

    static SecureRandom deterministic() {
        return new SecureRandom() {
            final Random r = new Random(0);
            @Override
            public void nextBytes(byte[] bytes) {
                r.nextBytes(bytes);
            }
        };
    }
}