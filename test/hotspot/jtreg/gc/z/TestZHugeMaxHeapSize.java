/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package gc.z;

/**
 * @test
 * @bug 8382070
 * @summary A max heap size large enough to overflow the ZGC address offset
 *          request must be rejected with an error message, not crash the VM
 * @requires vm.gc.Z
 * @library /test/lib
 * @run driver gc.z.TestZHugeMaxHeapSize
 */

import jdk.test.lib.process.ProcessTools;

public class TestZHugeMaxHeapSize {

    private static void test(String option) throws Exception {
        ProcessTools.executeTestJava("-XX:+UseZGC", option, "-version")
                .outputTo(System.out)
                .errorTo(System.out)
                .shouldHaveExitValue(1)
                .shouldContain("Java heap too large");
    }

    public static void main(String[] args) throws Exception {
        // 2^60, large enough to overflow MaxHeapSize * ZVirtualToPhysicalRatio
        // past the largest power of two, with or without NUMA
        test("-XX:MaxHeapSize=1152921504606846976");
        // Value found by the flag fuzzer
        test("-XX:MaxHeapSize=17232779273145073716");
        // MinHeapSize larger than the default max heap size raises MaxHeapSize
        test("-XX:MinHeapSize=17232779273145073716");
    }
}
