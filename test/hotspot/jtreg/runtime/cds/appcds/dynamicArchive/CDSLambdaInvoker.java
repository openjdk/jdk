/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * This is launched from TestLambdaInvokers.
 */
public class CDSLambdaInvoker {
    public static void main(String args[]) throws Throwable {
        // The following calls trigger the generation of new Species classes
        // that are not included in the base archive (or the default modules image).
        // - java.lang.invoke.BoundMethodHandle$Species_F
        // - java.lang.invoke.BoundMethodHandle$Species_FL
        // - java.lang.invoke.BoundMethodHandle$Species_J
        // - java.lang.invoke.BoundMethodHandle$Species_JL
        invoke(MethodHandles.identity(double.class), 1.0);
        invoke(MethodHandles.identity(long.class), 1);
        invoke(MethodHandles.identity(int.class), 1); // Note: Species_IL is in default modules image.
        invoke(MethodHandles.identity(float.class), 1.0f);

        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodType mt = MethodType.methodType(void.class, float.class, double.class, int.class,
                                              boolean.class, Object.class, long.class, double.class);
        MethodHandle mh = lookup.findStatic(CDSLambdaInvoker.class, "callme", mt);
        mh.invokeExact(4.0f, 5.0, 6, true, (Object)args, 7L, 8.0);

        mh = MethodHandles.dropArguments(MethodHandles.zero(Object.class), 0, Object.class, int.class);
        MethodHandle inv = MethodHandles.invoker(mh.type());
        invoke(inv, mh, args, 3);
    }

    private static Object invoke(MethodHandle mh, Object ... args) throws Throwable {
        try {
            for (Object o : args) {
                mh = MethodHandles.insertArguments(mh, 0, o);
            }
            return mh.invoke();
        } catch (Throwable t) {
            System.out.println("Failed to find, link and/or invoke " + mh.toString() + ": " + t.getMessage());
            throw t;
        }
    }

    private static void callme(float f, double d, int i, boolean b, Object o, long l, double d2) {

    }
}
