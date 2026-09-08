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

package compiler.valhalla.inlinetypes;

import compiler.lib.ir_framework.Check;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8389623
 * @summary Test scalar replacement when delayed inlining hides exact types.
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.vm.annotation
 * @run driver ${test.main.class}
 * @run driver ${test.main.class} delayinline
 */
public class TestLateInliningPreventsScalarization {
    static value class MyValue2 {
        int x;

        MyValue2(int x) {
            this.x = x;
        }
    }

    @LooselyConsistentValue
    static value class MyValue1 {
        MyValue2 v;

        MyValue1(MyValue2 v) {
            this.v = v;
        }
    }

    @Test
    @IR(failOn = {IRNode.LOAD_OF_CLASS, "MyValue2"})
    static int test() {
        MyValue1 val = new MyValue1(new MyValue2(1));
        for (int i = 0; i < 10; ++i) {
            if ((i & 1) == 0) {
                val = new MyValue1(new MyValue2(i));
            }
        }
        return val.v.x;
    }

    @Check(test = "test")
    static void checkTest(int result) {
        Asserts.assertEquals(result, 8);
    }

    public static void main(String[] args) {
        TestFramework framework = new TestFramework();
        framework.addFlags("--enable-preview", "--add-exports", "java.base/jdk.internal.vm.annotation=ALL-UNNAMED");
        if (args.length > 0) {
            Asserts.assertEquals(args[0], "delayinline");
            framework.addFlags("-XX:CompileCommand=delayinline,*MyValue1::<init>");
        }
        framework.start();
    }
}

