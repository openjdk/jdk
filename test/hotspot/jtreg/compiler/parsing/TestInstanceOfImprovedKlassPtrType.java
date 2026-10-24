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
 * @bug 8387197
 * @summary Verify that improving klass_ptr_type in GraphKit::gen_instanceof() allows
 *          eliminating SubTypeCheckNode when the receiver implements an interface
 *          unrelated to the checked class.
 * @library /test/lib /
 * @run driver ${test.main.class}
 */

package compiler.parsing;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

public class TestInstanceOfImprovedKlassPtrType {
    static abstract class B {}
    static final class C extends B {}

    interface I {}
    static class D implements I {}
    static class E implements I {}

    public static void main(String[] args) {
        TestFramework.run();
    }

    @DontInline
    int testHelper2(Object o) {
        return 1;
    }

    @Test
    @IR(counts = {IRNode.SUBTYPE_CHECK, "1"},
        phase = CompilePhase.AFTER_PARSING)
    int test1(Object o) {
        Object o1 = (I) o;
        if (o1 instanceof B) {
            return testHelper2(o1);
        } else {
            return 2;
        }
    }

    @Run(test = "test1")
    @Warmup(0)
    void runTest() {
        int sum = 0;
        Object[] arr = new Object[] {new C(), new D(), new E()};
        for (int i = 0; i < 3; i++){
            Object o = arr[i];
            if (o instanceof I) {
                sum += test1(o);
            } else {
                sum += 3;
            }
        }
        Asserts.assertEquals(sum, 7);
        return;
    }
}
