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
 * @bug 8381546
 * @summary Verify that SubTypeCheckNode is eliminated when receiver implements
 *          an interface not related to the class being checked against
 * @library /test/lib /
 * @run driver ${test.main.class}
 */

package compiler.types;

import compiler.lib.ir_framework.*;

public class TestSubTypeCheckInterfaceCheck {
    static abstract class B {}
    static final class C extends B {}

    interface I {}
    static final class D implements I {}

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:CompileCommand=compileonly,compiler.types.TestSubTypeCheckInterfaceCheck::*");
    }

    boolean testHelper2(Object o) {
        return true;
    }

    @Test
    @IR(counts = {IRNode.SUBTYPE_CHECK, "1"},
        phase = CompilePhase.AFTER_PARSING)
    boolean test1(Object o) {
        Object o1 = (I) o ;
        if (o1 instanceof B) {
            return testHelper2(o1);
        } else {
            return false;
        }
    }

    @Run(test = "test1")
    boolean runTest() {
        Object[] arr = new Object[] { new C(), new D() };
        for (int i = 0; i < 2; i++){
            Object o = arr[i];
            if (o instanceof I) {
                return test1(o);
            } else {
                return false;
            }
        }
        return true;
    }
}
