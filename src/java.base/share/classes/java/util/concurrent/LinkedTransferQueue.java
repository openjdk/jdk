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
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.AbstractQueue;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Queue;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * An unbounded {@link TransferQueue} based on linked nodes.
 * This queue orders elements FIFO (first-in-first-out) with respect
 * to any given producer.  The <em>head</em> of the queue is that
 * element that has been on the queue the longest time for some
 * producer.  The <em>tail</em> of the queue is that element that has
 * been on the queue the shortest time for some producer.
 *
 * <p>Beware that, unlike in most collections, the {@code size} method
 * is <em>NOT</em> a constant-time operation. Because of the
 * asynchronous nature of these queues, determining the current number
 * of elements requires a traversal of the elements, and so may report
 * inaccurate results if this collection is modified during traversal.
 *
 * <p>Bulk operations that add, remove, or examine multiple elements,
 * such as {@link #addAll}, {@link #removeIf} or {@link #forEach},
 * are <em>not</em> guaranteed to be performed atomically.
 * For example, a {@code forEach} traversal concurrent with an {@code
 * addAll} operation might observe only some of the added elements.
 *
 * <p>This class and its iterator implement all of the <em>optional</em>
 * methods of the {@link Collection} and {@link Iterator} interfaces.
 *
 * <p>Memory consistency effects: As with other concurrent
 * collections, actions in a thread prior to placing an object into a
 * {@code LinkedTransferQueue}
 * <a href="package-summary.html#MemoryVisibility"><i>happen-before</i></a>
 * actions subsequent to the access or removal of that element from
 * the {@code LinkedTransferQueue} in another thread.
 *
 * <p>This class is a member of the
 * <a href="{@docRoot}/java.base/java/util/package-summary.html#CollectionsFramework">
 * Java Collections Framework</a>.
 *
 * @since 1.7
 * @author Doug Lea
 * @param <E> the type of elements held in this queue
 */
public class LinkedTransferQueue<E> extends AbstractQueue<E>
    implements TransferQueue<E>, java.io.Serializable {
    private static final long serialVersionUID = -3223113410248163686L;

    /*
     * *** Overview of Dual Queues with Slack ***
     *
     * Dual Queues, introduced by Scherer and Scott
     * (http://www.cs.rochester.edu/~scott/papers/2004_DISC_dual_DS.pdf)
     * are (linked) queues in which nodes may represent either data or
     * requests.  When a thread tries to enqueue a data node, but
     * encounters a request node, it instead "matches" and removes it;
     * and vice versa for enqueuing requests. Blocking Dual Queues
     * arrange that threads enqueuing unmatched requests block until
     * other threads provide the match. Dual Synchronous Queues (see
     * Scherer, Lea, & Scott
     * http://www.cs.rochester.edu/u/scott/papers/2009_Scherer_CACM_SSQ.pdf)
     * additionally arrange that threads enqueuing unmatched data also
     * block.  Dual Transfer Queues support all of these modes, as
     * dictated by callers. All enqueue/dequeue operations can be
     * handled by a single method (here, "xfer") with parameters
     * indicating whether to act as some form of offer, put, poll,
     * take, or transfer (each possibly with timeout), as described
     * below.
     *
     * A FIFO dual queue may be implemented using a variation of the
     * Michael & Scott (M&S) lock-free queue algorithm
     * (http://www.cs.rochester.edu/~scott/papers/1996_PODC_queues.pdf).
     * It maintains two pointer fields, "head", pointing to a
     * (matched) node that in turn points to the first actual
     * (unmatched) queue node (or null if empty); and "tail" that
     * points to the last node on the queue (or again null if
     * empty). For example, here is a possible queue with four data
     * elements:
     *
     *  head                tail
     *    |                   |
     *    v                   v
     *    M -> U -> U -> U -> U
     *
     * The M&S queue algorithm is known to be prone to scalability and
     * overhead limitations when maintaining (via CAS) these head and
     * tail pointers. To address these, dual queues with slack differ
     * from plain M&S dual queues by virtue of only sometimes updating
     * head or tail pointers when matching, appending, or even
     * traversing nodes.
     *
     * In a dual queue, each node must atomically maintain its match
     * status. Matching entails CASing an "item" field from a non-null
     * data value to null upon match, and vice-versa for request
     * nodes, CASing from null to a data value.  (To reduce the need
     * for re-reads, we use the compareAndExchange forms of CAS for
     * pointer updates, that provide the current value to continue
     * with on failure.)  Note that the linearization properties of
     * this style of queue are easy to verify -- elements are made
     * available by linking, and unavailable by matching. Compared to
     * plain M&S queues, this property of dual queues requires one
     * additional successful atomic operation per enq/deq pair. But it
     * also enables lower cost variants of queue maintenance
     * mechanics.
     *
     * Once a node is matched, it is no longer live -- its match
     * status can never again change.  We may thus arrange that the
     * linked list of them contain a prefix of zero or more dead
     * nodes, followed by a suffix of zero or more live nodes. Note
     * that we allow both the prefix and suffix to be zero length,
     * which in turn means that we do not require a dummy header.
     *
     * We use here an approach that lies between the extremes of
     * never versus always updating queue (head and tail) pointers.
     * This offers a tradeoff between sometimes requiring extra
     * traversal steps to locate the first and/or last unmatched
     * nodes, versus the reduced overhead and contention of fewer
     * updates to queue pointers. For example, a possible snapshot of
     * a queue is:
     *
     *  head           tail
     *    |              |
     *    v              v
     *    M -> M -> U -> U -> U -> U
     *
     * The best value for this "slack" (the targeted maximum distance
     * between the value of "head" and the first unmatched node, and
     * similarly for "tail") is an empirical matter. Larger values
     * introduce increasing costs of cache misses and risks of long
     * traversal chains and out-of-order updates, while smaller values
     * increase CAS contention and overhead. Using the smallest
     * non-zero value of one is both simple and empirically a good
     * choice in most applicatkions.  The slack value is hard-wired: a
     * path greater than one is usually implemented by checking
     * equality of traversal pointers.  Because CASes updating fields
     * attempting to do so may stall, the writes may appear out of
     * order (an older CAS from the same head or tail may execute
     * after a newer one), the actual slack may exceed targeted
     * slack. To reduce impact, other threads may help update by
     * unsplicing dead nodes while traversing.
     *
     * These ideas must be further extended to avoid unbounded amounts
     * of costly-to-reclaim garbage caused by the sequential "next"
     * links of nodes starting at old forgotten head nodes: As first
     * described in detail by Boehm
     * (http://portal.acm.org/citation.cfm?doid=503272.503282), if a
     * GC delays noticing that any arbitrarily old node has become
     * garbage, all newer dead nodes will also be unreclaimed.
     * (Similar issues arise in non-GC environments.)  To cope with
     * this in our implementation, upon advancing the head pointer, we
     * set the "next" link of the previous head to point only to
     * itself; thus limiting the length of chains of dead nodes.  (We
     * also take similar care to wipe out possibly garbage retaining
     * values held in other node fields.) This is easy to accommodate
     * in the primary xfer method, but adds a lot of complexity to
     * Collection operations including traversal; mainly because if
     * any "next" pointer links to itself, the current thread has
     * lagged behind a head-update, and so must restart.
     *
     * *** Blocking ***
     *
     * The DualNode class is shared with class SynchronousQueue. It
     * houses method await, which is used for all blocking control, as
     * described below in DualNode internal documentation.
     *
     * ** Unlinking removed interior nodes **
     *
     * In addition to minimizing garbage retention via self-linking
     * described above, we also unlink removed interior nodes. These
     * may arise due to timed out or interrupted waits, or calls to
     * remove(x) or Iterator.remove.  Normally, given a node that was
     * at one time known to be the predecessor of some node s that is
     * to be removed, we can unsplice s by CASing the next field of
     * its predecessor if it still points to s (otherwise s must
     * already have been removed or is now offlist). But there are two
     * situations in which we cannot guarantee to make node s
     * unreachable in this way: (1) If s is the trailing node of list
     * (i.e., with null next), then it is pinned as the target node
     * for appends, so can only be removed later after other nodes are
     * appended. (2) Unless we know it is already off-list, we cannot
     * necessarily unlink s given a predecessor node that is matched
     * (including the case of being cancelled): the predecessor may
     * already be unspliced, in which case some previous reachable
     * node may still point to s.  (For further explanation see
     * Herlihy & Shavit "The Art of Multiprocessor Programming"
     * chapter 9).
     *
     * Without taking these into account, it would be possible for an
     * unbounded number of supposedly removed nodes to remain reachable.
     * Situations leading to such buildup are uncommon but can occur
     * in practice; for example when a series of short timed calls to
     * poll repeatedly time out at the trailing node but otherwise
     * never fall off the list because of an untimed call to take() at
     * the front of the queue.
     *
     * When these cases arise, rather than always retraversing the
     * entire list to find an actual predecessor to unlink (which
     * won't help for case (1) anyway), we record a conservative
     * estimate of possible unsplice failures (in "sweepVotes").
     * We trigger a full sweep when the estimate exceeds a threshold
     * ("SWEEP_THRESHOLD") indicating the maximum number of estimated
     * removal failures to tolerate before sweeping through, unlinking
     * cancelled nodes that were not unlinked upon initial removal.
     * We perform sweeps by the thread hitting threshold (rather than
     * background threads or by spreading work to other threads)
     * because in the main contexts in which removal occurs, the
     * caller is timed-out or cancelled, which are not time-critical
     * enough to warrant the overhead that alternatives would impose
     * on other threads.
     *
     * Because the sweepVotes estimate is conservative, and because
     * nodes become unlinked "naturally" as they fall off the head of
     * the queue, and because we allow votes to accumulate even while
     * sweeps are in progress, there are typically significantly fewer
     * such nodes than estimated.
     *
     * Note that we cannot self-link unlinked interior nodes during
     * sweeps. However, the associated garbage chains terminate when
     * some successor ultimately falls off the head of the list and is
     * self-linked.
     *
     * *** Revision notes ***
     *
     * This version differs from previous releases as follows:
     *
     * * Class DualNode replaces Qnode, with fields and methods
     *   that apply to any match-based dual data structure, and now
     *   usable in other j.u.c classes. in particular, SynchronousQueue.
     * * Blocking control (in class DualNode) accommodates
     *   VirtualThreads and (perhaps virtualized) uniprocessors.
     * * All fields of this class (LinkedTransferQueue) are
     *   default-initializable (to null), allowing further extension
     *   (in particular, SynchronousQueue.Transferer)
     * * Head and tail fields are lazily initialized rather than set
     *   to a dummy node, while also reducing retries under heavy
     *   contention and misorderings, and relaxing some accesses,
     *   requiring accommodation in many places (as well as
     *   adjustments in WhiteBox tests).
     */

    /**
     * Node for linked dual data structures. Uses type Object, not E,
     * for items to allow cancellation and forgetting after use. Only
     * field "item" is declared volatile (with bypasses for
     * pre-publication and post-match writes), although field "next"
     * is also CAS-able. Other accesses are constrained by context
     * (including dependent chains of next's headed by a volatile
     * read).
     *
     * This class also arranges blocking while awaiting matches.
     * Control of blocking (and thread scheduling in general) for
     * possibly-synchronous queues (and channels etc constructed
     * from them) must straddle two extremes: If there are too few
     * underlying cores for a fulfilling party to continue, then
     * the caller must park to cause a context switch. On the
     * other hand, if the queue is busy with approximately the
     * same number of independent producers and consumers, then
     * that context switch may cause an order-of-magnitude
     * slowdown. Many cases are somewhere in-between, in which
     * case threads should try spinning and then give up and
     * block. We deal with this as follows:
     *
     * 1. Callers to method await indicate eligibility for
     * spinning when the node is either the only waiting node, or
     * the next matchable node is still spinning.  Otherwise, the
     * caller may block (almost) immediately.
     *
     * 2. Even if eligible to spin, a caller blocks anyway in two
     * cases where it is normally best: If the thread isVirtual,
     * or the system is a uniprocessor. Uniprocessor status can
     * vary over time (due to virtualization at other system
     * levels), but checking Runtime availableProcessors can be
     * slow and may itself acquire blocking locks, so we only
     * occasionally (using ThreadLocalRandom) update when an
     * otherwise-eligible spin elapses.
     *
     * 3. When enabled, spins should be long enough to cover
     * bookeeping overhead of almost-immediate fulfillments, but
     * much less than the expected time of a (non-virtual)
     * park/unpark context switch.  The optimal value is
     * unknowable, in part because the relative costs of
     * Thread.onSpinWait versus park/unpark vary across platforms.
     * The current value is an empirical compromise across tested
     * platforms.
     *
     * 4. When using timed waits, callers spin instead of invoking
     * timed park if the remaining time is less than the likely cost
     * of park/unpark. This also avoids re-parks when timed park
     * returns just barely too soon. As is the case in most j.u.c
     * blocking support, untimed waits use ManagedBlockers when
     * callers are ForkJoin threads, but timed waits use plain
     * parkNanos, under the rationale that known-to-be transient
     * blocking doesn't require compensation. (This decision should be
     * revisited here and elsewhere to deal with very long timeouts.)
     *
     * 5. Park/unpark signalling otherwise relies on a Dekker-like
     * scheme in which the caller advertises the need to unpark by
     * setting its waiter field, followed by a full fence and recheck
     * before actually parking. An explicit fence in used here rather
     * than unnecessarily requiring volatile accesses elsewhere. This
     * fence also separates accesses to field isUniprocessor.
     *
     * 6. To make the above work, callers must precheck that
     * timeouts are not already elapsed, and that interruptible
     * operations were not already interrupted on call to the
     * corresponding queue operation.  Cancellation on timeout or
     * interrupt otherwise proceeds by trying to fulfill with an
     * impossible value (which is one reason that we use Object
     * types here rather than typed fields).
     */
    static final class DualNode implements ForkJoinPool.ManagedBlocker {
        volatile Object item;   // initially non-null if isData; CASed to match
        DualNode next;          // accessed only in chains of volatile ops
        Thread waiter;          // access order constrained by context
        final boolean isData;   // false if this is a request node

        DualNode(Object item, boolean isData) {
            ITEM.set(this, item); // relaxed write before publication
            this.isData = isData;
        }

        // Atomic updates
        final Object cmpExItem(Object cmp, Object val) { // try to match
            return ITEM.compareAndExchange(this, cmp, val);
        }
        final DualNode cmpExNext(DualNode cmp, DualNode val) {
            return (DualNode)NEXT.compareAndExchange(this, cmp, val);
        }

        /** Returns true if this node has been matched or cancelled  */
        final boolean matched() {
            return isData != (item != null);
        }

        /**
         * Relaxed write to replace reference to user data with
         * self-link. Can be used only if not already null after
         * match.
         */
        final void selfLinkItem() {
            ITEM.set(this, this);
        }

        /** The number of times to spin when eligible */
        private static final int SPINS = 1 << 7;

        /**
         * The number of nanoseconds for which it is faster to spin
         * rather than to use timed park. A rough estimate suffices.
         */
        private static final long SPIN_FOR_TIMEOUT_THRESHOLD = 1L << 10;

        /**
         * True if system is a uniprocessor, occasionally rechecked.
         */
        private static boolean isUniprocessor =
            (Runtime.getRuntime().availableProcessors() == 1);

        /**
         * Refresh rate (probablility) for updating isUniprocessor
         * field, to reduce the likeihood that multiple calls to await
         * will contend invoking Runtime.availableProcessors.  Must be
         * a power of two minus one.
         */
        private static final int UNIPROCESSOR_REFRESH_RATE = (1 << 5) - 1;

        /**
         * Possibly blocks until matched or caller gives up.
         *
         * @param e the comparison value for checking match
         * @param ns timeout, or Long.MAX_VALUE if untimed
         * @param blocker the LockSupport.setCurrentBlocker argument
         * @param spin true if should spin when enabled
         * @return matched item, or e if unmatched on interrupt or timeout
         */
        final Object await(Object e, long ns, Object blocker, boolean spin) {
            Object m;                      // the match or e if none
            boolean timed = (ns != Long.MAX_VALUE);
            long deadline = (timed) ? System.nanoTime() + ns : 0L;
            boolean upc = isUniprocessor;  // don't spin but later recheck
            Thread w = Thread.currentThread();
            if (w.isVirtual())             // don't spin
                spin = false;
            int spins = (spin & !upc) ? SPINS : 0; // negative when may park
            while ((m = item) == e) {
                if (spins >= 0) {
                    if (--spins >= 0)
                        Thread.onSpinWait();
                    else {                 // prepare to park
                        if (spin)          // occasionally recheck
                            checkForUniprocessor(upc);
                        LockSupport.setCurrentBlocker(blocker);
                        waiter = w;        // ensure ordering
                        VarHandle.fullFence();
                    }
                } else if (w.isInterrupted() ||
                           (timed &&       // try to cancel with impossible match
                            ((ns = deadline - System.nanoTime()) <= 0L))) {
                    m = cmpExItem(e, (e == null) ? this : null);
                    break;
                } else if (timed) {
                    if (ns < SPIN_FOR_TIMEOUT_THRESHOLD)
                        Thread.onSpinWait();
                    else
                        LockSupport.parkNanos(ns);
                } else if (w instanceof ForkJoinWorkerThread) {
                    try {
                        ForkJoinPool.managedBlock(this);
                    } catch (InterruptedException cannotHappen) { }
                } else
                    LockSupport.park();
            }
            if (spins < 0) {
                LockSupport.setCurrentBlocker(null);
                waiter = null;
            }
            return m;
        }

        /** Occasionally updates isUniprocessor field */
        private void checkForUniprocessor(boolean prev) {
            int r = ThreadLocalRandom.nextSecondarySeed();
            if ((r & UNIPROCESSOR_REFRESH_RATE) == 0) {
                boolean u = (Runtime.getRuntime().availableProcessors() == 1);
                if (u != prev)
                    isUniprocessor = u;
            }
        }

        // ManagedBlocker support
        public final boolean isReleasable() {
            return (matched() || Thread.currentThread().isInterrupted());
        }
        public final boolean block() {
            while (!isReleasable()) LockSupport.park();
            return true;
        }

        // VarHandle mechanics
        static final VarHandle ITEM;
        static final VarHandle NEXT;
        static {
            try {
                Class<?> tn = DualNode.class;
                MethodHandles.Lookup l = MethodHandles.lookup();
                ITEM = l.findVarHandle(tn, "item", Object.class);
                NEXT = l.findVarHandle(tn, "next", tn);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
            // Reduce the risk of rare disastrous classloading in first call to
            // LockSupport.park: https://bugs.openjdk.org/browse/JDK-8074773
            Class<?> ensureLoaded = LockSupport.class;
        }
    }

    /**
     * Unless empty (in which case possibly null), a node from which
     * all live nodes are reachable.
     * Invariants:
     * - head is never self-linked
     * Non-invariants:
     * - head may or may not be live
     *
     * This field is used by subclass SynchronousQueue.Transferer to
     * record the top of a Lifo stack, with tail always null, but
     * otherwise maintaining the same properties.
     */
    transient volatile DualNode head;

    /**
     * Unless null, a node from which the last node on list (that is,
     * the unique node with node.next == null), if one exists, can be
     * reached.
     * Non-invariants:
     * - tail may or may not be live
     * - tail may be the same as head
     * - tail may or may not be self-linked.
     * - tail may lag behind head, so need not be reachable from head
     */
    transient volatile DualNode tail;

    /** The number of apparent failures to unsplice cancelled nodes */
    transient volatile int sweepVotes;

    // Atomic updates

    final DualNode cmpExTail(DualNode cmp, DualNode val) {
        return (DualNode)TAIL.compareAndExchange(this, cmp, val);
    }
    final DualNode cmpExHead(DualNode cmp, DualNode val) {
        return (DualNode)HEAD.compareAndExchange(this, cmp, val);
    }

    /**
     * The maximum number of estimated removal failures (sweepVotes)
     * to tolerate before sweeping through the queue unlinking
     * dead nodes that were initially pinned.  Must be a power of
     * two minus one, at least 3.
     */
    static final int SWEEP_THRESHOLD = (1 << 4) - 1;

    /**
     * Adds a sweepVote and returns true if triggered threshold.
     */
    final boolean sweepNow() {
        return (SWEEP_THRESHOLD ==
                ((int)SWEEPVOTES.getAndAdd(this, 1) & (SWEEP_THRESHOLD)));
    }

    /**
     * Implements all queuing methods. Loops, trying:
     *
     * * If not initialized, try to add new node (unless immediate) and exit
     * * If tail has same mode, start traversing at tail for a likely
     *   append, else at head for a likely match
     * * Traverse over dead or wrong-mode nodes until finding a spot
     *   to match/append, or falling off the list because of self-links.
     * * On success, update head or tail if slacked, and possibly wait,
     *   depending on ns argument
     *
     * @param e the item or null for take
     * @param ns timeout or negative if async, 0 if immediate,
     *        Long.MAX_VALUE if untimed
     * @return an item if matched, else e
     */
    final Object xfer(Object e, long ns) {
        boolean haveData = (e != null);
        Object m;                           // the match or e if none
        DualNode s = null, p;               // enqueued node and its predecessor
        restart: for (DualNode prevp = null;;) {
            DualNode h, t, q;
            if ((h = head) == null &&       // initialize unless immediate
                (ns == 0L ||
                 (h = cmpExHead(null, s = new DualNode(e, haveData))) == null)) {
                p = null;                   // no predecessor
                break;                      // else lost init race
            }
            p = (t = tail) != null && t.isData == haveData && t != prevp ? t : h;
            prevp = p;                      // avoid known self-linked tail path
            do {
                m = p.item;
                q = p.next;
                if (p.isData != haveData && haveData != (m != null) &&
                    p.cmpExItem(m, e) == m) {
                    Thread w = p.waiter;    // matched complementary node
                    if (p != h && h == cmpExHead(h, (q == null) ? p : q))
                        h.next = h;         // advance head; self-link old
                    LockSupport.unpark(w);
                    return m;
                } else if (q == null) {
                    if (ns == 0L)           // try to append unless immediate
                        break restart;
                    if (s == null)
                        s = new DualNode(e, haveData);
                    if ((q = p.cmpExNext(null, s)) == null) {
                        if (p != t)
                            cmpExTail(t, s);
                        break restart;
                    }
                }
            } while (p != (p = q));         // restart if self-linked
        }
        if (s == null || ns <= 0L)
            m = e;                          // don't wait
        else if ((m = s.await(e, ns, this,  // spin if at or near head
                              p == null || p.waiter == null)) == e)
            unsplice(p, s);                 // cancelled
        else if (m != null)
            s.selfLinkItem();

        return m;
    }

    /* --------------  Removals -------------- */

    /**
     * Unlinks (now or later) the given (non-live) node with given
     * predecessor. See above for rationale.
     *
     * @param pred if nonnull, a node that was at one time known to be the
     * predecessor of s (else s may have been head)
     * @param s the node to be unspliced
     */
    private void unsplice(DualNode pred, DualNode s) {
        boolean seen = false; // try removing by collapsing head
        for (DualNode h = head, p = h, f; p != null;) {
            boolean matched;
            if (p == s)
                matched = seen = true;
            else
                matched = p.matched();
            if ((f = p.next) == p)
                p = h = head;
            else if (f != null && matched)
                p = f;
            else {
                if (p != h && cmpExHead(h, p) == h)
                    h.next = h; // self-link
                break;
            }
        }
        DualNode sn;      // try to unsplice if not pinned
        if (!seen &&
            pred != null && pred.next == s && s != null && (sn = s.next) != s &&
            (sn == null || pred.cmpExNext(s, sn) != s || pred.matched()) &&
            sweepNow()) { // occasionally sweep if might not have been removed
            for (DualNode p = head, f, n, u;
                 p != null && (f = p.next) != null && (n = f.next) != null;) {
                p = (f == p                       ? head :  // stale
                     !f.matched()                 ? f :     // skip
                     f == (u = p.cmpExNext(f, n)) ? n : u); // unspliced
            }
        }
    }

    /**
     * Tries to CAS pred.next (or head, if pred is null) from c to p.
     * Caller must ensure that we're not unlinking the trailing node.
     */
    final boolean tryCasSuccessor(DualNode pred, DualNode c, DualNode p) {
        // assert p != null && c.matched() && c != p;
        if (pred != null)
            return pred.cmpExNext(c, p) == c;
        else if (cmpExHead(c, p) != c)
            return false;
        if (c != null)
            c.next = c;

        return true;
    }

    /**
     * Collapses dead (matched) nodes between pred and q.
     * @param pred the last known live node, or null if none
     * @param c the first dead node
     * @param p the last dead node
     * @param q p.next: the next live node, or null if at end
     * @return pred if pred still alive and CAS succeeded; else p
     */
    final DualNode skipDeadNodes(DualNode pred, DualNode c,
                                 DualNode p, DualNode q) {
        // assert pred != c && p != q; && c.matched() && p.matched();
        if (q == null) { // Never unlink trailing node.
            if (c == p)
                return pred;
            q = p;
        }
        return (tryCasSuccessor(pred, c, q) && (pred == null || !pred.matched()))
            ? pred : p;
    }

    /**
     * Tries to match the given object only if p is a data
     * node. Signals waiter on success.
     */
    final boolean tryMatchData(DualNode p, Object x) {
        if (p != null && p.isData &&
            x != null && p.cmpExItem(x, null) == x) {
            LockSupport.unpark(p.waiter);
            return true;
        }
        return false;
    }

    /* -------------- Traversal methods -------------- */

    /**
     * Returns the first unmatched data node, or null if none.
     * Callers must recheck if the returned node is unmatched
     * before using.
     */
    final DualNode firstDataNode() {
        for (DualNode h = head, p = h, q, u; p != null;) {
            boolean isData = p.isData;
            Object item = p.item;
            if (isData && item != null)       // is live data
                return p;
            else if (!isData && item == null) // is live request
                break;
            else if ((q = p.next) == null)    // end of list
                break;
            else if (p == q)                  // self-link; restart
                p = h = head;
            else if (p == h)                  // traverse past header
                p = q;
            else if ((u = cmpExHead(h, q)) != h)
                p = h = u;                    // lost update race
            else {
                h.next = h;                   // collapse; self-link
                p = h = q;
            }
        }
        return null;
    }

    /**
     * Traverses and counts unmatched nodes of the given mode.
     * Used by methods size and getWaitingConsumerCount.
     */
    final int countOfMode(boolean data) {
        restartFromHead: for (;;) {
            int count = 0;
            for (DualNode p = head; p != null;) {
                if (!p.matched()) {
                    if (p.isData != data)
                        return 0;
                    if (++count == Integer.MAX_VALUE)
                        break;  // @see Collection.size()
                }
                if (p == (p = p.next))
                    continue restartFromHead;
            }
            return count;
        }
    }

    public String toString() {
        String[] a = null;
        restartFromHead: for (;;) {
            int charLength = 0;
            int size = 0;
            for (DualNode p = head; p != null;) {
                Object item = p.item;
                if (p.isData) {
                    if (item != null) {
                        if (a == null)
                            a = new String[4];
                        else if (size == a.length)
                            a = Arrays.copyOf(a, 2 * size);
                        String s = item.toString();
                        a[size++] = s;
                        charLength += s.length();
                    }
                } else if (item == null)
                    break;
                if (p == (p = p.next))
                    continue restartFromHead;
            }

            if (size == 0)
                return "[]";

            return Helpers.toString(a, size, charLength);
        }
    }

    private Object[] toArrayInternal(Object[] a) {
        Object[] x = a;
        restartFromHead: for (;;) {
            int size = 0;
            for (DualNode p = head; p != null;) {
                Object item = p.item;
                if (p.isData) {
                    if (item != null) {
                        if (x == null)
                            x = new Object[4];
                        else if (size == x.length)
                            x = Arrays.copyOf(x, 2 * (size + 4));
                        x[size++] = item;
                    }
                } else if (item == null)
                    break;
                if (p == (p = p.next))
                    continue restartFromHead;
            }
            if (x == null)
                return new Object[0];
            else if (a != null && size <= a.length) {
                if (a != x)
                    System.arraycopy(x, 0, a, 0, size);
                if (size < a.length)
                    a[size] = null;
                return a;
            }
            return (size == x.length) ? x : Arrays.copyOf(x, size);
        }
    }

    /**
     * Returns an array containing all of the elements in this queue, in
     * proper sequence.
     *
     * <p>The returned array will be "safe" in that no references to it are
     * maintained by this queue.  (In other words, this method must allocate
     * a new array).  The caller is thus free to modify the returned array.
     *
     * <p>This method acts as bridge between array-based and collection-based
     * APIs.
     *
     * @return an array containing all of the elements in this queue
     */
    public Object[] toArray() {
        return toArrayInternal(null);
    }

    /**
     * Returns an array containing all of the elements in this queue, in
     * proper sequence; the runtime type of the returned array is that of
     * the specified array.  If the queue fits in the specified array, it
     * is returned therein.  Otherwise, a new array is allocated with the
     * runtime type of the specified array and the size of this queue.
     *
     * <p>If this queue fits in the specified array with room to spare
     * (i.e., the array has more elements than this queue), the element in
     * the array immediately following the end of the queue is set to
     * {@code null}.
     *
     * <p>Like the {@link #toArray()} method, this method acts as bridge between
     * array-based and collection-based APIs.  Further, this method allows
     * precise control over the runtime type of the output array, and may,
     * under certain circumstances, be used to save allocation costs.
     *
     * <p>Suppose {@code x} is a queue known to contain only strings.
     * The following code can be used to dump the queue into a newly
     * allocated array of {@code String}:
     *
     * <pre> {@code String[] y = x.toArray(new String[0]);}</pre>
     *
     * Note that {@code toArray(new Object[0])} is identical in function to
     * {@code toArray()}.
     *
     * @param a the array into which the elements of the queue are to
     *          be stored, if it is big enough; otherwise, a new array of the
     *          same runtime type is allocated for this purpose
     * @return an array containing all of the elements in this queue
     * @throws ArrayStoreException if the runtime type of the specified array
     *         is not a supertype of the runtime type of every element in
     *         this queue
     * @throws NullPointerException if the specified array is null
     */
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        Objects.requireNonNull(a);
        return (T[]) toArrayInternal(a);
    }

    /**
     * Weakly-consistent iterator.
     *
     * Lazily updated ancestor is expected to be amortized O(1) remove(),
     * but O(n) in the worst case, when lastRet is concurrently deleted.
     */
    final class Itr implements Iterator<E> {
        private DualNode nextNode;   // next node to return item for
        private E nextItem;          // the corresponding item
        private DualNode lastRet;    // last returned node, to support remove
        private DualNode ancestor;   // Helps unlink lastRet on remove()

        /**
         * Moves to next node after pred, or first node if pred null.
         */
        @SuppressWarnings("unchecked")
        private void advance(DualNode pred) {
            for (DualNode p = (pred == null) ? head : pred.next, c = p;
                 p != null; ) {
                boolean isData = p.isData;
                Object item = p.item;
                if (isData && item != null) {
                    nextNode = p;
                    nextItem = (E) item;
                    if (c != p)
                        tryCasSuccessor(pred, c, p);
                    return;
                }
                else if (!isData && item == null)
                    break;
                if (c != p && !tryCasSuccessor(pred, c, c = p)) {
                    pred = p;
                    c = p = p.next;
                }
                else if (p == (p = p.next)) {
                    pred = null;
                    c = p = head;
                }
            }
            nextItem = null;
            nextNode = null;
        }

        Itr() {
            advance(null);
        }

        public final boolean hasNext() {
            return nextNode != null;
        }

        public final E next() {
            DualNode p;
            if ((p = nextNode) == null) throw new NoSuchElementException();
            E e = nextItem;
            advance(lastRet = p);
            return e;
        }

        public void forEachRemaining(Consumer<? super E> action) {
            Objects.requireNonNull(action);
            DualNode q = null;
            for (DualNode p; (p = nextNode) != null; advance(q = p))
                action.accept(nextItem);
            if (q != null)
                lastRet = q;
        }

        public final void remove() {
            final DualNode lastRet = this.lastRet;
            if (lastRet == null)
                throw new IllegalStateException();
            this.lastRet = null;
            if (lastRet.item == null)   // already deleted?
                return;
            // Advance ancestor, collapsing intervening dead nodes
            DualNode pred = ancestor;
            for (DualNode p = (pred == null) ? head : pred.next, c = p, q;
                 p != null; ) {
                if (p == lastRet) {
                    tryMatchData(p, p.item);
                    if ((q = p.next) == null) q = p;
                    if (c != q) tryCasSuccessor(pred, c, q);
                    ancestor = pred;
                    return;
                }
                final Object item; final boolean pAlive;
                if (pAlive = ((item = p.item) != null && p.isData)) {
                    // exceptionally, nothing to do
                }
                else if (!p.isData && item == null)
                    break;
                if ((c != p && !tryCasSuccessor(pred, c, c = p)) || pAlive) {
                    pred = p;
                    c = p = p.next;
                }
                else if (p == (p = p.next)) {
                    pred = null;
                    c = p = head;
                }
            }
            // traversal failed to find lastRet; must have been deleted;
            // leave ancestor at original location to avoid overshoot;
            // better luck next time!

            // assert lastRet.matched();
        }
    }

    /** A customized variant of Spliterators.IteratorSpliterator */
    final class LTQSpliterator implements Spliterator<E> {
        static final int MAX_BATCH = 1 << 25;  // max batch array size;
        DualNode current;   // current node; null until initialized
        int batch;          // batch size for splits
        boolean exhausted;  // true when no more nodes
        LTQSpliterator() {}

        public Spliterator<E> trySplit() {
            DualNode p, q;
            if ((p = current()) == null || (q = p.next) == null)
                return null;
            int i = 0, n = batch = Math.min(batch + 1, MAX_BATCH);
            Object[] a = null;
            do {
                final Object item = p.item;
                if (p.isData) {
                    if (item != null) {
                        if (a == null)
                            a = new Object[n];
                        a[i++] = item;
                    }
                } else if (item == null) {
                    p = null;
                    break;
                }
                if (p == (p = q))
                    p = firstDataNode();
            } while (p != null && (q = p.next) != null && i < n);
            setCurrent(p);
            return (i == 0) ? null :
                Spliterators.spliterator(a, 0, i, (Spliterator.ORDERED |
                                                   Spliterator.NONNULL |
                                                   Spliterator.CONCURRENT));
        }

        public void forEachRemaining(Consumer<? super E> action) {
            Objects.requireNonNull(action);
            final DualNode p;
            if ((p = current()) != null) {
                current = null;
                exhausted = true;
                forEachFrom(action, p);
            }
        }

        @SuppressWarnings("unchecked")
        public boolean tryAdvance(Consumer<? super E> action) {
            Objects.requireNonNull(action);
            DualNode p;
            if ((p = current()) != null) {
                E e = null;
                do {
                    boolean isData = p.isData;
                    Object item = p.item;
                    if (p == (p = p.next))
                        p = head;
                    if (isData) {
                        if (item != null) {
                            e = (E) item;
                            break;
                        }
                    }
                    else if (item == null)
                        p = null;
                } while (p != null);
                setCurrent(p);
                if (e != null) {
                    action.accept(e);
                    return true;
                }
            }
            return false;
        }

        private void setCurrent(DualNode p) {
            if ((current = p) == null)
                exhausted = true;
        }

        private DualNode current() {
            DualNode p;
            if ((p = current) == null && !exhausted)
                setCurrent(p = firstDataNode());
            return p;
        }

        public long estimateSize() { return Long.MAX_VALUE; }

        public int characteristics() {
            return (Spliterator.ORDERED |
                    Spliterator.NONNULL |
                    Spliterator.CONCURRENT);
        }
    }

    /**
     * Returns a {@link Spliterator} over the elements in this queue.
     *
     * <p>The returned spliterator is
     * <a href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
     *
     * <p>The {@code Spliterator} reports {@link Spliterator#CONCURRENT},
     * {@link Spliterator#ORDERED}, and {@link Spliterator#NONNULL}.
     *
     * @implNote
     * The {@code Spliterator} implements {@code trySplit} to permit limited
     * parallelism.
     *
     * @return a {@code Spliterator} over the elements in this queue
     * @since 1.8
     */
    public Spliterator<E> spliterator() {
        return new LTQSpliterator();
    }

    /**
     * Creates an initially empty {@code LinkedTransferQueue}.
     */
    public LinkedTransferQueue() {
    }

    /**
     * Creates a {@code LinkedTransferQueue}
     * initially containing the elements of the given collection,
     * added in traversal order of the collection's iterator.
     *
     * @param c the collection of elements to initially contain
     * @throws NullPointerException if the specified collection or any
     *         of its elements are null
     */
    public LinkedTransferQueue(Collection<? extends E> c) {
        DualNode h = null, t = null;
        for (E e : c) {
            DualNode newNode = new DualNode(Objects.requireNonNull(e), true);
            if (t == null)
                h = newNode;
            else
                t.next = newNode;
            t = newNode;
        }
        head = h;
        tail = t;
    }

    /**
     * Inserts the specified element at the tail of this queue.
     * As the queue is unbounded, this method will never block.
     *
     * @throws NullPointerException if the specified element is null
     */
    public void put(E e) {
        Objects.requireNonNull(e);
        xfer(e, -1L);
    }

    /**
     * Inserts the specified element at the tail of this queue.
     * As the queue is unbounded, this method will never block or
     * return {@code false}.
     *
     * @return {@code true} (as specified by
     *  {@link BlockingQueue#offer(Object,long,TimeUnit) BlockingQueue.offer})
     * @throws NullPointerException if the specified element is null
     */
    public boolean offer(E e, long timeout, TimeUnit unit) {
        Objects.requireNonNull(e);
        xfer(e, -1L);
        return true;
    }

    /**
     * Inserts the specified element at the tail of this queue.
     * As the queue is unbounded, this method will never return {@code false}.
     *
     * @return {@code true} (as specified by {@link Queue#offer})
     * @throws NullPointerException if the specified element is null
     */
    public boolean offer(E e) {
        Objects.requireNonNull(e);
        xfer(e, -1L);
        return true;
    }

    /**
     * Inserts the specified element at the tail of this queue.
     * As the queue is unbounded, this method will never throw
     * {@link IllegalStateException} or return {@code false}.
     *
     * @return {@code true} (as specified by {@link Collection#add})
     * @throws NullPointerException if the specified element is null
     */
    public boolean add(E e) {
        Objects.requireNonNull(e);
        xfer(e, -1L);
        return true;
    }

    /**
     * Transfers the element to a waiting consumer immediately, if possible.
     *
     * <p>More precisely, transfers the specified element immediately
     * if there exists a consumer already waiting to receive it (in
     * {@link #take} or timed {@link #poll(long,TimeUnit) poll}),
     * otherwise returning {@code false} without enqueuing the element.
     *
     * @throws NullPointerException if the specified element is null
     */
    public boolean tryTransfer(E e) {
        Objects.requireNonNull(e);
        return xfer(e, 0L) == null;
    }

    /**
     * Transfers the element to a consumer, waiting if necessary to do so.
     *
     * <p>More precisely, transfers the specified element immediately
     * if there exists a consumer already waiting to receive it (in
     * {@link #take} or timed {@link #poll(long,TimeUnit) poll}),
     * else inserts the specified element at the tail of this queue
     * and waits until the element is received by a consumer.
     *
     * @throws NullPointerException if the specified element is null
     */
    public void transfer(E e) throws InterruptedException {
        Objects.requireNonNull(e);
        if (!Thread.interrupted()) {
            if (xfer(e, Long.MAX_VALUE) == null)
                return;
            Thread.interrupted(); // failure possible only due to interrupt
        }
        throw new InterruptedException();
    }

    /**
     * Transfers the element to a consumer if it is possible to do so
     * before the timeout elapses.
     *
     * <p>More precisely, transfers the specified element immediately
     * if there exists a consumer already waiting to receive it (in
     * {@link #take} or timed {@link #poll(long,TimeUnit) poll}),
     * else inserts the specified element at the tail of this queue
     * and waits until the element is received by a consumer,
     * returning {@code false} if the specified wait time elapses
     * before the element can be transferred.
     *
     * @throws NullPointerException if the specified element is null
     */
    public boolean tryTransfer(E e, long timeout, TimeUnit unit)
        throws InterruptedException {
        Objects.requireNonNull(e);
        long nanos = Math.max(unit.toNanos(timeout), 0L);
        if (xfer(e, nanos) == null)
            return true;
        if (!Thread.interrupted())
            return false;
        throw new InterruptedException();
    }

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

    @SuppressWarnings("unchecked")
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        Object e;
        long nanos = Math.max(unit.toNanos(timeout), 0L);
        if ((e = xfer(null, nanos)) != null || !Thread.interrupted())
            return (E) e;
        throw new InterruptedException();
    }

    @SuppressWarnings("unchecked")
    public E poll() {
        return (E) xfer(null, 0L);
    }

    /**
     * @throws NullPointerException     {@inheritDoc}
     * @throws IllegalArgumentException {@inheritDoc}
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
     * @throws NullPointerException     {@inheritDoc}
     * @throws IllegalArgumentException {@inheritDoc}
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

    /**
     * Returns an iterator over the elements in this queue in proper sequence.
     * The elements will be returned in order from first (head) to last (tail).
     *
     * <p>The returned iterator is
     * <a href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
     *
     * @return an iterator over the elements in this queue in proper sequence
     */
    public Iterator<E> iterator() {
        return new Itr();
    }

    public E peek() {
        restartFromHead: for (;;) {
            for (DualNode p = head; p != null;) {
                Object item = p.item;
                if (p.isData) {
                    if (item != null) {
                        @SuppressWarnings("unchecked") E e = (E) item;
                        return e;
                    }
                }
                else if (item == null)
                    break;
                if (p == (p = p.next))
                    continue restartFromHead;
            }
            return null;
        }
    }

    /**
     * Returns {@code true} if this queue contains no elements.
     *
     * @return {@code true} if this queue contains no elements
     */
    public boolean isEmpty() {
        return firstDataNode() == null;
    }

    public boolean hasWaitingConsumer() {
        restartFromHead: for (;;) {
            for (DualNode p = head; p != null;) {
                Object item = p.item;
                if (p.isData) {
                    if (item != null)
                        break;
                }
                else if (item == null)
                    return true;
                if (p == (p = p.next))
                    continue restartFromHead;
            }
            return false;
        }
    }

    /**
     * Returns the number of elements in this queue.  If this queue
     * contains more than {@code Integer.MAX_VALUE} elements, returns
     * {@code Integer.MAX_VALUE}.
     *
     * <p>Beware that, unlike in most collections, this method is
     * <em>NOT</em> a constant-time operation. Because of the
     * asynchronous nature of these queues, determining the current
     * number of elements requires an O(n) traversal.
     *
     * @return the number of elements in this queue
     */
    public int size() {
        return countOfMode(true);
    }

    public int getWaitingConsumerCount() {
        return countOfMode(false);
    }

    /**
     * Removes a single instance of the specified element from this queue,
     * if it is present.  More formally, removes an element {@code e} such
     * that {@code o.equals(e)}, if this queue contains one or more such
     * elements.
     * Returns {@code true} if this queue contained the specified element
     * (or equivalently, if this queue changed as a result of the call).
     *
     * @param o element to be removed from this queue, if present
     * @return {@code true} if this queue changed as a result of the call
     */
    public boolean remove(Object o) {
        if (o == null) return false;
        restartFromHead: for (;;) {
            for (DualNode p = head, pred = null; p != null; ) {
                boolean isData = p.isData;
                Object item = p.item;
                DualNode q = p.next;
                if (item != null) {
                    if (isData) {
                        if (o.equals(item) && tryMatchData(p, item)) {
                            skipDeadNodes(pred, p, p, q);
                            return true;
                        }
                        pred = p; p = q; continue;
                    }
                }
                else if (!isData)
                    break;
                for (DualNode c = p;; q = p.next) {
                    if (q == null || !q.matched()) {
                        pred = skipDeadNodes(pred, c, p, q); p = q; break;
                    }
                    if (p == (p = q)) continue restartFromHead;
                }
            }
            return false;
        }
    }

    /**
     * Returns {@code true} if this queue contains the specified element.
     * More formally, returns {@code true} if and only if this queue contains
     * at least one element {@code e} such that {@code o.equals(e)}.
     *
     * @param o object to be checked for containment in this queue
     * @return {@code true} if this queue contains the specified element
     */
    public boolean contains(Object o) {
        if (o == null) return false;
        restartFromHead: for (;;) {
            for (DualNode p = head, pred = null; p != null; ) {
                boolean isData = p.isData;
                Object item = p.item;
                DualNode q = p.next;
                if (item != null) {
                    if (isData) {
                        if (o.equals(item))
                            return true;
                        pred = p; p = q; continue;
                    }
                }
                else if (!isData)
                    break;
                for (DualNode c = p;; q = p.next) {
                    if (q == null || !q.matched()) {
                        pred = skipDeadNodes(pred, c, p, q); p = q; break;
                    }
                    if (p == (p = q)) continue restartFromHead;
                }
            }
            return false;
        }
    }

    /**
     * Always returns {@code Integer.MAX_VALUE} because a
     * {@code LinkedTransferQueue} is not capacity constrained.
     *
     * @return {@code Integer.MAX_VALUE} (as specified by
     *         {@link BlockingQueue#remainingCapacity()})
     */
    public int remainingCapacity() {
        return Integer.MAX_VALUE;
    }

    /**
     * Saves this queue to a stream (that is, serializes it).
     *
     * @param s the stream
     * @throws java.io.IOException if an I/O error occurs
     * @serialData All of the elements (each an {@code E}) in
     * the proper order, followed by a null
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException {
        s.defaultWriteObject();
        for (E e : this)
            s.writeObject(e);
        // Use trailing null as sentinel
        s.writeObject(null);
    }

    /**
     * Reconstitutes this queue from a stream (that is, deserializes it).
     * @param s the stream
     * @throws ClassNotFoundException if the class of a serialized object
     *         could not be found
     * @throws java.io.IOException if an I/O error occurs
     */
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {

        // Read in elements until trailing null sentinel found
        DualNode h = null, t = null;
        for (Object item; (item = s.readObject()) != null; ) {
            DualNode newNode = new DualNode(item, true);
            if (t == null)
                h = newNode;
            else
                t.next = newNode;
            t = newNode;
        }
        head = h;
        tail = t;
    }

    /**
     * @throws NullPointerException {@inheritDoc}
     */
    public boolean removeIf(Predicate<? super E> filter) {
        Objects.requireNonNull(filter);
        return bulkRemove(filter);
    }

    /**
     * @throws NullPointerException {@inheritDoc}
     */
    public boolean removeAll(Collection<?> c) {
        Objects.requireNonNull(c);
        return bulkRemove(e -> c.contains(e));
    }

    /**
     * @throws NullPointerException {@inheritDoc}
     */
    public boolean retainAll(Collection<?> c) {
        Objects.requireNonNull(c);
        return bulkRemove(e -> !c.contains(e));
    }

    public void clear() {
        bulkRemove(e -> true);
    }

    /**
     * Tolerate this many consecutive dead nodes before CAS-collapsing.
     * Amortized cost of clear() is (1 + 1/MAX_HOPS) CASes per element.
     */
    private static final int MAX_HOPS = 8;


    /** Implementation of bulk remove methods. */
    @SuppressWarnings("unchecked")
    private boolean bulkRemove(Predicate<? super E> filter) {
        boolean removed = false;
        restartFromHead: for (;;) {
            int hops = MAX_HOPS;
            // c will be CASed to collapse intervening dead nodes between
            // pred (or head if null) and p.
            for (DualNode p = head, c = p, pred = null, q; p != null; p = q) {
                boolean isData = p.isData, pAlive;
                Object item = p.item;
                q = p.next;
                if (pAlive = (item != null && isData)) {
                    if (filter.test((E) item)) {
                        if (tryMatchData(p, item))
                            removed = true;
                        pAlive = false;
                    }
                }
                else if (!isData && item == null)
                    break;
                if (pAlive || q == null || --hops == 0) {
                    // p might already be self-linked here, but if so:
                    // - CASing head will surely fail
                    // - CASing pred's next will be useless but harmless.
                    if ((c != p && !tryCasSuccessor(pred, c, c = p)) || pAlive) {
                        // if CAS failed or alive, abandon old pred
                        hops = MAX_HOPS;
                        pred = p;
                        c = q;
                    }
                } else if (p == q)
                    continue restartFromHead;
            }
            return removed;
        }
    }

    /**
     * Runs action on each element found during a traversal starting at p.
     * If p is null, the action is not run.
     */
    @SuppressWarnings("unchecked")
    void forEachFrom(Consumer<? super E> action, DualNode p) {
        for (DualNode pred = null; p != null; ) {
            boolean isData = p.isData;
            Object item = p.item;
            DualNode q = p.next;
            if (item != null) {
                if (isData) {
                    action.accept((E) item);
                    pred = p; p = q; continue;
                }
            }
            else if (!isData)
                break;
            for (DualNode c = p;; q = p.next) {
                if (q == null || !q.matched()) {
                    pred = skipDeadNodes(pred, c, p, q); p = q; break;
                }
                if (p == (p = q)) { pred = null; p = head; break; }
            }
        }
    }

    /**
     * @throws NullPointerException {@inheritDoc}
     */
    public void forEach(Consumer<? super E> action) {
        Objects.requireNonNull(action);
        forEachFrom(action, head);
    }

    // VarHandle mechanics
    static final VarHandle HEAD;
    static final VarHandle TAIL;
    static final VarHandle SWEEPVOTES;
    static {
        try {
            Class<?> ltq = LinkedTransferQueue.class, tn = DualNode.class;
            MethodHandles.Lookup l = MethodHandles.lookup();
            HEAD = l.findVarHandle(ltq, "head", tn);
            TAIL = l.findVarHandle(ltq, "tail", tn);
            SWEEPVOTES = l.findVarHandle(ltq, "sweepVotes", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
