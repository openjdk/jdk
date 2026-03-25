/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=default
 * @summary Test arguments to JVM_InvokeMethod not flattened into an args array.
 * @requires vm.flagless
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 *          java.base/jdk.internal.misc
 * @library /test/lib
 * @enablePreview
 * @compile --source 27 DirectMethodTest.java
 * @run main/othervm -Djdk.reflect.useNativeAccessorOnly=true -XX:+UseArrayFlattening -XX:+UseFieldFlattening -XX:+UseAtomicValueFlattening -XX:+UseNullableValueFlattening runtime.valhalla.inlinetypes.DirectMethodTest
 */

/*
 * @test id=no-array-flattening
 * @summary Test arguments to JVM_InvokeMethod not flattened into an args array.
 * @requires vm.flagless
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 *          java.base/jdk.internal.misc
 * @library /test/lib
 * @enablePreview
 * @compile --source 27 DirectMethodTest.java
 * @run main/othervm -Djdk.reflect.useNativeAccessorOnly=true -XX:-UseArrayFlattening -XX:+UseAtomicValueFlattening -XX:+UseNullableValueFlattening runtime.valhalla.inlinetypes.DirectMethodTest
 */

package runtime.valhalla.inlinetypes;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import jdk.internal.value.ValueClass;

public class DirectMethodTest {

    public int method1(int i, int j, int k) {
        System.out.println("i = " + i + " j = " + j + " k = " + k);
        return i + j * k;
    }

    public static void printFlat(Object[] array) {
        if (!ValueClass.isFlatArray(array)) {
            System.out.println("not flat " + array);
        } else {
            System.out.println("yay flat " + array);
        }
    }

    static value class SmallValue {
        byte b;
        short s;

        SmallValue(short i) { b = 0; s = i; }
    }

    public int method2(SmallValue i, SmallValue j, SmallValue k) {
        System.out.println("i = " + i + " j = " + j + " k = " + k);
        return i.s + j.s * k.s;
    }

    static final int ARRAY_SIZE = 3;

    public static void main(java.lang.String[] unused) throws Exception {
        DirectMethodTest d = new DirectMethodTest();

        Method m = DirectMethodTest.class.getMethod("method1", int.class, int.class, int.class);
        Integer[] intarray = new Integer[]{1, 2, 3};  // is this flattened?
        printFlat(intarray);
        Object[] array = (Object[])Array.newInstance(Integer.class, 3);
        printFlat(array);
        array = ValueClass.newNullableAtomicArray(Integer.class, ARRAY_SIZE);
        printFlat(array);
        System.out.println("value is " + m.invoke(d, 1, 2, 3));

        Method m2 = DirectMethodTest.class.getMethod("method2", SmallValue.class, SmallValue.class, SmallValue.class);
        Object[] smallValueArray = (Object[])Array.newInstance(SmallValue.class, ARRAY_SIZE);
        printFlat(smallValueArray);
        smallValueArray = ValueClass.newNullableAtomicArray(SmallValue.class, ARRAY_SIZE);
        printFlat(smallValueArray);
        System.out.println("value is " + m2.invoke(d, new SmallValue((short)1), new SmallValue((short)2), new SmallValue((short)3)));
    }
}

