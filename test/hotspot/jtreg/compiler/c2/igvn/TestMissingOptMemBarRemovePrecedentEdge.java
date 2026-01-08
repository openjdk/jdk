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
 * @bug JDK-8367627
 * @summary When we load from a volatile field, a MemBarAcquire node gets inserted
 *          after the load. It happens that the load gets converted to a constant
 *          during the initial _gvn.transform(...) call. The MemBarAcquire node
 *          ends up with a constant node as its Precedent input, and this should
 *          trigger MemBar::Ideal if the MemBar is the only use of the constant.
 *          This test ensures that this optimization is not missed.
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions
 *      -Xcomp -XX:-TieredCompilation
 *      -XX:CompileCommand=compileonly,${test.main.class}::test*
 *      -XX:VerifyIterativeGVN=1110
 *      -XX:-EliminateAutoBox
 *      ${test.main.class}
  * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions
 *      -Xcomp -XX:-TieredCompilation
 *      -XX:CompileCommand=compileonly,${test.main.class}::test*
 *      -XX:VerifyIterativeGVN=1110
 *      -XX:-EliminateAutoBox
 *      -XX:-DoEscapeAnalysis
 *      ${test.main.class}
  * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions
 *      -Xcomp -XX:-TieredCompilation
 *      -XX:CompileCommand=compileonly,${test.main.class}::test*
 *      -XX:VerifyIterativeGVN=1110
 *      -XX:-EliminateAutoBox
 *      -XX:-DoEscapeAnalysis
 *      -XX:+AlwaysIncrementalInline
 *      ${test.main.class}
 * @run main ${test.main.class}
 *
 */

package compiler.c2.igvn;

public class TestMissingOptMemBarRemovePrecedentEdge {
    static class MyClass {
        volatile long l;
    }

    static class MyClass2 {
        volatile MyClass o;
    }

    static int test0() {
        var c = new MyClass();
        // the conversion ensures that the ConL node only has one use
        // in the end, which triggers the optimization
        return (int) c.l;
    }

    static int test1() {
        var c = new MyClass2();
        // the Load gets folded to a ConP, and gets added to the worklist
        // because the basic type is T_OBJECT and there is specific logic
        // in GraphKit::make_load.
        // However this only happens if escape analysis is enabled
        if (c.o == null) return 0;
        else return 1;
    }

    public static void main(String[] args) {
        new MyClass();
        new MyClass2();
        test0();
        test1();
    }
}