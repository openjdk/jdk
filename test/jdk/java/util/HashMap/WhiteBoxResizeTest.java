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

    static class KeyStructure {

        int value;

        public KeyStructure(int value) {
            this.value = value;
        }

        @Override
        public int hashCode() {
            return this.value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            KeyStructure that = (KeyStructure) o;
            return value == that.value;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }

    }

    private static void putMap(Map<KeyStructure, KeyStructure> map, int i) {
        KeyStructure keyStructure = new KeyStructure(i);
        map.put(keyStructure, keyStructure);
    }

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
    Map<KeyStructure, KeyStructure> makeMap(int size) {
        Map<KeyStructure, KeyStructure> map = new HashMap<>();
        for (int i = 0; i < size; ++i) {
            putMap(map, i);
        }
        return map;
    }

    // creates a "fake" map: size() returns the given size, but
    // the entrySet iterator returns only one entry
    Map<KeyStructure, KeyStructure> fakeMap(int size) {
        return new AbstractMap<>() {
            public Set<Map.Entry<KeyStructure, KeyStructure>> entrySet() {
                return new AbstractSet<Map.Entry<KeyStructure, KeyStructure>>() {
                    public int size() {
                        return size;
                    }

                    public Iterator<Map.Entry<KeyStructure, KeyStructure>> iterator() {
                        KeyStructure keyStructure = new KeyStructure(1);
                        return Set.of(Map.entry(keyStructure, keyStructure)).iterator();
                    }
                };
            }
        };
    }

    void putN(Map<KeyStructure, KeyStructure> map, int n) {
        for (int i = 0; i < n; i++) {
            putMap(map, i);
        }
    }

    /*
     * tests of tableSizeFor
     */

    @DataProvider(name = "tableSizeFor")
    public Object[][] tableSizeForCases() {
        final int MAX = 1 << 30;
        return new Object[][]{
                // tableSizeFor(arg), expected
                {1, 1},
                {2, 2},
                {3, 4},
                {4, 4},
                {5, 8},
                {6, 8},
                {7, 8},
                {8, 8},
                {9, 16},
                {10, 16},
                {11, 16},
                {12, 16},
                {13, 16},
                {14, 16},
                {15, 16},
                {16, 16},
                {17, 32},
                {18, 32},
                {19, 32},
                {20, 32},
                {21, 32},
                {22, 32},
                {23, 32},
                {24, 32},
                {25, 32},
                {26, 32},
                {27, 32},
                {28, 32},
                {29, 32},
                {30, 32},
                {31, 32},
                {32, 32},
                {33, 64},
                {34, 64},
                {35, 64},
                {36, 64},
                {37, 64},
                {38, 64},
                {39, 64},
                {40, 64},
                {41, 64},
                {42, 64},
                {43, 64},
                {44, 64},
                {45, 64},
                {46, 64},
                {47, 64},
                {48, 64},
                {49, 64},
                {50, 64},
                {51, 64},
                {52, 64},
                {53, 64},
                {54, 64},
                {55, 64},
                {56, 64},
                {57, 64},
                {58, 64},
                {59, 64},
                {60, 64},
                {61, 64},
                {62, 64},
                {63, 64},
                {64, 64},
                {65, 128},
                {66, 128},
                {67, 128},
                {68, 128},
                {69, 128},
                {70, 128},
                {71, 128},
                {72, 128},
                {73, 128},
                {74, 128},
                {75, 128},
                {76, 128},
                {77, 128},
                {78, 128},
                {79, 128},
                {80, 128},
                {81, 128},
                {82, 128},
                {83, 128},
                {84, 128},
                {85, 128},
                {86, 128},
                {87, 128},
                {88, 128},
                {89, 128},
                {90, 128},
                {91, 128},
                {92, 128},
                {93, 128},
                {94, 128},
                {95, 128},
                {96, 128},
                {97, 128},
                {98, 128},
                {99, 128},
                {100, 128},
                {101, 128},
                {102, 128},
                {103, 128},
                {104, 128},
                {105, 128},
                {106, 128},
                {107, 128},
                {108, 128},
                {109, 128},
                {110, 128},
                {111, 128},
                {112, 128},
                {113, 128},
                {114, 128},
                {115, 128},
                {116, 128},
                {117, 128},
                {118, 128},
                {119, 128},
                {120, 128},
                {121, 128},
                {122, 128},
                {123, 128},
                {124, 128},
                {125, 128},
                {126, 128},
                {127, 128},
                {MAX - 1, MAX},
                {MAX, MAX},
                {MAX + 1, MAX},
                {Integer.MAX_VALUE, MAX}
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
    public void defaultCapacity(Supplier<Map<KeyStructure, KeyStructure>> s) {
        Map<KeyStructure, KeyStructure> map = s.get();
        putMap(map, 0);
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
            cases.add(new Object[]{"rhm1", cap, (Supplier<Map<KeyStructure, KeyStructure>>) () -> new HashMap<>(cap)});
            cases.add(new Object[]{"rhm2", cap, (Supplier<Map<KeyStructure, KeyStructure>>) () -> new HashMap<>(cap, 0.75f)});
            cases.add(new Object[]{"rlm1", cap, (Supplier<Map<KeyStructure, KeyStructure>>) () -> new LinkedHashMap<>(cap)});
            cases.add(new Object[]{"rlm2", cap, (Supplier<Map<KeyStructure, KeyStructure>>) () -> new LinkedHashMap<>(cap, 0.75f)});
            cases.add(new Object[]{"rwm1", cap, (Supplier<Map<KeyStructure, KeyStructure>>) () -> new WeakHashMap<>(cap)});
            cases.add(new Object[]{"rwm2", cap, (Supplier<Map<KeyStructure, KeyStructure>>) () -> new WeakHashMap<>(cap, 0.75f)});
        }
        return cases.iterator();
    }

    @Test(dataProvider = "requestedCapacity")
    public void requestedCapacity(String label, int cap, Supplier<Map<KeyStructure, KeyStructure>> s) {
        Map<KeyStructure, KeyStructure> map = s.get();
        putMap(map, 0);
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
                 Supplier<Map<KeyStructure, KeyStructure>> supplier,
                 Consumer<Map<KeyStructure, KeyStructure>> consumer) {
        return new Object[]{label, size, expectedCapacity, supplier, consumer};
    }

    List<Object[]> genPopulatedCapacityCases(int size, int cap) {
        return Arrays.asList(
                pcc("phmcpy", size, cap, () -> new HashMap<>(makeMap(size)), map -> {
                }),
                pcc("phm0pn", size, cap, () -> new HashMap<>(), map -> {
                    putN(map, size);
                }),
                pcc("phm1pn", size, cap, () -> new HashMap<>(cap), map -> {
                    putN(map, size);
                }),
                pcc("phm2pn", size, cap, () -> new HashMap<>(cap, 0.75f), map -> {
                    putN(map, size);
                }),
                pcc("phm0pa", size, cap, () -> new HashMap<>(), map -> {
                    map.putAll(makeMap(size));
                }),
                pcc("phm1pa", size, cap, () -> new HashMap<>(cap), map -> {
                    map.putAll(makeMap(size));
                }),
                pcc("phm2pa", size, cap, () -> new HashMap<>(cap, 0.75f), map -> {
                    map.putAll(makeMap(size));
                }),

                pcc("plmcpy", size, cap, () -> new LinkedHashMap<>(makeMap(size)), map -> {
                }),
                pcc("plm0pn", size, cap, () -> new LinkedHashMap<>(), map -> {
                    putN(map, size);
                }),
                pcc("plm1pn", size, cap, () -> new LinkedHashMap<>(cap), map -> {
                    putN(map, size);
                }),
                pcc("plm2pn", size, cap, () -> new LinkedHashMap<>(cap, 0.75f), map -> {
                    putN(map, size);
                }),
                pcc("plm0pa", size, cap, () -> new LinkedHashMap<>(), map -> {
                    map.putAll(makeMap(size));
                }),
                pcc("plm1pa", size, cap, () -> new LinkedHashMap<>(cap), map -> {
                    map.putAll(makeMap(size));
                }),
                pcc("plm2pa", size, cap, () -> new LinkedHashMap<>(cap, 0.75f), map -> {
                    map.putAll(makeMap(size));
                }),

                pcc("pwmcpy", size, cap, () -> new WeakHashMap<>(makeMap(size)), map -> {
                }),
                pcc("pwm0pn", size, cap, () -> new WeakHashMap<>(), map -> {
                    putN(map, size);
                }),
                pcc("pwm1pn", size, cap, () -> new WeakHashMap<>(cap), map -> {
                    putN(map, size);
                }),
                pcc("pwm2pn", size, cap, () -> new WeakHashMap<>(cap, 0.75f), map -> {
                    putN(map, size);
                }),
                pcc("pwm0pa", size, cap, () -> new WeakHashMap<>(), map -> {
                    map.putAll(makeMap(size));
                }),
                pcc("pwm1pa", size, cap, () -> new WeakHashMap<>(cap), map -> {
                    map.putAll(makeMap(size));
                }),
                pcc("pwm2pa", size, cap, () -> new WeakHashMap<>(cap, 0.75f), map -> {
                    map.putAll(makeMap(size));
                })
        );
    }

    List<Object[]> genFakePopulatedCapacityCases(int size, int cap) {
        return Arrays.asList(
                pcc("fhmcpy", size, cap, () -> new HashMap<>(fakeMap(size)), map -> {
                }),
                pcc("fhm0pa", size, cap, () -> new HashMap<>(), map -> {
                    map.putAll(fakeMap(size));
                }),
                pcc("fhm1pa", size, cap, () -> new HashMap<>(cap), map -> {
                    map.putAll(fakeMap(size));
                }),
                pcc("fhm2pa", size, cap, () -> new HashMap<>(cap, 0.75f), map -> {
                    map.putAll(fakeMap(size));
                }),

                pcc("flmcpy", size, cap, () -> new LinkedHashMap<>(fakeMap(size)), map -> {
                }),
                pcc("flm0pa", size, cap, () -> new LinkedHashMap<>(), map -> {
                    map.putAll(fakeMap(size));
                }),
                pcc("flm1pa", size, cap, () -> new LinkedHashMap<>(cap), map -> {
                    map.putAll(fakeMap(size));
                }),
                pcc("flm2pa", size, cap, () -> new LinkedHashMap<>(cap, 0.75f), map -> {
                    map.putAll(fakeMap(size));
                }),

                pcc("fwmcpy", size, cap, () -> new WeakHashMap<>(fakeMap(size)), map -> {
                }),
                // pcc("fwm0pa", size, cap, () -> new WeakHashMap<>(),                map -> { map.putAll(fakeMap(size)); }), // see note
                pcc("fwm1pa", size, cap, () -> new WeakHashMap<>(cap), map -> {
                    map.putAll(fakeMap(size));
                }),
                pcc("fwm2pa", size, cap, () -> new WeakHashMap<>(cap, 0.75f), map -> {
                    map.putAll(fakeMap(size));
                })
        );

        // Test case "fwm0pa" is commented out because WeakHashMap uses a different allocation
        // policy from the other map implementations: it deliberately under-allocates in this case.
    }

    @DataProvider(name = "populatedCapacity")
    public Iterator<Object[]> populatedCapacityCases() {
        ArrayList<Object[]> cases = new ArrayList<>();
        cases.addAll(genPopulatedCapacityCases(16, 32));
        cases.addAll(genPopulatedCapacityCases(17, 32));
        cases.addAll(genPopulatedCapacityCases(18, 32));
        cases.addAll(genPopulatedCapacityCases(19, 32));
        cases.addAll(genPopulatedCapacityCases(20, 32));
        cases.addAll(genPopulatedCapacityCases(21, 32));
        cases.addAll(genPopulatedCapacityCases(22, 32));
        cases.addAll(genPopulatedCapacityCases(23, 32));
        cases.addAll(genPopulatedCapacityCases(24, 32));
        cases.addAll(genPopulatedCapacityCases(25, 64));
        cases.addAll(genPopulatedCapacityCases(26, 64));
        cases.addAll(genPopulatedCapacityCases(27, 64));
        cases.addAll(genPopulatedCapacityCases(28, 64));
        cases.addAll(genPopulatedCapacityCases(29, 64));
        cases.addAll(genPopulatedCapacityCases(30, 64));
        cases.addAll(genPopulatedCapacityCases(31, 64));
        cases.addAll(genPopulatedCapacityCases(32, 64));
        cases.addAll(genPopulatedCapacityCases(33, 64));
        cases.addAll(genPopulatedCapacityCases(34, 64));
        cases.addAll(genPopulatedCapacityCases(35, 64));
        cases.addAll(genPopulatedCapacityCases(36, 64));
        cases.addAll(genPopulatedCapacityCases(37, 64));
        cases.addAll(genPopulatedCapacityCases(38, 64));
        cases.addAll(genPopulatedCapacityCases(39, 64));
        cases.addAll(genPopulatedCapacityCases(40, 64));
        cases.addAll(genPopulatedCapacityCases(41, 64));
        cases.addAll(genPopulatedCapacityCases(42, 64));
        cases.addAll(genPopulatedCapacityCases(43, 64));
        cases.addAll(genPopulatedCapacityCases(44, 64));
        cases.addAll(genPopulatedCapacityCases(45, 64));
        cases.addAll(genPopulatedCapacityCases(46, 64));
        cases.addAll(genPopulatedCapacityCases(47, 64));
        cases.addAll(genPopulatedCapacityCases(48, 64));
        cases.addAll(genPopulatedCapacityCases(49, 128));
        cases.addAll(genPopulatedCapacityCases(50, 128));
        cases.addAll(genPopulatedCapacityCases(51, 128));
        cases.addAll(genPopulatedCapacityCases(52, 128));
        cases.addAll(genPopulatedCapacityCases(53, 128));
        cases.addAll(genPopulatedCapacityCases(54, 128));
        cases.addAll(genPopulatedCapacityCases(55, 128));
        cases.addAll(genPopulatedCapacityCases(56, 128));
        cases.addAll(genPopulatedCapacityCases(57, 128));
        cases.addAll(genPopulatedCapacityCases(58, 128));
        cases.addAll(genPopulatedCapacityCases(59, 128));
        cases.addAll(genPopulatedCapacityCases(60, 128));
        cases.addAll(genPopulatedCapacityCases(61, 128));
        cases.addAll(genPopulatedCapacityCases(62, 128));
        cases.addAll(genPopulatedCapacityCases(63, 128));
        cases.addAll(genPopulatedCapacityCases(64, 128));
        cases.addAll(genPopulatedCapacityCases(65, 128));
        cases.addAll(genPopulatedCapacityCases(66, 128));
        cases.addAll(genPopulatedCapacityCases(67, 128));
        cases.addAll(genPopulatedCapacityCases(68, 128));
        cases.addAll(genPopulatedCapacityCases(69, 128));
        cases.addAll(genPopulatedCapacityCases(70, 128));
        cases.addAll(genPopulatedCapacityCases(71, 128));
        cases.addAll(genPopulatedCapacityCases(72, 128));
        cases.addAll(genPopulatedCapacityCases(73, 128));
        cases.addAll(genPopulatedCapacityCases(74, 128));
        cases.addAll(genPopulatedCapacityCases(75, 128));
        cases.addAll(genPopulatedCapacityCases(76, 128));
        cases.addAll(genPopulatedCapacityCases(77, 128));
        cases.addAll(genPopulatedCapacityCases(78, 128));
        cases.addAll(genPopulatedCapacityCases(79, 128));
        cases.addAll(genPopulatedCapacityCases(80, 128));
        cases.addAll(genPopulatedCapacityCases(81, 128));
        cases.addAll(genPopulatedCapacityCases(82, 128));
        cases.addAll(genPopulatedCapacityCases(83, 128));
        cases.addAll(genPopulatedCapacityCases(84, 128));
        cases.addAll(genPopulatedCapacityCases(85, 128));
        cases.addAll(genPopulatedCapacityCases(86, 128));
        cases.addAll(genPopulatedCapacityCases(87, 128));
        cases.addAll(genPopulatedCapacityCases(88, 128));
        cases.addAll(genPopulatedCapacityCases(89, 128));
        cases.addAll(genPopulatedCapacityCases(90, 128));
        cases.addAll(genPopulatedCapacityCases(91, 128));
        cases.addAll(genPopulatedCapacityCases(92, 128));
        cases.addAll(genPopulatedCapacityCases(93, 128));
        cases.addAll(genPopulatedCapacityCases(94, 128));
        cases.addAll(genPopulatedCapacityCases(95, 128));
        cases.addAll(genPopulatedCapacityCases(96, 128));
        cases.addAll(genPopulatedCapacityCases(97, 256));
        cases.addAll(genPopulatedCapacityCases(98, 256));
        cases.addAll(genPopulatedCapacityCases(99, 256));
        cases.addAll(genPopulatedCapacityCases(100, 256));
        cases.addAll(genPopulatedCapacityCases(101, 256));
        cases.addAll(genPopulatedCapacityCases(102, 256));
        cases.addAll(genPopulatedCapacityCases(103, 256));
        cases.addAll(genPopulatedCapacityCases(104, 256));
        cases.addAll(genPopulatedCapacityCases(105, 256));
        cases.addAll(genPopulatedCapacityCases(106, 256));
        cases.addAll(genPopulatedCapacityCases(107, 256));
        cases.addAll(genPopulatedCapacityCases(108, 256));
        cases.addAll(genPopulatedCapacityCases(109, 256));
        cases.addAll(genPopulatedCapacityCases(110, 256));
        cases.addAll(genPopulatedCapacityCases(111, 256));
        cases.addAll(genPopulatedCapacityCases(112, 256));
        cases.addAll(genPopulatedCapacityCases(113, 256));
        cases.addAll(genPopulatedCapacityCases(114, 256));
        cases.addAll(genPopulatedCapacityCases(115, 256));
        cases.addAll(genPopulatedCapacityCases(116, 256));
        cases.addAll(genPopulatedCapacityCases(117, 256));
        cases.addAll(genPopulatedCapacityCases(118, 256));
        cases.addAll(genPopulatedCapacityCases(119, 256));
        cases.addAll(genPopulatedCapacityCases(120, 256));
        cases.addAll(genPopulatedCapacityCases(121, 256));
        cases.addAll(genPopulatedCapacityCases(122, 256));
        cases.addAll(genPopulatedCapacityCases(123, 256));
        cases.addAll(genPopulatedCapacityCases(124, 256));
        cases.addAll(genPopulatedCapacityCases(125, 256));
        cases.addAll(genPopulatedCapacityCases(126, 256));
        cases.addAll(genPopulatedCapacityCases(127, 256));

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
                                  Supplier<Map<KeyStructure, KeyStructure>> s,
                                  Consumer<Map<KeyStructure, KeyStructure>> c) {
        Map<KeyStructure, KeyStructure> map = s.get();
        c.accept(map);
        assertEquals(capacity(map), expectedCapacity);
    }
}
