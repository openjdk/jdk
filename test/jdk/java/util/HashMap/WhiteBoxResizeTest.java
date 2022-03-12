/*
 * Copyright (c) 2018, Red Hat, Inc. All rights reserved.
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

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

/*
 * @test
 * @bug 8210280 8281631
 * @modules java.base/java.util:open
 * @summary White box tests for HashMap-related internals around table sizing
 * @run testng WhiteBoxResizeTest
 */
public class WhiteBoxResizeTest {

    final MethodHandle TABLE_SIZE_FOR;
    final VarHandle HM_TABLE;
    final VarHandle WHM_TABLE;

    public WhiteBoxResizeTest() throws ReflectiveOperationException {
        MethodHandles.Lookup hmlookup = MethodHandles.privateLookupIn(HashMap.class, MethodHandles.lookup());
        TABLE_SIZE_FOR = hmlookup.findStatic(
                HashMap.class, "tableSizeFor", MethodType.methodType(int.class, int.class));
        HM_TABLE = hmlookup.unreflectVarHandle(HashMap.class.getDeclaredField("table"));

        MethodHandles.Lookup whmlookup = MethodHandles.privateLookupIn(WeakHashMap.class, MethodHandles.lookup());
        WHM_TABLE = whmlookup.unreflectVarHandle(WeakHashMap.class.getDeclaredField("table"));
    }

    /*
     * utility methods
     */

    int tableSizeFor(int n) {
        try {
            return (int) TABLE_SIZE_FOR.invoke(n);
        } catch (Throwable t) {
            throw new AssertionError(t);
        }
    }

    Object[] table(Map<?, ?> map) {
        try {
            VarHandle vh = map instanceof WeakHashMap ? WHM_TABLE : HM_TABLE;
            return (Object[]) vh.get(map);
        } catch (Throwable t) {
            throw new AssertionError(t);
        }
    }

    int capacity(Map<?, ?> map) {
        return table(map).length;
    }

    // creates a map with size mappings
    Map<String, String> makeMap(int size) {
        Map<String, String> map = new HashMap<>();
        putN(map, size);
        return map;
    }

    // creates a "fake" map: size() returns the given size, but
    // the entrySet iterator returns only one entry
    Map<String, String> fakeMap(int size) {
        return new AbstractMap<>() {
            public Set<Map.Entry<String, String>> entrySet() {
                return new AbstractSet<Map.Entry<String, String>>() {
                    public int size() {
                        return size;
                    }

                    public Iterator<Map.Entry<String, String>> iterator() {
                        return Set.of(Map.entry("1", "1")).iterator();
                    }
                };
            }
        };
    }

    void putN(Map<String, String> map, int n) {
        for (int i = 0; i < n; i++) {
            String string = Integer.toString(i);
            map.put(string, string);
        }
    }

    /*
     * tests of tableSizeFor
     */

    @DataProvider(name = "tableSizeFor")
    public Object[][] tableSizeForCases() {
        final int MAX = 1 << 30;
        return new Object[][] {
                // tableSizeFor(arg), expected
                { 0,                   1 },
                { 1,                   1 },
                { 2,                   2 },
                { 3,                   4 },
                { 4,                   4 },
                { 5,                   8 },
                { 15,                 16 },
                { 16,                 16 },
                { 17,                 32 },
                { MAX-1,             MAX },
                { MAX,               MAX },
                { MAX+1,             MAX },
                { Integer.MAX_VALUE, MAX }
        };
    }

    @Test(dataProvider = "tableSizeFor")
    public void tableSizeFor(int arg, int expected) {
        assertEquals(tableSizeFor(arg), expected);
    }

    /*
     * tests for lazy table allocation
     */

    @DataProvider(name = "lazy")
    public Object[][] lazyTableAllocationCases() {
        return new Object[][]{
                {new HashMap<>()},
                // { new WeakHashMap<>() }, // WHM doesn't allocate lazily
                {new LinkedHashMap<>()}
        };
    }

    @Test(dataProvider = "lazy")
    public void lazyTableAllocation(Map<?, ?> map) {
        assertNull(table(map));
    }

    /*
     * tests for default capacity (no-arg constructor)
     */

    @DataProvider(name = "defaultCapacity")
    public Object[][] defaultCapacityCases() {
        return new Supplier<?>[][]{
                {() -> new HashMap<>()},
                {() -> new LinkedHashMap<>()},
                {() -> new WeakHashMap<>()}
        };
    }

    @Test(dataProvider = "defaultCapacity")
    public void defaultCapacity(Supplier<Map<String, String>> s) {
        Map<String, String> map = s.get();
        map.put("", "");
        assertEquals(capacity(map), 16);
    }

    /*
     * tests for requested capacity (int and int+float constructors)
     */

    @DataProvider(name = "requestedCapacity")
    public Iterator<Object[]> requestedCapacityCases() {
        ArrayList<Object[]> cases = new ArrayList<>();
        for (int i = 2; i < 128; i++) {
            int cap = i;
            cases.add(new Object[]{"rhm1", cap, (Supplier<Map<String, String>>) () -> new HashMap<>(cap)});
            cases.add(new Object[]{"rhm2", cap, (Supplier<Map<String, String>>) () -> new HashMap<>(cap, 0.75f)});
            cases.add(new Object[]{"rlm1", cap, (Supplier<Map<String, String>>) () -> new LinkedHashMap<>(cap)});
            cases.add(new Object[]{"rlm2", cap, (Supplier<Map<String, String>>) () -> new LinkedHashMap<>(cap, 0.75f)});
            cases.add(new Object[]{"rwm1", cap, (Supplier<Map<String, String>>) () -> new WeakHashMap<>(cap)});
            cases.add(new Object[]{"rwm2", cap, (Supplier<Map<String, String>>) () -> new WeakHashMap<>(cap, 0.75f)});
        }
        return cases.iterator();
    }

    @Test(dataProvider = "requestedCapacity")
    public void requestedCapacity(String label, int cap, Supplier<Map<String, String>> s) {
        Map<String, String> map = s.get();
        map.put("", "");
        assertEquals(capacity(map), tableSizeFor(cap));
    }

    /*
     * Tests for capacity after map is populated with a given number N of mappings.
     * Maps are populated using a copy constructor on a map with N mappings,
     * other constructors followed by N put() calls, and other constructors followed
     * by putAll() on a map with N mappings.
     *
     * String labels encode the test case for ease of diagnosis if one of the test cases fails.
     * For example, "plm2pn" is "populated LinkedHashMap, 2-arg constructor, followed by putN".
     */

    // helper method for one populated capacity case, to provide target types for lambdas
    Object[] pcc(String label,
                 int size,
                 int expectedCapacity,
                 Supplier<Map<String, String>> supplier,
                 Consumer<Map<String, String>> consumer) {
        return new Object[]{label, size, expectedCapacity, supplier, consumer};
    }

    List<Object[]> genPopulatedCapacityCases(int size, int cap) {
        return Arrays.asList(
                pcc("phmcpy", size, cap, () -> new HashMap<>(makeMap(size)),       map -> { }),
                pcc("phm0pn", size, cap, () -> new HashMap<>(),                    map -> { putN(map, size); }),
                pcc("phm1pn", size, cap, () -> new HashMap<>(cap),                 map -> { putN(map, size); }),
                pcc("phm2pn", size, cap, () -> new HashMap<>(cap, 0.75f),          map -> { putN(map, size); }),
                pcc("phm0pa", size, cap, () -> new HashMap<>(),                    map -> { map.putAll(makeMap(size)); }),
                pcc("phm1pa", size, cap, () -> new HashMap<>(cap),                 map -> { map.putAll(makeMap(size)); }),
                pcc("phm2pa", size, cap, () -> new HashMap<>(cap, 0.75f),          map -> { map.putAll(makeMap(size)); }),

                pcc("plmcpy", size, cap, () -> new LinkedHashMap<>(makeMap(size)), map -> { }),
                pcc("plm0pn", size, cap, () -> new LinkedHashMap<>(),              map -> { putN(map, size); }),
                pcc("plm1pn", size, cap, () -> new LinkedHashMap<>(cap),           map -> { putN(map, size); }),
                pcc("plm2pn", size, cap, () -> new LinkedHashMap<>(cap, 0.75f),    map -> { putN(map, size); }),
                pcc("plm0pa", size, cap, () -> new LinkedHashMap<>(),              map -> { map.putAll(makeMap(size)); }),
                pcc("plm1pa", size, cap, () -> new LinkedHashMap<>(cap),           map -> { map.putAll(makeMap(size)); }),
                pcc("plm2pa", size, cap, () -> new LinkedHashMap<>(cap, 0.75f),    map -> { map.putAll(makeMap(size)); }),

                pcc("pwmcpy", size, cap, () -> new WeakHashMap<>(makeMap(size)),   map -> { }),
                pcc("pwm0pn", size, cap, () -> new WeakHashMap<>(),                map -> { putN(map, size); }),
                pcc("pwm1pn", size, cap, () -> new WeakHashMap<>(cap),             map -> { putN(map, size); }),
                pcc("pwm2pn", size, cap, () -> new WeakHashMap<>(cap, 0.75f),      map -> { putN(map, size); }),
                pcc("pwm0pa", size, cap, () -> new WeakHashMap<>(),                map -> { map.putAll(makeMap(size)); }),
                pcc("pwm1pa", size, cap, () -> new WeakHashMap<>(cap),             map -> { map.putAll(makeMap(size)); }),
                pcc("pwm2pa", size, cap, () -> new WeakHashMap<>(cap, 0.75f),      map -> { map.putAll(makeMap(size)); })
        );
    }

    List<Object[]> genFakePopulatedCapacityCases(int size, int cap) {
        return Arrays.asList(
                pcc("fhmcpy", size, cap, () -> new HashMap<>(fakeMap(size)),       map -> { }),
                pcc("fhm0pa", size, cap, () -> new HashMap<>(),                    map -> { map.putAll(fakeMap(size)); }),
                pcc("fhm1pa", size, cap, () -> new HashMap<>(cap),                 map -> { map.putAll(fakeMap(size)); }),
                pcc("fhm2pa", size, cap, () -> new HashMap<>(cap, 0.75f),          map -> { map.putAll(fakeMap(size)); }),

                pcc("flmcpy", size, cap, () -> new LinkedHashMap<>(fakeMap(size)), map -> { }),
                pcc("flm0pa", size, cap, () -> new LinkedHashMap<>(),              map -> { map.putAll(fakeMap(size)); }),
                pcc("flm1pa", size, cap, () -> new LinkedHashMap<>(cap),           map -> { map.putAll(fakeMap(size)); }),
                pcc("flm2pa", size, cap, () -> new LinkedHashMap<>(cap, 0.75f),    map -> { map.putAll(fakeMap(size)); }),

                pcc("fwmcpy", size, cap, () -> new WeakHashMap<>(fakeMap(size)),   map -> { }),
                // pcc("fwm0pa", size, cap, () -> new WeakHashMap<>(),                map -> { map.putAll(fakeMap(size)); }), // see note
                pcc("fwm1pa", size, cap, () -> new WeakHashMap<>(cap),             map -> { map.putAll(fakeMap(size)); }),
                pcc("fwm2pa", size, cap, () -> new WeakHashMap<>(cap, 0.75f),      map -> { map.putAll(fakeMap(size)); })
        );

        // Test case "fwm0pa" is commented out because WeakHashMap uses a different allocation
        // policy from the other map implementations: it deliberately under-allocates in this case.
    }

    @DataProvider(name = "populatedCapacity")
    public Iterator<Object[]> populatedCapacityCases() {
        ArrayList<Object[]> cases = new ArrayList<>();
        cases.addAll(genPopulatedCapacityCases(11,  16));
        cases.addAll(genPopulatedCapacityCases(12,  16));
        cases.addAll(genPopulatedCapacityCases(13,  32));
        cases.addAll(genPopulatedCapacityCases(64, 128));

        // numbers in this range are truncated by a float computation with 0.75f
        // but can get an exact result with a double computation with 0.75d
        cases.addAll(genFakePopulatedCapacityCases(25165824, 33554432));
        cases.addAll(genFakePopulatedCapacityCases(25165825, 67108864));
        cases.addAll(genFakePopulatedCapacityCases(25165826, 67108864));

        return cases.iterator();
    }

    @Test(dataProvider = "populatedCapacity")
    public void populatedCapacity(String label, // unused, included for diagnostics
                                  int size,     // unused, included for diagnostics
                                  int expectedCapacity,
                                  Supplier<Map<String, String>> s,
                                  Consumer<Map<String, String>> c) {
        Map<String, String> map = s.get();
        c.accept(map);
        assertEquals(capacity(map), expectedCapacity);
    }

}
