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

package compiler.valhalla.valuetypes;

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;

import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8394860
 * @summary C1 must not delay flattened field and array accesses across basic block boundaries.
 * @enablePreview
 * @requires vm.compiler1.enabled
 * @library /test/lib
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main ${test.main.class}
 * @run main/othervm -Xcomp -XX:TieredStopAtLevel=1
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test*
 *                   ${test.main.class}
 */
public class TestDelayedInDifferentBlock {
    static value class A {
        @NullRestricted
        Integer i = 42;
    }

    @LooselyConsistentValue
    static value class B {
        @NullRestricted
        A a = new A();
    }

    static final B[] ARRAY = (B[])ValueClass.newNullRestrictedNonAtomicArray(B.class, 1, new B());

    static Integer testField(boolean b, B value) {
        return (b ? value.a : value.a).i;
    }

    static Integer testArray(boolean b) {
        return (b ? ARRAY[0] : ARRAY[0]).a.i;
    }

    public static void main(String[] args) {
        Asserts.assertEQ(testField(false, new B()), 42);
        Asserts.assertEQ(testArray(false), 42);
    }
}

