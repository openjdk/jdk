/*
 * Copyright (c) 2003, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group.  Adapted and released, under explicit permission,
 * from JDK ArrayList.java which carries the following copyright:
 *
 * Copyright 1997 by Sun Microsystems, Inc.,
 * 901 San Antonio Road, Palo Alto, California, 94303, U.S.A.
 * All rights reserved.
 */

package java.util.concurrent;
import java.util.*;
import java.util.concurrent.locks.*;
import sun.misc.Unsafe;

/**
 * A thread-safe variant of {@link java.util.ArrayList} in which all mutative
 * operations (<tt>add</tt>, <tt>set</tt>, and so on) are implemented by
 * making a fresh copy of the underlying array.
 *
 * <p> This is ordinarily too costly, but may be <em>more</em> efficient
 * than alternatives when traversal operations vastly outnumber
 * mutations, and is useful when you cannot or don't want to
 * synchronize traversals, yet need to preclude interference among
 * concurrent threads.  The "snapshot" style iterator method uses a
 * reference to the state of the array at the point that the iterator
 * was created. This array never changes during the lifetime of the
 * iterator, so interference is impossible and the iterator is
 * guaranteed not to throw <tt>ConcurrentModificationException</tt>.
 * The iterator will not reflect additions, removals, or changes to
 * the list since the iterator was created.  Element-changing
 * operations on iterators themselves (<tt>remove</tt>, <tt>set</tt>, and
 * <tt>add</tt>) are not supported. These methods throw
 * <tt>UnsupportedOperationException</tt>.
 *
 * <p>All elements are permitted, including <tt>null</tt>.
 *
 * <p>Memory consistency effects: As with other concurrent
 * collections, actions in a thread prior to placing an object into a
 * {@code CopyOnWriteArrayList}
 * <a href="package-summary.html#MemoryVisibility"><i>happen-before</i></a>
 * actions subsequent to the access or removal of that element from
 * the {@code CopyOnWriteArrayList} in another thread.
 *
 * <p>This class is a member of the
 * <a href="{@docRoot}/../technotes/guides/collections/index.html">
 * Java Collections Framework</a>.
 *
 * @since 1.5
 * @author Doug Lea
 * @param <E> the type of elements held in this collection
 */
public class CopyOnWriteArrayList<E>
    implements List<E>, RandomAccess, Cloneable, java.io.Serializable {
    private static final long serialVersionUID = 8673264195747942595L;

    /** The lock protecting all mutators */
    transient final ReentrantLock lock = new ReentrantLock();

    /** The array, accessed only via getArray/setArray. */
    private volatile transient Object[] array;

    /**
     * Gets the array.  Non-private so as to also be accessible
     * from CopyOnWriteArraySet class.
     */
    final Object[] getArray() {
        return array;
    }

    /**
     * Sets the array.
     */
    final void setArray(Object[] a) {
        array = a;
    }

    /**
     * Creates an empty list.
     */
    public CopyOnWriteArrayList() {
        setArray(new Object[0]);
    }

    /**
     * Creates a list containing the elements of the specified
     * collection, in the order they are returned by the collection's
     * iterator.
     *
     * @param c the collection of initially held elements
     * @throws NullPointerException if the specified collection is null
     */
    public CopyOnWriteArrayList(Collection<? extends E> c) {
        Object[] elements = c.toArray();
        // c.toArray might (incorrectly) not return Object[] (see 6260652)
        if (elements.getClass() != Object[].class)
            elements = Arrays.copyOf(elements, elements.length, Object[].class);
        setArray(elements);
    }

    /**
     * Creates a list holding a copy of the given array.
     *
     * @param toCopyIn the array (a copy of this array is used as the
     *        internal array)
     * @throws NullPointerException if the specified array is null
     */
    public CopyOnWriteArrayList(E[] toCopyIn) {
        setArray(Arrays.copyOf(toCopyIn, toCopyIn.length, Object[].class));
    }

    /**
     * Returns the number of elements in this list.
     *
     * @return the number of elements in this list
     */
    public int size() {
        return getArray().length;
    }

    /**
     * Returns <tt>true</tt> if this list contains no elements.
     *
     * @return <tt>true</tt> if this list contains no elements
     */
    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Test for equality, coping with nulls.
     */
    private static boolean eq(Object o1, Object o2) {
        return (o1 == null ? o2 == null : o1.equals(o2));
    }

    /**
     * static version of indexOf, to allow repeated calls without
     * needing to re-acquire array each time.
     * @param o element to search for
     * @param elements the array
     * @param index first index to search
     * @param fence one past last index to search
     * @return index of element, or -1 if absent
     */
    private static int indexOf(Object o, Object[] elements,
                               int index, int fence) {
        if (o == null) {
            for (int i = index; i < fence; i++)
                if (elements[i] == null)
                    return i;
        } else {
            for (int i = index; i < fence; i++)
                if (o.equals(elements[i]))
                    return i;
        }
        return -1;
    }

    /**
     * static version of lastIndexOf.
     * @param o element to search for
     * @param elements the array
     * @param index first index to search
     * @return index of element, or -1 if absent
     */
    private static int lastIndexOf(Object o, Object[] elements, int index) {
        if (o == null) {
            for (int i = index; i >= 0; i--)
                if (elements[i] == null)
                    return i;
        } else {
            for (int i = index; i >= 0; i--)
                if (o.equals(elements[i]))
                    return i;
        }
        return -1;
    }

    /**
     * Returns <tt>true</tt> if this list contains the specified element.
     * More formally, returns <tt>true</tt> if and only if this list contains
     * at least one element <tt>e</tt> such that
     * <tt>(o==null&nbsp;?&nbsp;e==null&nbsp;:&nbsp;o.equals(e))</tt>.
     *
     * @param o element whose presence in this list is to be tested
     * @return <tt>true</tt> if this list contains the specified element
     */
    public boolean contains(Object o) {
        Object[] elements = getArray();
        return indexOf(o, elements, 0, elements.length) >= 0;
    }

    /**
     * {@inheritDoc}
     */
    public int indexOf(Object o) {
        Object[] elements = getArray();
        return indexOf(o, elements, 0, elements.length);
    }

    /**
     * Returns the index of the first occurrence of the specified element in
     * this list, searching forwards from <tt>index</tt>, or returns -1 if
     * the element is not found.
     * More formally, returns the lowest index <tt>i</tt> such that
     * <tt>(i&nbsp;&gt;=&nbsp;index&nbsp;&amp;&amp;&nbsp;(e==null&nbsp;?&nbsp;get(i)==null&nbsp;:&nbsp;e.equals(get(i))))</tt>,
     * or -1 if there is no such index.
     *
     * @param e element to search for
     * @param index index to start searching from
     * @return the index of the first occurrence of the element in
     *         this list at position <tt>index</tt> or later in the list;
     *         <tt>-1</tt> if the element is not found.
     * @throws IndexOutOfBoundsException if the specified index is negative
     */
    public int indexOf(E e, int index) {
        Object[] elements = getArray();
        return indexOf(e, elements, index, elements.length);
    }

    /**
     * {@inheritDoc}
     */
    public int lastIndexOf(Object o) {
        Object[] elements = getArray();
        return lastIndexOf(o, elements, elements.length - 1);
    }

    /**
     * Returns the index of the last occurrence of the specified element in
     * this list, searching backwards from <tt>index</tt>, or returns -1 if
     * the element is not found.
     * More formally, returns the highest index <tt>i</tt> such that
     * <tt>(i&nbsp;&lt;=&nbsp;index&nbsp;&amp;&amp;&nbsp;(e==null&nbsp;?&nbsp;get(i)==null&nbsp;:&nbsp;e.equals(get(i))))</tt>,
     * or -1 if there is no such index.
     *
     * @param e element to search for
     * @param index index to start searching backwards from
     * @return the index of the last occurrence of the element at position
     *         less than or equal to <tt>index</tt> in this list;
     *         -1 if the element is not found.
     * @throws IndexOutOfBoundsException if the specified index is greater
     *         than or equal to the current size of this list
     */
    public int lastIndexOf(E e, int index) {
        Object[] elements = getArray();
        return lastIndexOf(e, elements, index);
    }

    /**
     * Returns a shallow copy of this list.  (The elements themselves
     * are not copied.)
     *
     * @return a clone of this list
     */
    public Object clone() {
        try {
            CopyOnWriteArrayList c = (CopyOnWriteArrayList)(super.clone());
            c.resetLock();
            return c;
        } catch (CloneNotSupportedException e) {
            // this shouldn't happen, since we are Cloneable
            throw new InternalError();
        }
    }

    /**
     * Returns an array containing all of the elements in this list
     * in proper sequence (from first to last element).
     *
     * <p>The returned array will be "safe" in that no references to it are
     * maintained by this list.  (In other words, this method must allocate
     * a new array).  The caller is thus free to modify the returned array.
     *
     * <p>This method acts as bridge between array-based and collection-based
     * APIs.
     *
     * @return an array containing all the elements in this list
     */
    public Object[] toArray() {
        Object[] elements = getArray();
        return Arrays.copyOf(elements, elements.length);
    }

    /**
     * Returns an array containing all of the elements in this list in
     * proper sequence (from first to last element); the runtime type of
     * the returned array is that of the specified array.  If the list fits
     * in the specified array, it is returned therein.  Otherwise, a new
     * array is allocated with the runtime type of the specified array and
     * the size of this list.
     *
     * <p>If this list fits in the specified array with room to spare
     * (i.e., the array has more elements than this list), the element in
     * the array immediately following the end of the list is set to
     * <tt>null</tt>.  (This is useful in determining the length of this
     * list <i>only</i> if the caller knows that this list does not contain
     * any null elements.)
     *
     * <p>Like the {@link #toArray()} method, this method acts as bridge between
     * array-based and collection-based APIs.  Further, this method allows
     * precise control over the runtime type of the output array, and may,
     * under certain circumstances, be used to save allocation costs.
     *
     * <p>Suppose <tt>x</tt> is a list known to contain only strings.
     * The following code can be used to dump the list into a newly
     * allocated array of <tt>String</tt>:
     *
     * <pre>
     *     String[] y = x.toArray(new String[0]);</pre>
     *
     * Note that <tt>toArray(new Object[0])</tt> is identical in function to
     * <tt>toArray()</tt>.
     *
     * @param a the array into which the elements of the list are to
     *          be stored, if it is big enough; otherwise, a new array of the
     *          same runtime type is allocated for this purpose.
     * @return an array containing all the elements in this list
     * @throws ArrayStoreException if the runtime type of the specified array
     *         is not a supertype of the runtime type of every element in
     *         this list
     * @throws NullPointerException if the specified array is null
     */
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T a[]) {
        Object[] elements = getArray();
        int len = elements.length;
        if (a.length < len)
            return (T[]) Arrays.copyOf(elements, len, a.getClass());
        else {
            System.arraycopy(elements, 0, a, 0, len);
            if (a.length > len)
                a[len] = null;
            return a;
        }
    }

    // Positional Access Operations

    @SuppressWarnings("unchecked")
    private E get(Object[] a, int index) {
        return (E) a[index];
    }

    /**
     * {@inheritDoc}
     *
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public E get(int index) {
        return get(getArray(), index);
    }

    /**
     * Replaces the element at the specified position in this list with the
     * specified element.
     *
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public E set(int index, E element) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            Object[] elements = getArray();
            E oldValue = get(elements, index);

            if (oldValue != element) {
                int len = elements.length;
                Object[] newElements = Arrays.copyOf(elements, len);
                newElements[index] = element;
                setArray(newElements);
            } else {
                // Not quite a no-op; ensures volatile write semantics
                setArray(elements);
            }
            return oldValue;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Appends the specified element to the end of this list.
     *
     * @param e element to be appended to this list
     * @return <tt>true</tt> (as specified by {@link Collection#add})
     */
    public boolean add(E e) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            Object[] elements = getArray();
            int len = elements.length;
            Object[] newElements = Arrays.copyOf(elements, len + 1);
            newElements[len] = e;
            setArray(newElements);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Inserts the specified element at the specified position in this
     * list. Shifts the element currently at that position (if any) and
     * any subsequent elements to the right (adds one to their indices).
     *
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public void add(int index, E element) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            Object[] elements = getArray();
            int len = elements.length;
            if (index > len || index < 0)
                throw new IndexOutOfBoundsException("Index: "+index+
                                                    ", Size: "+len);
            Object[] newElements;
            int numMoved = len - index;
            if (numMoved == 0)
                newElements = Arrays.copyOf(elements, len + 1);
            else {
                newElements = new Object[len + 1];
                System.arraycopy(elements, 0, newElements, 0, index);
                System.arraycopy(elements, index, newElements, index + 1,
                                 numMoved);
            }
            newElements[index] = element;
            setArray(newElements);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes the element at the specified position in this list.
     * Shifts any subsequent elements to the left (subtracts one from their
     * indices).  Returns the element that was removed from the list.
     *
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public E remove(int index) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            Object[] elements = getArray();
            int len = elements.length;
            E oldValue = get(elements, index);
            int numMoved = len - index - 1;
            if (numMoved == 0)
                setArray(Arrays.copyOf(elements, len - 1));
            else {
                Object[] newElements = new Object[len - 1];
                System.arraycopy(elements, 0, newElements, 0, index);
                System.arraycopy(elements, index + 1, newElements, index,
                                 numMoved);
                setArray(newElements);
            }
            return oldValue;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes the first occurrence of the specified element from this list,
     * if it is present.  If this list does not contain the element, it is
     * unchanged.  More formally, removes the element with the lowest index
     * <tt>i</tt> such that
     * <tt>(o==null&nbsp;?&nbsp;get(i)==null&nbsp;:&nbsp;o.equals(get(i)))</tt>
     * (if such an element exists).  Returns <tt>true</tt> if this list
     * contained the specified element (or equivalently, if this list
     * changed as a result of the call).
     *
     * @param o element to be removed from this list, if present
     * @return <tt>true</tt> if this list contained the specified element
     */
    public boolean remove(Object o) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            Object[] elements = getArray();
            int len = elements.length;
            if (len != 0) {
                // Copy while searching for element to remove
                // This wins in the normal case of element being present
                int newlen = len - 1;
                Object[] newElements = new Object[newlen];

                for (int i = 0; i < newlen; ++i) {
                    if (eq(o, elements[i])) {
                        // found one;  copy remaining and exit
                        for (int k = i + 1; k < len; ++k)
                            newElements[k-1] = elements[k];
                        setArray(newElements);
                        return true;
                    } else
                        newElements[i] = elements[i];
                }

                // special handling for last cell
                if (eq(o, elements[newlen])) {
                    setArray(newElements);
                    return true;
                }
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes from this list all of the elements whose index is between
     * <tt>fromIndex</tt>, inclusive, and <tt>toIndex</tt>, exclusive.
     * Shifts any succeeding elements to the left (reduces their index).
     * This call shortens the list by <tt>(toIndex - fromIndex)</tt> elements.
     * (If <tt>toIndex==fromIndex</tt>, this operation has no effect.)
     *
     * @param fromIndex index of first element to be removed
     * @param toIndex index after last element to be removed
     * @throws IndexOutOfBoundsException if fromIndex or toIndex out of range
     *         ({@code{fromIndex < 0 || toIndex > size() || toIndex < fromIndex})
     */
    private void removeRange(int fromIndex, int toIndex) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            Object[] elements = getArray();
            int len = elements.length;

            if (fromIndex < 0 || toIndex > len || toIndex < fromIndex)
                throw new IndexOutOfBoundsException();
            int newlen = len - (toIndex - fromIndex);
            int numMoved = len - toIndex;
            if (numMoved == 0)
                setArray(Arrays.copyOf(elements, newlen));
            else {
                Object[] newElements = new Object[newlen];
                System.arraycopy(elements, 0, newElements, 0, fromIndex);
                System.arraycopy(elements, toIndex, newElements,
                                 fromIndex, numMoved);
                setArray(newElements);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Append the element if not present.
     *
     * @param e element to be added to this list, if absent
     * @return <tt>true</tt> if the element was added
     */
    public boolean addIfAbsent(E e) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            // Copy while checking if already present.
            // This wins in the most common case where it is not present
            Object[] elements = getArray();
            int len = elements.length;
            Object[] newElements = new Object[len + 1];
            for (int i = 0; i < len; ++i) {
                if (eq(e, elements[i]))
                    return false; // exit, throwing away copy
                else
                    newElements[i] = elements[i];
            }
            newElements[len] = e;
            setArray(newElements);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns <tt>true</tt> if this list contains all of the elements of the
     * specified collection.
     *
     * @param c collection to be checked for containment in this list
     * @return <tt>true</tt> if this list contains all of the elements of the
     *         specified collection
     * @throws NullPointerException if the specified collection is null
     * @see #contains(Object)
     */
    public boolean containsAll(Collection<?> c) {
        Object[] elements = getArray();
        int len = elements.length;
        for (Object e : c) {
            if (indexOf(e, elements, 0, len) < 0)
                return false;
        }
        return true;
    }

    /**
     * Removes from this list all of its elements that are contained in
     * the specified collection. This is a particularly expensive operation
     * in this class because of the need for an internal temporary array.
     *
     * @param c collection containing elements to be removed from this list
     * @return <tt>true</tt> if this list changed as a result of the call
     * @throws ClassCastException if the class of an element of this list
     *         is incompatible with the specified collection
     *         (<a href="../Collection.html#optional-restrictions">optional</a>)
     * @throws NullPointerException if this list contains a null element and the
     *         specified collection does not permit null elements
     *         (<a href="../Collection.html#optional-restrictions">optional</a>),
     *         or if the specified collection is null
     * @see #remove(Object)
     */
    public boolean removeAll(Collection<?> c) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            Object[] elements = getArray();
            int len = elements.length;
            if (len != 0) {
                // temp array holds those elements we know we want to keep
                int newlen = 0;
                Object[] temp = new Object[len];
                for (int i = 0; i < len; ++i) {
                    Object element = elements[i];
                    if (!c.contains(element))
                        temp[newlen++] = element;
                }
                if (newlen != len) {
                    setArray(Arrays.copyOf(temp, newlen));
                    return true;
                }
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Retains only the elements in this list that are contained in the
     * specified collection.  In other words, removes from this list all of
     * its elements that are not contained in the specified collection.
     *
     * @param c collection containing elements to be retained in this list
     * @return <tt>true</tt> if this list changed as a result of the call
     * @throws ClassCastException if the class of an element of this list
     *         is incompatible with the specified collection
     *         (<a href="../Collection.html#optional-restrictions">optional</a>)
     * @throws NullPointerException if this list contains a null element and the
     *         specified collection does not permit null elements
     *         (<a href="../Collection.html#optional-restrictions">optional</a>),
     *         or if the specified collection is null
     * @see #remove(Object)
     */
    public boolean retainAll(Collection<?> c) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            Object[] elements = getArray();
            int len = elements.length;
            if (len != 0) {
                // temp array holds those elements we know we want to keep
                int newlen = 0;
                Object[] temp = new Object[len];
                for (int i = 0; i < len; ++i) {
                    Object element = elements[i];
                    if (c.contains(element))
                        temp[newlen++] = element;
                }
                if (newlen != len) {
                    setArray(Arrays.copyOf(temp, newlen));
                    return true;
                }
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Appends all of the elements in the specified collection that
     * are not already contained in this list, to the end of
     * this list, in the order that they are returned by the
     * specified collection's iterator.
     *
     * @param c collection containing elements to be added to this list
     * @return the number of elements added
     * @throws NullPointerException if the specified collection is null
     * @see #addIfAbsent(Object)
     */
    public int addAllAbsent(Collection<? extends E> c) {
        Object[] cs = c.toArray();
        if (cs.length == 0)
            return 0;
        Object[] uniq = new Object[cs.length];
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            Object[] elements = getArray();
            int len = elements.length;
            int added = 0;
            for (int i = 0; i < cs.length; ++i) { // scan for duplicates
                Object e = cs[i];
                if (indexOf(e, elements, 0, len) < 0 &&
                    indexOf(e, uniq, 0, added) < 0)
                    uniq[added++] = e;
            }
            if (added > 0) {
                Object[] newElements = Arrays.copyOf(elements, len + added);
                System.arraycopy(uniq, 0, newElements, len, added);
                setArray(newElements);
            }
            return added;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes all of the elements from this list.
     * The list will be empty after this call returns.
     */
    public void clear() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            setArray(new Object[0]);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Appends all of the elements in the specified collection to the end
     * of this list, in the order that they are returned by the specified
     * collection's iterator.
     *
     * @param c collection containing elements to be added to this list
     * @return <tt>true</tt> if this list changed as a result of the call
     * @throws NullPointerException if the specified collection is null
     * @see #add(Object)
     */
    public boolean addAll(Collection<? extends E> c) {
        Object[] cs = c.toArray();
        if (cs.length == 0)
            return false;
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            Object[] elements = getArray();
            int len = elements.length;
            Object[] newElements = Arrays.copyOf(elements, len + cs.length);
            System.arraycopy(cs, 0, newElements, len, cs.length);
            setArray(newElements);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Inserts all of the elements in the specified collection into this
     * list, starting at the specified position.  Shifts the element
     * currently at that position (if any) and any subsequent elements to
     * the right (increases their indices).  The new elements will appear
     * in this list in the order that they are returned by the
     * specified collection's iterator.
     *
     * @param index index at which to insert the first element
     *        from the specified collection
     * @param c collection containing elements to be added to this list
     * @return <tt>true</tt> if this list changed as a result of the call
     * @throws IndexOutOfBoundsException {@inheritDoc}
     * @throws NullPointerException if the specified collection is null
     * @see #add(int,Object)
     */
    public boolean addAll(int index, Collection<? extends E> c) {
        Object[] cs = c.toArray();
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            Object[] elements = getArray();
            int len = elements.length;
            if (index > len || index < 0)
                throw new IndexOutOfBoundsException("Index: "+index+
                                                    ", Size: "+len);
            if (cs.length == 0)
                return false;
            int numMoved = len - index;
            Object[] newElements;
            if (numMoved == 0)
                newElements = Arrays.copyOf(elements, len + cs.length);
            else {
                newElements = new Object[len + cs.length];
                System.arraycopy(elements, 0, newElements, 0, index);
                System.arraycopy(elements, index,
                                 newElements, index + cs.length,
                                 numMoved);
            }
            System.arraycopy(cs, 0, newElements, index, cs.length);
            setArray(newElements);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Saves the state of the list to a stream (that is, serializes it).
     *
     * @serialData The length of the array backing the list is emitted
     *               (int), followed by all of its elements (each an Object)
     *               in the proper order.
     * @param s the stream
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException{

        s.defaultWriteObject();

        Object[] elements = getArray();
        // Write out array length
        s.writeInt(elements.length);

        // Write out all elements in the proper order.
        for (Object element : elements)
            s.writeObject(element);
    }

    /**
     * Reconstitutes the list from a stream (that is, deserializes it).
     *
     * @param s the stream
     */
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {

        s.defaultReadObject();

        // bind to new lock
        resetLock();

        // Read in array length and allocate array
        int len = s.readInt();
        Object[] elements = new Object[len];

        // Read in all elements in the proper order.
        for (int i = 0; i < len; i++)
            elements[i] = s.readObject();
        setArray(elements);
    }

    /**
     * Returns a string representation of this list.  The string
     * representation consists of the string representations of the list's
     * elements in the order they are returned by its iterator, enclosed in
     * square brackets (<tt>"[]"</tt>).  Adjacent elements are separated by
     * the characters <tt>", "</tt> (comma and space).  Elements are
     * converted to strings as by {@link String#valueOf(Object)}.
     *
     * @return a string representation of this list
     */
    public String toString() {
        return Arrays.toString(getArray());
    }

    /**
     * Compares the specified object with this list for equality.
     * Returns {@code true} if the specified object is the same object
     * as this object, or if it is also a {@link List} and the sequence
     * of elements returned by an {@linkplain List#iterator() iterator}
     * over the specified list is the same as the sequence returned by
     * an iterator over this list.  The two sequences are considered to
     * be the same if they have the same length and corresponding
     * elements at the same position in the sequence are <em>equal</em>.
     * Two elements {@code e1} and {@code e2} are considered
     * <em>equal</em> if {@code (e1==null ? e2==null : e1.equals(e2))}.
     *
     * @param o the object to be compared for equality with this list
     * @return {@code true} if the specified object is equal to this list
     */
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof List))
            return false;

        List<?> list = (List<?>)(o);
        Iterator<?> it = list.iterator();
        Object[] elements = getArray();
        int len = elements.length;
        for (int i = 0; i < len; ++i)
            if (!it.hasNext() || !eq(elements[i], it.next()))
                return false;
        if (it.hasNext())
            return false;
        return true;
    }

    /**
     * Returns the hash code value for this list.
     *
     * <p>This implementation uses the definition in {@link List#hashCode}.
     *
     * @return the hash code value for this list
     */
    public int hashCode() {
        int hashCode = 1;
        Object[] elements = getArray();
        int len = elements.length;
        for (int i = 0; i < len; ++i) {
            Object obj = elements[i];
            hashCode = 31*hashCode + (obj==null ? 0 : obj.hashCode());
        }
        return hashCode;
    }

    /**
     * Returns an iterator over the elements in this list in proper sequence.
     *
     * <p>The returned iterator provides a snapshot of the state of the list
     * when the iterator was constructed. No synchronization is needed while
     * traversing the iterator. The iterator does <em>NOT</em> support the
     * <tt>remove</tt> method.
     *
     * @return an iterator over the elements in this list in proper sequence
     */
    public Iterator<E> iterator() {
        return new COWIterator<E>(getArray(), 0);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The returned iterator provides a snapshot of the state of the list
     * when the iterator was constructed. No synchronization is needed while
     * traversing the iterator. The iterator does <em>NOT</em> support the
     * <tt>remove</tt>, <tt>set</tt> or <tt>add</tt> methods.
     */
    public ListIterator<E> listIterator() {
        return new COWIterator<E>(getArray(), 0);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The returned iterator provides a snapshot of the state of the list
     * when the iterator was constructed. No synchronization is needed while
     * traversing the iterator. The iterator does <em>NOT</em> support the
     * <tt>remove</tt>, <tt>set</tt> or <tt>add</tt> methods.
     *
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public ListIterator<E> listIterator(final int index) {
        Object[] elements = getArray();
        int len = elements.length;
        if (index<0 || index>len)
            throw new IndexOutOfBoundsException("Index: "+index);

        return new COWIterator<E>(elements, index);
    }

    private static class COWIterator<E> implements ListIterator<E> {
        /** Snapshot of the array */
        private final Object[] snapshot;
        /** Index of element to be returned by subsequent call to next.  */
        private int cursor;

        private COWIterator(Object[] elements, int initialCursor) {
            cursor = initialCursor;
            snapshot = elements;
        }

        public boolean hasNext() {
            return cursor < snapshot.length;
        }

        public boolean hasPrevious() {
            return cursor > 0;
        }

        @SuppressWarnings("unchecked")
        public E next() {
            if (! hasNext())
                throw new NoSuchElementException();
            return (E) snapshot[cursor++];
        }

        @SuppressWarnings("unchecked")
        public E previous() {
            if (! hasPrevious())
                throw new NoSuchElementException();
            return (E) snapshot[--cursor];
        }

        public int nextIndex() {
            return cursor;
        }

        public int previousIndex() {
            return cursor-1;
        }

        /**
         * Not supported. Always throws UnsupportedOperationException.
         * @throws UnsupportedOperationException always; <tt>remove</tt>
         *         is not supported by this iterator.
         */
        public void remove() {
            throw new UnsupportedOperationException();
        }

        /**
         * Not supported. Always throws UnsupportedOperationException.
         * @throws UnsupportedOperationException always; <tt>set</tt>
         *         is not supported by this iterator.
         */
        public void set(E e) {
            throw new UnsupportedOperationException();
        }

        /**
         * Not supported. Always throws UnsupportedOperationException.
         * @throws UnsupportedOperationException always; <tt>add</tt>
         *         is not supported by this iterator.
         */
        public void add(E e) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Returns a view of the portion of this list between
     * <tt>fromIndex</tt>, inclusive, and <tt>toIndex</tt>, exclusive.
     * The returned list is backed by this list, so changes in the
     * returned list are reflected in this list.
     *
     * <p>The semantics of the list returned by this method become
     * undefined if the backing list (i.e., this list) is modified in
     * any way other than via the returned list.
     *
     * @param fromIndex low endpoint (inclusive) of the subList
     * @param toIndex high endpoint (exclusive) of the subList
     * @return a view of the specified range within this list
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public List<E> subList(int fromIndex, int toIndex) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            Object[] elements = getArray();
            int len = elements.length;
            if (fromIndex < 0 || toIndex > len || fromIndex > toIndex)
                throw new IndexOutOfBoundsException();
            return new COWSubList<E>(this, fromIndex, toIndex);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sublist for CopyOnWriteArrayList.
     * This class extends AbstractList merely for convenience, to
     * avoid having to define addAll, etc. This doesn't hurt, but
     * is wasteful.  This class does not need or use modCount
     * mechanics in AbstractList, but does need to check for
     * concurrent modification using similar mechanics.  On each
     * operation, the array that we expect the backing list to use
     * is checked and updated.  Since we do this for all of the
     * base operations invoked by those defined in AbstractList,
     * all is well.  While inefficient, this is not worth
     * improving.  The kinds of list operations inherited from
     * AbstractList are already so slow on COW sublists that
     * adding a bit more space/time doesn't seem even noticeable.
     */
    private static class COWSubList<E>
        extends AbstractList<E>
        implements RandomAccess
    {
        private final CopyOnWriteArrayList<E> l;
        private final int offset;
        private int size;
        private Object[] expectedArray;

        // only call this holding l's lock
        COWSubList(CopyOnWriteArrayList<E> list,
                   int fromIndex, int toIndex) {
            l = list;
            expectedArray = l.getArray();
            offset = fromIndex;
            size = toIndex - fromIndex;
        }

        // only call this holding l's lock
        private void checkForComodification() {
            if (l.getArray() != expectedArray)
                throw new ConcurrentModificationException();
        }

        // only call this holding l's lock
        private void rangeCheck(int index) {
            if (index<0 || index>=size)
                throw new IndexOutOfBoundsException("Index: "+index+
                                                    ",Size: "+size);
        }

        public E set(int index, E element) {
            final ReentrantLock lock = l.lock;
            lock.lock();
            try {
                rangeCheck(index);
                checkForComodification();
                E x = l.set(index+offset, element);
                expectedArray = l.getArray();
                return x;
            } finally {
                lock.unlock();
            }
        }

        public E get(int index) {
            final ReentrantLock lock = l.lock;
            lock.lock();
            try {
                rangeCheck(index);
                checkForComodification();
                return l.get(index+offset);
            } finally {
                lock.unlock();
            }
        }

        public int size() {
            final ReentrantLock lock = l.lock;
            lock.lock();
            try {
                checkForComodification();
                return size;
            } finally {
                lock.unlock();
            }
        }

        public void add(int index, E element) {
            final ReentrantLock lock = l.lock;
            lock.lock();
            try {
                checkForComodification();
                if (index<0 || index>size)
                    throw new IndexOutOfBoundsException();
                l.add(index+offset, element);
                expectedArray = l.getArray();
                size++;
            } finally {
                lock.unlock();
            }
        }

        public void clear() {
            final ReentrantLock lock = l.lock;
            lock.lock();
            try {
                checkForComodification();
                l.removeRange(offset, offset+size);
                expectedArray = l.getArray();
                size = 0;
            } finally {
                lock.unlock();
            }
        }

        public E remove(int index) {
            final ReentrantLock lock = l.lock;
            lock.lock();
            try {
                rangeCheck(index);
                checkForComodification();
                E result = l.remove(index+offset);
                expectedArray = l.getArray();
                size--;
                return result;
            } finally {
                lock.unlock();
            }
        }

        public boolean remove(Object o) {
            int index = indexOf(o);
            if (index == -1)
                return false;
            remove(index);
            return true;
        }

        public Iterator<E> iterator() {
            final ReentrantLock lock = l.lock;
            lock.lock();
            try {
                checkForComodification();
                return new COWSubListIterator<E>(l, 0, offset, size);
            } finally {
                lock.unlock();
            }
        }

        public ListIterator<E> listIterator(final int index) {
            final ReentrantLock lock = l.lock;
            lock.lock();
            try {
                checkForComodification();
                if (index<0 || index>size)
                    throw new IndexOutOfBoundsException("Index: "+index+
                                                        ", Size: "+size);
                return new COWSubListIterator<E>(l, index, offset, size);
            } finally {
                lock.unlock();
            }
        }

        public List<E> subList(int fromIndex, int toIndex) {
            final ReentrantLock lock = l.lock;
            lock.lock();
            try {
                checkForComodification();
                if (fromIndex<0 || toIndex>size)
                    throw new IndexOutOfBoundsException();
                return new COWSubList<E>(l, fromIndex + offset,
                                         toIndex + offset);
            } finally {
                lock.unlock();
            }
        }

    }


    private static class COWSubListIterator<E> implements ListIterator<E> {
        private final ListIterator<E> i;
        private final int index;
        private final int offset;
        private final int size;

        COWSubListIterator(List<E> l, int index, int offset,
                           int size) {
            this.index = index;
            this.offset = offset;
            this.size = size;
            i = l.listIterator(index+offset);
        }

        public boolean hasNext() {
            return nextIndex() < size;
        }

        public E next() {
            if (hasNext())
                return i.next();
            else
                throw new NoSuchElementException();
        }

        public boolean hasPrevious() {
            return previousIndex() >= 0;
        }

        public E previous() {
            if (hasPrevious())
                return i.previous();
            else
                throw new NoSuchElementException();
        }

        public int nextIndex() {
            return i.nextIndex() - offset;
        }

        public int previousIndex() {
            return i.previousIndex() - offset;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }

        public void set(E e) {
            throw new UnsupportedOperationException();
        }

        public void add(E e) {
            throw new UnsupportedOperationException();
        }
    }

    // Support for resetting lock while deserializing
    private void resetLock() {
        UNSAFE.putObjectVolatile(this, lockOffset, new ReentrantLock());
    }
    private static final sun.misc.Unsafe UNSAFE;
    private static final long lockOffset;
    static {
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class k = CopyOnWriteArrayList.class;
            lockOffset = UNSAFE.objectFieldOffset
                (k.getDeclaredField("lock"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }
}
