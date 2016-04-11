/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.vm.ci.hotspot.test;

import static jdk.vm.ci.hotspot.test.TestHelper.ARRAYS_MAP;
import static jdk.vm.ci.hotspot.test.TestHelper.ARRAY_ARRAYS_MAP;
import static jdk.vm.ci.hotspot.test.TestHelper.CONSTANT_REFLECTION_PROVIDER;
import static jdk.vm.ci.hotspot.test.TestHelper.DUMMY_CLASS_CONSTANT;
import static jdk.vm.ci.hotspot.test.TestHelper.DUMMY_CLASS_INSTANCE;
import static jdk.vm.ci.hotspot.test.TestHelper.getResolvedJavaField;
import static jdk.vm.ci.hotspot.test.TestHelper.INSTANCE_STABLE_FIELDS_MAP;
import static jdk.vm.ci.hotspot.test.TestHelper.INSTANCE_FIELDS_MAP;
import static jdk.vm.ci.hotspot.test.TestHelper.STABLE_ARRAYS_MAP;
import static jdk.vm.ci.hotspot.test.TestHelper.STABLE_ARRAY_ARRAYS_MAP;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import jdk.vm.ci.meta.JavaConstant;
import org.testng.annotations.DataProvider;
import jdk.internal.misc.Unsafe;
import jdk.vm.ci.meta.ResolvedJavaField;

public class ReadConstantArrayElementDataProvider {

    // Non-stable array fields names mapped to their base offsets and index scale
    private static final List<ArrayFieldParams> NON_STABLE_ARRAY_NAMES
            = new LinkedList<>();

    static {
        NON_STABLE_ARRAY_NAMES.add(
                new ArrayFieldParams("booleanArrayWithValues", Unsafe.ARRAY_BOOLEAN_BASE_OFFSET,
                             Unsafe.ARRAY_BOOLEAN_INDEX_SCALE));
        NON_STABLE_ARRAY_NAMES.add(new ArrayFieldParams("byteArrayWithValues",
                                                Unsafe.ARRAY_BYTE_BASE_OFFSET,
                                                Unsafe.ARRAY_BYTE_INDEX_SCALE));
        NON_STABLE_ARRAY_NAMES.add(new ArrayFieldParams("shortArrayWithValues",
                                                Unsafe.ARRAY_SHORT_BASE_OFFSET,
                                                Unsafe.ARRAY_SHORT_INDEX_SCALE));
        NON_STABLE_ARRAY_NAMES.add(new ArrayFieldParams("charArrayWithValues",
                                                Unsafe.ARRAY_CHAR_BASE_OFFSET,
                                                Unsafe.ARRAY_CHAR_INDEX_SCALE));
        NON_STABLE_ARRAY_NAMES.add(new ArrayFieldParams("intArrayWithValues",
                                                Unsafe.ARRAY_INT_BASE_OFFSET,
                                                Unsafe.ARRAY_INT_INDEX_SCALE));
        NON_STABLE_ARRAY_NAMES.add(new ArrayFieldParams("longArrayWithValues",
                                                Unsafe.ARRAY_LONG_BASE_OFFSET,
                                                Unsafe.ARRAY_LONG_INDEX_SCALE));
        NON_STABLE_ARRAY_NAMES.add(new ArrayFieldParams("floatArrayWithValues",
                                                Unsafe.ARRAY_FLOAT_BASE_OFFSET,
                                                Unsafe.ARRAY_FLOAT_INDEX_SCALE));
        NON_STABLE_ARRAY_NAMES.add(new ArrayFieldParams("doubleArrayWithValues",
                                                Unsafe.ARRAY_DOUBLE_BASE_OFFSET,
                                                Unsafe.ARRAY_DOUBLE_INDEX_SCALE));
        NON_STABLE_ARRAY_NAMES.add(new ArrayFieldParams("objectArrayWithValues",
                                                Unsafe.ARRAY_BOOLEAN_BASE_OFFSET,
                                                Unsafe.ARRAY_BOOLEAN_INDEX_SCALE));
        NON_STABLE_ARRAY_NAMES.add(new ArrayFieldParams("booleanArrayArrayWithValues",
                                                Unsafe.ARRAY_OBJECT_BASE_OFFSET,
                                                Unsafe.ARRAY_OBJECT_INDEX_SCALE));
        NON_STABLE_ARRAY_NAMES.add(new ArrayFieldParams("byteArrayArrayWithValues",
                                                Unsafe.ARRAY_OBJECT_BASE_OFFSET,
                                                Unsafe.ARRAY_OBJECT_INDEX_SCALE));
        NON_STABLE_ARRAY_NAMES.add(new ArrayFieldParams("shortArrayArrayWithValues",
                                                Unsafe.ARRAY_OBJECT_BASE_OFFSET,
                                                Unsafe.ARRAY_OBJECT_INDEX_SCALE));
        NON_STABLE_ARRAY_NAMES.add(new ArrayFieldParams("charArrayArrayWithValues",
                                                Unsafe.ARRAY_OBJECT_BASE_OFFSET,
                                                Unsafe.ARRAY_OBJECT_INDEX_SCALE));
        NON_STABLE_ARRAY_NAMES.add(new ArrayFieldParams("intArrayArrayWithValues",
                                                Unsafe.ARRAY_OBJECT_BASE_OFFSET,
                                                Unsafe.ARRAY_OBJECT_INDEX_SCALE));
        NON_STABLE_ARRAY_NAMES.add(new ArrayFieldParams("longArrayArrayWithValues",
                                                Unsafe.ARRAY_OBJECT_BASE_OFFSET,
                                                Unsafe.ARRAY_OBJECT_INDEX_SCALE));
        NON_STABLE_ARRAY_NAMES.add(new ArrayFieldParams("floatArrayArrayWithValues",
                                                Unsafe.ARRAY_OBJECT_BASE_OFFSET,
                                                Unsafe.ARRAY_OBJECT_INDEX_SCALE));
        NON_STABLE_ARRAY_NAMES.add(new ArrayFieldParams("doubleArrayArrayWithValues",
                                                Unsafe.ARRAY_OBJECT_BASE_OFFSET,
                                                Unsafe.ARRAY_OBJECT_INDEX_SCALE));
        NON_STABLE_ARRAY_NAMES.add(new ArrayFieldParams("objectArrayArrayWithValues",
                                                Unsafe.ARRAY_OBJECT_BASE_OFFSET,
                                                Unsafe.ARRAY_OBJECT_INDEX_SCALE));
    }

    // Stable array fields names mapped to their base offsets and index scale
    private static final List<ArrayFieldParams> STABLE_ARRAY_NAMES
            = new LinkedList<>();

    static {
        NON_STABLE_ARRAY_NAMES.stream().forEach((entry) -> {
            String nsFieldName = entry.name;
            char firstChar = nsFieldName.charAt(0);
            char newFirstChar = Character.toUpperCase(firstChar);
            String sFieldName = nsFieldName.replaceFirst("" + firstChar,
                                                         "" + newFirstChar);
            sFieldName = "stable" + sFieldName;
            STABLE_ARRAY_NAMES.add(new ArrayFieldParams(sFieldName, entry.offsetBase, entry.scale));
        });
    }

    @DataProvider(name = "readConstantArrayElementDataProvider")
    public static Object[][] readConstantArrayElementDataProvider() {
        LinkedList<Object[]> cfgSet = new LinkedList<>();
        for (int i : new int[]{0, 1}) {
            NON_STABLE_ARRAY_NAMES.stream().forEach((entry) -> {
                String fieldName = entry.name;
                cfgSet.add(new Object[]{
                        readFieldValue(fieldName),
                        i,
                        null,
                        "array field \"" + fieldName + "\" for index " + i});
            });
            STABLE_ARRAY_NAMES.stream().forEach((entry) -> {
                String fieldName = entry.name;
                cfgSet.add(new Object[]{
                        readFieldValue(fieldName),
                        i,
                        i == 0 ? getJavaConstant(fieldName) : null,
                        "array field \"" + fieldName + "\" for index " + i});
            });
        }
        Stream<Map.Entry<ResolvedJavaField, JavaConstant>> arraysStream1
                = Stream.concat(ARRAYS_MAP.entrySet().stream(),
                                ARRAY_ARRAYS_MAP.entrySet().stream());
        Stream<Map.Entry<ResolvedJavaField, JavaConstant>> arraysStream2
                = Stream.concat(STABLE_ARRAYS_MAP.entrySet().stream(),
                                STABLE_ARRAY_ARRAYS_MAP.entrySet().stream());
        Stream.concat(arraysStream1, arraysStream2).forEach((array) -> {
            for (int i : new int[]{-1, 2}) {
                cfgSet.add(new Object[]{
                        array.getValue(),
                        i,
                        null,
                        "array field \"" + array.getKey() + "\" for index " + i});
            }
        });
        cfgSet.add(new Object[]{null, 0, null, "null"});
        cfgSet.add(new Object[]{JavaConstant.NULL_POINTER, 0, null, "JavaConstant.NULL_POINTER"});
        INSTANCE_FIELDS_MAP.values().forEach((constant) -> {
            cfgSet.add(new Object[]{constant, 0, null, "non-stable non-array field"});
        });
        INSTANCE_STABLE_FIELDS_MAP.values().forEach((constant) -> {
            cfgSet.add(new Object[]{constant, 0, null, "stable non-array field"});
        });
        return cfgSet.toArray(new Object[0][0]);
    }

    @DataProvider(name = "readConstantArrayElementForOffsetDataProvider")
    public static Object[][] readConstantArrayElementForOffsetDataProvider() {
        LinkedList<Object[]> cfgSet = new LinkedList<>();
        // Testing non-stable arrays. Result should be null in all cases
        for (double i : new double[]{-1, 0, 0.5, 1, 1.5, 2}) {
            NON_STABLE_ARRAY_NAMES.stream().forEach(entry -> {
                String fieldName = entry.name;
                long offset = (long) (entry.offsetBase + i * entry.scale);
                cfgSet.add(new Object[]{
                        readFieldValue(fieldName),
                        offset,
                        null,
                        "array field \"" + fieldName + "\" for offset " + offset});
            });
        }
        // Testing stable arrays. Result should be null in all cases except "offset = base + 0"
        for (double i : new double[]{-1, 0.5, 1, 1.5, 2}) {
            STABLE_ARRAY_NAMES.stream().forEach(entry -> {
                String fieldName = entry.name;
                long offset = (long) Math.ceil(entry.offsetBase + i * entry.scale);
                cfgSet.add(new Object[]{
                        readFieldValue(fieldName),
                        offset,
                        null,
                        "array field \"" + fieldName + "\" for offset " + offset});
            });
        }
        // Testing stable arrays "offset = base + 0". Result should be non-null
        STABLE_ARRAY_NAMES.stream().forEach(entry -> {
            String fieldName = entry.name;
            long offset = (long) entry.offsetBase;
            cfgSet.add(new Object[]{
                    readFieldValue(fieldName),
                    offset,
                    getJavaConstant(fieldName),
                    "array field \"" + fieldName + "\" for offset " + offset});
        });
        // Testing null as array
        cfgSet.add(new Object[]{null, 0, null, "null"});
        // Testing JavaConstant.NULL_POINTER as array
        cfgSet.add(new Object[]{JavaConstant.NULL_POINTER, 0, null, "JavaConstant.NULL_POINTER"});
        // Testing non-stable non-array fields
        INSTANCE_FIELDS_MAP.values().forEach((constant) -> {
            cfgSet.add(new Object[]{constant, 0, null, "non-stable non-array field"});
        });
        // Testing stable non-array fields
        INSTANCE_STABLE_FIELDS_MAP.values().forEach((constant) -> {
            cfgSet.add(new Object[]{constant, 0, null, "stable non-array field"});
        });
        return cfgSet.toArray(new Object[0][0]);
    }

    private static JavaConstant readFieldValue(String fieldName) {
        return CONSTANT_REFLECTION_PROVIDER.readFieldValue(getResolvedJavaField(DummyClass.class, fieldName),
                                                           DUMMY_CLASS_CONSTANT);
    }

    private static JavaConstant getJavaConstant(String fieldName) {
        Class<DummyClass> dummyClass = DummyClass.class;
        Field arrayField;
        try {
            arrayField = dummyClass.getDeclaredField(fieldName);
        } catch (NoSuchFieldException ex) {
            throw new Error("Test bug: wrong field name " + ex, ex);
        } catch (SecurityException ex) {
            throw new Error("Unexpected error: " + ex, ex);
        }
        arrayField.setAccessible(true);
        Class<?> componentType = arrayField.getType().getComponentType();
        if (componentType == null) {
            throw new Error("Test error: field is not an array");
        }
        Object value;
        try {
            value = arrayField.get(DUMMY_CLASS_INSTANCE);
        } catch (IllegalArgumentException | IllegalAccessException ex) {
            throw new Error("Unexpected error: " + ex, ex);
        }
        if (componentType == boolean.class) {
            return JavaConstant.forBoolean(((boolean[]) value)[0]);
        }
        if (componentType == byte.class) {
            return JavaConstant.forByte(((byte[]) value)[0]);
        }
        if (componentType == short.class) {
            return JavaConstant.forShort(((short[]) value)[0]);
        }
        if (componentType == char.class) {
            return JavaConstant.forChar(((char[]) value)[0]);
        }
        if (componentType == int.class) {
            return JavaConstant.forInt(((int[]) value)[0]);
        }
        if (componentType == long.class) {
            return JavaConstant.forLong(((long[]) value)[0]);
        }
        if (componentType == float.class) {
            return JavaConstant.forFloat(((float[]) value)[0]);
        }
        if (componentType == double.class) {
            return JavaConstant.forDouble(((double[]) value)[0]);
        }
        return CONSTANT_REFLECTION_PROVIDER.forObject(((Object[]) value)[0]);
    }

    private static class ArrayFieldParams {
        public final String name;
        public final int offsetBase;
        public final int scale;

       ArrayFieldParams(String name, int offsetBase, int scale) {
           this.name = name;
           this.offsetBase = offsetBase;
           this.scale = scale;
       }
    }
}
