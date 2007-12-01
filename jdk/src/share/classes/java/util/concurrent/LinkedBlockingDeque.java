/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package java.util.concurrent;
import java.util.*;
import java.util.concurrent.locks.*;

/**
 * An optionally-bounded {@linkplain BlockingDeque blocking deque} based on
 * linked nodes.
 *
 * <p> The optional capacity bound constructor argument serves as a
 * way to prevent excessive expansion. The capacity, if unspecified,
 * is equal to {@link Integer#MAX_VALUE}.  Linked nodes are
 * dynamically created upon each insertion unless this would bring the
 * deque above capacity.
 *
 * <p>Most operations run in constant time (ignoring time spent
 * blocking).  Exceptions include {@link #remove(Object) remove},
 * {@link #removeFirstOccurrence removeFirstOccurrence}, {@link
 * #removeLastOccurrence removeLastOccurrence}, {@link #contains
 * contains}, {@link #iterator iterator.remove()}, and the bulk
 * operations, all of which run in linear time.
 *
 * <p>This class and its iterator implement all of the
 * <em>optional</em> methods of the {@link Collection} and {@link
 * Iterator} interfaces.
 *
 * <p>This class is a member of the
 * <a href="{@docRoot}/../technotes/guides/collections/index.html">
 * Java Collections Framework</a>.
 *
 * @since 1.6
 * @author  Doug Lea
 * @param <E> the type of elements held in this collection
 */
public class LinkedBlockingDeque<E>
    extends AbstractQueue<E>
    implements BlockingDeque<E>,  java.io.Serializable {

    /*
     * Implemented as a simple doubly-linked list protected by a
     * single lock and using conditions to manage blocking.
     */

    /*
     * We have "diamond" multiple interface/abstract class inheritance
     * here, and that introduces ambiguities. Often we want the
     * BlockingDeque javadoc combined with the AbstractQueue
     * implementation, so a lot of method specs are duplicated here.
     */

    private static final long serialVersionUID = -387911632671998426L;

    /** Doubly-linked list node class */
    static final class Node<E> {
        E item;
        Node<E> prev;
        Node<E> next;
        Node(E x, Node<E> p, Node<E> n) {
            item = x;
            prev = p;
            next = n;
        }
    }

    /** Pointer to first node */
    private transient Node<E> first;
    /** Pointer to last node */
    private transient Node<E> last;
    /** Number of items in the deque */
    private transient int count;
    /** Maximum number of items in the deque */
    private final int capacity;
    /** Main lock guarding all access */
    private final ReentrantLock lock = new ReentrantLock();
    /** Condition for waiting takes */
    private final Condition notEmpty = lock.newCondition();
    /** Condition for waiting puts */
    private final Condition notFull = lock.newCondition();

    /**
     * Creates a <tt>LinkedBlockingDeque</tt> with a capacity of
     * {@link Integer#MAX_VALUE}.
     */
    public LinkedBlockingDeque() {
        this(Integer.MAX_VALUE);
    }

    /**
     * Creates a <tt>LinkedBlockingDeque</tt> with the given (fixed) capacity.
     *
     * @param capacity the capacity of this deque
     * @throws IllegalArgumentException if <tt>capacity</tt> is less than 1
     */
    public LinkedBlockingDeque(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException();
        this.capacity = capacity;
    }

    /**
     * Creates a <tt>LinkedBlockingDeque</tt> with a capacity of
     * {@link Integer#MAX_VALUE}, initially containing the elements of
     * the given collection, added in traversal order of the
     * collection's iterator.
     *
     * @param c the collection of elements to initially contain
     * @throws NullPointerException if the specified collection or any
     *         of its elements are null
     */
    public LinkedBlockingDeque(Collection<? extends E> c) {
        this(Integer.MAX_VALUE);
        for (E e : c)
            add(e);
    }


    // Basic linking and unlinking operations, called only while holding lock

    /**
     * Links e as first element, or returns false if full.
     */
    private boolean linkFirst(E e) {
        if (count >= capacity)
            return false;
        ++count;
        Node<E> f = first;
        Node<E> x = new Node<E>(e, null, f);
        first = x;
        if (last == null)
            last = x;
        else
            f.prev = x;
        notEmpty.signal();
        return true;
    }

    /**
     * Links e as last element, or returns false if full.
     */
    private boolean linkLast(E e) {
        if (count >= capacity)
            return false;
        ++count;
        Node<E> l = last;
        Node<E> x = new Node<E>(e, l, null);
        last = x;
        if (first == null)
            first = x;
        else
            l.next = x;
        notEmpty.signal();
        return true;
    }

    /**
     * Removes and returns first element, or null if empty.
     */
    private E unlinkFirst() {
        Node<E> f = first;
        if (f == null)
            return null;
        Node<E> n = f.next;
        first = n;
        if (n == null)
            last = null;
        else
            n.prev = null;
        --count;
        notFull.signal();
        return f.item;
    }

    /**
     * Removes and returns last element, or null if empty.
     */
    private E unlinkLast() {
        Node<E> l = last;
        if (l == null)
            return null;
        Node<E> p = l.prev;
        last = p;
        if (p == null)
            first = null;
        else
            p.next = null;
        --count;
        notFull.signal();
        return l.item;
    }

    /**
     * Unlink e
     */
    private void unlink(Node<E> x) {
        Node<E> p = x.prev;
        Node<E> n = x.next;
        if (p == null) {
            if (n == null)
                first = last = null;
            else {
                n.prev = null;
                first = n;
            }
        } else if (n == null) {
            p.next = null;
            last = p;
        } else {
            p.next = n;
            n.prev = p;
        }
        --count;
        notFull.signalAll();
    }

    // BlockingDeque methods

    /**
     * @throws IllegalStateException {@inheritDoc}
     * @throws NullPointerException  {@inheritDoc}
     */
    public void addFirst(E e) {
        if (!offerFirst(e))
            throw new IllegalStateException("Deque full");
    }

    /**
     * @throws IllegalStateException {@inheritDoc}
     * @throws NullPointerException  {@inheritDoc}
     */
    public void addLast(E e) {
        if (!offerLast(e))
            throw new IllegalStateException("Deque full");
    }

    /**
     * @throws NullPointerException {@inheritDoc}
     */
    public boolean offerFirst(E e) {
        if (e == null) throw new NullPointerException();
        lock.lock();
        try {
            return linkFirst(e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * @throws NullPointerException {@inheritDoc}
     */
    public boolean offerLast(E e) {
        if (e == null) throw new NullPointerException();
        lock.lock();
        try {
            return linkLast(e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * @throws NullPointerException {@inheritDoc}
     * @throws InterruptedException {@inheritDoc}
     */
    public void putFirst(E e) throws InterruptedException {
        if (e == null) throw new NullPointerException();
        lock.lock();
        try {
            while (!linkFirst(e))
                notFull.await();
        } finally {
            lock.unlock();
        }
    }

    /**
     * @throws NullPointerException {@inheritDoc}
     * @throws InterruptedException {@inheritDoc}
     */
    public void putLast(E e) throws InterruptedException {
        if (e == null) throw new NullPointerException();
        lock.lock();
        try {
            while (!linkLast(e))
                notFull.await();
        } finally {
            lock.unlock();
        }
    }

    /**
     * @throws NullPointerException {@inheritDoc}
     * @throws InterruptedException {@inheritDoc}
     */
    public boolean offerFirst(E e, long timeout, TimeUnit unit)
        throws InterruptedException {
        if (e == null) throw new NullPointerException();
        long nanos = unit.toNanos(timeout);
        lock.lockInterruptibly();
        try {
            for (;;) {
                if (linkFirst(e))
                    return true;
                if (nanos <= 0)
                    return false;
                nanos = notFull.awaitNanos(nanos);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * @throws NullPointerException {@inheritDoc}
     * @throws InterruptedException {@inheritDoc}
     */
    public boolean offerLast(E e, long timeout, TimeUnit unit)
        throws InterruptedException {
        if (e == null) throw new NullPointerException();
        long nanos = unit.toNanos(timeout);
        lock.lockInterruptibly();
        try {
            for (;;) {
                if (linkLast(e))
                    return true;
                if (nanos <= 0)
                    return false;
                nanos = notFull.awaitNanos(nanos);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * @throws NoSuchElementException {@inheritDoc}
     */
    public E removeFirst() {
        E x = pollFirst();
        if (x == null) throw new NoSuchElementException();
        return x;
    }

    /**
     * @throws NoSuchElementException {@inheritDoc}
     */
    public E removeLast() {
        E x = pollLast();
        if (x == null) throw new NoSuchElementException();
        return x;
    }

    public E pollFirst() {
        lock.lock();
        try {
            return unlinkFirst();
        } finally {
            lock.unlock();
        }
    }

    public E pollLast() {
        lock.lock();
        try {
            return unlinkLast();
        } finally {
            lock.unlock();
        }
    }

    public E takeFirst() throws InterruptedException {
        lock.lock();
        try {
            E x;
            while ( (x = unlinkFirst()) == null)
                notEmpty.await();
            return x;
        } finally {
            lock.unlock();
        }
    }

    public E takeLast() throws InterruptedException {
        lock.lock();
        try {
            E x;
            while ( (x = unlinkLast()) == null)
                notEmpty.await();
            return x;
        } finally {
            lock.unlock();
        }
    }

    public E pollFirst(long timeout, TimeUnit unit)
        throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        lock.lockInterruptibly();
        try {
            for (;;) {
                E x = unlinkFirst();
                if (x != null)
                    return x;
                if (nanos <= 0)
                    return null;
                nanos = notEmpty.awaitNanos(nanos);
            }
        } finally {
            lock.unlock();
        }
    }

    public E pollLast(long timeout, TimeUnit unit)
        throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        lock.lockInterruptibly();
        try {
            for (;;) {
                E x = unlinkLast();
                if (x != null)
                    return x;
                if (nanos <= 0)
                    return null;
                nanos = notEmpty.awaitNanos(nanos);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * @throws NoSuchElementException {@inheritDoc}
     */
    public E getFirst() {
        E x = peekFirst();
        if (x == null) throw new NoSuchElementException();
        return x;
    }

    /**
     * @throws NoSuchElementException {@inheritDoc}
     */
    public E getLast() {
        E x = peekLast();
        if (x == null) throw new NoSuchElementException();
        return x;
    }

    public E peekFirst() {
        lock.lock();
        try {
            return (first == null) ? null : first.item;
        } finally {
            lock.unlock();
        }
    }

    public E peekLast() {
        lock.lock();
        try {
            return (last == null) ? null : last.item;
        } finally {
            lock.unlock();
        }
    }

    public boolean removeFirstOccurrence(Object o) {
        if (o == null) return false;
        lock.lock();
        try {
            for (Node<E> p = first; p != null; p = p.next) {
                if (o.equals(p.item)) {
                    unlink(p);
                    return true;
                }
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    public boolean removeLastOccurrence(Object o) {
        if (o == null) return false;
        lock.lock();
        try {
            for (Node<E> p = last; p != null; p = p.prev) {
                if (o.equals(p.item)) {
                    unlink(p);
                    return true;
                }
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    // BlockingQueue methods

    /**
     * Inserts the specified element at the end of this deque unless it would
     * violate capacity restrictions.  When using a capacity-restricted deque,
     * it is generally preferable to use method {@link #offer(Object) offer}.
     *
     * <p>This method is equivalent to {@link #addLast}.
     *
     * @throws IllegalStateException if the element cannot be added at this
     *         time due to capacity restrictions
     * @throws NullPointerException if the specified element is null
     */
    public boolean add(E e) {
        addLast(e);
        return true;
    }

    /**
     * @throws NullPointerException if the specified element is null
     */
    public boolean offer(E e) {
        return offerLast(e);
    }

    /**
     * @throws NullPointerException {@inheritDoc}
     * @throws InterruptedException {@inheritDoc}
     */
    public void put(E e) throws InterruptedException {
        putLast(e);
    }

    /**
     * @throws NullPointerException {@inheritDoc}
     * @throws InterruptedException {@inheritDoc}
     */
    public boolean offer(E e, long timeout, TimeUnit unit)
        throws InterruptedException {
        return offerLast(e, timeout, unit);
    }

    /**
     * Retrieves and removes the head of the queue represented by this deque.
     * This method differs from {@link #poll poll} only in that it throws an
     * exception if this deque is empty.
     *
     * <p>This method is equivalent to {@link #removeFirst() removeFirst}.
     *
     * @return the head of the queue represented by this deque
     * @throws NoSuchElementException if this deque is empty
     */
    public E remove() {
        return removeFirst();
    }

    public E poll() {
        return pollFirst();
    }

    public E take() throws InterruptedException {
        return takeFirst();
    }

    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        return pollFirst(timeout, unit);
    }

    /**
     * Retrieves, but does not remove, the head of the queue represented by
     * this deque.  This method differs from {@link #peek peek} only in that
     * it throws an exception if this deque is empty.
     *
     * <p>This method is equivalent to {@link #getFirst() getFirst}.
     *
     * @return the head of the queue represented by this deque
     * @throws NoSuchElementException if this deque is empty
     */
    public E element() {
        return getFirst();
    }

    public E peek() {
        return peekFirst();
    }

    /**
     * Returns the number of additional elements that this deque can ideally
     * (in the absence of memory or resource constraints) accept without
     * blocking. This is always equal to the initial capacity of this deque
     * less the current <tt>size</tt> of this deque.
     *
     * <p>Note that you <em>cannot</em> always tell if an attempt to insert
     * an element will succeed by inspecting <tt>remainingCapacity</tt>
     * because it may be the case that another thread is about to
     * insert or remove an element.
     */
    public int remainingCapacity() {
        lock.lock();
        try {
            return capacity - count;
        } finally {
            lock.unlock();
        }
    }

    /**
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     */
    public int drainTo(Collection<? super E> c) {
        if (c == null)
            throw new NullPointerException();
        if (c == this)
            throw new IllegalArgumentException();
        lock.lock();
        try {
            for (Node<E> p = first; p != null; p = p.next)
                c.add(p.item);
            int n = count;
            count = 0;
            first = last = null;
            notFull.signalAll();
            return n;
        } finally {
            lock.unlock();
        }
    }

    /**
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     */
    public int drainTo(Collection<? super E> c, int maxElements) {
        if (c == null)
            throw new NullPointerException();
        if (c == this)
            throw new IllegalArgumentException();
        lock.lock();
        try {
            int n = 0;
            while (n < maxElements && first != null) {
                c.add(first.item);
                first.prev = null;
                first = first.next;
                --count;
                ++n;
            }
            if (first == null)
                last = null;
            notFull.signalAll();
            return n;
        } finally {
            lock.unlock();
        }
    }

    // Stack methods

    /**
     * @throws IllegalStateException {@inheritDoc}
     * @throws NullPointerException  {@inheritDoc}
     */
    public void push(E e) {
        addFirst(e);
    }

    /**
     * @throws NoSuchElementException {@inheritDoc}
     */
    public E pop() {
        return removeFirst();
    }

    // Collection methods

    /**
     * Removes the first occurrence of the specified element from this deque.
     * If the deque does not contain the element, it is unchanged.
     * More formally, removes the first element <tt>e</tt> such that
     * <tt>o.equals(e)</tt> (if such an element exists).
     * Returns <tt>true</tt> if this deque contained the specified element
     * (or equivalently, if this deque changed as a result of the call).
     *
     * <p>This method is equivalent to
     * {@link #removeFirstOccurrence(Object) removeFirstOccurrence}.
     *
     * @param o element to be removed from this deque, if present
     * @return <tt>true</tt> if this deque changed as a result of the call
     */
    public boolean remove(Object o) {
        return removeFirstOccurrence(o);
    }

    /**
     * Returns the number of elements in this deque.
     *
     * @return the number of elements in this deque
     */
    public int size() {
        lock.lock();
        try {
            return count;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns <tt>true</tt> if this deque contains the specified element.
     * More formally, returns <tt>true</tt> if and only if this deque contains
     * at least one element <tt>e</tt> such that <tt>o.equals(e)</tt>.
     *
     * @param o object to be checked for containment in this deque
     * @return <tt>true</tt> if this deque contains the specified element
     */
    public boolean contains(Object o) {
        if (o == null) return false;
        lock.lock();
        try {
            for (Node<E> p = first; p != null; p = p.next)
                if (o.equals(p.item))
                    return true;
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Variant of removeFirstOccurrence needed by iterator.remove.
     * Searches for the node, not its contents.
     */
    boolean removeNode(Node<E> e) {
        lock.lock();
        try {
            for (Node<E> p = first; p != null; p = p.next) {
                if (p == e) {
                    unlink(p);
                    return true;
                }
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns an array containing all of the elements in this deque, in
     * proper sequence (from first to last element).
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
        lock.lock();
        try {
            Object[] a = new Object[count];
            int k = 0;
            for (Node<E> p = first; p != null; p = p.next)
                a[k++] = p.item;
            return a;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns an array containing all of the elements in this deque, in
     * proper sequence; the runtime type of the returned array is that of
     * the specified array.  If the deque fits in the specified array, it
     * is returned therein.  Otherwise, a new array is allocated with the
     * runtime type of the specified array and the size of this deque.
     *
     * <p>If this deque fits in the specified array with room to spare
     * (i.e., the array has more elements than this deque), the element in
     * the array immediately following the end of the deque is set to
     * <tt>null</tt>.
     *
     * <p>Like the {@link #toArray()} method, this method acts as bridge between
     * array-based and collection-based APIs.  Further, this method allows
     * precise control over the runtime type of the output array, and may,
     * under certain circumstances, be used to save allocation costs.
     *
     * <p>Suppose <tt>x</tt> is a deque known to contain only strings.
     * The following code can be used to dump the deque into a newly
     * allocated array of <tt>String</tt>:
     *
     * <pre>
     *     String[] y = x.toArray(new String[0]);</pre>
     *
     * Note that <tt>toArray(new Object[0])</tt> is identical in function to
     * <tt>toArray()</tt>.
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
    public <T> T[] toArray(T[] a) {
        lock.lock();
        try {
            if (a.length < count)
                a = (T[])java.lang.reflect.Array.newInstance(
                    a.getClass().getComponentType(),
                    count
                    );

            int k = 0;
            for (Node<E> p = first; p != null; p = p.next)
                a[k++] = (T)p.item;
            if (a.length > k)
                a[k] = null;
            return a;
        } finally {
            lock.unlock();
        }
    }

    public String toString() {
        lock.lock();
        try {
            return super.toString();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Atomically removes all of the elements from this deque.
     * The deque will be empty after this call returns.
     */
    public void clear() {
        lock.lock();
        try {
            first = last = null;
            count = 0;
            notFull.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns an iterator over the elements in this deque in proper sequence.
     * The elements will be returned in order from first (head) to last (tail).
     * The returned <tt>Iterator</tt> is a "weakly consistent" iterator that
     * will never throw {@link ConcurrentModificationException},
     * and guarantees to traverse elements as they existed upon
     * construction of the iterator, and may (but is not guaranteed to)
     * reflect any modifications subsequent to construction.
     *
     * @return an iterator over the elements in this deque in proper sequence
     */
    public Iterator<E> iterator() {
        return new Itr();
    }

    /**
     * Returns an iterator over the elements in this deque in reverse
     * sequential order.  The elements will be returned in order from
     * last (tail) to first (head).
     * The returned <tt>Iterator</tt> is a "weakly consistent" iterator that
     * will never throw {@link ConcurrentModificationException},
     * and guarantees to traverse elements as they existed upon
     * construction of the iterator, and may (but is not guaranteed to)
     * reflect any modifications subsequent to construction.
     */
    public Iterator<E> descendingIterator() {
        return new DescendingItr();
    }

    /**
     * Base class for Iterators for LinkedBlockingDeque
     */
    private abstract class AbstractItr implements Iterator<E> {
        /**
         * The next node to return in next
         */
         Node<E> next;

        /**
         * nextItem holds on to item fields because once we claim that
         * an element exists in hasNext(), we must return item read
         * under lock (in advance()) even if it was in the process of
         * being removed when hasNext() was called.
         */
        E nextItem;

        /**
         * Node returned by most recent call to next. Needed by remove.
         * Reset to null if this element is deleted by a call to remove.
         */
        private Node<E> lastRet;

        AbstractItr() {
            advance(); // set to initial position
        }

        /**
         * Advances next, or if not yet initialized, sets to first node.
         * Implemented to move forward vs backward in the two subclasses.
         */
        abstract void advance();

        public boolean hasNext() {
            return next != null;
        }

        public E next() {
            if (next == null)
                throw new NoSuchElementException();
            lastRet = next;
            E x = nextItem;
            advance();
            return x;
        }

        public void remove() {
            Node<E> n = lastRet;
            if (n == null)
                throw new IllegalStateException();
            lastRet = null;
            // Note: removeNode rescans looking for this node to make
            // sure it was not already removed. Otherwise, trying to
            // re-remove could corrupt list.
            removeNode(n);
        }
    }

    /** Forward iterator */
    private class Itr extends AbstractItr {
        void advance() {
            final ReentrantLock lock = LinkedBlockingDeque.this.lock;
            lock.lock();
            try {
                next = (next == null)? first : next.next;
                nextItem = (next == null)? null : next.item;
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * Descending iterator for LinkedBlockingDeque
     */
    private class DescendingItr extends AbstractItr {
        void advance() {
            final ReentrantLock lock = LinkedBlockingDeque.this.lock;
            lock.lock();
            try {
                next = (next == null)? last : next.prev;
                nextItem = (next == null)? null : next.item;
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * Save the state of this deque to a stream (that is, serialize it).
     *
     * @serialData The capacity (int), followed by elements (each an
     * <tt>Object</tt>) in the proper order, followed by a null
     * @param s the stream
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException {
        lock.lock();
        try {
            // Write out capacity and any hidden stuff
            s.defaultWriteObject();
            // Write out all elements in the proper order.
            for (Node<E> p = first; p != null; p = p.next)
                s.writeObject(p.item);
            // Use trailing null as sentinel
            s.writeObject(null);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Reconstitute this deque from a stream (that is,
     * deserialize it).
     * @param s the stream
     */
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        count = 0;
        first = null;
        last = null;
        // Read in all elements and place in queue
        for (;;) {
            E item = (E)s.readObject();
            if (item == null)
                break;
            add(item);
        }
    }

}
