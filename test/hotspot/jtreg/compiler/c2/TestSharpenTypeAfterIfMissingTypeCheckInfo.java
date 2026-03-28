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
 * @bug 8380579
 * @summary Test that C2 match_type_check handles Bool(CmpP(CastPP(LoadKlass(...)), ConP(klass)), eq)
 * @run main/othervm
 *      -XX:-TieredCompilation
 *      -XX:CompileThreshold=100
 *      ${test.main.class}
 */

package compiler.c2;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collections;

public class TestSharpenTypeAfterIfMissingTypeCheckInfo {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final MethodHandle CHANGE_ARRAY_TYPE;

    static {
        try {
            CHANGE_ARRAY_TYPE = LOOKUP.findStatic(TestSharpenTypeAfterIfMissingTypeCheckInfo.class,
                                                  "changeArrayType",
                                                  MethodType.methodType(Object.class, Class.class, Object[].class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws Throwable {
        for (int i = 0; i < 150; i++) {
            test();
        }
    }

    static void test() throws Throwable {
        for (Class<?> argType : new Class<?>[]{Object.class, Integer.class, int.class}) {
            Class<?> arrayType = Array.newInstance(argType, 0).getClass();
            for (int nargs = 0; nargs < 10; nargs++) {
                MethodHandle target2 = varargsArray(arrayType, nargs);
                MethodHandle target = target2.asType(target2.type().generic());

                Object[] args = new Object[nargs];
                for (int i = 0; i < nargs; i++) {
                    args[i] = sample(argType, i);
                }

                for (int j = 0; j < 150; j++) {
                    target.invokeWithArguments(args);
                }
            }
        }
    }

    static MethodHandle varargsArray(Class<?> arrayType, int nargs) {
        Class<?> elemType = arrayType.getComponentType();
        MethodType vaType = MethodType.methodType(arrayType, Collections.nCopies(nargs, elemType));
        MethodHandle mh = MethodHandles.identity(Object[].class).asCollector(Object[].class, nargs);
        if (arrayType != Object[].class) {
            mh = MethodHandles.filterReturnValue(mh, CHANGE_ARRAY_TYPE.bindTo(arrayType));
        }
        return mh.asType(vaType);
    }

    static Object changeArrayType(Class<?> arrayType, Object[] a) {
        Class<?> elemType = arrayType.getComponentType();
        if (!elemType.isPrimitive()) {
            return Arrays.copyOf(a, a.length, arrayType.asSubclass(Object[].class));
        }
        Object b = Array.newInstance(elemType, a.length);
        for (int i = 0; i < a.length; i++) {
            Array.set(b, i, a[i]);
        }
        return b;
    }

    static Object sample(Class<?> argType, int i) {
        if (argType == Object.class) {
            return "x" + i;
        }
        return Integer.valueOf(i);
    }
}