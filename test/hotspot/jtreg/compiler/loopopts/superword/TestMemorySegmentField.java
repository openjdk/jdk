/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package compiler.loopopts.superword;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Utils;

import java.util.Random;
import java.lang.foreign.*;

/*
 * @test
 * @bug 8356176
 * @summary Test vectorization of loops over MemorySegment stored in field
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegmentField
 */

public class TestMemorySegmentField {
    static int SIZE = 10_000;
    static MemorySegment MS = Arena.ofAuto().allocate(SIZE * 4);

    public static void main(String[] args) {
        TestFramework f = new TestFramework();
        f.addFlags("-XX:+IgnoreUnrecognizedVMOptions");
        f.addScenarios(new Scenario(0, "-XX:-AlignVector", "-XX:-ShortRunningLongLoop"),
                       new Scenario(1, "-XX:+AlignVector", "-XX:-ShortRunningLongLoop"),
                       new Scenario(2, "-XX:-AlignVector", "-XX:+ShortRunningLongLoop"),
                       new Scenario(3, "-XX:+AlignVector", "-XX:+ShortRunningLongLoop"));
        f.start();
    }

    static int zeroInvarI = 0;
    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
                  IRNode.ADD_VB,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    public static void testFields() {
        int invar = zeroInvarI;
        for (long i = 0; i < MS.byteSize(); i++) {
            long adr = (long)(i) + (long)(invar);
            byte v = MS.get(ValueLayout.JAVA_BYTE, adr);
            MS.set(ValueLayout.JAVA_BYTE, adr, (byte)(v + 1));
        }
    }
}
