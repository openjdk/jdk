/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8322854
 * @summary Check that the RAM optimization works when there is a memory loop.
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 * @run main/othervm -XX:CompileCommand=compileonly,*TestReduceAllocationAndMemoryLoop*::test*
 *                   -XX:-TieredCompilation -Xbatch
 *                   compiler.c2.TestReduceAllocationAndMemoryLoop
 */

package compiler.c2;

public class TestReduceAllocationAndMemoryLoop {
    public static void main(String[] args) throws Exception {
        // Warmup
        for (int i = 0; i < 50_000; ++i) {
            test(false, 10);
        }

        // Trigger deoptimization
        MyClass obj = test(false, 11);
        if (obj.val != 42) {
            throw new RuntimeException("Test failed, val = " + obj.val);
        }
    }

    static class MyClass {
        final int val;

        public MyClass(int val) {
            this.val = val;
        }
    }

    public static MyClass test(boolean alwaysFalse, int limit) {
        for (int i = 0; ; ++i) {
            MyClass obj = new MyClass(42);
            if (alwaysFalse || i > 10) {
                return obj;
            }
            if (i == limit) {
              return null;
            }
        }
    }
}
