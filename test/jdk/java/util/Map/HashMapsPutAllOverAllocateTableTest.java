package java.util.Map;/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8281631
 * @summary HashMap copy constructor and putAll can over-allocate table
 * @library /test/lib
 * @modules java.base/java.lang:open
 *          java.base/java.util:open
 * @run main HashMaPutAllOverAllocateTableTest
 */


import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

public class HashMapsPutAllOverAllocateTableTest {

    public static void main(String[] args) throws Exception {
        testOneMapClass(HashMap.class);
        testOneMapClass(WeakHashMap.class);
        testOneMapClass(IdentityHashMap.class);
        testOneMapClass(LinkedHashMap.class);
        testOneMapClass(ConcurrentHashMap.class);
    }

    public static <T extends Map<Object, Object>> void testOneMapClass(Class<T> mapClass) throws Exception {
        Map<Object, Object> a = mapClass.getDeclaredConstructor().newInstance();
        fill12(a);
        Map<Object, Object> b = mapClass.getDeclaredConstructor(int.class).newInstance(12);
        fill12(b);
        Map<Object, Object> c = mapClass.getDeclaredConstructor(Map.class).newInstance(a);
        Map<Object, Object> d = mapClass.getDeclaredConstructor().newInstance();
        d.putAll(a);
        int lengthA = getArrayLength(a);
        int lengthB = getArrayLength(b);
        int lengthC = getArrayLength(c);
        int lengthD = getArrayLength(d);
        if (lengthA != lengthB) {
            throw new RuntimeException("lengthA not equals lengthB! lengthA : " + lengthA + " , lengthB : " + lengthB);
        }
        if (lengthA != lengthC) {
            throw new RuntimeException("lengthA not equals lengthB! lengthA : " + lengthA + " , lengthC : " + lengthC);
        }
        if (lengthA != lengthD) {
            throw new RuntimeException("lengthA not equals lengthB! lengthA : " + lengthA + " , lengthD : " + lengthD);
        }
    }

    public static void fill12(Map<Object, Object> map) {
        for (int i = 0; i < 12; i++) {
            map.put(i, i);
        }
    }

    public static int getArrayLength(Map<Object, Object> map) throws NoSuchFieldException, IllegalAccessException {
        Field field = map.getClass().getDeclaredField("table");
        field.setAccessible(true);
        Object table = field.get(map);
        return Array.getLength(table);
    }

}
