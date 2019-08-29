/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import static java.lang.System.out;
import java.security.SecureRandom;
import sun.security.provider.SunEntries;

/**
 * @test
 * @bug 8228613
 * @summary Ensure that the default SecureRandom algo matches
 *     SunEntries.DEF_SECURE_RANDOM_ALGO when SUN provider is used
 * @modules java.base/sun.security.provider
 */
public class DefaultAlgo {

    public static void main(String[] args) throws Exception {
        SecureRandom sr = new SecureRandom();
        String actualAlg = sr.getAlgorithm();
        out.println("Default SecureRandom algo: " + actualAlg);
        if (sr.getProvider().getName().equals("SUN")) {
            // when using Sun provider, compare and check if the algorithm
            // matches SunEntries.DEF_SECURE_RANDOM_ALGO
            if (actualAlg.equals(SunEntries.DEF_SECURE_RANDOM_ALGO)) {
                out.println("Test Passed");
            } else {
                throw new RuntimeException("Failed: Expected " +
                        SunEntries.DEF_SECURE_RANDOM_ALGO);
            }
        } else {
            out.println("Skip test for non-Sun provider: " + sr.getProvider());
        }
    }
}
