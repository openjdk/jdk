/*
 * Copyright (c) 2026 IBM Corporation. All rights reserved.
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

/**
 * @test
 * @bug 8350330
 * @summary C2: PhaseIdealLoop::add_parse_predicate() should mirror GraphKit::add_parse_predicate()
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *      -XX:-BackgroundCompilation -XX:-ShortRunningLongLoop -XX:-UseOnStackReplacement
 *      -XX:CompileOnly=*TestLoopNestTooManyTraps::test1 -XX:LoopMaxUnroll=0
 *      compiler.longcountedloops.TestLoopNestTooManyTraps
 */

package compiler.longcountedloops;

import java.lang.reflect.Method;
import java.util.Objects;
import jdk.test.whitebox.WhiteBox;

public class TestLoopNestTooManyTraps {
    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();

    public static void main(String[] args) throws NoSuchMethodException {
        Method methodTest1 = TestLoopNestTooManyTraps.class.getDeclaredMethod("test1", int.class, long.class);

        for (int j = 0; j < 10; j++) {
            System.out.println("iteration " + j);
            for (int i = 0; i < 20_000; i++) {
                test1(1000, 1000);
            }
            if (!WHITE_BOX.isMethodCompiled(methodTest1)) {
                throw new RuntimeException("test1 should be compiled");
            }
            System.out.println("iteration 2 " + j);
            try {
                test1(10000, 1000);
            } catch (IndexOutOfBoundsException ioobe) {
            }
            if (j <= 1) {
                if (WHITE_BOX.isMethodCompiled(methodTest1)) {
                    throw new RuntimeException("test1 should have deoptimized at iteration " + j);
                }
            } else {
                if (!WHITE_BOX.isMethodCompiled(methodTest1)) {
                    throw new RuntimeException("test1 shouldn't have deoptimized");
                }
            }
        }
    }

    private static void test1(int stop, long length) {
        for (int i = 0; i < stop; i++) {
            Objects.checkIndex(i, length);
        }
    }
}
