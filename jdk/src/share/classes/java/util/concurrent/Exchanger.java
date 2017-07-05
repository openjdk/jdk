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
 * Written by Doug Lea, Bill Scherer, and Michael Scott with
 * assistance from members of JCP JSR-166 Expert Group and released to
 * the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package java.util.concurrent;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.LockSupport;

/**
 * A synchronization point at which threads can pair and swap elements
 * within pairs.  Each thread presents some object on entry to the
 * {@link #exchange exchange} method, matches with a partner thread,
 * and receives its partner's object on return.  An Exchanger may be
 * viewed as a bidirectional form of a {@link SynchronousQueue}.
 * Exchangers may be useful in applications such as genetic algorithms
 * and pipeline designs.
 *
 * <p><b>Sample Usage:</b>
 * Here are the highlights of a class that uses an {@code Exchanger}
 * to swap buffers between threads so that the thread filling the
 * buffer gets a freshly emptied one when it needs it, handing off the
 * filled one to the thread emptying the buffer.
 * <pre>{@code
 * class FillAndEmpty {
 *   Exchanger<DataBuffer> exchanger = new Exchanger<DataBuffer>();
 *   DataBuffer initialEmptyBuffer = ... a made-up type
 *   DataBuffer initialFullBuffer = ...
 *
 *   class FillingLoop implements Runnable {
 *     public void run() {
 *       DataBuffer currentBuffer = initialEmptyBuffer;
 *       try {
 *         while (currentBuffer != null) {
 *           addToBuffer(currentBuffer);
 *           if (currentBuffer.isFull())
 *             currentBuffer = exchanger.exchange(currentBuffer);
 *         }
 *       } catch (InterruptedException ex) { ... handle ... }
 *     }
 *   }
 *
 *   class EmptyingLoop implements Runnable {
 *     public void run() {
 *       DataBuffer currentBuffer = initialFullBuffer;
 *       try {
 *         while (currentBuffer != null) {
 *           takeFromBuffer(currentBuffer);
 *           if (currentBuffer.isEmpty())
 *             currentBuffer = exchanger.exchange(currentBuffer);
 *         }
 *       } catch (InterruptedException ex) { ... handle ...}
 *     }
 *   }
 *
 *   void start() {
 *     new Thread(new FillingLoop()).start();
 *     new Thread(new EmptyingLoop()).start();
 *   }
 * }
 * }</pre>
 *
 * <p>Memory consistency effects: For each pair of threads that
 * successfully exchange objects via an {@code Exchanger}, actions
 * prior to the {@code exchange()} in each thread
 * <a href="package-summary.html#MemoryVisibility"><i>happen-before</i></a>
 * those subsequent to a return from the corresponding {@code exchange()}
 * in the other thread.
 *
 * @since 1.5
 * @author Doug Lea and Bill Scherer and Michael Scott
 * @param <V> The type of objects that may be exchanged
 */
public class Exchanger<V> {
    /*
     * Algorithm Description:
     *
     * The basic idea is to maintain a "slot", which is a reference to
     * a Node containing both an Item to offer and a "hole" waiting to
     * get filled in.  If an incoming "occupying" thread sees that the
     * slot is null, it CAS'es (compareAndSets) a Node there and waits
     * for another to invoke exchange.  That second "fulfilling" thread
     * sees that the slot is non-null, and so CASes it back to null,
     * also exchanging items by CASing the hole, plus waking up the
     * occupying thread if it is blocked.  In each case CAS'es may
     * fail because a slot at first appears non-null but is null upon
     * CAS, or vice-versa.  So threads may need to retry these
     * actions.
     *
     * This simple approach works great when there are only a few
     * threads using an Exchanger, but performance rapidly
     * deteriorates due to CAS contention on the single slot when
     * there are lots of threads using an exchanger.  So instead we use
     * an "arena"; basically a kind of hash table with a dynamically
     * varying number of slots, any one of which can be used by
     * threads performing an exchange.  Incoming threads pick slots
     * based on a hash of their Thread ids.  If an incoming thread
     * fails to CAS in its chosen slot, it picks an alternative slot
     * instead.  And similarly from there.  If a thread successfully
     * CASes into a slot but no other thread arrives, it tries
     * another, heading toward the zero slot, which always exists even
     * if the table shrinks.  The particular mechanics controlling this
     * are as follows:
     *
     * Waiting: Slot zero is special in that it is the only slot that
     * exists when there is no contention.  A thread occupying slot
     * zero will block if no thread fulfills it after a short spin.
     * In other cases, occupying threads eventually give up and try
     * another slot.  Waiting threads spin for a while (a period that
     * should be a little less than a typical context-switch time)
     * before either blocking (if slot zero) or giving up (if other
     * slots) and restarting.  There is no reason for threads to block
     * unless there are unlikely to be any other threads present.
     * Occupants are mainly avoiding memory contention so sit there
     * quietly polling for a shorter period than it would take to
     * block and then unblock them.  Non-slot-zero waits that elapse
     * because of lack of other threads waste around one extra
     * context-switch time per try, which is still on average much
     * faster than alternative approaches.
     *
     * Sizing: Usually, using only a few slots suffices to reduce
     * contention.  Especially with small numbers of threads, using
     * too many slots can lead to just as poor performance as using
     * too few of them, and there's not much room for error.  The
     * variable "max" maintains the number of slots actually in
     * use.  It is increased when a thread sees too many CAS
     * failures.  (This is analogous to resizing a regular hash table
     * based on a target load factor, except here, growth steps are
     * just one-by-one rather than proportional.)  Growth requires
     * contention failures in each of three tried slots.  Requiring
     * multiple failures for expansion copes with the fact that some
     * failed CASes are not due to contention but instead to simple
     * races between two threads or thread pre-emptions occurring
     * between reading and CASing.  Also, very transient peak
     * contention can be much higher than the average sustainable
     * levels.  The max limit is decreased on average 50% of the times
     * that a non-slot-zero wait elapses without being fulfilled.
     * Threads experiencing elapsed waits move closer to zero, so
     * eventually find existing (or future) threads even if the table
     * has been shrunk due to inactivity.  The chosen mechanics and
     * thresholds for growing and shrinking are intrinsically
     * entangled with indexing and hashing inside the exchange code,
     * and can't be nicely abstracted out.
     *
     * Hashing: Each thread picks its initial slot to use in accord
     * with a simple hashcode.  The sequence is the same on each
     * encounter by any given thread, but effectively random across
     * threads.  Using arenas encounters the classic cost vs quality
     * tradeoffs of all hash tables.  Here, we use a one-step FNV-1a
     * hash code based on the current thread's Thread.getId(), along
     * with a cheap approximation to a mod operation to select an
     * index.  The downside of optimizing index selection in this way
     * is that the code is hardwired to use a maximum table size of
     * 32.  But this value more than suffices for known platforms and
     * applications.
     *
     * Probing: On sensed contention of a selected slot, we probe
     * sequentially through the table, analogously to linear probing
     * after collision in a hash table.  (We move circularly, in
     * reverse order, to mesh best with table growth and shrinkage
     * rules.)  Except that to minimize the effects of false-alarms
     * and cache thrashing, we try the first selected slot twice
     * before moving.
     *
     * Padding: Even with contention management, slots are heavily
     * contended, so use cache-padding to avoid poor memory
     * performance.  Because of this, slots are lazily constructed
     * only when used, to avoid wasting this space unnecessarily.
     * While isolation of locations is not much of an issue at first
     * in an application, as time goes on and garbage-collectors
     * perform compaction, slots are very likely to be moved adjacent
     * to each other, which can cause much thrashing of cache lines on
     * MPs unless padding is employed.
     *
     * This is an improvement of the algorithm described in the paper
     * "A Scalable Elimination-based Exchange Channel" by William
     * Scherer, Doug Lea, and Michael Scott in Proceedings of SCOOL05
     * workshop.  Available at: http://hdl.handle.net/1802/2104
     */

    /** The number of CPUs, for sizing and spin control */
    private static final int NCPU = Runtime.getRuntime().availableProcessors();

    /**
     * The capacity of the arena.  Set to a value that provides more
     * than enough space to handle contention.  On small machines
     * most slots won't be used, but it is still not wasted because
     * the extra space provides some machine-level address padding
     * to minimize interference with heavily CAS'ed Slot locations.
     * And on very large machines, performance eventually becomes
     * bounded by memory bandwidth, not numbers of threads/CPUs.
     * This constant cannot be changed without also modifying
     * indexing and hashing algorithms.
     */
    private static final int CAPACITY = 32;

    /**
     * The value of "max" that will hold all threads without
     * contention.  When this value is less than CAPACITY, some
     * otherwise wasted expansion can be avoided.
     */
    private static final int FULL =
        Math.max(0, Math.min(CAPACITY, NCPU / 2) - 1);

    /**
     * The number of times to spin (doing nothing except polling a
     * memory location) before blocking or giving up while waiting to
     * be fulfilled.  Should be zero on uniprocessors.  On
     * multiprocessors, this value should be large enough so that two
     * threads exchanging items as fast as possible block only when
     * one of them is stalled (due to GC or preemption), but not much
     * longer, to avoid wasting CPU resources.  Seen differently, this
     * value is a little over half the number of cycles of an average
     * context switch time on most systems.  The value here is
     * approximately the average of those across a range of tested
     * systems.
     */
    private static final int SPINS = (NCPU == 1) ? 0 : 2000;

    /**
     * The number of times to spin before blocking in timed waits.
     * Timed waits spin more slowly because checking the time takes
     * time.  The best value relies mainly on the relative rate of
     * System.nanoTime vs memory accesses.  The value is empirically
     * derived to work well across a variety of systems.
     */
    private static final int TIMED_SPINS = SPINS / 20;

    /**
     * Sentinel item representing cancellation of a wait due to
     * interruption, timeout, or elapsed spin-waits.  This value is
     * placed in holes on cancellation, and used as a return value
     * from waiting methods to indicate failure to set or get hole.
     */
    private static final Object CANCEL = new Object();

    /**
     * Value representing null arguments/returns from public
     * methods.  This disambiguates from internal requirement that
     * holes start out as null to mean they are not yet set.
     */
    private static final Object NULL_ITEM = new Object();

    /**
     * Nodes hold partially exchanged data.  This class
     * opportunistically subclasses AtomicReference to represent the
     * hole.  So get() returns hole, and compareAndSet CAS'es value
     * into hole.  This class cannot be parameterized as "V" because
     * of the use of non-V CANCEL sentinels.
     */
    private static final class Node extends AtomicReference<Object> {
        /** The element offered by the Thread creating this node. */
        public final Object item;

        /** The Thread waiting to be signalled; null until waiting. */
        public volatile Thread waiter;

        /**
         * Creates node with given item and empty hole.
         * @param item the item
         */
        public Node(Object item) {
            this.item = item;
        }
    }

    /**
     * A Slot is an AtomicReference with heuristic padding to lessen
     * cache effects of this heavily CAS'ed location.  While the
     * padding adds noticeable space, all slots are created only on
     * demand, and there will be more than one of them only when it
     * would improve throughput more than enough to outweigh using
     * extra space.
     */
    private static final class Slot extends AtomicReference<Object> {
        // Improve likelihood of isolation on <= 64 byte cache lines
        long q0, q1, q2, q3, q4, q5, q6, q7, q8, q9, qa, qb, qc, qd, qe;
    }

    /**
     * Slot array.  Elements are lazily initialized when needed.
     * Declared volatile to enable double-checked lazy construction.
     */
    private volatile Slot[] arena = new Slot[CAPACITY];

    /**
     * The maximum slot index being used.  The value sometimes
     * increases when a thread experiences too many CAS contentions,
     * and sometimes decreases when a spin-wait elapses.  Changes
     * are performed only via compareAndSet, to avoid stale values
     * when a thread happens to stall right before setting.
     */
    private final AtomicInteger max = new AtomicInteger();

    /**
     * Main exchange function, handling the different policy variants.
     * Uses Object, not "V" as argument and return value to simplify
     * handling of sentinel values.  Callers from public methods decode
     * and cast accordingly.
     *
     * @param item the (non-null) item to exchange
     * @param timed true if the wait is timed
     * @param nanos if timed, the maximum wait time
     * @return the other thread's item, or CANCEL if interrupted or timed out
     */
    private Object doExchange(Object item, boolean timed, long nanos) {
        Node me = new Node(item);                 // Create in case occupying
        int index = hashIndex();                  // Index of current slot
        int fails = 0;                            // Number of CAS failures

        for (;;) {
            Object y;                             // Contents of current slot
            Slot slot = arena[index];
            if (slot == null)                     // Lazily initialize slots
                createSlot(index);                // Continue loop to reread
            else if ((y = slot.get()) != null &&  // Try to fulfill
                     slot.compareAndSet(y, null)) {
                Node you = (Node)y;               // Transfer item
                if (you.compareAndSet(null, item)) {
                    LockSupport.unpark(you.waiter);
                    return you.item;
                }                                 // Else cancelled; continue
            }
            else if (y == null &&                 // Try to occupy
                     slot.compareAndSet(null, me)) {
                if (index == 0)                   // Blocking wait for slot 0
                    return timed? awaitNanos(me, slot, nanos): await(me, slot);
                Object v = spinWait(me, slot);    // Spin wait for non-0
                if (v != CANCEL)
                    return v;
                me = new Node(item);              // Throw away cancelled node
                int m = max.get();
                if (m > (index >>>= 1))           // Decrease index
                    max.compareAndSet(m, m - 1);  // Maybe shrink table
            }
            else if (++fails > 1) {               // Allow 2 fails on 1st slot
                int m = max.get();
                if (fails > 3 && m < FULL && max.compareAndSet(m, m + 1))
                    index = m + 1;                // Grow on 3rd failed slot
                else if (--index < 0)
                    index = m;                    // Circularly traverse
            }
        }
    }

    /**
     * Returns a hash index for the current thread.  Uses a one-step
     * FNV-1a hash code (http://www.isthe.com/chongo/tech/comp/fnv/)
     * based on the current thread's Thread.getId().  These hash codes
     * have more uniform distribution properties with respect to small
     * moduli (here 1-31) than do other simple hashing functions.
     *
     * <p>To return an index between 0 and max, we use a cheap
     * approximation to a mod operation, that also corrects for bias
     * due to non-power-of-2 remaindering (see {@link
     * java.util.Random#nextInt}).  Bits of the hashcode are masked
     * with "nbits", the ceiling power of two of table size (looked up
     * in a table packed into three ints).  If too large, this is
     * retried after rotating the hash by nbits bits, while forcing new
     * top bit to 0, which guarantees eventual termination (although
     * with a non-random-bias).  This requires an average of less than
     * 2 tries for all table sizes, and has a maximum 2% difference
     * from perfectly uniform slot probabilities when applied to all
     * possible hash codes for sizes less than 32.
     *
     * @return a per-thread-random index, 0 <= index < max
     */
    private final int hashIndex() {
        long id = Thread.currentThread().getId();
        int hash = (((int)(id ^ (id >>> 32))) ^ 0x811c9dc5) * 0x01000193;

        int m = max.get();
        int nbits = (((0xfffffc00  >> m) & 4) | // Compute ceil(log2(m+1))
                     ((0x000001f8 >>> m) & 2) | // The constants hold
                     ((0xffff00f2 >>> m) & 1)); // a lookup table
        int index;
        while ((index = hash & ((1 << nbits) - 1)) > m)       // May retry on
            hash = (hash >>> nbits) | (hash << (33 - nbits)); // non-power-2 m
        return index;
    }

    /**
     * Creates a new slot at given index.  Called only when the slot
     * appears to be null.  Relies on double-check using builtin
     * locks, since they rarely contend.  This in turn relies on the
     * arena array being declared volatile.
     *
     * @param index the index to add slot at
     */
    private void createSlot(int index) {
        // Create slot outside of lock to narrow sync region
        Slot newSlot = new Slot();
        Slot[] a = arena;
        synchronized (a) {
            if (a[index] == null)
                a[index] = newSlot;
        }
    }

    /**
     * Tries to cancel a wait for the given node waiting in the given
     * slot, if so, helping clear the node from its slot to avoid
     * garbage retention.
     *
     * @param node the waiting node
     * @param the slot it is waiting in
     * @return true if successfully cancelled
     */
    private static boolean tryCancel(Node node, Slot slot) {
        if (!node.compareAndSet(null, CANCEL))
            return false;
        if (slot.get() == node) // pre-check to minimize contention
            slot.compareAndSet(node, null);
        return true;
    }

    // Three forms of waiting. Each just different enough not to merge
    // code with others.

    /**
     * Spin-waits for hole for a non-0 slot.  Fails if spin elapses
     * before hole filled.  Does not check interrupt, relying on check
     * in public exchange method to abort if interrupted on entry.
     *
     * @param node the waiting node
     * @return on success, the hole; on failure, CANCEL
     */
    private static Object spinWait(Node node, Slot slot) {
        int spins = SPINS;
        for (;;) {
            Object v = node.get();
            if (v != null)
                return v;
            else if (spins > 0)
                --spins;
            else
                tryCancel(node, slot);
        }
    }

    /**
     * Waits for (by spinning and/or blocking) and gets the hole
     * filled in by another thread.  Fails if interrupted before
     * hole filled.
     *
     * When a node/thread is about to block, it sets its waiter field
     * and then rechecks state at least one more time before actually
     * parking, thus covering race vs fulfiller noticing that waiter
     * is non-null so should be woken.
     *
     * Thread interruption status is checked only surrounding calls to
     * park.  The caller is assumed to have checked interrupt status
     * on entry.
     *
     * @param node the waiting node
     * @return on success, the hole; on failure, CANCEL
     */
    private static Object await(Node node, Slot slot) {
        Thread w = Thread.currentThread();
        int spins = SPINS;
        for (;;) {
            Object v = node.get();
            if (v != null)
                return v;
            else if (spins > 0)                 // Spin-wait phase
                --spins;
            else if (node.waiter == null)       // Set up to block next
                node.waiter = w;
            else if (w.isInterrupted())         // Abort on interrupt
                tryCancel(node, slot);
            else                                // Block
                LockSupport.park(node);
        }
    }

    /**
     * Waits for (at index 0) and gets the hole filled in by another
     * thread.  Fails if timed out or interrupted before hole filled.
     * Same basic logic as untimed version, but a bit messier.
     *
     * @param node the waiting node
     * @param nanos the wait time
     * @return on success, the hole; on failure, CANCEL
     */
    private Object awaitNanos(Node node, Slot slot, long nanos) {
        int spins = TIMED_SPINS;
        long lastTime = 0;
        Thread w = null;
        for (;;) {
            Object v = node.get();
            if (v != null)
                return v;
            long now = System.nanoTime();
            if (w == null)
                w = Thread.currentThread();
            else
                nanos -= now - lastTime;
            lastTime = now;
            if (nanos > 0) {
                if (spins > 0)
                    --spins;
                else if (node.waiter == null)
                    node.waiter = w;
                else if (w.isInterrupted())
                    tryCancel(node, slot);
                else
                    LockSupport.parkNanos(node, nanos);
            }
            else if (tryCancel(node, slot) && !w.isInterrupted())
                return scanOnTimeout(node);
        }
    }

    /**
     * Sweeps through arena checking for any waiting threads.  Called
     * only upon return from timeout while waiting in slot 0.  When a
     * thread gives up on a timed wait, it is possible that a
     * previously-entered thread is still waiting in some other
     * slot.  So we scan to check for any.  This is almost always
     * overkill, but decreases the likelihood of timeouts when there
     * are other threads present to far less than that in lock-based
     * exchangers in which earlier-arriving threads may still be
     * waiting on entry locks.
     *
     * @param node the waiting node
     * @return another thread's item, or CANCEL
     */
    private Object scanOnTimeout(Node node) {
        Object y;
        for (int j = arena.length - 1; j >= 0; --j) {
            Slot slot = arena[j];
            if (slot != null) {
                while ((y = slot.get()) != null) {
                    if (slot.compareAndSet(y, null)) {
                        Node you = (Node)y;
                        if (you.compareAndSet(null, node.item)) {
                            LockSupport.unpark(you.waiter);
                            return you.item;
                        }
                    }
                }
            }
        }
        return CANCEL;
    }

    /**
     * Creates a new Exchanger.
     */
    public Exchanger() {
    }

    /**
     * Waits for another thread to arrive at this exchange point (unless
     * the current thread is {@linkplain Thread#interrupt interrupted}),
     * and then transfers the given object to it, receiving its object
     * in return.
     *
     * <p>If another thread is already waiting at the exchange point then
     * it is resumed for thread scheduling purposes and receives the object
     * passed in by the current thread.  The current thread returns immediately,
     * receiving the object passed to the exchange by that other thread.
     *
     * <p>If no other thread is already waiting at the exchange then the
     * current thread is disabled for thread scheduling purposes and lies
     * dormant until one of two things happens:
     * <ul>
     * <li>Some other thread enters the exchange; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts} the current
     * thread.
     * </ul>
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@linkplain Thread#interrupt interrupted} while waiting
     * for the exchange,
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     *
     * @param x the object to exchange
     * @return the object provided by the other thread
     * @throws InterruptedException if the current thread was
     *         interrupted while waiting
     */
    public V exchange(V x) throws InterruptedException {
        if (!Thread.interrupted()) {
            Object v = doExchange(x == null? NULL_ITEM : x, false, 0);
            if (v == NULL_ITEM)
                return null;
            if (v != CANCEL)
                return (V)v;
            Thread.interrupted(); // Clear interrupt status on IE throw
        }
        throw new InterruptedException();
    }

    /**
     * Waits for another thread to arrive at this exchange point (unless
     * the current thread is {@linkplain Thread#interrupt interrupted} or
     * the specified waiting time elapses), and then transfers the given
     * object to it, receiving its object in return.
     *
     * <p>If another thread is already waiting at the exchange point then
     * it is resumed for thread scheduling purposes and receives the object
     * passed in by the current thread.  The current thread returns immediately,
     * receiving the object passed to the exchange by that other thread.
     *
     * <p>If no other thread is already waiting at the exchange then the
     * current thread is disabled for thread scheduling purposes and lies
     * dormant until one of three things happens:
     * <ul>
     * <li>Some other thread enters the exchange; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * the current thread; or
     * <li>The specified waiting time elapses.
     * </ul>
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@linkplain Thread#interrupt interrupted} while waiting
     * for the exchange,
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     *
     * <p>If the specified waiting time elapses then {@link
     * TimeoutException} is thrown.  If the time is less than or equal
     * to zero, the method will not wait at all.
     *
     * @param x the object to exchange
     * @param timeout the maximum time to wait
     * @param unit the time unit of the <tt>timeout</tt> argument
     * @return the object provided by the other thread
     * @throws InterruptedException if the current thread was
     *         interrupted while waiting
     * @throws TimeoutException if the specified waiting time elapses
     *         before another thread enters the exchange
     */
    public V exchange(V x, long timeout, TimeUnit unit)
        throws InterruptedException, TimeoutException {
        if (!Thread.interrupted()) {
            Object v = doExchange(x == null? NULL_ITEM : x,
                                  true, unit.toNanos(timeout));
            if (v == NULL_ITEM)
                return null;
            if (v != CANCEL)
                return (V)v;
            if (!Thread.interrupted())
                throw new TimeoutException();
        }
        throw new InterruptedException();
    }
}
