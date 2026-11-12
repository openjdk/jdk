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

/*
 * @test
 * @bug 8391449
 * @summary Test rollback of value object scalarization at safepoints by EA.
 * @requires vm.compiler2.enabled
 * @enablePreview
 * @library /test/lib
 * @run main ${test.main.class}
 * @run main/othervm -Xbatch -XX:CompileCommand=compileonly,${test.main.class}::test
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressEliminateAllocations
 *                   -XX:StressSeed=339978925 ${test.main.class}
 */

package compiler.valhalla.valuetypes;

import jdk.test.lib.Asserts;

public class TestScalarizationRollback {
    static value class MyValue {
        int x = 42;

        MyValue(boolean b1, boolean b2) {
            // 'this' will be scalarized by EA in both traps below because it's still in larval state.
            // -XX:+StressEliminateAllocations will trigger a rollback after one trap was already processed.
            if (b1) {
                throw new RuntimeException("Should not reach here");
            }
            if (b2) {
                throw new RuntimeException("Should not reach here");
            }
        }
    }

    static MyValue test(boolean b1, boolean b2) {
        return new MyValue(b1, b2);
    }

    public static void main(String[] args) {
        for (int i = 0; i < 10_000; i++) {
            MyValue val = test(false, false);
            Asserts.assertEquals(val.x, 42);
        }
    }
}

