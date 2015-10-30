/*
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
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Written by Josh Bloch of Google Inc. and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/.
 */

package java.util;

import java.io.Serializable;
import java.util.function.Consumer;

/**
 * Resizable-array implementation of the {@link Deque} interface.  Array
 * deques have no capacity restrictions; they grow as necessary to support
 * usage.  They are not thread-safe; in the absence of external
 * synchronization, they do not support concurrent access by multiple threads.
 * Null elements are prohibited.  This class is likely to be faster than
 * {@link Stack} when used as a stack, and faster than {@link LinkedList}
 * when used as a queue.
 *
 * <p>Most {@code ArrayDeque} operations run in amortized constant time.
 * Exceptions include
 * {@link #remove(Object) remove},
 * {@link #removeFirstOccurrence removeFirstOccurrence},
 * {@link #removeLastOccurrence removeLastOccurrence},
 * {@link #contains contains},
 * {@link #iterator iterator.remove()},
 * and the bulk operations, all of which run in linear time.
 *
 * <p>The iterators returned by this class's {@link #iterator() iterator}
 * method are <em>fail-fast</em>: If the deque is modified at any time after
 * the iterator is created, in any way except through the iterator's own
 * {@code remove} method, the iterator will generally throw a {@link
 * ConcurrentModificationException}.  Thus, in the face of concurrent
 * modification, the iterator fails quickly and cleanly, rather than risking
 * arbitrary, non-deterministic behavior at an undetermined time in the
 * future.
 *
 * <p>Note that the fail-fast behavior of an iterator cannot be guaranteed
 * as it is, generally speaking, impossible to make any hard guarantees in the
 * presence of unsynchronized concurrent modification.  Fail-fast iterators
 * throw {@code ConcurrentModificationException} on a best-effort basis.
 * Therefore, it would be wrong to write a program that depended on this
 * exception for its correctness: <i>the fail-fast behavior of iterators
 * should be used only to detect bugs.</i>
 *
 * <p>This class and its iterator implement all of the
 * <em>optional</em> methods of the {@link Collection} and {@link
 * Iterator} interfaces.
 *
 * <p>This class is a member of the
 * <a href="{@docRoot}/../technotes/guides/collections/index.html">
 * Java Collections Framework</a>.
 *
 * @author  Josh Bloch and Doug Lea
 * @since   1.6
 * @param <E> the type of elements held in this deque
 */
public class ArrayDeque<E> extends AbstractCollection<E>
                           implements Deque<E>, Cloneable, Serializable
{
    /**
     * The array in which the elements of the deque are stored.
     * The capacity of the deque is the length of this array, which is
     * always a power of two. The array is never allowed to become
     * full, except transiently within an addX method where it is
     * resized (see doubleCapacity) immediately upon becoming full,
     * thus avoiding head and tail wrapping around to equal each
     * other.  We also guarantee that all array cells not holding
     * deque elements are always null.
     */
    transient Object[] elements; // non-private to simplify nested class access

    /**
     * The index of the element at the head of the deque (which is the
     * element that would be removed by remove() or pop()); or an
     * arbitrary number equal to tail if the deque is empty.
     */
    transient int head;

    /**
     * The index at which the next element would be added to the tail
     * of the deque (via addLast(E), add(E), or push(E)).
     */
    transient int tail;

    /**
     * The minimum capacity that we'll use for a newly created deque.
     * Must be a power of 2.
     */
    private static final int MIN_INITIAL_CAPACITY = 8;

    // ******  Array allocation and resizing utilities ******

    /**
     * Allocates empty array to hold the given number of elements.
     *
     * @param numElements  the number of elements to hold
     */
    private void allocateElements(int numElements) {
        int initialCapacity = MIN_INITIAL_CAPACITY;
        // Find the best power of two to hold elements.
        // Tests "<=" because arrays aren't kept full.
        if (numElements >= initialCapacity) {
            initialCapacity = numElements;
            initialCapacity |= (initialCapacity >>>  1);
            initialCapacity |= (initialCapacity >>>  2);
            initialCapacity |= (initialCapacity >>>  4);
            initialCapacity |= (initialCapacity >>>  8);
            initialCapacity |= (initialCapacity >>> 16);
            initialCapacity++;

            if (initialCapacity < 0)    // Too many elements, must back off
                initialCapacity >>>= 1; // Good luck allocating 2^30 elements
        }
        elements = new Object[initialCapacity];
    }

    /**
     * Doubles the capacity of this deque.  Call only when full, i.e.,
     * when head and tail have wrapped around to become equal.
     */
    private void doubleCapacity() {
        assert head == tail;
        int p = head;
        int n = elements.length;
        int r = n - p; // number of elements to the right of p
        int newCapacity = n << 1;
        if (newCapacity < 0)
            throw new IllegalStateException("Sorry, deque too big");
        Object[] a = new Object[newCapacity];
        System.arraycopy(elements, p, a, 0, r);
        System.arraycopy(elements, 0, a, r, p);
        elements = a;
        head = 0;
        tail = n;
    }

    /**
     * Constructs an empty array deque with an initial capacity
     * sufficient to hold 16 elements.
     */
    public ArrayDeque() {
        elements = new Object[16];
    }

    /**
     * Constructs an empty array deque with an initial capacity
     * sufficient to hold the specified number of elements.
     *
     * @param numElements  lower bound on initial capacity of the deque
     */
    public ArrayDeque(int numElements) {
        allocateElements(numElements);
    }

    /**
     * Constructs a deque containing the elements of the specified
     * collection, in the order they are returned by the collection's
     * iterator.  (The first element returned by the collection's
     * iterator becomes the first element, or <i>front</i> of the
     * deque.)
     *
     * @param c the collection whose elements are to be placed into the deque
     * @throws NullPointerException if the specified collection is null
     */
    public ArrayDeque(Collection<? extends E> c) {
        allocateElements(c.size());
        addAll(c);
    }

    // The main insertion and extraction methods are addFirst,
    // addLast, pollFirst, pollLast. The other methods are defined in
    // terms of these.

    /**
     * Inserts the specified element at the front of this deque.
     *
     * @param e the element to add
     * @throws NullPointerException if the specified element is null
     */
    public void addFirst(E e) {
        if (e == null)
            throw new NullPointerException();
        elements[head = (head - 1) & (elements.length - 1)] = e;
        if (head == tail)
            doubleCapacity();
    }

    /**
     * Inserts the specified element at the end of this deque.
     *
     * <p>This method is equivalent to {@link #add}.
     *
     * @param e the element to add
     * @throws NullPointerException if the specified element is null
     */
    public void addLast(E e) {
        if (e == null)
            throw new NullPointerException();
        elements[tail] = e;
        if ( (tail = (tail + 1) & (elements.length - 1)) == head)
            doubleCapacity();
    }

    /**
     * Inserts the specified element at the front of this deque.
     *
     * @param e the element to add
     * @return {@code true} (as specified by {@link Deque#offerFirst})
     * @throws NullPointerException if the specified element is null
     */
    public boolean offerFirst(E e) {
        addFirst(e);
        return true;
    }

    /**
     * Inserts the specified element at the end of this deque.
     *
     * @param e the element to add
     * @return {@code true} (as specified by {@link Deque#offerLast})
     * @throws NullPointerException if the specified element is null
     */
    public boolean offerLast(E e) {
        addLast(e);
        return true;
    }

    /**
     * @throws NoSuchElementException {@inheritDoc}
     */
    public E removeFirst() {
        E x = pollFirst();
        if (x == null)
            throw new NoSuchElementException();
        return x;
    }

    /**
     * @throws NoSuchElementException {@inheritDoc}
     */
    public E removeLast() {
        E x = pollLast();
        if (x == null)
            throw new NoSuchElementException();
        return x;
    }

    public E pollFirst() {
        final Object[] elements = this.elements;
        final int h = head;
        @SuppressWarnings("unchecked")
        E result = (E) elements[h];
        // Element is null if deque empty
        if (result != null) {
            elements[h] = null; // Must null out slot
            head = (h + 1) & (elements.length - 1);
        }
        return result;
    }

    public E pollLast() {
        final Object[] elements = this.elements;
        final int t = (tail - 1) & (elements.length - 1);
        @SuppressWarnings("unchecked")
        E result = (E) elements[t];
        if (result != null) {
            elements[t] = null;
            tail = t;
        }
        return result;
    }

    /**
     * @throws NoSuchElementException {@inheritDoc}
     */
    public E getFirst() {
        @SuppressWarnings("unchecked")
        E result = (E) elements[head];
        if (result == null)
            throw new NoSuchElementException();
        return result;
    }

    /**
     * @throws NoSuchElementException {@inheritDoc}
     */
    public E getLast() {
        @SuppressWarnings("unchecked")
        E result = (E) elements[(tail - 1) & (elements.length - 1)];
        if (result == null)
            throw new NoSuchElementException();
        return result;
    }

    @SuppressWarnings("unchecked")
    public E peekFirst() {
        // elements[head] is null if deque empty
        return (E) elements[head];
    }

    @SuppressWarnings("unchecked")
    public E peekLast() {
        return (E) elements[(tail - 1) & (elements.length - 1)];
    }

    /**
     * Removes the first occurrence of the specified element in this
     * deque (when traversing the deque from head to tail).
     * If the deque does not contain the element, it is unchanged.
     * More formally, removes the first element {@code e} such that
     * {@code o.equals(e)} (if such an element exists).
     * Returns {@code true} if this deque contained the specified element
     * (or equivalently, if this deque changed as a result of the call).
     *
     * @param o element to be removed from this deque, if present
     * @return {@code true} if the deque contained the specified element
     */
    public boolean removeFirstOccurrence(Object o) {
        if (o != null) {
            int mask = elements.length - 1;
            int i = head;
            for (Object x; (x = elements[i]) != null; i = (i + 1) & mask) {
                if (o.equals(x)) {
                    delete(i);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Removes the last occurrence of the specified element in this
     * deque (when traversing the deque from head to tail).
     * If the deque does not contain the element, it is unchanged.
     * More formally, removes the last element {@code e} such that
     * {@code o.equals(e)} (if such an element exists).
     * Returns {@code true} if this deque contained the specified element
     * (or equivalently, if this deque changed as a result of the call).
     *
     * @param o element to be removed from this deque, if present
     * @return {@code true} if the deque contained the specified element
     */
    public boolean removeLastOccurrence(Object o) {
        if (o != null) {
            int mask = elements.length - 1;
            int i = (tail - 1) & mask;
            for (Object x; (x = elements[i]) != null; i = (i - 1) & mask) {
                if (o.equals(x)) {
                    delete(i);
                    return true;
                }
            }
        }
        return false;
    }

    // *** Queue methods ***

    /**
     * Inserts the specified element at the end of this deque.
     *
     * <p>This method is equivalent to {@link #addLast}.
     *
     * @param e the element to add
     * @return {@code true} (as specified by {@link Collection#add})
     * @throws NullPointerException if the specified element is null
     */
    public boolean add(E e) {
        addLast(e);
        return true;
    }

    /**
     * Inserts the specified element at the end of this deque.
     *
     * <p>This method is equivalent to {@link #offerLast}.
     *
     * @param e the element to add
     * @return {@code true} (as specified by {@link Queue#offer})
     * @throws NullPointerException if the specified element is null
     */
    public boolean offer(E e) {
        return offerLast(e);
    }

    /**
     * Retrieves and removes the head of the queue represented by this deque.
     *
     * This method differs from {@link #poll poll} only in that it throws an
     * exception if this deque is empty.
     *
     * <p>This method is equivalent to {@link #removeFirst}.
     *
     * @return the head of the queue represented by this deque
     * @throws NoSuchElementException {@inheritDoc}
     */
    public E remove() {
        return removeFirst();
    }

    /**
     * Retrieves and removes the head of the queue represented by this deque
     * (in other words, the first element of this deque), or returns
     * {@code null} if this deque is empty.
     *
     * <p>This method is equivalent to {@link #pollFirst}.
     *
     * @return the head of the queue represented by this deque, or
     *         {@code null} if this deque is empty
     */
    public E poll() {
        return pollFirst();
    }

    /**
     * Retrieves, but does not remove, the head of the queue represented by
     * this deque.  This method differs from {@link #peek peek} only in
     * that it throws an exception if this deque is empty.
     *
     * <p>This method is equivalent to {@link #getFirst}.
     *
     * @return the head of the queue represented by this deque
     * @throws NoSuchElementException {@inheritDoc}
     */
    public E element() {
        return getFirst();
    }

    /**
     * Retrieves, but does not remove, the head of the queue represented by
     * this deque, or returns {@code null} if this deque is empty.
     *
     * <p>This method is equivalent to {@link #peekFirst}.
     *
     * @return the head of the queue represented by this deque, or
     *         {@code null} if this deque is empty
     */
    public E peek() {
        return peekFirst();
    }

    // *** Stack methods ***

    /**
     * Pushes an element onto the stack represented by this deque.  In other
     * words, inserts the element at the front of this deque.
     *
     * <p>This method is equivalent to {@link #addFirst}.
     *
     * @param e the element to push
     * @throws NullPointerException if the specified element is null
     */
    public void push(E e) {
        addFirst(e);
    }

    /**
     * Pops an element from the stack represented by this deque.  In other
     * words, removes and returns the first element of this deque.
     *
     * <p>This method is equivalent to {@link #removeFirst()}.
     *
     * @return the element at the front of this deque (which is the top
     *         of the stack represented by this deque)
     * @throws NoSuchElementException {@inheritDoc}
     */
    public E pop() {
        return removeFirst();
    }

    private void checkInvariants() {
        assert elements[tail] == null;
        assert head == tail ? elements[head] == null :
            (elements[head] != null &&
             elements[(tail - 1) & (elements.length - 1)] != null);
        assert elements[(head - 1) & (elements.length - 1)] == null;
    }

    /**
     * Removes the element at the specified position in the elements array,
     * adjusting head and tail as necessary.  This can result in motion of
     * elements backwards or forwards in the array.
     *
     * <p>This method is called delete rather than remove to emphasize
     * that its semantics differ from those of {@link List#remove(int)}.
     *
     * @return true if elements moved backwards
     */
    boolean delete(int i) {
        checkInvariants();
        final Object[] elements = this.elements;
        final int mask = elements.length - 1;
        final int h = head;
        final int t = tail;
        final int front = (i - h) & mask;
        final int back  = (t - i) & mask;

        // Invariant: head <= i < tail mod circularity
        if (front >= ((t - h) & mask))
            throw new ConcurrentModificationException();

        // Optimize for least element motion
        if (front < back) {
            if (h <= i) {
                System.arraycopy(elements, h, elements, h + 1, front);
            } else { // Wrap around
                System.arraycopy(elements, 0, elements, 1, i);
                elements[0] = elements[mask];
                System.arraycopy(elements, h, elements, h + 1, mask - h);
            }
            elements[h] = null;
            head = (h + 1) & mask;
            return false;
        } else {
            if (i < t) { // Copy the null tail as well
                System.arraycopy(elements, i + 1, elements, i, back);
                tail = t - 1;
            } else { // Wrap around
                System.arraycopy(elements, i + 1, elements, i, mask - i);
                elements[mask] = elements[0];
                System.arraycopy(elements, 1, elements, 0, t);
                tail = (t - 1) & mask;
            }
            return true;
        }
    }

    // *** Collection Methods ***

    /**
     * Returns the number of elements in this deque.
     *
     * @return the number of elements in this deque
     */
    public int size() {
        return (tail - head) & (elements.length - 1);
    }

    /**
     * Returns {@code true} if this deque contains no elements.
     *
     * @return {@code true} if this deque contains no elements
     */
    public boolean isEmpty() {
        return head == tail;
    }

    /**
     * Returns an iterator over the elements in this deque.  The elements
     * will be ordered from first (head) to last (tail).  This is the same
     * order that elements would be dequeued (via successive calls to
     * {@link #remove} or popped (via successive calls to {@link #pop}).
     *
     * @return an iterator over the elements in this deque
     */
    public Iterator<E> iterator() {
        return new DeqIterator();
    }

    public Iterator<E> descendingIterator() {
        return new DescendingIterator();
    }

    private class DeqIterator implements Iterator<E> {
        /**
         * Index of element to be returned by subsequent call to next.
         */
        private int cursor = head;

        /**
         * Tail recorded at construction (also in remove), to stop
         * iterator and also to check for comodification.
         */
        private int fence = tail;

        /**
         * Index of element returned by most recent call to next.
         * Reset to -1 if element is deleted by a call to remove.
         */
        private int lastRet = -1;

        public boolean hasNext() {
            return cursor != fence;
        }

        public E next() {
            if (cursor == fence)
                throw new NoSuchElementException();
            @SuppressWarnings("unchecked")
            E result = (E) elements[cursor];
            // This check doesn't catch all possible comodifications,
            // but does catch the ones that corrupt traversal
            if (tail != fence || result == null)
                throw new ConcurrentModificationException();
            lastRet = cursor;
            cursor = (cursor + 1) & (elements.length - 1);
            return result;
        }

        public void remove() {
            if (lastRet < 0)
                throw new IllegalStateException();
            if (delete(lastRet)) { // if left-shifted, undo increment in next()
                cursor = (cursor - 1) & (elements.length - 1);
                fence = tail;
            }
            lastRet = -1;
        }

        public void forEachRemaining(Consumer<? super E> action) {
            Objects.requireNonNull(action);
            Object[] a = elements;
            int m = a.length - 1, f = fence, i = cursor;
            cursor = f;
            while (i != f) {
                @SuppressWarnings("unchecked") E e = (E)a[i];
                i = (i + 1) & m;
                if (e == null)
                    throw new ConcurrentModificationException();
                action.accept(e);
            }
        }
    }

    /**
     * This class is nearly a mirror-image of DeqIterator, using tail
     * instead of head for initial cursor, and head instead of tail
     * for fence.
     */
    private class DescendingIterator implements Iterator<E> {
        private int cursor = tail;
        private int fence = head;
        private int lastRet = -1;

        public boolean hasNext() {
            return cursor != fence;
        }

        public E next() {
            if (cursor == fence)
                throw new NoSuchElementException();
            cursor = (cursor - 1) & (elements.length - 1);
            @SuppressWarnings("unchecked")
            E result = (E) elements[cursor];
            if (head != fence || result == null)
                throw new ConcurrentModificationException();
            lastRet = cursor;
            return result;
        }

        public void remove() {
            if (lastRet < 0)
                throw new IllegalStateException();
            if (!delete(lastRet)) {
                cursor = (cursor + 1) & (elements.length - 1);
                fence = head;
            }
            lastRet = -1;
        }
    }

    /**
     * Returns {@code true} if this deque contains the specified element.
     * More formally, returns {@code true} if and only if this deque contains
     * at least one element {@code e} such that {@code o.equals(e)}.
     *
     * @param o object to be checked for containment in this deque
     * @return {@code true} if this deque contains the specified element
     */
    public boolean contains(Object o) {
        if (o != null) {
            int mask = elements.length - 1;
            int i = head;
            for (Object x; (x = elements[i]) != null; i = (i + 1) & mask) {
                if (o.equals(x))
                    return true;
            }
        }
        return false;
    }

    /**
     * Removes a single instance of the specified element from this deque.
     * If the deque does not contain the element, it is unchanged.
     * More formally, removes the first element {@code e} such that
     * {@code o.equals(e)} (if such an element exists).
     * Returns {@code true} if this deque contained the specified element
     * (or equivalently, if this deque changed as a result of the call).
     *
     * <p>This method is equivalent to {@link #removeFirstOccurrence(Object)}.
     *
     * @param o element to be removed from this deque, if present
     * @return {@code true} if this deque contained the specified element
     */
    public boolean remove(Object o) {
        return removeFirstOccurrence(o);
    }

    /**
     * Removes all of the elements from this deque.
     * The deque will be empty after this call returns.
     */
    public void clear() {
        int h = head;
        int t = tail;
        if (h != t) { // clear all cells
            head = tail = 0;
            int i = h;
            int mask = elements.length - 1;
            do {
                elements[i] = null;
                i = (i + 1) & mask;
            } while (i != t);
        }
    }

    /**
     * Returns an array containing all of the elements in this deque
     * in proper sequence (from first to last element).
     *
     * <p>The returned array will be "safe" in that no references to it are
     * maintained by this deque.  (In other words, this method must allocate
     * a new array).  The caller is thus free to modify the returned array.
     *
     * <p>This method acts as bridge between array-based and collection-based
     * APIs.
     *
     * @return an array containing all of the elements in this deque
     */
    public Object[] toArray() {
        final int head = this.head;
        final int tail = this.tail;
        boolean wrap = (tail < head);
        int end = wrap ? tail + elements.length : tail;
        Object[] a = Arrays.copyOfRange(elements, head, end);
        if (wrap)
            System.arraycopy(elements, 0, a, elements.length - head, tail);
        return a;
    }

    /**
     * Returns an array containing all of the elements in this deque in
     * proper sequence (from first to last element); the runtime type of the
     * returned array is that of the specified array.  If the deque fits in
     * the specified array, it is returned therein.  Otherwise, a new array
     * is allocated with the runtime type of the specified array and the
     * size of this deque.
     *
     * <p>If this deque fits in the specified array with room to spare
     * (i.e., the array has more elements than this deque), the element in
     * the array immediately following the end of the deque is set to
     * {@code null}.
     *
     * <p>Like the {@link #toArray()} method, this method acts as bridge between
     * array-based and collection-based APIs.  Further, this method allows
     * precise control over the runtime type of the output array, and may,
     * under certain circumstances, be used to save allocation costs.
     *
     * <p>Suppose {@code x} is a deque known to contain only strings.
     * The following code can be used to dump the deque into a newly
     * allocated array of {@code String}:
     *
     * <pre> {@code String[] y = x.toArray(new String[0]);}</pre>
     *
     * Note that {@code toArray(new Object[0])} is identical in function to
     * {@code toArray()}.
     *
     * @param a the array into which the elements of the deque are to
     *          be stored, if it is big enough; otherwise, a new array of the
     *          same runtime type is allocated for this purpose
     * @return an array containing all of the elements in this deque
     * @throws ArrayStoreException if the runtime type of the specified array
     *         is not a supertype of the runtime type of every element in
     *         this deque
     * @throws NullPointerException if the specified array is null
     */
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        final int head = this.head;
        final int tail = this.tail;
        boolean wrap = (tail < head);
        int size = (tail - head) + (wrap ? elements.length : 0);
        int firstLeg = size - (wrap ? tail : 0);
        int len = a.length;
        if (size > len) {
            a = (T[]) Arrays.copyOfRange(elements, head, head + size,
                                         a.getClass());
        } else {
            System.arraycopy(elements, head, a, 0, firstLeg);
            if (size < len)
                a[size] = null;
        }
        if (wrap)
            System.arraycopy(elements, 0, a, firstLeg, tail);
        return a;
    }

    // *** Object methods ***

    /**
     * Returns a copy of this deque.
     *
     * @return a copy of this deque
     */
    public ArrayDeque<E> clone() {
        try {
            @SuppressWarnings("unchecked")
            ArrayDeque<E> result = (ArrayDeque<E>) super.clone();
            result.elements = Arrays.copyOf(elements, elements.length);
            return result;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }

    private static final long serialVersionUID = 2340985798034038923L;

    /**
     * Saves this deque to a stream (that is, serializes it).
     *
     * @param s the stream
     * @throws java.io.IOException if an I/O error occurs
     * @serialData The current size ({@code int}) of the deque,
     * followed by all of its elements (each an object reference) in
     * first-to-last order.
     */
    private void writeObject(java.io.ObjectOutputStream s)
            throws java.io.IOException {
        s.defaultWriteObject();

        // Write out size
        s.writeInt(size());

        // Write out elements in order.
        int mask = elements.length - 1;
        for (int i = head; i != tail; i = (i + 1) & mask)
            s.writeObject(elements[i]);
    }

    /**
     * Reconstitutes this deque from a stream (that is, deserializes it).
     * @param s the stream
     * @throws ClassNotFoundException if the class of a serialized object
     *         could not be found
     * @throws java.io.IOException if an I/O error occurs
     */
    private void readObject(java.io.ObjectInputStream s)
            throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();

        // Read in size and allocate array
        int size = s.readInt();
        allocateElements(size);
        head = 0;
        tail = size;

        // Read in all elements in the proper order.
        for (int i = 0; i < size; i++)
            elements[i] = s.readObject();
    }

    /**
     * Creates a <em><a href="Spliterator.html#binding">late-binding</a></em>
     * and <em>fail-fast</em> {@link Spliterator} over the elements in this
     * deque.
     *
     * <p>The {@code Spliterator} reports {@link Spliterator#SIZED},
     * {@link Spliterator#SUBSIZED}, {@link Spliterator#ORDERED}, and
     * {@link Spliterator#NONNULL}.  Overriding implementations should document
     * the reporting of additional characteristic values.
     *
     * @return a {@code Spliterator} over the elements in this deque
     * @since 1.8
     */
    public Spliterator<E> spliterator() {
        return new DeqSpliterator<>(this, -1, -1);
    }

    static final class DeqSpliterator<E> implements Spliterator<E> {
        private final ArrayDeque<E> deq;
        private int fence;  // -1 until first use
        private int index;  // current index, modified on traverse/split

        /** Creates new spliterator covering the given array and range. */
        DeqSpliterator(ArrayDeque<E> deq, int origin, int fence) {
            this.deq = deq;
            this.index = origin;
            this.fence = fence;
        }

        private int getFence() { // force initialization
            int t;
            if ((t = fence) < 0) {
                t = fence = deq.tail;
                index = deq.head;
            }
            return t;
        }

        public DeqSpliterator<E> trySplit() {
            int t = getFence(), h = index, n = deq.elements.length;
            if (h != t && ((h + 1) & (n - 1)) != t) {
                if (h > t)
                    t += n;
                int m = ((h + t) >>> 1) & (n - 1);
                return new DeqSpliterator<E>(deq, h, index = m);
            }
            return null;
        }

        public void forEachRemaining(Consumer<? super E> consumer) {
            if (consumer == null)
                throw new NullPointerException();
            Object[] a = deq.elements;
            int m = a.length - 1, f = getFence(), i = index;
            index = f;
            while (i != f) {
                @SuppressWarnings("unchecked") E e = (E)a[i];
                i = (i + 1) & m;
                if (e == null)
                    throw new ConcurrentModificationException();
                consumer.accept(e);
            }
        }

        public boolean tryAdvance(Consumer<? super E> consumer) {
            if (consumer == null)
                throw new NullPointerException();
            Object[] a = deq.elements;
            int m = a.length - 1, f = getFence(), i = index;
            if (i != f) {
                @SuppressWarnings("unchecked") E e = (E)a[i];
                index = (i + 1) & m;
                if (e == null)
                    throw new ConcurrentModificationException();
                consumer.accept(e);
                return true;
            }
            return false;
        }

        public long estimateSize() {
            int n = getFence() - index;
            if (n < 0)
                n += deq.elements.length;
            return (long) n;
        }

        @Override
        public int characteristics() {
            return Spliterator.ORDERED | Spliterator.SIZED |
                Spliterator.NONNULL | Spliterator.SUBSIZED;
        }
    }

}
