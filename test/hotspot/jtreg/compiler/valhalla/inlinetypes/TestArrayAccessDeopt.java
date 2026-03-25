/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Verify that certain array accesses do not trigger deoptimization.
 * @requires vm.debug == true
 * @library /test/lib
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main compiler.valhalla.inlinetypes.TestArrayAccessDeopt
 */

package compiler.valhalla.inlinetypes;

import java.util.Objects;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;

@LooselyConsistentValue
value class MyValue1ArrayAccessDeopt {
    public int x = 0;
}

public class TestArrayAccessDeopt {

    public static void test1(Object[] va, Object vt) {
        va[0] = vt;
    }

    public static void test2(Object[] va, MyValue1ArrayAccessDeopt vt) {
        va[0] = vt;
    }

    public static void test3(MyValue1ArrayAccessDeopt[] va, Object vt) {
        va[0] = (MyValue1ArrayAccessDeopt)vt;
    }

    public static void test4(MyValue1ArrayAccessDeopt[] va, MyValue1ArrayAccessDeopt vt) {
        va[0] = vt;
    }

    public static void test5(Object[] va, MyValue1ArrayAccessDeopt vt) {
        va[0] = vt;
    }

    public static void test6(MyValue1ArrayAccessDeopt[] va, Object vt) {
        va[0] = (MyValue1ArrayAccessDeopt)Objects.requireNonNull(vt);
    }

    public static void test7(MyValue1ArrayAccessDeopt[] va, MyValue1ArrayAccessDeopt vt) {
        va[0] = vt;
    }

    public static void test8(MyValue1ArrayAccessDeopt[] va, MyValue1ArrayAccessDeopt vt) {
        va[0] = vt;
    }

    public static void test9(MyValue1ArrayAccessDeopt[] va, MyValue1ArrayAccessDeopt vt) {
        va[0] = Objects.requireNonNull(vt);
    }

    public static void test10(Object[] va) {
        va[0] = null;
    }

    public static void test11(MyValue1ArrayAccessDeopt[] va) {
        va[0] = null;
    }

    static public void main(String[] args) throws Exception {
        if (args.length == 0) {
            // Run test in new VM instance
            String[] arg = {"--enable-preview", "--add-exports", "java.base/jdk.internal.vm.annotation=ALL-UNNAMED", "--add-exports", "java.base/jdk.internal.value=ALL-UNNAMED",
                            "-XX:CompileCommand=quiet", "-XX:CompileCommand=compileonly,compiler.valhalla.inlinetypes.TestArrayAccessDeopt::test*", "-XX:-UseArrayLoadStoreProfile",
                            "-XX:+TraceDeoptimization", "-Xbatch", "-XX:-MonomorphicArrayCheck", "-Xmixed", "-XX:+ProfileInterpreter", "compiler.valhalla.inlinetypes.TestArrayAccessDeopt", "run"};
            OutputAnalyzer oa = ProcessTools.executeTestJava(arg);
            oa.shouldHaveExitValue(0);
            String output = oa.getOutput();
            oa.shouldNotContain("UNCOMMON TRAP");
        } else {
            MyValue1ArrayAccessDeopt[] va = (MyValue1ArrayAccessDeopt[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1ArrayAccessDeopt.class, 1, new MyValue1ArrayAccessDeopt());
            MyValue1ArrayAccessDeopt[] vaB = new MyValue1ArrayAccessDeopt[1];
            MyValue1ArrayAccessDeopt vt = new MyValue1ArrayAccessDeopt();
            for (int i = 0; i < 10_000; ++i) {
                test1(va, vt);
                test1(vaB, vt);
                test1(vaB, null);
                test2(va, vt);
                test2(vaB, vt);
                test2(vaB, null);
                test3(va, vt);
                test3(vaB, vt);
                test3(vaB, null);
                test4(va, vt);
                test4(vaB, vt);
                test4(vaB, null);
                test5(va, vt);
                test5(vaB, vt);
                test6(va, vt);
                try {
                    test6(va, null);
                    throw new RuntimeException("NullPointerException expected");
                } catch (NullPointerException npe) {
                    // Expected
                }
                test7(va, vt);
                test8(va, vt);
                test8(vaB, vt);
                test9(va, vt);
                try {
                    test9(va, null);
                    throw new RuntimeException("NullPointerException expected");
                } catch (NullPointerException npe) {
                    // Expected
                }
                test10(vaB);
                test11(vaB);
            }
        }
    }
}
