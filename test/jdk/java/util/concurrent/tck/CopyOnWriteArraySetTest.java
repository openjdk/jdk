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
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import junit.framework.Test;

public class CopyOnWriteArraySetTest extends JSR166TestCase {
    public static void main(String[] args) {
        main(suite(), args);
    }
    public static Test suite() {
        class Implementation implements CollectionImplementation {
            public Class<?> klazz() { return CopyOnWriteArraySet.class; }
            public Set emptyCollection() { return new CopyOnWriteArraySet(); }
            public Object makeElement(int i) { return i; }
            public boolean isConcurrent() { return true; }
            public boolean permitsNulls() { return true; }
        }
        return newTestSuite(
                CopyOnWriteArraySetTest.class,
                CollectionTest.testSuite(new Implementation()));
    }

    static CopyOnWriteArraySet<Integer> populatedSet(int n) {
        CopyOnWriteArraySet<Integer> a = new CopyOnWriteArraySet<>();
        assertTrue(a.isEmpty());
        for (int i = 0; i < n; i++)
            a.add(i);
        assertEquals(n == 0, a.isEmpty());
        assertEquals(n, a.size());
        return a;
    }

    static CopyOnWriteArraySet populatedSet(Integer[] elements) {
        CopyOnWriteArraySet<Integer> a = new CopyOnWriteArraySet<>();
        assertTrue(a.isEmpty());
        for (int i = 0; i < elements.length; i++)
            a.add(elements[i]);
        assertFalse(a.isEmpty());
        assertEquals(elements.length, a.size());
        return a;
    }

    /**
     * Default-constructed set is empty
     */
    public void testConstructor() {
        CopyOnWriteArraySet a = new CopyOnWriteArraySet();
        assertTrue(a.isEmpty());
    }

    /**
     * Collection-constructed set holds all of its elements
     */
    public void testConstructor3() {
        Integer[] ints = new Integer[SIZE];
        for (int i = 0; i < SIZE - 1; ++i)
            ints[i] = new Integer(i);
        CopyOnWriteArraySet a = new CopyOnWriteArraySet(Arrays.asList(ints));
        for (int i = 0; i < SIZE; ++i)
            assertTrue(a.contains(ints[i]));
    }

    /**
     * addAll adds each non-duplicate element from the given collection
     */
    public void testAddAll() {
        Set full = populatedSet(3);
        assertTrue(full.addAll(Arrays.asList(three, four, five)));
        assertEquals(6, full.size());
        assertFalse(full.addAll(Arrays.asList(three, four, five)));
        assertEquals(6, full.size());
    }

    /**
     * addAll adds each non-duplicate element from the given collection
     */
    public void testAddAll2() {
        Set full = populatedSet(3);
        // "one" is duplicate and will not be added
        assertTrue(full.addAll(Arrays.asList(three, four, one)));
        assertEquals(5, full.size());
        assertFalse(full.addAll(Arrays.asList(three, four, one)));
        assertEquals(5, full.size());
    }

    /**
     * add will not add the element if it already exists in the set
     */
    public void testAdd2() {
        Set full = populatedSet(3);
        full.add(one);
        assertEquals(3, full.size());
    }

    /**
     * add adds the element when it does not exist in the set
     */
    public void testAdd3() {
        Set full = populatedSet(3);
        full.add(three);
        assertTrue(full.contains(three));
    }

    /**
     * clear removes all elements from the set
     */
    public void testClear() {
        Collection full = populatedSet(3);
        full.clear();
        assertEquals(0, full.size());
        assertTrue(full.isEmpty());
    }

    /**
     * contains returns true for added elements
     */
    public void testContains() {
        Collection full = populatedSet(3);
        assertTrue(full.contains(one));
        assertFalse(full.contains(five));
    }

    /**
     * Sets with equal elements are equal
     */
    public void testEquals() {
        CopyOnWriteArraySet a = populatedSet(3);
        CopyOnWriteArraySet b = populatedSet(3);
        assertTrue(a.equals(b));
        assertTrue(b.equals(a));
        assertTrue(a.containsAll(b));
        assertTrue(b.containsAll(a));
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(a.size(), b.size());

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

        Object x = a.iterator().next();
        a.remove(x);
        assertFalse(a.equals(b));
        assertFalse(b.equals(a));
        assertFalse(a.containsAll(b));
        assertTrue(b.containsAll(a));
        a.add(x);
        assertTrue(a.equals(b));
        assertTrue(b.equals(a));
        assertTrue(a.containsAll(b));
        assertTrue(b.containsAll(a));
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(a.size(), b.size());

        CopyOnWriteArraySet empty1 = new CopyOnWriteArraySet(Arrays.asList());
        CopyOnWriteArraySet empty2 = new CopyOnWriteArraySet(Arrays.asList());
        assertTrue(empty1.equals(empty1));
        assertTrue(empty1.equals(empty2));

        assertFalse(empty1.equals(a));
        assertFalse(a.equals(empty1));

        assertFalse(a.equals(null));
    }

    /**
     * containsAll returns true for collections with subset of elements
     */
    public void testContainsAll() {
        Collection full = populatedSet(3);
        assertTrue(full.containsAll(full));
        assertTrue(full.containsAll(Arrays.asList()));
        assertTrue(full.containsAll(Arrays.asList(one)));
        assertTrue(full.containsAll(Arrays.asList(one, two)));
        assertFalse(full.containsAll(Arrays.asList(one, two, six)));
        assertFalse(full.containsAll(Arrays.asList(six)));

        CopyOnWriteArraySet empty1 = new CopyOnWriteArraySet(Arrays.asList());
        CopyOnWriteArraySet empty2 = new CopyOnWriteArraySet(Arrays.asList());
        assertTrue(empty1.containsAll(empty2));
        assertTrue(empty1.containsAll(empty1));
        assertFalse(empty1.containsAll(full));
        assertTrue(full.containsAll(empty1));

        try {
            full.containsAll(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * isEmpty is true when empty, else false
     */
    public void testIsEmpty() {
        assertTrue(populatedSet(0).isEmpty());
        assertFalse(populatedSet(3).isEmpty());
    }

    /**
     * iterator() returns an iterator containing the elements of the
     * set in insertion order
     */
    public void testIterator() {
        Collection empty = new CopyOnWriteArraySet();
        assertFalse(empty.iterator().hasNext());
        try {
            empty.iterator().next();
            shouldThrow();
        } catch (NoSuchElementException success) {}

        Integer[] elements = new Integer[SIZE];
        for (int i = 0; i < SIZE; i++)
            elements[i] = i;
        shuffle(elements);
        Collection<Integer> full = populatedSet(elements);

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
        assertIteratorExhausted(new CopyOnWriteArraySet().iterator());
    }

    /**
     * iterator remove is unsupported
     */
    public void testIteratorRemove() {
        Collection full = populatedSet(3);
        Iterator it = full.iterator();
        it.next();
        try {
            it.remove();
            shouldThrow();
        } catch (UnsupportedOperationException success) {}
    }

    /**
     * toString holds toString of elements
     */
    public void testToString() {
        assertEquals("[]", new CopyOnWriteArraySet().toString());
        Collection full = populatedSet(3);
        String s = full.toString();
        for (int i = 0; i < 3; ++i)
            assertTrue(s.contains(String.valueOf(i)));
        assertEquals(new ArrayList(full).toString(),
                     full.toString());
    }

    /**
     * removeAll removes all elements from the given collection
     */
    public void testRemoveAll() {
        Set full = populatedSet(3);
        assertTrue(full.removeAll(Arrays.asList(one, two)));
        assertEquals(1, full.size());
        assertFalse(full.removeAll(Arrays.asList(one, two)));
        assertEquals(1, full.size());
    }

    /**
     * remove removes an element
     */
    public void testRemove() {
        Collection full = populatedSet(3);
        full.remove(one);
        assertFalse(full.contains(one));
        assertEquals(2, full.size());
    }

    /**
     * size returns the number of elements
     */
    public void testSize() {
        Collection empty = new CopyOnWriteArraySet();
        Collection full = populatedSet(3);
        assertEquals(3, full.size());
        assertEquals(0, empty.size());
    }

    /**
     * toArray() returns an Object array containing all elements from
     * the set in insertion order
     */
    public void testToArray() {
        Object[] a = new CopyOnWriteArraySet().toArray();
        assertTrue(Arrays.equals(new Object[0], a));
        assertSame(Object[].class, a.getClass());

        Integer[] elements = new Integer[SIZE];
        for (int i = 0; i < SIZE; i++)
            elements[i] = i;
        shuffle(elements);
        Collection<Integer> full = populatedSet(elements);

        assertTrue(Arrays.equals(elements, full.toArray()));
        assertSame(Object[].class, full.toArray().getClass());
    }

    /**
     * toArray(Integer array) returns an Integer array containing all
     * elements from the set in insertion order
     */
    public void testToArray2() {
        Collection empty = new CopyOnWriteArraySet();
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
        Collection<Integer> full = populatedSet(elements);

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
     * toArray throws an ArrayStoreException when the given array can
     * not store the objects inside the set
     */
    public void testToArray_ArrayStoreException() {
        CopyOnWriteArraySet c = new CopyOnWriteArraySet();
        c.add("zfasdfsdf");
        c.add("asdadasd");
        try {
            c.toArray(new Long[5]);
            shouldThrow();
        } catch (ArrayStoreException success) {}
    }

    /**
     * A deserialized/reserialized set equals original
     */
    public void testSerialization() throws Exception {
        Set x = populatedSet(SIZE);
        Set y = serialClone(x);

        assertNotSame(y, x);
        assertEquals(x.size(), y.size());
        assertEquals(x.toString(), y.toString());
        assertTrue(Arrays.equals(x.toArray(), y.toArray()));
        assertEquals(x, y);
        assertEquals(y, x);
    }

    /**
     * addAll is idempotent
     */
    public void testAddAll_idempotent() throws Exception {
        Set x = populatedSet(SIZE);
        Set y = new CopyOnWriteArraySet(x);
        y.addAll(x);
        assertEquals(x, y);
        assertEquals(y, x);
    }

}
