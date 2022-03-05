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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class WhiteBoxHashMapTestUtil {

    private static final MethodHandle TABLE_SIZE_FOR;

    static {
        try {
            Class<?> mClass = HashMap.class;
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(mClass, MethodHandles.lookup());
            TABLE_SIZE_FOR = lookup.findStatic(
                    mClass, "tableSizeFor",
                    MethodType.methodType(int.class, int.class));
        } catch (Exception e) {
            throw new RuntimeException("WhiteBoxHashMapTableSizeForTest init failed", e);
        }
    }

    static int tableSizeFor(int n) {
        try {
            return (int) TABLE_SIZE_FOR.invoke(n);
        } catch (Throwable t) {
            throw new AssertionError(t);
        }
    }

    static int getArrayLength(Map<?, ?> map) {
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
        Object table = null;
        try {
            table = field.get(map);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("cannot get table for map " + map.getClass().getName(), e);
        }
        if (table == null) {
            return -1;
        }
        return Array.getLength(table);
    }

    static class WhiteBoxHashMapTestSuite<T extends Map> {

        private final Class<T> mapClass;

        private final Function<T, Integer> getArrayLength;

        private final Supplier<T> createNewMap;

        private final Function<Integer, T> createNewMapWithCapacity;

        private final BiFunction<Integer, Float, T> createNewMapWithCapacityAndFactor;

        private final Function<Map, T> createNewMapWithMap;

        public WhiteBoxHashMapTestSuite(
                Class<T> mapClass,
                Function<T, Integer> getArrayLength,
                Supplier<T> createNewMap,
                BiFunction<Integer, Float, T> createNewMapWithCapacityAndFactor,
                Function<Integer, T> createNewMapWithCapacity,
                Function<Map, T> createNewMapWithMap
        ) {
            this.mapClass = mapClass;
            this.getArrayLength = getArrayLength;
            this.createNewMap = createNewMap;
            this.createNewMapWithCapacityAndFactor = createNewMapWithCapacityAndFactor;
            this.createNewMapWithCapacity = createNewMapWithCapacity;
            this.createNewMapWithMap = createNewMapWithMap;
        }

        public Class<T> getMapClass() {
            return mapClass;
        }

        public Function<T, Integer> getGetArrayLength() {
            return getArrayLength;
        }

        public Supplier<T> getCreateNewMap() {
            return createNewMap;
        }

        public Function<Integer, T> getCreateNewMapWithCapacity() {
            return createNewMapWithCapacity;
        }

        public BiFunction<Integer, Float, T> getCreateNewMapWithCapacityAndFactor() {
            return createNewMapWithCapacityAndFactor;
        }

        public Function<Map, T> getCreateNewMapWithMap() {
            return createNewMapWithMap;
        }

    }

    static WhiteBoxHashMapTestSuite<HashMap> HASH_MAP_TEST_SUITE = new WhiteBoxHashMapTestSuite<>(
            HashMap.class,
            WhiteBoxHashMapTestUtil::getArrayLength,
            HashMap::new,
            HashMap::new,
            HashMap::new,
            HashMap::new
    );

    static WhiteBoxHashMapTestSuite<LinkedHashMap> LINKED_HASH_MAP_TEST_SUITE = new WhiteBoxHashMapTestSuite<>(
            LinkedHashMap.class,
            WhiteBoxHashMapTestUtil::getArrayLength,
            LinkedHashMap::new,
            LinkedHashMap::new,
            LinkedHashMap::new,
            LinkedHashMap::new
    );

    static WhiteBoxHashMapTestSuite<WeakHashMap> WEAK_HASH_MAP_TEST_SUITE = new WhiteBoxHashMapTestSuite<>(
            WeakHashMap.class,
            WhiteBoxHashMapTestUtil::getArrayLength,
            WeakHashMap::new,
            WeakHashMap::new,
            WeakHashMap::new,
            WeakHashMap::new
    );

}
