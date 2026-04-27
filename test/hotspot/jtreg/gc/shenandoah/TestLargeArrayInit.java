/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 *
 */

/*
 * @test id=adaptive
 * @summary Verify zero-initialization completeness for large arrays under Shenandoah adaptive mode
 * @requires vm.gc.Shenandoah
 * @library /test/lib
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xmx512m -Xms512m
 *      -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=adaptive
 *      TestLargeArrayInit
 */

/*
 * @test id=generational
 * @summary Verify zero-initialization completeness for large arrays under Shenandoah generational mode
 * @requires vm.gc.Shenandoah
 * @library /test/lib
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xmx512m -Xms512m
 *      -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=adaptive -XX:ShenandoahGCMode=generational
 *      TestLargeArrayInit
 */

/*
 * @test id=compact-on
 * @summary Verify zero-initialization completeness for large arrays with compact object headers enabled
 * @requires vm.gc.Shenandoah
 * @library /test/lib
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xmx512m -Xms512m
 *      -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=adaptive
 *      -XX:+UseCompactObjectHeaders
 *      TestLargeArrayInit
 */

/*
 * @test id=compact-off
 * @summary Verify zero-initialization completeness for large arrays with compact object headers disabled
 * @requires vm.gc.Shenandoah
 * @library /test/lib
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xmx512m -Xms512m
 *      -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=adaptive
 *      -XX:-UseCompactObjectHeaders
 *      TestLargeArrayInit
 */

/**
 *
 * Allocates large byte[], int[], long[], and Object[] arrays whose sizes span
 * multiple 64K-word segments (~100MB each), then verifies every element is
 * zero (or null for reference arrays).
 */
public class TestLargeArrayInit {

    // ~100MB for each array type
    static final int BYTE_LEN  = 100 * 1024 * 1024;          // 100M elements
    static final int INT_LEN   = 25 * 1024 * 1024;           // 25M elements * 4 bytes = 100MB
    static final int LONG_LEN  = 12 * 1024 * 1024 + 512*1024; // ~100MB in longs
    static final int OBJ_LEN   = 12 * 1024 * 1024 + 512*1024; // ~100MB in refs (8 bytes each on 64-bit)

    public static void main(String[] args) {
        testByteArray();
        testIntArray();
        testLongArray();
        testObjectArray();
        System.out.println("TestLargeArrayInit PASSED");
    }

    static void testByteArray() {
        byte[] arr = new byte[BYTE_LEN];
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] != 0) {
                throw new RuntimeException("byte[] not zero at index " + i + ": " + arr[i]);
            }
        }
    }

    static void testIntArray() {
        int[] arr = new int[INT_LEN];
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] != 0) {
                throw new RuntimeException("int[] not zero at index " + i + ": " + arr[i]);
            }
        }
    }

    static void testLongArray() {
        long[] arr = new long[LONG_LEN];
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] != 0L) {
                throw new RuntimeException("long[] not zero at index " + i + ": " + arr[i]);
            }
        }
    }

    static void testObjectArray() {
        Object[] arr = new Object[OBJ_LEN];
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] != null) {
                throw new RuntimeException("Object[] not null at index " + i + ": " + arr[i]);
            }
        }
    }
}
