/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.RandomFactory;

/**
 * @test
 * @summary Check that nextExponential() returns non-negative outcomes
 * @bug 8284866
 *
 * @key randomness
 * @library /test/lib
 * @build jdk.test.lib.RandomFactory
 * @run main RandomExponentialTest
 */

public class RandomExponentialTest {

    private static final int SAMPLES = 1_000_000_000;

    public static void main(String[] args) throws Exception {
        var errCount = 0;
        var errSample = Double.NaN;
        var random = RandomFactory.getRandom();
        for (int i = 0; i < SAMPLES; i++) {
            var expVal = random.nextExponential();
            if (!(expVal >= 0.0)) {
                errCount += 1;
                errSample = expVal;
            }
        }
        if (errCount > 0) {
            throw new RuntimeException("%d errors out of %d samples: e.g., %f"
                            .formatted(errCount, SAMPLES, errSample));
        }
    }

}
