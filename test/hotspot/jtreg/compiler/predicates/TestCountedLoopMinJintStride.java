/*
 * Copyright (c) 2024, Red Hat, Inc. All rights reserved.
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
 * @bug 8328822
 * @summary C2: "negative trip count?" assert failure in profile predicate code
 * @run main/othervm -XX:-TieredCompilation -XX:-BackgroundCompilation -XX:-UseOnStackReplacement -XX:CompileOnly=TestCountedLoopMinJintStride::test1 TestCMoveAndIf
 */

import java.util.Objects;

public class TestCountedLoopMinJintStride {
    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            test1(Integer.MAX_VALUE, Long.MAX_VALUE, 0);
            testHelper(100, -1, Long.MAX_VALUE, 0);
        }
    }

    private static void test1(int stop, long range, int start) {
        testHelper(stop, Integer.MIN_VALUE, range, start);
    }

    private static void testHelper(int stop, int stride, long range, int start) {
        for (int i = stop; i >= start; i += stride) {
            Objects.checkIndex(i, range);
        }
    }
}
