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

import jdk.test.lib.Asserts;

/*
 * @test
 * @library /test/lib
 * @run main/othervm jdk.test.lib.security.SeededSecureRandomTest
 */
public class SeededSecureRandomTest {

    private static final String PROP = "secure.random.seed";

    public static void main(String[] args) throws Exception {
        try {
            System.clearProperty(PROP);
            Asserts.assertNE(get(), get()); // random seed (different)
            Asserts.assertEQ(get(1L), get(1L)); // same seed
            Asserts.assertEQ(get(10L), get(10L)); // same seed
            Asserts.assertNE(get(1L), get(10L)); // different seed

            System.setProperty(PROP, "10");
            Asserts.assertEQ(get(), get()); // seed set by system property
            Asserts.assertNE(get(), get(1L)); // seed set not system property
            Asserts.assertEQ(get(), get(10L)); // seed set same as system property
            Asserts.assertEQ(get(1L), get(1L)); // same seed
            Asserts.assertEQ(get(10L), get(10L)); // same seed
            Asserts.assertNE(get(1L), get(10L)); // different seed
        } finally {
            System.clearProperty(PROP);
        }
    }

    static int get() {
        return new SeededSecureRandom(SeededSecureRandom.seed()).nextInt();
    }

    static int get(long seed) {
        return new SeededSecureRandom(seed).nextInt();
    }
}
