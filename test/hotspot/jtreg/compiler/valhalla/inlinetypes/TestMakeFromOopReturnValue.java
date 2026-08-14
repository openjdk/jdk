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

import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8380434
 * @summary When trying to predict a virtual call that returns an inline type,
 *          we want to make sure to both branches (inlined call and dynamic call)
 *          result in an InlineType so that we do not accidentally try to use
 *          an unbuffered InlineType as an oop.
 * @library /test/lib /
 * @enablePreview
 * @run main/othervm -XX:+UnlockExperimentalVMOptions
 *                   -Xbatch -XX:-TieredCompilation
 *                   -XX:PerMethodSpecTrapLimit=0 -XX:PerMethodTrapLimit=0
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test
 *                   ${test.main.class}
 * @run main ${test.main.class}
 */

value class MyValueRetType {
    int i1 = 42;
    int i2 = 43;
    int i3 = 44;
    int i4 = 45;
    int i5 = 46;
    int i6 = 47;
    int i7 = 48;
}

class A {
    public MyValueRetType virtual() {
        return new MyValueRetType();
    }
}

class B extends A {
    @Override
    public MyValueRetType virtual() {
        return new MyValueRetType();
    }
}

public class TestMakeFromOopReturnValue {
    public static void test() {
        // B needs to be loaded so that we don't get an exact type for the receiver in the OSR compilation
        new B();
        A a = new A();

        // We want an OSR compilation here
        for (int i = 0; i < 100_000; i++) {
            Asserts.assertEquals(a.virtual(), new MyValueRetType());
        }
    }

    public static void main(String[] args) {
        test();
    }
}