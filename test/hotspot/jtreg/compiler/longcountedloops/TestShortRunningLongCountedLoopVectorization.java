/*
 * Copyright (c) 2025, Red Hat, Inc. All rights reserved.
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

package compiler.longcountedloops;
import jdk.internal.misc.Unsafe;

import java.util.Objects;
/*
 * @test
 * @bug 8342692
 * @summary C2: long counted loop/long range checks: don't create loop-nest for short running loops
 * @modules java.base/jdk.internal.misc
 * @run main/othervm -XX:-BackgroundCompilation compiler.longcountedloops.TestShortRunningLongCountedLoopVectorization
 */

public class TestShortRunningLongCountedLoopVectorization {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static volatile int volatileField;

    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            test1();
        }
    }

    static int size = 1024;
    static long longSize = size;
    static int[] intArray = new int[size];

    public static void test1() {
        boolean doIt = true;
        int localSize = Integer.max(Integer.min(size, 10000), 0);
        int i = 0;
        while (true) {
            synchronized (new Object()) {};
            if (i >= localSize) {
                break;
            }
            if (doIt) {
                volatileField = 42;
                doIt = false;
            }
            long j = Objects.checkIndex(i, longSize);
            UNSAFE.putInt(intArray, Unsafe.ARRAY_INT_BASE_OFFSET + j * Unsafe.ARRAY_INT_INDEX_SCALE, 42);
            i++;
        }
    }
};
