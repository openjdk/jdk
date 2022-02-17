/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8281631
 * @summary HashMap copy constructor and putAll can over-allocate table
 * @modules java.base/java.lang:open
 *          java.base/java.util:open
 * @author  Xeno Amess
 *
 * @run junit HashMapsPutAllOverAllocateTableTest
 */

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class HashMapsPutAllOverAllocateTableTest {

    @Parameterized.Parameters
    public static List<Object[]> testFunctionsList() {
        return List.of(
                new Object[]{
                        (Supplier<Map<Object, Object>>) HashMap::new,
                        (Function<Integer, Map<Object, Object>>) HashMap::new,
                        (Function<Map<Object, Object>, Map<Object, Object>>) HashMap::new,
                },
                new Object[]{
                        (Supplier<Map<Object, Object>>) WeakHashMap::new,
                        (Function<Integer, Map<Object, Object>>) WeakHashMap::new,
                        (Function<Map<Object, Object>, Map<Object, Object>>) WeakHashMap::new,
                },
                new Object[]{
                        (Supplier<Map<Object, Object>>) IdentityHashMap::new,
                        (Function<Integer, Map<Object, Object>>) IdentityHashMap::new,
                        (Function<Map<Object, Object>, Map<Object, Object>>) IdentityHashMap::new,
                }
        );
    }

    private final Supplier<Map<Object, Object>> createNewMap;

    private final Function<Integer, Map<Object, Object>> createNewMapWithInt;

    private final Function<Map<Object, Object>, Map<Object, Object>> createNewMapWithMap;

    public HashMapsPutAllOverAllocateTableTest(
            Supplier<Map<Object, Object>> createNewMap,
            Function<Integer, Map<Object, Object>> createNewMapWithInt,
            Function<Map<Object, Object>, Map<Object, Object>> createNewMapWithMap
    ) {
        this.createNewMap = createNewMap;
        this.createNewMapWithInt = createNewMapWithInt;
        this.createNewMapWithMap = createNewMapWithMap;
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

    @Test
    public void test() throws NoSuchFieldException, IllegalAccessException {

        Map<Object, Object> a = createNewMap.get();
        fill12(a);

        {
            int length = getArrayLength(a);
            Assert.assertEquals(
                    "length a not equals to 16",
                    16,
                    length
            );
        }

        {
            Map<Object, Object> b = createNewMapWithInt.apply(12);
            fill12(b);
            int length = getArrayLength(b);
            Assert.assertEquals(
                    "length b not equals to 16",
                    16,
                    length
            );
        }

        {
            Map<Object, Object> c = createNewMapWithMap.apply(a);
            int length = getArrayLength(c);
            Assert.assertEquals(
                    "length c not equals to 16",
                    16,
                    length
            );
        }

        {
            Map<Object, Object> d = createNewMap.get();
            d.putAll(a);
            int length = getArrayLength(d);
            Assert.assertEquals(
                    "length d not equals to 16",
                    16,
                    length
            );
        }

    }

}
