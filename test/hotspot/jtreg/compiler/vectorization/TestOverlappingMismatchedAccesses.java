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
 * @bug 8300258
 * @modules java.base/jdk.internal.misc
 *
 * @run main/othervm -XX:-TieredCompilation -Xbatch TestOverlappingMismatchedAccesses
 */

import jdk.internal.misc.Unsafe;

public class TestOverlappingMismatchedAccesses {
    static int N = 50;
    static int gold[] = new int[N];

    static Unsafe unsafe = Unsafe.getUnsafe();

    public static void main(String[] strArr) {
        init(gold);
        test(gold);
        for (int i = 0; i < 10_000; i++){
            int[] data = new int[N];
            init(data);
            test(data);
            verify(data, gold);
        }
    }

    static void test(int[] data) {
        for (int i = 2; i < N-2; i++) {
            int v = data[i];
            unsafe.putFloat(data, unsafe.ARRAY_BYTE_BASE_OFFSET + 4 * i + 8, v + 5);
        }
    }

    static void init(int[] data) {
        for (int j = 0; j < N; j++) {
            data[j] = j;
        }
    }

    static void verify(int[] data, int[] gold) {
        for (int i = 0; i < N; i++) {
            if (data[i] != gold[i]) {
                throw new RuntimeException(" Invalid result: dataI[" + i + "]: " + data[i] + " != " + gold[i]);
            }
        }
    }
}
