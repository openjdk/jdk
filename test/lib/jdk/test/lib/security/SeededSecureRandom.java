/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.test.lib.security;

import java.security.SecureRandom;
import java.util.Random;

/**
 * A deterministic SecureRandom with a seed.
 * <p>
 * Users can provide the seed with the system property "secure.random.seed".
 * Otherwise, it's a random value. Usually, a test runs without this system
 * property and the random seed is printed out. When it fails, set the
 * system property to this recorded seed to reproduce the failure.
 */
public class SeededSecureRandom extends SecureRandom {

    private final Random rnd;

    public static long seed() {
        String value = System.getProperty("secure.random.seed");
        long seed = value != null
                ? Long.parseLong(value)
                : new Random().nextLong();
        System.out.println("SeededSecureRandom: seed = " + seed);
        return seed;
    }

    public SeededSecureRandom(long seed) {
        rnd = new Random(seed);
    }

    public static SeededSecureRandom one() {
        return new SeededSecureRandom(seed());
    }

    @Override
    public void nextBytes(byte[] bytes) {
        rnd.nextBytes(bytes);
    }

    @Override
    public byte[] generateSeed(int numBytes) {
        var out = new byte[numBytes];
        rnd.nextBytes(out);
        return out;
    }
}
