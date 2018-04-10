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
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

import junit.framework.Test;

public class CopyOnWriteArrayListTest extends JSR166TestCase {

    public static void main(String[] args) {
        main(suite(), args);
    }

    public static Test suite() {
        class Implementation implements CollectionImplementation {
            public Class<?> klazz() { return CopyOnWriteArrayList.class; }
            public List emptyCollection() { return new CopyOnWriteArrayList(); }
            public Object makeElement(int i) { return i; }
            public boolean isConcurrent() { return true; }
            public boolean permitsNulls() { return true; }
        }
        class SubListImplementation extends Implementation {
            public List emptyCollection() {
                List list = super.emptyCollection();
                ThreadLocalRandom rnd = ThreadLocalRandom.current();
                if (rnd.nextBoolean())
                    list.add(makeElement(rnd.nextInt()));
                int i = rnd.nextInt(list.size() + 1);
                return list.subList(i, i);
            }
        }
        return newTestSuite(
                CopyOnWriteArrayListTest.class,
                CollectionTest.testSuite(new Implementation()),
                CollectionTest.testSuite(new SubListImplementation()));
    }

    static CopyOnWriteArrayList<Integer> populatedList(int n) {
        CopyOnWriteArrayList<Integer> list = new CopyOnWriteArrayList<>();
        assertTrue(list.isEmpty());
        for (int i = 0; i < n; i++)
            list.add(i);
        assertEquals(n <= 0, list.isEmpty());
        assertEquals(n, list.size());
        return list;
    }

    static CopyOnWriteArrayList<Integer> populatedList(Integer[] elements) {
        CopyOnWriteArrayList<Integer> list = new CopyOnWriteArrayList<>();
        assertTrue(list.isEmpty());
        for (Integer element : elements)
            list.add(element);
        assertFalse(list.isEmpty());
        assertEquals(elements.length, list.size());
        return list;
    }

    /**
     * a new list is empty
     */
    public void testConstructor() {
        List list = new CopyOnWriteArrayList();
        assertTrue(list.isEmpty());
    }

    /**
     * new list contains all elements of initializing array
     */
    public void testConstructor2() {
        Integer[] elts = new Integer[SIZE];
        for (int i = 0; i < SIZE - 1; ++i)
            elts[i] = i;
        List list = new CopyOnWriteArrayList(elts);
        for (int i = 0; i < SIZE; ++i)
            assertEquals(elts[i], list.get(i));
    }

    /**
     * new list contains all elements of initializing collection
     */
    public void testConstructor3() {
        Integer[] elts = new Integer[SIZE];
        for (int i = 0; i < SIZE - 1; ++i)
            elts[i] = i;
        List list = new CopyOnWriteArrayList(Arrays.asList(elts));
        for (int i = 0; i < SIZE; ++i)
            assertEquals(elts[i], list.get(i));
    }

    /**
     * addAll adds each element from the given collection, including duplicates
     */
    public void testAddAll() {
        List list = populatedList(3);
        assertTrue(list.addAll(Arrays.asList(three, four, five)));
        assertEquals(6, list.size());
        assertTrue(list.addAll(Arrays.asList(three, four, five)));
        assertEquals(9, list.size());
    }

    /**
     * addAllAbsent adds each element from the given collection that did not
     * already exist in the List
     */
    public void testAddAllAbsent() {
        CopyOnWriteArrayList list = populatedList(3);
        // "one" is duplicate and will not be added
        assertEquals(2, list.addAllAbsent(Arrays.asList(three, four, one)));
        assertEquals(5, list.size());
        assertEquals(0, list.addAllAbsent(Arrays.asList(three, four, one)));
        assertEquals(5, list.size());
    }

    /**
     * addIfAbsent will not add the element if it already exists in the list
     */
    public void testAddIfAbsent() {
        CopyOnWriteArrayList list = populatedList(SIZE);
        list.addIfAbsent(one);
        assertEquals(SIZE, list.size());
    }

    /**
     * addIfAbsent adds the element when it does not exist in the list
     */
    public void testAddIfAbsent2() {
        CopyOnWriteArrayList list = populatedList(SIZE);
        list.addIfAbsent(three);
        assertTrue(list.contains(three));
    }

    /**
     * clear removes all elements from the list
     */
    public void testClear() {
        List list = populatedList(SIZE);
        list.clear();
        assertEquals(0, list.size());
    }

    /**
     * Cloned list is equal
     */
    public void testClone() {
        CopyOnWriteArrayList l1 = populatedList(SIZE);
        CopyOnWriteArrayList l2 = (CopyOnWriteArrayList)(l1.clone());
        assertEquals(l1, l2);
        l1.clear();
        assertFalse(l1.equals(l2));
    }

    /**
     * contains is true for added elements
     */
    public void testContains() {
        List list = populatedList(3);
        assertTrue(list.contains(one));
        assertFalse(list.contains(five));
    }

    /**
     * adding at an index places it in the indicated index
     */
    public void testAddIndex() {
        List list = populatedList(3);
        list.add(0, m1);
        assertEquals(4, list.size());
        assertEquals(m1, list.get(0));
        assertEquals(zero, list.get(1));

        list.add(2, m2);
        assertEquals(5, list.size());
        assertEquals(m2, list.get(2));
        assertEquals(two, list.get(4));
    }

    /**
     * lists with same elements are equal and have same hashCode
     */
    public void testEquals() {
        List a = populatedList(3);
        List b = populatedList(3);
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
        List list = populatedList(3);
        assertTrue(list.containsAll(Arrays.asList()));
        assertTrue(list.containsAll(Arrays.asList(one)));
        assertTrue(list.containsAll(Arrays.asList(one, two)));
        assertFalse(list.containsAll(Arrays.asList(one, two, six)));
        assertFalse(list.containsAll(Arrays.asList(six)));

        try {
            list.containsAll(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * get returns the value at the given index
     */
    public void testGet() {
        List list = populatedList(3);
        assertEquals(0, list.get(0));
    }

    /**
     * indexOf(Object) returns the index of the first occurrence of the
     * specified element in this list, or -1 if this list does not
     * contain the element
     */
    public void testIndexOf() {
        List list = populatedList(3);
        assertEquals(-1, list.indexOf(-42));
        int size = list.size();
        for (int i = 0; i < size; i++) {
            assertEquals(i, list.indexOf(i));
            assertEquals(i, list.subList(0, size).indexOf(i));
            assertEquals(i, list.subList(0, i + 1).indexOf(i));
            assertEquals(-1, list.subList(0, i).indexOf(i));
            assertEquals(0, list.subList(i, size).indexOf(i));
            assertEquals(-1, list.subList(i + 1, size).indexOf(i));
        }

        list.add(1);
        assertEquals(1, list.indexOf(1));
        assertEquals(1, list.subList(0, size + 1).indexOf(1));
        assertEquals(0, list.subList(1, size + 1).indexOf(1));
        assertEquals(size - 2, list.subList(2, size + 1).indexOf(1));
        assertEquals(0, list.subList(size, size + 1).indexOf(1));
        assertEquals(-1, list.subList(size + 1, size + 1).indexOf(1));
    }

    /**
     * indexOf(E, int) returns the index of the first occurrence of the
     * specified element in this list, searching forwards from index,
     * or returns -1 if the element is not found
     */
    public void testIndexOf2() {
        CopyOnWriteArrayList list = populatedList(3);
        int size = list.size();
        assertEquals(-1, list.indexOf(-42, 0));

        // we might expect IOOBE, but spec says otherwise
        assertEquals(-1, list.indexOf(0, size));
        assertEquals(-1, list.indexOf(0, Integer.MAX_VALUE));

        assertThrows(
            IndexOutOfBoundsException.class,
            () -> list.indexOf(0, -1),
            () -> list.indexOf(0, Integer.MIN_VALUE));

        for (int i = 0; i < size; i++) {
            assertEquals(i, list.indexOf(i, 0));
            assertEquals(i, list.indexOf(i, i));
            assertEquals(-1, list.indexOf(i, i + 1));
        }

        list.add(1);
        assertEquals(1, list.indexOf(1, 0));
        assertEquals(1, list.indexOf(1, 1));
        assertEquals(size, list.indexOf(1, 2));
        assertEquals(size, list.indexOf(1, size));
    }

    /**
     * isEmpty returns true when empty, else false
     */
    public void testIsEmpty() {
        List empty = new CopyOnWriteArrayList();
        assertTrue(empty.isEmpty());
        assertTrue(empty.subList(0, 0).isEmpty());

        List full = populatedList(SIZE);
        assertFalse(full.isEmpty());
        assertTrue(full.subList(0, 0).isEmpty());
        assertTrue(full.subList(SIZE, SIZE).isEmpty());
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
        shuffle(elements);
        Collection<Integer> full = populatedList(elements);

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
        CopyOnWriteArrayList list = populatedList(SIZE);
        Iterator it = list.iterator();
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
        List list = populatedList(3);
        String s = list.toString();
        for (int i = 0; i < 3; ++i)
            assertTrue(s.contains(String.valueOf(i)));
        assertEquals(new ArrayList(list).toString(),
                     list.toString());
    }

    /**
     * lastIndexOf(Object) returns the index of the last occurrence of
     * the specified element in this list, or -1 if this list does not
     * contain the element
     */
    public void testLastIndexOf1() {
        List list = populatedList(3);
        assertEquals(-1, list.lastIndexOf(-42));
        int size = list.size();
        for (int i = 0; i < size; i++) {
            assertEquals(i, list.lastIndexOf(i));
            assertEquals(i, list.subList(0, size).lastIndexOf(i));
            assertEquals(i, list.subList(0, i + 1).lastIndexOf(i));
            assertEquals(-1, list.subList(0, i).lastIndexOf(i));
            assertEquals(0, list.subList(i, size).lastIndexOf(i));
            assertEquals(-1, list.subList(i + 1, size).lastIndexOf(i));
        }

        list.add(1);
        assertEquals(size, list.lastIndexOf(1));
        assertEquals(size, list.subList(0, size + 1).lastIndexOf(1));
        assertEquals(1, list.subList(0, size).lastIndexOf(1));
        assertEquals(0, list.subList(1, 2).lastIndexOf(1));
        assertEquals(-1, list.subList(0, 1).indexOf(1));
    }

    /**
     * lastIndexOf(E, int) returns the index of the last occurrence of the
     * specified element in this list, searching backwards from index, or
     * returns -1 if the element is not found
     */
    public void testLastIndexOf2() {
        CopyOnWriteArrayList list = populatedList(3);

        // we might expect IOOBE, but spec says otherwise
        assertEquals(-1, list.lastIndexOf(0, -1));

        int size = list.size();
        assertThrows(
            IndexOutOfBoundsException.class,
            () -> list.lastIndexOf(0, size),
            () -> list.lastIndexOf(0, Integer.MAX_VALUE));

        for (int i = 0; i < size; i++) {
            assertEquals(i, list.lastIndexOf(i, i));
            assertEquals(list.indexOf(i), list.lastIndexOf(i, i));
            if (i > 0)
                assertEquals(-1, list.lastIndexOf(i, i - 1));
        }
        list.add(one);
        list.add(three);
        assertEquals(1, list.lastIndexOf(one, 1));
        assertEquals(1, list.lastIndexOf(one, 2));
        assertEquals(3, list.lastIndexOf(one, 3));
        assertEquals(3, list.lastIndexOf(one, 4));
        assertEquals(-1, list.lastIndexOf(three, 3));
    }

    /**
     * listIterator traverses all elements
     */
    public void testListIterator1() {
        List list = populatedList(SIZE);
        ListIterator i = list.listIterator();
        int j;
        for (j = 0; i.hasNext(); j++)
            assertEquals(j, i.next());
        assertEquals(SIZE, j);
    }

    /**
     * listIterator only returns those elements after the given index
     */
    public void testListIterator2() {
        List list = populatedList(3);
        ListIterator i = list.listIterator(1);
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
            List list = populatedList(SIZE);
            assertEquals(i, list.remove(i));
            assertEquals(SIZE - 1, list.size());
            assertFalse(list.contains(new Integer(i)));
        }
    }

    /**
     * remove(Object) removes the object if found and returns true
     */
    public void testRemove_Object() {
        int SIZE = 3;
        for (int i = 0; i < SIZE; i++) {
            List list = populatedList(SIZE);
            assertFalse(list.remove(new Integer(-42)));
            assertTrue(list.remove(new Integer(i)));
            assertEquals(SIZE - 1, list.size());
            assertFalse(list.contains(new Integer(i)));
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
        List list = populatedList(3);
        assertTrue(list.removeAll(Arrays.asList(one, two)));
        assertEquals(1, list.size());
        assertFalse(list.removeAll(Arrays.asList(one, two)));
        assertEquals(1, list.size());
    }

    /**
     * set changes the element at the given index
     */
    public void testSet() {
        List list = populatedList(3);
        assertEquals(2, list.set(2, four));
        assertEquals(4, list.get(2));
    }

    /**
     * size returns the number of elements
     */
    public void testSize() {
        List empty = new CopyOnWriteArrayList();
        assertEquals(0, empty.size());
        assertEquals(0, empty.subList(0, 0).size());

        List full = populatedList(SIZE);
        assertEquals(SIZE, full.size());
        assertEquals(0, full.subList(0, 0).size());
        assertEquals(0, full.subList(SIZE, SIZE).size());
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
        shuffle(elements);
        Collection<Integer> full = populatedList(elements);

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
        shuffle(elements);
        Collection<Integer> full = populatedList(elements);

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
        List a = populatedList(10);
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

        assertThrows(
            IndexOutOfBoundsException.class,
            () -> s.get(0),
            () -> s.set(0, 42));
    }

    // Exception tests

    /**
     * toArray throws an ArrayStoreException when the given array
     * can not store the objects inside the list
     */
    public void testToArray_ArrayStoreException() {
        List list = new CopyOnWriteArrayList();
        // Integers are not auto-converted to Longs
        list.add(86);
        list.add(99);
        assertThrows(
            ArrayStoreException.class,
            () -> list.toArray(new Long[0]),
            () -> list.toArray(new Long[5]));
    }

    void testIndexOutOfBoundsException(List list) {
        int size = list.size();
        assertThrows(
            IndexOutOfBoundsException.class,
            () -> list.get(-1),
            () -> list.get(size),
            () -> list.set(-1, "qwerty"),
            () -> list.set(size, "qwerty"),
            () -> list.add(-1, "qwerty"),
            () -> list.add(size + 1, "qwerty"),
            () -> list.remove(-1),
            () -> list.remove(size),
            () -> list.addAll(-1, Collections.emptyList()),
            () -> list.addAll(size + 1, Collections.emptyList()),
            () -> list.listIterator(-1),
            () -> list.listIterator(size + 1),
            () -> list.subList(-1, size),
            () -> list.subList(0, size + 1));

        // Conversely, operations that must not throw
        list.addAll(0, Collections.emptyList());
        list.addAll(size, Collections.emptyList());
        list.add(0, "qwerty");
        list.add(list.size(), "qwerty");
        list.get(0);
        list.get(list.size() - 1);
        list.set(0, "azerty");
        list.set(list.size() - 1, "azerty");
        list.listIterator(0);
        list.listIterator(list.size());
        list.subList(0, list.size());
        list.remove(list.size() - 1);
    }

    /**
     * IndexOutOfBoundsException is thrown when specified
     */
    public void testIndexOutOfBoundsException() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        List x = populatedList(rnd.nextInt(5));
        testIndexOutOfBoundsException(x);

        int start = rnd.nextInt(x.size() + 1);
        int end = rnd.nextInt(start, x.size() + 1);
        assertThrows(
            IndexOutOfBoundsException.class,
            () -> x.subList(start, start - 1));
        List subList = x.subList(start, end);
        testIndexOutOfBoundsException(x);
    }

    /**
     * a deserialized/reserialized list equals original
     */
    public void testSerialization() throws Exception {
        List x = populatedList(SIZE);
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
