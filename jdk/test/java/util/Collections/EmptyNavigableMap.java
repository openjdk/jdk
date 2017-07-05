/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4533691 7129185
 * @summary Unit test for Collections.emptyNavigableMap
 * @run testng EmptyNavigableMap
 */
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.NavigableMap;
import java.util.SortedMap;
import java.util.TreeMap;
import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;

import static org.testng.Assert.fail;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;

public class EmptyNavigableMap {

    public static <T> void assertInstance(T actual, Class<? extends T> expected) {
        assertInstance(expected.isInstance(actual), null);
    }

    public static <T> void assertInstance(T actual, Class<? extends T> expected, String message) {
        assertTrue(expected.isInstance(actual), ((null != message) ? message : "")
            + " " + (actual == null ? "<null>" : actual.getClass().getSimpleName()) + " != " + expected.getSimpleName() + ". ");
    }

    public static <T extends Throwable> void assertEmptyNavigableMap(Object obj) {
        assertInstance(obj, NavigableMap.class);
        assertTrue(((NavigableMap)obj).isEmpty() && (((NavigableMap)obj).size() == 0));
    }

    public static <T extends Throwable> void assertEmptyNavigableMap(Object obj, String message) {
        assertInstance(obj, NavigableMap.class, message);
        assertTrue(((NavigableMap)obj).isEmpty() && (((NavigableMap)obj).size() == 0),
            ((null != message) ? message : "") + " Not empty. ");
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
            fail(((null != message) ? message : "") + "Failed to throw " + throwable.getCanonicalName() + ". ");
            return;
        } catch (Throwable caught) {
            result = caught;
        }

        assertInstance(result, throwable, ((null != message) ? message : "") + "Failed to throw " + throwable.getCanonicalName() + ". ");
    }

    public static final boolean isDescending(SortedMap<?,?> set) {
        if (null == set.comparator()) {
            // natural order
            return false;
        }

        if (Collections.reverseOrder() == set.comparator()) {
            // reverse natural order.
            return true;
        }

        if (set.comparator().equals(Collections.reverseOrder(Collections.reverseOrder(set.comparator())))) {
            // it's a Collections.reverseOrder(Comparator).
            return true;
        }

        throw new IllegalStateException("can't determine ordering for " + set);
    }

    /**
     * Tests that the comparator is {@code null}.
     */
    @Test(dataProvider = "NavigableMap<?,?>", dataProviderClass = EmptyNavigableMap.class)
    public void testComparatorIsNull(String description, NavigableMap<?,?> navigableMap) {
        Comparator comparator = navigableMap.comparator();

        assertTrue(comparator == null || comparator == Collections.reverseOrder(), description + ": Comparator (" + comparator + ") is not null.");
    }

    /**
     * Tests that contains requires Comparable
     */
    @Test(dataProvider = "NavigableMap<?,?>", dataProviderClass = EmptyNavigableMap.class)
    public void testContainsRequiresComparable(String description, NavigableMap<?,?> navigableMap) {
        assertThrows(() -> {
            navigableMap.containsKey(new Object());
        },
            ClassCastException.class,
            description + ": Compareable should be required");
    }

    /**
     * Tests that the contains method returns {@code false}.
     */
    @Test(dataProvider = "NavigableMap<?,?>", dataProviderClass = EmptyNavigableMap.class)
    public void testContains(String description, NavigableMap<?,?> navigableMap) {
        assertFalse(navigableMap.containsKey(new Integer(1)),
            description + ": Should not contain any elements.");
        assertFalse(navigableMap.containsValue(new Integer(1)),
            description + ": Should not contain any elements.");
    }

    /**
     * Tests that the containsAll method returns {@code false}.
     */
    @Test(dataProvider = "NavigableMap<?,?>", dataProviderClass = EmptyNavigableMap.class)
    public void testContainsAll(String description, NavigableMap<?,?> navigableMap) {
        TreeMap treeMap = new TreeMap();
        treeMap.put("1", 1);
        treeMap.put("2", 2);
        treeMap.put("3", 3);

        assertFalse(navigableMap.equals(treeMap), "Should not contain any elements.");
    }

    /**
     * Tests that the iterator is empty.
     */
    @Test(dataProvider = "NavigableMap<?,?>", dataProviderClass = EmptyNavigableMap.class)
    public void testEmptyIterator(String description, NavigableMap<?,?> navigableMap) {
        assertFalse(navigableMap.keySet().iterator().hasNext(), "The iterator is not empty.");
        assertFalse(navigableMap.values().iterator().hasNext(), "The iterator is not empty.");
        assertFalse(navigableMap.entrySet().iterator().hasNext(), "The iterator is not empty.");
    }

    /**
     * Tests that the set is empty.
     */
    @Test(dataProvider = "NavigableMap<?,?>", dataProviderClass = EmptyNavigableMap.class)
    public void testIsEmpty(String description, NavigableMap<?,?> navigableMap) {
        assertTrue(navigableMap.isEmpty(), "The set is not empty.");
    }

    /**
     * Tests the headMap() method.
     */
    @Test(dataProvider = "NavigableMap<?,?>", dataProviderClass = EmptyNavigableMap.class)
    public void testHeadMap(String description, NavigableMap navigableMap) {
        assertThrows(
            () -> { NavigableMap ss = navigableMap.headMap(null, false); },
            NullPointerException.class,
            description + ": Must throw NullPointerException for null element");

        assertThrows(
            () -> { NavigableMap ss = navigableMap.headMap(new Object(), true); },
            ClassCastException.class,
            description + ": Must throw ClassCastException for non-Comparable element");

        NavigableMap ss = navigableMap.headMap("1", false);

        assertEmptyNavigableMap(ss, description + ": Returned value is not empty navigable set.");
    }

    /**
     * Tests that the size is 0.
     */
    @Test(dataProvider = "NavigableMap<?,?>", dataProviderClass = EmptyNavigableMap.class)
    public void testSizeIsZero(String description, NavigableMap<?,?> navigableMap) {
        assertTrue(0 == navigableMap.size(), "The size of the set is not 0.");
    }

    /**
     * Tests the subMap() method.
     */
    @Test(dataProvider = "NavigableMap<?,?>", dataProviderClass = EmptyNavigableMap.class)
    public void testSubMap(String description, NavigableMap navigableMap) {
        assertThrows(
            () -> {
                SortedMap ss = navigableMap.subMap(null, BigInteger.TEN);
            },
            NullPointerException.class,
            description + ": Must throw NullPointerException for null element");

        assertThrows(
            () -> {
                SortedMap ss = navigableMap.subMap(BigInteger.ZERO, null);
            },
            NullPointerException.class,
            description + ": Must throw NullPointerException for null element");

        assertThrows(
            () -> {
                SortedMap ss = navigableMap.subMap(null, null);
            },
            NullPointerException.class,
            description + ": Must throw NullPointerException for null element");

        Object obj1 = new Object();
        Object obj2 = new Object();

        assertThrows(
            () -> {
                SortedMap ss = navigableMap.subMap(obj1, BigInteger.TEN);
            },
            ClassCastException.class, description
            + ": Must throw ClassCastException for parameter which is not Comparable.");

        assertThrows(
            () -> {
                SortedMap ss = navigableMap.subMap(BigInteger.ZERO, obj2);
            },
            ClassCastException.class, description
            + ": Must throw ClassCastException for parameter which is not Comparable.");

        assertThrows(
            () -> {
                SortedMap ss = navigableMap.subMap(obj1, obj2);
            },
            ClassCastException.class, description
            + ": Must throw ClassCastException for parameter which is not Comparable.");

        // minimal range
        navigableMap.subMap(BigInteger.ZERO, false, BigInteger.ZERO, false);
        navigableMap.subMap(BigInteger.ZERO, false, BigInteger.ZERO, true);
        navigableMap.subMap(BigInteger.ZERO, true, BigInteger.ZERO, false);
        navigableMap.subMap(BigInteger.ZERO, true, BigInteger.ZERO, true);

        Object first = isDescending(navigableMap) ? BigInteger.TEN : BigInteger.ZERO;
        Object last = (BigInteger.ZERO == first) ? BigInteger.TEN : BigInteger.ZERO;

            assertThrows(
                () -> {
                    navigableMap.subMap(last, true, first, false);
                },
                IllegalArgumentException.class, description
                + ": Must throw IllegalArgumentException when fromElement is not less then then toElement.");

        navigableMap.subMap(first, true, last, false);
    }

    @Test(dataProvider = "NavigableMap<?,?>", dataProviderClass = EmptyNavigableMap.class)
    public void testSubMapRanges(String description, NavigableMap navigableMap) {
        Object first = isDescending(navigableMap) ? BigInteger.TEN : BigInteger.ZERO;
        Object last = (BigInteger.ZERO == first) ? BigInteger.TEN : BigInteger.ZERO;

        NavigableMap subMap = navigableMap.subMap(first, true, last, true);

        // same subset
        subMap.subMap(first, true, last, true);

        // slightly smaller
        NavigableMap ns = subMap.subMap(first, false, last, false);
        // slight expansion
        assertThrows(() -> {
            ns.subMap(first, true, last, true);
        },
            IllegalArgumentException.class,
            description + ": Expansion should not be allowed");

        // much smaller
        subMap.subMap(first, false, BigInteger.ONE, false);
    }

    @Test(dataProvider = "NavigableMap<?,?>", dataProviderClass = EmptyNavigableMap.class)
    public void testheadMapRanges(String description, NavigableMap navigableMap) {
        NavigableMap subMap = navigableMap.headMap(BigInteger.ONE, true);

        // same subset
        subMap.headMap(BigInteger.ONE, true);

        // slightly smaller
        NavigableMap ns = subMap.headMap(BigInteger.ONE, false);

        // slight expansion
        assertThrows(() -> {
            ns.headMap(BigInteger.ONE, true);
        },
            IllegalArgumentException.class,
            description + ": Expansion should not be allowed");

        // much smaller
        subMap.headMap(isDescending(subMap) ? BigInteger.TEN : BigInteger.ZERO, true);
    }

    @Test(dataProvider = "NavigableMap<?,?>", dataProviderClass = EmptyNavigableMap.class)
    public void testTailMapRanges(String description, NavigableMap navigableMap) {
        NavigableMap subMap = navigableMap.tailMap(BigInteger.ONE, true);

        // same subset
        subMap.tailMap(BigInteger.ONE, true);

        // slightly smaller
        NavigableMap ns = subMap.tailMap(BigInteger.ONE, false);

        // slight expansion
        assertThrows(() -> {
            ns.tailMap(BigInteger.ONE, true);
        },
            IllegalArgumentException.class,
            description + ": Expansion should not be allowed");

        // much smaller
        subMap.tailMap(isDescending(subMap) ? BigInteger.ZERO : BigInteger.TEN, false);
    }

    /**
     * Tests the tailMap() method.
     */
    @Test(dataProvider = "NavigableMap<?,?>", dataProviderClass = EmptyNavigableMap.class)
    public void testTailMap(String description, NavigableMap navigableMap) {
        assertThrows(() -> {
            navigableMap.tailMap(null);
        },
            NullPointerException.class,
            description + ": Must throw NullPointerException for null element");

        assertThrows(() -> {
            navigableMap.tailMap(new Object());
        }, ClassCastException.class);

        NavigableMap ss = navigableMap.tailMap("1", true);

        assertEmptyNavigableMap(ss, description + ": Returned value is not empty navigable set.");
    }

    @DataProvider(name = "NavigableMap<?,?>", parallel = true)
    public static Iterator<Object[]> navigableMapsProvider() {
        return makeNavigableMaps().iterator();
    }

    public static Collection<Object[]> makeNavigableMaps() {
        return Arrays.asList(
            new Object[]{"UnmodifiableNavigableMap(TreeMap)", Collections.unmodifiableNavigableMap(new TreeMap())},
            new Object[]{"UnmodifiableNavigableMap(TreeMap.descendingMap()", Collections.unmodifiableNavigableMap(new TreeMap().descendingMap())},
            new Object[]{"UnmodifiableNavigableMap(TreeMap.descendingMap().descendingMap()", Collections.unmodifiableNavigableMap(new TreeMap().descendingMap().descendingMap())},
            new Object[]{"emptyNavigableMap()", Collections.emptyNavigableMap()},
            new Object[]{"emptyNavigableMap().descendingMap()", Collections.emptyNavigableMap().descendingMap()},
            new Object[]{"emptyNavigableMap().descendingMap().descendingMap()", Collections.emptyNavigableMap().descendingMap().descendingMap()}
        );
    }
}
