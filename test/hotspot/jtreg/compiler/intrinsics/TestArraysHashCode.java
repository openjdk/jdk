/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8301093
 * @summary Verify failure to intrinsify does not pollute control flow
 * @modules java.base/jdk.internal.util:+open
 *
 * @run main/othervm -Xbatch -XX:-TieredCompilation compiler.intrinsics.TestArraysHashCode
 */

package compiler.intrinsics;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class TestArraysHashCode {

    private static final Method vectorizedHashCode;
    private static final int T_BOOLEAN;

    static {
        try {
            var arraysSupport = Class.forName("jdk.internal.util.ArraysSupport");
            vectorizedHashCode = arraysSupport.getDeclaredMethod("vectorizedHashCode",
                    Object.class, int.class, int.class, int.class, int.class);
            vectorizedHashCode.setAccessible(true);
            Field f = arraysSupport.getDeclaredField("T_BOOLEAN");
            f.setAccessible(true);
            T_BOOLEAN = f.getInt(null);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    static int type;
    static byte[] bytes;

    public static void main(String[] args)
            throws InvocationTargetException, IllegalAccessException {
        // read
        bytes = new byte[256];
        type = T_BOOLEAN;
        testIntrinsicWithConstantType();
        testIntrinsicWithNonConstantType();
    }

    private static void testIntrinsicWithConstantType()
            throws InvocationTargetException, IllegalAccessException {
        for (int i = 0; i < 20_000; i++) {
            testIntrinsic(bytes, T_BOOLEAN);
        }
    }

    // ok, but shouldn't be intrinsified due the non-constant type
    private static void testIntrinsicWithNonConstantType()
            throws InvocationTargetException, IllegalAccessException {
        type = T_BOOLEAN;
        for (int i = 0; i < 20_000; i++) {
            testIntrinsic(bytes, type);
        }
    }

    private static int testIntrinsic(byte[] bytes, int type)
            throws InvocationTargetException, IllegalAccessException {
        return (int) vectorizedHashCode.invoke(null, bytes, 0, 256, 1, type);
    }
}
