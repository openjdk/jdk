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

package compiler.vectorapi;

import compiler.lib.verify.Verify;
import jdk.incubator.vector.ShortVector;
import jdk.internal.vm.vector.VectorSupport;

/*
 * @test id=Test1
 * @bug 8393338
 * @summary Out-of-range constant vector slice index must throw, not crash C2.
 * @modules java.base/jdk.internal.vm.vector
 *          jdk.incubator.vector
 * @library /test/lib /
 * @run main/othervm -Xcomp -XX:-TieredCompilation
 *                   -XX:CompileCommand=compileonly,${test.main.class}$Test1::test
 *                   -XX:CompileCommand=dontinline,java.util.Objects::checkIndex
 *                   -XX:CompileCommand=dontinline,jdk.internal.util.Preconditions::checkIndex
 *                   ${test.main.class} Test1
 * @run main ${test.main.class} Test1
 */

/*
 * @test id=Test2
 * @bug 8393338
 * @summary Out-of-range constant vector slice index must throw, not crash C2.
 * @modules java.base/jdk.internal.vm.vector
 *          jdk.incubator.vector
 * @library /test/lib /
 * @run main/othervm -Xbatch -XX:-TieredCompilation
 *                   -XX:CompileCommand=compileonly,${test.main.class}$Test2::test
 *                   ${test.main.class} Test2
 * @run main ${test.main.class} Test2
 */

public class TestVectorSliceOutOfBounds {
    public static class Test1 {
        static void test(short[] a, short[] b) {
            try {
                ShortVector.fromArray(ShortVector.SPECIES_512, a, 0)
                           .slice(-271, ShortVector.fromArray(ShortVector.SPECIES_512, b, 0))
                           .intoArray(a, 0);
                throw new RuntimeException("missing IndexOutOfBoundsException");
            } catch (IndexOutOfBoundsException e) {
            }
        }

        public static void main(String[] args) {
            short[] a = new short[32];
            ShortVector.fromArray(ShortVector.SPECIES_512, a, 0)
                       .slice(0, ShortVector.fromArray(ShortVector.SPECIES_512, a, 0));
            test(a, a);
        }
    }

    public static class Test2 {
        static ShortVector test() {
            ShortVector sv = ShortVector.broadcast(ShortVector.SPECIES_128, (short) -5);
            return (ShortVector) VectorSupport.sliceOp(128, sv.getClass(), VectorSupport.LT_SHORT,
                                                        8, sv, sv, (v1, v2, v3) -> v2);
        }

        public static void main(String[] args) {
            for (int i = 0; i < 10_000; i++) {
                test();
            }
            Verify.checkEQ(test().lane(0), (short) -5);
        }
    }

    public static void main(String[] args) {
        switch (args[0]) {
            case "Test1" -> Test1.main(args);
            case "Test2" -> Test2.main(args);
            default -> throw new IllegalArgumentException(args[0]);
        }
    }
}
