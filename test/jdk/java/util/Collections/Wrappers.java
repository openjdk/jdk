/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @run testng Wrappers
 * @summary Ensure Collections wrapping classes provide non-default implementations
 */

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Queue;
import java.util.SequencedCollection;
import java.util.SequencedSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;

import static org.testng.Assert.assertTrue;

@Test(groups = "unit")
public class Wrappers {
    static Object[][] collections;
    static int total = 0;

    @AfterClass
    public void printTotal() {
        System.out.println(">>>> Total inherited default methods = " + total);
    }

    @DataProvider(name="collections")
    public static Object[][] collectionCases() {
        if (collections != null) {
            return collections;
        }

        List<Object[]> cases = new ArrayList<>();
        LinkedList<Integer> seedList = new LinkedList<>();
        ArrayList<Integer> seedRandomAccess = new ArrayList<>();
        TreeSet<Integer> seedSet = new TreeSet<>();
        TreeMap<Integer, Integer> seedMap = new TreeMap<>();

        for (int i = 1; i <= 10; i++) {
            seedList.add(i);
            seedRandomAccess.add(i);
            seedSet.add(i);
            seedMap.put(i, i);
        }

        cases.add(new Object[] { Collections.unmodifiableCollection(seedList) });
        cases.add(new Object[] { Collections.unmodifiableSequencedCollection(seedList) });
        cases.add(new Object[] { Collections.unmodifiableList(seedList) });
        cases.add(new Object[] { Collections.unmodifiableList(seedRandomAccess) });
        cases.add(new Object[] { Collections.unmodifiableSet(seedSet) });
        cases.add(new Object[] { Collections.unmodifiableSequencedSet(seedSet) });
        cases.add(new Object[] { Collections.unmodifiableSortedSet(seedSet) });
        cases.add(new Object[] { Collections.unmodifiableNavigableSet(seedSet) });

        // As sets from map also need to be unmodifiable, thus a wrapping
        // layer exist and should not have default methods
        cases.add(new Object[] { Collections.unmodifiableMap(seedMap).entrySet() });
        cases.add(new Object[] { Collections.unmodifiableMap(seedMap).keySet() });
        cases.add(new Object[] { Collections.unmodifiableMap(seedMap).values() });
        cases.add(new Object[] { Collections.unmodifiableSequencedMap(seedMap).entrySet() });
        cases.add(new Object[] { Collections.unmodifiableSequencedMap(seedMap).keySet() });
        cases.add(new Object[] { Collections.unmodifiableSequencedMap(seedMap).values() });
        cases.add(new Object[] { Collections.unmodifiableSequencedMap(seedMap).reversed().entrySet() });
        cases.add(new Object[] { Collections.unmodifiableSequencedMap(seedMap).reversed().keySet() });
        cases.add(new Object[] { Collections.unmodifiableSequencedMap(seedMap).reversed().values() });
        cases.add(new Object[] { Collections.unmodifiableSequencedMap(seedMap).sequencedEntrySet() });
        cases.add(new Object[] { Collections.unmodifiableSequencedMap(seedMap).sequencedKeySet() });
        cases.add(new Object[] { Collections.unmodifiableSequencedMap(seedMap).sequencedValues() });
        cases.add(new Object[] { Collections.unmodifiableSequencedMap(seedMap).sequencedEntrySet().reversed() });
        cases.add(new Object[] { Collections.unmodifiableSequencedMap(seedMap).sequencedKeySet().reversed() });
        cases.add(new Object[] { Collections.unmodifiableSequencedMap(seedMap).sequencedValues().reversed() });
        cases.add(new Object[] { Collections.unmodifiableSequencedMap(seedMap).reversed().sequencedEntrySet() });
        cases.add(new Object[] { Collections.unmodifiableSequencedMap(seedMap).reversed().sequencedKeySet() });
        cases.add(new Object[] { Collections.unmodifiableSequencedMap(seedMap).reversed().sequencedValues() });
        cases.add(new Object[] { Collections.unmodifiableSequencedMap(seedMap).reversed().sequencedEntrySet().reversed() });
        cases.add(new Object[] { Collections.unmodifiableSequencedMap(seedMap).reversed().sequencedKeySet().reversed() });
        cases.add(new Object[] { Collections.unmodifiableSequencedMap(seedMap).reversed().sequencedValues().reversed() });
        cases.add(new Object[] { Collections.unmodifiableSortedMap(seedMap).entrySet() });
        cases.add(new Object[] { Collections.unmodifiableSortedMap(seedMap).keySet() });
        cases.add(new Object[] { Collections.unmodifiableSortedMap(seedMap).values() });
        cases.add(new Object[] { Collections.unmodifiableNavigableMap(seedMap).entrySet() });
        cases.add(new Object[] { Collections.unmodifiableNavigableMap(seedMap).keySet() });
        cases.add(new Object[] { Collections.unmodifiableNavigableMap(seedMap).values() });

        // Synchronized
        cases.add(new Object[] { Collections.synchronizedCollection(seedList) });
        cases.add(new Object[] { Collections.synchronizedList(seedList) });
        cases.add(new Object[] { Collections.synchronizedList(seedRandomAccess) });
        cases.add(new Object[] { Collections.synchronizedSet(seedSet) });
        cases.add(new Object[] { Collections.synchronizedSortedSet(seedSet) });
        cases.add(new Object[] { Collections.synchronizedNavigableSet(seedSet) });

        // As sets from map also need to be synchronized on the map, thus a
        // wrapping layer exist and should not have default methods
        cases.add(new Object[] { Collections.synchronizedMap(seedMap).entrySet() });
        cases.add(new Object[] { Collections.synchronizedMap(seedMap).keySet() });
        cases.add(new Object[] { Collections.synchronizedMap(seedMap).values() });
        cases.add(new Object[] { Collections.synchronizedSortedMap(seedMap).entrySet() });
        cases.add(new Object[] { Collections.synchronizedSortedMap(seedMap).keySet() });
        cases.add(new Object[] { Collections.synchronizedSortedMap(seedMap).values() });
        cases.add(new Object[] { Collections.synchronizedNavigableMap(seedMap).entrySet() });
        cases.add(new Object[] { Collections.synchronizedNavigableMap(seedMap).keySet() });
        cases.add(new Object[] { Collections.synchronizedNavigableMap(seedMap).values() });

        // Checked
        cases.add(new Object[] { Collections.checkedCollection(seedList, Integer.class) });
        cases.add(new Object[] { Collections.checkedList(seedList, Integer.class) });
        cases.add(new Object[] { Collections.checkedList(seedRandomAccess, Integer.class) });
        cases.add(new Object[] { Collections.checkedSet(seedSet, Integer.class) });
        cases.add(new Object[] { Collections.checkedSortedSet(seedSet, Integer.class) });
        cases.add(new Object[] { Collections.checkedNavigableSet(seedSet, Integer.class) });
        cases.add(new Object[] { Collections.checkedQueue(seedList, Integer.class) });

        // asLifoQueue is another wrapper
        cases.add(new Object[] { Collections.asLifoQueue(seedList) });

        collections = cases.toArray(new Object[0][]);
        return collections;
    }

    static Method[] defaultMethods;

    static final List<Class<?>> interfaces = List.of(
        Iterable.class,
        Collection.class,
        SequencedCollection.class,
        List.class,
        Set.class,
        SequencedSet.class,
        SortedSet.class,
        NavigableSet.class,
        Queue.class,
        Deque.class);

    static final Map<Class<?>, List<Method>> defaultMethodMap = new HashMap<>();

    static {
        for (var intf : interfaces) {
            List<Method> list = new ArrayList<>();
            Method[] methods = intf.getMethods();
            for (Method m: methods) {
                if (m.isDefault()) {
                    list.add(m);
                }
            }
            defaultMethodMap.put(intf, list);
        }
    }

//    @Test(dataProvider = "collections")
//    public static void testAllDefaultMethodsOverridden(Collection c) throws NoSuchMethodException {
//        Class cls = c.getClass();
//        var notOverridden = new ArrayList<Method>();
//        for (var entry : defaultMethodMap.entrySet()) {
//            if (entry.getKey().isInstance(c)) {
//                for (Method m : entry.getValue()) {
//                    Method m2 = cls.getMethod(m.getName(), m.getParameterTypes());
//                    if (m2.isDefault()) {
//                        notOverridden.add(m);
//                    }
//                }
//            }
//        }
//        total += notOverridden.size();
//        assertTrue(notOverridden.isEmpty(), cls.getName() + " does not override " + notOverridden);
//    }

    @Test(dataProvider = "collections")
    public static void testNoDefaultMethodsInherited(Collection c) {
        List<Method> inherited = Arrays.stream(c.getClass().getMethods())
                                       .filter(Method::isDefault)
                                       .toList();
        total += inherited.size();
        assertTrue(inherited.isEmpty(),
                   c.getClass().getName() + " inherited default method " + inherited);
    }
}

