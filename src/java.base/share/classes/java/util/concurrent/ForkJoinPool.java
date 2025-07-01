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
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.LockSupport;
import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.JavaUtilConcurrentFJPAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.misc.Unsafe;
import jdk.internal.vm.SharedThreadContainer;
import static java.util.concurrent.DelayScheduler.ScheduledForkJoinTask;

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
 * <p>Additionally, this class supports {@link
 * ScheduledExecutorService} methods to delay or periodically execute
 * tasks, as well as method {@link #submitWithTimeout} to cancel tasks
 * that take too long. The scheduled functions or actions may create
 * and invoke other {@linkplain ForkJoinTask ForkJoinTasks}. Delayed
 * actions become enabled for execution and behave as ordinary submitted
 * tasks when their delays elapse.  Scheduling methods return
 * {@linkplain ForkJoinTask ForkJoinTasks} that implement the {@link
 * ScheduledFuture} interface. Resource exhaustion encountered after
 * initial submission results in task cancellation. When time-based
 * methods are used, shutdown policies match the default policies of
 * class {@link ScheduledThreadPoolExecutor}: upon {@link #shutdown},
 * existing periodic tasks will not re-execute, and the pool
 * terminates when quiescent and existing delayed tasks
 * complete. Method {@link #cancelDelayedTasksOnShutdown} may be used
 * to disable all delayed tasks upon shutdown, and method {@link
 * #shutdownNow} may be used to instead unconditionally initiate pool
 * termination. Monitoring methods such as {@link #getQueuedTaskCount}
 * do not include scheduled tasks that are not yet enabled for execution,
 * which are reported separately by method {@link
 * #getDelayedTaskCount}.
 *
 * <p>The parameters used to construct the common pool may be controlled by
 * setting the following {@linkplain System#getProperty system properties}:
 * <ul>
 * <li>{@systemProperty java.util.concurrent.ForkJoinPool.common.parallelism}
 * - the parallelism level, a non-negative integer. Usage is discouraged.
 *   Use {@link #setParallelism} instead.
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
 *
 * Upon any error in establishing these settings, default parameters
 * are used. It is possible to disable use of threads by using a
 * factory that may return {@code null}, in which case some tasks may
 * never execute. While possible, it is strongly discouraged to set
 * the parallelism property to zero, which may be internally
 * overridden in the presence of intrinsically async tasks.
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
public class ForkJoinPool extends AbstractExecutorService
    implements ScheduledExecutorService {

    /*
     * Implementation Overview
     *
     * This class and its nested classes provide the main
     * functionality and control for a set of worker threads.  Because
     * most internal methods and nested classes are interrelated,
     * their main rationale and descriptions are presented here;
     * individual methods and nested classes contain only brief
     * comments about details. Broadly: submissions from non-FJ
     * threads enter into submission queues.  Workers take these tasks
     * and typically split them into subtasks that may be stolen by
     * other workers. Work-stealing based on randomized scans
     * generally leads to better throughput than "work dealing" in
     * which producers assign tasks to idle threads, in part because
     * threads that have finished other tasks before the signalled
     * thread wakes up (which can be a long time) can take the task
     * instead.  Preference rules give first priority to processing
     * tasks from their own queues (LIFO or FIFO, depending on mode),
     * then to randomized FIFO steals of tasks in other queues.
     *
     * This framework began as vehicle for supporting structured
     * parallelism using work-stealing, designed to work best when
     * tasks are dag-structured (wrt completion dependencies), nested
     * (generated using recursion or completions), of reasonable
     * granularity, independent (wrt memory and resources) and where
     * callers participate in task execution. These are properties
     * that anyone aiming for efficient parallel multicore execution
     * should design for.  Over time, the scalability advantages of
     * this framework led to extensions to better support more diverse
     * usage contexts, amounting to weakenings or violations of each
     * of these properties. Accommodating them may compromise
     * performance, but mechanics discussed below include tradeoffs
     * attempting to arrange that no single performance issue dominates.
     *
     * Here's a brief history of major revisions, each also with other
     * minor features and changes.
     *
     * 1. Only handle recursively structured computational tasks
     * 2. Async (FIFO) mode and striped submission queues
     * 3. Completion-based tasks (mainly CountedCompleters)
     * 4. CommonPool and parallelStream support
     * 5. InterruptibleTasks for externally submitted tasks
     * 6. Support ScheduledExecutorService methods
     *
     * Most changes involve adaptions of base algorithms using
     * combinations of static and dynamic bitwise mode settings (both
     * here and in ForkJoinTask), and subclassing of ForkJoinTask.
     * There are a fair number of odd code constructions and design
     * decisions for components that reside at the edge of Java vs JVM
     * functionality.
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
     * algorithms similar to the one used here.  We use per-operation
     * ordered writes of various kinds for accesses when required.
     *
     * We also support a user mode in which local task processing is
     * in FIFO, not LIFO order, simply by using a local version of
     * poll rather than pop.  This can be useful in message-passing
     * frameworks in which tasks are never joined, although with
     * increased contention among task producers and consumers. Also,
     * the same data structure (and class) is used for "submission
     * queues" (described below) holding externally submitted tasks,
     * that differ only in that a lock (using field "phase"; see below) is
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
     * which requires stronger forms of order accesses.
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
     *    CAS succeeds.
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
     *    encourage timely writes to indices using otherwise
     *    unnecessarily strong writes.
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
     *    contention, non-owners avoid reading the "top" when
     *    possible, by using one-ahead reads to check whether to
     *    repoll, relying on the fact that a non-empty queue does not
     *    have two null slots in a row, except in cases (resizes and
     *    shifts) that can be detected with a secondary recheck that
     *    is less likely to conflict with owner writes.
     *
     * The poll operations in q.poll(), runWorker(), helpJoin(), and
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
     * with submitting threads (or carriers when using VirtualThreads)
     * using a form of hashing.  The ThreadLocalRandom probe value
     * serves as a hash code for choosing existing queues, and may be
     * randomly repositioned upon contention with other submitters.
     * In essence, submitters act like workers except that they are
     * restricted to executing local tasks that they submitted (or
     * when known, subtasks thereof).  Insertion of tasks in shared
     * mode requires a lock. We use only a simple spinlock (as one
     * role of field "phase") because submitters encountering a busy
     * queue move to a different position to use or create other
     * queues.  They (spin) block when registering new queues, or
     * indirectly elsewhere, by revisiting later.
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
     * subfields.
     *
     * Field "runState" and per-WorkQueue field "phase" play similar
     * roles, as lockable, versioned counters. Field runState also
     * includes monotonic event bits:
     * * SHUTDOWN: no more external tasks accepted; STOP when quiescent
     * * STOP: no more tasks run, and deregister all workers
     * * CLEANED: all unexecuted tasks have been cancelled
     * * TERMINATED: all workers deregistered and all queues cleaned
     * The version tags enable detection of state changes (by
     * comparing two reads) modulo bit wraparound. The bit range in
     * each case suffices for purposes of determining quiescence,
     * termination, avoiding ABA-like errors, and signal control, most
     * of which are ultimately based on at most 15bit ranges (due to
     * 32767 max total workers). RunState updates do not need to be
     * atomic with respect to ctl updates, but because they are not,
     * some care is required to avoid stalls. The seqLock properties
     * detect changes and conditionally upgrade to coordinate with
     * updates. It is typically held for less than a dozen
     * instructions unless the queue array is being resized, during
     * which contention is rare. To be conservative, lockRunState is
     * implemented as a spin/sleep loop. Here and elsewhere spin
     * constants are short enough to apply even on systems with few
     * available processors.  In addition to checking pool status,
     * reads of runState sometimes serve as acquire fences before
     * reading other fields.
     *
     * Field "parallelism" holds the target parallelism (normally
     * corresponding to pool size). Users can dynamically reset target
     * parallelism, but is only accessed when signalling or awaiting
     * work, so only slowly has an effect in creating threads or
     * letting them time out and terminate when idle.
     *
     * Array "queues" holds references to WorkQueues.  It is updated
     * (only during worker creation and termination) under the
     * runState lock. It is otherwise concurrently readable but reads
     * for use in scans (see below) are always prefaced by a volatile
     * read of runState (or equivalent constructions), ensuring that
     * its state is current at the point it is used (which is all we
     * require). To simplify index-based operations, the array size is
     * always a power of two, and all readers must tolerate null
     * slots.  Worker queues are at odd indices. Worker phase ids
     * masked with SMASK match their index. Shared (submission) queues
     * are at even indices. Grouping them together in this way aids in
     * task scanning: At top-level, both kinds of queues should be
     * sampled with approximately the same probability, which is
     * simpler if they are all in the same array. But we also need to
     * identify what kind they are without looking at them, leading to
     * this odd/even scheme. One disadvantage is that there are
     * usually many fewer submission queues, so there can be many
     * wasted probes (null slots). But this is still cheaper than
     * alternatives. Other loops over the queues array vary in origin
     * and stride depending on whether they cover only submission
     * (even) or worker (odd) queues or both, and whether they require
     * randomness (in which case cyclically exhaustive strides may be
     * used).
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
     * indices, not references. Operations on queues obtained from
     * these indices remain valid (with at most some unnecessary extra
     * work) even if an underlying worker failed and was replaced by
     * another at the same index. During termination, worker queue
     * array updates are disabled.
     *
     * Queuing Idle Workers. Unlike HPC work-stealing frameworks, we
     * cannot let workers spin indefinitely scanning for tasks when
     * none can be found immediately, and we cannot start/resume
     * workers unless there appear to be tasks available.  On the
     * other hand, we must quickly prod them into action when new
     * tasks are submitted or generated. These latencies are mainly a
     * function of JVM park/unpark (and underlying OS) performance,
     * which can be slow and variable (even though usages are
     * streamlined as much as possible).  In many usages, ramp-up time
     * is the main limiting factor in overall performance, which is
     * compounded at program start-up by JIT compilation and
     * allocation. On the other hand, throughput degrades when too
     * many threads poll for too few tasks. (See below.)
     *
     * The "ctl" field atomically maintains total and "released"
     * worker counts, plus the head of the available worker queue
     * (actually stack, represented by the lower 32bit subfield of
     * ctl).  Released workers are those known to be scanning for
     * and/or running tasks (we cannot accurately determine
     * which). Unreleased ("available") workers are recorded in the
     * ctl stack. These workers are made eligible for signalling by
     * enqueuing in ctl (see method deactivate).  This "queue" is a
     * form of Treiber stack. This is ideal for activating threads in
     * most-recently used order, and improves performance and
     * locality, outweighing the disadvantages of being prone to
     * contention and inability to release a worker unless it is
     * topmost on stack. The top stack state holds the value of the
     * "phase" field of the worker: its index and status, plus a
     * version counter that, in addition to the count subfields (also
     * serving as version stamps) provide protection against Treiber
     * stack ABA effects.
     *
     * Creating workers. To create a worker, we pre-increment counts
     * (serving as a reservation), and attempt to construct a
     * ForkJoinWorkerThread via its factory. On starting, the new
     * thread first invokes registerWorker, where it is assigned an
     * index in the queues array (expanding the array if necessary).
     * Upon any exception across these steps, or null return from
     * factory, deregisterWorker adjusts counts and records
     * accordingly.  If a null return, the pool continues running with
     * fewer than the target number workers. If exceptional, the
     * exception is propagated, generally to some external caller.
     *
     * WorkQueue field "phase" encodes the queue array id in lower
     * bits, and otherwise acts similarly to the pool runState field:
     * The "IDLE" bit is clear while active (either a released worker
     * or a locked external queue), with other bits serving as a
     * version counter to distinguish changes across multiple reads.
     * Note that phase field updates lag queue CAS releases; seeing a
     * non-idle phase does not guarantee that the worker is available
     * (and so is never checked in this way).
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
     * on the side of too many workers vs too few to avoid stalls:
     *
     *  * If computations are purely tree structured, it suffices for
     *    every worker to activate another when it pushes a task into
     *    an empty queue, resulting in O(log(#threads)) steps to full
     *    activation. Emptiness must be conservatively approximated,
     *    which may result in unnecessary signals.  Also, to reduce
     *    resource usages in some cases, at the expense of slower
     *    startup in others, activation of an idle thread is preferred
     *    over creating a new one, here and elsewhere.
     *
     *  * At the other extreme, if "flat" tasks (those that do not in
     *    turn generate others) come in serially from only a single
     *    producer, each worker taking a task from a queue should
     *    propagate a signal if there are more tasks in that
     *    queue. This is equivalent to, but generally faster than,
     *    arranging the stealer take multiple tasks, re-pushing one or
     *    more on its own queue, and signalling (because its queue is
     *    empty), also resulting in logarithmic full activation
     *    time. If tasks do not not engage in unbounded loops based on
     *    the actions of other workers with unknown dependencies loop,
     *    this form of proagation can be limited to one signal per
     *    activation (phase change). We distinguish the cases by
     *    further signalling only if the task is an InterruptibleTask
     *    (see below), which are the only supported forms of task that
     *    may do so.
     *
     * * Because we don't know about usage patterns (or most commonly,
     *    mixtures), we use both approaches, which present even more
     *    opportunities to over-signal. (Failure to distinguish these
     *    cases in terms of submission methods was arguably an early
     *    design mistake.)  Note that in either of these contexts,
     *    signals may be (and often are) unnecessary because active
     *    workers continue scanning after running tasks without the
     *    need to be signalled (which is one reason work stealing is
     *    often faster than alternatives), so additional workers
     *    aren't needed.
     *
     * * For rapidly branching tasks that require full pool resources,
     *   oversignalling is OK, because signalWork will soon have no
     *   more workers to create or reactivate. But for others (mainly
     *   externally submitted tasks), overprovisioning may cause very
     *   noticeable slowdowns due to contention and resource
     *   wastage. We reduce impact by deactivating workers when
     *   queues don't have accessible tasks, but reactivating and
     *   rescanning if other tasks remain.
     *
     * * Despite these, signal contention and overhead effects still
     *   occur during ramp-up and ramp-down of small computations.
     *
     * Scanning. Method runWorker performs top-level scanning for (and
     * execution of) tasks by polling a pseudo-random permutation of
     * the array (by starting at a given index, and using a constant
     * cyclically exhaustive stride.)  It uses the same basic polling
     * method as WorkQueue.poll(), but restarts with a different
     * permutation on each invocation.  The pseudorandom generator
     * need not have high-quality statistical properties in the long
     * term. We use Marsaglia XorShifts, seeded with the Weyl sequence
     * from ThreadLocalRandom probes, which are cheap and
     * suffice. Each queue's polling attempts to avoid becoming stuck
     * when other scanners/pollers stall.  Scans do not otherwise
     * explicitly take into account core affinities, loads, cache
     * localities, etc, However, they do exploit temporal locality
     * (which usually approximates these) by preferring to re-poll
     * from the same queue after a successful poll before trying
     * others, which also reduces bookkeeping, cache traffic, and
     * scanning overhead. But it also reduces fairness, which is
     * partially counteracted by giving up on detected interference
     * (which also reduces contention when too many workers try to
     * take small tasks from the same queue).
     *
     * Deactivation. When no tasks are found by a worker in runWorker,
     * it tries to deactivate()), giving up (and rescanning) on "ctl"
     * contention. To avoid missed signals during deactivation, the
     * method rescans and reactivates if there may have been a missed
     * signal during deactivation. To reduce false-alarm reactivations
     * while doing so, we scan multiple times (analogously to method
     * quiescent()) before trying to reactivate.  Because idle workers
     * are often not yet blocked (parked), we use a WorkQueue field to
     * advertise that a waiter actually needs unparking upon signal.
     *
     * Quiescence. Workers scan looking for work, giving up when they
     * don't find any, without being sure that none are available.
     * However, some required functionality relies on consensus about
     * quiescence (also termination, discussed below). The count
     * fields in ctl allow accurate discovery of states in which all
     * workers are idle.  However, because external (asynchronous)
     * submitters are not part of this vote, these mechanisms
     * themselves do not guarantee that the pool is in a quiescent
     * state with respect to methods isQuiescent, shutdown (which
     * begins termination when quiescent), helpQuiesce, and indirectly
     * others including tryCompensate. Method quiescent() is used in
     * all of these contexts. It provides checks that all workers are
     * idle and there are no submissions that they could poll if they
     * were not idle, retrying on inconsistent reads of queues and
     * using the runState seqLock to retry on queue array updates.
     * (It also reports quiescence if the pool is terminating.) A true
     * report means only that there was a moment at which quiescence
     * held.  False negatives are inevitable (for example when queues
     * indices lag updates, as described above), which is accommodated
     * when (tentatively) idle by scanning for work etc, and then
     * re-invoking. This includes cases in which the final unparked
     * thread (in deactivate()) uses quiescent() to check for tasks
     * that could have been added during a race window that would not
     * be accompanied by a signal, in which case re-activating itself
     * (or any other worker) to rescan. Method helpQuiesce acts
     * similarly but cannot rely on ctl counts to determine that all
     * workers are inactive because the caller and any others
     * executing helpQuiesce are not included in counts.
     *
     * Termination. Termination is initiated by setting STOP in one of
     * three ways (via methods tryTerminate and quiescent):
     * * A call to shutdownNow, in which case all workers are
     *   interrupted, first ensuring that the queues array is stable,
     *   to avoid missing any workers.
     * * A call to shutdown when quiescent, in which case method
     *   releaseWaiters is used to dequeue them, at which point they notice
     *   STOP state and return from runWorker to deregister();
     * * The pool becomes quiescent() sometime after shutdown has
     *   been called, in which case releaseWaiters is also used to
     *   propagate as they deregister.
     * Upon STOP, each worker, as well as external callers to
     * tryTerminate (via close() etc) race to set CLEANED, indicating
     * that all tasks have been cancelled. The implementation (method
     * cleanQueues) balances cases in which there may be many tasks to
     * cancel (benefitting from parallelism) versus contention and
     * interference when many threads try to poll remaining queues,
     * while also avoiding unnecessary rechecks, by using
     * pseudorandom scans and giving up upon interference. This may be
     * retried by the same caller only when there are no more
     * registered workers, using the same criteria as method
     * quiescent.  When CLEANED and all workers have deregistered,
     * TERMINATED is set, also signalling any caller of
     * awaitTermination or close.  Because shutdownNow-based
     * termination relies on interrupts, there is no guarantee that
     * workers will stop if their tasks ignore interrupts.  Class
     * InterruptibleTask (see below) further arranges runState checks
     * before executing task bodies, and ensures interrupts while
     * terminating. Even so, there are no guarantees because tasks may
     * internally enter unbounded loops.
     *
     * Trimming workers. To release resources after periods of lack of
     * use, a worker starting to wait when the pool is quiescent will
     * time out and terminate if the pool has remained quiescent for
     * period given by field keepAlive (default 60sec), which applies
     * to the first timeout of a quiescent pool. Subsequent cases use
     * minimal delays such that, if still quiescent, all will be
     * released soon thereafter. This is checked by setting the
     * "source" field of signallee to an invalid value, that will
     * remain invalid only if it did not process any tasks.
     *
     * Joining Tasks
     * =============
     *
     * The "Join" part of ForkJoinPools consists of a set of
     * mechanisms that sometimes or always (depending on the kind of
     * task) avoid context switching or adding worker threads when one
     * task would otherwise be blocked waiting for completion of
     * another, basically, just by running that task or one of its
     * subtasks if not already taken. These mechanics are disabled for
     * InterruptibleTasks, that guarantee that callers do not execute
     * submitted tasks.
     *
     * The basic structure of joining is an extended spin/block scheme
     * in which workers check for task completions status between
     * steps to find other work, until relevant pool state stabilizes
     * enough to believe that no such tasks are available, at which
     * point blocking. This is usually a good choice of when to block
     * that would otherwise be harder to approximate.
     *
     * These forms of helping may increase stack space usage, but that
     * space is bounded in tree/dag structured procedurally parallel
     * designs to be no more than that if a task were executed only by
     * the joining thread. This is arranged by associated task
     * subclasses that also help detect and control the ways in which
     * this may occur.
     *
     * Normally, the first option when joining a task that is not done
     * is to try to take it from the local queue and run it. Method
     * tryRemoveAndExec tries to do so.  For tasks with any form of
     * subtasks that must be completed first, we try to locate these
     * subtasks and run them as well. This is easy when local, but
     * when stolen, steal-backs are restricted to the same rules as
     * stealing (polling), which requires additional bookkeeping and
     * scanning. This cost is still very much worthwhile because of
     * its impact on task scheduling and resource control.
     *
     * The two methods for finding and executing subtasks vary in
     * details.  The algorithm in helpJoin entails a form of "linear
     * helping".  Each worker records (in field "source") the index of
     * the internal queue from which it last stole a task. (Note:
     * because chains cannot include even-numbered external queues,
     * they are ignored, and 0 is an OK default. However, the source
     * field is set anyway, or eventually to DROPPED, to ensure
     * volatile memory synchronization effects.) The scan in method
     * helpJoin uses these markers to try to find a worker to help
     * (i.e., steal back a task from and execute it) that could make
     * progress toward completion of the actively joined task.  Thus,
     * the joiner executes a task that would be on its own local deque
     * if the to-be-joined task had not been stolen. This is a
     * conservative variant of the approach described in Wagner &
     * Calder "Leapfrogging: a portable technique for implementing
     * efficient futures" SIGPLAN Notices, 1993
     * (http://portal.acm.org/citation.cfm?id=155354). It differs
     * mainly in that we only record queues, not full dependency
     * links.  This requires a linear scan of the queues to locate
     * stealers, but isolates cost to when it is needed, rather than
     * adding to per-task overhead.  For CountedCompleters, the
     * analogous method helpComplete doesn't need stealer-tracking,
     * but requires a similar (but simpler) check of completion
     * chains.
     *
     * In either case, searches can fail to locate stealers when
     * stalls delay recording sources or issuing subtasks. We avoid
     * some of these cases by using snapshotted values of ctl as a
     * check that the numbers of workers are not changing, along with
     * rescans to deal with contention and stalls.  But even when
     * accurately identified, stealers might not ever produce a task
     * that the joiner can in turn help with.
     *
     * Related method helpAsyncBlocker does not directly rely on
     * subtask structure, but instead avoids or postpones blocking of
     * tagged tasks (CompletableFuture.AsynchronousCompletionTask) by
     * executing other asyncs that can be processed in any order.
     * This is currently invoked only in non-join-based blocking
     * contexts from classes CompletableFuture and
     * SubmissionPublisher, that could be further generalized.
     *
     * When any of the above fail to avoid blocking, we rely on
     * "compensation" -- an indirect form of context switching that
     * either activates an existing worker to take the place of the
     * blocked one, or expands the number of workers.
     *
     * Compensation does not by default aim to keep exactly the target
     * parallelism number of unblocked threads running at any given
     * time. Some previous versions of this class employed immediate
     * compensations for any blocked join. However, in practice, the
     * vast majority of blockages are transient byproducts of GC and
     * other JVM or OS activities that are made worse by replacement
     * by causing longer-term oversubscription. These are inevitable
     * without (unobtainably) perfect information about whether worker
     * creation is actually necessary.  False alarms are common enough
     * to negatively impact performance, so compensation is by default
     * attempted only when it appears possible that the pool could
     * stall due to lack of any unblocked workers.  However, we allow
     * users to override defaults using the long form of the
     * ForkJoinPool constructor. The compensation mechanism may also
     * be bounded.  Bounds for the commonPool better enable JVMs to
     * cope with programming errors and abuse before running out of
     * resources to do so.
     *
     * The ManagedBlocker extension API can't use helping so relies
     * only on compensation in method awaitBlocker. This API was
     * designed to highlight the uncertainty of compensation decisions
     * by requiring implementation of method isReleasable to abort
     * compensation during attempts to obtain a stable snapshot. But
     * users now rely upon the fact that if isReleasable always
     * returns false, the API can be used to obtain precautionary
     * compensation, which is sometimes the only reasonable option
     * when running unknown code in tasks; which is now supported more
     * simply (see method beginCompensatedBlock).
     *
     * Common Pool
     * ===========
     *
     * The static common pool always exists after static
     * initialization.  Since it (or any other created pool) need
     * never be used, we minimize initial construction overhead and
     * footprint to the setup of about a dozen fields, although with
     * some System property parsing properties are set. The common pool is
     * distinguished by having a null workerNamePrefix (which is an
     * odd convention, but avoids the need to decode status in factory
     * classes).  It also has PRESET_SIZE config set if parallelism
     * was configured by system property.
     *
     * When external threads use the common pool, they can perform
     * subtask processing (see helpComplete and related methods) upon
     * joins, unless they are submitted using ExecutorService
     * submission methods, which implicitly disallow this.  This
     * caller-helps policy makes it sensible to set common pool
     * parallelism level to one (or more) less than the total number
     * of available cores, or even zero for pure caller-runs. External
     * threads waiting for joins first check the common pool for their
     * task, which fails quickly if the caller did not fork to common
     * pool.
     *
     * Guarantees for common pool parallelism zero are limited to
     * tasks that are joined by their callers in a tree-structured
     * fashion or use CountedCompleters (as is true for jdk
     * parallelStreams). Support infiltrates several methods,
     * including those that retry helping steps until we are sure that
     * none apply if there are no workers. To deal with conflicting
     * requirements, uses of the commonPool that require async because
     * caller-runs need not apply, ensure threads are enabled (by
     * setting parallelism) via method asyncCommonPool before
     * proceeding. (In principle, overriding zero parallelism needs to
     * ensure at least one worker, but due to other backward
     * compatibility contraints, ensures two.)
     *
     * As a more appropriate default in managed environments, unless
     * overridden by system properties, we use workers of subclass
     * InnocuousForkJoinWorkerThread for the commonPool.  These
     * workers do not belong to any user-defined ThreadGroup, and
     * clear all ThreadLocals and reset the ContextClassLoader before
     * (re)activating to execute top-level tasks.  The associated
     * mechanics may be JVM-dependent and must access particular
     * Thread class fields to achieve this effect.
     *
     * InterruptibleTasks
     * ====================
     *
     * Regular ForkJoinTasks manage task cancellation (method cancel)
     * independently from the interrupt status of threads running
     * tasks.  Interrupts are issued internally only while
     * terminating, to wake up workers and cancel queued tasks.  By
     * default, interrupts are cleared only when necessary to ensure
     * that calls to LockSupport.park do not loop indefinitely (park
     * returns immediately if the current thread is interrupted).
     *
     * To comply with ExecutorService specs, we use subclasses of
     * abstract class InterruptibleTask for tasks that require
     * stronger interruption and cancellation guarantees.  External
     * submitters never run these tasks, even if in the common pool
     * (as indicated by ForkJoinTask.noUserHelp status bit).
     * InterruptibleTasks include a "runner" field (implemented
     * similarly to FutureTask) to support cancel(true).  Upon pool
     * shutdown, runners are interrupted so they can cancel. Since
     * external joining callers never run these tasks, they must await
     * cancellation by others, which can occur along several different
     * paths. The inability to rely on caller-runs may also require
     * extra signalling (resulting in scanning and contention) so is
     * done only conditionally in methods push and runworker.
     *
     * Across these APIs, rules for reporting exceptions for tasks
     * with results accessed via join() differ from those via get(),
     * which differ from those invoked using pool submit methods by
     * non-workers (which comply with Future.get() specs). Internal
     * usages of ForkJoinTasks ignore interrupt status when executing
     * or awaiting completion.  Otherwise, reporting task results or
     * exceptions is preferred to throwing InterruptedExceptions,
     * which are in turn preferred to timeouts. Similarly, completion
     * status is preferred to reporting cancellation.  Cancellation is
     * reported as an unchecked exception by join(), and by worker
     * calls to get(), but is otherwise wrapped in a (checked)
     * ExecutionException.
     *
     * Worker Threads cannot be VirtualThreads, as enforced by
     * requiring ForkJoinWorkerThreads in factories.  There are
     * several constructions relying on this.  However as of this
     * writing, virtual thread bodies are by default run as some form
     * of InterruptibleTask.
     *
     * DelayScheduler
     * ================
     *
     * This class supports ScheduledExecutorService methods by
     * creating and starting a DelayScheduler on first use of these
     * methods (via startDelayScheduler). The scheduler operates
     * independently in its own thread, relaying tasks to the pool to
     * execute when their delays elapse (see method
     * executeEnabledScheduledTask).  The only other interactions with
     * the delayScheduler are to control shutdown and maintain
     * shutdown-related policies in methods quiescent() and
     * tryTerminate(). In particular, processing must deal with cases
     * in which tasks are submitted before shutdown, but not enabled
     * until afterwards, in which case they must bypass some screening
     * to be allowed to run. Conversely, the DelayScheduler checks
     * runState status and when enabled, completes termination, using
     * only methods shutdownStatus and tryStopIfShutdown. All of these
     * methods are final and have signatures referencing
     * DelaySchedulers, so cannot conflict with those of any existing
     * FJP subclasses.
     *
     * Memory placement
     * ================
     *
     * Performance is very sensitive to placement of instances of
     * ForkJoinPool and WorkQueues and their queue arrays, as well as
     * the placement of their fields. Caches misses and contention due
     * to false-sharing have been observed to slow down some programs
     * by more than a factor of four. Effects may vary across initial
     * memory configuarations, applications, and different garbage
     * collectors and GC settings, so there is no perfect solution.
     * Too much isolation may generate more cache misses in common
     * cases (because some fields snd slots are usually read at the
     * same time). The @Contended annotation provides only rough
     * control (for good reason). Similarly for relying on fields
     * being placed in size-sorted declaration order.
     *
     * We isolate the ForkJoinPool.ctl field that otherwise causes the
     * most false-sharing misses with respect to other fields. Also,
     * ForkJoinPool fields are ordered such that fields less prone to
     * contention effects are first, offsetting those that otherwise
     * would be, while also reducing total footprint vs using
     * multiple @Contended regions, which tends to slow down
     * less-contended applications. To help arrange this, some
     * non-reference fields are declared as "long" even when ints or
     * shorts would suffice.  For class WorkQueue, an
     * embedded @Contended region segregates fields most heavily
     * updated by owners from those most commonly read by stealers or
     * other management.
     *
     * Initial sizing and resizing of WorkQueue arrays is an even more
     * delicate tradeoff because the best strategy systematically
     * varies across garbage collectors. Small arrays are better for
     * locality and reduce GC scan time, but large arrays reduce both
     * direct false-sharing and indirect cases due to GC bookkeeping
     * (cardmarks etc), and reduce the number of resizes, which are
     * not especially fast because they require atomic transfers.
     * Currently, arrays for workers are initialized to be just large
     * enough to avoid resizing in most tree-structured tasks, but
     * larger for external queues where both false-sharing problems
     * and the need for resizing are more common. (Maintenance note:
     * any changes in fields, queues, or their uses, or JVM layout
     * policies, must be accompanied by re-evaluation of these
     * placement and sizing decisions.)
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
     * with very few invariants. All atomic task slot updates use
     * Unsafe operations requiring offset positions, not indices, as
     * computed by method slotOffset. All fields are read into locals
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
     * effectiveness) JVMs about where (not) to place safepoints. All
     * public methods screen arguments (mainly null checks) before
     * creating or executing tasks.
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
     * (1) Static configuration constants
     * (2) Static utility functions
     * (3) Nested (static) classes
     * (4) Fields, along with constants used when unpacking some of them
     * (5) Internal control methods
     * (6) Callbacks and other support for ForkJoinTask methods
     * (7) Exported methods
     * (8) Static block initializing statics in minimally dependent order
     *
     */

    // static configuration constants

    /**
     * Default idle timeout value (in milliseconds) for idle threads
     * to park waiting for new work before terminating.
     */
    static final long DEFAULT_KEEPALIVE = 60_000L;

    /**
     * Undershoot tolerance for idle timeouts, also serving as the
     * minimum allowed timeout value.
     */
    static final long TIMEOUT_SLOP = 20L;

    /**
     * The default value for common pool maxSpares.  Overridable using
     * the "java.util.concurrent.ForkJoinPool.common.maximumSpares"
     * system property.  The default value is far in excess of normal
     * requirements, but also far short of maximum capacity and typical OS
     * thread limits, so allows JVMs to catch misuse/abuse before
     * running out of resources needed to do so.
     */
    static final int DEFAULT_COMMON_MAX_SPARES = 256;

    /**
     * Initial capacity of work-stealing queue array for workers.
     * Must be a power of two, at least 2. See above.
     */
    static final int INITIAL_QUEUE_CAPACITY = 1 << 6;

    /**
     * Initial capacity of work-stealing queue array for external queues.
     * Must be a power of two, at least 2. See above.
     */
    static final int INITIAL_EXTERNAL_QUEUE_CAPACITY = 1 << 9;

    // conversions among short, int, long
    static final int  SMASK           = 0xffff;      // (unsigned) short bits
    static final long LMASK           = 0xffffffffL; // lower 32 bits of long
    static final long UMASK           = ~LMASK;      // upper 32 bits

    // masks and sentinels for queue indices
    static final int MAX_CAP          = 0x7fff;   // max # workers
    static final int EXTERNAL_ID_MASK = 0x3ffe;   // max external queue id
    static final int INVALID_ID       = 0x4000;   // unused external queue id

    // pool.runState bits
    static final long STOP            = 1L <<  0;   // terminating
    static final long SHUTDOWN        = 1L <<  1;   // terminate when quiescent
    static final long CLEANED         = 1L <<  2;   // stopped and queues cleared
    static final long TERMINATED      = 1L <<  3;   // only set if STOP also set
    static final long RS_LOCK         = 1L <<  4;   // lowest seqlock bit

    // spin/sleep limits for runState locking and elsewhere
    static final int SPIN_WAITS       = 1 <<  7;   // max calls to onSpinWait
    static final int MIN_SLEEP        = 1 << 10;   // approx 1 usec as nanos
    static final int MAX_SLEEP        = 1 << 20;   // approx 1 sec  as nanos

    // {pool, workQueue} config bits
    static final int FIFO             = 1 << 0;   // fifo queue or access mode
    static final int CLEAR_TLS        = 1 << 1;   // set for Innocuous workers
    static final int PRESET_SIZE      = 1 << 2;   // size was set by property

    // others
    static final int DROPPED          = 1 << 16;  // removed from ctl counts
    static final int UNCOMPENSATE     = 1 << 16;  // tryCompensate return
    static final int IDLE             = 1 << 16;  // phase seqlock/version count
    static final int MIN_QUEUES_SIZE  = 1 << 4;   // ensure external slots

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

    // Release counts
    static final int  RC_SHIFT = 48;
    static final long RC_UNIT  = 0x0001L << RC_SHIFT;
    static final long RC_MASK  = 0xffffL << RC_SHIFT;
    // Total counts
    static final int  TC_SHIFT = 32;
    static final long TC_UNIT  = 0x0001L << TC_SHIFT;
    static final long TC_MASK  = 0xffffL << TC_SHIFT;

    /*
     * All atomic operations on task arrays (queues) use Unsafe
     * operations that take array offsets versus indices, based on
     * array base and shift constants established during static
     * initialization.
     */
    static final long ABASE;
    static final int  ASHIFT;

    // Static utilities

    /**
     * Returns the array offset corresponding to the given index for
     * Unsafe task queue operations
     */
    static long slotOffset(int index) {
        return ((long)index << ASHIFT) + ABASE;
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
            return ((pool.workerNamePrefix == null) ? // is commonPool
                    new ForkJoinWorkerThread.InnocuousForkJoinWorkerThread(pool) :
                    new ForkJoinWorkerThread(null, pool, true, false));
        }
    }

    /**
     * Queues supporting work-stealing as well as external task
     * submission. See above for descriptions and algorithms.
     */
    static final class WorkQueue {
        // fields declared in order of their likely layout on most VMs
        final ForkJoinWorkerThread owner; // null if shared
        ForkJoinTask<?>[] array;   // the queued tasks; power of 2 size
        int base;                  // index of next slot for poll
        final int config;          // mode bits

        // fields otherwise causing more unnecessary false-sharing cache misses
        @jdk.internal.vm.annotation.Contended("w")
        int top;                   // index of next slot for push
        @jdk.internal.vm.annotation.Contended("w")
        volatile int phase;        // versioned active status
        @jdk.internal.vm.annotation.Contended("w")
        int stackPred;             // pool stack (ctl) predecessor link
        @jdk.internal.vm.annotation.Contended("w")
        volatile int source;       // source queue id (or DROPPED)
        @jdk.internal.vm.annotation.Contended("w")
        int nsteals;               // number of steals from other queues
        @jdk.internal.vm.annotation.Contended("w")
        volatile int parking;      // nonzero if parked in awaitWork

        // Support for atomic operations
        private static final Unsafe U;
        private static final long PHASE;
        private static final long BASE;
        private static final long TOP;
        private static final long ARRAY;

        final void updateBase(int v) {
            U.putIntVolatile(this, BASE, v);
        }
        final void updateTop(int v) {
            U.putIntOpaque(this, TOP, v);
        }
        final void updateArray(ForkJoinTask<?>[] a) {
            U.getAndSetReference(this, ARRAY, a);
        }
        final void unlockPhase() {
            U.getAndAddInt(this, PHASE, IDLE);
        }
        final boolean tryLockPhase() {    // seqlock acquire
            int p;
            return (((p = phase) & IDLE) != 0 &&
                    U.compareAndSetInt(this, PHASE, p, p + IDLE));
        }

        /**
         * Constructor. For internal queues, most fields are initialized
         * upon thread start in pool.registerWorker.
         */
        WorkQueue(ForkJoinWorkerThread owner, int id, int cfg,
                  boolean clearThreadLocals) {
            array = new ForkJoinTask<?>[owner == null ?
                                        INITIAL_EXTERNAL_QUEUE_CAPACITY :
                                        INITIAL_QUEUE_CAPACITY];
            this.owner = owner;
            this.config = (clearThreadLocals) ? cfg | CLEAR_TLS : cfg;
        }

        /**
         * Returns an exportable index (used by ForkJoinWorkerThread).
         */
        final int getPoolIndex() {
            return (phase & 0xffff) >>> 1; // ignore odd/even tag bit
        }

        /**
         * Returns the approximate number of tasks in the queue.
         */
        final int queueSize() {
            int unused = phase;             // for ordering effect
            return Math.max(top - base, 0); // ignore transient negative
        }

        /**
         * Pushes a task. Called only by owner or if already locked
         *
         * @param task the task; no-op if null
         * @param pool the pool to signal if was previously empty, else null
         * @param internal if caller owns this queue
         * @throws RejectedExecutionException if array could not be resized
         */
        final void push(ForkJoinTask<?> task, ForkJoinPool pool, boolean internal) {
            int s = top, b = base, m, cap, room; ForkJoinTask<?>[] a;
            if ((a = array) != null && (cap = a.length) > 0 && // else disabled
                task != null) {
                int pk = task.noUserHelp() + 1;             // prev slot offset
                if ((room = (m = cap - 1) - (s - b)) >= 0) {
                    top = s + 1;
                    long pos = slotOffset(m & s);
                    if (!internal)
                        U.putReference(a, pos, task);       // inside lock
                    else
                        U.getAndSetReference(a, pos, task); // fully fenced
                    if (room == 0)                          // resize
                        growArray(a, cap, s);
                }
                if (!internal)
                    unlockPhase();
                if (room < 0)
                    throw new RejectedExecutionException("Queue capacity exceeded");
                if ((room == 0 || a[m & (s - pk)] == null) &&
                    pool != null)
                    pool.signalWork();   // may have appeared empty
            }
        }

        /**
         * Resizes the queue array unless out of memory.
         * @param a old array
         * @param cap old array capacity
         * @param s current top
         */
        private void growArray(ForkJoinTask<?>[] a, int cap, int s) {
            int newCap = cap << 1;
            if (a != null && a.length == cap && cap > 0 && newCap > 0) {
                ForkJoinTask<?>[] newArray = null;
                try {
                    newArray = new ForkJoinTask<?>[newCap];
                } catch (OutOfMemoryError ex) {
                }
                if (newArray != null) {               // else throw on next push
                    int mask = cap - 1, newMask = newCap - 1;
                    for (int k = s, j = cap; j > 0; --j, --k) {
                        ForkJoinTask<?> u;            // poll old, push to new
                        if ((u = (ForkJoinTask<?>)U.getAndSetReference(
                                 a, slotOffset(k & mask), null)) == null)
                            break;                    // lost to pollers
                        newArray[k & newMask] = u;
                    }
                    updateArray(newArray);           // fully fenced
                }
            }
        }

        /**
         * Takes next task, if one exists, in order specified by mode,
         * so acts as either local-pop or local-poll. Called only by owner.
         * @param fifo nonzero if FIFO mode
         */
        private ForkJoinTask<?> nextLocalTask(int fifo) {
            ForkJoinTask<?> t = null;
            ForkJoinTask<?>[] a = array;
            int b = base, p = top, cap;
            if (p - b > 0 && a != null && (cap = a.length) > 0) {
                for (int m = cap - 1, s, nb;;) {
                    if (fifo == 0 || (nb = b + 1) == p) {
                        if ((t = (ForkJoinTask<?>)U.getAndSetReference(
                                 a, slotOffset(m & (s = p - 1)), null)) != null)
                            updateTop(s);       // else lost race for only task
                        break;
                    }
                    if ((t = (ForkJoinTask<?>)U.getAndSetReference(
                             a, slotOffset(m & b), null)) != null) {
                        updateBase(nb);
                        break;
                    }
                    while (b == (b = U.getIntAcquire(this, BASE)))
                        Thread.onSpinWait();    // spin to reduce memory traffic
                    if (p - b <= 0)
                        break;
                }
            }
            return t;
        }

        /**
         * Takes next task, if one exists, using configured mode.
         * (Always internal, never called for Common pool.)
         */
        final ForkJoinTask<?> nextLocalTask() {
            return nextLocalTask(config & FIFO);
        }

        /**
         * Pops the given task only if it is at the current top.
         * @param task the task. Caller must ensure non-null.
         * @param internal if caller owns this queue
         */
        final boolean tryUnpush(ForkJoinTask<?> task, boolean internal) {
            boolean taken = false;
            ForkJoinTask<?>[] a = array;
            int p = top, s = p - 1, cap; long k;
            if (a != null && (cap = a.length) > 0 &&
                U.getReference(a, k = slotOffset((cap - 1) & s)) == task &&
                (internal || tryLockPhase())) {
                if (top == p && U.compareAndSetReference(a, k, task, null)) {
                    taken = true;
                    updateTop(s);
                }
                if (!internal)
                    unlockPhase();
            }
            return taken;
        }

        /**
         * Returns next task, if one exists, in order specified by mode.
         */
        final ForkJoinTask<?> peek() {
            ForkJoinTask<?>[] a = array;
            int b = base, cfg = config, p = top, cap;
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
         * Polls for a task. Used only by non-owners.
         */
        final ForkJoinTask<?> poll() {
            for (int pb = -1, b; ; pb = b) {       // track progress
                ForkJoinTask<?> t; int cap, nb; long k; ForkJoinTask<?>[] a;
                if ((a = array) == null || (cap = a.length) <= 0)
                    break;
                t = (ForkJoinTask<?>)U.getReferenceAcquire(
                    a, k = slotOffset((cap - 1) & (b = base)));
                Object u = U.getReference(         // next slot
                    a, slotOffset((cap - 1) & (nb = b + 1)));
                if (base != b)                     // inconsistent
                    ;
                else if (t == null) {
                    if (u == null && top - b <= 0)
                        break;                     // empty
                    if (pb == b)
                        Thread.onSpinWait();       // stalled
                }
                else if (U.compareAndSetReference(a, k, t, null)) {
                    updateBase(nb);
                    return t;
                }
            }
            return null;
        }

        // specialized execution methods

        /**
         * Runs the given task, as well as remaining local tasks.
         */
        final void topLevelExec(ForkJoinTask<?> task, int fifo) {
            while (task != null) {
                task.doExec();
                task = nextLocalTask(fifo);
            }
        }

        /**
         * Deep form of tryUnpush: Traverses from top and removes and
         * runs task if present.
         */
        final void tryRemoveAndExec(ForkJoinTask<?> task, boolean internal) {
            ForkJoinTask<?>[] a = array;
            int b = base, p = top, s = p - 1, d = p - b, cap;
            if (a != null && (cap = a.length) > 0) {
                for (int m = cap - 1, i = s; d > 0; --i, --d) {
                    long k; boolean taken;
                    ForkJoinTask<?> t = (ForkJoinTask<?>)U.getReference(
                        a, k = slotOffset(i & m));
                    if (t == null)
                        break;
                    if (t == task) {
                        if (!internal && !tryLockPhase())
                            break;                  // fail if locked
                        if (taken =
                            (top == p &&
                             U.compareAndSetReference(a, k, task, null))) {
                            if (i == s)             // act as pop
                                updateTop(s);
                            else if (i == base)     // act as poll
                                updateBase(i + 1);
                            else {                  // swap with top
                                U.putReferenceVolatile(
                                    a, k, (ForkJoinTask<?>)
                                    U.getAndSetReference(
                                        a, slotOffset(s & m), null));
                                updateTop(s);
                            }
                        }
                        if (!internal)
                            unlockPhase();
                        if (taken)
                            task.doExec();
                        break;
                    }
                }
            }
        }

        /**
         * Tries to pop and run tasks within the target's computation
         * until done, not found, or limit exceeded.
         *
         * @param task root of computation
         * @param limit max runs, or zero for no limit
         * @return task status if known to be done
         */
        final int helpComplete(ForkJoinTask<?> task, boolean internal, int limit) {
            int status = 0;
            if (task != null) {
                outer: for (;;) {
                    ForkJoinTask<?>[] a; boolean taken; Object o;
                    int stat, p, s, cap;
                    if ((stat = task.status) < 0) {
                        status = stat;
                        break;
                    }
                    if ((a = array) == null || (cap = a.length) <= 0)
                        break;
                    long k = slotOffset((cap - 1) & (s = (p = top) - 1));
                    if (!((o = U.getReference(a, k)) instanceof CountedCompleter))
                        break;
                    CountedCompleter<?> t = (CountedCompleter<?>)o, f = t;
                    for (int steps = cap;;) {       // bound path
                        if (f == task)
                            break;
                        if ((f = f.completer) == null || --steps == 0)
                            break outer;
                    }
                    if (!internal && !tryLockPhase())
                        break;
                    if (taken =
                        (top == p &&
                         U.compareAndSetReference(a, k, t, null)))
                        updateTop(s);
                    if (!internal)
                        unlockPhase();
                    if (!taken)
                        break;
                    t.doExec();
                    if (limit != 0 && --limit == 0)
                        break;
                }
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
            for (;;) {
                ForkJoinTask<?> t; ForkJoinTask<?>[] a; int b, cap; long k;
                if ((a = array) == null || (cap = a.length) <= 0)
                    break;
                t = (ForkJoinTask<?>)U.getReferenceAcquire(
                    a, k = slotOffset((cap - 1) & (b = base)));
                if (t == null) {
                    if (top - b <= 0)
                        break;
                }
                else if (!(t instanceof CompletableFuture
                           .AsynchronousCompletionTask))
                    break;
                if (blocker != null && blocker.isReleasable())
                    break;
                if (base == b && t != null &&
                    U.compareAndSetReference(a, k, t, null)) {
                    updateBase(b + 1);
                    t.doExec();
                }
            }
        }

        // misc

        /**
         * Cancels all local tasks. Called only by owner.
         */
        final void cancelTasks() {
            for (ForkJoinTask<?> t; (t = nextLocalTask(0)) != null; ) {
                try {
                    t.cancel(false);
                } catch (Throwable ignore) {
                }
            }
        }

        /**
         * Returns true if internal and not known to be blocked.
         */
        final boolean isApparentlyUnblocked() {
            Thread wt; Thread.State s;
            return ((wt = owner) != null && (phase & IDLE) != 0 &&
                    (s = wt.getState()) != Thread.State.BLOCKED &&
                    s != Thread.State.WAITING &&
                    s != Thread.State.TIMED_WAITING);
        }

        static {
            U = Unsafe.getUnsafe();
            Class<WorkQueue> klass = WorkQueue.class;
            PHASE = U.objectFieldOffset(klass, "phase");
            BASE = U.objectFieldOffset(klass, "base");
            TOP = U.objectFieldOffset(klass, "top");
            ARRAY = U.objectFieldOffset(klass, "array");
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
     * For VirtualThread intrinsics
     */
    private static final JavaLangAccess JLA;

    // fields declared in order of their likely layout on most VMs
    volatile CountDownLatch termination; // lazily constructed
    final Predicate<? super ForkJoinPool> saturate;
    final ForkJoinWorkerThreadFactory factory;
    final UncaughtExceptionHandler ueh;  // per-worker UEH
    final SharedThreadContainer container;
    final String workerNamePrefix;       // null for common pool
    final String poolName;
    volatile DelayScheduler delayScheduler;  // lazily constructed
    WorkQueue[] queues;                  // main registry
    volatile long runState;              // versioned, lockable
    final long keepAlive;                // milliseconds before dropping if idle
    final long config;                   // static configuration bits
    volatile long stealCount;            // collects worker nsteals
    volatile long threadIds;             // for worker thread names

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
    private static final long TERMINATION;
    private static final Object POOLIDS_BASE;
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
    private long incrementThreadIds() {
        return U.getAndAddLong(this, THREADIDS, 1L);
    }
    private static int getAndAddPoolIds(int x) {
        return U.getAndAddInt(POOLIDS_BASE, POOLIDS, x);
    }
    private int getAndSetParallelism(int v) {
        return U.getAndSetInt(this, PARALLELISM, v);
    }
    private int getParallelismOpaque() {
        return U.getIntOpaque(this, PARALLELISM);
    }
    private CountDownLatch cmpExTerminationSignal(CountDownLatch x) {
        return (CountDownLatch)
            U.compareAndExchangeReference(this, TERMINATION, null, x);
    }

    // runState operations

    private long getAndBitwiseOrRunState(long v) { // for status bits
        return U.getAndBitwiseOrLong(this, RUNSTATE, v);
    }
    private boolean casRunState(long c, long v) {
        return U.compareAndSetLong(this, RUNSTATE, c, v);
    }
    private void unlockRunState() {              // increment lock bit
        U.getAndAddLong(this, RUNSTATE, RS_LOCK);
    }
    private long lockRunState() {                // lock and return current state
        long s, u;                               // locked when RS_LOCK set
        if (((s = runState) & RS_LOCK) == 0L && casRunState(s, u = s + RS_LOCK))
            return u;
        else
            return spinLockRunState();
    }
    private long spinLockRunState() {            // spin/sleep
        for (int waits = 0;;) {
            long s, u;
            if (((s = runState) & RS_LOCK) == 0L) {
                if (casRunState(s, u = s + RS_LOCK))
                    return u;
                waits = 0;
            } else if (waits < SPIN_WAITS) {
                ++waits;
                Thread.onSpinWait();
            } else {
                if (waits < MIN_SLEEP)
                    waits = MIN_SLEEP;
                LockSupport.parkNanos(this, (long)waits);
                if (waits < MAX_SLEEP)
                    waits <<= 1;
            }
        }
    }

    static boolean poolIsStopping(ForkJoinPool p) { // Used by ForkJoinTask
        return p != null && (p.runState & STOP) != 0L;
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
        SharedThreadContainer ctr = container;
        Throwable ex = null;
        ForkJoinWorkerThread wt = null;
        try {
            if ((runState & STOP) == 0L &&  // avoid construction if terminating
                fac != null && (wt = fac.newThread(this)) != null) {
                if (ctr != null)
                    ctr.start(wt);
                else
                    wt.start();
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
     * Finishes initializing and records internal queue.
     *
     * @param w caller's WorkQueue
     */
    final void registerWorker(WorkQueue w) {
        if (w != null && (runState & STOP) == 0L) {
            ThreadLocalRandom.localInit();
            int seed = w.stackPred = ThreadLocalRandom.getProbe();
            int phaseSeq = seed & ~((IDLE << 1) - 1); // initial phase tag
            int id = ((seed << 1) | 1) & SMASK; // base of linear-probe-like scan
            long stop = lockRunState() & STOP;
            try {
                WorkQueue[] qs; int n;
                if (stop == 0L && (qs = queues) != null && (n = qs.length) > 0) {
                    for (int k = n, m = n - 1;  ; id += 2) {
                        if (qs[id &= m] == null)
                            break;
                        if ((k -= 2) <= 0) {
                            id |= n;
                            break;
                        }
                    }
                    w.phase = id | phaseSeq;    // now publishable
                    if (id < n)
                        qs[id] = w;
                    else {                      // expand
                        int an = n << 1, am = an - 1;
                        WorkQueue[] as = new WorkQueue[an];
                        as[id & am] = w;
                        for (int j = 1; j < n; j += 2)
                            as[j] = qs[j];
                        for (int j = 0; j < n; j += 2) {
                            WorkQueue q;        // shared queues may move
                            if ((q = qs[j]) != null)
                                as[q.phase & EXTERNAL_ID_MASK & am] = q;
                        }
                        U.storeFence();         // fill before publish
                        queues = as;
                    }
                }
            } finally {
                unlockRunState();
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
        WorkQueue w = null;                // null if not created
        int phase = 0;                     // 0 if not registered
        if (wt != null && (w = wt.workQueue) != null &&
            (phase = w.phase) != 0 && (phase & IDLE) != 0)
            releaseWaiters();              // ensure released
        if (w == null || w.source != DROPPED) {
            long c = ctl;                  // decrement counts
            do {} while (c != (c = compareAndExchangeCtl(
                                   c, ((RC_MASK & (c - RC_UNIT)) |
                                       (TC_MASK & (c - TC_UNIT)) |
                                       (LMASK & c)))));
        }
        if (phase != 0 && w != null) {     // remove index unless terminating
            long ns = w.nsteals & 0xffffffffL;
            if ((runState & STOP) == 0L) {
                WorkQueue[] qs; int n, i;
                if ((lockRunState() & STOP) == 0L &&
                    (qs = queues) != null && (n = qs.length) > 0 &&
                    qs[i = phase & SMASK & (n - 1)] == w) {
                    qs[i] = null;
                    stealCount += ns;      // accumulate steals
                }
                unlockRunState();
            }
        }
        if ((tryTerminate(false, false) & STOP) == 0L &&
            phase != 0 && w != null && w.source != DROPPED) {
            signalWork();                  // possibly replace
            w.cancelTasks();               // clean queue
        }
        if (ex != null)
            ForkJoinTask.rethrow(ex);
    }

    /**
     * Releases an idle worker, or creates one if not enough exist.
     */
    final void signalWork() {
        int pc = parallelism;
        for (long c = ctl;;) {
            WorkQueue[] qs = queues;
            long ac = (c + RC_UNIT) & RC_MASK, nc;
            int sp = (int)c, i = sp & SMASK;
            if ((short)(c >>> RC_SHIFT) >= pc)
                break;
            if (qs == null)
                break;
            if (qs.length <= i)
                break;
            WorkQueue w = qs[i], v = null;
            if (sp == 0) {
                if ((short)(c >>> TC_SHIFT) >= pc)
                    break;
                nc = ((c + TC_UNIT) & TC_MASK);
            }
            else if ((v = w) == null)
                break;
            else
                nc = (v.stackPred & LMASK) | (c & TC_MASK);
            if (c == (c = compareAndExchangeCtl(c, nc | ac))) {
                if (v == null)
                    createWorker();
                else {
                    v.phase = sp;
                    if (v.parking != 0)
                        U.unpark(v.owner);
                }
                break;
            }
        }
    }

    /**
     * Releases all waiting workers. Called only during shutdown.
     */
    private void releaseWaiters() {
        for (long c = ctl;;) {
            WorkQueue[] qs; WorkQueue v; int sp, i;
            if ((sp = (int)c) == 0 || (qs = queues) == null ||
                qs.length <= (i = sp & SMASK) || (v = qs[i]) == null)
                break;
            if (c == (c = compareAndExchangeCtl(
                          c, ((UMASK & (c + RC_UNIT)) | (c & TC_MASK) |
                              (v.stackPred & LMASK))))) {
                v.phase = sp;
                if (v.parking != 0)
                    U.unpark(v.owner);
            }
        }
    }

    /**
     * Internal version of isQuiescent and related functionality.
     * @return positive if stopping, nonnegative if terminating or all
     * workers are inactive and submission queues are empty and
     * unlocked; if so, setting STOP if shutdown is enabled
     */
    private int quiescent() {
        for (;;) {
            long phaseSum = 0L;
            boolean swept = false;
            for (long e, prevRunState = 0L; ; prevRunState = e) {
                DelayScheduler ds;
                long c = ctl;
                if (((e = runState) & STOP) != 0L)
                    return 1;                             // terminating
                else if ((c & RC_MASK) > 0L)
                    return -1;                            // at least one active
                else if (!swept || e != prevRunState || (e & RS_LOCK) != 0) {
                    long sum = c;
                    WorkQueue[] qs = queues;
                    int n = (qs == null) ? 0 : qs.length;
                    for (int i = 0; i < n; ++i) {         // scan queues
                        WorkQueue q;
                        if ((q = qs[i]) != null) {
                            int p = q.phase, s = q.top, b = q.base;
                            sum += (p & 0xffffffffL) | ((long)b << 32);
                            if ((p & IDLE) == 0 || s - b > 0)
                                return -1;
                        }
                    }
                    swept = (phaseSum == (phaseSum = sum));
                }
                else if ((e & SHUTDOWN) == 0)
                    return 0;
                else if ((ds = delayScheduler) != null && !ds.canShutDown())
                    return 0;
                else if (compareAndSetCtl(c, c) && casRunState(e, e | STOP))
                    return 1;                             // enable termination
                else
                    break;                                // restart
            }
        }
    }

    /**
     * Top-level runloop for workers, called by ForkJoinWorkerThread.run.
     * See above for explanation.
     *
     * @param w caller's WorkQueue (may be null on failed initialization)
     */
    final void runWorker(WorkQueue w) {
        if (w != null) {
            int phase = w.phase, r = w.stackPred;     // seed from registerWorker
            int fifo = w.config & FIFO, nsteals = 0, src = -1;
            for (;;) {
                WorkQueue[] qs;
                r ^= r << 13; r ^= r >>> 17; r ^= r << 5; // xorshift
                if ((runState & STOP) != 0L || (qs = queues) == null)
                    break;
                int n = qs.length, i = r, step = (r >>> 16) | 1;
                boolean rescan = false;
                scan: for (int l = n; l > 0; --l, i += step) {  // scan queues
                    int j, cap; WorkQueue q; ForkJoinTask<?>[] a;
                    if ((q = qs[j = i & (n - 1)]) != null &&
                        (a = q.array) != null && (cap = a.length) > 0) {
                        for (int m = cap - 1, pb = -1, b = q.base;;) {
                            ForkJoinTask<?> t; long k;
                            t = (ForkJoinTask<?>)U.getReferenceAcquire(
                                a, k = slotOffset(m & b));
                            if (b != (b = q.base) || t == null ||
                                !U.compareAndSetReference(a, k, t, null)) {
                                if (a[b & m] == null) {
                                    if (rescan)           // end of run
                                        break scan;
                                    if (a[(b + 1) & m] == null &&
                                        a[(b + 2) & m] == null) {
                                        break;            // probably empty
                                    }
                                    if (pb == (pb = b)) { // track progress
                                        rescan = true;    // stalled; reorder scan
                                        break scan;
                                    }
                                }
                            }
                            else {
                                boolean propagate;
                                int nb = q.base = b + 1, prevSrc = src;
                                w.nsteals = ++nsteals;
                                w.source = src = j;       // volatile
                                rescan = true;
                                int nh = t.noUserHelp();
                                if (propagate =
                                    (prevSrc != src || nh != 0) && a[nb & m] != null)
                                    signalWork();
                                w.topLevelExec(t, fifo);
                                if ((b = q.base) != nb && !propagate)
                                    break scan;          // reduce interference
                            }
                        }
                    }
                }
                if (!rescan) {
                    if (((phase = deactivate(w, phase)) & IDLE) != 0)
                        break;
                    src = -1;                            // re-enable propagation
                }
            }
        }
    }

    /**
     * Deactivates and if necessary awaits signal or termination.
     *
     * @param w the worker
     * @param phase current phase
     * @return current phase, with IDLE set if worker should exit
     */
    private int deactivate(WorkQueue w, int phase) {
        if (w == null)                        // currently impossible
            return IDLE;
        int p = phase | IDLE, activePhase = phase + (IDLE << 1);
        long pc = ctl, qc = (activePhase & LMASK) | ((pc - RC_UNIT) & UMASK);
        int sp = w.stackPred = (int)pc;       // set ctl stack link
        w.phase = p;
        if (!compareAndSetCtl(pc, qc))        // try to enqueue
            return w.phase = phase;           // back out on possible signal
        int ac = (short)(qc >>> RC_SHIFT), n; long e; WorkQueue[] qs;
        if (((e = runState) & STOP) != 0L ||
            ((e & SHUTDOWN) != 0L && ac == 0 && quiescent() > 0) ||
            (qs = queues) == null || (n = qs.length) <= 0)
            return IDLE;                      // terminating

        for (int prechecks = Math.min(ac, 2), // reactivation threshold
             k = Math.max(n + (n << 1), SPIN_WAITS << 1);;) {
            WorkQueue q; int cap; ForkJoinTask<?>[] a; long c;
            if (w.phase == activePhase)
                return activePhase;
            if (--k < 0)
                return awaitWork(w, p);       // block, drop, or exit
            if ((q = qs[k & (n - 1)]) == null)
                Thread.onSpinWait();
            else if ((a = q.array) != null && (cap = a.length) > 0 &&
                     a[q.base & (cap - 1)] != null && --prechecks < 0 &&
                     (int)(c = ctl) == activePhase &&
                     compareAndSetCtl(c, (sp & LMASK) | ((c + RC_UNIT) & UMASK)))
                return w.phase = activePhase; // reactivate
        }
    }

    /**
     * Awaits signal or termination.
     *
     * @param w the work queue
     * @param p current phase (known to be idle)
     * @return current phase, with IDLE set if worker should exit
     */
    private int awaitWork(WorkQueue w, int p) {
        if (w != null) {
            ForkJoinWorkerThread t; long deadline;
            if ((w.config & CLEAR_TLS) != 0 && (t = w.owner) != null)
                t.resetThreadLocals();          // clear before reactivate
            if ((ctl & RC_MASK) > 0L)
                deadline = 0L;
            else if ((deadline =
                      (((w.source != INVALID_ID) ? keepAlive : TIMEOUT_SLOP)) +
                      System.currentTimeMillis()) == 0L)
                deadline = 1L;                 // avoid zero
            int activePhase = p + IDLE;
            if ((p = w.phase) != activePhase && (runState & STOP) == 0L) {
                LockSupport.setCurrentBlocker(this);
                w.parking = 1;                 // enable unpark
                while ((p = w.phase) != activePhase) {
                    boolean trimmable = false; int trim;
                    Thread.interrupted();      // clear status
                    if ((runState & STOP) != 0L)
                        break;
                    if (deadline != 0L) {
                        if ((trim = tryTrim(w, p, deadline)) > 0)
                            break;
                        else if (trim < 0)
                            deadline = 0L;
                        else
                            trimmable = true;
                    }
                    U.park(trimmable, deadline);
                }
                w.parking = 0;
                LockSupport.setCurrentBlocker(null);
            }
        }
        return p;
    }

    /**
     * Tries to remove and deregister worker after timeout, and release
     * another to do the same.
     * @return > 0: trimmed, < 0 : not trimmable, else 0
     */
    private int tryTrim(WorkQueue w, int phase, long deadline) {
        long c, nc; int stat, activePhase, vp, i; WorkQueue[] vs; WorkQueue v;
        if ((activePhase = phase + IDLE) != (int)(c = ctl) || w == null)
            stat = -1;                      // no longer ctl top
        else if (deadline - System.currentTimeMillis() >= TIMEOUT_SLOP)
            stat = 0;                       // spurious wakeup
        else if (!compareAndSetCtl(
                     c, nc = ((w.stackPred & LMASK) | (RC_MASK & c) |
                               (TC_MASK & (c - TC_UNIT)))))
            stat = -1;                      // lost race to signaller
        else {
            stat = 1;
            w.source = DROPPED;
            w.phase = activePhase;
            if ((vp = (int)nc) != 0 && (vs = queues) != null &&
                vs.length > (i = vp & SMASK) && (v = vs[i]) != null &&
                compareAndSetCtl(           // try to wake up next waiter
                    nc, ((UMASK & (nc + RC_UNIT)) |
                         (nc & TC_MASK) | (v.stackPred & LMASK)))) {
                v.source = INVALID_ID;      // enable cascaded timeouts
                v.phase = vp;
                U.unpark(v.owner);
            }
        }
        return stat;
    }

    /**
     * Scans for and returns a polled task, if available.  Used only
     * for untracked polls. Begins scan at a random index to avoid
     * systematic unfairness.
     *
     * @param submissionsOnly if true, only scan submission queues
     */
    private ForkJoinTask<?> pollScan(boolean submissionsOnly) {
        if ((runState & STOP) == 0L) {
            WorkQueue[] qs; int n; WorkQueue q; ForkJoinTask<?> t;
            int r = ThreadLocalRandom.nextSecondarySeed();
            if (submissionsOnly)                 // even indices only
                r &= ~1;
            int step = (submissionsOnly) ? 2 : 1;
            if ((qs = queues) != null && (n = qs.length) > 0) {
                for (int i = n; i > 0; i -= step, r += step) {
                    if ((q = qs[r & (n - 1)]) != null &&
                        (t = q.poll()) != null)
                        return t;
                }
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
     * @return UNCOMPENSATE: block then adjust, 0: block, -1 : retry
     */
    private int tryCompensate(long c) {
        Predicate<? super ForkJoinPool> sat;
        long b = config;
        int pc        = parallelism,                    // unpack fields
            minActive = (short)(b >>> RC_SHIFT),
            maxTotal  = (short)(b >>> TC_SHIFT) + pc,
            active    = (short)(c >>> RC_SHIFT),
            total     = (short)(c >>> TC_SHIFT),
            sp        = (int)c,
            stat      = -1;                             // default retry return
        if (sp != 0 && active <= pc) {                  // activate idle worker
            WorkQueue[] qs; WorkQueue v; int i;
            if ((qs = queues) != null && qs.length > (i = sp & SMASK) &&
                (v = qs[i]) != null &&
                compareAndSetCtl(c, (c & UMASK) | (v.stackPred & LMASK))) {
                v.phase = sp;
                if (v.parking != 0)
                    U.unpark(v.owner);
                stat = UNCOMPENSATE;
            }
        }
        else if (active > minActive && total >= pc) {   // reduce active workers
            if (compareAndSetCtl(c, ((c - RC_UNIT) & RC_MASK) | (c & ~RC_MASK)))
                stat = UNCOMPENSATE;
        }
        else if (total < maxTotal && total < MAX_CAP) { // try to expand pool
            long nc = ((c + TC_UNIT) & TC_MASK) | (c & ~TC_MASK);
            if ((runState & STOP) != 0L)                // terminating
                stat = 0;
            else if (compareAndSetCtl(c, nc))
                stat = createWorker() ? UNCOMPENSATE : 0;
        }
        else if (!compareAndSetCtl(c, c))               // validate
            ;
        else if ((sat = saturate) != null && sat.test(this))
            stat = 0;
        else
            throw new RejectedExecutionException(
                "Thread limit exceeded replacing blocked worker");
        return stat;
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
     * @param internal true if w is owned by a ForkJoinWorkerThread
     * @return task status on exit, or UNCOMPENSATE for compensated blocking
     */
    final int helpJoin(ForkJoinTask<?> task, WorkQueue w, boolean internal) {
        if (w != null)
            w.tryRemoveAndExec(task, internal);
        int s = 0;
        if (task != null && (s = task.status) >= 0 && internal && w != null) {
            int wid = w.phase & SMASK, r = wid + 2, wsrc = w.source;
            long sctl = 0L;                             // track stability
            outer: for (boolean rescan = true;;) {
                if ((s = task.status) < 0)
                    break;
                if (!rescan) {
                    if ((runState & STOP) != 0L)
                        break;
                    if (sctl == (sctl = ctl) && (s = tryCompensate(sctl)) >= 0)
                        break;
                }
                rescan = false;
                WorkQueue[] qs = queues;
                int n = (qs == null) ? 0 : qs.length;
                scan: for (int l = n >>> 1; l > 0; --l, r += 2) {
                    int j; WorkQueue q;
                    if ((q = qs[j = r & SMASK & (n - 1)]) != null) {
                        for (;;) {
                            ForkJoinTask<?> t; ForkJoinTask<?>[] a;
                            boolean eligible = false;
                            int sq = q.source, b, cap; long k;
                            if ((a = q.array) == null || (cap = a.length) <= 0)
                                break;
                            t = (ForkJoinTask<?>)U.getReferenceAcquire(
                                a, k = slotOffset((cap - 1) & (b = q.base)));
                            if (t == task)
                                eligible = true;
                            else if (t != null) {       // check steal chain
                                for (int v = sq, d = cap;;) {
                                    WorkQueue p;
                                    if (v == wid) {
                                        eligible = true;
                                        break;
                                    }
                                    if ((v & 1) == 0 || // external or none
                                        --d < 0 ||      // bound depth
                                        (p = qs[v & (n - 1)]) == null)
                                        break;
                                    v = p.source;
                                }
                            }
                            if ((s = task.status) < 0)
                                break outer;            // validate
                            if (q.source == sq && q.base == b &&
                                U.getReference(a, k) == t) {
                                if (!eligible) {        // revisit if nonempty
                                    if (!rescan && t == null && q.top - b > 0)
                                        rescan = true;
                                    break;
                                }
                                if (U.compareAndSetReference(a, k, t, null)) {
                                    q.base = b + 1;
                                    w.source = j;    // volatile write
                                    t.doExec();
                                    w.source = wsrc;
                                    rescan = true;   // restart at index r
                                    break scan;
                                }
                            }
                        }
                    }
                }
            }
        }
        return s;
    }

    /**
     * Version of helpJoin for CountedCompleters.
     *
     * @param task root of computation (only called when a CountedCompleter)
     * @param w caller's WorkQueue
     * @param internal true if w is owned by a ForkJoinWorkerThread
     * @return task status on exit, or UNCOMPENSATE for compensated blocking
     */
    final int helpComplete(ForkJoinTask<?> task, WorkQueue w, boolean internal) {
        int s = 0;
        if (task != null && (s = task.status) >= 0 && w != null) {
            int r = w.phase + 1;                          // for indexing
            long sctl = 0L;                               // track stability
            outer: for (boolean rescan = true, locals = true;;) {
                if (locals && (s = w.helpComplete(task, internal, 0)) < 0)
                    break;
                if ((s = task.status) < 0)
                    break;
                if (!rescan) {
                    if ((runState & STOP) != 0L)
                        break;
                    if (sctl == (sctl = ctl) &&
                        (!internal || (s = tryCompensate(sctl)) >= 0))
                        break;
                }
                rescan = locals = false;
                WorkQueue[] qs = queues;
                int n = (qs == null) ? 0 : qs.length;
                scan: for (int l = n; l > 0; --l, ++r) {
                    int j; WorkQueue q;
                    if ((q = qs[j = r & SMASK & (n - 1)]) != null) {
                        for (;;) {
                            ForkJoinTask<?> t; ForkJoinTask<?>[] a;
                            int b, cap, nb; long k;
                            boolean eligible = false;
                            if ((a = q.array) == null || (cap = a.length) <= 0)
                                break;
                            t = (ForkJoinTask<?>)U.getReferenceAcquire(
                                a, k = slotOffset((cap - 1) & (b = q.base)));
                            if (t instanceof CountedCompleter) {
                                CountedCompleter<?> f = (CountedCompleter<?>)t;
                                for (int steps = cap; steps > 0; --steps) {
                                    if (f == task) {
                                        eligible = true;
                                        break;
                                    }
                                    if ((f = f.completer) == null)
                                        break;
                                }
                            }
                            if ((s = task.status) < 0)    // validate
                                break outer;
                            if (q.base == b) {
                                if (eligible) {
                                    if (U.compareAndSetReference(
                                            a, k, t, null)) {
                                        q.updateBase(b + 1);
                                        t.doExec();
                                        locals = rescan = true;
                                        break scan;
                                    }
                                }
                                else if (U.getReference(a, k) == t) {
                                    if (!rescan && t == null && q.top - b > 0)
                                        rescan = true;    // revisit
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
        return s;
     }

    /**
     * Runs tasks until all workers are inactive and no tasks are
     * found. Rather than blocking when tasks cannot be found, rescans
     * until all others cannot find tasks either.
     *
     * @param nanos max wait time (Long.MAX_VALUE if effectively untimed)
     * @param interruptible true if return on interrupt
     * @return positive if quiescent, negative if interrupted, else 0
     */
    private int helpQuiesce(WorkQueue w, long nanos, boolean interruptible) {
        int phase; // w.phase inactive bit set when temporarily quiescent
        if (w == null || ((phase = w.phase) & IDLE) != 0)
            return 0;
        int wsrc = w.source;
        long startTime = System.nanoTime();
        long maxSleep = Math.min(nanos >>> 8, MAX_SLEEP); // approx 1% nanos
        long prevSum = 0L;
        int activePhase = phase, inactivePhase = phase + IDLE;
        int r = phase + 1, waits = 0, returnStatus = 1;
        boolean locals = true;
        for (long e = runState;;) {
            if ((e & STOP) != 0L)
                break;                      // terminating
            if (interruptible && Thread.interrupted()) {
                returnStatus = -1;
                break;
            }
            if (locals) {                   // run local tasks before (re)polling
                locals = false;
                for (ForkJoinTask<?> u; (u = w.nextLocalTask()) != null;)
                    u.doExec();
            }
            WorkQueue[] qs = queues;
            int n = (qs == null) ? 0 : qs.length;
            long phaseSum = 0L;
            boolean rescan = false, busy = false;
            scan: for (int l = n; l > 0; --l, ++r) {
                int j; WorkQueue q;
                if ((q = qs[j = r & SMASK & (n - 1)]) != null && q != w) {
                    for (;;) {
                        ForkJoinTask<?> t; ForkJoinTask<?>[] a;
                        int b, cap; long k;
                        if ((a = q.array) == null || (cap = a.length) <= 0)
                            break;
                        t = (ForkJoinTask<?>)U.getReferenceAcquire(
                            a, k = slotOffset((cap - 1) & (b = q.base)));
                        if (t != null && phase == inactivePhase) // reactivate
                            w.phase = phase = activePhase;
                        if (q.base == b && U.getReference(a, k) == t) {
                            int nb = b + 1;
                            if (t == null) {
                                if (!rescan) {
                                    int qp = q.phase, mq = qp & (IDLE | 1);
                                    phaseSum += qp;
                                    if (mq == 0 || q.top - b > 0)
                                        rescan = true;
                                    else if (mq == 1)
                                        busy = true;
                                }
                                break;
                            }
                            if (U.compareAndSetReference(a, k, t, null)) {
                                q.base = nb;
                                w.source = j; // volatile write
                                t.doExec();
                                w.source = wsrc;
                                rescan = locals = true;
                                break scan;
                            }
                        }
                    }
                }
            }
            if (e != (e = runState) || prevSum != (prevSum = phaseSum) ||
                rescan || (e & RS_LOCK) != 0L)
                ;                   // inconsistent
            else if (!busy)
                break;
            else if (phase == activePhase) {
                waits = 0;          // recheck, then sleep
                w.phase = phase = inactivePhase;
            }
            else if (System.nanoTime() - startTime > nanos) {
                returnStatus = 0;   // timed out
                break;
            }
            else if (waits == 0)   // same as spinLockRunState except
                waits = MIN_SLEEP; //   with rescan instead of onSpinWait
            else {
                LockSupport.parkNanos(this, (long)waits);
                if (waits < maxSleep)
                    waits <<= 1;
            }
        }
        w.phase = activePhase;
        return returnStatus;
    }

    /**
     * Helps quiesce from external caller until done, interrupted, or timeout
     *
     * @param nanos max wait time (Long.MAX_VALUE if effectively untimed)
     * @param interruptible true if return on interrupt
     * @return positive if quiescent, negative if interrupted, else 0
     */
    private int externalHelpQuiesce(long nanos, boolean interruptible) {
        if (quiescent() < 0) {
            long startTime = System.nanoTime();
            long maxSleep = Math.min(nanos >>> 8, MAX_SLEEP);
            for (int waits = 0;;) {
                ForkJoinTask<?> t;
                if (interruptible && Thread.interrupted())
                    return -1;
                else if ((t = pollScan(false)) != null) {
                    waits = 0;
                    t.doExec();
                }
                else if (quiescent() >= 0)
                    break;
                else if (System.nanoTime() - startTime > nanos)
                    return 0;
                else if (waits == 0)
                    waits = MIN_SLEEP;
                else {
                    LockSupport.parkNanos(this, (long)waits);
                    if (waits < maxSleep)
                        waits <<= 1;
                }
            }
        }
        return 1;
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
     * @param r current ThreadLocalRandom.getProbe() value
     * @param rejectOnShutdown true if RejectedExecutionException
     *        should be thrown when shutdown (else only if terminating)
     */
    private WorkQueue submissionQueue(int r, boolean rejectOnShutdown) {
        int reuse;                                   // nonzero if prefer create
        if ((reuse = r) == 0) {
            ThreadLocalRandom.localInit();           // initialize caller's probe
            r = ThreadLocalRandom.getProbe();
        }
        for (int probes = 0; ; ++probes) {
            int n, i, id; WorkQueue[] qs; WorkQueue q;
            if ((qs = queues) == null)
                break;
            if ((n = qs.length) <= 0)
                break;
            if ((q = qs[i = (id = r & EXTERNAL_ID_MASK) & (n - 1)]) == null) {
                WorkQueue w = new WorkQueue(null, id, 0, false);
                w.phase = id;
                boolean reject = ((lockRunState() & SHUTDOWN) != 0 &&
                                  rejectOnShutdown);
                if (!reject && queues == qs && qs[i] == null)
                    q = qs[i] = w;                   // else lost race to install
                unlockRunState();
                if (q != null)
                    return q;
                if (reject)
                    break;
                reuse = 0;
            }
            if (reuse == 0 || !q.tryLockPhase()) {   // move index
                if (reuse == 0) {
                    if (probes >= n >> 1)
                        reuse = r;                   // stop prefering free slot
                }
                else if (q != null)
                    reuse = 0;                       // probe on collision
                r = ThreadLocalRandom.advanceProbe(r);
            }
            else if (rejectOnShutdown && (runState & SHUTDOWN) != 0L) {
                q.unlockPhase();                     // check while q lock held
                break;
            }
            else
                return q;
        }
        throw new RejectedExecutionException();
    }

    private <T> ForkJoinTask<T> poolSubmit(boolean signalIfEmpty, ForkJoinTask<T> task) {
        Thread t; ForkJoinWorkerThread wt; WorkQueue q; boolean internal;
        if (((t = JLA.currentCarrierThread()) instanceof ForkJoinWorkerThread) &&
            (wt = (ForkJoinWorkerThread)t).pool == this) {
            internal = true;
            q = wt.workQueue;
        }
        else {                     // find and lock queue
            internal = false;
            q = submissionQueue(ThreadLocalRandom.getProbe(), true);
        }
        q.push(task, signalIfEmpty ? this : null, internal);
        return task;
    }

    /**
     * Returns queue for an external submission, bypassing call to
     * submissionQueue if already established and unlocked.
     */
    final WorkQueue externalSubmissionQueue(boolean rejectOnShutdown) {
        WorkQueue[] qs; WorkQueue q; int n;
        int r = ThreadLocalRandom.getProbe();
        return (((qs = queues) != null && (n = qs.length) > 0 &&
                 (q = qs[r & EXTERNAL_ID_MASK & (n - 1)]) != null && r != 0 &&
                 q.tryLockPhase()) ? q : submissionQueue(r, rejectOnShutdown));
    }

    /**
     * Returns queue for an external thread, if one exists that has
     * possibly ever submitted to the given pool (nonzero probe), or
     * null if none.
     */
    static WorkQueue externalQueue(ForkJoinPool p) {
        WorkQueue[] qs; int n;
        int r = ThreadLocalRandom.getProbe();
        return (p != null && (qs = p.queues) != null &&
                (n = qs.length) > 0 && r != 0) ?
            qs[r & EXTERNAL_ID_MASK & (n - 1)] : null;
    }

    /**
     * Returns external queue for common pool.
     */
    static WorkQueue commonQueue() {
        return externalQueue(common);
    }

    /**
     * If the given executor is a ForkJoinPool, poll and execute
     * AsynchronousCompletionTasks from worker's queue until none are
     * available or blocker is released.
     */
    static void helpAsyncBlocker(Executor e, ManagedBlocker blocker) {
        WorkQueue w = null; Thread t; ForkJoinWorkerThread wt;
        if (((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) &&
            (wt = (ForkJoinWorkerThread)t).pool == e)
            w = wt.workQueue;
        else if (e instanceof ForkJoinPool)
            w = externalQueue((ForkJoinPool)e);
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
     * @return runState on exit
     */
    private long tryTerminate(boolean now, boolean enable) {
        long e, isShutdown, ps;
        if (((e = runState) & TERMINATED) != 0L)
            now = false;
        else if ((e & STOP) != 0L)
            now = true;
        else if (now) {
            if (((ps = getAndBitwiseOrRunState(SHUTDOWN|STOP) & STOP)) == 0L) {
                if ((ps & RS_LOCK) != 0L) {
                    spinLockRunState(); // ensure queues array stable after stop
                    unlockRunState();
                }
                interruptAll();
            }
        }
        else if ((isShutdown = (e & SHUTDOWN)) != 0L || enable) {
            long quiet; DelayScheduler ds;
            if (isShutdown == 0L)
                getAndBitwiseOrRunState(SHUTDOWN);
            if ((quiet = quiescent()) > 0)
                now = true;
            else if (quiet == 0 && (ds = delayScheduler) != null)
                ds.signal();
        }

        if (now) {
            DelayScheduler ds;
            releaseWaiters();
            if ((ds = delayScheduler) != null)
                ds.signal();
            for (;;) {
                if (((e = runState) & CLEANED) == 0L) {
                    boolean clean = cleanQueues();
                    if (((e = runState) & CLEANED) == 0L && clean)
                        e = getAndBitwiseOrRunState(CLEANED) | CLEANED;
                }
                if ((e & TERMINATED) != 0L)
                    break;
                if (ctl != 0L) // else loop if didn't finish cleaning
                    break;
                if ((ds = delayScheduler) != null && ds.signal() >= 0)
                    break;
                if ((e & CLEANED) != 0L) {
                    e |= TERMINATED;
                    if ((getAndBitwiseOrRunState(TERMINATED) & TERMINATED) == 0L) {
                        CountDownLatch done; SharedThreadContainer ctr;
                        if ((done = termination) != null)
                            done.countDown();
                        if ((ctr = container) != null)
                            ctr.close();
                    }
                    break;
                }
            }
        }
        return e;
    }

    /**
     * Scans queues in a psuedorandom order based on thread id,
     * cancelling tasks until empty, or returning early upon
     * interference or still-active external queues, in which case
     * other calls will finish cancellation.
     *
     * @return true if all queues empty
     */
    private boolean cleanQueues() {
        int r = (int)Thread.currentThread().threadId();
        r ^= r << 13; r ^= r >>> 17; r ^= r << 5; // xorshift
        int step = (r >>> 16) | 1;                // randomize traversals
        WorkQueue[] qs = queues;
        int n = (qs == null) ? 0 : qs.length;
        for (int l = n; l > 0; --l, r += step) {
            WorkQueue q; ForkJoinTask<?>[] a; int cap;
            if ((q = qs[r & (n - 1)]) != null &&
                (a = q.array) != null && (cap = a.length) > 0) {
                for (;;) {
                    ForkJoinTask<?> t; int b; long k;
                    t = (ForkJoinTask<?>)U.getReferenceAcquire(
                        a, k = slotOffset((cap - 1) & (b = q.base)));
                    if (q.base == b && t != null &&
                        U.compareAndSetReference(a, k, t, null)) {
                        q.updateBase(b + 1);
                        try {
                            t.cancel(false);
                        } catch (Throwable ignore) {
                        }
                    }
                    else if ((q.phase & (IDLE|1)) == 0 || // externally locked
                             q.top - q.base > 0)
                        return false;             // incomplete
                    else
                        break;
                }
            }
        }
        return true;
    }

    /**
     * Interrupts all workers
     */
    private void interruptAll() {
        Thread current = Thread.currentThread();
        WorkQueue[] qs = queues;
        int n = (qs == null) ? 0 : qs.length;
        for (int i = 1; i < n; i += 2) {
            WorkQueue q; Thread o;
            if ((q = qs[i]) != null && (o = q.owner) != null && o != current) {
                try {
                    o.interrupt();
                } catch (Throwable ignore) {
                }
            }
        }
    }

    /**
     * Returns termination signal, constructing if necessary
     */
    private CountDownLatch terminationSignal() {
        CountDownLatch signal, s, u;
        if ((signal = termination) == null)
            signal = ((u = cmpExTerminationSignal(
                           s = new CountDownLatch(1))) == null) ? s : u;
        return signal;
    }

    // Exported methods

    // Constructors

    /**
     * Creates a {@code ForkJoinPool} with parallelism equal to {@link
     * java.lang.Runtime#availableProcessors}, using defaults for all
     * other parameters (see {@link #ForkJoinPool(int,
     * ForkJoinWorkerThreadFactory, UncaughtExceptionHandler, boolean,
     * int, int, int, Predicate, long, TimeUnit)}).
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
     * @param corePoolSize ignored: used in previous releases of this
     * class but no longer applicable. Using {@code 0} maintains
     * compatibility across releases.
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
        int p = parallelism;
        if (p <= 0 || p > MAX_CAP || p > maximumPoolSize || keepAliveTime <= 0L)
            throw new IllegalArgumentException();
        if (factory == null || unit == null)
            throw new NullPointerException();
        int size = Math.max(MIN_QUEUES_SIZE,
                            1 << (33 - Integer.numberOfLeadingZeros(p - 1)));
        this.parallelism = p;
        this.factory = factory;
        this.ueh = handler;
        this.saturate = saturate;
        this.keepAlive = Math.max(unit.toMillis(keepAliveTime), TIMEOUT_SLOP);
        int maxSpares = Math.clamp(maximumPoolSize - p, 0, MAX_CAP);
        int minAvail = Math.clamp(minimumRunnable, 0, MAX_CAP);
        this.config = (((asyncMode ? FIFO : 0) & LMASK) |
                       (((long)maxSpares) << TC_SHIFT) |
                       (((long)minAvail)  << RC_SHIFT));
        this.queues = new WorkQueue[size];
        String pid = Integer.toString(getAndAddPoolIds(1) + 1);
        String name = "ForkJoinPool-" + pid;
        this.poolName = name;
        this.workerNamePrefix = name + "-worker-";
        this.container = SharedThreadContainer.create(name);
    }

    /**
     * Constructor for common pool using parameters possibly
     * overridden by system properties
     */
    private ForkJoinPool(byte forCommonPoolOnly) {
        String name = "ForkJoinPool.commonPool";
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
                maxSpares = Math.clamp(Integer.parseInt(ms), 0, MAX_CAP);
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
        int size = Math.max(MIN_QUEUES_SIZE,
                            (p == 0) ? 1 :
                            1 << (33 - Integer.numberOfLeadingZeros(p-1)));
        this.parallelism = p;
        this.config = ((preset & LMASK) | (((long)maxSpares) << TC_SHIFT) |
                       (1L << RC_SHIFT));
        this.factory = fac;
        this.ueh = handler;
        this.keepAlive = DEFAULT_KEEPALIVE;
        this.saturate = null;
        this.workerNamePrefix = null;
        this.poolName = name;
        this.queues = new WorkQueue[size];
        this.container = SharedThreadContainer.create(name);
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

    /**
     * Package-private access to commonPool overriding zero parallelism
     */
    static ForkJoinPool asyncCommonPool() {
        ForkJoinPool cp; int p;
        if ((p = (cp = common).parallelism) == 0)
            U.compareAndSetInt(cp, PARALLELISM, 0, 2);
        return cp;
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
        poolSubmit(true, Objects.requireNonNull(task));
        try {
            return task.join();
        } catch (RuntimeException | Error unchecked) {
            throw unchecked;
        } catch (Exception checked) {
            throw new RuntimeException(checked);
        }
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
        poolSubmit(true,  Objects.requireNonNull(task));
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
        poolSubmit(true, (Objects.requireNonNull(task) instanceof ForkJoinTask<?>)
                   ? (ForkJoinTask<Void>) task // avoid re-wrap
                   : new ForkJoinTask.RunnableExecuteAction(task));
    }

    /**
     * Submits a ForkJoinTask for execution.
     *
     * @implSpec
     * This method is equivalent to {@link #externalSubmit(ForkJoinTask)}
     * when called from a thread that is not in this pool.
     *
     * @param task the task to submit
     * @param <T> the type of the task's result
     * @return the task
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     */
    public <T> ForkJoinTask<T> submit(ForkJoinTask<T> task) {
        return poolSubmit(true,  Objects.requireNonNull(task));
    }

    /**
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     */
    @Override
    public <T> ForkJoinTask<T> submit(Callable<T> task) {
        Objects.requireNonNull(task);
        return poolSubmit(
            true,
            (Thread.currentThread() instanceof ForkJoinWorkerThread) ?
            new ForkJoinTask.AdaptedCallable<T>(task) :
            new ForkJoinTask.AdaptedInterruptibleCallable<T>(task));
    }

    /**
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     */
    @Override
    public <T> ForkJoinTask<T> submit(Runnable task, T result) {
        Objects.requireNonNull(task);
        return poolSubmit(
            true,
            (Thread.currentThread() instanceof ForkJoinWorkerThread) ?
            new ForkJoinTask.AdaptedRunnable<T>(task, result) :
            new ForkJoinTask.AdaptedInterruptibleRunnable<T>(task, result));
    }

    /**
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     */
    @Override
    @SuppressWarnings("unchecked")
    public ForkJoinTask<?> submit(Runnable task) {
        Objects.requireNonNull(task);
        return poolSubmit(
            true,
            (task instanceof ForkJoinTask<?>) ?
            (ForkJoinTask<Void>) task : // avoid re-wrap
            ((Thread.currentThread() instanceof ForkJoinWorkerThread) ?
             new ForkJoinTask.AdaptedRunnable<Void>(task, null) :
             new ForkJoinTask.AdaptedInterruptibleRunnable<Void>(task, null)));
    }

    /**
     * Submits the given task as if submitted from a non-{@code ForkJoinTask}
     * client. The task is added to a scheduling queue for submissions to the
     * pool even when called from a thread in the pool.
     *
     * @implSpec
     * This method is equivalent to {@link #submit(ForkJoinTask)} when called
     * from a thread that is not in this pool.
     *
     * @return the task
     * @param task the task to submit
     * @param <T> the type of the task's result
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     * @since 20
     */
    public <T> ForkJoinTask<T> externalSubmit(ForkJoinTask<T> task) {
        Objects.requireNonNull(task);
        externalSubmissionQueue(true).push(task, this, false);
        return task;
    }

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
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     * @since 19
     */
    public <T> ForkJoinTask<T> lazySubmit(ForkJoinTask<T> task) {
        return poolSubmit(false,  Objects.requireNonNull(task));
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
     * @since 19
     */
    public int setParallelism(int size) {
        if (size < 1 || size > MAX_CAP)
            throw new IllegalArgumentException();
        if ((config & PRESET_SIZE) != 0)
            throw new UnsupportedOperationException("Cannot override System property");
        return getAndSetParallelism(size);
    }

    /**
     * Uninterrupible version of {@code invokeAll}. Executes the given
     * tasks, returning a list of Futures holding their status and
     * results when all complete, ignoring interrupts.  {@link
     * Future#isDone} is {@code true} for each element of the returned
     * list.  Note that a <em>completed</em> task could have
     * terminated either normally or by throwing an exception.  The
     * results of this method are undefined if the given collection is
     * modified while this operation is in progress.
     *
     * @apiNote This method supports usages that previously relied on an
     * incompatible override of
     * {@link ExecutorService#invokeAll(java.util.Collection)}.
     *
     * @param tasks the collection of tasks
     * @param <T> the type of the values returned from the tasks
     * @return a list of Futures representing the tasks, in the same
     *         sequential order as produced by the iterator for the
     *         given task list, each of which has completed
     * @throws NullPointerException if tasks or any of its elements are {@code null}
     * @throws RejectedExecutionException if any task cannot be
     *         scheduled for execution
     * @since 22
     */
    public <T> List<Future<T>> invokeAllUninterruptibly(Collection<? extends Callable<T>> tasks) {
        ArrayList<Future<T>> futures = new ArrayList<>(tasks.size());
        try {
            for (Callable<T> t : tasks) {
                ForkJoinTask<T> f = ForkJoinTask.adapt(t);
                futures.add(f);
                poolSubmit(true, f);
            }
            for (int i = futures.size() - 1; i >= 0; --i)
                ((ForkJoinTask<?>)futures.get(i)).quietlyJoin();
            return futures;
        } catch (Throwable t) {
            for (Future<T> e : futures)
                e.cancel(true);
            throw t;
        }
    }

    /**
     * Common support for timed and untimed invokeAll
     */
    private  <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks,
                                           long deadline)
        throws InterruptedException {
        ArrayList<Future<T>> futures = new ArrayList<>(tasks.size());
        try {
            for (Callable<T> t : tasks) {
                ForkJoinTask<T> f = ForkJoinTask.adaptInterruptible(t);
                futures.add(f);
                poolSubmit(true, f);
            }
            for (int i = futures.size() - 1; i >= 0; --i)
                ((ForkJoinTask<?>)futures.get(i))
                    .quietlyJoinPoolInvokeAllTask(deadline);
            return futures;
        } catch (Throwable t) {
            for (Future<T> e : futures)
                e.cancel(true);
            throw t;
        }
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
        throws InterruptedException {
        return invokeAll(tasks, 0L);
    }
    // for jdk version < 22, replace with
    // /**
    //  * @throws NullPointerException       {@inheritDoc}
    //  * @throws RejectedExecutionException {@inheritDoc}
    //  */
    // @Override
    // public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) {
    //     return invokeAllUninterruptibly(tasks);
    // }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks,
                                         long timeout, TimeUnit unit)
        throws InterruptedException {
        return invokeAll(tasks, (System.nanoTime() + unit.toNanos(timeout)) | 1L);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
        throws InterruptedException, ExecutionException {
        try {
            return new ForkJoinTask.InvokeAnyRoot<T>()
                .invokeAny(tasks, this, false, 0L);
        } catch (TimeoutException cannotHappen) {
            assert false;
            return null;
        }
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks,
                           long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
        return new ForkJoinTask.InvokeAnyRoot<T>()
            .invokeAny(tasks, this, true, unit.toNanos(timeout));
    }

    // Support for delayed tasks

    /**
     * Returns STOP and SHUTDOWN status (zero if neither), masking or
     * truncating out other bits.
     */
    final int shutdownStatus(DelayScheduler ds) {
        return (int)(runState & (SHUTDOWN | STOP));
    }

    /**
     * Tries to stop and possibly terminate if already enabled, return success.
     */
    final boolean tryStopIfShutdown(DelayScheduler ds) {
        return (tryTerminate(false, false) & STOP) != 0L;
    }

    /**
     *  Creates and starts DelayScheduler
     */
    private DelayScheduler startDelayScheduler() {
        DelayScheduler ds;
        if ((ds = delayScheduler) == null) {
            boolean start = false;
            String name = poolName + "-delayScheduler";
            if (workerNamePrefix == null)
                asyncCommonPool();  // override common parallelism zero
            long isShutdown = lockRunState() & SHUTDOWN;
            try {
                if (isShutdown == 0L && (ds = delayScheduler) == null) {
                    ds = delayScheduler = new DelayScheduler(this, name);
                    start = true;
                }
            } finally {
                unlockRunState();
            }
            if (start) { // start outside of lock
                SharedThreadContainer ctr;
                try {
                    if ((ctr = container) != null)
                        ctr.start(ds);
                    else
                        ds.start();
                } catch (RuntimeException | Error ex) { // back out
                    lockRunState();
                    ds = delayScheduler = null;
                    unlockRunState();
                    tryTerminate(false, false);
                    if (ex instanceof Error)
                        throw ex;
                }
            }
        }
        return ds;
    }

    /**
     * Arranges execution of a ScheduledForkJoinTask whose delay has
     * elapsed
     */
    final void executeEnabledScheduledTask(ScheduledForkJoinTask<?> task) {
        externalSubmissionQueue(false).push(task, this, false);
    }

    /**
     * Arranges delayed execution of a ScheduledForkJoinTask via the
     * DelayScheduler, creating and starting it if necessary.
     * @return the task
     */
    final <T> ScheduledForkJoinTask<T> scheduleDelayedTask(ScheduledForkJoinTask<T> task) {
        DelayScheduler ds;
        if (((ds = delayScheduler) == null &&
             (ds = startDelayScheduler()) == null) ||
            (runState & SHUTDOWN) != 0L)
            throw new RejectedExecutionException();
        ds.pend(task);
        return task;
    }

    /**
     * Submits a one-shot task that becomes enabled for execution after the given
     * delay.  At that point it will execute unless explicitly
     * cancelled, or fail to execute (eventually reporting
     * cancellation) when encountering resource exhaustion, or the
     * pool is {@link #shutdownNow}, or is {@link #shutdown} when
     * otherwise quiescent and {@link #cancelDelayedTasksOnShutdown}
     * is in effect.
     *
     * @param command the task to execute
     * @param delay the time from now to delay execution
     * @param unit the time unit of the delay parameter
     * @return a ForkJoinTask implementing the ScheduledFuture
     *         interface, whose {@code get()} method will return
     *         {@code null} upon normal completion.
     * @throws RejectedExecutionException if the pool is shutdown or
     *         submission encounters resource exhaustion.
     * @throws NullPointerException if command or unit is null
     * @since 25
     */
    public ScheduledFuture<?> schedule(Runnable command,
                                       long delay, TimeUnit unit) {
        return scheduleDelayedTask(
            new ScheduledForkJoinTask<Void>(
                unit.toNanos(delay), 0L, false, // implicit null check of unit
                Objects.requireNonNull(command), null, this));
    }

    /**
     * Submits a value-returning one-shot task that becomes enabled for execution
     * after the given delay. At that point it will execute unless
     * explicitly cancelled, or fail to execute (eventually reporting
     * cancellation) when encountering resource exhaustion, or the
     * pool is {@link #shutdownNow}, or is {@link #shutdown} when
     * otherwise quiescent and {@link #cancelDelayedTasksOnShutdown}
     * is in effect.
     *
     * @param callable the function to execute
     * @param delay the time from now to delay execution
     * @param unit the time unit of the delay parameter
     * @param <V> the type of the callable's result
     * @return a ForkJoinTask implementing the ScheduledFuture
     *         interface, whose {@code get()} method will return the
     *         value from the callable upon normal completion.
     * @throws RejectedExecutionException if the pool is shutdown or
     *         submission encounters resource exhaustion.
     * @throws NullPointerException if command or unit is null
     * @since 25
     */
    public <V> ScheduledFuture<V> schedule(Callable<V> callable,
                                           long delay, TimeUnit unit) {
        return scheduleDelayedTask(
            new ScheduledForkJoinTask<V>(
                unit.toNanos(delay), 0L, false, null,  // implicit null check of unit
                Objects.requireNonNull(callable), this));
    }

    /**
     * Submits a periodic action that becomes enabled for execution first after the
     * given initial delay, and subsequently with the given period;
     * that is, executions will commence after
     * {@code initialDelay}, then {@code initialDelay + period}, then
     * {@code initialDelay + 2 * period}, and so on.
     *
     * <p>The sequence of task executions continues indefinitely until
     * one of the following exceptional completions occur:
     * <ul>
     * <li>The task is {@linkplain Future#cancel explicitly cancelled}
     * <li>Method {@link #shutdownNow} is called
     * <li>Method {@link #shutdown} is called and the pool is
     * otherwise quiescent, in which case existing executions continue
     * but subsequent executions do not.
     * <li>An execution or the task encounters resource exhaustion.
     * <li>An execution of the task throws an exception.  In this case
     * calling {@link Future#get() get} on the returned future will throw
     * {@link ExecutionException}, holding the exception as its cause.
     * </ul>
     * Subsequent executions are suppressed.  Subsequent calls to
     * {@link Future#isDone isDone()} on the returned future will
     * return {@code true}.
     *
     * <p>If any execution of this task takes longer than its period, then
     * subsequent executions may start late, but will not concurrently
     * execute.
     * @param command the task to execute
     * @param initialDelay the time to delay first execution
     * @param period the period between successive executions
     * @param unit the time unit of the initialDelay and period parameters
     * @return a ForkJoinTask implementing the ScheduledFuture
     *         interface.  The future's {@link Future#get() get()}
     *         method will never return normally, and will throw an
     *         exception upon task cancellation or abnormal
     *         termination of a task execution.
     * @throws RejectedExecutionException if the pool is shutdown or
     *         submission encounters resource exhaustion.
     * @throws NullPointerException if command or unit is null
     * @throws IllegalArgumentException if period less than or equal to zero
     * @since 25
     */
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command,
                                                  long initialDelay,
                                                  long period, TimeUnit unit) {
        if (period <= 0L)
            throw new IllegalArgumentException();
        return scheduleDelayedTask(
            new ScheduledForkJoinTask<Void>(
                unit.toNanos(initialDelay),  // implicit null check of unit
                unit.toNanos(period), false,
                Objects.requireNonNull(command), null, this));
    }

    /**
     * Submits a periodic action that becomes enabled for execution first after the
     * given initial delay, and subsequently with the given delay
     * between the termination of one execution and the commencement of
     * the next.
     * <p>The sequence of task executions continues indefinitely until
     * one of the following exceptional completions occur:
     * <ul>
     * <li>The task is {@linkplain Future#cancel explicitly cancelled}
     * <li>Method {@link #shutdownNow} is called
     * <li>Method {@link #shutdown} is called and the pool is
     * otherwise quiescent, in which case existing executions continue
     * but subsequent executions do not.
     * <li>An execution or the task encounters resource exhaustion.
     * <li>An execution of the task throws an exception.  In this case
     * calling {@link Future#get() get} on the returned future will throw
     * {@link ExecutionException}, holding the exception as its cause.
     * </ul>
     * Subsequent executions are suppressed.  Subsequent calls to
     * {@link Future#isDone isDone()} on the returned future will
     * return {@code true}.
     * @param command the task to execute
     * @param initialDelay the time to delay first execution
     * @param delay the delay between the termination of one
     * execution and the commencement of the next
     * @param unit the time unit of the initialDelay and delay parameters
     * @return a ForkJoinTask implementing the ScheduledFuture
     *         interface.  The future's {@link Future#get() get()}
     *         method will never return normally, and will throw an
     *         exception upon task cancellation or abnormal
     *         termination of a task execution.
     * @throws RejectedExecutionException if the pool is shutdown or
     *         submission encounters resource exhaustion.
     * @throws NullPointerException if command or unit is null
     * @throws IllegalArgumentException if delay less than or equal to zero
     * @since 25
     */
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command,
                                                     long initialDelay,
                                                     long delay, TimeUnit unit) {
        if (delay <= 0L)
            throw new IllegalArgumentException();
        return scheduleDelayedTask(
            new ScheduledForkJoinTask<Void>(
                unit.toNanos(initialDelay),  // implicit null check of unit
                -unit.toNanos(delay), false, // negative for fixed delay
                Objects.requireNonNull(command), null, this));
    }

    /**
     * Body of a task performed on timeout of another task
     */
    static final class TimeoutAction<V> implements Runnable {
        // set after construction, nulled after use
        ForkJoinTask.CallableWithTimeout<V> task;
        Consumer<? super ForkJoinTask<V>> action;
        TimeoutAction(Consumer<? super ForkJoinTask<V>> action) {
            this.action = action;
        }
        public void run() {
            ForkJoinTask.CallableWithTimeout<V> t = task;
            Consumer<? super ForkJoinTask<V>> a = action;
            task = null;
            action = null;
            if (t != null && t.status >= 0) {
                if (a == null)
                    t.cancel(true);
                else {
                    a.accept(t);
                    t.interruptIfRunning(true);
                }
            }
        }
    }

    /**
     * Submits a task executing the given function, cancelling the
     * task or performing a given timeoutAction if not completed
     * within the given timeout period. If the optional {@code
     * timeoutAction} is null, the task is cancelled (via {@code
     * cancel(true)}.  Otherwise, the action is applied and the task
     * may be interrupted if running. Actions may include {@link
     * ForkJoinTask#complete} to set a replacement value or {@link
     * ForkJoinTask#completeExceptionally} to throw an appropriate
     * exception. Note that these can succeed only if the task has
     * not already completed when the timeoutAction executes.
     *
     * @param callable the function to execute
     * @param <V> the type of the callable's result
     * @param timeout the time to wait before cancelling if not completed
     * @param timeoutAction if nonnull, an action to perform on
     *        timeout, otherwise the default action is to cancel using
     *        {@code cancel(true)}.
     * @param unit the time unit of the timeout parameter
     * @return a Future that can be used to extract result or cancel
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     * @throws NullPointerException if callable or unit is null
     * @since 25
     */
    public <V> ForkJoinTask<V> submitWithTimeout(Callable<V> callable,
                                                 long timeout, TimeUnit unit,
                                                 Consumer<? super ForkJoinTask<V>> timeoutAction) {
        ForkJoinTask.CallableWithTimeout<V> task; TimeoutAction<V> onTimeout;
        Objects.requireNonNull(callable);
        ScheduledForkJoinTask<Void> timeoutTask =
            new ScheduledForkJoinTask<Void>(
                unit.toNanos(timeout), 0L, true,
                onTimeout = new TimeoutAction<V>(timeoutAction), null, this);
        onTimeout.task = task =
            new ForkJoinTask.CallableWithTimeout<V>(callable, timeoutTask);
        scheduleDelayedTask(timeoutTask);
        return poolSubmit(true, task);
    }

    /**
     * Arranges that scheduled tasks that are not executing and have
     * not already been enabled for execution will not be executed and
     * will be cancelled upon {@link #shutdown} (unless this pool is
     * the {@link #commonPool()} which never shuts down). This method
     * may be invoked either before {@link #shutdown} to take effect
     * upon the next call, or afterwards to cancel such tasks, which
     * may then allow termination. Note that subsequent executions of
     * periodic tasks are always disabled upon shutdown, so this
     * method applies meaningfully only to non-periodic tasks.
     * @since 25
     */
    public void cancelDelayedTasksOnShutdown() {
        DelayScheduler ds;
        if ((ds = delayScheduler) != null ||
            (ds = startDelayScheduler()) != null)
            ds.cancelDelayedTasksOnShutdown();
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
        if ((runState & TERMINATED) == 0L && (qs = queues) != null) {
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
        return quiescent() >= 0;
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
     * granularities.The returned count does not include scheduled
     * tasks that are not yet ready to execute, which are reported
     * separately by method {@link getDelayedTaskCount}.
     *
     * @return the number of queued tasks
     * @see ForkJoinWorkerThread#getQueuedTaskCount()
     */
    public long getQueuedTaskCount() {
        WorkQueue[] qs; WorkQueue q;
        long count = 0;
        if ((runState & TERMINATED) == 0L && (qs = queues) != null) {
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
        if ((runState & TERMINATED) == 0L && (qs = queues) != null) {
            for (int i = 0; i < qs.length; i += 2) {
                if ((q = qs[i]) != null)
                    count += q.queueSize();
            }
        }
        return count;
    }

    /**
     * Returns an estimate of the number of delayed (including
     * periodic) tasks scheduled in this pool that are not yet ready
     * to submit for execution. The returned value is inaccurate while
     * delayed tasks are being processed.
     *
     * @return an estimate of the number of delayed tasks
     * @since 25
     */
    public long getDelayedTaskCount() {
        DelayScheduler ds;
        return ((ds = delayScheduler) == null ? 0 : ds.lastStableSize());
    }

    /**
     * Returns {@code true} if there are any tasks submitted to this
     * pool that have not yet begun executing.
     *
     * @return {@code true} if there are any queued submissions
     */
    public boolean hasQueuedSubmissions() {
        WorkQueue[] qs; WorkQueue q;
        if ((runState & STOP) == 0L && (qs = queues) != null) {
            for (int i = 0; i < qs.length; i += 2) {
                if ((q = qs[i]) != null && q.queueSize() > 0)
                    return true;
            }
        }
        return false;
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
        DelayScheduler ds;
        long e = runState;
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
        String delayed = ((ds = delayScheduler) == null ? "" :
                          ", delayed = " + ds.lastStableSize());
        int pc = parallelism;
        long c = ctl;
        int tc = (short)(c >>> TC_SHIFT);
        int ac = (short)(c >>> RC_SHIFT);
        if (ac < 0) // ignore transient negative
            ac = 0;
        String level = ((e & TERMINATED) != 0L ? "Terminated" :
                        (e & STOP)       != 0L ? "Terminating" :
                        (e & SHUTDOWN)   != 0L ? "Shutting down" :
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
            delayed +
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
     */
    public void shutdown() {
        if (workerNamePrefix != null) // not common pool
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
     */
    public List<Runnable> shutdownNow() {
        if (workerNamePrefix != null) // not common pool
            tryTerminate(true, true);
        return Collections.emptyList();
    }

    /**
     * Returns {@code true} if all tasks have completed following shut down.
     *
     * @return {@code true} if all tasks have completed following shut down
     */
    public boolean isTerminated() {
        return (tryTerminate(false, false) & TERMINATED) != 0;
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
        return (tryTerminate(false, false) & (STOP | TERMINATED)) == STOP;
    }

    /**
     * Returns {@code true} if this pool has been shut down.
     *
     * @return {@code true} if this pool has been shut down
     */
    public boolean isShutdown() {
        return (runState & SHUTDOWN) != 0L;
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
        long nanos = unit.toNanos(timeout);
        CountDownLatch done;
        if (workerNamePrefix == null) {    // is common pool
            if (helpQuiescePool(this, nanos, true) < 0)
                throw new InterruptedException();
            return false;
        }
        else if ((tryTerminate(false, false) & TERMINATED) != 0 ||
                 (done = terminationSignal()) == null ||
                 (runState & TERMINATED) != 0L)
            return true;
        else
            return done.await(nanos, TimeUnit.NANOSECONDS);
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
     * Unless this is the {@link #commonPool()}, initiates an orderly
     * shutdown in which previously submitted tasks are executed, but
     * no new tasks will be accepted, and waits until all tasks have
     * completed execution and the executor has terminated.
     *
     * <p> If already terminated, or this is the {@link
     * #commonPool()}, this method has no effect on execution, and
     * does not wait. Otherwise, if interrupted while waiting, this
     * method stops all executing tasks as if by invoking {@link
     * #shutdownNow()}. It then continues to wait until all actively
     * executing tasks have completed. Tasks that were awaiting
     * execution are not executed. The interrupt status will be
     * re-asserted before this method returns.
     *
     * @since 19
     */
    @Override
    public void close() {
        if (workerNamePrefix != null) {
            CountDownLatch done = null;
            boolean interrupted = false;
            while ((tryTerminate(interrupted, true) & TERMINATED) == 0) {
                if (done == null)
                    done = terminationSignal();
                else {
                    try {
                        done.await();
                        break;
                    } catch (InterruptedException ex) {
                        interrupted = true;
                    }
                }
            }
            if (interrupted)
                Thread.currentThread().interrupt();
        }
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
        Objects.requireNonNull(blocker);
        for (;;) {
            int comp; boolean done;
            long c = ctl;
            if (blocker.isReleasable())
                break;
            if ((runState & STOP) != 0L)
                throw new InterruptedException();
            if ((comp = tryCompensate(c)) >= 0) {
                try {
                    done = blocker.block();
                } finally {
                    if (comp > 0)
                        getAndAddCtl(RC_UNIT);
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
     * @return value to use in endCompensatedBlock
     */
    final long beginCompensatedBlock() {
        int c;
        do {} while ((c = tryCompensate(ctl)) < 0);
        return (c == 0) ? 0L : RC_UNIT;
    }

    /**
     * Re-adjusts parallelism after a blocking operation completes.
     * @param post value from beginCompensatedBlock
     */
    void endCompensatedBlock(long post) {
        if (post > 0L) {
            getAndAddCtl(post);
        }
    }

    /** ManagedBlock for external threads */
    private static void unmanagedBlock(ManagedBlocker blocker)
        throws InterruptedException {
        Objects.requireNonNull(blocker);
        do {} while (!blocker.isReleasable() && !blocker.block());
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        Objects.requireNonNull(runnable);
        return (Thread.currentThread() instanceof ForkJoinWorkerThread) ?
            new ForkJoinTask.AdaptedRunnable<T>(runnable, value) :
            new ForkJoinTask.AdaptedInterruptibleRunnable<T>(runnable, value);
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        Objects.requireNonNull(callable);
        return (Thread.currentThread() instanceof ForkJoinWorkerThread) ?
            new ForkJoinTask.AdaptedCallable<T>(callable) :
            new ForkJoinTask.AdaptedInterruptibleCallable<T>(callable);
    }

    static {
        U = Unsafe.getUnsafe();
        Class<ForkJoinPool> klass = ForkJoinPool.class;
        try {
            Field poolIdsField = klass.getDeclaredField("poolIds");
            POOLIDS_BASE = U.staticFieldBase(poolIdsField);
            POOLIDS = U.staticFieldOffset(poolIdsField);
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
        CTL = U.objectFieldOffset(klass, "ctl");
        RUNSTATE = U.objectFieldOffset(klass, "runState");
        PARALLELISM =  U.objectFieldOffset(klass, "parallelism");
        THREADIDS = U.objectFieldOffset(klass, "threadIds");
        TERMINATION = U.objectFieldOffset(klass, "termination");
        Class<ForkJoinTask[]> aklass = ForkJoinTask[].class;
        ABASE = U.arrayBaseOffset(aklass);
        int scale = U.arrayIndexScale(aklass);
        ASHIFT = 31 - Integer.numberOfLeadingZeros(scale);
        if ((scale & (scale - 1)) != 0)
            throw new Error("array index scale not a power of two");

        Class<?> dep = LockSupport.class; // ensure loaded
        // allow access to non-public methods
        JLA = SharedSecrets.getJavaLangAccess();
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
        defaultForkJoinWorkerThreadFactory =
            new DefaultForkJoinWorkerThreadFactory();
        common = new ForkJoinPool((byte)0);
    }
}
