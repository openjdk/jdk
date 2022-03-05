/*
 * Copyright (c) 2018, Red Hat, Inc. All rights reserved.
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

import org.testng.annotations.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toMap;
import static org.testng.Assert.assertEquals;

/*
 * @test
 * @bug 8210280
 * @modules java.base/java.util:open
 * @summary White box tests for HashMap internals around table resize
 * @run testng WhiteBoxResizeTest
 */
public class WhiteBoxResizeTest {
    final ThreadLocalRandom rnd = ThreadLocalRandom.current();
    final MethodHandle TABLE_SIZE_FOR;
    final VarHandle THRESHOLD;
    final VarHandle TABLE;

    public WhiteBoxResizeTest() throws ReflectiveOperationException {
        Class<?> mClass = HashMap.class;
        String nodeClassName = mClass.getName() + "$Node";
        Class<?> nodeArrayClass = Class.forName("[L" + nodeClassName + ";");
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(mClass, MethodHandles.lookup());
        TABLE = lookup.findVarHandle(mClass, "table", nodeArrayClass);
        this.TABLE_SIZE_FOR = lookup.findStatic(
                mClass, "tableSizeFor",
                MethodType.methodType(int.class, int.class));
        this.THRESHOLD = lookup.findVarHandle(mClass, "threshold", int.class);
    }

    int tableSizeFor(int n) {
        try {
            return (int) TABLE_SIZE_FOR.invoke(n);
        } catch (Throwable t) {
            throw new AssertionError(t);
        }
    }

    @Test
    public void testTableSizeFor() {
        assertEquals(tableSizeFor(0), 1);
        assertEquals(tableSizeFor(1), 1);
        assertEquals(tableSizeFor(2), 2);
        assertEquals(tableSizeFor(3), 4);
        assertEquals(tableSizeFor(15), 16);
        assertEquals(tableSizeFor(16), 16);
        assertEquals(tableSizeFor(17), 32);
        int maxSize = 1 << 30;
        assertEquals(tableSizeFor(maxSize - 1), maxSize);
        assertEquals(tableSizeFor(maxSize), maxSize);
        assertEquals(tableSizeFor(maxSize + 1), maxSize);
        assertEquals(tableSizeFor(Integer.MAX_VALUE), maxSize);
    }

    @Test
    public void capacityTestDefaultConstructor() throws IllegalAccessException {
        capacityTestDefaultConstructor(new HashMap<>());
        capacityTestDefaultConstructor(new LinkedHashMap<>());
        capacityTestDefaultConstructor(new WeakHashMap<>());
    }

    public static int getArrayLength(Map<?, ?> map) throws
            IllegalAccessException {
        Field field = null;
        Class<?> mapClass = map.getClass();
        while (!Map.class.equals(mapClass)) {
            try {
                field = mapClass.getDeclaredField("table");
                break;
            } catch (NoSuchFieldException ignored) {
            }
            mapClass = mapClass.getSuperclass();
        }
        Objects.requireNonNull(field);
        field.setAccessible(true);
        Object table = field.get(map);
        if (table == null) {
            return -1;
        }
        return Array.getLength(table);
    }

    void capacityTestDefaultConstructor(
            Map<Integer, Integer> map
    ) throws IllegalAccessException {
        assertEquals(getArrayLength(map), -1);

        map.put(1, 1);
        assertEquals(getArrayLength(map), 16); // default initial capacity

        map.putAll(IntStream.range(0, 64).boxed().collect(toMap(i -> i, i -> i)));
        assertEquals(getArrayLength(map), 128);
    }

    @Test
    public void capacityTestInitialCapacity() throws IllegalAccessException {
        int initialCapacity = rnd.nextInt(2, 128);
        List<Supplier<Map<Integer, Integer>>> suppliers = List.of(
                () -> new HashMap<>(initialCapacity),
                () -> new HashMap<>(initialCapacity, 0.75f),
                () -> new LinkedHashMap<>(initialCapacity),
                () -> new LinkedHashMap<>(initialCapacity, 0.75f),
                () -> new WeakHashMap<>(initialCapacity),
                () -> new WeakHashMap<>(initialCapacity, 0.75f));

        for (Supplier<Map<Integer, Integer>> supplier : suppliers) {
            Map<Integer, Integer> map = supplier.get();
            assertEquals(-1, getArrayLength(map));

            map.put(1, 1);
            assertEquals(getArrayLength(map), tableSizeFor(initialCapacity));
        }
    }
}
