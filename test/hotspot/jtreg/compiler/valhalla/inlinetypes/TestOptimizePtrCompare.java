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
 * @bug 8377480
 * @summary [lworld] incorrect execution due to EA pointer comparison optimization at scalarized call
 * @library /test/lib /
 * @enablePreview
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @run main ${test.main.class}
 */

package compiler.valhalla.inlinetypes;
import compiler.lib.ir_framework.*;

public class TestOptimizePtrCompare {
    public static void main(String[] args) {
        TestFramework.runWithFlags("--enable-preview");
    }

    @Test
    @IR(failOn = {IRNode.CMP_P})
    public static void test1() {
        Object notUsed = new Object(); // make sure EA runs
        Object arg = null;
        Object res = notInlined1(arg);
        if (res != null) {
            throw new RuntimeException("never taken");
        }
    }

    @DontInline
    static Object notInlined1(Object o) {
        return o;
    }

    static value class MyValue {
        Object o;

        MyValue(Object o) {
            this.o = o;
        }
    }

    @Test
    @IR(counts = {IRNode.CMP_P, "1"})
    public static void test2() {
        Object notUsed = new Object(); // make sure EA runs
        MyValue arg = new MyValue(null);
        MyValue res = notInlined2(arg);
        if (res.o != null) {
            throw new RuntimeException("never taken");
        }
    }

    @DontInline
    static MyValue notInlined2(MyValue v) {
        return v;
    }
}
