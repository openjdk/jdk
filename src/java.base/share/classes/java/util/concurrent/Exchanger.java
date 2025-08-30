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

import jdk.internal.invoke.MhUtil;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
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
 * <pre> {@code
 * class FillAndEmpty {
 *   Exchanger<DataBuffer> exchanger = new Exchanger<>();
 *   DataBuffer initialEmptyBuffer = ...; // a made-up type
 *   DataBuffer initialFullBuffer = ...;
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
 *       } catch (InterruptedException ex) { ... handle ...}
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
 * }}</pre>
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
     * Overview: The core algorithm is, for an exchange "slot",
     * and a participant (caller) with an item:
     *
     * for (;;) {
     *   if (slot is empty) {                       // offer
     *     place item in a Node;
     *     if (can CAS slot from empty to node) {
     *       wait for release;
     *       return matching item in node;
     *     }
     *   }
     *   else if (can CAS slot from node to empty) { // release
     *     get the item in node;
     *     set matching item in node;
     *     release waiting thread;
     *   }
     *   // else retry on CAS failure
     * }
     *
     * This is among the simplest forms of a "dual data structure" --
     * see Scott and Scherer's DISC 04 paper and
     * http://www.cs.rochester.edu/research/synchronization/pseudocode/duals.html
     *
     * This works great in principle. But in practice, like many
     * algorithms centered on atomic updates to a single location, it
     * scales horribly when there are more than a few participants
     * using the same Exchanger. So the implementation instead uses a
     * form of elimination arena, that spreads out this contention by
     * arranging that some threads typically use different slots,
     * while still ensuring that eventually, any two parties will be
     * able to exchange items. That is, we cannot completely partition
     * across threads, but instead give threads arena indices that
     * will on average grow under contention and shrink under lack of
     * contention.
     *
     * We approach this by defining the Nodes holding references to
     * transfered items as ThreadLocals, and include in them
     * per-thread index and related bookkeeping state. We can safely
     * reuse per-thread nodes rather than creating them fresh each
     * time because slots alternate between pointing to a node vs
     * null, so cannot encounter ABA problems. However, we must ensure
     * that object transfer fields are reset between uses. Given this,
     * Participant nodes can be defined as static ThreadLocals. As
     * seen for example in class Striped64, using indices established
     * in one instance across others usually improves overall
     * performance.  Nodes also include a participant-local random
     * number generator.
     *
     * Spreading out contention requires that the memory locations
     * used by the arena slots don't share a cache line -- otherwise,
     * the arena would have almost no benefit. We arrange this by
     * adding another level of indirection: The arena elements point
     * to "Slots", each of which is padded using @Contended. We only
     * create a single Slot on intialization, adding more when
     * needed. The per-thread Participant Nodes may also be subject to
     * false-sharing contention, but tend to be more scattered in
     * memory, so are unpadded, with some occasional performance impact.
     *
     * The arena starts out with only one used slot. We expand the
     * effective arena size by tracking collisions; i.e., failed CASes
     * while trying to exchange. And shrink it via "spinouts" in which
     * threads give up waiting at a slot.  By nature of the above
     * algorithm, the only kinds of collision that reliably indicate
     * contention are when two attempted releases collide -- one of
     * two attempted offers can legitimately fail to CAS without
     * indicating contention by more than one other thread.
     *
     * Arena size (the value of field "bound") is controlled by random
     * sampling. On each miss (collision or spinout), a thread chooses
     * a new random index within the arena.  Upon the third collision
     * with the same current bound, it tries to grow the arena. And
     * upon the second spinout, it tries to shrink. The asymmetry in
     * part reflects relative costs, and reduces flailing. Because
     * they cannot be changed without also changing the sampling
     * strategy, these rules are directly incorporated into uses of
     * the xchg "misses" variable.  The bound field is tagged with
     * sequence numbers to reduce stale decisions. Uniform random
     * indices are generated using XorShift with enough bits so that
     * bias (See Knuth TAoCP vol 2) is negligible for moduli used here
     * (at most 256) without requiring rejection tests. Using
     * nonuniform randoms with greater weight to higher indices is
     * also possible but does not seem worthwhile in practice.
     *
     * These mechanics rely on a reasonable choice of constant SPINS.
     * The time cost of SPINS * Thread.onSpinWait() should be at least
     * the expected cost of a park/unpark context switch, and larger
     * than that of two failed CASes, but still small enough to avoid
     * excessive delays during arena shrinkage.  We also deal with the
     * possibility that when an offering thread waits for a release,
     * spin-waiting would be useless because the releasing thread is
     * descheduled. On multiprocessors, we cannot know this in
     * general. But when Virtual Threads are used, method
     * ForkJoinWorkerThread.hasKnownQueuedWork serves as a guide to
     * whether to spin or immediately block, allowing a context switch
     * that may enable a releaser.  Note also that when many threads
     * are being run on few cores, enountering enough collisions to
     * trigger arena growth is rare, and soon followed by shrinkage,
     * so this doesn't require special handling.
     *
     * The basic exchange mechanics rely on checks that Node item
     * fields are not null, which doesn't work when offered items are
     * null. We trap this case by translating nulls to the
     * (un-Exchangeable) value of the static Participant
     * reference.
     *
     * Essentially all of the implementation is in method xchg.  As is
     * too common in this sort of code, most of the logic relies on
     * reads of fields that are maintained as local variables so can't
     * be nicely factored. It is structured as a main loop with a
     * leading volatile read (of field bound), that causes others to
     * be freshly read even though declared in plain mode.  We don't
     * use compareAndExchange that would otherwise save some re-reads
     * because of the need to recheck indices and bounds on failures.
     *
     * Support for optional timeouts in a single method adds further
     * complexity. Note that for the sake of arena bounds control,
     * time bounds must be ignored during spinouts, which may delay
     * TimeoutExceptions (but no more so than would excessive context
     * switching that could occur otherwise).  Responses to
     * interruption are handled similarly, postponing commitment to
     * throw InterruptedException until successfully cancelled.
     *
     * Design differences from previous releases include:
     * * Accommodation of VirtualThreads.
     * * Use of Slots vs spaced indices for the arena and static
     *   ThreadLocals, avoiding separate arena vs non-arena modes.
     * * Use of random sampling for grow/shrink decisions, with typically
     *   faster and more stable adaptation (as was mentioned as a
     *   possible improvement in previous version).
     */

    /**
     * The maximum supported arena index. The maximum allocatable
     * arena size is MMASK + 1. Must be a power of two minus one. The
     * cap of 255 (0xff) more than suffices for the expected scaling
     * limits of the main algorithms.
     */
    private static final int MMASK = 0xff;

    /**
     * Unit for sequence/version bits of bound field. Each successful
     * change to the bound also adds SEQ.
     */
    private static final int SEQ = MMASK + 1;

    /**
     * The bound for spins while waiting for a match before either
     * blocking or possibly shrinking arena.
     */
    private static final int SPINS = 1 << 10;

    /**
     * Padded arena cells to avoid false-sharing memory contention
     */
    @jdk.internal.vm.annotation.Contended
    static final class Slot {
        Node entry;
    }

    /**
     * Nodes hold partially exchanged data, plus other per-thread
     * bookkeeping.
     */
    static final class Node {
        long seed;              // Random seed
        int index;              // Arena index
        Object item;            // This thread's current item
        volatile Object match;  // Item provided by releasing thread
        final Thread thread;
        Node() {
            index = -1;         // initialize on first use
            seed = (thread = Thread.currentThread()).threadId();
        }
    }

    /** The corresponding thread local class */
    static final class Participant extends ThreadLocal<Node> {
        public Node initialValue() { return new Node(); }
    }

    /**
     * The participant thread-locals. Because it is impossible to
     * exchange, we also use this reference for dealing with null user
     * arguments that are translated in and out of this value
     * surrounding use.
     */
    private static final Participant participant = new Participant();

    /**
     * Elimination array; element accesses use emulation of volatile
     * gets and CAS.
     */
    private final Slot[] arena;

    /**
     * Number of cores, for sizing and spin control. Computed only
     * upon construction.
     */
    private final int ncpu;

    /**
     * The index of the largest valid arena position.
     */
    private volatile int bound;

    /**
     * Exchange function. See above for explanation.
     *
     * @param x the item to exchange
     * @param deadline if zero, untimed, else timeout deadline
     * @return the other thread's item
     * @throws InterruptedException if interrupted while waiting
     * @throws TimeoutException if deadline nonzero and timed out
     */
    private final V xchg(V x, long deadline)
        throws InterruptedException, TimeoutException {
        Slot[] a = arena;
        int alen = a.length;
        Participant ps = participant;
        Object item = (x == null) ? ps : x;      // translate nulls
        Node p = ps.get();
        p.item = item;
        int i = p.index;                         // if < 0, move
        int misses = 0;                          // ++ on collide, -- on spinout
        Object v;                                // the match
        outer: for (;;) {
            int b, m; Slot s; Node q;
            if ((m = (b = bound) & MMASK) == 0)  // volatile read
                i = 0;
            if (i < 0 || i > m || i >= alen || (s = a[i]) == null) {
                long r = p.seed;                 // randomly move
                r ^= r << 13; r ^= r >>> 7; r ^= r << 17; // xorShift
                i = p.index = (int)((p.seed = r) % (m + 1));
            }
            else if ((q = s.entry) != null) {    // try release
                if (ENTRY.compareAndSet(s, q, null)) {
                    v = q.item;
                    q.match = item;
                    if (i == 0)
                        LockSupport.unpark(q.thread);
                    break;
                }
                else {                           // collision
                    int nb;
                    i = -1;                      // move index
                    if (b != bound)              // stale
                        misses = 0;
                    else if (misses <= 2)        // continue sampling
                        ++misses;
                    else if ((nb = (b + 1) & MMASK) < alen) {
                        misses = 0;              // try to grow
                        if (BOUND.compareAndSet(this, b, b + 1 + SEQ) &&
                            a[i = p.index = nb] == null)
                            AA.compareAndSet(a, nb, null, new Slot());
                    }
                }
            }
            else if (ENTRY.compareAndSet(s, null, p)) { // try offer
                boolean tryCancel = false;
                for (long ns = 1L;;) {
                    if (p.match == null && !tryCancel) {
                        if ((deadline != 0L &&
                             (ns = deadline - System.nanoTime()) <= 0L) ||
                            Thread.currentThread().isInterrupted())
                            tryCancel = true;    // cancel unless match
                        else if (ncpu > 1 &&
                                 (i != 0 ||      // check for busy VTs
                                  (!ForkJoinWorkerThread.hasKnownQueuedWork()))) {
                            for (int j = SPINS; p.match == null && j > 0; --j)
                                Thread.onSpinWait();
                        }
                    }
                    if ((v = MATCH.getAndSet(p, null)) != null)
                        break outer;
                    else if (!tryCancel && i == 0) {
                        if (deadline == 0L)
                            LockSupport.park(this);
                        else
                            LockSupport.parkNanos(this, ns);
                    }
                    else if (ENTRY.compareAndSet(s, p, null)) { // cancel
                        boolean interrupted = Thread.interrupted();
                        if (interrupted || ns <= 0L) {
                            p.item = null;
                            if (interrupted)
                                throw new InterruptedException();
                            else
                                throw new TimeoutException();
                        }
                        else {
                            i = -1;              // move and restart
                            if (bound != b)
                                misses = 0;      // stale
                            else if (misses >= 0)
                                --misses;        // continue sampling
                            else if ((b & MMASK) != 0) {
                                misses = 0;      // try to shrink
                                BOUND.compareAndSet(this, b, b - 1 + SEQ);
                            }
                            break;
                        }
                    }
                }
            }
        }
        p.item = null;                           // cleanup
        @SuppressWarnings("unchecked") V ret = (v == participant) ? null : (V)v;
        return ret;
    }

    /**
     * Creates a new Exchanger.
     */
    public Exchanger() {
        int h = (ncpu = Runtime.getRuntime().availableProcessors()) >>> 1;
        int size = (h == 0) ? 1 : (h > MMASK) ? MMASK + 1 : h;
        (arena = new Slot[size])[0] = new Slot();
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
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * the current thread.
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
        try {
            return xchg(x, 0L);
        } catch (TimeoutException cannotHappen) {
            return null; // not reached
        }
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
     * @param unit the time unit of the {@code timeout} argument
     * @return the object provided by the other thread
     * @throws InterruptedException if the current thread was
     *         interrupted while waiting
     * @throws TimeoutException if the specified waiting time elapses
     *         before another thread enters the exchange
     */
    public V exchange(V x, long timeout, TimeUnit unit)
        throws InterruptedException, TimeoutException {
        long d = unit.toNanos(timeout) + System.nanoTime();
        return xchg(x, (d == 0L) ? 1L : d); // avoid zero deadline
    }

    // VarHandle mechanics
    private static final VarHandle BOUND;
    private static final VarHandle MATCH;
    private static final VarHandle ENTRY;
    private static final VarHandle AA;
    static {
        MethodHandles.Lookup l = MethodHandles.lookup();
        BOUND = MhUtil.findVarHandle(l, "bound", int.class);
        MATCH = MhUtil.findVarHandle(l, Node.class, "match", Object.class);
        ENTRY = MhUtil.findVarHandle(l, Slot.class, "entry", Node.class);
        AA = MethodHandles.arrayElementVarHandle(Slot[].class);
    }

}
