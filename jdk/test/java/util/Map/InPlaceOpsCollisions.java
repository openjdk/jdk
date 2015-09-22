/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8005698
 * @run main InPlaceOpsCollisions -shortrun
 * @summary Ensure overrides of in-place operations in Maps behave well with lots of collisions.
 * @author Brent Christian
 */
import java.util.*;
import java.util.function.*;

public class InPlaceOpsCollisions {

    /**
     * Number of elements per map.
     */
    private static final int TEST_SIZE = 5000;

    static final class HashableInteger implements Comparable<HashableInteger> {

        final int value;
        final int hashmask; //yes duplication

        HashableInteger(int value, int hashmask) {
            this.value = value;
            this.hashmask = hashmask;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof HashableInteger) {
                HashableInteger other = (HashableInteger) obj;

                return other.value == value;
            }

            return false;
        }

        @Override
        public int hashCode() {
            return value % hashmask;
        }

        @Override
        public int compareTo(HashableInteger o) {
            return value - o.value;
        }

        @Override
        public String toString() {
            return Integer.toString(value);
        }
    }

    static HashableInteger EXTRA_INT_VAL;
    static String EXTRA_STRING_VAL;

    private static Object[][] makeTestData(int size) {
        HashableInteger UNIQUE_OBJECTS[] = new HashableInteger[size];
        HashableInteger COLLIDING_OBJECTS[] = new HashableInteger[size];
        String UNIQUE_STRINGS[] = new String[size];
        String COLLIDING_STRINGS[] = new String[size];

        for (int i = 0; i < size; i++) {
            UNIQUE_OBJECTS[i] = new HashableInteger(i, Integer.MAX_VALUE);
            COLLIDING_OBJECTS[i] = new HashableInteger(i, 10);
            UNIQUE_STRINGS[i] = unhash(i);
            COLLIDING_STRINGS[i] = (0 == i % 2)
                    ? UNIQUE_STRINGS[i / 2]
                    : "\u0000\u0000\u0000\u0000\u0000" + COLLIDING_STRINGS[i - 1];
        }
        EXTRA_INT_VAL = new HashableInteger(size, Integer.MAX_VALUE);
        EXTRA_STRING_VAL = new String ("Extra Value");

     return new Object[][] {
            new Object[]{"Unique Objects", UNIQUE_OBJECTS},
            new Object[]{"Colliding Objects", COLLIDING_OBJECTS},
            new Object[]{"Unique Strings", UNIQUE_STRINGS},
            new Object[]{"Colliding Strings", COLLIDING_STRINGS}
        };
    }

    /**
     * Returns a string with a hash equal to the argument.
     *
     * @return string with a hash equal to the argument.
     */
    public static String unhash(int target) {
        StringBuilder answer = new StringBuilder();
        if (target < 0) {
            // String with hash of Integer.MIN_VALUE, 0x80000000
            answer.append("\\u0915\\u0009\\u001e\\u000c\\u0002");

            if (target == Integer.MIN_VALUE) {
                return answer.toString();
            }
            // Find target without sign bit set
            target = target & Integer.MAX_VALUE;
        }

        unhash0(answer, target);
        return answer.toString();
    }

    private static void unhash0(StringBuilder partial, int target) {
        int div = target / 31;
        int rem = target % 31;

        if (div <= Character.MAX_VALUE) {
            if (div != 0) {
                partial.append((char) div);
            }
            partial.append((char) rem);
        } else {
            unhash0(partial, div);
            partial.append((char) rem);
        }
    }

    private static void realMain(String[] args) throws Throwable {
        boolean shortRun = args.length > 0 && args[0].equals("-shortrun");

        Object[][] mapKeys = makeTestData(shortRun ? (TEST_SIZE / 2) : TEST_SIZE);

        // loop through data sets
        for (Object[] keys_desc : mapKeys) {
            Map<Object, Object>[] maps = (Map<Object, Object>[]) new Map[]{
                        new HashMap<>(),
                        new LinkedHashMap<>(),
                    };

            // for each map type.
            for (Map<Object, Object> map : maps) {
                String desc = (String) keys_desc[0];
                Object[] keys = (Object[]) keys_desc[1];
                try {
                    testInPlaceOps(map, desc, keys);
                } catch(Exception all) {
                    unexpected("Failed for " + map.getClass().getName() + " with " + desc, all);
                }
            }
        }
    }

    private static <T> void testInsertion(Map<T, T> map, String keys_desc, T[] keys) {
        check("map empty", (map.size() == 0) && map.isEmpty());

        for (int i = 0; i < keys.length; i++) {
            check(String.format("insertion: map expected size m%d != i%d", map.size(), i),
                    map.size() == i);
            check(String.format("insertion: put(%s[%d])", keys_desc, i), null == map.put(keys[i], keys[i]));
            check(String.format("insertion: containsKey(%s[%d])", keys_desc, i), map.containsKey(keys[i]));
            check(String.format("insertion: containsValue(%s[%d])", keys_desc, i), map.containsValue(keys[i]));
        }

        check(String.format("map expected size m%d != k%d", map.size(), keys.length),
                map.size() == keys.length);
    }


    private static <T> void testInPlaceOps(Map<T, T> map, String keys_desc, T[] keys) {
        System.out.println(map.getClass() + " : " + keys_desc + ", testInPlaceOps");
        System.out.flush();

        testInsertion(map, keys_desc, keys);
        testPutIfAbsent(map, keys_desc, keys);

        map.clear();
        testInsertion(map, keys_desc, keys);
        testRemoveMapping(map, keys_desc, keys);

        map.clear();
        testInsertion(map, keys_desc, keys);
        testReplaceOldValue(map, keys_desc, keys);

        map.clear();
        testInsertion(map, keys_desc, keys);
        testReplaceIfMapped(map, keys_desc, keys);

        map.clear();
        testInsertion(map, keys_desc, keys);
        testComputeIfAbsent(map, keys_desc, keys, (k) -> getExtraVal(keys[0]));

        map.clear();
        testInsertion(map, keys_desc, keys);
        testComputeIfAbsent(map, keys_desc, keys, (k) -> null);

        map.clear();
        testInsertion(map, keys_desc, keys);
        testComputeIfPresent(map, keys_desc, keys, (k, v) -> getExtraVal(keys[0]));

        map.clear();
        testInsertion(map, keys_desc, keys);
        testComputeIfPresent(map, keys_desc, keys, (k, v) -> null);

        if (!keys_desc.contains("Strings")) { // avoid parseInt() number format error
            map.clear();
            testInsertion(map, keys_desc, keys);
            testComputeNonNull(map, keys_desc, keys);
        }

        map.clear();
        testInsertion(map, keys_desc, keys);
        testComputeNull(map, keys_desc, keys);

        if (!keys_desc.contains("Strings")) { // avoid parseInt() number format error
            map.clear();
            testInsertion(map, keys_desc, keys);
            testMergeNonNull(map, keys_desc, keys);
        }

        map.clear();
        testInsertion(map, keys_desc, keys);
        testMergeNull(map, keys_desc, keys);
    }



    private static <T> void testPutIfAbsent(Map<T, T> map, String keys_desc, T[] keys) {
        T extraVal = getExtraVal(keys[0]);
        T retVal;
        removeOddKeys(map, keys);
        for (int i = 0; i < keys.length; i++) {
            retVal = map.putIfAbsent(keys[i], extraVal);
            if (i % 2 == 0) { // even: not absent, not put
                check(String.format("putIfAbsent: (%s[%d]) retVal", keys_desc, i), retVal == keys[i]);
                check(String.format("putIfAbsent: get(%s[%d])", keys_desc, i), keys[i] == map.get(keys[i]));
                check(String.format("putIfAbsent: containsValue(%s[%d])", keys_desc, i), map.containsValue(keys[i]));
            } else { // odd: absent, was put
                check(String.format("putIfAbsent: (%s[%d]) retVal", keys_desc, i), retVal == null);
                check(String.format("putIfAbsent: get(%s[%d])", keys_desc, i), extraVal == map.get(keys[i]));
                check(String.format("putIfAbsent: !containsValue(%s[%d])", keys_desc, i), !map.containsValue(keys[i]));
            }
            check(String.format("insertion: containsKey(%s[%d])", keys_desc, i), map.containsKey(keys[i]));
        }
        check(String.format("map expected size m%d != k%d", map.size(), keys.length),
                map.size() == keys.length);
    }

    private static <T> void testRemoveMapping(Map<T, T> map, String keys_desc, T[] keys) {
        T extraVal = getExtraVal(keys[0]);
        boolean removed;
        int removes = 0;
        remapOddKeys(map, keys);
        for (int i = 0; i < keys.length; i++) {
            removed = map.remove(keys[i], keys[i]);
            if (i % 2 == 0) { // even: original mapping, should be removed
                check(String.format("removeMapping: retVal(%s[%d])", keys_desc, i), removed);
                check(String.format("removeMapping: get(%s[%d])", keys_desc, i), null == map.get(keys[i]));
                check(String.format("removeMapping: !containsKey(%s[%d])", keys_desc, i), !map.containsKey(keys[i]));
                check(String.format("removeMapping: !containsValue(%s[%d])", keys_desc, i), !map.containsValue(keys[i]));
                removes++;
            } else { // odd: new mapping, not removed
                check(String.format("removeMapping: retVal(%s[%d])", keys_desc, i), !removed);
                check(String.format("removeMapping: get(%s[%d])", keys_desc, i), extraVal == map.get(keys[i]));
                check(String.format("removeMapping: containsKey(%s[%d])", keys_desc, i), map.containsKey(keys[i]));
                check(String.format("removeMapping: containsValue(%s[%d])", keys_desc, i), map.containsValue(extraVal));
            }
        }
        check(String.format("map expected size m%d != k%d", map.size(), keys.length - removes),
                map.size() == keys.length - removes);
    }

    private static <T> void testReplaceOldValue(Map<T, T> map, String keys_desc, T[] keys) {
        // remap odds to extraVal
        // call replace to replace for extraVal, for all keys
        // check that all keys map to value from keys array
        T extraVal = getExtraVal(keys[0]);
        boolean replaced;
        remapOddKeys(map, keys);

        for (int i = 0; i < keys.length; i++) {
            replaced = map.replace(keys[i], extraVal, keys[i]);
            if (i % 2 == 0) { // even: original mapping, should not be replaced
                check(String.format("replaceOldValue: retVal(%s[%d])", keys_desc, i), !replaced);
            } else { // odd: new mapping, should be replaced
                check(String.format("replaceOldValue: get(%s[%d])", keys_desc, i), replaced);
            }
            check(String.format("replaceOldValue: get(%s[%d])", keys_desc, i), keys[i] == map.get(keys[i]));
            check(String.format("replaceOldValue: containsKey(%s[%d])", keys_desc, i), map.containsKey(keys[i]));
            check(String.format("replaceOldValue: containsValue(%s[%d])", keys_desc, i), map.containsValue(keys[i]));
//            removes++;
        }
        check(String.format("replaceOldValue: !containsValue(%s[%s])", keys_desc, extraVal.toString()), !map.containsValue(extraVal));
        check(String.format("map expected size m%d != k%d", map.size(), keys.length),
                map.size() == keys.length);
    }

    // TODO: Test case for key mapped to null value
    private static <T> void testReplaceIfMapped(Map<T, T> map, String keys_desc, T[] keys) {
        // remove odd keys
        // call replace for all keys[]
        // odd keys should remain absent, even keys should be mapped to EXTRA, no value from keys[] should be in map
        T extraVal = getExtraVal(keys[0]);
        int expectedSize1 = 0;
        removeOddKeys(map, keys);
        int expectedSize2 = map.size();

        for (int i = 0; i < keys.length; i++) {
            T retVal = map.replace(keys[i], extraVal);
            if (i % 2 == 0) { // even: still in map, should be replaced
                check(String.format("replaceIfMapped: retVal(%s[%d])", keys_desc, i), retVal == keys[i]);
                check(String.format("replaceIfMapped: get(%s[%d])", keys_desc, i), extraVal == map.get(keys[i]));
                check(String.format("replaceIfMapped: containsKey(%s[%d])", keys_desc, i), map.containsKey(keys[i]));
                expectedSize1++;
            } else { // odd: was removed, should not be replaced
                check(String.format("replaceIfMapped: retVal(%s[%d])", keys_desc, i), retVal == null);
                check(String.format("replaceIfMapped: get(%s[%d])", keys_desc, i), null == map.get(keys[i]));
                check(String.format("replaceIfMapped: containsKey(%s[%d])", keys_desc, i), !map.containsKey(keys[i]));
            }
            check(String.format("replaceIfMapped: !containsValue(%s[%d])", keys_desc, i), !map.containsValue(keys[i]));
        }
        check(String.format("replaceIfMapped: containsValue(%s[%s])", keys_desc, extraVal.toString()), map.containsValue(extraVal));
        check(String.format("map expected size#1 m%d != k%d", map.size(), expectedSize1),
                map.size() == expectedSize1);
        check(String.format("map expected size#2 m%d != k%d", map.size(), expectedSize2),
                map.size() == expectedSize2);

    }

    private static <T> void testComputeIfAbsent(Map<T, T> map, String keys_desc, T[] keys,
                                                Function<T,T> mappingFunction) {
        // remove a third of the keys
        // call computeIfAbsent for all keys, func returns EXTRA
        // check that removed keys now -> EXTRA, other keys -> original val
        T expectedVal = mappingFunction.apply(keys[0]);
        T retVal;
        int expectedSize = 0;
        removeThirdKeys(map, keys);
        for (int i = 0; i < keys.length; i++) {
            retVal = map.computeIfAbsent(keys[i], mappingFunction);
            if (i % 3 != 2) { // key present, not computed
                check(String.format("computeIfAbsent: (%s[%d]) retVal", keys_desc, i), retVal == keys[i]);
                check(String.format("computeIfAbsent: get(%s[%d])", keys_desc, i), keys[i] == map.get(keys[i]));
                check(String.format("computeIfAbsent: containsValue(%s[%d])", keys_desc, i), map.containsValue(keys[i]));
                check(String.format("insertion: containsKey(%s[%d])", keys_desc, i), map.containsKey(keys[i]));
                expectedSize++;
            } else { // key absent, computed unless function return null
                check(String.format("computeIfAbsent: (%s[%d]) retVal", keys_desc, i), retVal == expectedVal);
                check(String.format("computeIfAbsent: get(%s[%d])", keys_desc, i), expectedVal == map.get(keys[i]));
                check(String.format("computeIfAbsent: !containsValue(%s[%d])", keys_desc, i), !map.containsValue(keys[i]));
                // mapping should not be added if function returns null
                check(String.format("insertion: containsKey(%s[%d])", keys_desc, i), map.containsKey(keys[i]) != (expectedVal == null));
                if (expectedVal != null) { expectedSize++; }
            }
        }
        if (expectedVal != null) {
            check(String.format("computeIfAbsent: containsValue(%s[%s])", keys_desc, expectedVal), map.containsValue(expectedVal));
        }
        check(String.format("map expected size m%d != k%d", map.size(), expectedSize),
                map.size() == expectedSize);
    }

    private static <T> void testComputeIfPresent(Map<T, T> map, String keys_desc, T[] keys,
                                                BiFunction<T,T,T> mappingFunction) {
        // remove a third of the keys
        // call testComputeIfPresent for all keys[]
        // removed keys should remain absent, even keys should be mapped to $RESULT
        // no value from keys[] should be in map
        T funcResult = mappingFunction.apply(keys[0], keys[0]);
        int expectedSize1 = 0;
        removeThirdKeys(map, keys);

        for (int i = 0; i < keys.length; i++) {
            T retVal = map.computeIfPresent(keys[i], mappingFunction);
            if (i % 3 != 2) { // key present
                if (funcResult == null) { // was removed
                    check(String.format("replaceIfMapped: containsKey(%s[%d])", keys_desc, i), !map.containsKey(keys[i]));
                } else { // value was replaced
                    check(String.format("replaceIfMapped: containsKey(%s[%d])", keys_desc, i), map.containsKey(keys[i]));
                    expectedSize1++;
                }
                check(String.format("computeIfPresent: retVal(%s[%s])", keys_desc, i), retVal == funcResult);
                check(String.format("replaceIfMapped: get(%s[%d])", keys_desc, i), funcResult == map.get(keys[i]));

            } else { // odd: was removed, should not be replaced
                check(String.format("replaceIfMapped: retVal(%s[%d])", keys_desc, i), retVal == null);
                check(String.format("replaceIfMapped: get(%s[%d])", keys_desc, i), null == map.get(keys[i]));
                check(String.format("replaceIfMapped: containsKey(%s[%d])", keys_desc, i), !map.containsKey(keys[i]));
            }
            check(String.format("replaceIfMapped: !containsValue(%s[%d])", keys_desc, i), !map.containsValue(keys[i]));
        }
        check(String.format("map expected size#1 m%d != k%d", map.size(), expectedSize1),
                map.size() == expectedSize1);
    }

    private static <T> void testComputeNonNull(Map<T, T> map, String keys_desc, T[] keys) {
        // remove a third of the keys
        // call compute() for all keys[]
        // all keys should be present: removed keys -> EXTRA, others to k-1
        BiFunction<T,T,T> mappingFunction = (k, v) -> {
                if (v == null) {
                    return getExtraVal(keys[0]);
                } else {
                    return keys[Integer.parseInt(k.toString()) - 1];
                }
            };
        T extraVal = getExtraVal(keys[0]);
        removeThirdKeys(map, keys);
        for (int i = 1; i < keys.length; i++) {
            T retVal = map.compute(keys[i], mappingFunction);
            if (i % 3 != 2) { // key present, should be mapped to k-1
                check(String.format("compute: retVal(%s[%d])", keys_desc, i), retVal == keys[i-1]);
                check(String.format("compute: get(%s[%d])", keys_desc, i), keys[i-1] == map.get(keys[i]));
            } else { // odd: was removed, should be replaced with EXTRA
                check(String.format("compute: retVal(%s[%d])", keys_desc, i), retVal == extraVal);
                check(String.format("compute: get(%s[%d])", keys_desc, i), extraVal == map.get(keys[i]));
            }
            check(String.format("compute: containsKey(%s[%d])", keys_desc, i), map.containsKey(keys[i]));
        }
        check(String.format("map expected size#1 m%d != k%d", map.size(), keys.length),
                map.size() == keys.length);
        check(String.format("compute: containsValue(%s[%s])", keys_desc, extraVal.toString()), map.containsValue(extraVal));
        check(String.format("compute: !containsValue(%s,[null])", keys_desc), !map.containsValue(null));
    }

    private static <T> void testComputeNull(Map<T, T> map, String keys_desc, T[] keys) {
        // remove a third of the keys
        // call compute() for all keys[]
        // removed keys should -> EXTRA
        // for other keys: func returns null, should have no mapping
        BiFunction<T,T,T> mappingFunction = (k, v) -> {
            // if absent/null -> EXTRA
            // if present -> null
            if (v == null) {
                return getExtraVal(keys[0]);
            } else {
                return null;
            }
        };
        T extraVal = getExtraVal(keys[0]);
        int expectedSize = 0;
        removeThirdKeys(map, keys);
        for (int i = 0; i < keys.length; i++) {
            T retVal = map.compute(keys[i], mappingFunction);
            if (i % 3 != 2) { // key present, func returned null, should be absent from map
                check(String.format("compute: retVal(%s[%d])", keys_desc, i), retVal == null);
                check(String.format("compute: get(%s[%d])", keys_desc, i), null == map.get(keys[i]));
                check(String.format("compute: containsKey(%s[%d])", keys_desc, i), !map.containsKey(keys[i]));
                check(String.format("compute: containsValue(%s[%s])", keys_desc, i), !map.containsValue(keys[i]));
            } else { // odd: was removed, should now be mapped to EXTRA
                check(String.format("compute: retVal(%s[%d])", keys_desc, i), retVal == extraVal);
                check(String.format("compute: get(%s[%d])", keys_desc, i), extraVal == map.get(keys[i]));
                check(String.format("compute: containsKey(%s[%d])", keys_desc, i), map.containsKey(keys[i]));
                expectedSize++;
            }
        }
        check(String.format("compute: containsValue(%s[%s])", keys_desc, extraVal.toString()), map.containsValue(extraVal));
        check(String.format("map expected size#1 m%d != k%d", map.size(), expectedSize),
                map.size() == expectedSize);
    }

    private static <T> void testMergeNonNull(Map<T, T> map, String keys_desc, T[] keys) {
        // remove a third of the keys
        // call merge() for all keys[]
        // all keys should be present: removed keys now -> EXTRA, other keys -> k-1

        // Map to preceding key
        BiFunction<T,T,T> mappingFunction = (k, v) -> keys[Integer.parseInt(k.toString()) - 1];
        T extraVal = getExtraVal(keys[0]);
        removeThirdKeys(map, keys);
        for (int i = 1; i < keys.length; i++) {
            T retVal = map.merge(keys[i], extraVal, mappingFunction);
            if (i % 3 != 2) { // key present, should be mapped to k-1
                check(String.format("compute: retVal(%s[%d])", keys_desc, i), retVal == keys[i-1]);
                check(String.format("compute: get(%s[%d])", keys_desc, i), keys[i-1] == map.get(keys[i]));
            } else { // odd: was removed, should be replaced with EXTRA
                check(String.format("compute: retVal(%s[%d])", keys_desc, i), retVal == extraVal);
                check(String.format("compute: get(%s[%d])", keys_desc, i), extraVal == map.get(keys[i]));
            }
            check(String.format("compute: containsKey(%s[%d])", keys_desc, i), map.containsKey(keys[i]));
        }

        check(String.format("map expected size#1 m%d != k%d", map.size(), keys.length),
                map.size() == keys.length);
        check(String.format("compute: containsValue(%s[%s])", keys_desc, extraVal.toString()), map.containsValue(extraVal));
        check(String.format("compute: !containsValue(%s,[null])", keys_desc), !map.containsValue(null));

    }

    private static <T> void testMergeNull(Map<T, T> map, String keys_desc, T[] keys) {
        // remove a third of the keys
        // call merge() for all keys[]
        // result: removed keys -> EXTRA, other keys absent

        BiFunction<T,T,T> mappingFunction = (k, v) -> null;
        T extraVal = getExtraVal(keys[0]);
        int expectedSize = 0;
        removeThirdKeys(map, keys);
        for (int i = 0; i < keys.length; i++) {
            T retVal = map.merge(keys[i], extraVal, mappingFunction);
            if (i % 3 != 2) { // key present, func returned null, should be absent from map
                check(String.format("compute: retVal(%s[%d])", keys_desc, i), retVal == null);
                check(String.format("compute: get(%s[%d])", keys_desc, i), null == map.get(keys[i]));
                check(String.format("compute: containsKey(%s[%d])", keys_desc, i), !map.containsKey(keys[i]));
            } else { // odd: was removed, should now be mapped to EXTRA
                check(String.format("compute: retVal(%s[%d])", keys_desc, i), retVal == extraVal);
                check(String.format("compute: get(%s[%d])", keys_desc, i), extraVal == map.get(keys[i]));
                check(String.format("compute: containsKey(%s[%d])", keys_desc, i), map.containsKey(keys[i]));
                expectedSize++;
            }
            check(String.format("compute: containsValue(%s[%s])", keys_desc, i), !map.containsValue(keys[i]));
        }
        check(String.format("compute: containsValue(%s[%s])", keys_desc, extraVal.toString()), map.containsValue(extraVal));
        check(String.format("map expected size#1 m%d != k%d", map.size(), expectedSize),
                map.size() == expectedSize);
    }

    /*
     * Return the EXTRA val for the key type being used
     */
    private static <T> T getExtraVal(T key) {
        if (key instanceof HashableInteger) {
            return (T)EXTRA_INT_VAL;
        } else {
            return (T)EXTRA_STRING_VAL;
        }
    }

    /*
     * Remove half of the keys
     */
    private static <T> void removeOddKeys(Map<T, T> map, /*String keys_desc, */ T[] keys) {
        int removes = 0;
        for (int i = 0; i < keys.length; i++) {
            if (i % 2 != 0) {
                map.remove(keys[i]);
                removes++;
            }
        }
        check(String.format("map expected size m%d != k%d", map.size(), keys.length - removes),
                map.size() == keys.length - removes);
    }

    /*
     * Remove every third key
     * This will hopefully leave some removed keys in TreeBins for, e.g., computeIfAbsent
     * w/ a func that returns null.
     *
     * TODO: consider using this in other tests (and maybe adding a remapThirdKeys)
     */
    private static <T> void removeThirdKeys(Map<T, T> map, /*String keys_desc, */ T[] keys) {
        int removes = 0;
        for (int i = 0; i < keys.length; i++) {
            if (i % 3 == 2) {
                map.remove(keys[i]);
                removes++;
            }
        }
        check(String.format("map expected size m%d != k%d", map.size(), keys.length - removes),
                map.size() == keys.length - removes);
    }

    /*
     * Re-map the odd-numbered keys to map to the EXTRA value
     */
    private static <T> void remapOddKeys(Map<T, T> map, /*String keys_desc, */ T[] keys) {
        T extraVal = getExtraVal(keys[0]);
        for (int i = 0; i < keys.length; i++) {
            if (i % 2 != 0) {
                map.put(keys[i], extraVal);
            }
        }
    }

    //--------------------- Infrastructure ---------------------------
    static volatile int passed = 0, failed = 0;

    static void pass() {
        passed++;
    }

    static void fail() {
        failed++;
        (new Error("Failure")).printStackTrace(System.err);
    }

    static void fail(String msg) {
        failed++;
        (new Error("Failure: " + msg)).printStackTrace(System.err);
    }

    static void abort() {
        fail();
        System.exit(1);
    }

    static void abort(String msg) {
        fail(msg);
        System.exit(1);
    }

    static void unexpected(String msg, Throwable t) {
        System.err.println("Unexpected: " + msg);
        unexpected(t);
    }

    static void unexpected(Throwable t) {
        failed++;
        t.printStackTrace(System.err);
    }

    static void check(boolean cond) {
        if (cond) {
            pass();
        } else {
            fail();
        }
    }

    static void check(String desc, boolean cond) {
        if (cond) {
            pass();
        } else {
            fail(desc);
        }
    }

    static void equal(Object x, Object y) {
        if (Objects.equals(x, y)) {
            pass();
        } else {
            fail(x + " not equal to " + y);
        }
    }

    public static void main(String[] args) throws Throwable {
        Thread.currentThread().setName(Collisions.class.getName());
//        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
        try {
            realMain(args);
        } catch (Throwable t) {
            unexpected(t);
        }

        System.out.printf("%nPassed = %d, failed = %d%n%n", passed, failed);
        if (failed > 0) {
            throw new Error("Some tests failed");
        }
    }
}
