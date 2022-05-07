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

import java.lang.Thread.UncaughtExceptionHandler;
import java.security.AccessController;
import java.security.AccessControlContext;
import java.security.Permission;
import java.security.Permissions;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;
import jdk.internal.access.JavaUtilConcurrentFJPAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.misc.Unsafe;
import jdk.internal.vm.SharedThreadContainer;

/**
 * An {@link ExecutorService} for running {@link ForkJoinTask}s.
 * A {@code ForkJoinPool} provides the entry point for submissions
 * from non-{@code ForkJoinTask} clients, as well as management and
 * monitoring operations.
 *
 * <p>A {@code ForkJoinPool} differs from other kinds of {@link
 * ExecutorService} mainly by virtue of employing
 * <em>work-stealing</em>: all threads in the pool attempt to find and
 * execute tasks submitted to the pool and/or created by other active
 * tasks (eventually blocking waiting for work if none exist). This
 * enables efficient processing when most tasks spawn other subtasks
 * (as do most {@code ForkJoinTask}s), as well as when many small
 * tasks are submitted to the pool from external clients.  Especially
 * when setting <em>asyncMode</em> to true in constructors, {@code
 * ForkJoinPool}s may also be appropriate for use with event-style
 * tasks that are never joined. All worker threads are initialized
 * with {@link Thread#isDaemon} set {@code true}.
 *
 * <p>A static {@link #commonPool()} is available and appropriate for
 * most applications. The common pool is used by any ForkJoinTask that
 * is not explicitly submitted to a specified pool. Using the common
 * pool normally reduces resource usage (its threads are slowly
 * reclaimed during periods of non-use, and reinstated upon subsequent
 * use).
 *
 * <p>For applications that require separate or custom pools, a {@code
 * ForkJoinPool} may be constructed with a given target parallelism
 * level; by default, equal to the number of available processors.
 * The pool attempts to maintain enough active (or available) threads
 * by dynamically adding, suspending, or resuming internal worker
 * threads, even if some tasks are stalled waiting to join others.
 * However, no such adjustments are guaranteed in the face of blocked
 * I/O or other unmanaged synchronization. The nested {@link
 * ManagedBlocker} interface enables extension of the kinds of
 * synchronization accommodated. The default policies may be
 * overridden using a constructor with parameters corresponding to
 * those documented in class {@link ThreadPoolExecutor}.
 *
 * <p>In addition to execution and lifecycle control methods, this
 * class provides status check methods (for example
 * {@link #getStealCount}) that are intended to aid in developing,
 * tuning, and monitoring fork/join applications. Also, method
 * {@link #toString} returns indications of pool state in a
 * convenient form for informal monitoring.
 *
 * <p>As is the case with other ExecutorServices, there are three
 * main task execution methods summarized in the following table.
 * These are designed to be used primarily by clients not already
 * engaged in fork/join computations in the current pool.  The main
 * forms of these methods accept instances of {@code ForkJoinTask},
 * but overloaded forms also allow mixed execution of plain {@code
 * Runnable}- or {@code Callable}- based activities as well.  However,
 * tasks that are already executing in a pool should normally instead
 * use the within-computation forms listed in the table unless using
 * async event-style tasks that are not usually joined, in which case
 * there is little difference among choice of methods.
 *
 * <table class="plain">
 * <caption>Summary of task execution methods</caption>
 *  <tr>
 *    <td></td>
 *    <th scope="col"> Call from non-fork/join clients</th>
 *    <th scope="col"> Call from within fork/join computations</th>
 *  </tr>
 *  <tr>
 *    <th scope="row" style="text-align:left"> Arrange async execution</th>
 *    <td> {@link #execute(ForkJoinTask)}</td>
 *    <td> {@link ForkJoinTask#fork}</td>
 *  </tr>
 *  <tr>
 *    <th scope="row" style="text-align:left"> Await and obtain result</th>
 *    <td> {@link #invoke(ForkJoinTask)}</td>
 *    <td> {@link ForkJoinTask#invoke}</td>
 *  </tr>
 *  <tr>
 *    <th scope="row" style="text-align:left"> Arrange exec and obtain Future</th>
 *    <td> {@link #submit(ForkJoinTask)}</td>
 *    <td> {@link ForkJoinTask#fork} (ForkJoinTasks <em>are</em> Futures)</td>
 *  </tr>
 * </table>
 *
 * <p>The parameters used to construct the common pool may be controlled by
 * setting the following {@linkplain System#getProperty system properties}:
 * <ul>
 * <li>{@systemProperty java.util.concurrent.ForkJoinPool.common.parallelism}
 * - the parallelism level, a non-negative integer
 * <li>{@systemProperty java.util.concurrent.ForkJoinPool.common.threadFactory}
 * - the class name of a {@link ForkJoinWorkerThreadFactory}.
 * The {@linkplain ClassLoader#getSystemClassLoader() system class loader}
 * is used to load this class.
 * <li>{@systemProperty java.util.concurrent.ForkJoinPool.common.exceptionHandler}
 * - the class name of a {@link UncaughtExceptionHandler}.
 * The {@linkplain ClassLoader#getSystemClassLoader() system class loader}
 * is used to load this class.
 * <li>{@systemProperty java.util.concurrent.ForkJoinPool.common.maximumSpares}
 * - the maximum number of allowed extra threads to maintain target
 * parallelism (default 256).
 * </ul>
 * If no thread factory is supplied via a system property, then the
 * common pool uses a factory that uses the system class loader as the
 * {@linkplain Thread#getContextClassLoader() thread context class loader}.
 * In addition, if a {@link SecurityManager} is present, then
 * the common pool uses a factory supplying threads that have no
 * {@link Permissions} enabled, and are not guaranteed to preserve
 * the values of {@link java.lang.ThreadLocal} variables across tasks.
 *
 * Upon any error in establishing these settings, default parameters
 * are used. It is possible to disable or limit the use of threads in
 * the common pool by setting the parallelism property to zero, and/or
 * using a factory that may return {@code null}. However doing so may
 * cause unjoined tasks to never be executed.
 *
 * @implNote This implementation restricts the maximum number of
 * running threads to 32767. Attempts to create pools with greater
 * than the maximum number result in {@code
 * IllegalArgumentException}. Also, this implementation rejects
 * submitted tasks (that is, by throwing {@link
 * RejectedExecutionException}) only when the pool is shut down or
 * internal resources have been exhausted.
 *
 * @since 1.7
 * @author Doug Lea
 */
public class ForkJoinPool extends AbstractExecutorService {

    /*
     * Implementation Overview
     *
     * This class and its nested classes provide the main
     * functionality and control for a set of worker threads:
     * Submissions from non-FJ threads enter into submission queues.
     * Workers take these tasks and typically split them into subtasks
     * that may be stolen by other workers. Work-stealing based on
     * randomized scans generally leads to better throughput than
     * "work dealing" in which producers assign tasks to idle threads,
     * in part because threads that have finished other tasks before
     * the signalled thread wakes up (which can be a long time) can
     * take the task instead.  Preference rules give first priority to
     * processing tasks from their own queues (LIFO or FIFO, depending
     * on mode), then to randomized FIFO steals of tasks in other
     * queues.  This framework began as vehicle for supporting
     * tree-structured parallelism using work-stealing.  Over time,
     * its scalability advantages led to extensions and changes to
     * better support more diverse usage contexts.  Because most
     * internal methods and nested classes are interrelated, their
     * main rationale and descriptions are presented here; individual
     * methods and nested classes contain only brief comments about
     * details. There are a fair number of odd code constructions and
     * design decisions for components that reside at the edge of Java
     * vs JVM functionality.
     *
     * WorkQueues
     * ==========
     *
     * Most operations occur within work-stealing queues (in nested
     * class WorkQueue).  These are special forms of Deques that
     * support only three of the four possible end-operations -- push,
     * pop, and poll (aka steal), under the further constraints that
     * push and pop are called only from the owning thread (or, as
     * extended here, under a lock), while poll may be called from
     * other threads.  (If you are unfamiliar with them, you probably
     * want to read Herlihy and Shavit's book "The Art of
     * Multiprocessor programming", chapter 16 describing these in
     * more detail before proceeding.)  The main work-stealing queue
     * design is roughly similar to those in the papers "Dynamic
     * Circular Work-Stealing Deque" by Chase and Lev, SPAA 2005
     * (http://research.sun.com/scalable/pubs/index.html) and
     * "Idempotent work stealing" by Michael, Saraswat, and Vechev,
     * PPoPP 2009 (http://portal.acm.org/citation.cfm?id=1504186).
     * The main differences ultimately stem from GC requirements that
     * we null out taken slots as soon as we can, to maintain as small
     * a footprint as possible even in programs generating huge
     * numbers of tasks. To accomplish this, we shift the CAS
     * arbitrating pop vs poll (steal) from being on the indices
     * ("base" and "top") to the slots themselves. These provide the
     * primary required memory ordering -- see "Correct and Efficient
     * Work-Stealing for Weak Memory Models" by Le, Pop, Cohen, and
     * Nardelli, PPoPP 2013
     * (http://www.di.ens.fr/~zappa/readings/ppopp13.pdf) for an
     * analysis of memory ordering requirements in work-stealing
     * algorithms similar to the one used here. We also use ordered,
     * moded accesses and/or fences for other control, with modes
     * reflecting the presence or absence of other contextual sync
     * provided by atomic and/or volatile accesses. Some methods (or
     * their primary loops) begin with an acquire fence or
     * otherwise-unnecessary volatile read that amounts to an
     * acquiring read of "this" to cover all fields (which is
     * sometimes stronger than necessary, but less brittle). Some
     * constructions are intentionally racy because they use read
     * values as hints, not for correctness.
     *
     * We also support a user mode in which local task processing is
     * in FIFO, not LIFO order, simply by using a local version of
     * poll rather than pop.  This can be useful in message-passing
     * frameworks in which tasks are never joined, although with
     * increased contention among task producers and consumers. Also,
     * the same data structure (and class) is used for "submission
     * queues" (described below) holding externally submitted tasks,
     * that differ only in that a lock (field "access"; see below) is
     * required by external callers to push and pop tasks.
     *
     * Adding tasks then takes the form of a classic array push(task)
     * in a circular buffer:
     *    q.array[q.top++ % length] = task;
     *
     * The actual code needs to null-check and size-check the array,
     * uses masking, not mod, for indexing a power-of-two-sized array,
     * enforces memory ordering, supports resizing, and possibly
     * signals waiting workers to start scanning (described below),
     * which requires even internal usages to strictly order accesses
     * (using a form of lock release).
     *
     * The pop operation (always performed by owner) is of the form:
     *   if ((task = getAndSet(q.array, (q.top-1) % length, null)) != null)
     *        decrement top and return task;
     * If this fails, the queue is empty. This operation is one part
     * of the nextLocalTask method, that instead does a local-poll
     * in FIFO mode.
     *
     * The poll operation is, basically:
     *   if (CAS nonnull task t = q.array[k = q.base % length] to null)
     *       increment base and return task;
     *
     * However, there are several more cases that must be dealt with.
     * Some of them are just due to asynchrony; others reflect
     * contention and stealing policies. Stepping through them
     * illustrates some of the implementation decisions in this class.
     *
     *  * Slot k must be read with an acquiring read, which it must
     *    anyway to dereference and run the task if the (acquiring)
     *    CAS succeeds, but uses an explicit acquire fence to support
     *    the following rechecks even if the CAS is not attempted.
     *
     *  * q.base may change between reading and using its value to
     *    index the slot. To avoid trying to use the wrong t, the
     *    index and slot must be reread (not necessarily immediately)
     *    until consistent, unless this is a local poll by owner, in
     *    which case this form of inconsistency can only appear as t
     *    being null, below.
     *
     *  * Similarly, q.array may change (due to a resize), unless this
     *    is a local poll by owner. Otherwise, when t is present, this
     *    only needs consideration on CAS failure (since a CAS
     *    confirms the non-resized case.)
     *
     *  * t may appear null because a previous poll operation has not
     *    yet incremented q.base, so the read is from an already-taken
     *    index. This form of stall reflects the non-lock-freedom of
     *    the poll operation. Stalls can be detected by observing that
     *    q.base doesn't change on repeated reads of null t and when
     *    no other alternatives apply, spin-wait for it to settle.  To
     *    reduce producing these kinds of stalls by other stealers, we
     *    encourage timely writes to indices using store fences when
     *    memory ordering is not already constrained by context.
     *
     *  * The CAS may fail, in which case we may want to retry unless
     *    there is too much contention. One goal is to balance and
     *    spread out the many forms of contention that may be
     *    encountered across polling and other operations to avoid
     *    sustained performance degradations. Across all cases where
     *    alternatives exist, a bounded number of CAS misses or stalls
     *    are tolerated (for slots, ctl, and elsewhere described
     *    below) before taking alternative action. These may move
     *    contention or retries elsewhere, which is still preferable
     *    to single-point bottlenecks.
     *
     *  * Even though the check "top == base" is quiescently accurate
     *    to determine whether a queue is empty, it is not of much use
     *    when deciding whether to try to poll or repoll after a
     *    failure.  Both top and base may move independently, and both
     *    lag updates to the underlying array. To reduce memory
     *    contention, when possible, non-owners avoid reading the
     *    "top" index at all, and instead use array reads, including
     *    one-ahead reads to check whether to repoll, relying on the
     *    fact that a non-empty queue does not have two null slots in
     *    a row, except in cases (resizes and shifts) that can be
     *    detected with a secondary recheck.
     *
     * The poll operations in q.poll(), scan(), helpJoin(), and
     * elsewhere differ with respect to whether other queues are
     * available to try, and the presence or nature of screening steps
     * when only some kinds of tasks can be taken. When alternatives
     * (or failing) is an option, they uniformly give up after
     * bounded numbers of stalls and/or CAS failures, which reduces
     * contention when too many workers are polling too few tasks.
     * Overall, in the aggregate, we ensure probabilistic
     * non-blockingness of work-stealing at least until checking
     * quiescence (which is intrinsically blocking): If an attempted
     * steal fails in these ways, a scanning thief chooses a different
     * target to try next. In contexts where alternatives aren't
     * available, and when progress conditions can be isolated to
     * values of a single variable, simple spinloops (using
     * Thread.onSpinWait) are used to reduce memory traffic.
     *
     * WorkQueues are also used in a similar way for tasks submitted
     * to the pool. We cannot mix these tasks in the same queues used
     * by workers. Instead, we randomly associate submission queues
     * with submitting threads, using a form of hashing.  The
     * ThreadLocalRandom probe value serves as a hash code for
     * choosing existing queues, and may be randomly repositioned upon
     * contention with other submitters.  In essence, submitters act
     * like workers except that they are restricted to executing local
     * tasks that they submitted (or when known, subtasks thereof).
     * Insertion of tasks in shared mode requires a lock. We use only
     * a simple spinlock because submitters encountering a busy queue
     * move to a different position to use or create other queues.
     * They (spin) block when registering new queues, and less
     * often in tryRemove and helpComplete.  The lock needed for
     * external queues is generalized (as field "access") for
     * operations on owned queues that require a fully-fenced write
     * (including push, parking status, and termination) in order to
     * deal with Dekker-like signalling constructs described below.
     *
     * Management
     * ==========
     *
     * The main throughput advantages of work-stealing stem from
     * decentralized control -- workers mostly take tasks from
     * themselves or each other, at rates that can exceed a billion
     * per second.  Most non-atomic control is performed by some form
     * of scanning across or within queues.  The pool itself creates,
     * activates (enables scanning for and running tasks),
     * deactivates, blocks, and terminates threads, all with minimal
     * central information.  There are only a few properties that we
     * can globally track or maintain, so we pack them into a small
     * number of variables, often maintaining atomicity without
     * blocking or locking.  Nearly all essentially atomic control
     * state is held in a few variables that are by far most often
     * read (not written) as status and consistency checks. We pack as
     * much information into them as we can.
     *
     * Field "ctl" contains 64 bits holding information needed to
     * atomically decide to add, enqueue (on an event queue), and
     * dequeue and release workers.  To enable this packing, we
     * restrict maximum parallelism to (1<<15)-1 (which is far in
     * excess of normal operating range) to allow ids, counts, and
     * their negations (used for thresholding) to fit into 16bit
     * subfields. Field "parallelism" holds the target parallelism
     * (normally corresponding to pool size). It is needed (nearly)
     * only in methods updating ctl, so is packed nearby. As of the
     * current release, users can dynamically reset target
     * parallelism, which is read once per update, so only slowly has
     * an effect in creating threads or letting them time out and
     * terminate when idle.
     *
     * Field "runState" holds lifetime status, atomically and
     * monotonically setting SHUTDOWN, STOP, and finally TERMINATED
     * bits. It is updated only via bitwise atomics (getAndBitwiseOr).
     *
     * Array "queues" holds references to WorkQueues.  It is updated
     * (only during worker creation and termination) under the
     * registrationLock, but is otherwise concurrently readable (often
     * prefaced by a volatile read of mode to check termination, that
     * is required anyway, and serves as an acquire fence). To
     * simplify index-based operations, the array size is always a
     * power of two, and all readers must tolerate null slots.  Worker
     * queues are at odd indices. Worker ids masked with SMASK match
     * their index. Shared (submission) queues are at even
     * indices. Grouping them together in this way simplifies and
     * speeds up task scanning.
     *
     * All worker thread creation is on-demand, triggered by task
     * submissions, replacement of terminated workers, and/or
     * compensation for blocked workers. However, all other support
     * code is set up to work with other policies.  To ensure that we
     * do not hold on to worker or task references that would prevent
     * GC, all accesses to workQueues in waiting, signalling, and
     * control methods are via indices into the queues array (which is
     * one source of some of the messy code constructions here). In
     * essence, the queues array serves as a weak reference
     * mechanism. In particular, the stack top subfield of ctl stores
     * indices, not references.
     *
     * Queuing Idle Workers. Unlike HPC work-stealing frameworks, we
     * cannot let workers spin indefinitely scanning for tasks when
     * none can be found immediately, and we cannot start/resume
     * workers unless there appear to be tasks available.  On the
     * other hand, we must quickly prod them into action when new
     * tasks are submitted or generated. These latencies are mainly a
     * function of JVM park/unpark (and underlying OS) performance,
     * which can be slow and variable.  In many usages, ramp-up time
     * is the main limiting factor in overall performance, which is
     * compounded at program start-up by JIT compilation and
     * allocation. On the other hand, throughput degrades when too
     * many threads poll for too few tasks.
     *
     * The "ctl" field atomically maintains total and "released"
     * worker counts, plus the head of the available worker queue
     * (actually stack, represented by the lower 32bit subfield of
     * ctl).  Released workers are those known to be scanning for
     * and/or running tasks. Unreleased ("available") workers are
     * recorded in the ctl stack. These workers are made eligible for
     * signalling by enqueuing in ctl (see method awaitWork).  The
     * "queue" is a form of Treiber stack. This is ideal for
     * activating threads in most-recently used order, and improves
     * performance and locality, outweighing the disadvantages of
     * being prone to contention and inability to release a worker
     * unless it is topmost on stack. The top stack state holds the
     * value of the "phase" field of the worker: its index and status,
     * plus a version counter that, in addition to the count subfields
     * (also serving as version stamps) provide protection against
     * Treiber stack ABA effects.
     *
     * Creating workers. To create a worker, we pre-increment counts
     * (serving as a reservation), and attempt to construct a
     * ForkJoinWorkerThread via its factory. On starting, the new
     * thread first invokes registerWorker, where it constructs a
     * WorkQueue and is assigned an index in the queues array
     * (expanding the array if necessary).  Upon any exception across
     * these steps, or null return from factory, deregisterWorker
     * adjusts counts and records accordingly.  If a null return, the
     * pool continues running with fewer than the target number
     * workers. If exceptional, the exception is propagated, generally
     * to some external caller.
     *
     * WorkQueue field "phase" is used by both workers and the pool to
     * manage and track whether a worker is unsignalled (possibly
     * blocked waiting for a signal), conveniently using the sign bit
     * to check.  When a worker is enqueued its phase field is set
     * negative. Note that phase field updates lag queue CAS releases;
     * seeing a negative phase does not guarantee that the worker is
     * available (and so is never checked in this way). When queued,
     * the lower 16 bits of its phase must hold its pool index. So we
     * place the index there upon initialization and never modify
     * these bits.
     *
     * The ctl field also serves as the basis for memory
     * synchronization surrounding activation. This uses a more
     * efficient version of a Dekker-like rule that task producers and
     * consumers sync with each other by both writing/CASing ctl (even
     * if to its current value).  However, rather than CASing ctl to
     * its current value in the common case where no action is
     * required, we reduce write contention by ensuring that
     * signalWork invocations are prefaced with a fully fenced memory
     * access (which is usually needed anyway).
     *
     * Signalling. Signals (in signalWork) cause new or reactivated
     * workers to scan for tasks.  Method signalWork and its callers
     * try to approximate the unattainable goal of having the right
     * number of workers activated for the tasks at hand, but must err
     * on the side of too many workers vs too few to avoid stalls.  If
     * computations are purely tree structured, it suffices for every
     * worker to activate another when it pushes a task into an empty
     * queue, resulting in O(log(#threads)) steps to full activation.
     * (To reduce resource usages in some cases, at the expense of
     * slower startup in others, activation of an idle thread is
     * preferred over creating a new one, here and elsewhere.)  If
     * instead, tasks come in serially from only a single producer,
     * each worker taking its first (since the last activation) task
     * from a queue should signal another if there are more tasks in
     * that queue. This is equivalent to, but generally faster than,
     * arranging the stealer take two tasks, re-pushing one on its own
     * queue, and signalling (because its queue is empty), also
     * resulting in logarithmic full activation time. Because we don't
     * know about usage patterns (or most commonly, mixtures), we use
     * both approaches. Together these are minimally necessary for
     * maintaining liveness. However, they do not account for the fact
     * that when tasks are short-lived, signals are unnecessary
     * because workers will already be scanning for new tasks without
     * the need of new signals. We track these cases (variable
     * "prevSrc" in scan() and related methods) to avoid some
     * unnecessary signals and scans.  However, signal contention and
     * overhead effects may still occur during ramp-up, ramp-down, and
     * small computations involving only a few workers.
     *
     * Scanning. Method scan performs top-level scanning for (and
     * execution of) tasks by polling a pseudo-random permutation of
     * the array (by starting at a random index, and using a constant
     * cyclically exhaustive stride.) It uses the same basic polling
     * method as WorkQueue.poll(), but restarts with a different
     * permutation on each invocation. (Non-top-level scans; for
     * example in helpJoin, use simpler and faster linear probes
     * because they do not systematically contend with top-level
     * scans.)  The pseudorandom generator need not have high-quality
     * statistical properties in the long term. We use Marsaglia
     * XorShifts, seeded with the Weyl sequence from ThreadLocalRandom
     * probes, which are cheap and suffice. Scans do not otherwise
     * explicitly take into account core affinities, loads, cache
     * localities, etc, However, they do exploit temporal locality
     * (which usually approximates these) by preferring to re-poll
     * from the same queue (using method tryPoll()) after a successful
     * poll before trying others (see method topLevelExec), which also
     * reduces bookkeeping and scanning overhead.  This also reduces
     * fairness, which is partially counteracted by giving up on
     * contention.
     *
     * Deactivation. When method scan indicates that no tasks are
     * found by a worker, it deactivates (see awaitWork).  Note that
     * not finding tasks doesn't mean that there won't soon be
     * some. Further, a scan may give up under contention, returning
     * even without knowing whether any tasks are still present, which
     * is OK, given the above signalling rules that will eventually
     * maintain progress.  Blocking and unblocking via park/unpark can
     * cause serious slowdowns when tasks are rapidly but irregularly
     * generated (which is often due to garbage collectors and other
     * activities). One way to ameliorate is for workers to rescan
     * multiple times, even when there are unlikely to be tasks. But
     * this causes enough memory and CAS contention to prefer using
     * quieter spinwaits in awaitWork; currently set to small values
     * that only cover near-miss scenarios for deactivate vs activate
     * races. Because idle workers are often not yet blocked (via
     * LockSupport.park), we use the WorkQueue access field to
     * advertise that a waiter actually needs unparking upon signal.
     *
     * When idle workers are not continually woken up, the count
     * fields in ctl allow efficient and accurate discovery of
     * quiescent states (i.e., when all workers are idle) after
     * deactivation. However, this voting mechanism alone does not
     * guarantee that a pool can become dormant (quiesced or
     * terminated), because external racing producers do not vote, and
     * can asynchronously submit new tasks. To deal with this, the
     * final unparked thread (in awaitWork) scans external queues to
     * check for tasks that could have been added during a race window
     * that would not be accompanied by a signal, in which case
     * re-activating itself (or any other worker) to recheck. The same
     * sets of checks are used in tryTerminate, to correctly trigger
     * delayed termination (shutDown, followed by quiescence) in the
     * presence of racing submissions. In all cases, the notion of the
     * "final" unparked thread is an approximation, because new
     * workers could be in the process of being constructed, which
     * occasionally adds some extra unnecessary processing.
     *
     * Shutdown and Termination. A call to shutdownNow invokes
     * tryTerminate to atomically set a mode bit. The calling thread,
     * as well as every other worker thereafter terminating, helps
     * terminate others by cancelling their unprocessed tasks, and
     * interrupting other workers. Calls to non-abrupt shutdown()
     * preface this by checking isQuiescent before triggering the
     * "STOP" phase of termination. During termination, workers are
     * stopped using all three of (often in parallel): releasing via
     * ctl (method reactivate), interrupts, and cancelling tasks that
     * will cause workers to not find work and exit. To support this,
     * worker references not removed from the queues array during
     * termination. It is possible for late thread creations to still
     * be in progress after a quiescent termination reports terminated
     * status, but they will also immediately terminate. To conform to
     * ExecutorService invoke, invokeAll, and invokeAny specs, we must
     * track pool status while waiting in ForkJoinTask.awaitDone, and
     * interrupt interruptible callers on termination, while also
     * avoiding cancelling other tasks that are normally completing
     * during quiescent termination. This is tracked by recording
     * ForkJoinTask.POOLSUBMIT in task status and/or as a bit flag
     * argument to joining methods.
     *
     * Trimming workers. To release resources after periods of lack of
     * use, a worker starting to wait when the pool is quiescent will
     * time out and terminate if the pool has remained quiescent for
     * period given by field keepAlive.
     *
     * Joining Tasks
     * =============
     *
     * Normally, the first option when joining a task that is not done
     * is to try to take it from local queue and run it.  Otherwise,
     * any of several actions may be taken when one worker is waiting
     * to join a task stolen (or always held) by another.  Because we
     * are multiplexing many tasks on to a pool of workers, we can't
     * always just let them block (as in Thread.join).  We also cannot
     * just reassign the joiner's run-time stack with another and
     * replace it later, which would be a form of "continuation", that
     * even if possible is not necessarily a good idea since we may
     * need both an unblocked task and its continuation to progress.
     * Instead we combine two tactics:
     *
     *   Helping: Arranging for the joiner to execute some task that it
     *      could be running if the steal had not occurred.
     *
     *   Compensating: Unless there are already enough live threads,
     *      method tryCompensate() may create or re-activate a spare
     *      thread to compensate for blocked joiners until they unblock.
     *
     * A third form (implemented via tryRemove) amounts to helping a
     * hypothetical compensator: If we can readily tell that a
     * possible action of a compensator is to steal and execute the
     * task being joined, the joining thread can do so directly,
     * without the need for a compensation thread; although with a
     * possibility of reduced parallelism because of a transient gap
     * in the queue array that stalls stealers.
     *
     * Other intermediate forms available for specific task types (for
     * example helpAsyncBlocker) often avoid or postpone the need for
     * blocking or compensation.
     *
     * The ManagedBlocker extension API can't use helping so relies
     * only on compensation in method awaitBlocker.
     *
     * The algorithm in helpJoin entails a form of "linear helping".
     * Each worker records (in field "source") a reference to the
     * queue from which it last stole a task.  The scan in method
     * helpJoin uses these markers to try to find a worker to help
     * (i.e., steal back a task from and execute it) that could hasten
     * completion of the actively joined task.  Thus, the joiner
     * executes a task that would be on its own local deque if the
     * to-be-joined task had not been stolen. This is a conservative
     * variant of the approach described in Wagner & Calder
     * "Leapfrogging: a portable technique for implementing efficient
     * futures" SIGPLAN Notices, 1993
     * (http://portal.acm.org/citation.cfm?id=155354). It differs
     * mainly in that we only record queues, not full dependency
     * links.  This requires a linear scan of the queues array to
     * locate stealers, but isolates cost to when it is needed, rather
     * than adding to per-task overhead.  For CountedCompleters, the
     * analogous method helpComplete doesn't need stealer-tracking,
     * but requires a similar check of completion chains.
     *
     * In either case, searches can fail to locate stealers when
     * stalls delay recording sources. We avoid some of these cases by
     * using snapshotted values of ctl as a check that the numbers of
     * workers are not changing.  But even when accurately identified,
     * stealers might not ever produce a task that the joiner can in
     * turn help with. So, compensation is tried upon failure to find
     * tasks to run.
     *
     * Compensation does not by default aim to keep exactly the target
     * parallelism number of unblocked threads running at any given
     * time. Some previous versions of this class employed immediate
     * compensations for any blocked join. However, in practice, the
     * vast majority of blockages are transient byproducts of GC and
     * other JVM or OS activities that are made worse by replacement
     * when they cause longer-term oversubscription.  Rather than
     * impose arbitrary policies, we allow users to override the
     * default of only adding threads upon apparent starvation.  The
     * compensation mechanism may also be bounded.  Bounds for the
     * commonPool better enable JVMs to cope with programming errors
     * and abuse before running out of resources to do so.
     *
     * Common Pool
     * ===========
     *
     * The static common pool always exists after static
     * initialization.  Since it (or any other created pool) need
     * never be used, we minimize initial construction overhead and
     * footprint to the setup of about a dozen fields, although with
     * some System property parsing and with security processing that
     * takes far longer than the actual construction when
     * SecurityManagers are used or properties are set. The common
     * pool is distinguished internally by having both a null
     * workerNamePrefix and ISCOMMON config bit set, along with
     * PRESET_SIZE set if parallelism was configured by system
     * property.
     *
     * When external threads use ForkJoinTask.fork for the common
     * pool, they can perform subtask processing (see helpComplete and
     * related methods) upon joins.  This caller-helps policy makes it
     * sensible to set common pool parallelism level to one (or more)
     * less than the total number of available cores, or even zero for
     * pure caller-runs. For the sake of ExecutorService specs, we can
     * only do this for tasks entered via fork, not submit.  We track
     * this using a task status bit (markPoolSubmission).  In all
     * other cases, external threads waiting for joins first check the
     * common pool for their task, which fails quickly if the caller
     * did not fork to common pool.
     *
     * Guarantees for common pool parallelism zero are limited to
     * tasks that are joined by their callers in a tree-structured
     * fashion or use CountedCompleters (as is true for jdk
     * parallelStreams). Support infiltrates several methods,
     * including those that retry helping steps or spin until we are
     * sure that none apply if there are no workers.
     *
     * As a more appropriate default in managed environments, unless
     * overridden by system properties, we use workers of subclass
     * InnocuousForkJoinWorkerThread when there is a SecurityManager
     * present. These workers have no permissions set, do not belong
     * to any user-defined ThreadGroup, and clear all ThreadLocals
     * after executing any top-level task.  The associated mechanics
     * may be JVM-dependent and must access particular Thread class
     * fields to achieve this effect.
     *
     * Interrupt handling
     * ==================
     *
     * The framework is designed to manage task cancellation
     * (ForkJoinTask.cancel) independently from the interrupt status
     * of threads running tasks. (See the public ForkJoinTask
     * documentation for rationale.)  Interrupts are issued only in
     * tryTerminate, when workers should be terminating and tasks
     * should be cancelled anyway. Interrupts are cleared only when
     * necessary to ensure that calls to LockSupport.park do not loop
     * indefinitely (park returns immediately if the current thread is
     * interrupted).  For cases in which task bodies are specified or
     * desired to interrupt upon cancellation, ForkJoinTask.cancel can
     * be overridden to do so (as is done for invoke{Any,All}).
     *
     * Memory placement
     * ================
     *
     * Performance is very sensitive to placement of instances of
     * ForkJoinPool and WorkQueues and their queue arrays, as well the
     * placement of their fields. Caches misses and contention due to
     * false-sharing have been observed to slow down some programs by
     * more than a factor of four. There is no perfect solution, in
     * part because isolating more fields also generates more cache
     * misses in more common cases (because some fields snd slots are
     * usually read at the same time), and the main means of placing
     * memory, the @Contended annotation provides only rough control
     * (for good reason). We isolate the ForkJoinPool.ctl field as
     * well the set of WorkQueue fields that otherwise cause the most
     * false-sharing misses with respect to other fields. Also,
     * ForkJoinPool fields are ordered such that fields less prone to
     * contention effects are first, offsetting those that otherwise
     * would be, while also reducing total footprint vs using
     * multiple @Contended regions, which tends to slow down
     * less-contended applications.  These arrangements mainly reduce
     * cache traffic by scanners, which speeds up finding tasks to
     * run.  Initial sizing and resizing of WorkQueue arrays is an
     * even more delicate tradeoff because the best strategy may vary
     * across garbage collectors. Small arrays are better for locality
     * and reduce GC scan time, but large arrays reduce both direct
     * false-sharing and indirect cases due to GC bookkeeping
     * (cardmarks etc), and reduce the number of resizes, which are
     * not especially fast because they require atomic transfers, and
     * may cause other scanning workers to stall or give up.
     * Currently, arrays are initialized to be fairly small but early
     * resizes rapidly increase size by more than a factor of two
     * until very large.  (Maintenance note: any changes in fields,
     * queues, or their uses must be accompanied by re-evaluation of
     * these placement and sizing decisions.)
     *
     * Style notes
     * ===========
     *
     * Memory ordering relies mainly on atomic operations (CAS,
     * getAndSet, getAndAdd) along with moded accesses. These use
     * jdk-internal Unsafe for atomics and special memory modes,
     * rather than VarHandles, to avoid initialization dependencies in
     * other jdk components that require early parallelism.  This can
     * be awkward and ugly, but also reflects the need to control
     * outcomes across the unusual cases that arise in very racy code
     * with very few invariants. All fields are read into locals
     * before use, and null-checked if they are references, even if
     * they can never be null under current usages. Usually,
     * computations (held in local variables) are defined as soon as
     * logically enabled, sometimes to convince compilers that they
     * may be performed despite memory ordering constraints.  Array
     * accesses using masked indices include checks (that are always
     * true) that the array length is non-zero to avoid compilers
     * inserting more expensive traps.  This is usually done in a
     * "C"-like style of listing declarations at the heads of methods
     * or blocks, and using inline assignments on first encounter.
     * Nearly all explicit checks lead to bypass/return, not exception
     * throws, because they may legitimately arise during shutdown. A
     * few unusual loop constructions encourage (with varying
     * effectiveness) JVMs about where (not) to place safepoints.
     *
     * There is a lot of representation-level coupling among classes
     * ForkJoinPool, ForkJoinWorkerThread, and ForkJoinTask.  The
     * fields of WorkQueue maintain data structures managed by
     * ForkJoinPool, so are directly accessed.  There is little point
     * trying to reduce this, since any associated future changes in
     * representations will need to be accompanied by algorithmic
     * changes anyway. Several methods intrinsically sprawl because
     * they must accumulate sets of consistent reads of fields held in
     * local variables. Some others are artificially broken up to
     * reduce producer/consumer imbalances due to dynamic compilation.
     * There are also other coding oddities (including several
     * unnecessary-looking hoisted null checks) that help some methods
     * perform reasonably even when interpreted (not compiled).
     *
     * The order of declarations in this file is (with a few exceptions):
     * (1) Static constants
     * (2) Static utility functions
     * (3) Nested (static) classes
     * (4) Fields, along with constants used when unpacking some of them
     * (5) Internal control methods
     * (6) Callbacks and other support for ForkJoinTask methods
     * (7) Exported methods
     * (8) Static block initializing statics in minimally dependent order
     *
     * Revision notes
     * ==============
     *
     * The main sources of differences from previous version are:
     *
     * * Use of Unsafe vs VarHandle, including re-instatement of some
     *   constructions from pre-VarHandle versions.
     * * Reduced memory and signal contention, mainly by distinguishing
     *   failure cases.
     * * Improved initialization, in part by preparing for possible
     *   removal of SecurityManager
     * * Enable resizing (includes refactoring quiescence/termination)
     * * Unification of most internal vs external operations; some made
     *   possible via use of WorkQueue.access, and POOLSUBMIT status in tasks.
     */

    // static configuration constants

    /**
     * Default idle timeout value (in milliseconds) for idle threads
     * to park waiting for new work before terminating.
     */
    static final long DEFAULT_KEEPALIVE = 60_000L;

    /**
     * Undershoot tolerance for idle timeouts
     */
    static final long TIMEOUT_SLOP = 20L;

    /**
     * The default value for common pool maxSpares.  Overridable using
     * the "java.util.concurrent.ForkJoinPool.common.maximumSpares"
     * system property.  The default value is far in excess of normal
     * requirements, but also far short of MAX_CAP and typical OS
     * thread limits, so allows JVMs to catch misuse/abuse before
     * running out of resources needed to do so.
     */
    static final int DEFAULT_COMMON_MAX_SPARES = 256;

    /**
     * Initial capacity of work-stealing queue array.  Must be a power
     * of two, at least 2. See above.
     */
    static final int INITIAL_QUEUE_CAPACITY = 1 << 6;

    // Bounds
    static final int SWIDTH    = 16;               // width of short
    static final int SMASK     = 0xffff;           // short bits == max index
    static final int MAX_CAP   = 0x7fff;           // max #workers - 1

    // pool.runState and workQueue.access bits and sentinels
    static final int STOP         = 1 << 31;       // must be negative
    static final int SHUTDOWN     = 1;
    static final int TERMINATED   = 2;
    static final int PARKED       = -1;            // access value when parked

    // {pool, workQueue}.config bits
    static final int FIFO         = 1 << 16;       // fifo queue or access mode
    static final int SRC          = 1 << 17;       // set when stealable
    static final int CLEAR_TLS    = 1 << 18;       // set for Innocuous workers
    static final int TRIMMED      = 1 << 19;       // timed out while idle
    static final int ISCOMMON     = 1 << 20;       // set for common pool
    static final int PRESET_SIZE  = 1 << 21;       // size was set by property

    static final int UNCOMPENSATE = 1 << 16;       // tryCompensate return

    /*
     * Bits and masks for ctl and bounds are packed with 4 16 bit subfields:
     * RC: Number of released (unqueued) workers
     * TC: Number of total workers
     * SS: version count and status of top waiting thread
     * ID: poolIndex of top of Treiber stack of waiters
     *
     * When convenient, we can extract the lower 32 stack top bits
     * (including version bits) as sp=(int)ctl. When sp is non-zero,
     * there are waiting workers.  Count fields may be transiently
     * negative during termination because of out-of-order updates.
     * To deal with this, we use casts in and out of "short" and/or
     * signed shifts to maintain signedness. Because it occupies
     * uppermost bits, we can add one release count using getAndAdd of
     * RC_UNIT, rather than CAS, when returning from a blocked join.
     * Other updates of multiple subfields require CAS.
     */

    // Lower and upper word masks
    static final long SP_MASK  = 0xffffffffL;
    static final long UC_MASK  = ~SP_MASK;
    // Release counts
    static final int  RC_SHIFT = 48;
    static final long RC_UNIT  = 0x0001L << RC_SHIFT;
    static final long RC_MASK  = 0xffffL << RC_SHIFT;
    // Total counts
    static final int  TC_SHIFT = 32;
    static final long TC_UNIT  = 0x0001L << TC_SHIFT;
    static final long TC_MASK  = 0xffffL << TC_SHIFT;
    // sp bits
    static final int SS_SEQ    = 1 << 16;  // version count
    static final int INACTIVE  = 1 << 31;  // phase bit when idle

    // Static utilities

    /**
     * If there is a security manager, makes sure caller has
     * permission to modify threads.
     */
    @SuppressWarnings("removal")
    private static void checkPermission() {
        SecurityManager security; RuntimePermission perm;
        if ((security = System.getSecurityManager()) != null) {
            if ((perm = modifyThreadPermission) == null)
                modifyThreadPermission = perm = // races OK
                    new RuntimePermission("modifyThread");
            security.checkPermission(perm);
        }
    }

    // Nested classes

    /**
     * Factory for creating new {@link ForkJoinWorkerThread}s.
     * A {@code ForkJoinWorkerThreadFactory} must be defined and used
     * for {@code ForkJoinWorkerThread} subclasses that extend base
     * functionality or initialize threads with different contexts.
     */
    public static interface ForkJoinWorkerThreadFactory {
        /**
         * Returns a new worker thread operating in the given pool.
         * Returning null or throwing an exception may result in tasks
         * never being executed.  If this method throws an exception,
         * it is relayed to the caller of the method (for example
         * {@code execute}) causing attempted thread creation. If this
         * method returns null or throws an exception, it is not
         * retried until the next attempted creation (for example
         * another call to {@code execute}).
         *
         * @param pool the pool this thread works in
         * @return the new worker thread, or {@code null} if the request
         *         to create a thread is rejected
         * @throws NullPointerException if the pool is null
         */
        public ForkJoinWorkerThread newThread(ForkJoinPool pool);
    }

    /**
     * Default ForkJoinWorkerThreadFactory implementation; creates a
     * new ForkJoinWorkerThread using the system class loader as the
     * thread context class loader.
     */
    static final class DefaultForkJoinWorkerThreadFactory
        implements ForkJoinWorkerThreadFactory {
        public final ForkJoinWorkerThread newThread(ForkJoinPool pool) {
            boolean isCommon = (pool.workerNamePrefix == null);
            @SuppressWarnings("removal")
            SecurityManager sm = System.getSecurityManager();
            if (sm == null)
                return new ForkJoinWorkerThread(null, pool, true, false);
            else if (isCommon)
                return newCommonWithACC(pool);
            else
                return newRegularWithACC(pool);
        }

        /*
         * Create and use static AccessControlContexts only if there
         * is a SecurityManager. (These can be removed if/when
         * SecurityManagers are removed from platform.) The ACCs are
         * immutable and equivalent even when racily initialized, so
         * they don't require locking, although with the chance of
         * needlessly duplicate construction.
         */
        @SuppressWarnings("removal")
        static volatile AccessControlContext regularACC, commonACC;

        @SuppressWarnings("removal")
        static ForkJoinWorkerThread newRegularWithACC(ForkJoinPool pool) {
            AccessControlContext acc = regularACC;
            if (acc == null) {
                Permissions ps = new Permissions();
                ps.add(new RuntimePermission("getClassLoader"));
                ps.add(new RuntimePermission("setContextClassLoader"));
                regularACC = acc =
                    new AccessControlContext(new ProtectionDomain[] {
                            new ProtectionDomain(null, ps) });
            }
            return AccessController.doPrivileged(
                new PrivilegedAction<>() {
                    public ForkJoinWorkerThread run() {
                        return new ForkJoinWorkerThread(null, pool, true, false);
                    }}, acc);
        }

        @SuppressWarnings("removal")
        static ForkJoinWorkerThread newCommonWithACC(ForkJoinPool pool) {
            AccessControlContext acc = commonACC;
            if (acc == null) {
                Permissions ps = new Permissions();
                ps.add(new RuntimePermission("getClassLoader"));
                ps.add(new RuntimePermission("setContextClassLoader"));
                ps.add(new RuntimePermission("modifyThread"));
                ps.add(new RuntimePermission("enableContextClassLoaderOverride"));
                ps.add(new RuntimePermission("modifyThreadGroup"));
                commonACC = acc =
                    new AccessControlContext(new ProtectionDomain[] {
                            new ProtectionDomain(null, ps) });
            }
            return AccessController.doPrivileged(
                new PrivilegedAction<>() {
                    public ForkJoinWorkerThread run() {
                        return new ForkJoinWorkerThread.
                            InnocuousForkJoinWorkerThread(pool);
                    }}, acc);
        }
    }

    /**
     * Queues supporting work-stealing as well as external task
     * submission. See above for descriptions and algorithms.
     */
    static final class WorkQueue {
        int stackPred;             // pool stack (ctl) predecessor link
        int config;                // index, mode, ORed with SRC after init
        int base;                  // index of next slot for poll
        ForkJoinTask<?>[] array;   // the queued tasks; power of 2 size
        final ForkJoinWorkerThread owner; // owning thread or null if shared

        // fields otherwise causing more unnecessary false-sharing cache misses
        @jdk.internal.vm.annotation.Contended("w")
        int top;                   // index of next slot for push
        @jdk.internal.vm.annotation.Contended("w")
        volatile int access;       // values 0, 1 (locked), PARKED, STOP
        @jdk.internal.vm.annotation.Contended("w")
        volatile int phase;        // versioned, negative if inactive
        @jdk.internal.vm.annotation.Contended("w")
        volatile int source;       // source queue id in topLevelExec
        @jdk.internal.vm.annotation.Contended("w")
        int nsteals;               // number of steals from other queues

        // Support for atomic operations
        private static final Unsafe U;
        private static final long ACCESS;
        private static final long PHASE;
        private static final long ABASE;
        private static final int  ASHIFT;

        static ForkJoinTask<?> getAndClearSlot(ForkJoinTask<?>[] a, int i) {
            return (ForkJoinTask<?>)
                U.getAndSetReference(a, ((long)i << ASHIFT) + ABASE, null);
        }
        static boolean casSlotToNull(ForkJoinTask<?>[] a, int i,
                                     ForkJoinTask<?> c) {
            return U.compareAndSetReference(a, ((long)i << ASHIFT) + ABASE,
                                            c, null);
        }
        final void forcePhaseActive() {    // clear sign bit
            U.getAndBitwiseAndInt(this, PHASE, 0x7fffffff);
        }
        final int getAndSetAccess(int v) {
            return U.getAndSetInt(this, ACCESS, v);
        }
        final void releaseAccess() {
            U.putIntRelease(this, ACCESS, 0);
        }

        /**
         * Constructor. For owned queues, most fields are initialized
         * upon thread start in pool.registerWorker.
         */
        WorkQueue(ForkJoinWorkerThread owner, int config) {
            this.owner = owner;
            this.config = config;
            base = top = 1;
        }

        /**
         * Returns an exportable index (used by ForkJoinWorkerThread).
         */
        final int getPoolIndex() {
            return (config & 0xffff) >>> 1; // ignore odd/even tag bit
        }

        /**
         * Returns the approximate number of tasks in the queue.
         */
        final int queueSize() {
            int unused = access;            // for ordering effect
            return Math.max(top - base, 0); // ignore transient negative
        }

        /**
         * Pushes a task. Called only by owner or if already locked
         *
         * @param task the task. Caller must ensure non-null.
         * @param pool the pool. Must be non-null unless terminating.
         * @param signalIfEmpty true if signal when pushing to empty queue
         * @throws RejectedExecutionException if array cannot be resized
         */
        final void push(ForkJoinTask<?> task, ForkJoinPool pool,
                        boolean signalIfEmpty) {
            boolean resize = false;
            int s = top++, b = base, cap, m; ForkJoinTask<?>[] a;
            if ((a = array) != null && (cap = a.length) > 0) {
                if ((m = (cap - 1)) == s - b) {
                    resize = true;            // rapidly grow until large
                    int newCap = (cap < 1 << 24) ? cap << 2 : cap << 1;
                    ForkJoinTask<?>[] newArray;
                    try {
                        newArray = new ForkJoinTask<?>[newCap];
                    } catch (Throwable ex) {
                        top = s;
                        access = 0;
                        throw new RejectedExecutionException(
                            "Queue capacity exceeded");
                    }
                    if (newCap > 0) {         // always true
                        int newMask = newCap - 1, k = s;
                        do {                  // poll old, push to new
                            newArray[k-- & newMask] = task;
                        } while ((task = getAndClearSlot(a, k & m)) != null);
                    }
                    array = newArray;
                }
                else
                    a[m & s] = task;
                getAndSetAccess(0);           // for memory effects if owned
                if ((resize || (a[m & (s - 1)] == null && signalIfEmpty)) &&
                    pool != null)
                    pool.signalWork();
            }
        }

        /**
         * Takes next task, if one exists, in order specified by mode,
         * so acts as either local-pop or local-poll. Called only by owner.
         * @param fifo nonzero if FIFO mode
         */
        final ForkJoinTask<?> nextLocalTask(int fifo) {
            ForkJoinTask<?> t = null;
            ForkJoinTask<?>[] a = array;
            int p = top, s = p - 1, b = base, nb, cap;
            if (p - b > 0 && a != null && (cap = a.length) > 0) {
                do {
                    if (fifo == 0 || (nb = b + 1) == p) {
                        if ((t = getAndClearSlot(a, (cap - 1) & s)) != null)
                            top = s;
                        break;                   // lost race for only task
                    }
                    else if ((t = getAndClearSlot(a, (cap - 1) & b)) != null) {
                        base = nb;
                        break;
                    }
                    else {
                        while (b == (b = base)) {
                            U.loadFence();
                            Thread.onSpinWait(); // spin to reduce memory traffic
                        }
                    }
                } while (p - b > 0);
                U.storeStoreFence(); // for timely index updates
            }
            return t;
        }

        /**
         * Takes next task, if one exists, using configured mode.
         * (Always owned, never called for Common pool.)
         */
        final ForkJoinTask<?> nextLocalTask() {
            return nextLocalTask(config & FIFO);
        }

        /**
         * Pops the given task only if it is at the current top.
         */
        final boolean tryUnpush(ForkJoinTask<?> task, boolean owned) {
            ForkJoinTask<?>[] a = array;
            int p = top, s, cap, k;
            if (task != null && base != p && a != null && (cap = a.length) > 0 &&
                a[k = (cap - 1) & (s = p - 1)] == task) {
                if (owned || getAndSetAccess(1) == 0) {
                    if (top != p || a[k] != task ||
                        getAndClearSlot(a, k) == null)
                        access = 0;
                    else {
                        top = s;
                        access = 0;
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * Returns next task, if one exists, in order specified by mode.
         */
        final ForkJoinTask<?> peek() {
            ForkJoinTask<?>[] a = array;
            int cfg = config, p = top, b = base, cap;
            if (p != b && a != null && (cap = a.length) > 0) {
                if ((cfg & FIFO) == 0)
                    return a[(cap - 1) & (p - 1)];
                else { // skip over in-progress removals
                    ForkJoinTask<?> t;
                    for ( ; p - b > 0; ++b) {
                        if ((t = a[(cap - 1) & b]) != null)
                            return t;
                    }
                }
            }
            return null;
        }

        /**
         * Polls for a task. Used only by non-owners in usually
         * uncontended contexts.
         *
         * @param pool if nonnull, pool to signal if more tasks exist
         */
        final ForkJoinTask<?> poll(ForkJoinPool pool) {
            for (int b = base;;) {
                int cap; ForkJoinTask<?>[] a;
                if ((a = array) == null || (cap = a.length) <= 0)
                    break;                        // currently impossible
                int k = (cap - 1) & b, nb = b + 1, nk = (cap - 1) & nb;
                ForkJoinTask<?> t = a[k];
                U.loadFence();                    // for re-reads
                if (b != (b = base))              // inconsistent
                    ;
                else if (t != null && casSlotToNull(a, k, t)) {
                    base = nb;
                    U.storeFence();
                    if (pool != null && a[nk] != null)
                        pool.signalWork();        // propagate
                    return t;
                }
                else if (array != a || a[k] != null)
                    ;                             // stale
                else if (a[nk] == null && top - b <= 0)
                    break;                        // empty
            }
            return null;
        }

        /**
         * Tries to poll next task in FIFO order, failing on
         * contention or stalls. Used only by topLevelExec to repoll
         * from the queue obtained from pool.scan.
         */
        final ForkJoinTask<?> tryPoll() {
            int b = base, cap; ForkJoinTask<?>[] a;
            if ((a = array) != null && (cap = a.length) > 0) {
                for (;;) {
                    int k = (cap - 1) & b, nb = b + 1;
                    ForkJoinTask<?> t = a[k];
                    U.loadFence();                // for re-reads
                    if (b != (b = base))
                        ;                         // inconsistent
                    else if (t != null) {
                        if (casSlotToNull(a, k, t)) {
                            base = nb;
                            U.storeStoreFence();
                            return t;
                        }
                        break;                   // contended
                    }
                    else if (a[k] == null)
                        break;                   // empty or stalled
                }
            }
            return null;
        }

        // specialized execution methods

        /**
         * Runs the given (stolen) task if nonnull, as well as
         * remaining local tasks and/or others available from its
         * source queue, if any.
         */
        final void topLevelExec(ForkJoinTask<?> task, WorkQueue src) {
            int cfg = config, fifo = cfg & FIFO, nstolen = 1;
            while (task != null) {
                task.doExec();
                if ((task = nextLocalTask(fifo)) == null &&
                    src != null && (task = src.tryPoll()) != null)
                    ++nstolen;
            }
            nsteals += nstolen;
            source = 0;
            if ((cfg & CLEAR_TLS) != 0)
                ThreadLocalRandom.eraseThreadLocals(Thread.currentThread());
        }

        /**
         * Deep form of tryUnpush: Traverses from top and removes and
         * runs task if present, shifting others to fill gap.
         * @return task status if removed, else 0
         */
        final int tryRemoveAndExec(ForkJoinTask<?> task, boolean owned) {
            ForkJoinTask<?>[] a = array;
            int p = top, s = p - 1, d = p - base, cap;
            if (task != null && d > 0 && a != null && (cap = a.length) > 0) {
                for (int m = cap - 1, i = s; ; --i) {
                    ForkJoinTask<?> t; int k;
                    if ((t = a[k = i & m]) == task) {
                        if (!owned && getAndSetAccess(1) != 0)
                            break;                 // fail if locked
                        else if (top != p || a[k] != task ||
                                 getAndClearSlot(a, k) == null) {
                            access = 0;
                            break;                 // missed
                        }
                        else {
                            if (i != s && i == base)
                                base = i + 1;      // avoid shift
                            else {
                                for (int j = i; j != s;) // shift down
                                    a[j & m] = getAndClearSlot(a, ++j & m);
                                top = s;
                            }
                            releaseAccess();
                            return task.doExec();
                        }
                    }
                    else if (t == null || --d == 0)
                        break;
                }
            }
            return 0;
        }

        /**
         * Tries to pop and run tasks within the target's computation
         * until done, not found, or limit exceeded.
         *
         * @param task root of computation
         * @param limit max runs, or zero for no limit
         * @return task status on exit
         */
        final int helpComplete(ForkJoinTask<?> task, boolean owned, int limit) {
            int status = 0;
            if (task != null) {
                outer: for (;;) {
                    ForkJoinTask<?>[] a; ForkJoinTask<?> t;
                    int p, s, cap, k;
                    if ((status = task.status) < 0)
                        return status;
                    if ((a = array) == null || (cap = a.length) <= 0 ||
                        (t = a[k = (cap - 1) & (s = (p = top) - 1)]) == null ||
                        !(t instanceof CountedCompleter))
                        break;
                    for (CountedCompleter<?> f = (CountedCompleter<?>)t;;) {
                        if (f == task)
                            break;
                        else if ((f = f.completer) == null)
                            break outer;       // ineligible
                    }
                    if (!owned && getAndSetAccess(1) != 0)
                        break;                 // fail if locked
                    if (top != p || a[k] != t || getAndClearSlot(a, k) == null) {
                        access = 0;
                        break;                 // missed
                    }
                    top = s;
                    releaseAccess();
                    t.doExec();
                    if (limit != 0 && --limit == 0)
                        break;
                }
                status = task.status;
            }
            return status;
        }

        /**
         * Tries to poll and run AsynchronousCompletionTasks until
         * none found or blocker is released
         *
         * @param blocker the blocker
         */
        final void helpAsyncBlocker(ManagedBlocker blocker) {
            if (blocker != null) {
                for (;;) {
                    int b = base, cap; ForkJoinTask<?>[] a;
                    if ((a = array) == null || (cap = a.length) <= 0 || b == top)
                        break;
                    int k = (cap - 1) & b, nb = b + 1, nk = (cap - 1) & nb;
                    ForkJoinTask<?> t = a[k];
                    U.loadFence();                     // for re-reads
                    if (base != b)
                        ;
                    else if (blocker.isReleasable())
                        break;
                    else if (a[k] != t)
                        ;
                    else if (t != null) {
                        if (!(t instanceof CompletableFuture
                              .AsynchronousCompletionTask))
                            break;
                        else if (casSlotToNull(a, k, t)) {
                            base = nb;
                            U.storeStoreFence();
                            t.doExec();
                        }
                    }
                    else if (a[nk] == null)
                        break;
                }
            }
        }

        // misc

        /**
         * Returns true if owned by a worker thread and not known to be blocked.
         */
        final boolean isApparentlyUnblocked() {
            Thread wt; Thread.State s;
            return (access != STOP && (wt = owner) != null &&
                    (s = wt.getState()) != Thread.State.BLOCKED &&
                    s != Thread.State.WAITING &&
                    s != Thread.State.TIMED_WAITING);
        }

        /**
         * Called in constructors if ThreadLocals not preserved
         */
        final void setClearThreadLocals() {
            config |= CLEAR_TLS;
        }

        static {
            U = Unsafe.getUnsafe();
            Class<WorkQueue> klass = WorkQueue.class;
            ACCESS = U.objectFieldOffset(klass, "access");
            PHASE = U.objectFieldOffset(klass, "phase");
            Class<ForkJoinTask[]> aklass = ForkJoinTask[].class;
            ABASE = U.arrayBaseOffset(aklass);
            int scale = U.arrayIndexScale(aklass);
            ASHIFT = 31 - Integer.numberOfLeadingZeros(scale);
            if ((scale & (scale - 1)) != 0)
                throw new Error("array index scale not a power of two");
        }
    }

    // static fields (initialized in static initializer below)

    /**
     * Creates a new ForkJoinWorkerThread. This factory is used unless
     * overridden in ForkJoinPool constructors.
     */
    public static final ForkJoinWorkerThreadFactory
        defaultForkJoinWorkerThreadFactory;

    /**
     * Common (static) pool. Non-null for public use unless a static
     * construction exception, but internal usages null-check on use
     * to paranoically avoid potential initialization circularities
     * as well as to simplify generated code.
     */
    static final ForkJoinPool common;

    /**
     * Sequence number for creating worker names
     */
    private static volatile int poolIds;

    /**
     * Permission required for callers of methods that may start or
     * kill threads. Lazily constructed.
     */
    static volatile RuntimePermission modifyThreadPermission;


    // Instance fields
    volatile long stealCount;            // collects worker nsteals
    volatile long threadIds;             // for worker thread names
    final long keepAlive;                // milliseconds before dropping if idle
    final long bounds;                   // min, max threads packed as shorts
    final int config;                    // static configuration bits
    volatile int runState;               // SHUTDOWN, STOP, TERMINATED bits
    WorkQueue[] queues;                  // main registry
    final ReentrantLock registrationLock;
    Condition termination;               // lazily constructed
    final String workerNamePrefix;       // null for common pool
    final ForkJoinWorkerThreadFactory factory;
    final UncaughtExceptionHandler ueh;  // per-worker UEH
    final Predicate<? super ForkJoinPool> saturate;
    final SharedThreadContainer container;

    @jdk.internal.vm.annotation.Contended("fjpctl") // segregate
    volatile long ctl;                   // main pool control
    @jdk.internal.vm.annotation.Contended("fjpctl") // colocate
    int parallelism;                     // target number of workers

    // Support for atomic operations
    private static final Unsafe U;
    private static final long CTL;
    private static final long RUNSTATE;
    private static final long PARALLELISM;
    private static final long THREADIDS;
    private static final long POOLIDS;

    private boolean compareAndSetCtl(long c, long v) {
        return U.compareAndSetLong(this, CTL, c, v);
    }
    private long compareAndExchangeCtl(long c, long v) {
        return U.compareAndExchangeLong(this, CTL, c, v);
    }
    private long getAndAddCtl(long v) {
        return U.getAndAddLong(this, CTL, v);
    }
    private int getAndBitwiseOrRunState(int v) {
        return U.getAndBitwiseOrInt(this, RUNSTATE, v);
    }
    private long incrementThreadIds() {
        return U.getAndAddLong(this, THREADIDS, 1L);
    }
    private static int getAndAddPoolIds(int x) {
        return U.getAndAddInt(ForkJoinPool.class, POOLIDS, x);
    }
    private int getAndSetParallelism(int v) {
        return U.getAndSetInt(this, PARALLELISM, v);
    }
    private int getParallelismOpaque() {
        return U.getIntOpaque(this, PARALLELISM);
    }

    // Creating, registering, and deregistering workers

    /**
     * Tries to construct and start one worker. Assumes that total
     * count has already been incremented as a reservation.  Invokes
     * deregisterWorker on any failure.
     *
     * @return true if successful
     */
    private boolean createWorker() {
        ForkJoinWorkerThreadFactory fac = factory;
        Throwable ex = null;
        ForkJoinWorkerThread wt = null;
        try {
            if (runState >= 0 &&  // avoid construction if terminating
                fac != null && (wt = fac.newThread(this)) != null) {
                container.start(wt);
                return true;
            }
        } catch (Throwable rex) {
            ex = rex;
        }
        deregisterWorker(wt, ex);
        return false;
    }

    /**
     * Provides a name for ForkJoinWorkerThread constructor.
     */
    final String nextWorkerThreadName() {
        String prefix = workerNamePrefix;
        long tid = incrementThreadIds() + 1L;
        if (prefix == null) // commonPool has no prefix
            prefix = "ForkJoinPool.commonPool-worker-";
        return prefix.concat(Long.toString(tid));
    }

    /**
     * Finishes initializing and records owned queue.
     *
     * @param w caller's WorkQueue
     */
    final void registerWorker(WorkQueue w) {
        ThreadLocalRandom.localInit();
        int seed = ThreadLocalRandom.getProbe();
        ReentrantLock lock = registrationLock;
        int cfg = config & FIFO;
        if (w != null && lock != null) {
            w.array = new ForkJoinTask<?>[INITIAL_QUEUE_CAPACITY];
            cfg |= w.config | SRC;
            w.stackPred = seed;
            int id = (seed << 1) | 1;                   // initial index guess
            lock.lock();
            try {
                WorkQueue[] qs; int n;                  // find queue index
                if ((qs = queues) != null && (n = qs.length) > 0) {
                    int k = n, m = n - 1;
                    for (; qs[id &= m] != null && k > 0; id -= 2, k -= 2);
                    if (k == 0)
                        id = n | 1;                     // resize below
                    w.phase = w.config = id | cfg;      // now publishable

                    if (id < n)
                        qs[id] = w;
                    else {                              // expand array
                        int an = n << 1, am = an - 1;
                        WorkQueue[] as = new WorkQueue[an];
                        as[id & am] = w;
                        for (int j = 1; j < n; j += 2)
                            as[j] = qs[j];
                        for (int j = 0; j < n; j += 2) {
                            WorkQueue q;
                            if ((q = qs[j]) != null)    // shared queues may move
                                as[q.config & am] = q;
                        }
                        U.storeFence();                 // fill before publish
                        queues = as;
                    }
                }
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * Final callback from terminating worker, as well as upon failure
     * to construct or start a worker.  Removes record of worker from
     * array, and adjusts counts. If pool is shutting down, tries to
     * complete termination.
     *
     * @param wt the worker thread, or null if construction failed
     * @param ex the exception causing failure, or null if none
     */
    final void deregisterWorker(ForkJoinWorkerThread wt, Throwable ex) {
        WorkQueue w = (wt == null) ? null : wt.workQueue;
        int cfg = (w == null) ? 0 : w.config;
        long c = ctl;
        if ((cfg & TRIMMED) == 0)             // decrement counts
            do {} while (c != (c = compareAndExchangeCtl(
                                   c, ((RC_MASK & (c - RC_UNIT)) |
                                       (TC_MASK & (c - TC_UNIT)) |
                                       (SP_MASK & c)))));
        else if ((int)c == 0)                 // was dropped on timeout
            cfg &= ~SRC;                      // suppress signal if last
        if (!tryTerminate(false, false) && w != null) {
            ReentrantLock lock; WorkQueue[] qs; int n, i;
            long ns = w.nsteals & 0xffffffffL;
            if ((lock = registrationLock) != null) {
                lock.lock();                  // remove index unless terminating
                if ((qs = queues) != null && (n = qs.length) > 0 &&
                    qs[i = cfg & (n - 1)] == w)
                    qs[i] = null;
                stealCount += ns;             // accumulate steals
                lock.unlock();
            }
            if ((cfg & SRC) != 0)
                signalWork();                 // possibly replace worker
        }
        if (ex != null) {
            if (w != null) {
                w.access = STOP;              // cancel tasks
                for (ForkJoinTask<?> t; (t = w.nextLocalTask(0)) != null; )
                    ForkJoinTask.cancelIgnoringExceptions(t);
            }
            ForkJoinTask.rethrow(ex);
        }
    }

    /*
     * Releases an idle worker, or creates one if not enough exist.
     */
    final void signalWork() {
        int pc = parallelism, n;
        long c = ctl;
        WorkQueue[] qs = queues;
        if ((short)(c >>> RC_SHIFT) < pc && qs != null && (n = qs.length) > 0) {
            for (;;) {
                boolean create = false;
                int sp = (int)c & ~INACTIVE;
                WorkQueue v = qs[sp & (n - 1)];
                int deficit = pc - (short)(c >>> TC_SHIFT);
                long ac = (c + RC_UNIT) & RC_MASK, nc;
                if (sp != 0 && v != null)
                    nc = (v.stackPred & SP_MASK) | (c & TC_MASK);
                else if (deficit <= 0)
                    break;
                else {
                    create = true;
                    nc = ((c + TC_UNIT) & TC_MASK);
                }
                if (c == (c = compareAndExchangeCtl(c, nc | ac))) {
                    if (create)
                        createWorker();
                    else {
                        Thread owner = v.owner;
                        v.phase = sp;
                        if (v.access == PARKED)
                            LockSupport.unpark(owner);
                    }
                    break;
                }
            }
        }
    }

    /**
     * Reactivates any idle worker, if one exists.
     *
     * @return the signalled worker, or null if none
     */
    private WorkQueue reactivate() {
        WorkQueue[] qs; int n;
        long c = ctl;
        if ((qs = queues) != null && (n = qs.length) > 0) {
            for (;;) {
                int sp = (int)c & ~INACTIVE;
                WorkQueue v = qs[sp & (n - 1)];
                long ac = UC_MASK & (c + RC_UNIT);
                if (sp == 0 || v == null)
                    break;
                if (c == (c = compareAndExchangeCtl(
                              c, (v.stackPred & SP_MASK) | ac))) {
                    Thread owner = v.owner;
                    v.phase = sp;
                    if (v.access == PARKED)
                        LockSupport.unpark(owner);
                    return v;
                }
            }
        }
        return null;
    }

    /**
     * Tries to deactivate worker w; called only on idle timeout.
     */
    private boolean tryTrim(WorkQueue w) {
        if (w != null) {
            int pred = w.stackPred, cfg = w.config | TRIMMED;
            long c = ctl;
            int sp = (int)c & ~INACTIVE;
            if ((sp & SMASK) == (cfg & SMASK) &&
                compareAndSetCtl(c, ((pred & SP_MASK) |
                                     (UC_MASK & (c - TC_UNIT))))) {
                w.config = cfg;  // add sentinel for deregisterWorker
                w.phase = sp;
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if any queue is detectably nonempty.  Accurate
     * only when workers are quiescent; else conservatively
     * approximate.
     * @param submissionsOnly if true, only check submission queues
     */
    private boolean hasTasks(boolean submissionsOnly) {
        int step = submissionsOnly ? 2 : 1;
        for (int checkSum = 0;;) { // repeat until stable (normally twice)
            U.loadFence();
            WorkQueue[] qs = queues;
            int n = (qs == null) ? 0 : qs.length, sum = 0;
            for (int i = 0; i < n; i += step) {
                WorkQueue q; int s;
                if ((q = qs[i]) != null) {
                    if (q.access > 0 || (s = q.top) != q.base)
                        return true;
                    sum += (s << 16) + i + 1;
                }
            }
            if (checkSum == (checkSum = sum))
                return false;
        }
    }

    /**
     * Top-level runloop for workers, called by ForkJoinWorkerThread.run.
     * See above for explanation.
     *
     * @param w caller's WorkQueue (may be null on failed initialization)
     */
    final void runWorker(WorkQueue w) {
        if (w != null) {                        // skip on failed init
            int r = w.stackPred, src = 0;       // use seed from registerWorker
            do {
                r ^= r << 13; r ^= r >>> 17; r ^= r << 5; // xorshift
            } while ((src = scan(w, src, r)) >= 0 ||
                     (src = awaitWork(w)) == 0);
            w.access = STOP;                    // record normal termination
        }
    }

    /**
     * Scans for and if found executes top-level tasks: Tries to poll
     * each queue starting at a random index with random stride,
     * returning source id or retry indicator.
     *
     * @param w caller's WorkQueue
     * @param prevSrc the previous queue stolen from in current phase, or 0
     * @param r random seed
     * @return id of queue if taken, negative if none found, prevSrc for retry
     */
    private int scan(WorkQueue w, int prevSrc, int r) {
        WorkQueue[] qs = queues;
        int n = (w == null || qs == null) ? 0 : qs.length;
        for (int step = (r >>> 16) | 1, i = n; i > 0; --i, r += step) {
            int j, cap; WorkQueue q; ForkJoinTask<?>[] a;
            if ((q = qs[j = r & (n - 1)]) != null &&
                (a = q.array) != null && (cap = a.length) > 0) {
                int src = j | SRC, b = q.base;
                int k = (cap - 1) & b, nb = b + 1, nk = (cap - 1) & nb;
                ForkJoinTask<?> t = a[k];
                U.loadFence();                  // for re-reads
                if (q.base != b)                // inconsistent
                    return prevSrc;
                else if (t != null && WorkQueue.casSlotToNull(a, k, t)) {
                    q.base = nb;
                    w.source = src;
                    if (prevSrc == 0 && q.base == nb && a[nk] != null)
                        signalWork();           // propagate
                    w.topLevelExec(t, q);
                    return src;
                }
                else if (q.array != a || a[k] != null || a[nk] != null)
                    return prevSrc;             // revisit
            }
        }
        return -1;
    }

    /**
     * Advances phase, enqueues, and awaits signal or termination.
     *
     * @return negative if terminated, else 0
     */
    private int awaitWork(WorkQueue w) {
        if (w == null)
            return -1;                           // currently impossible
        int p = (w.phase + SS_SEQ) & ~INACTIVE;  // advance phase
        boolean idle = false;                    // true if possibly quiescent
        if (runState < 0)
            return -1;                           // terminating
        long sp = p & SP_MASK, pc = ctl, qc;
        w.phase = p | INACTIVE;
        do {                                     // enqueue
            w.stackPred = (int)pc;               // set ctl stack link
        } while (pc != (pc = compareAndExchangeCtl(
                            pc, qc = ((pc - RC_UNIT) & UC_MASK) | sp)));
        if ((qc & RC_MASK) <= 0L) {
            if (hasTasks(true) && (w.phase >= 0 || reactivate() == w))
                return 0;                        // check for stragglers
            if (runState != 0 && tryTerminate(false, false))
                return -1;                       // quiescent termination
            idle = true;
        }
        WorkQueue[] qs = queues; // spin for expected #accesses in scan+signal
        int spins = ((qs == null) ? 0 : ((qs.length & SMASK) << 1)) | 0xf;
        while ((p = w.phase) < 0 && --spins > 0)
            Thread.onSpinWait();
        if (p < 0) {
            long deadline = idle ? keepAlive + System.currentTimeMillis() : 0L;
            LockSupport.setCurrentBlocker(this);
            for (;;) {                           // await signal or termination
                if (runState < 0)
                    return -1;
                w.access = PARKED;               // enable unpark
                if (w.phase < 0) {
                    if (idle)
                        LockSupport.parkUntil(deadline);
                    else
                        LockSupport.park();
                }
                w.access = 0;                    // disable unpark
                if (w.phase >= 0) {
                    LockSupport.setCurrentBlocker(null);
                    break;
                }
                Thread.interrupted();            // clear status for next park
                if (idle) {                      // check for idle timeout
                    if (deadline - System.currentTimeMillis() < TIMEOUT_SLOP) {
                        if (tryTrim(w))
                            return -1;
                        else                     // not at head; restart timer
                            deadline += keepAlive;
                    }
                }
            }
        }
        return 0;
    }

    /**
     * Non-overridable version of isQuiescent. Returns true if
     * quiescent or already terminating.
     */
    private boolean canStop() {
        long c = ctl;
        do {
            if (runState < 0)
                break;
            if ((c & RC_MASK) > 0L || hasTasks(false))
                return false;
        } while (c != (c = ctl));  // validate
        return true;
    }

    /**
     * Scans for and returns a polled task, if available.  Used only
     * for untracked polls. Begins scan at a random index to avoid
     * systematic unfairness.
     *
     * @param submissionsOnly if true, only scan submission queues
     */
    private ForkJoinTask<?> pollScan(boolean submissionsOnly) {
        int r = ThreadLocalRandom.nextSecondarySeed();
        if (submissionsOnly)                    // even indices only
            r &= ~1;
        int step = (submissionsOnly) ? 2 : 1;
        WorkQueue[] qs; int n; WorkQueue q; ForkJoinTask<?> t;
        if (runState >= 0 && (qs = queues) != null && (n = qs.length) > 0) {
            for (int i = n; i > 0; i -= step, r += step) {
                if ((q = qs[r & (n - 1)]) != null &&
                    (t = q.poll(this)) != null)
                    return t;
            }
        }
        return null;
    }

    /**
     * Tries to decrement counts (sometimes implicitly) and possibly
     * arrange for a compensating worker in preparation for
     * blocking. May fail due to interference, in which case -1 is
     * returned so caller may retry. A zero return value indicates
     * that the caller doesn't need to re-adjust counts when later
     * unblocked.
     *
     * @param c incoming ctl value
     * @param canSaturate to override saturate predicate
     * @return UNCOMPENSATE: block then adjust, 0: block, -1 : retry
     */
    private int tryCompensate(long c, boolean canSaturate) {
        Predicate<? super ForkJoinPool> sat;
        long b = bounds;                               // unpack fields
        int pc = parallelism;
        int minActive = (short)(b & SMASK),
            maxTotal  = (short)(b >>> SWIDTH) + pc,
            active    = (short)(c >>> RC_SHIFT),
            total     = (short)(c >>> TC_SHIFT),
            sp        = (int)c & ~INACTIVE;
        if (sp != 0 && active <= pc) {                 // activate idle worker
            WorkQueue[] qs; WorkQueue v; int i;
            if (ctl == c && (qs = queues) != null &&
                qs.length > (i = sp & SMASK) && (v = qs[i]) != null) {
                long nc = (v.stackPred & SP_MASK) | (UC_MASK & c);
                if (compareAndSetCtl(c, nc)) {
                    v.phase = sp;
                    LockSupport.unpark(v.owner);
                    return UNCOMPENSATE;
                }
            }
            return -1;                                  // retry
        }
        else if (active > minActive && total >= pc) {   // reduce active workers
            long nc = ((RC_MASK & (c - RC_UNIT)) | (~RC_MASK & c));
            return compareAndSetCtl(c, nc) ? UNCOMPENSATE : -1;
        }
        else if (total < maxTotal && total < MAX_CAP) { // expand pool
            long nc = ((c + TC_UNIT) & TC_MASK) | (c & ~TC_MASK);
            return (!compareAndSetCtl(c, nc) ? -1 :
                    !createWorker() ? 0 : UNCOMPENSATE);
        }
        else if (!compareAndSetCtl(c, c))               // validate
            return -1;
        else if (canSaturate || ((sat = saturate) != null && sat.test(this)))
            return 0;
        else
            throw new RejectedExecutionException(
                "Thread limit exceeded replacing blocked worker");
    }

    /**
     * Readjusts RC count; called from ForkJoinTask after blocking.
     */
    final void uncompensate() {
        getAndAddCtl(RC_UNIT);
    }

    /**
     * Helps if possible until the given task is done.  Processes
     * compatible local tasks and scans other queues for task produced
     * by w's stealers; returning compensated blocking sentinel if
     * none are found.
     *
     * @param task the task
     * @param w caller's WorkQueue
     * @param timed true if this is a timed join
     * @return task status on exit, or UNCOMPENSATE for compensated blocking
     */
    final int helpJoin(ForkJoinTask<?> task, WorkQueue w, boolean timed) {
        if (w == null || task == null)
            return 0;
        int wsrc = w.source, wid = (w.config & SMASK) | SRC, r = wid + 2;
        long sctl = 0L;                               // track stability
        for (boolean rescan = true;;) {
            int s; WorkQueue[] qs;
            if ((s = task.status) < 0)
                return s;
            if (!rescan && sctl == (sctl = ctl)) {
                if (runState < 0)
                    return 0;
                if ((s = tryCompensate(sctl, timed)) >= 0)
                    return s;                              // block
            }
            rescan = false;
            int n = ((qs = queues) == null) ? 0 : qs.length, m = n - 1;
            scan: for (int i = n >>> 1; i > 0; --i, r += 2) {
                int j, cap; WorkQueue q; ForkJoinTask<?>[] a;
                if ((q = qs[j = r & m]) != null && (a = q.array) != null &&
                    (cap = a.length) > 0) {
                    for (int src = j | SRC;;) {
                        int sq = q.source, b = q.base;
                        int k = (cap - 1) & b, nb = b + 1;
                        ForkJoinTask<?> t = a[k];
                        U.loadFence();                // for re-reads
                        boolean eligible = true;      // check steal chain
                        for (int d = n, v = sq;;) {   // may be cyclic; bound
                            WorkQueue p;
                            if (v == wid)
                                break;
                            if (v == 0 || --d == 0 || (p = qs[v & m]) == null) {
                                eligible = false;
                                break;
                            }
                            v = p.source;
                        }
                        if (q.source != sq || q.base != b)
                            ;                          // stale
                        else if ((s = task.status) < 0)
                            return s;                  // recheck before taking
                        else if (t == null) {
                            if (a[k] == null) {
                                if (!rescan && eligible &&
                                    (q.array != a || q.top != b))
                                    rescan = true;     // resized or stalled
                                break;
                            }
                        }
                        else if (t != task && !eligible)
                            break;
                        else if (WorkQueue.casSlotToNull(a, k, t)) {
                            q.base = nb;
                            w.source = src;
                            t.doExec();
                            w.source = wsrc;
                            rescan = true;
                            break scan;
                        }
                    }
                }
            }
        }
    }

    /**
     * Version of helpJoin for CountedCompleters.
     *
     * @param task the task
     * @param w caller's WorkQueue
     * @param owned true if w is owned by a ForkJoinWorkerThread
     * @param timed true if this is a timed join
     * @return task status on exit, or UNCOMPENSATE for compensated blocking
     */
    final int helpComplete(ForkJoinTask<?> task, WorkQueue w, boolean owned,
                           boolean timed) {
        if (w == null || task == null)
            return 0;
        int wsrc = w.source, r = w.config;
        long sctl = 0L;                               // track stability
        for (boolean rescan = true;;) {
            int s; WorkQueue[] qs;
            if ((s = w.helpComplete(task, owned, 0)) < 0)
                return s;
            if (!rescan && sctl == (sctl = ctl)) {
                if (!owned || runState < 0)
                    return 0;
                if ((s = tryCompensate(sctl, timed)) >= 0)
                    return s;
            }
            rescan = false;
            int n = ((qs = queues) == null) ? 0 : qs.length, m = n - 1;
            scan: for (int i = n; i > 0; --i, ++r) {
                int j, cap; WorkQueue q; ForkJoinTask<?>[] a;
                if ((q = qs[j = r & m]) != null && (a = q.array) != null &&
                    (cap = a.length) > 0) {
                    poll: for (int src = j | SRC, b = q.base;;) {
                        int k = (cap - 1) & b, nb = b + 1;
                        ForkJoinTask<?> t = a[k];
                        U.loadFence();                // for re-reads
                        if (b != (b = q.base))
                            ;                         // stale
                        else if ((s = task.status) < 0)
                            return s;                 // recheck before taking
                        else if (t == null) {
                            if (a[k] == null) {
                                if (!rescan &&        // resized or stalled
                                    (q.array != a || q.top != b))
                                    rescan = true;
                                break;
                            }
                        }
                        else if (t instanceof CountedCompleter) {
                            CountedCompleter<?> f;
                            for (f = (CountedCompleter<?>)t;;) {
                                if (f == task)
                                    break;
                                else if ((f = f.completer) == null)
                                    break poll;       // ineligible
                            }
                            if (WorkQueue.casSlotToNull(a, k, t)) {
                                q.base = nb;
                                w.source = src;
                                t.doExec();
                                w.source = wsrc;
                                rescan = true;
                                break scan;
                            }
                        }
                        else
                            break;
                    }
                }
            }
        }
     }

    /**
     * Runs tasks until {@code isQuiescent()}. Rather than blocking
     * when tasks cannot be found, rescans until all others cannot
     * find tasks either.
     *
     * @param nanos max wait time (Long.MAX_VALUE if effectively untimed)
     * @param interruptible true if return on interrupt
     * @return positive if quiescent, negative if interrupted, else 0
     */
    private int helpQuiesce(WorkQueue w, long nanos, boolean interruptible) {
        long startTime = System.nanoTime(), parkTime = 0L;
        int phase; // w.phase set negative when temporarily quiescent
        if (w == null || (phase = w.phase) < 0)
            return 0;
        int activePhase = phase, inactivePhase = phase | INACTIVE;
        int wsrc = w.source, r = 0;
        for (boolean locals = true;;) {
            WorkQueue[] qs; WorkQueue q;
            if (runState < 0) {             // terminating
                w.phase = activePhase;
                return 1;
            }
            if (locals) {                   // run local tasks before (re)polling
                for (ForkJoinTask<?> u; (u = w.nextLocalTask()) != null;)
                    u.doExec();
            }
            boolean rescan = false, busy = locals = false, interrupted;
            int n = ((qs = queues) == null) ? 0 : qs.length, m = n - 1;
            scan: for (int i = n, j; i > 0; --i, ++r) {
                if ((q = qs[j = m & r]) != null && q != w) {
                    for (int src = j | SRC;;) {
                        ForkJoinTask<?>[] a = q.array;
                        int b = q.base, cap;
                        if (a == null || (cap = a.length) <= 0)
                            break;
                        int k = (cap - 1) & b, nb = b + 1, nk = (cap - 1) & nb;
                        ForkJoinTask<?> t = a[k];
                        U.loadFence();      // for re-reads
                        if (q.base != b || q.array != a || a[k] != t)
                            ;
                        else if (t == null) {
                            if (!rescan) {
                                if (a[nk] != null || q.top - b > 0)
                                    rescan = true;
                                else if (!busy &&
                                         q.owner != null && q.phase >= 0)
                                    busy = true;
                            }
                            break;
                        }
                        else if (phase < 0) // reactivate before taking
                            w.phase = phase = activePhase;
                        else if (WorkQueue.casSlotToNull(a, k, t)) {
                            q.base = nb;
                            w.source = src;
                            t.doExec();
                            w.source = wsrc;
                            rescan = locals = true;
                            break scan;
                        }
                    }
                }
            }
            if (rescan)
                ;                   // retry
            else if (phase >= 0) {
                parkTime = 0L;
                w.phase = phase = inactivePhase;
            }
            else if (!busy) {
                w.phase = activePhase;
                return 1;
            }
            else if (parkTime == 0L) {
                parkTime = 1L << 10; // initially about 1 usec
                Thread.yield();
            }
            else if ((interrupted = interruptible && Thread.interrupted()) ||
                     System.nanoTime() - startTime > nanos) {
                w.phase = activePhase;
                return interrupted ? -1 : 0;
            }
            else {
                LockSupport.parkNanos(this, parkTime);
                if (parkTime < nanos >>> 8 && parkTime < 1L << 20)
                    parkTime <<= 1;  // max sleep approx 1 sec or 1% nanos
            }
        }
    }

    /**
     * Helps quiesce from external caller until done, interrupted, or timeout
     *
     * @param nanos max wait time (Long.MAX_VALUE if effectively untimed)
     * @param interruptible true if return on interrupt
     * @return positive if quiescent, negative if interrupted, else 0
     */
    private int externalHelpQuiesce(long nanos, boolean interruptible) {
        for (long startTime = System.nanoTime(), parkTime = 0L;;) {
            ForkJoinTask<?> t;
            if ((t = pollScan(false)) != null) {
                t.doExec();
                parkTime = 0L;
            }
            else if (canStop())
                return 1;
            else if (parkTime == 0L) {
                parkTime = 1L << 10;
                Thread.yield();
            }
            else if ((System.nanoTime() - startTime) > nanos)
                return 0;
            else if (interruptible && Thread.interrupted())
                return -1;
            else {
                LockSupport.parkNanos(this, parkTime);
                if (parkTime < nanos >>> 8 && parkTime < 1L << 20)
                    parkTime <<= 1;
            }
        }
    }

    /**
     * Helps quiesce from either internal or external caller
     *
     * @param pool the pool to use, or null if any
     * @param nanos max wait time (Long.MAX_VALUE if effectively untimed)
     * @param interruptible true if return on interrupt
     * @return positive if quiescent, negative if interrupted, else 0
     */
    static final int helpQuiescePool(ForkJoinPool pool, long nanos,
                                     boolean interruptible) {
        Thread t; ForkJoinPool p; ForkJoinWorkerThread wt;
        if ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread &&
            (p = (wt = (ForkJoinWorkerThread)t).pool) != null &&
            (p == pool || pool == null))
            return p.helpQuiesce(wt.workQueue, nanos, interruptible);
        else if ((p = pool) != null || (p = common) != null)
            return p.externalHelpQuiesce(nanos, interruptible);
        else
            return 0;
    }

    /**
     * Gets and removes a local or stolen task for the given worker.
     *
     * @return a task, if available
     */
    final ForkJoinTask<?> nextTaskFor(WorkQueue w) {
        ForkJoinTask<?> t;
        if (w == null || (t = w.nextLocalTask()) == null)
            t = pollScan(false);
        return t;
    }

    // External operations

    /**
     * Finds and locks a WorkQueue for an external submitter, or
     * throws RejectedExecutionException if shutdown or terminating.
     * @param isSubmit false if this is for a common pool fork
     */
    final WorkQueue submissionQueue(boolean isSubmit) {
        int r;
        ReentrantLock lock = registrationLock;
        if ((r = ThreadLocalRandom.getProbe()) == 0) {
            ThreadLocalRandom.localInit();           // initialize caller's probe
            r = ThreadLocalRandom.getProbe();
        }
        if (lock != null) {                          // else init error
            for (int id = r << 1;;) {                // even indices only
                int n, i; WorkQueue[] qs; WorkQueue q;
                if ((qs = queues) == null || (n = qs.length) <= 0)
                    break;
                else if ((q = qs[i = (n - 1) & id]) == null) {
                    WorkQueue w = new WorkQueue(null, id | SRC);
                    w.array = new ForkJoinTask<?>[INITIAL_QUEUE_CAPACITY];
                    lock.lock();                     // install under lock
                    if (queues == qs && qs[i] == null)
                        qs[i] = w;                   // else lost race; discard
                    lock.unlock();
                }
                else if (q.getAndSetAccess(1) != 0)  // move and restart
                    id = (r = ThreadLocalRandom.advanceProbe(r)) << 1;
                else if (isSubmit && runState != 0) {
                    q.access = 0;                    // check while lock held
                    break;
                }
                else
                    return q;
            }
        }
        throw new RejectedExecutionException();
    }

    /**
     * Pushes a submission to the pool, using internal queue if called
     * from ForkJoinWorkerThread, else external queue.
     */
    private <T> ForkJoinTask<T> poolSubmit(boolean signalIfEmpty,
                                           ForkJoinTask<T> task) {
        WorkQueue q; Thread t; ForkJoinWorkerThread wt;
        U.storeStoreFence();  // ensure safely publishable
        if (task == null) throw new NullPointerException();
        if (((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) &&
            (wt = (ForkJoinWorkerThread)t).pool == this)
            q = wt.workQueue;
        else {
            task.markPoolSubmission();
            q = submissionQueue(true);
        }
        q.push(task, this, signalIfEmpty);
        return task;
    }

    /**
     * Returns queue for an external thread, if one exists that has
     * possibly ever submitted to the given pool (nonzero probe), or
     * null if none.
     */
    private static WorkQueue externalQueue(ForkJoinPool p) {
        WorkQueue[] qs;
        int r = ThreadLocalRandom.getProbe(), n;
        return (p != null && (qs = p.queues) != null &&
                (n = qs.length) > 0 && r != 0) ?
            qs[(n - 1) & (r << 1)] : null;
    }

    /**
     * Returns external queue for common pool.
     */
    static WorkQueue commonQueue() {
        return externalQueue(common);
    }

    /**
     * Returns queue for an external thread, if one exists
     */
    final WorkQueue externalQueue() {
        return externalQueue(this);
    }

    /**
     * If the given executor is a ForkJoinPool, poll and execute
     * AsynchronousCompletionTasks from worker's queue until none are
     * available or blocker is released.
     */
    static void helpAsyncBlocker(Executor e, ManagedBlocker blocker) {
        WorkQueue w = null; Thread t; ForkJoinWorkerThread wt;
        if ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) {
            if ((wt = (ForkJoinWorkerThread)t).pool == e)
                w = wt.workQueue;
        }
        else if (e instanceof ForkJoinPool)
            w = ((ForkJoinPool)e).externalQueue();
        if (w != null)
            w.helpAsyncBlocker(blocker);
    }

    /**
     * Returns a cheap heuristic guide for task partitioning when
     * programmers, frameworks, tools, or languages have little or no
     * idea about task granularity.  In essence, by offering this
     * method, we ask users only about tradeoffs in overhead vs
     * expected throughput and its variance, rather than how finely to
     * partition tasks.
     *
     * In a steady state strict (tree-structured) computation, each
     * thread makes available for stealing enough tasks for other
     * threads to remain active. Inductively, if all threads play by
     * the same rules, each thread should make available only a
     * constant number of tasks.
     *
     * The minimum useful constant is just 1. But using a value of 1
     * would require immediate replenishment upon each steal to
     * maintain enough tasks, which is infeasible.  Further,
     * partitionings/granularities of offered tasks should minimize
     * steal rates, which in general means that threads nearer the top
     * of computation tree should generate more than those nearer the
     * bottom. In perfect steady state, each thread is at
     * approximately the same level of computation tree. However,
     * producing extra tasks amortizes the uncertainty of progress and
     * diffusion assumptions.
     *
     * So, users will want to use values larger (but not much larger)
     * than 1 to both smooth over transient shortages and hedge
     * against uneven progress; as traded off against the cost of
     * extra task overhead. We leave the user to pick a threshold
     * value to compare with the results of this call to guide
     * decisions, but recommend values such as 3.
     *
     * When all threads are active, it is on average OK to estimate
     * surplus strictly locally. In steady-state, if one thread is
     * maintaining say 2 surplus tasks, then so are others. So we can
     * just use estimated queue length.  However, this strategy alone
     * leads to serious mis-estimates in some non-steady-state
     * conditions (ramp-up, ramp-down, other stalls). We can detect
     * many of these by further considering the number of "idle"
     * threads, that are known to have zero queued tasks, so
     * compensate by a factor of (#idle/#active) threads.
     */
    static int getSurplusQueuedTaskCount() {
        Thread t; ForkJoinWorkerThread wt; ForkJoinPool pool; WorkQueue q;
        if (((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) &&
            (pool = (wt = (ForkJoinWorkerThread)t).pool) != null &&
            (q = wt.workQueue) != null) {
            int n = q.top - q.base;
            int p = pool.parallelism;
            int a = (short)(pool.ctl >>> RC_SHIFT);
            return n - (a > (p >>>= 1) ? 0 :
                        a > (p >>>= 1) ? 1 :
                        a > (p >>>= 1) ? 2 :
                        a > (p >>>= 1) ? 4 :
                        8);
        }
        return 0;
    }

    // Termination

    /**
     * Possibly initiates and/or completes pool termination.
     *
     * @param now if true, unconditionally terminate, else only
     * if no work and no active workers
     * @param enable if true, terminate when next possible
     * @return true if terminating or terminated
     */
    private boolean tryTerminate(boolean now, boolean enable) {
        int rs; ReentrantLock lock; Condition cond;
        if ((rs = runState) >= 0) {                 // set SHUTDOWN and/or STOP
            if ((config & ISCOMMON) != 0)
                return false;                       // cannot shutdown
            if (!now) {
                if ((rs & SHUTDOWN) == 0) {
                    if (!enable)
                        return false;
                    getAndBitwiseOrRunState(SHUTDOWN);
                }
                if (!canStop())
                    return false;
            }
            getAndBitwiseOrRunState(SHUTDOWN | STOP);
        }
        WorkQueue released = reactivate();          // try signalling waiter
        int tc = (short)(ctl >>> TC_SHIFT);
        if (released == null && tc > 0) {           // help unblock and cancel
            Thread current = Thread.currentThread();
            WorkQueue w = ((current instanceof ForkJoinWorkerThread) ?
                           ((ForkJoinWorkerThread)current).workQueue : null);
            int r = (w == null) ? 0 : w.config + 1; // stagger traversals
            WorkQueue[] qs = queues;
            int n = (qs == null) ? 0 : qs.length;
            for (int i = 0; i < n; ++i) {
                WorkQueue q; Thread thread;
                if ((q = qs[(r + i) & (n - 1)]) != null &&
                    (thread = q.owner) != current && q.access != STOP) {
                    for (ForkJoinTask<?> t; (t = q.poll(null)) != null; )
                        ForkJoinTask.cancelIgnoringExceptions(t);
                    if (thread != null && !thread.isInterrupted()) {
                        q.forcePhaseActive();      // for awaitWork
                        try {
                            thread.interrupt();
                        } catch (Throwable ignore) {
                        }
                    }
                }
            }
        }
        if ((tc <= 0 || (short)(ctl >>> TC_SHIFT) <= 0) &&
            (getAndBitwiseOrRunState(TERMINATED) & TERMINATED) == 0 &&
            (lock = registrationLock) != null) {
            lock.lock();                            // signal when no workers
            if ((cond = termination) != null)
                cond.signalAll();
            lock.unlock();
            container.close();
        }
        return true;
    }

    // Exported methods

    // Constructors

    /**
     * Creates a {@code ForkJoinPool} with parallelism equal to {@link
     * java.lang.Runtime#availableProcessors}, using defaults for all
     * other parameters (see {@link #ForkJoinPool(int,
     * ForkJoinWorkerThreadFactory, UncaughtExceptionHandler, boolean,
     * int, int, int, Predicate, long, TimeUnit)}).
     *
     * @throws SecurityException if a security manager exists and
     *         the caller is not permitted to modify threads
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}{@code ("modifyThread")}
     */
    public ForkJoinPool() {
        this(Math.min(MAX_CAP, Runtime.getRuntime().availableProcessors()),
             defaultForkJoinWorkerThreadFactory, null, false,
             0, MAX_CAP, 1, null, DEFAULT_KEEPALIVE, TimeUnit.MILLISECONDS);
    }

    /**
     * Creates a {@code ForkJoinPool} with the indicated parallelism
     * level, using defaults for all other parameters (see {@link
     * #ForkJoinPool(int, ForkJoinWorkerThreadFactory,
     * UncaughtExceptionHandler, boolean, int, int, int, Predicate,
     * long, TimeUnit)}).
     *
     * @param parallelism the parallelism level
     * @throws IllegalArgumentException if parallelism less than or
     *         equal to zero, or greater than implementation limit
     * @throws SecurityException if a security manager exists and
     *         the caller is not permitted to modify threads
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}{@code ("modifyThread")}
     */
    public ForkJoinPool(int parallelism) {
        this(parallelism, defaultForkJoinWorkerThreadFactory, null, false,
             0, MAX_CAP, 1, null, DEFAULT_KEEPALIVE, TimeUnit.MILLISECONDS);
    }

    /**
     * Creates a {@code ForkJoinPool} with the given parameters (using
     * defaults for others -- see {@link #ForkJoinPool(int,
     * ForkJoinWorkerThreadFactory, UncaughtExceptionHandler, boolean,
     * int, int, int, Predicate, long, TimeUnit)}).
     *
     * @param parallelism the parallelism level. For default value,
     * use {@link java.lang.Runtime#availableProcessors}.
     * @param factory the factory for creating new threads. For default value,
     * use {@link #defaultForkJoinWorkerThreadFactory}.
     * @param handler the handler for internal worker threads that
     * terminate due to unrecoverable errors encountered while executing
     * tasks. For default value, use {@code null}.
     * @param asyncMode if true,
     * establishes local first-in-first-out scheduling mode for forked
     * tasks that are never joined. This mode may be more appropriate
     * than default locally stack-based mode in applications in which
     * worker threads only process event-style asynchronous tasks.
     * For default value, use {@code false}.
     * @throws IllegalArgumentException if parallelism less than or
     *         equal to zero, or greater than implementation limit
     * @throws NullPointerException if the factory is null
     * @throws SecurityException if a security manager exists and
     *         the caller is not permitted to modify threads
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}{@code ("modifyThread")}
     */
    public ForkJoinPool(int parallelism,
                        ForkJoinWorkerThreadFactory factory,
                        UncaughtExceptionHandler handler,
                        boolean asyncMode) {
        this(parallelism, factory, handler, asyncMode,
             0, MAX_CAP, 1, null, DEFAULT_KEEPALIVE, TimeUnit.MILLISECONDS);
    }

    /**
     * Creates a {@code ForkJoinPool} with the given parameters.
     *
     * @param parallelism the parallelism level. For default value,
     * use {@link java.lang.Runtime#availableProcessors}.
     *
     * @param factory the factory for creating new threads. For
     * default value, use {@link #defaultForkJoinWorkerThreadFactory}.
     *
     * @param handler the handler for internal worker threads that
     * terminate due to unrecoverable errors encountered while
     * executing tasks. For default value, use {@code null}.
     *
     * @param asyncMode if true, establishes local first-in-first-out
     * scheduling mode for forked tasks that are never joined. This
     * mode may be more appropriate than default locally stack-based
     * mode in applications in which worker threads only process
     * event-style asynchronous tasks.  For default value, use {@code
     * false}.
     *
     * @param corePoolSize the number of threads to keep in the pool
     * (unless timed out after an elapsed keep-alive). Normally (and
     * by default) this is the same value as the parallelism level,
     * but may be set to a larger value to reduce dynamic overhead if
     * tasks regularly block. Using a smaller value (for example
     * {@code 0}) has the same effect as the default.
     *
     * @param maximumPoolSize the maximum number of threads allowed.
     * When the maximum is reached, attempts to replace blocked
     * threads fail.  (However, because creation and termination of
     * different threads may overlap, and may be managed by the given
     * thread factory, this value may be transiently exceeded.)  To
     * arrange the same value as is used by default for the common
     * pool, use {@code 256} plus the {@code parallelism} level. (By
     * default, the common pool allows a maximum of 256 spare
     * threads.)  Using a value (for example {@code
     * Integer.MAX_VALUE}) larger than the implementation's total
     * thread limit has the same effect as using this limit (which is
     * the default).
     *
     * @param minimumRunnable the minimum allowed number of core
     * threads not blocked by a join or {@link ManagedBlocker}.  To
     * ensure progress, when too few unblocked threads exist and
     * unexecuted tasks may exist, new threads are constructed, up to
     * the given maximumPoolSize.  For the default value, use {@code
     * 1}, that ensures liveness.  A larger value might improve
     * throughput in the presence of blocked activities, but might
     * not, due to increased overhead.  A value of zero may be
     * acceptable when submitted tasks cannot have dependencies
     * requiring additional threads.
     *
     * @param saturate if non-null, a predicate invoked upon attempts
     * to create more than the maximum total allowed threads.  By
     * default, when a thread is about to block on a join or {@link
     * ManagedBlocker}, but cannot be replaced because the
     * maximumPoolSize would be exceeded, a {@link
     * RejectedExecutionException} is thrown.  But if this predicate
     * returns {@code true}, then no exception is thrown, so the pool
     * continues to operate with fewer than the target number of
     * runnable threads, which might not ensure progress.
     *
     * @param keepAliveTime the elapsed time since last use before
     * a thread is terminated (and then later replaced if needed).
     * For the default value, use {@code 60, TimeUnit.SECONDS}.
     *
     * @param unit the time unit for the {@code keepAliveTime} argument
     *
     * @throws IllegalArgumentException if parallelism is less than or
     *         equal to zero, or is greater than implementation limit,
     *         or if maximumPoolSize is less than parallelism,
     *         of if the keepAliveTime is less than or equal to zero.
     * @throws NullPointerException if the factory is null
     * @throws SecurityException if a security manager exists and
     *         the caller is not permitted to modify threads
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}{@code ("modifyThread")}
     * @since 9
     */
    public ForkJoinPool(int parallelism,
                        ForkJoinWorkerThreadFactory factory,
                        UncaughtExceptionHandler handler,
                        boolean asyncMode,
                        int corePoolSize,
                        int maximumPoolSize,
                        int minimumRunnable,
                        Predicate<? super ForkJoinPool> saturate,
                        long keepAliveTime,
                        TimeUnit unit) {
        checkPermission();
        int p = parallelism;
        if (p <= 0 || p > MAX_CAP || p > maximumPoolSize || keepAliveTime <= 0L)
            throw new IllegalArgumentException();
        if (factory == null || unit == null)
            throw new NullPointerException();
        this.parallelism = p;
        this.factory = factory;
        this.ueh = handler;
        this.saturate = saturate;
        this.config = asyncMode ? FIFO : 0;
        this.keepAlive = Math.max(unit.toMillis(keepAliveTime), TIMEOUT_SLOP);
        int corep = Math.min(Math.max(corePoolSize, p), MAX_CAP);
        int maxSpares = Math.max(0, Math.min(maximumPoolSize - p, MAX_CAP));
        int minAvail = Math.max(0, Math.min(minimumRunnable, MAX_CAP));
        this.bounds = (long)(minAvail & SMASK) | (long)(maxSpares << SWIDTH) |
            ((long)corep << 32);
        int size = 1 << (33 - Integer.numberOfLeadingZeros(p - 1));
        this.registrationLock = new ReentrantLock();
        this.queues = new WorkQueue[size];
        String pid = Integer.toString(getAndAddPoolIds(1) + 1);
        String name = "ForkJoinPool-" + pid;
        this.workerNamePrefix = name + "-worker-";
        this.container = SharedThreadContainer.create(name);
    }

    /**
     * Constructor for common pool using parameters possibly
     * overridden by system properties
     */
    private ForkJoinPool(byte forCommonPoolOnly) {
        ForkJoinWorkerThreadFactory fac = defaultForkJoinWorkerThreadFactory;
        UncaughtExceptionHandler handler = null;
        int maxSpares = DEFAULT_COMMON_MAX_SPARES;
        int pc = 0, preset = 0; // nonzero if size set as property
        try {  // ignore exceptions in accessing/parsing properties
            String pp = System.getProperty
                ("java.util.concurrent.ForkJoinPool.common.parallelism");
            if (pp != null) {
                pc = Math.max(0, Integer.parseInt(pp));
                preset = PRESET_SIZE;
            }
            String ms = System.getProperty
                ("java.util.concurrent.ForkJoinPool.common.maximumSpares");
            if (ms != null)
                maxSpares = Math.max(0, Math.min(MAX_CAP, Integer.parseInt(ms)));
            String sf = System.getProperty
                ("java.util.concurrent.ForkJoinPool.common.threadFactory");
            String sh = System.getProperty
                ("java.util.concurrent.ForkJoinPool.common.exceptionHandler");
            if (sf != null || sh != null) {
                ClassLoader ldr = ClassLoader.getSystemClassLoader();
                if (sf != null)
                    fac = (ForkJoinWorkerThreadFactory)
                        ldr.loadClass(sf).getConstructor().newInstance();
                if (sh != null)
                    handler = (UncaughtExceptionHandler)
                        ldr.loadClass(sh).getConstructor().newInstance();
            }
        } catch (Exception ignore) {
        }
        if (preset == 0)
            pc = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        int p = Math.min(pc, MAX_CAP);
        int size = (p == 0) ? 1 : 1 << (33 - Integer.numberOfLeadingZeros(p-1));
        this.parallelism = p;
        this.config = ISCOMMON | preset;
        this.bounds = (long)(1 | (maxSpares << SWIDTH));
        this.factory = fac;
        this.ueh = handler;
        this.keepAlive = DEFAULT_KEEPALIVE;
        this.saturate = null;
        this.workerNamePrefix = null;
        this.registrationLock = new ReentrantLock();
        this.queues = new WorkQueue[size];
        this.container = SharedThreadContainer.create("ForkJoinPool.commonPool");
    }

    /**
     * Returns the common pool instance. This pool is statically
     * constructed; its run state is unaffected by attempts to {@link
     * #shutdown} or {@link #shutdownNow}. However this pool and any
     * ongoing processing are automatically terminated upon program
     * {@link System#exit}.  Any program that relies on asynchronous
     * task processing to complete before program termination should
     * invoke {@code commonPool().}{@link #awaitQuiescence awaitQuiescence},
     * before exit.
     *
     * @return the common pool instance
     * @since 1.8
     */
    public static ForkJoinPool commonPool() {
        // assert common != null : "static init error";
        return common;
    }

    // Execution methods

    /**
     * Performs the given task, returning its result upon completion.
     * If the computation encounters an unchecked Exception or Error,
     * it is rethrown as the outcome of this invocation.  Rethrown
     * exceptions behave in the same way as regular exceptions, but,
     * when possible, contain stack traces (as displayed for example
     * using {@code ex.printStackTrace()}) of both the current thread
     * as well as the thread actually encountering the exception;
     * minimally only the latter.
     *
     * @param task the task
     * @param <T> the type of the task's result
     * @return the task's result
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     */
    public <T> T invoke(ForkJoinTask<T> task) {
        poolSubmit(true, task);
        return task.join();
    }

    /**
     * Arranges for (asynchronous) execution of the given task.
     *
     * @param task the task
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     */
    public void execute(ForkJoinTask<?> task) {
        poolSubmit(true, task);
    }

    // AbstractExecutorService methods

    /**
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     */
    @Override
    @SuppressWarnings("unchecked")
    public void execute(Runnable task) {
        poolSubmit(true, (task instanceof ForkJoinTask<?>)
                   ? (ForkJoinTask<Void>) task // avoid re-wrap
                   : new ForkJoinTask.RunnableExecuteAction(task));
    }

    /**
     * Submits a ForkJoinTask for execution.
     *
     * @param task the task to submit
     * @param <T> the type of the task's result
     * @return the task
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     */
    public <T> ForkJoinTask<T> submit(ForkJoinTask<T> task) {
        return poolSubmit(true, task);
    }

    /**
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     */
    @Override
    public <T> ForkJoinTask<T> submit(Callable<T> task) {
        return poolSubmit(true, new ForkJoinTask.AdaptedCallable<T>(task));
    }

    /**
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     */
    @Override
    public <T> ForkJoinTask<T> submit(Runnable task, T result) {
        return poolSubmit(true, new ForkJoinTask.AdaptedRunnable<T>(task, result));
    }

    /**
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     */
    @Override
    @SuppressWarnings("unchecked")
    public ForkJoinTask<?> submit(Runnable task) {
        return poolSubmit(true, (task instanceof ForkJoinTask<?>)
                          ? (ForkJoinTask<Void>) task // avoid re-wrap
                          : new ForkJoinTask.AdaptedRunnableAction(task));
    }

    // Added mainly for possible use in Loom

    /**
     * Submits the given task without guaranteeing that it will
     * eventually execute in the absence of available active threads.
     * In some contexts, this method may reduce contention and
     * overhead by relying on context-specific knowledge that existing
     * threads (possibly including the calling thread if operating in
     * this pool) will eventually be available to execute the task.
     *
     * @param task the task
     * @param <T> the type of the task's result
     * @return the task
     * @since 19
     */
    public <T> ForkJoinTask<T> lazySubmit(ForkJoinTask<T> task) {
        return poolSubmit(false, task);
    }

    /**
     * Changes the target parallelism of this pool, controlling the
     * future creation, use, and termination of worker threads.
     * Applications include contexts in which the number of available
     * processors changes over time.
     *
     * @implNote This implementation restricts the maximum number of
     * running threads to 32767
     *
     * @param size the target parallelism level
     * @return the previous parallelism level.
     * @throws IllegalArgumentException if size is less than 1 or
     *         greater than the maximum supported by this pool.
     * @throws UnsupportedOperationException this is the{@link
     *         #commonPool()} and parallelism level was set by System
     *         property {@systemProperty
     *         java.util.concurrent.ForkJoinPool.common.parallelism}.
     * @throws SecurityException if a security manager exists and
     *         the caller is not permitted to modify threads
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}{@code ("modifyThread")}
     * @since 19
     */
    public int setParallelism(int size) {
        if (size < 1 || size > MAX_CAP)
            throw new IllegalArgumentException();
        if ((config & PRESET_SIZE) != 0)
            throw new UnsupportedOperationException("Cannot override System property");
        checkPermission();
        return getAndSetParallelism(size);
    }

    /**
     * @throws NullPointerException       {@inheritDoc}
     * @throws RejectedExecutionException {@inheritDoc}
     */
    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) {
        ArrayList<Future<T>> futures = new ArrayList<>(tasks.size());
        try {
            for (Callable<T> t : tasks) {
                ForkJoinTask<T> f =
                    new ForkJoinTask.AdaptedInterruptibleCallable<T>(t);
                futures.add(f);
                poolSubmit(true, f);
            }
            for (int i = futures.size() - 1; i >= 0; --i)
                ((ForkJoinTask<?>)futures.get(i)).quietlyJoin();
            return futures;
        } catch (Throwable t) {
            for (Future<T> e : futures)
                ForkJoinTask.cancelIgnoringExceptions(e);
            throw t;
        }
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks,
                                         long timeout, TimeUnit unit)
        throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        ArrayList<Future<T>> futures = new ArrayList<>(tasks.size());
        try {
            for (Callable<T> t : tasks) {
                ForkJoinTask<T> f =
                    new ForkJoinTask.AdaptedInterruptibleCallable<T>(t);
                futures.add(f);
                poolSubmit(true, f);
            }
            long startTime = System.nanoTime(), ns = nanos;
            boolean timedOut = (ns < 0L);
            for (int i = futures.size() - 1; i >= 0; --i) {
                ForkJoinTask<T> f = (ForkJoinTask<T>)futures.get(i);
                if (!f.isDone()) {
                    if (!timedOut)
                        timedOut = !f.quietlyJoin(ns, TimeUnit.NANOSECONDS);
                    if (timedOut)
                        ForkJoinTask.cancelIgnoringExceptions(f);
                    else
                        ns = nanos - (System.nanoTime() - startTime);
                }
            }
            return futures;
        } catch (Throwable t) {
            for (Future<T> e : futures)
                ForkJoinTask.cancelIgnoringExceptions(e);
            throw t;
        }
    }

    // Task to hold results from InvokeAnyTasks
    static final class InvokeAnyRoot<E> extends ForkJoinTask<E> {
        private static final long serialVersionUID = 2838392045355241008L;
        @SuppressWarnings("serial") // Conditionally serializable
        volatile E result;
        final AtomicInteger count;  // in case all throw
        @SuppressWarnings("serial")
        final ForkJoinPool pool;    // to check shutdown while collecting
        InvokeAnyRoot(int n, ForkJoinPool p) {
            pool = p;
            count = new AtomicInteger(n);
        }
        final void tryComplete(Callable<E> c) { // called by InvokeAnyTasks
            Throwable ex = null;
            boolean failed;
            if (c == null || Thread.interrupted() ||
                (pool != null && pool.runState < 0))
                failed = true;
            else if (isDone())
                failed = false;
            else {
                try {
                    complete(c.call());
                    failed = false;
                } catch (Throwable tx) {
                    ex = tx;
                    failed = true;
                }
            }
            if ((pool != null && pool.runState < 0) ||
                (failed && count.getAndDecrement() <= 1))
                trySetThrown(ex != null ? ex : new CancellationException());
        }
        public final boolean exec()         { return false; } // never forked
        public final E getRawResult()       { return result; }
        public final void setRawResult(E v) { result = v; }
    }

    // Variant of AdaptedInterruptibleCallable with results in InvokeAnyRoot
    static final class InvokeAnyTask<E> extends ForkJoinTask<E> {
        private static final long serialVersionUID = 2838392045355241008L;
        final InvokeAnyRoot<E> root;
        @SuppressWarnings("serial") // Conditionally serializable
        final Callable<E> callable;
        transient volatile Thread runner;
        InvokeAnyTask(InvokeAnyRoot<E> root, Callable<E> callable) {
            this.root = root;
            this.callable = callable;
        }
        public final boolean exec() {
            Thread.interrupted();
            runner = Thread.currentThread();
            root.tryComplete(callable);
            runner = null;
            Thread.interrupted();
            return true;
        }
        public final boolean cancel(boolean mayInterruptIfRunning) {
            Thread t;
            boolean stat = super.cancel(false);
            if (mayInterruptIfRunning && (t = runner) != null) {
                try {
                    t.interrupt();
                } catch (Throwable ignore) {
                }
            }
            return stat;
        }
        public final void setRawResult(E v) {} // unused
        public final E getRawResult()       { return null; }
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
        throws InterruptedException, ExecutionException {
        int n = tasks.size();
        if (n <= 0)
            throw new IllegalArgumentException();
        InvokeAnyRoot<T> root = new InvokeAnyRoot<T>(n, this);
        ArrayList<InvokeAnyTask<T>> fs = new ArrayList<>(n);
        try {
            for (Callable<T> c : tasks) {
                if (c == null)
                    throw new NullPointerException();
                InvokeAnyTask<T> f = new InvokeAnyTask<T>(root, c);
                fs.add(f);
                poolSubmit(true, f);
                if (root.isDone())
                    break;
            }
            return root.get();
        } finally {
            for (InvokeAnyTask<T> f : fs)
                ForkJoinTask.cancelIgnoringExceptions(f);
        }
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks,
                           long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
        long nanos = unit.toNanos(timeout);
        int n = tasks.size();
        if (n <= 0)
            throw new IllegalArgumentException();
        InvokeAnyRoot<T> root = new InvokeAnyRoot<T>(n, this);
        ArrayList<InvokeAnyTask<T>> fs = new ArrayList<>(n);
        try {
            for (Callable<T> c : tasks) {
                if (c == null)
                    throw new NullPointerException();
                InvokeAnyTask<T> f = new InvokeAnyTask<T>(root, c);
                fs.add(f);
                poolSubmit(true, f);
                if (root.isDone())
                    break;
            }
            return root.get(nanos, TimeUnit.NANOSECONDS);
        } finally {
            for (InvokeAnyTask<T> f : fs)
                ForkJoinTask.cancelIgnoringExceptions(f);
        }
    }

    /**
     * Returns the factory used for constructing new workers.
     *
     * @return the factory used for constructing new workers
     */
    public ForkJoinWorkerThreadFactory getFactory() {
        return factory;
    }

    /**
     * Returns the handler for internal worker threads that terminate
     * due to unrecoverable errors encountered while executing tasks.
     *
     * @return the handler, or {@code null} if none
     */
    public UncaughtExceptionHandler getUncaughtExceptionHandler() {
        return ueh;
    }

    /**
     * Returns the targeted parallelism level of this pool.
     *
     * @return the targeted parallelism level of this pool
     */
    public int getParallelism() {
        return Math.max(getParallelismOpaque(), 1);
    }

    /**
     * Returns the targeted parallelism level of the common pool.
     *
     * @return the targeted parallelism level of the common pool
     * @since 1.8
     */
    public static int getCommonPoolParallelism() {
        return common.getParallelism();
    }

    /**
     * Returns the number of worker threads that have started but not
     * yet terminated.  The result returned by this method may differ
     * from {@link #getParallelism} when threads are created to
     * maintain parallelism when others are cooperatively blocked.
     *
     * @return the number of worker threads
     */
    public int getPoolSize() {
        return (short)(ctl >>> TC_SHIFT);
    }

    /**
     * Returns {@code true} if this pool uses local first-in-first-out
     * scheduling mode for forked tasks that are never joined.
     *
     * @return {@code true} if this pool uses async mode
     */
    public boolean getAsyncMode() {
        return (config & FIFO) != 0;
    }

    /**
     * Returns an estimate of the number of worker threads that are
     * not blocked waiting to join tasks or for other managed
     * synchronization. This method may overestimate the
     * number of running threads.
     *
     * @return the number of worker threads
     */
    public int getRunningThreadCount() {
        WorkQueue[] qs; WorkQueue q;
        int rc = 0;
        if ((runState & TERMINATED) == 0 && (qs = queues) != null) {
            for (int i = 1; i < qs.length; i += 2) {
                if ((q = qs[i]) != null && q.isApparentlyUnblocked())
                    ++rc;
            }
        }
        return rc;
    }

    /**
     * Returns an estimate of the number of threads that are currently
     * stealing or executing tasks. This method may overestimate the
     * number of active threads.
     *
     * @return the number of active threads
     */
    public int getActiveThreadCount() {
        return Math.max((short)(ctl >>> RC_SHIFT), 0);
    }

    /**
     * Returns {@code true} if all worker threads are currently idle.
     * An idle worker is one that cannot obtain a task to execute
     * because none are available to steal from other threads, and
     * there are no pending submissions to the pool. This method is
     * conservative; it might not return {@code true} immediately upon
     * idleness of all threads, but will eventually become true if
     * threads remain inactive.
     *
     * @return {@code true} if all threads are currently idle
     */
    public boolean isQuiescent() {
        return canStop();
    }

    /**
     * Returns an estimate of the total number of completed tasks that
     * were executed by a thread other than their submitter. The
     * reported value underestimates the actual total number of steals
     * when the pool is not quiescent. This value may be useful for
     * monitoring and tuning fork/join programs: in general, steal
     * counts should be high enough to keep threads busy, but low
     * enough to avoid overhead and contention across threads.
     *
     * @return the number of steals
     */
    public long getStealCount() {
        long count = stealCount;
        WorkQueue[] qs; WorkQueue q;
        if ((qs = queues) != null) {
            for (int i = 1; i < qs.length; i += 2) {
                if ((q = qs[i]) != null)
                     count += (long)q.nsteals & 0xffffffffL;
            }
        }
        return count;
    }

    /**
     * Returns an estimate of the total number of tasks currently held
     * in queues by worker threads (but not including tasks submitted
     * to the pool that have not begun executing). This value is only
     * an approximation, obtained by iterating across all threads in
     * the pool. This method may be useful for tuning task
     * granularities.
     *
     * @return the number of queued tasks
     */
    public long getQueuedTaskCount() {
        WorkQueue[] qs; WorkQueue q;
        int count = 0;
        if ((runState & TERMINATED) == 0 && (qs = queues) != null) {
            for (int i = 1; i < qs.length; i += 2) {
                if ((q = qs[i]) != null)
                    count += q.queueSize();
            }
        }
        return count;
    }

    /**
     * Returns an estimate of the number of tasks submitted to this
     * pool that have not yet begun executing.  This method may take
     * time proportional to the number of submissions.
     *
     * @return the number of queued submissions
     */
    public int getQueuedSubmissionCount() {
        WorkQueue[] qs; WorkQueue q;
        int count = 0;
        if ((runState & TERMINATED) == 0 && (qs = queues) != null) {
            for (int i = 0; i < qs.length; i += 2) {
                if ((q = qs[i]) != null)
                    count += q.queueSize();
            }
        }
        return count;
    }

    /**
     * Returns {@code true} if there are any tasks submitted to this
     * pool that have not yet begun executing.
     *
     * @return {@code true} if there are any queued submissions
     */
    public boolean hasQueuedSubmissions() {
        return hasTasks(true);
    }

    /**
     * Removes and returns the next unexecuted submission if one is
     * available.  This method may be useful in extensions to this
     * class that re-assign work in systems with multiple pools.
     *
     * @return the next submission, or {@code null} if none
     */
    protected ForkJoinTask<?> pollSubmission() {
        return pollScan(true);
    }

    /**
     * Removes all available unexecuted submitted and forked tasks
     * from scheduling queues and adds them to the given collection,
     * without altering their execution status. These may include
     * artificially generated or wrapped tasks. This method is
     * designed to be invoked only when the pool is known to be
     * quiescent. Invocations at other times may not remove all
     * tasks. A failure encountered while attempting to add elements
     * to collection {@code c} may result in elements being in
     * neither, either or both collections when the associated
     * exception is thrown.  The behavior of this operation is
     * undefined if the specified collection is modified while the
     * operation is in progress.
     *
     * @param c the collection to transfer elements into
     * @return the number of elements transferred
     */
    protected int drainTasksTo(Collection<? super ForkJoinTask<?>> c) {
        int count = 0;
        for (ForkJoinTask<?> t; (t = pollScan(false)) != null; ) {
            c.add(t);
            ++count;
        }
        return count;
    }

    /**
     * Returns a string identifying this pool, as well as its state,
     * including indications of run state, parallelism level, and
     * worker and task counts.
     *
     * @return a string identifying this pool, as well as its state
     */
    public String toString() {
        // Use a single pass through queues to collect counts
        long st = stealCount;
        long qt = 0L, ss = 0L; int rc = 0;
        WorkQueue[] qs; WorkQueue q;
        if ((qs = queues) != null) {
            for (int i = 0; i < qs.length; ++i) {
                if ((q = qs[i]) != null) {
                    int size = q.queueSize();
                    if ((i & 1) == 0)
                        ss += size;
                    else {
                        qt += size;
                        st += (long)q.nsteals & 0xffffffffL;
                        if (q.isApparentlyUnblocked())
                            ++rc;
                    }
                }
            }
        }

        int pc = parallelism;
        long c = ctl;
        int tc = (short)(c >>> TC_SHIFT);
        int ac = (short)(c >>> RC_SHIFT);
        if (ac < 0) // ignore transient negative
            ac = 0;
        int rs = runState;
        String level = ((rs & TERMINATED) != 0 ? "Terminated" :
                        (rs & STOP)       != 0 ? "Terminating" :
                        (rs & SHUTDOWN)   != 0 ? "Shutting down" :
                        "Running");
        return super.toString() +
            "[" + level +
            ", parallelism = " + pc +
            ", size = " + tc +
            ", active = " + ac +
            ", running = " + rc +
            ", steals = " + st +
            ", tasks = " + qt +
            ", submissions = " + ss +
            "]";
    }

    /**
     * Possibly initiates an orderly shutdown in which previously
     * submitted tasks are executed, but no new tasks will be
     * accepted. Invocation has no effect on execution state if this
     * is the {@link #commonPool()}, and no additional effect if
     * already shut down.  Tasks that are in the process of being
     * submitted concurrently during the course of this method may or
     * may not be rejected.
     *
     * @throws SecurityException if a security manager exists and
     *         the caller is not permitted to modify threads
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}{@code ("modifyThread")}
     */
    public void shutdown() {
        checkPermission();
        tryTerminate(false, true);
    }

    /**
     * Possibly attempts to cancel and/or stop all tasks, and reject
     * all subsequently submitted tasks.  Invocation has no effect on
     * execution state if this is the {@link #commonPool()}, and no
     * additional effect if already shut down. Otherwise, tasks that
     * are in the process of being submitted or executed concurrently
     * during the course of this method may or may not be
     * rejected. This method cancels both existing and unexecuted
     * tasks, in order to permit termination in the presence of task
     * dependencies. So the method always returns an empty list
     * (unlike the case for some other Executors).
     *
     * @return an empty list
     * @throws SecurityException if a security manager exists and
     *         the caller is not permitted to modify threads
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}{@code ("modifyThread")}
     */
    public List<Runnable> shutdownNow() {
        checkPermission();
        tryTerminate(true, true);
        return Collections.emptyList();
    }

    /**
     * Returns {@code true} if all tasks have completed following shut down.
     *
     * @return {@code true} if all tasks have completed following shut down
     */
    public boolean isTerminated() {
        return (runState & TERMINATED) != 0;
    }

    /**
     * Returns {@code true} if the process of termination has
     * commenced but not yet completed.  This method may be useful for
     * debugging. A return of {@code true} reported a sufficient
     * period after shutdown may indicate that submitted tasks have
     * ignored or suppressed interruption, or are waiting for I/O,
     * causing this executor not to properly terminate. (See the
     * advisory notes for class {@link ForkJoinTask} stating that
     * tasks should not normally entail blocking operations.  But if
     * they do, they must abort them on interrupt.)
     *
     * @return {@code true} if terminating but not yet terminated
     */
    public boolean isTerminating() {
        return (runState & (STOP | TERMINATED)) == STOP;
    }

    /**
     * Returns {@code true} if this pool has been shut down.
     *
     * @return {@code true} if this pool has been shut down
     */
    public boolean isShutdown() {
        return runState != 0;
    }

    /**
     * Blocks until all tasks have completed execution after a
     * shutdown request, or the timeout occurs, or the current thread
     * is interrupted, whichever happens first. Because the {@link
     * #commonPool()} never terminates until program shutdown, when
     * applied to the common pool, this method is equivalent to {@link
     * #awaitQuiescence(long, TimeUnit)} but always returns {@code false}.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return {@code true} if this executor terminated and
     *         {@code false} if the timeout elapsed before termination
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean awaitTermination(long timeout, TimeUnit unit)
        throws InterruptedException {
        ReentrantLock lock; Condition cond; boolean terminated;
        long nanos = unit.toNanos(timeout);
        if ((config & ISCOMMON) != 0) {
            if (helpQuiescePool(this, nanos, true) < 0)
                throw new InterruptedException();
            terminated = false;
        }
        else if (!(terminated = ((runState & TERMINATED) != 0))) {
            tryTerminate(false, false);  // reduce transient blocking
            if ((lock = registrationLock) != null &&
                !(terminated = (((runState & TERMINATED) != 0)))) {
                lock.lock();
                try {
                    if ((cond = termination) == null)
                        termination = cond = lock.newCondition();
                    while (!(terminated = ((runState & TERMINATED) != 0)) &&
                           nanos > 0L)
                        nanos = cond.awaitNanos(nanos);
                } finally {
                    lock.unlock();
                }
            }
        }
        return terminated;
    }

    /**
     * If called by a ForkJoinTask operating in this pool, equivalent
     * in effect to {@link ForkJoinTask#helpQuiesce}. Otherwise,
     * waits and/or attempts to assist performing tasks until this
     * pool {@link #isQuiescent} or the indicated timeout elapses.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return {@code true} if quiescent; {@code false} if the
     * timeout elapsed.
     */
    public boolean awaitQuiescence(long timeout, TimeUnit unit) {
        return (helpQuiescePool(this, unit.toNanos(timeout), false) > 0);
    }

    /**
     * Interface for extending managed parallelism for tasks running
     * in {@link ForkJoinPool}s.
     *
     * <p>A {@code ManagedBlocker} provides two methods.  Method
     * {@link #isReleasable} must return {@code true} if blocking is
     * not necessary. Method {@link #block} blocks the current thread
     * if necessary (perhaps internally invoking {@code isReleasable}
     * before actually blocking). These actions are performed by any
     * thread invoking {@link
     * ForkJoinPool#managedBlock(ManagedBlocker)}.  The unusual
     * methods in this API accommodate synchronizers that may, but
     * don't usually, block for long periods. Similarly, they allow
     * more efficient internal handling of cases in which additional
     * workers may be, but usually are not, needed to ensure
     * sufficient parallelism.  Toward this end, implementations of
     * method {@code isReleasable} must be amenable to repeated
     * invocation. Neither method is invoked after a prior invocation
     * of {@code isReleasable} or {@code block} returns {@code true}.
     *
     * <p>For example, here is a ManagedBlocker based on a
     * ReentrantLock:
     * <pre> {@code
     * class ManagedLocker implements ManagedBlocker {
     *   final ReentrantLock lock;
     *   boolean hasLock = false;
     *   ManagedLocker(ReentrantLock lock) { this.lock = lock; }
     *   public boolean block() {
     *     if (!hasLock)
     *       lock.lock();
     *     return true;
     *   }
     *   public boolean isReleasable() {
     *     return hasLock || (hasLock = lock.tryLock());
     *   }
     * }}</pre>
     *
     * <p>Here is a class that possibly blocks waiting for an
     * item on a given queue:
     * <pre> {@code
     * class QueueTaker<E> implements ManagedBlocker {
     *   final BlockingQueue<E> queue;
     *   volatile E item = null;
     *   QueueTaker(BlockingQueue<E> q) { this.queue = q; }
     *   public boolean block() throws InterruptedException {
     *     if (item == null)
     *       item = queue.take();
     *     return true;
     *   }
     *   public boolean isReleasable() {
     *     return item != null || (item = queue.poll()) != null;
     *   }
     *   public E getItem() { // call after pool.managedBlock completes
     *     return item;
     *   }
     * }}</pre>
     */
    public static interface ManagedBlocker {
        /**
         * Possibly blocks the current thread, for example waiting for
         * a lock or condition.
         *
         * @return {@code true} if no additional blocking is necessary
         * (i.e., if isReleasable would return true)
         * @throws InterruptedException if interrupted while waiting
         * (the method is not required to do so, but is allowed to)
         */
        boolean block() throws InterruptedException;

        /**
         * Returns {@code true} if blocking is unnecessary.
         * @return {@code true} if blocking is unnecessary
         */
        boolean isReleasable();
    }

    /**
     * Runs the given possibly blocking task.  When {@linkplain
     * ForkJoinTask#inForkJoinPool() running in a ForkJoinPool}, this
     * method possibly arranges for a spare thread to be activated if
     * necessary to ensure sufficient parallelism while the current
     * thread is blocked in {@link ManagedBlocker#block blocker.block()}.
     *
     * <p>This method repeatedly calls {@code blocker.isReleasable()} and
     * {@code blocker.block()} until either method returns {@code true}.
     * Every call to {@code blocker.block()} is preceded by a call to
     * {@code blocker.isReleasable()} that returned {@code false}.
     *
     * <p>If not running in a ForkJoinPool, this method is
     * behaviorally equivalent to
     * <pre> {@code
     * while (!blocker.isReleasable())
     *   if (blocker.block())
     *     break;}</pre>
     *
     * If running in a ForkJoinPool, the pool may first be expanded to
     * ensure sufficient parallelism available during the call to
     * {@code blocker.block()}.
     *
     * @param blocker the blocker task
     * @throws InterruptedException if {@code blocker.block()} did so
     */
    public static void managedBlock(ManagedBlocker blocker)
        throws InterruptedException {
        Thread t; ForkJoinPool p;
        if ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread &&
            (p = ((ForkJoinWorkerThread)t).pool) != null)
            p.compensatedBlock(blocker);
        else
            unmanagedBlock(blocker);
    }

    /** ManagedBlock for ForkJoinWorkerThreads */
    private void compensatedBlock(ManagedBlocker blocker)
        throws InterruptedException {
        if (blocker == null) throw new NullPointerException();
        for (;;) {
            int comp; boolean done;
            long c = ctl;
            if (blocker.isReleasable())
                break;
            if ((comp = tryCompensate(c, false)) >= 0) {
                long post = (comp == 0) ? 0L : RC_UNIT;
                try {
                    done = blocker.block();
                } finally {
                    getAndAddCtl(post);
                }
                if (done)
                    break;
            }
        }
    }

    /**
     * Invokes tryCompensate to create or re-activate a spare thread to
     * compensate for a thread that performs a blocking operation. When the
     * blocking operation is done then endCompensatedBlock must be invoked
     * with the value returned by this method to re-adjust the parallelism.
     */
    private long beginCompensatedBlock() {
        for (;;) {
            int comp;
            if ((comp = tryCompensate(ctl, false)) >= 0) {
                return (comp == 0) ? 0L : RC_UNIT;
            } else {
                Thread.onSpinWait();
            }
        }
    }

    /**
     * Re-adjusts parallelism after a blocking operation completes.
     */
    void endCompensatedBlock(long post) {
        if (post > 0) {
            getAndAddCtl(post);
        }
    }

    /** ManagedBlock for external threads */
    private static void unmanagedBlock(ManagedBlocker blocker)
        throws InterruptedException {
        if (blocker == null) throw new NullPointerException();
        do {} while (!blocker.isReleasable() && !blocker.block());
    }

    // AbstractExecutorService.newTaskFor overrides rely on
    // undocumented fact that ForkJoinTask.adapt returns ForkJoinTasks
    // that also implement RunnableFuture.

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        return new ForkJoinTask.AdaptedRunnable<T>(runnable, value);
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        return new ForkJoinTask.AdaptedCallable<T>(callable);
    }

    static {
        U = Unsafe.getUnsafe();
        Class<ForkJoinPool> klass = ForkJoinPool.class;
        try {
            POOLIDS = U.staticFieldOffset(klass.getDeclaredField("poolIds"));
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
        CTL = U.objectFieldOffset(klass, "ctl");
        RUNSTATE = U.objectFieldOffset(klass, "runState");
        PARALLELISM = U.objectFieldOffset(klass, "parallelism");
        THREADIDS = U.objectFieldOffset(klass, "threadIds");

        defaultForkJoinWorkerThreadFactory =
            new DefaultForkJoinWorkerThreadFactory();
        @SuppressWarnings("removal")
        ForkJoinPool p = common = (System.getSecurityManager() == null) ?
            new ForkJoinPool((byte)0) :
            AccessController.doPrivileged(new PrivilegedAction<>() {
                    public ForkJoinPool run() {
                        return new ForkJoinPool((byte)0); }});
        // allow access to non-public methods
        SharedSecrets.setJavaUtilConcurrentFJPAccess(
            new JavaUtilConcurrentFJPAccess() {
                @Override
                public long beginCompensatedBlock(ForkJoinPool pool) {
                    return pool.beginCompensatedBlock();
                }
                public void endCompensatedBlock(ForkJoinPool pool, long post) {
                    pool.endCompensatedBlock(post);
                }
            });
        Class<?> dep = LockSupport.class; // ensure loaded
    }
}
