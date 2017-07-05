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
 * @bug 8010122 8004518
 * @summary Test Map default methods
 * @author Mike Duigou
 * @run testng Defaults
 */
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Supplier;

import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;
import static org.testng.Assert.fail;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

public class Defaults {

    @Test(dataProvider = "Nulls Map<IntegerEnum,String>")
    public void testGetOrDefaultNulls(String description, Map<IntegerEnum, String> map) {
        assertTrue(map.containsKey(null), "null key absent");
        assertNull(map.get(null), "value not null");
        assertSame(map.get(null), map.getOrDefault(null, EXTRA_VALUE), "values should match");
    }

    @Test(dataProvider = "Map<IntegerEnum,String>")
    public void testGetOrDefault(String description, Map<IntegerEnum, String> map) {
        assertTrue(map.containsKey(KEYS[1]), "expected key missing");
        assertSame(map.get(KEYS[1]), map.getOrDefault(KEYS[1], EXTRA_VALUE), "values should match");
        assertFalse(map.containsKey(EXTRA_KEY), "expected absent key");
        assertSame(map.getOrDefault(EXTRA_KEY, EXTRA_VALUE), EXTRA_VALUE, "value not returned as default");
        assertNull(map.getOrDefault(EXTRA_KEY, null), "null not returned as default");
    }

    @Test(dataProvider = "R/W Nulls Map<IntegerEnum,String>")
    public void testPutIfAbsentNulls(String description, Map<IntegerEnum, String> map) {
        assertTrue(map.containsKey(null), "null key absent");
        assertNull(map.get(null), "value not null");
        assertNull(map.putIfAbsent(null, EXTRA_VALUE), "previous not null");
        assertTrue(map.containsKey(null), "null key absent");
        assertSame(map.get(null), EXTRA_VALUE, "unexpected value");
        assertSame(map.putIfAbsent(null, null), EXTRA_VALUE, "previous not expected value");
        assertTrue(map.containsKey(null), "null key absent");
        assertSame(map.get(null), EXTRA_VALUE, "unexpected value");
        assertSame(map.remove(null), EXTRA_VALUE, "removed unexpected value");

        assertFalse(map.containsKey(null), description + ": key present after remove");
        assertNull(map.putIfAbsent(null, null), "previous not null");
        assertTrue(map.containsKey(null), "null key absent");
        assertNull(map.get(null), "value not null");
        assertNull(map.putIfAbsent(null, EXTRA_VALUE), "previous not null");
        assertSame(map.get(null), EXTRA_VALUE, "value not expected");
    }

    @Test(dataProvider = "R/W Map<IntegerEnum,String>")
    public void testPutIfAbsent(String description, Map<IntegerEnum, String> map) {
        assertTrue(map.containsKey(KEYS[1]));
        Object expected = map.get(KEYS[1]);
        assertTrue(null == expected || expected == VALUES[1]);
        assertSame(map.putIfAbsent(KEYS[1], EXTRA_VALUE), expected);
        assertSame(map.get(KEYS[1]), expected);

        assertFalse(map.containsKey(EXTRA_KEY));
        assertSame(map.putIfAbsent(EXTRA_KEY, EXTRA_VALUE), null);
        assertSame(map.get(EXTRA_KEY), EXTRA_VALUE);
    }

    @Test(dataProvider = "Nulls Map<IntegerEnum,String>")
    public void testForEach(String description, Map<IntegerEnum, String> map) {
        IntegerEnum[] EACH_KEY = new IntegerEnum[map.size()];

        map.forEach((k, v) -> {
            int idx = (null == k) ? 0 : k.ordinal(); // substitute for index.
            assertNull(EACH_KEY[idx]);
            EACH_KEY[idx] = (idx == 0) ? KEYS[0] : k; // substitute for comparison.
            assertSame(v, map.get(k));
        });

        assertEquals(KEYS, EACH_KEY);
    }

    @Test(dataProvider = "R/W Nulls Map<IntegerEnum,String>")
    public static void testRemoveNulls(String description, Map<IntegerEnum, String> map) {
        assertTrue(map.containsKey(null), "null key absent");
        assertNull(map.get(null), "value not null");
        assertFalse(map.remove(null, EXTRA_VALUE), description);
        assertTrue(map.containsKey(null));
        assertNull(map.get(null));
        assertTrue(map.remove(null, null));
        assertFalse(map.containsKey(null));
        assertNull(map.get(null));
        assertFalse(map.remove(null, null));
    }

    @Test(dataProvider = "R/W Map<IntegerEnum,String>")
    public static void testRemove(String description, Map<IntegerEnum, String> map) {
        assertTrue(map.containsKey(KEYS[1]));
        Object expected = map.get(KEYS[1]);
        assertTrue(null == expected || expected == VALUES[1]);
        assertFalse(map.remove(KEYS[1], EXTRA_VALUE), description);
        assertSame(map.get(KEYS[1]), expected);
        assertTrue(map.remove(KEYS[1], expected));
        assertNull(map.get(KEYS[1]));
        assertFalse(map.remove(KEYS[1], expected));

        assertFalse(map.containsKey(EXTRA_KEY));
        assertFalse(map.remove(EXTRA_KEY, EXTRA_VALUE));
    }

    @Test(dataProvider = "R/W Nulls Map<IntegerEnum,String>")
    public void testReplaceKVNulls(String description, Map<IntegerEnum, String> map) {
        assertTrue(map.containsKey(null), "null key absent");
        assertNull(map.get(null), "value not null");
        assertSame(map.replace(null, EXTRA_VALUE), null);
        assertSame(map.get(null), EXTRA_VALUE);
    }

    @Test(dataProvider = "R/W Map<IntegerEnum,String>")
    public void testReplaceKV(String description, Map<IntegerEnum, String> map) {
        assertTrue(map.containsKey(KEYS[1]));
        Object expected = map.get(KEYS[1]);
        assertTrue(null == expected || expected == VALUES[1]);
        assertSame(map.replace(KEYS[1], EXTRA_VALUE), expected);
        assertSame(map.get(KEYS[1]), EXTRA_VALUE);

        assertFalse(map.containsKey(EXTRA_KEY));
        assertNull(map.replace(EXTRA_KEY, EXTRA_VALUE));
        assertFalse(map.containsKey(EXTRA_KEY));
        assertNull(map.get(EXTRA_KEY));
        assertNull(map.put(EXTRA_KEY, EXTRA_VALUE));
        assertSame(map.get(EXTRA_KEY), EXTRA_VALUE);
        assertSame(map.replace(EXTRA_KEY, (String)expected), EXTRA_VALUE);
        assertSame(map.get(EXTRA_KEY), expected);
    }

    @Test(dataProvider = "R/W Nulls Map<IntegerEnum,String>")
    public void testReplaceKVVNulls(String description, Map<IntegerEnum, String> map) {
        assertTrue(map.containsKey(null), "null key absent");
        assertNull(map.get(null), "value not null");
        assertFalse(map.replace(null, EXTRA_VALUE, EXTRA_VALUE));
        assertNull(map.get(null));
        assertTrue(map.replace(null, null, EXTRA_VALUE));
        assertSame(map.get(null), EXTRA_VALUE);
        assertTrue(map.replace(null, EXTRA_VALUE, EXTRA_VALUE));
        assertSame(map.get(null), EXTRA_VALUE);
    }

    @Test(dataProvider = "R/W Map<IntegerEnum,String>")
    public void testReplaceKVV(String description, Map<IntegerEnum, String> map) {
        assertTrue(map.containsKey(KEYS[1]));
        Object expected = map.get(KEYS[1]);
        assertTrue(null == expected || expected == VALUES[1]);
        assertFalse(map.replace(KEYS[1], EXTRA_VALUE, EXTRA_VALUE));
        assertSame(map.get(KEYS[1]), expected);
        assertTrue(map.replace(KEYS[1], (String)expected, EXTRA_VALUE));
        assertSame(map.get(KEYS[1]), EXTRA_VALUE);
        assertTrue(map.replace(KEYS[1], EXTRA_VALUE, EXTRA_VALUE));
        assertSame(map.get(KEYS[1]), EXTRA_VALUE);

        assertFalse(map.containsKey(EXTRA_KEY));
        assertFalse(map.replace(EXTRA_KEY, EXTRA_VALUE, EXTRA_VALUE));
        assertFalse(map.containsKey(EXTRA_KEY));
        assertNull(map.get(EXTRA_KEY));
        assertNull(map.put(EXTRA_KEY, EXTRA_VALUE));
        assertTrue(map.containsKey(EXTRA_KEY));
        assertSame(map.get(EXTRA_KEY), EXTRA_VALUE);
        assertTrue(map.replace(EXTRA_KEY, EXTRA_VALUE, EXTRA_VALUE));
        assertSame(map.get(EXTRA_KEY), EXTRA_VALUE);
    }

    @Test(dataProvider = "R/W Nulls Map<IntegerEnum,String>")
    public void testComputeIfAbsentNulls(String description, Map<IntegerEnum, String> map) {
        assertTrue(map.containsKey(null), "null key absent");
        assertNull(map.get(null), "value not null");
        assertSame(map.computeIfAbsent(null, (k) -> EXTRA_VALUE), EXTRA_VALUE, description);
        assertSame(map.get(null), EXTRA_VALUE, description);
    }

    @Test(dataProvider = "R/W Map<IntegerEnum,String>")
    public void testComputeIfAbsent(String description, Map<IntegerEnum, String> map) {
        assertTrue(map.containsKey(KEYS[1]));
        Object expected = map.get(KEYS[1]);
        assertTrue(null == expected || expected == VALUES[1], description + String.valueOf(expected));
        expected = (null == expected) ? EXTRA_VALUE : expected;
        assertSame(map.computeIfAbsent(KEYS[1], (k) -> EXTRA_VALUE), expected, description);
        assertSame(map.get(KEYS[1]), expected, description);

        assertFalse(map.containsKey(EXTRA_KEY));
        assertSame(map.computeIfAbsent(EXTRA_KEY, (k) -> EXTRA_VALUE), EXTRA_VALUE);
        assertSame(map.get(EXTRA_KEY), EXTRA_VALUE);
    }

    @Test(dataProvider = "R/W Nulls Map<IntegerEnum,String>")
    public void testComputeIfPresentNulls(String description, Map<IntegerEnum, String> map) {
        assertTrue(map.containsKey(null));
        assertNull(map.get(null));
        assertSame(map.computeIfPresent(null, (k, v) -> {
            fail();
            return EXTRA_VALUE;
        }), null, description);
        assertTrue(map.containsKey(null));
        assertSame(map.get(null), null, description);
    }

    @Test(dataProvider = "R/W Map<IntegerEnum,String>")
    public void testComputeIfPresent(String description, Map<IntegerEnum, String> map) {
        assertTrue(map.containsKey(KEYS[1]));
        Object value = map.get(KEYS[1]);
        assertTrue(null == value || value == VALUES[1], description + String.valueOf(value));
        Object expected = (null == value) ? null : EXTRA_VALUE;
        assertSame(map.computeIfPresent(KEYS[1], (k, v) -> {
            assertSame(v, value);
            return EXTRA_VALUE;
        }), expected, description);
        assertSame(map.get(KEYS[1]), expected, description);

        assertFalse(map.containsKey(EXTRA_KEY));
        assertSame(map.computeIfPresent(EXTRA_KEY, (k, v) -> {
            fail();
            return EXTRA_VALUE;
        }), null);
        assertFalse(map.containsKey(EXTRA_KEY));
        assertSame(map.get(EXTRA_KEY), null);
    }

    @Test(dataProvider = "R/W Nulls Map<IntegerEnum,String>")
    public void testComputeNulls(String description, Map<IntegerEnum, String> map) {
        assertTrue(map.containsKey(null), "null key absent");
        assertNull(map.get(null), "value not null");
        assertSame(map.compute(null, (k, v) -> {
            assertSame(k, null);
            assertNull(v);
            return EXTRA_VALUE;
        }), EXTRA_VALUE, description);
        assertTrue(map.containsKey(null));
        assertSame(map.get(null), EXTRA_VALUE, description);
        assertSame(map.remove(null), EXTRA_VALUE, "removed value not expected");
        assertFalse(map.containsKey(null), "null key present");
        assertSame(map.compute(null, (k, v) -> {
            assertSame(k, null);
            assertNull(v);
            return null;
        }), null, description);
    }

    @Test(dataProvider = "R/W Map<IntegerEnum,String>")
    public void testCompute(String description, Map<IntegerEnum, String> map) {
        assertTrue(map.containsKey(KEYS[1]));
        Object value = map.get(KEYS[1]);
        assertTrue(null == value || value == VALUES[1], description + String.valueOf(value));
        assertSame(map.compute(KEYS[1], (k, v) -> {
            assertSame(k, KEYS[1]);
            assertSame(v, value);
            return EXTRA_VALUE;
        }), EXTRA_VALUE, description);
        assertSame(map.get(KEYS[1]), EXTRA_VALUE, description);
        assertNull(map.compute(KEYS[1], (k, v) -> {
            assertSame(v, EXTRA_VALUE);
            return null;
        }), description);
        assertFalse(map.containsKey(KEYS[1]));

        assertFalse(map.containsKey(EXTRA_KEY));
        assertSame(map.compute(EXTRA_KEY, (k, v) -> {
            assertNull(v);
            return EXTRA_VALUE;
        }), EXTRA_VALUE);
        assertTrue(map.containsKey(EXTRA_KEY));
        assertSame(map.get(EXTRA_KEY), EXTRA_VALUE);
    }


    @Test(dataProvider = "R/W Nulls Map<IntegerEnum,String>")
    public void testMergeNulls(String description, Map<IntegerEnum, String> map) {
        assertTrue(map.containsKey(null), "null key absent");
        assertNull(map.get(null), "value not null");
        assertSame(map.merge(null, EXTRA_VALUE, (v, vv) -> {
            assertNull(v);
            assertSame(vv, EXTRA_VALUE);
            return vv;
        }), EXTRA_VALUE, description);
        assertTrue(map.containsKey(null));
        assertSame(map.get(null), EXTRA_VALUE, description);
    }

    @Test(dataProvider = "R/W Map<IntegerEnum,String>")
    public void testMerge(String description, Map<IntegerEnum, String> map) {
        assertTrue(map.containsKey(KEYS[1]));
        Object value = map.get(KEYS[1]);
        assertTrue(null == value || value == VALUES[1], description + String.valueOf(value));
        assertSame(map.merge(KEYS[1], EXTRA_VALUE, (v, vv) -> {
            assertSame(v, value);
            assertSame(vv, EXTRA_VALUE);
            return vv;
        }), EXTRA_VALUE, description);
        assertSame(map.get(KEYS[1]), EXTRA_VALUE, description);
        assertNull(map.merge(KEYS[1], EXTRA_VALUE, (v, vv) -> {
            assertSame(v, EXTRA_VALUE);
            assertSame(vv, EXTRA_VALUE);
            return null;
        }), description);
        assertFalse(map.containsKey(KEYS[1]));

        assertFalse(map.containsKey(EXTRA_KEY));
        assertSame(map.merge(EXTRA_KEY, EXTRA_VALUE, (v, vv) -> {
            assertNull(v);
            assertSame(vv, EXTRA_VALUE);
            return EXTRA_VALUE;
        }), EXTRA_VALUE);
        assertTrue(map.containsKey(EXTRA_KEY));
        assertSame(map.get(EXTRA_KEY), EXTRA_VALUE);
    }

    enum IntegerEnum {

        e0, e1, e2, e3, e4, e5, e6, e7, e8, e9,
        e10, e11, e12, e13, e14, e15, e16, e17, e18, e19,
        e20, e21, e22, e23, e24, e25, e26, e27, e28, e29,
        e30, e31, e32, e33, e34, e35, e36, e37, e38, e39,
        e40, e41, e42, e43, e44, e45, e46, e47, e48, e49,
        e50, e51, e52, e53, e54, e55, e56, e57, e58, e59,
        e60, e61, e62, e63, e64, e65, e66, e67, e68, e69,
        e70, e71, e72, e73, e74, e75, e76, e77, e78, e79,
        e80, e81, e82, e83, e84, e85, e86, e87, e88, e89,
        e90, e91, e92, e93, e94, e95, e96, e97, e98, e99,
        EXTRA_KEY;
        public static final int SIZE = values().length;
    };
    private static final int TEST_SIZE = IntegerEnum.SIZE - 1;
    /**
     * Realized keys ensure that there is always a hard ref to all test objects.
     */
    private static final IntegerEnum[] KEYS = new IntegerEnum[TEST_SIZE];
    /**
     * Realized values ensure that there is always a hard ref to all test
     * objects.
     */
    private static final String[] VALUES = new String[TEST_SIZE];

    static {
        IntegerEnum[] keys = IntegerEnum.values();
        for (int each = 0; each < TEST_SIZE; each++) {
            KEYS[each] = keys[each];
            VALUES[each] = String.valueOf(each);
        }
    }
    private static final IntegerEnum EXTRA_KEY = IntegerEnum.EXTRA_KEY;
    private static final String EXTRA_VALUE = String.valueOf(TEST_SIZE);

    @DataProvider(name = "Map<IntegerEnum,String>", parallel = true)
    public static Iterator<Object[]> allNullsMapProvider() {
        return makeAllMaps().iterator();
    }

    @DataProvider(name = "Nulls Map<IntegerEnum,String>", parallel = true)
    public static Iterator<Object[]> allMapProvider() {
        return makeRWMaps(true).iterator();
    }

    @DataProvider(name = "R/W Map<IntegerEnum,String>", parallel = true)
    public static Iterator<Object[]> rwMapProvider() {
        return makeRWMapsNoNulls().iterator();
    }

    @DataProvider(name = "R/W Nulls Map<IntegerEnum,String>", parallel = true)
    public static Iterator<Object[]> rwNullsMapProvider() {
        return makeRWMaps(true).iterator();
    }

    private static Collection<Object[]> makeAllMapsNoNulls() {
        Collection<Object[]> all = new ArrayList<>();

        all.addAll(makeRWMaps(false));
        all.addAll(makeRWNoNullsMaps());
        all.addAll(makeROMaps(false));

        return all;
    }

    private static Collection<Object[]> makeRWMapsNoNulls() {
        Collection<Object[]> all = new ArrayList<>();

        all.addAll(makeRWMaps(false));
        all.addAll(makeRWNoNullsMaps());

        return all;
    }

    private static Collection<Object[]> makeAllMaps() {
        Collection<Object[]> all = new ArrayList<>();

        all.addAll(makeROMaps(false));
        all.addAll(makeRWMaps(false));
        all.addAll(makeRWNoNullsMaps());
        all.addAll(makeRWMaps(true));
        all.addAll(makeROMaps(true));

        return all;
    }

    private static Collection<Object[]> makeAllRWMaps() {
        Collection<Object[]> all = new ArrayList<>();

        all.addAll(makeRWMaps(false));
        all.addAll(makeRWNoNullsMaps());
        all.addAll(makeRWMaps(true));

        return all;
    }

    private static Collection<Object[]> makeRWMaps(boolean nulls) {
        return Arrays.asList(
            new Object[]{"HashMap", makeMap(HashMap::new, nulls)},
            new Object[]{"IdentityHashMap", makeMap(IdentityHashMap::new, nulls)},
            new Object[]{"LinkedHashMap", makeMap(LinkedHashMap::new, nulls)},
            new Object[]{"WeakHashMap", makeMap(WeakHashMap::new, nulls)},
            new Object[]{"Collections.checkedMap(HashMap)", Collections.checkedMap(makeMap(HashMap::new, nulls), IntegerEnum.class, String.class)},
            new Object[]{"Collections.synchronizedMap(HashMap)", Collections.synchronizedMap(makeMap(HashMap::new, nulls))},
            new Object[]{"ExtendsAbstractMap", makeMap(ExtendsAbstractMap::new, nulls)});
    }

    private static Collection<Object[]> makeRWNoNullsMaps() {
        return Arrays.asList(
            // null hostile
            new Object[]{"EnumMap", makeMap(() -> new EnumMap(IntegerEnum.class), false)},
            new Object[]{"Hashtable", makeMap(Hashtable::new, false)},
            new Object[]{"TreeMap", makeMap(TreeMap::new, false)},
            new Object[]{"ConcurrentHashMap", makeMap(ConcurrentHashMap::new, false)},
            new Object[]{"ConcurrentSkipListMap", makeMap(ConcurrentSkipListMap::new, false)},
            new Object[]{"Collections.checkedMap(ConcurrentHashMap)", Collections.checkedMap(makeMap(ConcurrentHashMap::new, false), IntegerEnum.class, String.class)},
            new Object[]{"Collections.synchronizedMap(EnumMap)", Collections.synchronizedMap(makeMap(() -> new EnumMap(IntegerEnum.class), false))},
            new Object[]{"ImplementsConcurrentMap", makeMap(ImplementsConcurrentMap::new, false)});
    }

    private static Collection<Object[]> makeROMaps(boolean nulls) {
        return Arrays.asList(new Object[][]{
            new Object[]{"Collections.unmodifiableMap(HashMap)", Collections.unmodifiableMap(makeMap(HashMap::new, nulls))}
        });
    }

    private static Map<IntegerEnum, String> makeMap(Supplier<Map<IntegerEnum, String>> supplier, boolean nulls) {
        Map<IntegerEnum, String> result = supplier.get();

        for (int each = 0; each < TEST_SIZE; each++) {
            if (nulls) {
                result.put((each == 0) ? null : KEYS[each], null);
            } else {
                result.put(KEYS[each], VALUES[each]);
            }
        }

        return result;
    }

    public interface Thrower<T extends Throwable> {

        public void run() throws T;
    }

    public static <T extends Throwable> void assertThrows(Thrower<T> thrower, Class<T> throwable) {
        assertThrows(thrower, throwable, null);
    }

    public static <T extends Throwable> void assertThrows(Thrower<T> thrower, Class<T> throwable, String message) {
        Throwable result;
        try {
            thrower.run();
            result = null;
        } catch (Throwable caught) {
            result = caught;
        }

        assertInstance(result, throwable,
            (null != message)
            ? message
            : "Failed to throw " + throwable.getCanonicalName());
    }

    public static <T> void assertInstance(T actual, Class<? extends T> expected) {
        assertInstance(expected.isInstance(actual), null);
    }

    public static <T> void assertInstance(T actual, Class<? extends T> expected, String message) {
        assertTrue(expected.isInstance(actual), message);
    }

    /**
     * A simple mutable map implementation that provides only default
     * implementations of all methods. ie. none of the Map interface default
     * methods have overridden implementations.
     *
     * @param <K> Type of keys
     * @param <V> Type of values
     */
    public static class ExtendsAbstractMap<M extends Map<K,V>, K, V> extends AbstractMap<K, V> {

        protected final M map;

        public ExtendsAbstractMap() { this( (M) new HashMap<K,V>()); }

        protected ExtendsAbstractMap(M map) { this.map = map; }

        public Set<Map.Entry<K, V>> entrySet() {
            return new AbstractSet<Map.Entry<K, V>>() {
                public int size() {
                    return map.size();
                }

                public Iterator<Map.Entry<K,V>> iterator() {
                    final Iterator<Map.Entry<K,V>> source = map.entrySet().iterator();
                    return new Iterator<Map.Entry<K,V>>() {
                       public boolean hasNext() { return source.hasNext(); }
                       public Map.Entry<K,V> next() { return source.next(); }
                       public void remove() { source.remove(); }
                    };
                }

                public boolean add(Map.Entry<K,V> e) {
                    return map.entrySet().add(e);
                }
            };
        }

        public V put(K key, V value) {
            return map.put(key, value);
        }
    }

    /**
     * A simple mutable concurrent map implementation that provides only default
     * implementations of all methods. ie. none of the ConcurrentMap interface
     * default methods have overridden implementations.
     *
     * @param <K> Type of keys
     * @param <V> Type of values
     */
    public static class ImplementsConcurrentMap<K, V> extends ExtendsAbstractMap<ConcurrentMap<K,V>, K, V> implements ConcurrentMap<K,V> {
        public ImplementsConcurrentMap() { super(new ConcurrentHashMap<K,V>()); }

        // ConcurrentMap reabstracts these methods

        public V replace(K k, V v) { return map.replace(k, v); };

        public boolean replace(K k, V v, V vv) { return map.replace(k, v, vv); };

        public boolean remove(Object k, Object v) { return map.remove(k, v); }

        public V putIfAbsent(K k, V v) { return map.putIfAbsent(k, v); }
    }
}
