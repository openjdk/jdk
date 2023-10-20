/*
 * Copyright (c) 2023, Red Hat, Inc. All rights reserved.
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

package compiler.c2.irTests;

import compiler.lib.ir_framework.*;

/*
 * @test
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestArrayElementTypeLoad
 */

public class TestArrayElementTypeLoad {
    public static void main(String[] args) {
        TestFramework.run();
    }

    static final A[] array = new A[1];

    @Test
    @IR(phase = { CompilePhase.ITER_GVN1 }, failOn = { IRNode.SUBTYPE_CHECK })
    public static void test1(A a) {
        array[0] = a;
    }

    @Run(test = "test1")
    private void test1Runner() {
        test1(new A());
        test1(new B());
    }

    static class A {
    }

    static class B extends A {
    }
}
