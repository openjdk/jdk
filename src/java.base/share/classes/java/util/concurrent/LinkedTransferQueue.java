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
     * bwlow.
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
     * pointer updates, that provide the current value to comtinue
     * with on failure.)  Note that the linearization properties of
     * this style of queue are easy to verify -- elements are made
     * available by linking, and unavailable by matching. Compared to
     * plain M&S queues, this property of dual queues requires one
     * additional successful atomic operation per enq/deq pair. But it
     * also enables lower cost variants of queue maintenance
     * mechanics.
     *
     * Once a node is matched, its match status can never again
     * change.  We may thus arrange that the linked list of them
     * contain a prefix of zero or more matched nodes, followed by a
     * suffix of zero or more unmatched nodes. Note that we allow both
     * the prefix and suffix to be zero length, which in turn means
     * that we do not require a dummy header.
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
     * traversal chains, while smaller values increase CAS contention
     * and overhead. Using the smallest non-zero value of one is both
     * simple and empirically a good choice in most applicatkions.
     * The slack value is hard-wired: a path greater than one is
     * usually implemented by checking equality of traversal pointers.
     * Because CASes updating fields may fail and threads attempting
     * to do so may stall, the actual slack may exceed targeted
     * slack. To reduce the consequent staleness impact, threads may
     * help update (method unslacken) when traversal lengths exceed an
     * imposed limit of MAX_SLACK.
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
     * values held in other node fields.)  However, doing so adds some
     * further complexity to traversal: If any "next" pointer links to
     * itself, it indicates that the current thread has lagged behind
     * a head-update, and so the traversal must continue from the
     * "head".  Traversals trying to find the current tail starting
     * from "tail" may also encounter self-links, in which case they
     * also continue at "head".
     *
     * *** Blocking ***
     *
     * The TransferNode class is shared with class SynchronousQueue
     * (which adds Lifo-based matching methods). It houses method
     * await, which is used for all blocking control, as described
     * below in TransferNode internal documentation.
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
     * appended. (2) We cannot necessarily unlink s given a
     * predecessor node that is matched (including the case of being
     * cancelled): the predecessor may already be unspliced, in which
     * case some previous reachable node may still point to s.
     * (For further explanation see Herlihy & Shavit "The Art of
     * Multiprocessor Programming" chapter 9).
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
     */

    /**
     * Queue nodes. Uses type Object, not E, for items to allow
     * cancellation and forgetting after use. Note that this class is
     * statically imported by class SynchronousQueue.
     */
    static final class TransferNode implements ForkJoinPool.ManagedBlocker {
        volatile Object item;   // initially non-null if isData; CASed to match
        volatile TransferNode next;
        volatile Thread waiter; // null when not parked waiting for a match
        final boolean isData;   // false if this is a request node

        TransferNode(Object item, boolean isData) {
            ITEM.set(this, item); // relaxed write before publication
            this.isData = isData;
        }

        // Atomic updates
        final Object cmpExItem(Object cmp, Object val) { // try to match
            return ITEM.compareAndExchange(this, cmp, val);
        }
        final TransferNode cmpExNext(TransferNode cmp, TransferNode val) {
            return (TransferNode)NEXT.compareAndExchange(this, cmp, val);
        }

        /**
         * Returns true if this node has not been matched
         */
        final boolean isLive() {
            return isData == (item != null);
        }

        /**
         * Tries to cancel by matching with self if initially null else null
         * @param e the initial item value
         * @return e if successful, else current item
         */
        final Object tryCancel(Object e) {
            return cmpExItem(e, (e == null) ? this : null);
        }

        // Relaxed writes when volatile is unnecessarily strong
        final void clearWaiter()   { WAITER.setOpaque(this, null); }
        final void forgetItem()    { ITEM.set(this, this); }
        final void forgetNext()    { NEXT.set(this, this); }
        final void setNext(TransferNode n) { NEXT.set(this, n);  }

        // ManagedBlocker support
        public final boolean isReleasable() {
            return (!isLive() || Thread.currentThread().isInterrupted());
        }
        public final boolean block() {
            while (!isReleasable()) LockSupport.park();
            return true;
        }

        /**
         * Possibly blocks until matched or caller gives up.
         *
         * Control of blocking (and thread scheduling in general) for
         * possibly-synchronous queues (and channels etc constructed
         * from them) must straddle two extremes: If there are too few
         * underlying cores for a fulfilling party to continue, then
         * the caller must park to cause a context switch. On the
         * other hand, if the queue is busy with approximately the
         * same number of independent producers and consumers, then
         * that context switch causes a huge slowdown (often more than
         * 20X). Many cases are somewhere in-between, in which case
         * threads should try spinning and then give up and block. We
         * deal with this as follows:
         *
         * 1. Callers to method await indicate eligibility for
         * spinning when the node is either the only waiting node, or
         * the next eligible node is still spinning.  Otherwise, the
         * caller normally blocks (almost) immediately.
         *
         * 2. Even if eligible to spin, a caller blocks anyway in two
         * cases where it is normally best: If the thread is Virtual,
         * or the system is a uniprocessor. Because uniprocessor
         * status can vary over time (due to virtualization at other
         * system levels), we update it whenever an otherwise-eligible
         * spin elapses. (Updates to static field isUniprocessor are
         * allowed to be racy -- if status is dynamically varying,
         * tracking is at best approximate.)
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
         * timed park if the remaining time is less than the likely
         * cost of park/unpark. This also avoids re-parks when timed
         * park returns just barely too soon.
         *
         * 5. To make the above work, callers must precheck that
         * timeouts are not already elapsed, and that interruptible
         * operations were not already interrupted on call to the
         * corresponding queue operation.  Cancellation on timeout or
         * interrupt otherwise proceeds by trying to fulfill with an
         * impossible value (which is one reason that we use Object
         * types here rather than tyoed results).
         *
         * @param e the comparison value for checking match
         * @param nanos timeout, or Long.MAX_VALUE if untimed
         * @param blocker the LockSupport.setCurrentBlocker argument
         * @param spin true if eligible for spinning if enabled
         * @return matched item, or e if unmatched on interrupt or timeout
         */
        final Object await(Object e, long nanos, Object blocker, boolean spin) {
            boolean timed = (nanos != Long.MAX_VALUE);
            long deadline = (timed) ? System.nanoTime() + nanos : 0L;
            Thread w = Thread.currentThread();
            boolean canSpin = (!w.isVirtual() && spin);
            int spins = (canSpin && !isUniprocessor) ? SPINS : 0;
            Object match;
            while ((match = item) == e && --spins >= 0)
                Thread.onSpinWait();
            if (match == e) {
                boolean u;
                if (canSpin &&     // recheck for next time
                    (u = (Runtime.getRuntime().
                          availableProcessors() <= 1)) != isUniprocessor)
                    isUniprocessor = u;
                LockSupport.setCurrentBlocker(blocker);
                waiter = w;
                while ((match = item) == e) {
                    long ns;
                    if (w.isInterrupted()) {
                        match = tryCancel(e);
                        break;
                    }
                    if (timed) {
                        if ((ns = deadline - System.nanoTime()) <= 0L) {
                            match = tryCancel(e);
                            break;
                        }
                        if (ns < SPIN_FOR_TIMEOUT_THRESHOLD)
                            Thread.onSpinWait();
                        else
                            LockSupport.parkNanos(ns);
                    } else if (w instanceof ForkJoinWorkerThread) {
                        try {
                            ForkJoinPool.managedBlock(this);
                        } catch (InterruptedException cannotHappen) { }
                    } else
                        LockSupport.park(this);
                }
                clearWaiter();
                LockSupport.setCurrentBlocker(null);
            }
            if (e == null)
                forgetItem();
            return match;
        }

        /**
         * The number of times to spin when eligible.
         */
        private static final int SPINS = 1 << 7;

        /**
         * The number of nanoseconds for which it is faster to spin
         * rather than to use timed park. A rough estimate suffices.
         */
        private static final long SPIN_FOR_TIMEOUT_THRESHOLD = 1L << 10;

        /**
         * True if system is a uniprocessor. Initially assumed false.
         */
        private static boolean isUniprocessor;

        // VarHandle mechanics
        static final VarHandle ITEM;
        static final VarHandle NEXT;
        static final VarHandle WAITER;
        static {
            try {
                Class<?> tn = TransferNode.class;
                MethodHandles.Lookup l = MethodHandles.lookup();
                ITEM = l.findVarHandle(tn, "item", Object.class);
                NEXT = l.findVarHandle(tn, "next", tn);
                WAITER = l.findVarHandle(tn, "waiter", Thread.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }

    /**
     * The maximum number of dead nodes traversed before unslackening
     * to reduce retries due to stalls in updating head and tail.  Must
     * be at least 4.
     */
    private static final int MAX_SLACK = 1 << 6;

    /**
     * The maximum number of estimated removal failures (sweepVotes)
     * to tolerate before sweeping through the queue unlinking
     * cancelled nodes that were initially pinned.  Must be a power of
     * two, at least 2 and at most MAX_SLACK.
     */
    private static final int SWEEP_THRESHOLD = 1 << 5;

    /**
     * Unless empty (in which case possibly null), a node from which
     * all live nodes are reachable.
     * Invariants:
     * - head is never self-linked
     * Non-invariants:
     * - head may or may not be live
     */
    transient volatile TransferNode head;

    /**
     * Unless empty, a node from which the last node on list (that is, the unique
     * node with node.next == null), if one exists, can be reached
     * Non-invariants:
     * - tail may or may not be live
     * - tail may be the same as head
     * - tail may lag behind head, so need not be reachable from head
     * - tail.next may or may not be self-linked.
     */
    transient volatile TransferNode tail;

    /** The number of apparent failures to unsplice cancelled nodes */
    private transient volatile int sweepVotes;

    /** increment sweepVotes and return true on trigger */
    private boolean sweepNow() {
        return (((int) SWEEPVOTES.getAndAdd(this, 1) + 1) &
                (SWEEP_THRESHOLD - 1)) == 0;
    }

    // Atomic updates

    final TransferNode cmpExTail(TransferNode cmp, TransferNode val) {
        return (TransferNode)TAIL.compareAndExchange(this, cmp, val);
    }
    final TransferNode cmpExHead(TransferNode cmp, TransferNode val) {
        return (TransferNode)HEAD.compareAndExchange(this, cmp, val);
    }

    /**
     * Tries to update to new head, forgetting links from previous
     * head (if it exists) on success.
     */
    final TransferNode tryAdvanceHead(TransferNode h, TransferNode p) {
        TransferNode u;
        if ((u = cmpExHead(h, p)) == h && h != null)
            h.forgetNext();
        return u;
    }

    /**
     * Implements all queuing methods. Loops, trying:
     *
     * * If head not initialized, try to add new node and exit (unless immediate)
     * * If tail initialized and has same mode, and this is not a retry,
     *   start traversing at tail (for an append), else start at head
     *   (for a likely match, but if no live nodes, an append)
     * * Traverse over dead or wrong-mode nodes until finding a spot
     *   to match/append, or falling off the list because of self-links,
     *   taking or too many steps, in which case help unslacken and restart.
     * * On success, update head or tail if slacked, and return or wait,
     *   depending on nanos argument
     *
     * @param e the item or null for take
     * @param nanos timeout, or negative for async, 0 for immediate,
     *        Long.MAX_VALUE for untimed
     * @return an item if matched, else e
     */
    final Object xfer(Object e, long nanos) {
        boolean haveData = (e != null);
        TransferNode p;                     // current traversal node
        TransferNode s = null;              // the enqueued npde, if needed
        TransferNode prevTail = null;       // to avoid unbounded retries
        restart: for (;;) {
            TransferNode h, t;
            if ((p = h = head) == null) {   // lazily initialize
                if (nanos == 0L)            // no possible match
                    break restart;
                if ((h = cmpExHead(null,    // try to install as head
                                   s = new TransferNode(e, haveData))) == null) {
                    cmpExTail(null, s);
                    break restart;
                }
                p = h;                      // lost initialization race
            }
            if ((t = tail) != null && haveData == t.isData && t != prevTail)
                p = prevTail = t;           // start at tail
            for (int slack = 0; slack < MAX_SLACK; ++slack) { // bound steps
                TransferNode q, n; Object item;
                if (haveData != p.isData && // try to match waiting node
                    haveData != ((item = p.item) != null) &&
                    p.cmpExItem(item, e) == item) {
                    if (p != h)
                        tryAdvanceHead(h, (n = p.next) == null ? p : n);
                    LockSupport.unpark(p.waiter);
                    return item;
                }
                if ((q = p.next) == null) { // no matches
                    if (nanos == 0L)
                        break restart;
                    if (s == null) {        // try to append node
                        s = new TransferNode(e, haveData);
                        q = p.next;         // recheck after allocation
                    }
                    if (q == null && (q = p.cmpExNext(null, s)) == null) {
                        if (nanos > 0L || p != t)
                            cmpExTail(tail, s);
                        break restart;
                    }
                }
                if (p == (p = q))            // stale; restart
                    break;
            }
            unslacken();                     // collapse before retrying
        }
        Object match;
        if (s == null || nanos <= 0L)
            match = e;
        else if ((match = s.await(e, nanos, this, // spin if near head
                                  (p == null || p.waiter == null))) == e)
            unsplice(p, s);                 // cancelled
        return match;
    }

    /**
     * Collapses dead nodes from head and tail. Called before
     * retraversals and during unsplices to reduce retries due to
     * stalled head and tail updates.
     */
    private void unslacken() {
        TransferNode h, t, p;
        if ((h = head) != null && !h.isLive() &&
            (p = h.next) != null && p != h) {  // collapse head
            for (TransferNode n; (n = p.next) != p; p = n) {
                if (n == null || p.isLive()) {
                    if (cmpExHead(h, p) == h) {
                        if (n == null)         // absorb tail
                            cmpExTail(tail, p);
                        h.forgetNext();
                    }
                    break;
                }
            }
        }
        if ((t = tail) != null) {              // help collapse tail
            for (TransferNode q = t, n; (n = q.next) != q; q = n) {
                if (n == null) {
                    if (q != t)
                        cmpExTail(t, q);
                    break;
                }
            }
        }
    }

    /* --------------  Interior removals -------------- */
    /**
     * Unsplices (now or later) the given deleted/cancelled node with
     * the given predecessor.
     *
     * @param pred if nonnull, a node that was at one time known to be the
     * predecessor of s
     * @param s the node to be unspliced
     */
    final void unsplice(TransferNode pred, TransferNode s) {
        TransferNode n;
        if (pred != null && s != null && pred.next == s && (n = s.next) != s &&
            (n == null || pred.cmpExNext(s, n) != s) &&
            sweepNow())    // occasionally sweep initially pinned nodes
            sweep();
        unslacken();       // help clean endpoints
    }

    /**
     * Unlinks dead nodes encountered in a traversal from head.
     */
    private void sweep() {
        for (TransferNode p = head, s; p != null && (s = p.next) != null; ) {
            TransferNode n, u, h;
            if (s.isLive())
                p = s;
            else if ((n = s.next) == null) {
                if ((h = head) != null && s == h.next)
                    cmpExTail(s, h);       // absorb tail as head
                break;
            }
            else if (s == n)               // stale
                p = head;
            else                           // unlink
                p = ((u = p.cmpExNext(s, n)) == s) ? n : u;
        }
    }

    /**
     * Tries to CAS pred.next (or head, if pred is null) from c to p.
     * Caller must ensure that we're not unlinking the trailing node.
     */
    final boolean tryCasSuccessor(TransferNode pred, TransferNode c,
                                  TransferNode p) {
        // assert p != null && !c.isLive() && c != p;
        return ((pred != null ?
                 pred.cmpExNext(c, p) :
                 tryAdvanceHead(c, p))) == c;
    }

    /**
     * Collapses dead (matched) nodes between pred and q.
     * @param pred the last known live node, or null if none
     * @param c the first dead node
     * @param p the last dead node
     * @param q p.next: the next live node, or null if at end
     * @return pred if pred still alive and CAS succeeded; else p
     */
    final TransferNode skipDeadNodes(TransferNode pred, TransferNode c,
                                     TransferNode p, TransferNode q) {
        // assert pred != c && p != q; && !c.isLive() && !p.isLive();
        if (q == null) { // Never unlink trailing node.
            if (c == p)
                return pred;
            q = p;
        }
        return (tryCasSuccessor(pred, c, q) && (pred == null || pred.isLive()))
            ? pred : p;
    }

    /**
     * Tries to match the given object only if nonnull and p
     * is a data node. Signals waiter on success.
     */
    final boolean tryMatchData(TransferNode p, Object x) {
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
    final TransferNode firstDataNode() {
        for (TransferNode h = head, p = h, q, u; p != null;) {
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
            else if (p != h)                  // collapse
                p = h = ((u = tryAdvanceHead(h, q)) == h) ? q : u;
            else                              // traverse past header
                p = q;
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
            for (TransferNode p = head; p != null;) {
                if (p.isLive()) {
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
            for (TransferNode p = head; p != null;) {
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
            for (TransferNode p = head; p != null;) {
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
        private TransferNode nextNode;   // next node to return item for
        private E nextItem;      // the corresponding item
        private TransferNode lastRet;    // last returned node, to support remove
        private TransferNode ancestor;   // Helps unlink lastRet on remove()

        /**
         * Moves to next node after pred, or first node if pred null.
         */
        @SuppressWarnings("unchecked")
        private void advance(TransferNode pred) {
            for (TransferNode p = (pred == null) ? head : pred.next, c = p;
                 p != null; ) {
                final Object item;
                if ((item = p.item) != null && p.isData) {
                    nextNode = p;
                    nextItem = (E) item;
                    if (c != p)
                        tryCasSuccessor(pred, c, p);
                    return;
                }
                else if (!p.isData && item == null)
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
            final TransferNode p;
            if ((p = nextNode) == null) throw new NoSuchElementException();
            E e = nextItem;
            advance(lastRet = p);
            return e;
        }

        public void forEachRemaining(Consumer<? super E> action) {
            Objects.requireNonNull(action);
            TransferNode q = null;
            for (TransferNode p; (p = nextNode) != null; advance(q = p))
                action.accept(nextItem);
            if (q != null)
                lastRet = q;
        }

        public final void remove() {
            final TransferNode lastRet = this.lastRet;
            if (lastRet == null)
                throw new IllegalStateException();
            this.lastRet = null;
            if (lastRet.item == null)   // already deleted?
                return;
            // Advance ancestor, collapsing intervening dead nodes
            TransferNode pred = ancestor;
            for (TransferNode p = (pred == null) ? head : pred.next, c = p, q;
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

            // assert !lastRet.isLive();
        }
    }

    /** A customized variant of Spliterators.IteratorSpliterator */
    final class LTQSpliterator implements Spliterator<E> {
        static final int MAX_BATCH = 1 << 25;  // max batch array size;
        TransferNode current;       // current node; null until initialized
        int batch;          // batch size for splits
        boolean exhausted;  // true when no more nodes
        LTQSpliterator() {}

        public Spliterator<E> trySplit() {
            TransferNode p, q;
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
            final TransferNode p;
            if ((p = current()) != null) {
                current = null;
                exhausted = true;
                forEachFrom(action, p);
            }
        }

        @SuppressWarnings("unchecked")
        public boolean tryAdvance(Consumer<? super E> action) {
            Objects.requireNonNull(action);
            TransferNode p;
            if ((p = current()) != null) {
                E e = null;
                do {
                    final boolean isData = p.isData;
                    final Object item = p.item;
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

        private void setCurrent(TransferNode p) {
            if ((current = p) == null)
                exhausted = true;
        }

        private TransferNode current() {
            TransferNode p;
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
        TransferNode h = null, t = null;
        for (E e : c) {
            TransferNode newNode = new TransferNode(Objects.requireNonNull(e), true);
            if (t == null)
                t = h = newNode;
            else
                t.setNext(t = newNode);
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
        offer(e);
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
        return offer(e);
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
        return offer(e);
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
            for (TransferNode p = head; p != null;) {
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
            for (TransferNode p = head; p != null;) {
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
            for (TransferNode p = head, pred = null; p != null; ) {
                TransferNode q = p.next;
                final Object item;
                if ((item = p.item) != null) {
                    if (p.isData) {
                        if (o.equals(item) && tryMatchData(p, item)) {
                            skipDeadNodes(pred, p, p, q);
                            return true;
                        }
                        pred = p; p = q; continue;
                    }
                }
                else if (!p.isData)
                    break;
                for (TransferNode c = p;; q = p.next) {
                    if (q == null || q.isLive()) {
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
            for (TransferNode p = head, pred = null; p != null; ) {
                TransferNode q = p.next;
                final Object item;
                if ((item = p.item) != null) {
                    if (p.isData) {
                        if (o.equals(item))
                            return true;
                        pred = p; p = q; continue;
                    }
                }
                else if (!p.isData)
                    break;
                for (TransferNode c = p;; q = p.next) {
                    if (q == null || q.isLive()) {
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
        TransferNode h = null, t = null;
        for (Object item; (item = s.readObject()) != null; ) {
            TransferNode newNode = new TransferNode(item, true);
            if (t == null)
                t = h = newNode;
            else
                t.setNext(t = newNode);
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
            for (TransferNode p = head, c = p, pred = null, q; p != null; p = q) {
                q = p.next;
                final Object item; boolean pAlive;
                if (pAlive = ((item = p.item) != null && p.isData)) {
                    if (filter.test((E) item)) {
                        if (tryMatchData(p, item))
                            removed = true;
                        pAlive = false;
                    }
                }
                else if (!p.isData && item == null)
                    break;
                if (pAlive || q == null || --hops == 0) {
                    // p might already be self-linked here, but if so:
                    // - CASing head will surely fail
                    // - CASing pred's next will be useless but harmless.
                    if ((c != p && !tryCasSuccessor(pred, c, c = p))
                        || pAlive) {
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
    void forEachFrom(Consumer<? super E> action, TransferNode p) {
        for (TransferNode pred = null; p != null; ) {
            TransferNode q = p.next;
            final Object item;
            if ((item = p.item) != null) {
                if (p.isData) {
                    action.accept((E) item);
                    pred = p; p = q; continue;
                }
            }
            else if (!p.isData)
                break;
            for (TransferNode c = p;; q = p.next) {
                if (q == null || q.isLive()) {
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
            Class<?> ltq = LinkedTransferQueue.class, tn = TransferNode.class;
            MethodHandles.Lookup l = MethodHandles.lookup();
            HEAD = l.findVarHandle(ltq, "head", tn);
            TAIL = l.findVarHandle(ltq, "tail", tn);
            SWEEPVOTES = l.findVarHandle(ltq, "sweepVotes", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }

        // Reduce the risk of rare disastrous classloading in first call to
        // LockSupport.park: https://bugs.openjdk.org/browse/JDK-8074773
        Class<?> ensureLoaded = LockSupport.class;
    }
}
