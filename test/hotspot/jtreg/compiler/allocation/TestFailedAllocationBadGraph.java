/*
 * Copyright (c) 2022, Red Hat, Inc. All rights reserved.
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
 * bug 8279219
 * @summary C2 crash when allocating array of size too large
 * @requires vm.compiler2.enabled
 * @library /test/lib /
 * @build sun.hotspot.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -ea -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:-BackgroundCompilation TestFailedAllocationBadGraph
 */

import sun.hotspot.WhiteBox;
import java.lang.reflect.Method;
import compiler.whitebox.CompilerWhiteBoxTest;

public class TestFailedAllocationBadGraph {
    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();

    private static long[] array;
    private static int field;
    private static volatile int barrier;

    public static void main(String[] args) throws Exception {
        run("test1");
        run("test2");
    }

    private static void run(String method) throws Exception {
        Method m = TestFailedAllocationBadGraph.class.getDeclaredMethod(method);
        WHITE_BOX.enqueueMethodForCompilation(m, CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION);
        if (!WHITE_BOX.isMethodCompiled(m) || WHITE_BOX.getMethodCompilationLevel(m) != CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION) {
            throw new RuntimeException("should still be compiled");
        }
    }

    private static int test1() {
        int length = Integer.MAX_VALUE;
        try {
            array = new long[length];
        } catch (OutOfMemoryError outOfMemoryError) {
            barrier = 0x42;
            length = field;
        }
        return length;
    }

    private static int test2() {
        int length = -1;
        try {
            array = new long[length];
        } catch (OutOfMemoryError outOfMemoryError) {
            barrier = 0x42;
            length = field;
        }
        return length;
    }
}
