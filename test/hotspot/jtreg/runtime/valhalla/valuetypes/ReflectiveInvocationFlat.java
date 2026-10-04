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
package runtime.valhalla.valuetypes;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;

/*
 * @test
 * @summary Reflective invocation with flattened array argument functions when in the interpreter
 * @bug 8392757
 * @requires vm.flagless
 * @modules java.base/jdk.internal.value
 * @enablePreview
 * @run main/othervm -Xint -Djdk.reflect.useNativeAccessorOnly=true runtime.valhalla.valuetypes.ReflectiveInvocationFlat
 */
public class ReflectiveInvocationFlat {

    ReflectiveInvocationFlat(Object value) { }

    static void test(Object value) { }

    public static void main(String[] args) throws Exception {
        Integer[] array = new Integer[] { 42 };
        if (!ValueClass.isFlatArray(array)) {
            throw new AssertionError("Expected a flat reflection argument array");
        }
        Object res1 = ReflectiveInvocationFlat.class.getDeclaredConstructor(Object.class).newInstance((Object[])array);
        Object res2 = ReflectiveInvocationFlat.class.getDeclaredMethod("test", Object.class).invoke(null, (Object[])array);

        // This one also fails without -Djdk.reflect.useNativeAccessorOnly=true
        MethodHandle handle = MethodHandles.identity(Object.class);
        MethodHandle.class.getMethod("invokeExact", Object[].class).invoke(handle, (Object)array);
    }
}
