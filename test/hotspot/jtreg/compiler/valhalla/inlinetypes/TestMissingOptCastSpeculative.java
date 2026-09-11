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

import test.java.lang.invoke.lib.InstructionHelper;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import jdk.internal.value.ValueClass;

import static compiler.valhalla.inlinetypes.InlineTypes.*;

/*
 * @test
 * @bug 8367624
 * @summary Writing a null value to an inline type array casts the array to
 *          'not null free'. If there is a speculative type before the cast,
 *          we have to make sure to cast it as well, otherwise we get a
 *          missed value optimization.
 * @library /test/lib /test/jdk/java/lang/invoke/common /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build test.java.lang.invoke.lib.InstructionHelper
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+IgnoreUnrecognizedVMOptions
 *                   -Xbatch -XX:PerMethodSpecTrapLimit=0 -XX:PerMethodTrapLimit=0
 *                   -XX:VerifyIterativeGVN=1110 -XX:CompileCommand=compileonly,${test.main.class}::test
 *                   ${test.main.class}
 * @run main ${test.main.class}
 */

public class TestMissingOptCastSpeculative {
    public static void main(String[] args) {
        TestMissingOptCastSpeculative t = new TestMissingOptCastSpeculative();
        MyValue1[] testValue1Array = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 3, MyValue1.DEFAULT);
        for (int i = 0; i < 10000; i++) {
            try {
                t.test(testValue1Array, 0);
            } catch (Throwable e) {}
        }
    }

    // stores null at the specified index in the array
    private static final MethodHandle setArrayElementNull = InstructionHelper.buildMethodHandle(MethodHandles.lookup(),
        "setArrayElementNull",
        MethodType.methodType(void.class, TestMissingOptCastSpeculative.class, MyValue1[].class, int.class),
        CODE -> {
            CODE.
            aload(1).
            iload(2).
            aconst_null().
            aastore().
            return_();
        });

    private void test(MyValue1[] va, int index) throws Throwable {
        setArrayElementNull.invoke(this, va, index);
    }
}

