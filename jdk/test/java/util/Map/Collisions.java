/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7126277
 * @run main Collisions -shortrun
 * @summary Ensure Maps behave well with lots of hashCode() collisions.
 * @author Mike Duigou
 */
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

public class Collisions {

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
                        new Hashtable<>(),
                        new IdentityHashMap<>(),
                        new LinkedHashMap<>(),
                        new TreeMap<>(),
                        new WeakHashMap<>(),
                        new ConcurrentHashMap<>(),
                        new ConcurrentSkipListMap<>()
                    };

            // for each map type.
            for (Map<Object, Object> map : maps) {
                String desc = (String) keys_desc[0];
                Object[] keys = (Object[]) keys_desc[1];
                try {
                    testMap(map, desc, keys);
                } catch(Exception all) {
                    unexpected("Failed for " + map.getClass().getName() + " with " + desc, all);
                }
            }
        }
    }

    private static <T> void testMap(Map<T, T> map, String keys_desc, T[] keys) {
        System.out.println(map.getClass() + " : " + keys_desc);
        System.out.flush();
        testInsertion(map, keys_desc, keys);

        if (keys[0] instanceof HashableInteger) {
            testIntegerIteration((Map<HashableInteger, HashableInteger>) map, (HashableInteger[]) keys);
        } else {
            testStringIteration((Map<String, String>) map, (String[]) keys);
        }

        testContainsKey(map, keys_desc, keys);

        testRemove(map, keys_desc, keys);

        map.clear();
        testInsertion(map, keys_desc, keys);
        testKeysIteratorRemove(map, keys_desc, keys);

        map.clear();
        testInsertion(map, keys_desc, keys);
        testValuesIteratorRemove(map, keys_desc, keys);

        map.clear();
        testInsertion(map, keys_desc, keys);
        testEntriesIteratorRemove(map, keys_desc, keys);

        check(map.isEmpty());
    }

    private static <T> void testInsertion(Map<T, T> map, String keys_desc, T[] keys) {
        check(map.size() == 0 && map.isEmpty(), "map empty");

        for (int i = 0; i < keys.length; i++) {
            check(map.size() == i, "insertion: map expected size m%d != i%d", map.size(), i);
            check(null == map.put(keys[i], keys[i]), "insertion: put(%s[%d])", keys_desc, i);
            check(map.containsKey(keys[i]), "insertion: containsKey(%s[%d])", keys_desc, i);
            check(map.containsValue(keys[i]), "insertion: containsValue(%s[%d])", keys_desc, i);
        }

        check(map.size() == keys.length, "map expected size m%d != k%d", map.size(), keys.length);
    }

    private static void testIntegerIteration(Map<HashableInteger, HashableInteger> map, HashableInteger[] keys) {
        check(map.size() == keys.length, "map expected size m%d != k%d", map.size(), keys.length);

        BitSet all = new BitSet(keys.length);
        for (Map.Entry<HashableInteger, HashableInteger> each : map.entrySet()) {
            check(!all.get(each.getKey().value), "Iteration: key already seen");
            all.set(each.getKey().value);
        }

        all.flip(0, keys.length);
        check(all.isEmpty(), "Iteration: some keys not visited");

        for (HashableInteger each : map.keySet()) {
            check(!all.get(each.value), "Iteration: key already seen");
            all.set(each.value);
        }

        all.flip(0, keys.length);
        check(all.isEmpty(), "Iteration: some keys not visited");

        int count = 0;
        for (HashableInteger each : map.values()) {
            count++;
        }

        check(map.size() == count, "Iteration: value count matches size m%d != c%d", map.size(), count);
    }

    private static void testStringIteration(Map<String, String> map, String[] keys) {
        check(map.size() == keys.length, "map expected size m%d != k%d", map.size(), keys.length);

        BitSet all = new BitSet(keys.length);
        for (Map.Entry<String, String> each : map.entrySet()) {
            String key = each.getKey();
            boolean longKey = key.length() > 5;
            int index = key.hashCode() + (longKey ? keys.length / 2 : 0);
            check(!all.get(index), "key already seen");
            all.set(index);
        }

        all.flip(0, keys.length);
        check(all.isEmpty(), "some keys not visited");

        for (String each : map.keySet()) {
            boolean longKey = each.length() > 5;
            int index = each.hashCode() + (longKey ? keys.length / 2 : 0);
            check(!all.get(index), "key already seen");
            all.set(index);
        }

        all.flip(0, keys.length);
        check(all.isEmpty(), "some keys not visited");

        int count = 0;
        for (String each : map.values()) {
            count++;
        }

        check(map.size() == keys.length, "value count matches size m%d != k%d", map.size(), keys.length);
    }

    private static <T> void testContainsKey(Map<T, T> map, String keys_desc, T[] keys) {
        for (int i = 0; i < keys.length; i++) {
            T each = keys[i];
            check(map.containsKey(each), "containsKey: %s[%d]%s", keys_desc, i, each);
        }
    }

    private static <T> void testRemove(Map<T, T> map, String keys_desc, T[] keys) {
        check(map.size() == keys.length, "remove: map expected size m%d != k%d", map.size(), keys.length);

        for (int i = 0; i < keys.length; i++) {
            T each = keys[i];
            check(null != map.remove(each), "remove: %s[%d]%s", keys_desc, i, each);
        }

        check(map.size() == 0 && map.isEmpty(), "remove: map empty. size=%d", map.size());
    }

    private static <T> void testKeysIteratorRemove(Map<T, T> map, String keys_desc, T[] keys) {
        check(map.size() == keys.length, "remove: map expected size m%d != k%d", map.size(), keys.length);

        Iterator<T> each = map.keySet().iterator();
        while (each.hasNext()) {
            T t = each.next();
            each.remove();
            check(!map.containsKey(t), "not removed: %s", each);
        }

        check(map.size() == 0 && map.isEmpty(), "remove: map empty. size=%d", map.size());
    }

    private static <T> void testValuesIteratorRemove(Map<T, T> map, String keys_desc, T[] keys) {
        check(map.size() == keys.length, "remove: map expected size m%d != k%d", map.size(), keys.length);

        Iterator<T> each = map.values().iterator();
        while (each.hasNext()) {
            T t = each.next();
            each.remove();
            check(!map.containsValue(t), "not removed: %s", each);
        }

        check(map.size() == 0 && map.isEmpty(), "remove: map empty. size=%d", map.size());
    }

    private static <T> void testEntriesIteratorRemove(Map<T, T> map, String keys_desc, T[] keys) {
        check(map.size() == keys.length, "remove: map expected size m%d != k%d", map.size(), keys.length);

        Iterator<Map.Entry<T,T>> each = map.entrySet().iterator();
        while (each.hasNext()) {
            Map.Entry<T,T> t = each.next();
            T key = t.getKey();
            T value = t.getValue();
            each.remove();
            check((map instanceof IdentityHashMap) || !map.entrySet().contains(t), "not removed: %s", each);
            check(!map.containsKey(key),                                           "not removed: %s", each);
            check(!map.containsValue(value),                                       "not removed: %s", each);
        }

        check(map.size() == 0 && map.isEmpty(), "remove: map empty. size=%d", map.size());
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

    static void check(boolean cond, String desc) {
        if (cond) {
            pass();
        } else {
            fail(desc);
        }
    }

    static void check(boolean cond, String fmt, int i) {
        if (cond) {
            pass();
        } else {
            fail(String.format(fmt, i));
        }
    }

    static void check(boolean cond, String fmt, Object o) {
        if (cond) {
            pass();
        } else {
            fail(String.format(fmt, o));
        }
    }

    static void check(boolean cond, String fmt, int i1, int i2) {
        if (cond) {
            pass();
        } else {
            fail(String.format(fmt, i1, i2));
        }
    }

    static void check(boolean cond, String fmt, String s, int i) {
        if (cond) {
            pass();
        } else {
            fail(String.format(fmt, s, i));
        }
    }

    static void check(boolean cond, String fmt, String s, int i, Object o) {
        if (cond) {
            pass();
        } else {
            fail(String.format(fmt, s, i, o));
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
