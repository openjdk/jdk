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
import java.util.Locale;
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

        // Map views also need to be unmodifiable, thus a wrapping
        // layer exists and should not inherit default methods
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

        // Synchronized collections
        cases.add(new Object[] { Collections.synchronizedCollection(seedList) });
        cases.add(new Object[] { Collections.synchronizedList(seedList) });
        cases.add(new Object[] { Collections.synchronizedList(seedRandomAccess) });
        cases.add(new Object[] { Collections.synchronizedSet(seedSet) });
        cases.add(new Object[] { Collections.synchronizedSortedSet(seedSet) });
        cases.add(new Object[] { Collections.synchronizedNavigableSet(seedSet) });

        // Map views also need to be synchronized on the map, thus a
        // wrapping layer exists and should not inherit default methods
        cases.add(new Object[] { Collections.synchronizedMap(seedMap).entrySet() });
        cases.add(new Object[] { Collections.synchronizedMap(seedMap).keySet() });
        cases.add(new Object[] { Collections.synchronizedMap(seedMap).values() });
        cases.add(new Object[] { Collections.synchronizedSortedMap(seedMap).entrySet() });
        cases.add(new Object[] { Collections.synchronizedSortedMap(seedMap).keySet() });
        cases.add(new Object[] { Collections.synchronizedSortedMap(seedMap).values() });
        cases.add(new Object[] { Collections.synchronizedNavigableMap(seedMap).entrySet() });
        cases.add(new Object[] { Collections.synchronizedNavigableMap(seedMap).keySet() });
        cases.add(new Object[] { Collections.synchronizedNavigableMap(seedMap).values() });

        // Checked collections
        cases.add(new Object[] { Collections.checkedCollection(seedList, Integer.class) });
        cases.add(new Object[] { Collections.checkedList(seedList, Integer.class) });
        cases.add(new Object[] { Collections.checkedList(seedRandomAccess, Integer.class) });
        cases.add(new Object[] { Collections.checkedSet(seedSet, Integer.class) });
        cases.add(new Object[] { Collections.checkedSortedSet(seedSet, Integer.class) });
        cases.add(new Object[] { Collections.checkedNavigableSet(seedSet, Integer.class) });
        cases.add(new Object[] { Collections.checkedQueue(seedList, Integer.class) });

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

