/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * @test
 * @library testlibrary
 * @build CollectionAsserts CollectionSupplier
 * @run testng CollectionDefaults
 * @summary Unit tests for extension methods on Collection
 */
public class CollectionDefaults {

    public static final Predicate<Integer> pEven = x -> 0 == x % 2;
    public static final Predicate<Integer> pOdd = x -> 1 == x % 2;

    private static final String[] SET_CLASSES = {
        "java.util.HashSet",
        "java.util.LinkedHashSet",
        "java.util.TreeSet"
    };

    private static final int SIZE = 100;

    @DataProvider(name="setProvider", parallel=true)
    public static Object[][] setCases() {
        final List<Object[]> cases = new LinkedList<>();
        cases.add(new Object[] { new HashSet<>() });
        cases.add(new Object[] { new LinkedHashSet<>() });
        cases.add(new Object[] { new TreeSet<>() });

        cases.add(new Object[] { Collections.newSetFromMap(new HashMap<>()) });
        cases.add(new Object[] { Collections.newSetFromMap(new LinkedHashMap()) });
        cases.add(new Object[] { Collections.newSetFromMap(new TreeMap<>()) });

        cases.add(new Object[] { new HashSet(){{add(42);}} });
        cases.add(new Object[] { new LinkedHashSet(){{add(42);}} });
        cases.add(new Object[] { new TreeSet(){{add(42);}} });
        return cases.toArray(new Object[0][cases.size()]);
    }

    @Test(dataProvider = "setProvider")
    public void testProvidedWithNull(final Set<Integer> set) throws Exception {
        try {
            set.forEach(null);
            fail("expected NPE not thrown");
        } catch (NullPointerException npe) {}
        try {
            set.removeIf(null);
            fail("expected NPE not thrown");
        } catch (NullPointerException npe) {}
    }

    @Test
    public void testForEach() throws Exception {
        final CollectionSupplier supplier = new CollectionSupplier(SET_CLASSES, SIZE);
        for (final CollectionSupplier.TestCase test : supplier.get()) {
            final Set<Integer> original = ((Set<Integer>) test.original);
            final Set<Integer> set = ((Set<Integer>) test.collection);

            try {
                set.forEach(null);
                fail("expected NPE not thrown");
            } catch (NullPointerException npe) {}
            if (test.className.equals("java.util.HashSet")) {
                CollectionAsserts.assertContentsUnordered(set, original);
            } else {
                CollectionAsserts.assertContents(set, original);
            }

            final List<Integer> actual = new LinkedList<>();
            set.forEach(actual::add);
            if (test.className.equals("java.util.HashSet")) {
                CollectionAsserts.assertContentsUnordered(actual, set);
                CollectionAsserts.assertContentsUnordered(actual, original);
            } else {
                CollectionAsserts.assertContents(actual, set);
                CollectionAsserts.assertContents(actual, original);
            }
        }
    }

    @Test
    public void testRemoveIf() throws Exception {
        final CollectionSupplier supplier = new CollectionSupplier(SET_CLASSES, SIZE);
        for (final CollectionSupplier.TestCase test : supplier.get()) {
            final Set<Integer> original = ((Set<Integer>) test.original);
            final Set<Integer> set = ((Set<Integer>) test.collection);

            try {
                set.removeIf(null);
                fail("expected NPE not thrown");
            } catch (NullPointerException npe) {}
            if (test.className.equals("java.util.HashSet")) {
                CollectionAsserts.assertContentsUnordered(set, original);
            } else {
                CollectionAsserts.assertContents(set, original);
            }

            set.removeIf(pEven);
            for (int i : set) {
                assertTrue((i % 2) == 1);
            }
            for (int i : original) {
                if (i % 2 == 1) {
                    assertTrue(set.contains(i));
                }
            }
            set.removeIf(pOdd);
            assertTrue(set.isEmpty());
        }
    }
}
