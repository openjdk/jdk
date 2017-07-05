/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4533691
 * @summary Unit test for Collections.emptySortedSet
 */

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.SortedSet;
import java.util.TreeSet;

public class EmptySortedSet {
    static int status = 0;
    private static final String FAILED = " failed. ";
    private static final String PERIOD = ".";
    private final String thisClassName = this.getClass().getName();

    public static void main(String[] args) throws Exception {
        new EmptySortedSet();
    }

    public EmptySortedSet() throws Exception {
        run();
    }

    /**
     * Returns {@code true} if the {@link Object} passed in is an empty
     * {@link SortedSet}.
     *
     * @param obj the object to test
     * @return {@code true} if the {@link Object} is an empty {@link SortedSet}
     *         otherwise {@code false}.
     */
    private boolean isEmptySortedSet(Object obj) {
        boolean isEmptySortedSet = false;

        // We determine if the object is an empty sorted set by testing if it's
        // an instance of SortedSet, and if so, if it's empty.  Currently the
        // testing doesn't include checks of the other methods.
        if (obj instanceof SortedSet) {
            SortedSet ss = (SortedSet) obj;

            if ((ss.isEmpty()) && (ss.size() == 0)) {
                isEmptySortedSet = true;
            }
        }

        return isEmptySortedSet;
    }

    private void run() throws Exception {
        Method[] methods = this.getClass().getDeclaredMethods();

        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            String methodName = method.getName();

            if (methodName.startsWith("test")) {
                try {
                    Object obj = method.invoke(this, new Object[0]);
                } catch(Exception e) {
                    throw new Exception(this.getClass().getName() + "." +
                            methodName + " test failed, test exception "
                            + "follows\n" + e.getCause());
                }
            }
        }
    }

    private void throwException(String methodName, String reason)
            throws Exception
    {
        StringBuilder sb = new StringBuilder(thisClassName);
        sb.append(PERIOD);
        sb.append(methodName);
        sb.append(FAILED);
        sb.append(reason);
        throw new Exception(sb.toString());
    }

    /**
     *
     */
    private void test00() throws Exception {
        //throwException("test00", "This test has not been implemented yet.");
    }

    /**
     * Tests that the comparator is {@code null}.
     */
    private void testComparatorIsNull() throws Exception {
        SortedSet sortedSet = Collections.emptySortedSet();
        Comparator comparator = sortedSet.comparator();

        if (comparator != null) {
            throwException("testComparatorIsNull", "Comparator is not null.");
        }
    }

    /**
     * Tests that the contains method returns {@code false}.
     */
    private void testContains() throws Exception {
        SortedSet sortedSet = Collections.emptySortedSet();

        if (sortedSet.contains(new Object())) {
            throwException("testContains", "Should not contain any elements.");
        }
    }

    /**
     * Tests that the containsAll method returns {@code false}.
     */
    private void testContainsAll() throws Exception {
        SortedSet sortedSet = Collections.emptySortedSet();
        TreeSet treeSet = new TreeSet();
        treeSet.add("1");
        treeSet.add("2");
        treeSet.add("3");

        if (sortedSet.containsAll(treeSet)) {
            throwException("testContainsAll",
                    "Should not contain any elements.");
        }
    }

    /**
     * Tests that the iterator is empty.
     */
    private void testEmptyIterator() throws Exception {
        SortedSet sortedSet = Collections.emptySortedSet();
        Iterator emptyIterator = sortedSet.iterator();

        if ((emptyIterator != null) && (emptyIterator.hasNext())) {
            throwException("testEmptyIterator", "The iterator is not empty.");
        }
    }

    /**
     * Tests that the set is empty.
     */
    private void testIsEmpty() throws Exception {
        SortedSet sortedSet = Collections.emptySortedSet();

        if ((sortedSet != null) && (!sortedSet.isEmpty())) {
            throwException("testSizeIsZero", "The set is not empty.");
        }
    }

    /**
     * Tests that the first() method throws NoSuchElementException
     */
    private void testFirst() throws Exception {
        SortedSet sortedSet = Collections.emptySortedSet();

        try {
            sortedSet.first();
            throwException("testFirst",
                    "NoSuchElemenException was not thrown.");
        } catch(NoSuchElementException nsee) {
            // Do nothing
        }
    }

    /**
     * Tests the headSet() method.
     */
    private void testHeadSet() throws Exception {
        SortedSet sortedSet = Collections.emptySortedSet();
        SortedSet ss;

        try {
            ss = sortedSet.headSet(null);
            throwException("testHeadSet",
                    "Must throw NullPointerException for null element");
        } catch(NullPointerException npe) {
            // Do nothing
        }

        try {
            ss = sortedSet.headSet(new Object());
            throwException("testHeadSet",
                    "Must throw ClassCastException for non-Comparable element");
        } catch(ClassCastException cce) {
            // Do nothing.
        }

        ss = sortedSet.headSet("1");

        if ((ss == null) || !isEmptySortedSet(ss)) {
            throwException("testHeadSet",
                    "Returned value is null or not an EmptySortedSet.");
        }
    }

    /**
     * Tests that the last() method throws NoSuchElementException
     */
    private void testLast() throws Exception {
        SortedSet sortedSet = Collections.emptySortedSet();

        try {
            sortedSet.last();
            throwException("testLast",
                    "NoSuchElemenException was not thrown.");
        } catch(NoSuchElementException nsee) {
            // Do nothing
        }
    }

    /**
     * Tests that the size is 0.
     */
    private void testSizeIsZero() throws Exception {
        SortedSet sortedSet = Collections.emptySortedSet();
        int size = sortedSet.size();

        if (size > 0) {
            throwException("testSizeIsZero",
                    "The size of the set is greater then 0.");
        }
    }

    /**
     * Tests the subSet() method.
     */
    private void testSubSet() throws Exception {
        SortedSet sortedSet = Collections.emptySortedSet();
        SortedSet ss = sortedSet.headSet("1");

        try {
            ss = sortedSet.subSet(null, BigInteger.TEN);
            ss = sortedSet.subSet(BigInteger.ZERO, null);
            ss = sortedSet.subSet(null, null);
            throwException("testSubSet",
                    "Must throw NullPointerException for null element");
        } catch(NullPointerException npe) {
            // Do nothing
        }

        try {
            Object obj1 = new Object();
            Object obj2 = new Object();
            ss = sortedSet.subSet(obj1, BigInteger.TEN);
            ss = sortedSet.subSet(BigInteger.ZERO, obj2);
            ss = sortedSet.subSet(obj1, obj2);
            throwException("testSubSet",
                    "Must throw ClassCastException for parameter which is "
                    + "not Comparable.");
        } catch(ClassCastException cce) {
            // Do nothing.
        }

        try {
            ss = sortedSet.subSet(BigInteger.ZERO, BigInteger.ZERO);
            ss = sortedSet.subSet(BigInteger.TEN, BigInteger.ZERO);
            throwException("testSubSet",
                    "Must throw IllegalArgumentException when fromElement is "
                    + "not less then then toElement.");
        } catch(IllegalArgumentException iae) {
            // Do nothing.
        }

        ss = sortedSet.subSet(BigInteger.ZERO, BigInteger.TEN);

        if (!isEmptySortedSet(ss)) {
            throw new Exception("Returned value is not empty sorted set.");
        }
    }

    /**
     * Tests the tailSet() method.
     */
    private void testTailSet() throws Exception {
        SortedSet sortedSet = Collections.emptySortedSet();
        SortedSet ss;

        try {
            ss = sortedSet.tailSet(null);
            throwException("testTailSet",
                    "Must throw NullPointerException for null element");
        } catch(NullPointerException npe) {
            // Do nothing
        }

        try {
            SortedSet ss2 = sortedSet.tailSet(new Object());
            throwException("testTailSet",
                    "Must throw ClassCastException for non-Comparable element");
        } catch(ClassCastException cce) {
            // Do nothing.
        }

        ss = sortedSet.tailSet("1");

        if ((ss == null) || !isEmptySortedSet(ss)) {
            throwException("testTailSet",
                    "Returned value is null or not an EmptySortedSet.");
        }
    }

    /**
     * Tests that the array has a size of 0.
     */
    private void testToArray() throws Exception {
        SortedSet sortedSet = Collections.emptySortedSet();
        Object[] emptySortedSetArray = sortedSet.toArray();

        if ((emptySortedSetArray == null) || (emptySortedSetArray.length > 0)) {
            throwException("testToArray",
                    "Returned null array or array with length > 0.");
        }

        String[] strings = new String[2];
        strings[0] = "1";
        strings[1] = "2";
        emptySortedSetArray = sortedSet.toArray(strings);

        if ((emptySortedSetArray == null) || (emptySortedSetArray[0] != null)) {
            throwException("testToArray",
                    "Returned null array or array with length > 0.");
        }
    }
}
