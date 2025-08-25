/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Ensures that Collections wrapper classes do not inherit default
 *          method implementations.
 */

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Formatter;
import java.util.LinkedList;
import java.util.List;
import java.util.SequencedMap;
import java.util.TreeMap;
import java.util.TreeSet;

import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;

import static org.testng.Assert.assertTrue;

@Test(groups = "unit")
public class Wrappers {
    static int inheritedCount = 0;

    @AfterClass
    public void printCount() {
        System.out.println(">>>> Total inherited default methods = " + inheritedCount);
    }

    static void addSequencedMapViews(List<Object[]> cases, SequencedMap<?, ?> seqMap) {
        for (var map : List.of(seqMap, seqMap.reversed())) {
            cases.add(new Object[] { map.entrySet() });
            cases.add(new Object[] { map.keySet() });
            cases.add(new Object[] { map.values() });
            cases.add(new Object[] { map.sequencedEntrySet() });
            cases.add(new Object[] { map.sequencedKeySet() });
            cases.add(new Object[] { map.sequencedValues() });
            cases.add(new Object[] { map.sequencedEntrySet().reversed() });
            cases.add(new Object[] { map.sequencedKeySet().reversed() });
            cases.add(new Object[] { map.sequencedValues().reversed() });
        }
    }

    @DataProvider(name="collections")
    public static Object[][] collectionCases() {
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

        // Unmodifiable collections

        cases.add(new Object[] { Collections.unmodifiableCollection(seedList) });
        cases.add(new Object[] { Collections.unmodifiableSequencedCollection(seedList) });
        cases.add(new Object[] { Collections.unmodifiableList(seedList) });
        cases.add(new Object[] { Collections.unmodifiableList(seedRandomAccess) });
        cases.add(new Object[] { Collections.unmodifiableSet(seedSet) });
        cases.add(new Object[] { Collections.unmodifiableSequencedSet(seedSet) });
        cases.add(new Object[] { Collections.unmodifiableSortedSet(seedSet) });
        cases.add(new Object[] { Collections.unmodifiableNavigableSet(seedSet) });

        // Views of unmodifiable maps

        cases.add(new Object[] { Collections.unmodifiableMap(seedMap).entrySet() });
        cases.add(new Object[] { Collections.unmodifiableMap(seedMap).keySet() });
        cases.add(new Object[] { Collections.unmodifiableMap(seedMap).values() });

        addSequencedMapViews(cases, Collections.unmodifiableSequencedMap(seedMap));
        addSequencedMapViews(cases, Collections.unmodifiableSortedMap(seedMap));
        addSequencedMapViews(cases, Collections.unmodifiableNavigableMap(seedMap));

        // Synchronized collections

        cases.add(new Object[] { Collections.synchronizedCollection(seedList) });
        cases.add(new Object[] { Collections.synchronizedList(seedList) });
        cases.add(new Object[] { Collections.synchronizedList(seedRandomAccess) });
        cases.add(new Object[] { Collections.synchronizedSet(seedSet) });
        cases.add(new Object[] { Collections.synchronizedSortedSet(seedSet) });
        cases.add(new Object[] { Collections.synchronizedNavigableSet(seedSet) });

        // Views of synchronized maps

        cases.add(new Object[] { Collections.synchronizedMap(seedMap).entrySet() });
        cases.add(new Object[] { Collections.synchronizedMap(seedMap).keySet() });
        cases.add(new Object[] { Collections.synchronizedMap(seedMap).values() });

        addSequencedMapViews(cases, Collections.synchronizedSortedMap(seedMap));
        addSequencedMapViews(cases, Collections.synchronizedNavigableMap(seedMap));

        // Checked collections

        cases.add(new Object[] { Collections.checkedCollection(seedList, Integer.class) });
        cases.add(new Object[] { Collections.checkedList(seedList, Integer.class) });
        cases.add(new Object[] { Collections.checkedList(seedRandomAccess, Integer.class) });
        cases.add(new Object[] { Collections.checkedSet(seedSet, Integer.class) });
        cases.add(new Object[] { Collections.checkedSortedSet(seedSet, Integer.class) });
        cases.add(new Object[] { Collections.checkedNavigableSet(seedSet, Integer.class) });
        cases.add(new Object[] { Collections.checkedQueue(seedList, Integer.class) });

        // Omit views of checked maps for now. In general, checked maps' keySet and values views
        // don't have any operations that need checking and so they're simply the views from
        // the underlying map. The checked maps' entrySet views' only responsibilities are to
        // provide checked map entries. This is done by the iterator() and toArray() methods,
        // and most other things are inherited, including default methods.

        // asLifoQueue is another wrapper
        cases.add(new Object[] { Collections.asLifoQueue(seedList) });

        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "collections")
    public static void testNoDefaultMethodsInherited(Collection<?> c) {
        List<Method> inherited = Arrays.stream(c.getClass().getMethods())
                                       .filter(Method::isDefault)
                                       .toList();
        inheritedCount += inherited.size();
        assertTrue(inherited.isEmpty(), generateReport(c, inherited));
    }

    static String generateReport(Collection<?> c, List<Method> inherited) {
        if (inherited.isEmpty()) {
            return "";
        }

        var f = new Formatter();
        f.format("%s inherits the following:%n", c.getClass().getName());
        for (int i = 0; i < inherited.size(); i++) {
            f.format("  %d. %s%n", i+1, inherited.get(i));
        }
        return f.toString();
    }
}

