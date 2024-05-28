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
 * Written by Doug Lea, Bill Scherer, and Michael Scott with
 * assistance from members of JCP JSR-166 Expert Group and released to
 * the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TransferQueue;

/**
 * A {@linkplain BlockingQueue blocking queue} in which each insert
 * operation must wait for a corresponding remove operation by another
 * thread, and vice versa.  A synchronous queue does not have any
 * internal capacity, not even a capacity of one.  You cannot
 * {@code peek} at a synchronous queue because an element is only
 * present when you try to remove it; you cannot insert an element
 * (using any method) unless another thread is trying to remove it;
 * you cannot iterate as there is nothing to iterate.  The
 * <em>head</em> of the queue is the element that the first queued
 * inserting thread is trying to add to the queue; if there is no such
 * queued thread then no element is available for removal and
 * {@code poll()} will return {@code null}.  For purposes of other
 * {@code Collection} methods (for example {@code contains}), a
 * {@code SynchronousQueue} acts as an empty collection.  This queue
 * does not permit {@code null} elements.
 *
 * <p>Synchronous queues are similar to rendezvous channels used in
 * CSP and Ada. They are well suited for handoff designs, in which an
 * object running in one thread must sync up with an object running
 * in another thread in order to hand it some information, event, or
 * task.
 *
 * <p>This class supports an optional fairness policy for ordering
 * waiting producer and consumer threads.  By default, this ordering
 * is not guaranteed. However, a queue constructed with fairness set
 * to {@code true} grants threads access in FIFO order.
 *
 * <p>This class and its iterator implement all of the <em>optional</em>
 * methods of the {@link Collection} and {@link Iterator} interfaces.
 *
 * <p>This class is a member of the
 * <a href="{@docRoot}/java.base/java/util/package-summary.html#CollectionsFramework">
 * Java Collections Framework</a>.
 *
 * @since 1.5
 * @author Doug Lea and Bill Scherer and Michael Scott
 * @param <E> the type of elements held in this queue
 */
public class SynchronousQueue<E> extends AbstractQueue<E>
    implements BlockingQueue<E>, java.io.Serializable {
    private static final long serialVersionUID = -3223113410248163686L;

    /*
     * This class implements extensions of the dual stack and dual
     * queue algorithms described in "Nonblocking Concurrent Objects
     * with Condition Synchronization", by W. N. Scherer III and
     * M. L. Scott.  18th Annual Conf. on Distributed Computing,
     * Oct. 2004 (see also
     * http://www.cs.rochester.edu/u/scott/synchronization/pseudocode/duals.html).
     * The queue is treated as a Lifo stack in non-fair mode, and a
     * Fifo queue in fair mode. In most contexts, transfer performance
     * is roughly comparable across them. Lifo is usually faster under
     * low contention, but slower under high contention.  Performance
     * of applications using them also varies. Lifo is generally
     * preferable in resource management settings (for example cached
     * thread pools) because of better temporal locality, but
     * inappropriate for message-passing applications.
     *
     * A dual queue is one that at any given time either holds "data"
     * -- items provided by put operations, or "requests" -- slots
     * representing take operations, or is empty. A fulfilling
     * operation (i.e., a call requesting an item from a queue holding
     * data or vice versa) "matches" the item of and then dequeues a
     * complementary node.  Any operation can figure out which mode
     * the queue is in, and act accordingly without needing locks.  So
     * put and take operations are symmetrical, and all transfer
     * methods invoke a single "xfer" method that does a put or a take
     * in either fifo or lifo mode.
     *
     * The algorithms here differ from the versions in the above paper
     * in ways including:
     *
     *  * The original algorithms used bit-marked pointers, but the
     *     ones here use a bit (isData) in nodes, and usually avoid
     *     creating nodes when fulfilling. They also use the
     *     compareAndExchange form of CAS for pointer updates to
     *     reduce memory traffic.
     *  * Fifo mode is based on LinkedTransferQueue operations, but
     *     Lifo mode support is added in subclass Transferer.
     *  * The Fifo version accommodates lazy updates and slack as
     *     described in LinkedTransferQueue internal documentation.
     *  * Threads may block when waiting to become fulfilled,
     *     sometimes preceded by brief spins.
     *  * Support for cancellation via timeout and interrupts,
     *     including cleaning out cancelled nodes/threads from lists
     *     to avoid garbage retention and memory depletion.
     */

    /**
     * Extension of LinkedTransferQueue to support Lifo (stack) mode.
     * Methods use the "head" field as head (top) of stack (versus
     * queue). Note that popped nodes are not self-linked because they
     * are not prone to unbounded garbage chains. Also note that
     * "async" mode is never used and not supported for synchronous
     * transfers.
     */
    @SuppressWarnings("serial") // never serialized
    static final class Transferer<E> extends LinkedTransferQueue<E> {

        /**
         * Puts or takes an item with lifo ordering. Loops trying:
         * * If top (var p) exists and is already matched, pop and continue
         * * If top has complementary type, try to fulfill by CASing item,
         *    On success pop (which will succeed unless already helped),
         *    otherwise restart.
         * * If no possible match, unless immediate mode, push a
         *    node and wait, later unsplicing if cancelled.
         *
         * @param e the item or null for take
         * @param ns timeout or 0 if immediate, Long.MAX_VALUE if untimed
         * @return an item if matched, else e
         */
        final Object xferLifo(Object e, long ns) {
            boolean haveData = (e != null);
            Object m;                              // the match or e if none
            outer: for (DualNode s = null, p = head;;) {
                while (p != null) {
                    boolean isData; DualNode n, u; // help collapse
                    if ((isData = p.isData) != ((m = p.item) != null))
                        p = (p == (u = cmpExHead(p, (n = p.next)))) ? n : u;
                    else if (isData == haveData)   // same mode; push below
                        break;
                    else if (p.cmpExItem(m, e) != m)
                        p = head;                  // missed; restart
                    else {                         // matched complementary node
                        Thread w = p.waiter;
                        cmpExHead(p, p.next);
                        LockSupport.unpark(w);
                        break outer;
                    }
                }
                if (ns == 0L) {                    // no match, no wait
                    m = e;
                    break;
                }
                if (s == null)                     // try to push node and wait
                    s = new DualNode(e, haveData);
                s.next = p;
                if (p == (p = cmpExHead(p, s))) {
                    if ((m = s.await(e, ns, this,  // spin if (nearly) empty
                                     p == null || p.waiter == null)) == e)
                        unspliceLifo(s);           // cancelled
                    else if (m != null)
                        s.selfLinkItem();
                    break;
                }
            }
            return m;
        }

        /**
         * Unlinks node s. Same idea as Fifo version.
         */
        private void unspliceLifo(DualNode s) {
            boolean seen = false; // try removing by collapsing head
            DualNode p = head;
            for (DualNode f, u; p != null && p.matched();) {
                if (p == s)
                    seen = true;
                p = (p == (u = cmpExHead(p, (f = p.next)))) ? f : u;
            }
            if (p != null && !seen && sweepNow()) { // occasionally sweep
                for (DualNode f, n, u; p != null && (f = p.next) != null; ) {
                    p = (!f.matched() ? f :
                         f == (u = p.cmpExNext(f, n = f.next)) ? n : u);
                }
            }
        }
    }

    /**
     * The transferer. (See below about serialization.)
     */
    private final transient Transferer<E> transferer;

    private final transient boolean fair;

    /** Invokes fair or lifo transfer */
    private Object xfer(Object e, long nanos) {
        Transferer<E> x = transferer;
        return (fair) ? x.xfer(e, nanos) : x.xferLifo(e, nanos);
    }

    /**
     * Creates a {@code SynchronousQueue} with nonfair access policy.
     */
    public SynchronousQueue() {
        this(false);
    }

    /**
     * Creates a {@code SynchronousQueue} with the specified fairness policy.
     *
     * @param fair if true, waiting threads contend in FIFO order for
     *        access; otherwise the order is unspecified.
     */
    public SynchronousQueue(boolean fair) {
        this.fair = fair;
        transferer = new Transferer<E>();
    }

    /**
     * Adds the specified element to this queue, waiting if necessary for
     * another thread to receive it.
     *
     * @throws InterruptedException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    public void put(E e) throws InterruptedException {
        Objects.requireNonNull(e);
        if (!Thread.interrupted()) {
            if (xfer(e, Long.MAX_VALUE) == null)
                return;
            Thread.interrupted(); // failure possible only due to interrupt
        }
        throw new InterruptedException();
    }

    /**
     * Inserts the specified element into this queue, waiting if necessary
     * up to the specified wait time for another thread to receive it.
     *
     * @return {@code true} if successful, or {@code false} if the
     *         specified waiting time elapses before a consumer appears
     * @throws InterruptedException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    public boolean offer(E e, long timeout, TimeUnit unit)
        throws InterruptedException {
        Objects.requireNonNull(e);
        long nanos = Math.max(unit.toNanos(timeout), 0L);
        if (xfer(e, nanos) == null)
            return true;
        if (!Thread.interrupted())
            return false;
        throw new InterruptedException();
    }

    /**
     * Inserts the specified element into this queue, if another thread is
     * waiting to receive it.
     *
     * @param e the element to add
     * @return {@code true} if the element was added to this queue, else
     *         {@code false}
     * @throws NullPointerException if the specified element is null
     */
    public boolean offer(E e) {
        Objects.requireNonNull(e);
        return xfer(e, 0L) == null;
    }

    /**
     * Retrieves and removes the head of this queue, waiting if necessary
     * for another thread to insert it.
     *
     * @return the head of this queue
     * @throws InterruptedException {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    public E take() throws InterruptedException {
        Object e;
        if (!Thread.interrupted()) {
            if ((e = xfer(null, Long.MAX_VALUE)) != null)
                return (E) e;
            Thread.interrupted();
        }
        throw new InterruptedException();
    }

    /**
     * Retrieves and removes the head of this queue, waiting
     * if necessary up to the specified wait time, for another thread
     * to insert it.
     *
     * @return the head of this queue, or {@code null} if the
     *         specified waiting time elapses before an element is present
     * @throws InterruptedException {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        Object e;
        long nanos = Math.max(unit.toNanos(timeout), 0L);
        if ((e = xfer(null, nanos)) != null || !Thread.interrupted())
            return (E) e;
        throw new InterruptedException();
    }

    /**
     * Retrieves and removes the head of this queue, if another thread
     * is currently making an element available.
     *
     * @return the head of this queue, or {@code null} if no
     *         element is available
     */
    @SuppressWarnings("unchecked")
    public E poll() {
        return (E) xfer(null, 0L);
    }

    /**
     * Always returns {@code true}.
     * A {@code SynchronousQueue} has no internal capacity.
     *
     * @return {@code true}
     */
    public boolean isEmpty() {
        return true;
    }

    /**
     * Always returns zero.
     * A {@code SynchronousQueue} has no internal capacity.
     *
     * @return zero
     */
    public int size() {
        return 0;
    }

    /**
     * Always returns zero.
     * A {@code SynchronousQueue} has no internal capacity.
     *
     * @return zero
     */
    public int remainingCapacity() {
        return 0;
    }

    /**
     * Does nothing.
     * A {@code SynchronousQueue} has no internal capacity.
     */
    public void clear() {
    }

    /**
     * Always returns {@code false}.
     * A {@code SynchronousQueue} has no internal capacity.
     *
     * @param o the element
     * @return {@code false}
     */
    public boolean contains(Object o) {
        return false;
    }

    /**
     * Always returns {@code false}.
     * A {@code SynchronousQueue} has no internal capacity.
     *
     * @param o the element to remove
     * @return {@code false}
     */
    public boolean remove(Object o) {
        return false;
    }

    /**
     * Returns {@code false} unless the given collection is empty.
     * A {@code SynchronousQueue} has no internal capacity.
     *
     * @param c the collection
     * @return {@code false} unless given collection is empty
     */
    public boolean containsAll(Collection<?> c) {
        return c.isEmpty();
    }

    /**
     * Always returns {@code false}.
     * A {@code SynchronousQueue} has no internal capacity.
     *
     * @param c the collection
     * @return {@code false}
     */
    public boolean removeAll(Collection<?> c) {
        return false;
    }

    /**
     * Always returns {@code false}.
     * A {@code SynchronousQueue} has no internal capacity.
     *
     * @param c the collection
     * @return {@code false}
     */
    public boolean retainAll(Collection<?> c) {
        return false;
    }

    /**
     * Always returns {@code null}.
     * A {@code SynchronousQueue} does not return elements
     * unless actively waited on.
     *
     * @return {@code null}
     */
    public E peek() {
        return null;
    }

    /**
     * Returns an empty iterator in which {@code hasNext} always returns
     * {@code false}.
     *
     * @return an empty iterator
     */
    public Iterator<E> iterator() {
        return Collections.emptyIterator();
    }

    /**
     * Returns an empty spliterator in which calls to
     * {@link Spliterator#trySplit() trySplit} always return {@code null}.
     *
     * @return an empty spliterator
     * @since 1.8
     */
    public Spliterator<E> spliterator() {
        return Spliterators.emptySpliterator();
    }

    /**
     * Returns a zero-length array.
     * @return a zero-length array
     */
    public Object[] toArray() {
        return new Object[0];
    }

    /**
     * Sets the zeroth element of the specified array to {@code null}
     * (if the array has non-zero length) and returns it.
     *
     * @param a the array
     * @return the specified array
     * @throws NullPointerException if the specified array is null
     */
    public <T> T[] toArray(T[] a) {
        if (a.length > 0)
            a[0] = null;
        return a;
    }

    /**
     * Always returns {@code "[]"}.
     * @return {@code "[]"}
     */
    public String toString() {
        return "[]";
    }

    /**
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     */
    public int drainTo(Collection<? super E> c) {
        Objects.requireNonNull(c);
        if (c == this)
            throw new IllegalArgumentException();
        int n = 0;
        for (E e; (e = poll()) != null; n++)
            c.add(e);
        return n;
    }

    /**
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     */
    public int drainTo(Collection<? super E> c, int maxElements) {
        Objects.requireNonNull(c);
        if (c == this)
            throw new IllegalArgumentException();
        int n = 0;
        for (E e; n < maxElements && (e = poll()) != null; n++)
            c.add(e);
        return n;
    }

    /*
     * To cope with serialization across multiple implementation
     * overhauls, we declare some unused classes and fields that exist
     * solely to enable serializability across versions.  These fields
     * are never used, so are initialized only if this object is ever
     * serialized. We use readResolve to replace a deserialized queue
     * with a fresh one. Note that no queue elements are serialized,
     * since any existing ones are only transient.
     */

    @SuppressWarnings("serial")
    static class WaitQueue implements java.io.Serializable { }
    static class LifoWaitQueue extends WaitQueue {
        private static final long serialVersionUID = -3633113410248163686L;
    }
    static class FifoWaitQueue extends WaitQueue {
        private static final long serialVersionUID = -3623113410248163686L;
    }
    private ReentrantLock qlock;
    private WaitQueue waitingProducers;
    private WaitQueue waitingConsumers;

    /**
     * Saves this queue to a stream (that is, serializes it).
     * @param s the stream
     * @throws java.io.IOException if an I/O error occurs
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException {
        if (fair) {
            qlock = new ReentrantLock(true);
            waitingProducers = new FifoWaitQueue();
            waitingConsumers = new FifoWaitQueue();
        }
        else {
            qlock = new ReentrantLock();
            waitingProducers = new LifoWaitQueue();
            waitingConsumers = new LifoWaitQueue();
        }
        s.defaultWriteObject();
    }

    /**
     * Replaces a deserialized SynchronousQueue with a fresh one with
     * the associated fairness
     */
    private Object readResolve() {
        return new SynchronousQueue<E>(waitingProducers instanceof FifoWaitQueue);
    }
}
