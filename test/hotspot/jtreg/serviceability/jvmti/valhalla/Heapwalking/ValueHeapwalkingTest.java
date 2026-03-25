/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Tests heapwalking API (FollowReferences, IterateThroughHeap, GetObjectsWithTags) for value objects.
 * @requires vm.jvmti
 * @modules java.base/jdk.internal.vm.annotation java.base/jdk.internal.value
 * @enablePreview
 * @run main/othervm/native -agentlib:ValueHeapwalkingTest
 *                          -XX:+UnlockDiagnosticVMOptions
 *                          -XX:+UseArrayFlattening
 *                          -XX:+UseFieldFlattening
 *                          -XX:+UseAtomicValueFlattening
 *                          -XX:+UseNullableValueFlattening
 *                          -XX:+PrintInlineLayout
 *                          -XX:+PrintFlatArrayLayout
 *                          -Xlog:jvmti+table
 *                          ValueHeapwalkingTest
 */

import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.NullRestricted;

import java.lang.reflect.Field;

public class ValueHeapwalkingTest {

    static value class Value {
        int v;
        Value() {
            this(0);
        }
        Value(int v) {
            this.v = v;
        }
    }

    // flat object has flat field (address of the field is the same as address of the object)
    static value class Value2 {
        @NullRestricted
        public Value v1;
        @NullRestricted
        public Value v2;
        Value2() {
            this(0);
        }
        Value2(int i) {
            this.v1 = new Value(i);
            this.v2 = new Value(i+1);
            super();
        }
    }

    static value class ValueHolder {
        public Value v1;
        @NullRestricted
        public Value v2;
        public Value v_null;

        public Value2 v2_1;
        @NullRestricted
        public Value2 v2_2;

        public Value[] v_arr;
        public Value2[] v2_arr;

        public ValueHolder(int seed) throws Exception {
            v1 = new Value(seed);
            v2 = new Value(seed + 1);
            v_null = null;

            v2_1 = new Value2(seed + 6);
            v2_2 = new Value2(seed + 8);

            v_arr = createValueArray(seed);
            v2_arr = createValue2Array(seed);
        }
    }

    static Value[] createValueArray(int seed) throws Exception {
        Value[] arr = (Value[])ValueClass.newNullableAtomicArray(Value.class, 5);
        for (int i = 0; i < arr.length; i++) {
            arr[i] = i == 2 ? null : new Value(seed + 10 + i);
        }
        return arr;
    }

    static Value2[] createValue2Array(int seed) throws Exception {
        Value2[] arr = (Value2[])ValueClass.newNullRestrictedNonAtomicArray(Value2.class, 5, Value2.class.newInstance());
        for (int i = 0; i < arr.length; i++) {
            arr[i] = new Value2(seed + 20 + i * 2);
        }
        return arr;
    }

    static final int TAG_VALUE_CLASS = 1;
    static final int TAG_VALUE2_CLASS = 2;
    static final int TAG_HOLDER_CLASS = 3;
    static final int TAG_VALUE_ARRAY = 4;
    static final int TAG_VALUE2_ARRAY = 5;

    static final int TAG_MIN = TAG_VALUE_CLASS;
    static final int TAG_MAX = TAG_VALUE2_ARRAY;

    static String tagStr(int tag) {
        String suffix = " (tag " + tag + ")";
        switch (tag) {
        case TAG_VALUE_CLASS: return "Value class" + suffix;
        case TAG_VALUE2_CLASS: return "Value2 class" + suffix;
        case TAG_HOLDER_CLASS: return "ValueHolder class" + suffix;
        case TAG_VALUE_ARRAY: return "Value[] object" + suffix;
        case TAG_VALUE2_ARRAY: return "Value2[] object" + suffix;
        }
        return "Unknown" + suffix;
    }

    static native void setTag(Object object, long tag);
    static native long getTag(Object object);

    static native void reset();

    static native void followReferences();

    static native void iterateThroughHeap();

    static native int count(int classTag);
    static native int refCount(int fromTag, int toTag);
    static native int primitiveFieldCount(int tag);

    static native long getMaxTag();

    static native int getObjectWithTags(long minTag, long maxTag, Object[] objects, long[] tags);


    // counts non-null elements in the array
    static <T> int nonNullCount(T[] array) {
        return (int)Arrays.stream(array).filter(e -> e != null).count();
    }

    static void verifyMinCount(int classTag, int minCount) {
        int count = count(classTag);
        String msg = tagStr(classTag) + " count: " + count + ", min expected: " + minCount;
        if (count < minCount) {
            throw new RuntimeException(msg);
        }
        System.out.println(msg);
    }

    static void verifyRefCount(int tagFrom, int tagTo, int expectedCount) {
        int count = refCount(tagFrom, tagTo);
        String msg = "Ref.count from " + tagStr(tagFrom) + " to " + tagStr(tagTo) + ": "
                   + count + ", expected: " + expectedCount;
        if (count !=  expectedCount) {
            throw new RuntimeException(msg);
        }
        System.out.println(msg);
    }

    static void verifyPrimitiveFieldCount(int classTag, int expectedCount) {
        int count = primitiveFieldCount(classTag);
        String msg = "Primitive field count from " + tagStr(classTag) + ": "
                   + count + ", expected: " + expectedCount;
        if (count !=  expectedCount) {
            throw new RuntimeException(msg);
        }
        System.out.println(msg);
    }


    static void printObject(Object obj) {
        printObject("", obj);
    }

    static void printObject(String prefix, Object obj) {
        if (obj == null) {
            System.out.println(prefix + "null");
            return;
          }

        Class<?> clazz = obj.getClass();
        System.out.println(prefix + "Object (class " + clazz.getName() + ", tag = " + getTag(obj) + ", class tag = " + getTag(clazz));

        if (clazz.isArray()) {
            Class<?> elementType = clazz.getComponentType();
            int length = java.lang.reflect.Array.getLength(obj);
            System.out.println(prefix + "Array of " + elementType + ", length = " + length + " [");
            for (int i = 0; i < length; i++) {
                Object element = java.lang.reflect.Array.get(obj, i);
                if (elementType.isPrimitive()) {
                    if (i == 0) {
                        System.out.print(prefix + "  ");
                    } else {
                        System.out.print(", ");
                    }
                    System.out.print(prefix + "(" + i + "):" + element);
                } else {
                    System.out.println(prefix + "(" + i + "):" + "NOT primitive");
                    printObject(prefix + "  ", element);
                }
            }
            System.out.println(prefix + "]");
        } else {
            while (clazz != null && clazz != Object.class) {
                Field[] fields = clazz.getDeclaredFields();
                for (Field field : fields) {
                    try {
                        field.setAccessible(true);
                        Object value = field.get(obj);
                        Class<?> fieldType = field.getType();
                        System.out.println(prefix + "- " + field.getName() + " (" + fieldType + ") = " + value);

                        if (!fieldType.isPrimitive()) {
                            printObject(prefix + "  ", value);
                        }
                    } catch (IllegalAccessException | java.lang.reflect.InaccessibleObjectException e) {
                        System.err.println("  Error accessing field " + field.getName() + ": " + e.getMessage());
                    }
                }
                clazz = clazz.getSuperclass();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        System.loadLibrary("ValueHeapwalkingTest");
        ValueHolder holder = new ValueHolder(10);

        setTag(Value.class, TAG_VALUE_CLASS);
        setTag(Value2.class, TAG_VALUE2_CLASS);
        setTag(ValueHolder.class, TAG_HOLDER_CLASS);
        setTag(holder.v_arr, TAG_VALUE_ARRAY);
        setTag(holder.v2_arr, TAG_VALUE2_ARRAY);

        reset();
        System.out.println(">>iterateThroughHeap");
        iterateThroughHeap();
        System.out.println("<<iterateThroughHeap");

        // IterateThroughHeap reports reachable and unreachable objects,
        // so verify only minimum count.
        for (int i = TAG_MIN; i <= TAG_MAX; i++) {
            System.out.println(tagStr(i) + " count: " + count(i));
        }
        int reachableValueHolderCount = 1;
        // v2_1, v2_2, v2_arr
        int reachableValue2Count = reachableValueHolderCount * (2 + nonNullCount(holder.v2_arr));
        // ValueHolder: v1, v2, v_arr
        // Value2: v1, v2
        int reachableValueCount = reachableValueHolderCount * (2 + nonNullCount(holder.v_arr))
                                + reachableValue2Count * 2;
        verifyMinCount(TAG_VALUE_CLASS, reachableValueCount);
        verifyMinCount(TAG_VALUE2_CLASS, reachableValue2Count);
        verifyMinCount(TAG_HOLDER_CLASS, reachableValueHolderCount);
        // For each Value object 1 primitive field must be reported.
        verifyPrimitiveFieldCount(TAG_VALUE_CLASS, count(TAG_VALUE_CLASS));

        reset();  // to reset primitiveFieldCount
        System.out.println(">>followReferences");
        followReferences();
        System.out.println("<<followReferences");

        long maxTag = getMaxTag();

        for (int i = TAG_MIN; i <= TAG_MAX; i++) {
            for (int j = TAG_MIN; j <= TAG_MAX; j++) {
                System.out.println("Reference from " + tagStr(i) + " to " + tagStr(j) + ": " + refCount(i, j));
            }
        }

        printObject(holder);

        // ValueHolder: v1, v2
        verifyRefCount(TAG_HOLDER_CLASS, TAG_VALUE_CLASS, 2);
        // ValueHolder: v2_1, v2_2
        verifyRefCount(TAG_HOLDER_CLASS, TAG_VALUE_CLASS, 2);
        // ValueHolder: v_arr
        verifyRefCount(TAG_HOLDER_CLASS, TAG_VALUE_ARRAY, 1);
        // ValueHolder: v2_arr
        verifyRefCount(TAG_HOLDER_CLASS, TAG_VALUE2_ARRAY, 1);

        // References from arrays to their elements
        verifyRefCount(TAG_VALUE_ARRAY, TAG_VALUE_CLASS, nonNullCount(holder.v_arr));
        verifyRefCount(TAG_VALUE2_ARRAY, TAG_VALUE2_CLASS, nonNullCount(holder.v2_arr));

        // Each Value2 object must have 2 references to Value object (v1, v2).
        verifyRefCount(TAG_VALUE2_CLASS, TAG_VALUE_CLASS, reachableValue2Count * 2);

        // For each Value object 1 primitive field must be reported.
        verifyPrimitiveFieldCount(TAG_VALUE_CLASS, reachableValueCount);

        System.out.println(">>followReferences (2)");
        followReferences();
        System.out.println("<<followReferences (2)");

        long maxTag2 = getMaxTag();
        // no new objects are expected to be tagged
        if (maxTag != maxTag2) {
            throw new RuntimeException("maxTag (" + maxTag + ") not equal to maxTag2(" + maxTag2 + ")");
        }

        Object[] objects = new Object[1024];
        long tags[] = new long[1024];
        System.out.println(">>getObjectWithTags, maxTag = " + maxTag);
        int count = getObjectWithTags(1, maxTag, objects, tags);
        System.out.println("getObjectWithTags returned " + count);
        for (int i = 0; i < count; i++) {
            System.out.println("  [" + i + "] " + objects[i] + ", tag = " + tags[i]);
            if (objects[i] == null || tags[i] == 0) {
                throw new RuntimeException("unexpected object");
            }
        }
        int expectedTaggedCount = 5 // TAG_VALUE_CLASS/TAG_VALUE2_CLASS/TAG_HOLDER_CLASS/TAG_VALUE_ARRAY/TAG_VALUE2_ARRAY
                + reachableValue2Count + reachableValueCount;
        if (count !=  expectedTaggedCount) {
            throw new RuntimeException("unexpected getObjectWithTags result: " + count
                                     + ", expected " + expectedTaggedCount);
        }

        Reference.reachabilityFence(holder);
    }
}
