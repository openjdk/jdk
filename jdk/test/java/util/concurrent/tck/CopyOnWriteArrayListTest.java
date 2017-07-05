/*
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
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 * Other contributors include Andrew Wright, Jeffrey Hayes,
 * Pat Fisher, Mike Judd.
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.concurrent.CopyOnWriteArrayList;

import junit.framework.Test;
import junit.framework.TestSuite;

public class CopyOnWriteArrayListTest extends JSR166TestCase {

    public static void main(String[] args) {
        main(suite(), args);
    }

    public static Test suite() {
        return new TestSuite(CopyOnWriteArrayListTest.class);
    }

    static CopyOnWriteArrayList<Integer> populatedArray(int n) {
        CopyOnWriteArrayList<Integer> a = new CopyOnWriteArrayList<Integer>();
        assertTrue(a.isEmpty());
        for (int i = 0; i < n; i++)
            a.add(i);
        assertFalse(a.isEmpty());
        assertEquals(n, a.size());
        return a;
    }

    static CopyOnWriteArrayList<Integer> populatedArray(Integer[] elements) {
        CopyOnWriteArrayList<Integer> a = new CopyOnWriteArrayList<Integer>();
        assertTrue(a.isEmpty());
        for (int i = 0; i < elements.length; i++)
            a.add(elements[i]);
        assertFalse(a.isEmpty());
        assertEquals(elements.length, a.size());
        return a;
    }

    /**
     * a new list is empty
     */
    public void testConstructor() {
        CopyOnWriteArrayList a = new CopyOnWriteArrayList();
        assertTrue(a.isEmpty());
    }

    /**
     * new list contains all elements of initializing array
     */
    public void testConstructor2() {
        Integer[] ints = new Integer[SIZE];
        for (int i = 0; i < SIZE - 1; ++i)
            ints[i] = new Integer(i);
        CopyOnWriteArrayList a = new CopyOnWriteArrayList(ints);
        for (int i = 0; i < SIZE; ++i)
            assertEquals(ints[i], a.get(i));
    }

    /**
     * new list contains all elements of initializing collection
     */
    public void testConstructor3() {
        Integer[] ints = new Integer[SIZE];
        for (int i = 0; i < SIZE - 1; ++i)
            ints[i] = new Integer(i);
        CopyOnWriteArrayList a = new CopyOnWriteArrayList(Arrays.asList(ints));
        for (int i = 0; i < SIZE; ++i)
            assertEquals(ints[i], a.get(i));
    }

    /**
     * addAll adds each element from the given collection, including duplicates
     */
    public void testAddAll() {
        CopyOnWriteArrayList full = populatedArray(3);
        assertTrue(full.addAll(Arrays.asList(three, four, five)));
        assertEquals(6, full.size());
        assertTrue(full.addAll(Arrays.asList(three, four, five)));
        assertEquals(9, full.size());
    }

    /**
     * addAllAbsent adds each element from the given collection that did not
     * already exist in the List
     */
    public void testAddAllAbsent() {
        CopyOnWriteArrayList full = populatedArray(3);
        // "one" is duplicate and will not be added
        assertEquals(2, full.addAllAbsent(Arrays.asList(three, four, one)));
        assertEquals(5, full.size());
        assertEquals(0, full.addAllAbsent(Arrays.asList(three, four, one)));
        assertEquals(5, full.size());
    }

    /**
     * addIfAbsent will not add the element if it already exists in the list
     */
    public void testAddIfAbsent() {
        CopyOnWriteArrayList full = populatedArray(SIZE);
        full.addIfAbsent(one);
        assertEquals(SIZE, full.size());
    }

    /**
     * addIfAbsent adds the element when it does not exist in the list
     */
    public void testAddIfAbsent2() {
        CopyOnWriteArrayList full = populatedArray(SIZE);
        full.addIfAbsent(three);
        assertTrue(full.contains(three));
    }

    /**
     * clear removes all elements from the list
     */
    public void testClear() {
        CopyOnWriteArrayList full = populatedArray(SIZE);
        full.clear();
        assertEquals(0, full.size());
    }

    /**
     * Cloned list is equal
     */
    public void testClone() {
        CopyOnWriteArrayList l1 = populatedArray(SIZE);
        CopyOnWriteArrayList l2 = (CopyOnWriteArrayList)(l1.clone());
        assertEquals(l1, l2);
        l1.clear();
        assertFalse(l1.equals(l2));
    }

    /**
     * contains is true for added elements
     */
    public void testContains() {
        CopyOnWriteArrayList full = populatedArray(3);
        assertTrue(full.contains(one));
        assertFalse(full.contains(five));
    }

    /**
     * adding at an index places it in the indicated index
     */
    public void testAddIndex() {
        CopyOnWriteArrayList full = populatedArray(3);
        full.add(0, m1);
        assertEquals(4, full.size());
        assertEquals(m1, full.get(0));
        assertEquals(zero, full.get(1));

        full.add(2, m2);
        assertEquals(5, full.size());
        assertEquals(m2, full.get(2));
        assertEquals(two, full.get(4));
    }

    /**
     * lists with same elements are equal and have same hashCode
     */
    public void testEquals() {
        CopyOnWriteArrayList a = populatedArray(3);
        CopyOnWriteArrayList b = populatedArray(3);
        assertTrue(a.equals(b));
        assertTrue(b.equals(a));
        assertTrue(a.containsAll(b));
        assertTrue(b.containsAll(a));
        assertEquals(a.hashCode(), b.hashCode());
        a.add(m1);
        assertFalse(a.equals(b));
        assertFalse(b.equals(a));
        assertTrue(a.containsAll(b));
        assertFalse(b.containsAll(a));
        b.add(m1);
        assertTrue(a.equals(b));
        assertTrue(b.equals(a));
        assertTrue(a.containsAll(b));
        assertTrue(b.containsAll(a));
        assertEquals(a.hashCode(), b.hashCode());

        assertFalse(a.equals(null));
    }

    /**
     * containsAll returns true for collections with subset of elements
     */
    public void testContainsAll() {
        CopyOnWriteArrayList full = populatedArray(3);
        assertTrue(full.containsAll(Arrays.asList()));
        assertTrue(full.containsAll(Arrays.asList(one)));
        assertTrue(full.containsAll(Arrays.asList(one, two)));
        assertFalse(full.containsAll(Arrays.asList(one, two, six)));
        assertFalse(full.containsAll(Arrays.asList(six)));

        try {
            full.containsAll(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * get returns the value at the given index
     */
    public void testGet() {
        CopyOnWriteArrayList full = populatedArray(3);
        assertEquals(0, full.get(0));
    }

    /**
     * indexOf gives the index for the given object
     */
    public void testIndexOf() {
        CopyOnWriteArrayList full = populatedArray(3);
        assertEquals(1, full.indexOf(one));
        assertEquals(-1, full.indexOf("puppies"));
    }

    /**
     * indexOf gives the index based on the given index
     * at which to start searching
     */
    public void testIndexOf2() {
        CopyOnWriteArrayList full = populatedArray(3);
        assertEquals(1, full.indexOf(one, 0));
        assertEquals(-1, full.indexOf(one, 2));
    }

    /**
     * isEmpty returns true when empty, else false
     */
    public void testIsEmpty() {
        CopyOnWriteArrayList empty = new CopyOnWriteArrayList();
        CopyOnWriteArrayList full = populatedArray(SIZE);
        assertTrue(empty.isEmpty());
        assertFalse(full.isEmpty());
    }

    /**
     * iterator() returns an iterator containing the elements of the
     * list in insertion order
     */
    public void testIterator() {
        Collection empty = new CopyOnWriteArrayList();
        assertFalse(empty.iterator().hasNext());
        try {
            empty.iterator().next();
            shouldThrow();
        } catch (NoSuchElementException success) {}

        Integer[] elements = new Integer[SIZE];
        for (int i = 0; i < SIZE; i++)
            elements[i] = i;
        Collections.shuffle(Arrays.asList(elements));
        Collection<Integer> full = populatedArray(elements);

        Iterator it = full.iterator();
        for (int j = 0; j < SIZE; j++) {
            assertTrue(it.hasNext());
            assertEquals(elements[j], it.next());
        }
        assertIteratorExhausted(it);
    }

    /**
     * iterator of empty collection has no elements
     */
    public void testEmptyIterator() {
        Collection c = new CopyOnWriteArrayList();
        assertIteratorExhausted(c.iterator());
    }

    /**
     * iterator.remove throws UnsupportedOperationException
     */
    public void testIteratorRemove() {
        CopyOnWriteArrayList full = populatedArray(SIZE);
        Iterator it = full.iterator();
        it.next();
        try {
            it.remove();
            shouldThrow();
        } catch (UnsupportedOperationException success) {}
    }

    /**
     * toString contains toString of elements
     */
    public void testToString() {
        assertEquals("[]", new CopyOnWriteArrayList().toString());
        CopyOnWriteArrayList full = populatedArray(3);
        String s = full.toString();
        for (int i = 0; i < 3; ++i)
            assertTrue(s.contains(String.valueOf(i)));
        assertEquals(new ArrayList(full).toString(),
                     full.toString());
    }

    /**
     * lastIndexOf returns the index for the given object
     */
    public void testLastIndexOf1() {
        CopyOnWriteArrayList full = populatedArray(3);
        full.add(one);
        full.add(three);
        assertEquals(3, full.lastIndexOf(one));
        assertEquals(-1, full.lastIndexOf(six));
    }

    /**
     * lastIndexOf returns the index from the given starting point
     */
    public void testLastIndexOf2() {
        CopyOnWriteArrayList full = populatedArray(3);
        full.add(one);
        full.add(three);
        assertEquals(3, full.lastIndexOf(one, 4));
        assertEquals(-1, full.lastIndexOf(three, 3));
    }

    /**
     * listIterator traverses all elements
     */
    public void testListIterator1() {
        CopyOnWriteArrayList full = populatedArray(SIZE);
        ListIterator i = full.listIterator();
        int j;
        for (j = 0; i.hasNext(); j++)
            assertEquals(j, i.next());
        assertEquals(SIZE, j);
    }

    /**
     * listIterator only returns those elements after the given index
     */
    public void testListIterator2() {
        CopyOnWriteArrayList full = populatedArray(3);
        ListIterator i = full.listIterator(1);
        int j;
        for (j = 0; i.hasNext(); j++)
            assertEquals(j + 1, i.next());
        assertEquals(2, j);
    }

    /**
     * remove(int) removes and returns the object at the given index
     */
    public void testRemove_int() {
        int SIZE = 3;
        for (int i = 0; i < SIZE; i++) {
            CopyOnWriteArrayList full = populatedArray(SIZE);
            assertEquals(i, full.remove(i));
            assertEquals(SIZE - 1, full.size());
            assertFalse(full.contains(new Integer(i)));
        }
    }

    /**
     * remove(Object) removes the object if found and returns true
     */
    public void testRemove_Object() {
        int SIZE = 3;
        for (int i = 0; i < SIZE; i++) {
            CopyOnWriteArrayList full = populatedArray(SIZE);
            assertFalse(full.remove(new Integer(-42)));
            assertTrue(full.remove(new Integer(i)));
            assertEquals(SIZE - 1, full.size());
            assertFalse(full.contains(new Integer(i)));
        }
        CopyOnWriteArrayList x = new CopyOnWriteArrayList(Arrays.asList(4, 5, 6));
        assertTrue(x.remove(new Integer(6)));
        assertEquals(x, Arrays.asList(4, 5));
        assertTrue(x.remove(new Integer(4)));
        assertEquals(x, Arrays.asList(5));
        assertTrue(x.remove(new Integer(5)));
        assertEquals(x, Arrays.asList());
        assertFalse(x.remove(new Integer(5)));
    }

    /**
     * removeAll removes all elements from the given collection
     */
    public void testRemoveAll() {
        CopyOnWriteArrayList full = populatedArray(3);
        assertTrue(full.removeAll(Arrays.asList(one, two)));
        assertEquals(1, full.size());
        assertFalse(full.removeAll(Arrays.asList(one, two)));
        assertEquals(1, full.size());
    }

    /**
     * set changes the element at the given index
     */
    public void testSet() {
        CopyOnWriteArrayList full = populatedArray(3);
        assertEquals(2, full.set(2, four));
        assertEquals(4, full.get(2));
    }

    /**
     * size returns the number of elements
     */
    public void testSize() {
        CopyOnWriteArrayList empty = new CopyOnWriteArrayList();
        CopyOnWriteArrayList full = populatedArray(SIZE);
        assertEquals(SIZE, full.size());
        assertEquals(0, empty.size());
    }

    /**
     * toArray() returns an Object array containing all elements from
     * the list in insertion order
     */
    public void testToArray() {
        Object[] a = new CopyOnWriteArrayList().toArray();
        assertTrue(Arrays.equals(new Object[0], a));
        assertSame(Object[].class, a.getClass());

        Integer[] elements = new Integer[SIZE];
        for (int i = 0; i < SIZE; i++)
            elements[i] = i;
        Collections.shuffle(Arrays.asList(elements));
        Collection<Integer> full = populatedArray(elements);

        assertTrue(Arrays.equals(elements, full.toArray()));
        assertSame(Object[].class, full.toArray().getClass());
    }

    /**
     * toArray(Integer array) returns an Integer array containing all
     * elements from the list in insertion order
     */
    public void testToArray2() {
        Collection empty = new CopyOnWriteArrayList();
        Integer[] a;

        a = new Integer[0];
        assertSame(a, empty.toArray(a));

        a = new Integer[SIZE / 2];
        Arrays.fill(a, 42);
        assertSame(a, empty.toArray(a));
        assertNull(a[0]);
        for (int i = 1; i < a.length; i++)
            assertEquals(42, (int) a[i]);

        Integer[] elements = new Integer[SIZE];
        for (int i = 0; i < SIZE; i++)
            elements[i] = i;
        Collections.shuffle(Arrays.asList(elements));
        Collection<Integer> full = populatedArray(elements);

        Arrays.fill(a, 42);
        assertTrue(Arrays.equals(elements, full.toArray(a)));
        for (int i = 0; i < a.length; i++)
            assertEquals(42, (int) a[i]);
        assertSame(Integer[].class, full.toArray(a).getClass());

        a = new Integer[SIZE];
        Arrays.fill(a, 42);
        assertSame(a, full.toArray(a));
        assertTrue(Arrays.equals(elements, a));

        a = new Integer[2 * SIZE];
        Arrays.fill(a, 42);
        assertSame(a, full.toArray(a));
        assertTrue(Arrays.equals(elements, Arrays.copyOf(a, SIZE)));
        assertNull(a[SIZE]);
        for (int i = SIZE + 1; i < a.length; i++)
            assertEquals(42, (int) a[i]);
    }

    /**
     * sublists contains elements at indexes offset from their base
     */
    public void testSubList() {
        CopyOnWriteArrayList a = populatedArray(10);
        assertTrue(a.subList(1,1).isEmpty());
        for (int j = 0; j < 9; ++j) {
            for (int i = j ; i < 10; ++i) {
                List b = a.subList(j,i);
                for (int k = j; k < i; ++k) {
                    assertEquals(new Integer(k), b.get(k-j));
                }
            }
        }

        List s = a.subList(2, 5);
        assertEquals(3, s.size());
        s.set(2, m1);
        assertEquals(a.get(4), m1);
        s.clear();
        assertEquals(7, a.size());
    }

    // Exception tests

    /**
     * toArray throws an ArrayStoreException when the given array
     * can not store the objects inside the list
     */
    public void testToArray_ArrayStoreException() {
        CopyOnWriteArrayList c = new CopyOnWriteArrayList();
        c.add("zfasdfsdf");
        c.add("asdadasd");
        try {
            c.toArray(new Long[5]);
            shouldThrow();
        } catch (ArrayStoreException success) {}
    }

    /**
     * get throws an IndexOutOfBoundsException on a negative index
     */
    public void testGet1_IndexOutOfBoundsException() {
        CopyOnWriteArrayList c = populatedArray(5);
        List[] lists = { c, c.subList(1, c.size() - 1) };
        for (List list : lists) {
            try {
                list.get(-1);
                shouldThrow();
            } catch (IndexOutOfBoundsException success) {}
        }
    }

    /**
     * get throws an IndexOutOfBoundsException on a too high index
     */
    public void testGet2_IndexOutOfBoundsException() {
        CopyOnWriteArrayList c = populatedArray(5);
        List[] lists = { c, c.subList(1, c.size() - 1) };
        for (List list : lists) {
            try {
                list.get(list.size());
                shouldThrow();
            } catch (IndexOutOfBoundsException success) {}
        }
    }

    /**
     * set throws an IndexOutOfBoundsException on a negative index
     */
    public void testSet1_IndexOutOfBoundsException() {
        CopyOnWriteArrayList c = populatedArray(5);
        List[] lists = { c, c.subList(1, c.size() - 1) };
        for (List list : lists) {
            try {
                list.set(-1, "qwerty");
                shouldThrow();
            } catch (IndexOutOfBoundsException success) {}
        }
    }

    /**
     * set throws an IndexOutOfBoundsException on a too high index
     */
    public void testSet2() {
        CopyOnWriteArrayList c = populatedArray(5);
        List[] lists = { c, c.subList(1, c.size() - 1) };
        for (List list : lists) {
            try {
                list.set(list.size(), "qwerty");
                shouldThrow();
            } catch (IndexOutOfBoundsException success) {}
        }
    }

    /**
     * add throws an IndexOutOfBoundsException on a negative index
     */
    public void testAdd1_IndexOutOfBoundsException() {
        CopyOnWriteArrayList c = populatedArray(5);
        List[] lists = { c, c.subList(1, c.size() - 1) };
        for (List list : lists) {
            try {
                list.add(-1, "qwerty");
                shouldThrow();
            } catch (IndexOutOfBoundsException success) {}
        }
    }

    /**
     * add throws an IndexOutOfBoundsException on a too high index
     */
    public void testAdd2_IndexOutOfBoundsException() {
        CopyOnWriteArrayList c = populatedArray(5);
        List[] lists = { c, c.subList(1, c.size() - 1) };
        for (List list : lists) {
            try {
                list.add(list.size() + 1, "qwerty");
                shouldThrow();
            } catch (IndexOutOfBoundsException success) {}
        }
    }

    /**
     * remove throws an IndexOutOfBoundsException on a negative index
     */
    public void testRemove1_IndexOutOfBounds() {
        CopyOnWriteArrayList c = populatedArray(5);
        List[] lists = { c, c.subList(1, c.size() - 1) };
        for (List list : lists) {
            try {
                list.remove(-1);
                shouldThrow();
            } catch (IndexOutOfBoundsException success) {}
        }
    }

    /**
     * remove throws an IndexOutOfBoundsException on a too high index
     */
    public void testRemove2_IndexOutOfBounds() {
        CopyOnWriteArrayList c = populatedArray(5);
        List[] lists = { c, c.subList(1, c.size() - 1) };
        for (List list : lists) {
            try {
                list.remove(list.size());
                shouldThrow();
            } catch (IndexOutOfBoundsException success) {}
        }
    }

    /**
     * addAll throws an IndexOutOfBoundsException on a negative index
     */
    public void testAddAll1_IndexOutOfBoundsException() {
        CopyOnWriteArrayList c = populatedArray(5);
        List[] lists = { c, c.subList(1, c.size() - 1) };
        for (List list : lists) {
            try {
                list.addAll(-1, new LinkedList());
                shouldThrow();
            } catch (IndexOutOfBoundsException success) {}
        }
    }

    /**
     * addAll throws an IndexOutOfBoundsException on a too high index
     */
    public void testAddAll2_IndexOutOfBoundsException() {
        CopyOnWriteArrayList c = populatedArray(5);
        List[] lists = { c, c.subList(1, c.size() - 1) };
        for (List list : lists) {
            try {
                list.addAll(list.size() + 1, new LinkedList());
                shouldThrow();
            } catch (IndexOutOfBoundsException success) {}
        }
    }

    /**
     * listIterator throws an IndexOutOfBoundsException on a negative index
     */
    public void testListIterator1_IndexOutOfBoundsException() {
        CopyOnWriteArrayList c = populatedArray(5);
        List[] lists = { c, c.subList(1, c.size() - 1) };
        for (List list : lists) {
            try {
                list.listIterator(-1);
                shouldThrow();
            } catch (IndexOutOfBoundsException success) {}
        }
    }

    /**
     * listIterator throws an IndexOutOfBoundsException on a too high index
     */
    public void testListIterator2_IndexOutOfBoundsException() {
        CopyOnWriteArrayList c = populatedArray(5);
        List[] lists = { c, c.subList(1, c.size() - 1) };
        for (List list : lists) {
            try {
                list.listIterator(list.size() + 1);
                shouldThrow();
            } catch (IndexOutOfBoundsException success) {}
        }
    }

    /**
     * subList throws an IndexOutOfBoundsException on a negative index
     */
    public void testSubList1_IndexOutOfBoundsException() {
        CopyOnWriteArrayList c = populatedArray(5);
        List[] lists = { c, c.subList(1, c.size() - 1) };
        for (List list : lists) {
            try {
                list.subList(-1, list.size());
                shouldThrow();
            } catch (IndexOutOfBoundsException success) {}
        }
    }

    /**
     * subList throws an IndexOutOfBoundsException on a too high index
     */
    public void testSubList2_IndexOutOfBoundsException() {
        CopyOnWriteArrayList c = populatedArray(5);
        List[] lists = { c, c.subList(1, c.size() - 1) };
        for (List list : lists) {
            try {
                list.subList(0, list.size() + 1);
                shouldThrow();
            } catch (IndexOutOfBoundsException success) {}
        }
    }

    /**
     * subList throws IndexOutOfBoundsException when the second index
     * is lower then the first
     */
    public void testSubList3_IndexOutOfBoundsException() {
        CopyOnWriteArrayList c = populatedArray(5);
        List[] lists = { c, c.subList(1, c.size() - 1) };
        for (List list : lists) {
            try {
                list.subList(list.size() - 1, 1);
                shouldThrow();
            } catch (IndexOutOfBoundsException success) {}
        }
    }

    /**
     * a deserialized serialized list is equal
     */
    public void testSerialization() throws Exception {
        List x = populatedArray(SIZE);
        List y = serialClone(x);

        assertNotSame(x, y);
        assertEquals(x.size(), y.size());
        assertEquals(x.toString(), y.toString());
        assertTrue(Arrays.equals(x.toArray(), y.toArray()));
        assertEquals(x, y);
        assertEquals(y, x);
        while (!x.isEmpty()) {
            assertFalse(y.isEmpty());
            assertEquals(x.remove(0), y.remove(0));
        }
        assertTrue(y.isEmpty());
    }

}
