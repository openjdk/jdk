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
package compiler.parsing;

import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8382936
 * @library /test/lib
 * @summary Test that aastore generates a type safe graph
 * @run main ${test.main.class}
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -Xcomp -XX:-TieredCompilation
 *                   -XX:CompileOnly=${test.main.class}::test* -XX:-MonomorphicArrayCheck
 *                   -XX:+AlwaysIncrementalInline -XX:+UseCompressedOops ${test.main.class}
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -Xcomp -XX:-TieredCompilation
 *                   -XX:CompileOnly=${test.main.class}::test* -XX:-MonomorphicArrayCheck
 *                   -XX:+AlwaysIncrementalInline -XX:-UseCompressedOops ${test.main.class}
 */
public class TestAastoreTypeSafe {
    private static final class A {
        int v;
    }

    private static class B {
    }

    private static final class C extends B {
        int v;
    }

    public static void main(String[] args) {
        {
            A[] array = new A[1];
            A element = new A();
            for (int i = 0; i < 10; i++) {
                test1(true, array, element, element);
                Asserts.assertEQ(0, element.v);
            }
        }

        {
            C[] array = new C[1];
            C element = new C();
            for (int i = 0; i < 10; i++) {
                test2(true, array, element, element);
                Asserts.assertEQ(0, element.v);
            }
        }
    }

    // The array load will be folded, but the element type is lost in the graph. This leads to the
    // scheduler missing the dependency between the load from the return value of aaload and the
    // store into alias.v. The load will then be put late just before the second store to alias.v,
    // which is an invalid schedule.
    private static void test1(boolean b, A[] array, Object element, A alias) {
        aastoreA(array, element);
        int v = aaloadA(array).v;
        alias.v = 1;
        if (b) {
            alias.v = v;
        }
    }

    private static void aastoreA(A[] array, Object element) {
        // This forces the compiler to try storing an Object into an A[]. Otherwise, doing
        // array[0] = (A)element will make it so that the stored value being an A already.
        ((Object[]) array)[0] = element;
    }

    private static A aaloadA(A[] array) {
        return array[0];
    }

    // Similar to above, but the store cannot be truly type safe because the exact array type is
    // unknown
    private static void test2(boolean b, B[] array, Object element, C alias) {
        aastoreB(array, element);
        int v = aaloadC(array).v;
        alias.v = 1;
        if (b) {
            alias.v = v;
        }
    }

    private static void aastoreB(B[] array, Object element) {
        ((Object[]) array)[0] = element;
    }

    private static C aaloadC(B[] array) {
        return ((C[]) array)[0];
    }
}
