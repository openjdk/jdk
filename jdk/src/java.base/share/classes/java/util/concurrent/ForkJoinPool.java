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
import java.security.AccessControlContext;
import java.security.Permissions;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.LockSupport;

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
 * tasks that are never joined.
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
 * synchronization accommodated.
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
 * <table BORDER CELLPADDING=3 CELLSPACING=1>
 * <caption>Summary of task execution methods</caption>
 *  <tr>
 *    <td></td>
 *    <td ALIGN=CENTER> <b>Call from non-fork/join clients</b></td>
 *    <td ALIGN=CENTER> <b>Call from within fork/join computations</b></td>
 *  </tr>
 *  <tr>
 *    <td> <b>Arrange async execution</b></td>
 *    <td> {@link #execute(ForkJoinTask)}</td>
 *    <td> {@link ForkJoinTask#fork}</td>
 *  </tr>
 *  <tr>
 *    <td> <b>Await and obtain result</b></td>
 *    <td> {@link #invoke(ForkJoinTask)}</td>
 *    <td> {@link ForkJoinTask#invoke}</td>
 *  </tr>
 *  <tr>
 *    <td> <b>Arrange exec and obtain Future</b></td>
 *    <td> {@link #submit(ForkJoinTask)}</td>
 *    <td> {@link ForkJoinTask#fork} (ForkJoinTasks <em>are</em> Futures)</td>
 *  </tr>
 * </table>
 *
 * <p>The common pool is by default constructed with default
 * parameters, but these may be controlled by setting three
 * {@linkplain System#getProperty system properties}:
 * <ul>
 * <li>{@code java.util.concurrent.ForkJoinPool.common.parallelism}
 * - the parallelism level, a non-negative integer
 * <li>{@code java.util.concurrent.ForkJoinPool.common.threadFactory}
 * - the class name of a {@link ForkJoinWorkerThreadFactory}
 * <li>{@code java.util.concurrent.ForkJoinPool.common.exceptionHandler}
 * - the class name of a {@link UncaughtExceptionHandler}
 * <li>{@code java.util.concurrent.ForkJoinPool.common.maximumSpares}
 * - the maximum number of allowed extra threads to maintain target
 * parallelism (default 256).
 * </ul>
 * If a {@link SecurityManager} is present and no factory is
 * specified, then the default pool uses a factory supplying
 * threads that have no {@link Permissions} enabled.
 * The system class loader is used to load these classes.
 * Upon any error in establishing these settings, default parameters
 * are used. It is possible to disable or limit the use of threads in
 * the common pool by setting the parallelism property to zero, and/or
 * using a factory that may return {@code null}. However doing so may
 * cause unjoined tasks to never be executed.
 *
 * <p><b>Implementation notes</b>: This implementation restricts the
 * maximum number of running threads to 32767. Attempts to create
 * pools with greater than the maximum number result in
 * {@code IllegalArgumentException}.
 *
 * <p>This implementation rejects submitted tasks (that is, by throwing
 * {@link RejectedExecutionException}) only when the pool is shut down
 * or internal resources have been exhausted.
 *
 * @since 1.7
 * @author Doug Lea
 */
@jdk.internal.vm.annotation.Contended
public class ForkJoinPool extends AbstractExecutorService {

    /*
     * Implementation Overview
     *
     * This class and its nested classes provide the main
     * functionality and control for a set of worker threads:
     * Submissions from non-FJ threads enter into submission queues.
     * Workers take these tasks and typically split them into subtasks
     * that may be stolen by other workers.  Preference rules give
     * first priority to processing tasks from their own queues (LIFO
     * or FIFO, depending on mode), then to randomized FIFO steals of
     * tasks in other queues.  This framework began as vehicle for
     * supporting tree-structured parallelism using work-stealing.
     * Over time, its scalability advantages led to extensions and
     * changes to better support more diverse usage contexts.  Because
     * most internal methods and nested classes are interrelated,
     * their main rationale and descriptions are presented here;
     * individual methods and nested classes contain only brief
     * comments about details.
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
     * ("base" and "top") to the slots themselves.
     *
     * Adding tasks then takes the form of a classic array push(task)
     * in a circular buffer:
     *    q.array[q.top++ % length] = task;
     *
     * (The actual code needs to null-check and size-check the array,
     * uses masking, not mod, for indexing a power-of-two-sized array,
     * properly fences accesses, and possibly signals waiting workers
     * to start scanning -- see below.)  Both a successful pop and
     * poll mainly entail a CAS of a slot from non-null to null.
     *
     * The pop operation (always performed by owner) is:
     *   if ((the task at top slot is not null) and
     *        (CAS slot to null))
     *           decrement top and return task;
     *
     * And the poll operation (usually by a stealer) is
     *    if ((the task at base slot is not null) and
     *        (CAS slot to null))
     *           increment base and return task;
     *
     * There are several variants of each of these; for example most
     * versions of poll pre-screen the CAS by rechecking that the base
     * has not changed since reading the slot, and most methods only
     * attempt the CAS if base appears not to be equal to top.
     *
     * Memory ordering.  See "Correct and Efficient Work-Stealing for
     * Weak Memory Models" by Le, Pop, Cohen, and Nardelli, PPoPP 2013
     * (http://www.di.ens.fr/~zappa/readings/ppopp13.pdf) for an
     * analysis of memory ordering requirements in work-stealing
     * algorithms similar to (but different than) the one used here.
     * Extracting tasks in array slots via (fully fenced) CAS provides
     * primary synchronization. The base and top indices imprecisely
     * guide where to extract from. We do not always require strict
     * orderings of array and index updates, so sometimes let them be
     * subject to compiler and processor reorderings. However, the
     * volatile "base" index also serves as a basis for memory
     * ordering: Slot accesses are preceded by a read of base,
     * ensuring happens-before ordering with respect to stealers (so
     * the slots themselves can be read via plain array reads.)  The
     * only other memory orderings relied on are maintained in the
     * course of signalling and activation (see below).  A check that
     * base == top indicates (momentary) emptiness, but otherwise may
     * err on the side of possibly making the queue appear nonempty
     * when a push, pop, or poll have not fully committed, or making
     * it appear empty when an update of top has not yet been visibly
     * written.  (Method isEmpty() checks the case of a partially
     * completed removal of the last element.)  Because of this, the
     * poll operation, considered individually, is not wait-free. One
     * thief cannot successfully continue until another in-progress
     * one (or, if previously empty, a push) visibly completes.
     * However, in the aggregate, we ensure at least probabilistic
     * non-blockingness.  If an attempted steal fails, a scanning
     * thief chooses a different random victim target to try next. So,
     * in order for one thief to progress, it suffices for any
     * in-progress poll or new push on any empty queue to
     * complete. (This is why we normally use method pollAt and its
     * variants that try once at the apparent base index, else
     * consider alternative actions, rather than method poll, which
     * retries.)
     *
     * This approach also enables support of a user mode in which
     * local task processing is in FIFO, not LIFO order, simply by
     * using poll rather than pop.  This can be useful in
     * message-passing frameworks in which tasks are never joined.
     *
     * WorkQueues are also used in a similar way for tasks submitted
     * to the pool. We cannot mix these tasks in the same queues used
     * by workers. Instead, we randomly associate submission queues
     * with submitting threads, using a form of hashing.  The
     * ThreadLocalRandom probe value serves as a hash code for
     * choosing existing queues, and may be randomly repositioned upon
     * contention with other submitters.  In essence, submitters act
     * like workers except that they are restricted to executing local
     * tasks that they submitted (or in the case of CountedCompleters,
     * others with the same root task).  Insertion of tasks in shared
     * mode requires a lock but we use only a simple spinlock (using
     * field qlock), because submitters encountering a busy queue move
     * on to try or create other queues -- they block only when
     * creating and registering new queues. Because it is used only as
     * a spinlock, unlocking requires only a "releasing" store (using
     * putOrderedInt).  The qlock is also used during termination
     * detection, in which case it is forced to a negative
     * non-lockable value.
     *
     * Management
     * ==========
     *
     * The main throughput advantages of work-stealing stem from
     * decentralized control -- workers mostly take tasks from
     * themselves or each other, at rates that can exceed a billion
     * per second.  The pool itself creates, activates (enables
     * scanning for and running tasks), deactivates, blocks, and
     * terminates threads, all with minimal central information.
     * There are only a few properties that we can globally track or
     * maintain, so we pack them into a small number of variables,
     * often maintaining atomicity without blocking or locking.
     * Nearly all essentially atomic control state is held in two
     * volatile variables that are by far most often read (not
     * written) as status and consistency checks. (Also, field
     * "config" holds unchanging configuration state.)
     *
     * Field "ctl" contains 64 bits holding information needed to
     * atomically decide to add, inactivate, enqueue (on an event
     * queue), dequeue, and/or re-activate workers.  To enable this
     * packing, we restrict maximum parallelism to (1<<15)-1 (which is
     * far in excess of normal operating range) to allow ids, counts,
     * and their negations (used for thresholding) to fit into 16bit
     * subfields.
     *
     * Field "runState" holds lifetime status, atomically and
     * monotonically setting STARTED, SHUTDOWN, STOP, and finally
     * TERMINATED bits.
     *
     * Field "auxState" is a ReentrantLock subclass that also
     * opportunistically holds some other bookkeeping fields accessed
     * only when locked.  It is mainly used to lock (infrequent)
     * updates to workQueues.  The auxState instance is itself lazily
     * constructed (see tryInitialize), requiring a double-check-style
     * bootstrapping use of field runState, and locking a private
     * static.
     *
     * Field "workQueues" holds references to WorkQueues.  It is
     * updated (only during worker creation and termination) under the
     * lock, but is otherwise concurrently readable, and accessed
     * directly. We also ensure that reads of the array reference
     * itself never become too stale (for example, re-reading before
     * each scan). To simplify index-based operations, the array size
     * is always a power of two, and all readers must tolerate null
     * slots. Worker queues are at odd indices. Shared (submission)
     * queues are at even indices, up to a maximum of 64 slots, to
     * limit growth even if array needs to expand to add more
     * workers. Grouping them together in this way simplifies and
     * speeds up task scanning.
     *
     * All worker thread creation is on-demand, triggered by task
     * submissions, replacement of terminated workers, and/or
     * compensation for blocked workers. However, all other support
     * code is set up to work with other policies.  To ensure that we
     * do not hold on to worker references that would prevent GC, all
     * accesses to workQueues are via indices into the workQueues
     * array (which is one source of some of the messy code
     * constructions here). In essence, the workQueues array serves as
     * a weak reference mechanism. Thus for example the stack top
     * subfield of ctl stores indices, not references.
     *
     * Queuing Idle Workers. Unlike HPC work-stealing frameworks, we
     * cannot let workers spin indefinitely scanning for tasks when
     * none can be found immediately, and we cannot start/resume
     * workers unless there appear to be tasks available.  On the
     * other hand, we must quickly prod them into action when new
     * tasks are submitted or generated. In many usages, ramp-up time
     * to activate workers is the main limiting factor in overall
     * performance, which is compounded at program start-up by JIT
     * compilation and allocation. So we streamline this as much as
     * possible.
     *
     * The "ctl" field atomically maintains active and total worker
     * counts as well as a queue to place waiting threads so they can
     * be located for signalling. Active counts also play the role of
     * quiescence indicators, so are decremented when workers believe
     * that there are no more tasks to execute. The "queue" is
     * actually a form of Treiber stack.  A stack is ideal for
     * activating threads in most-recently used order. This improves
     * performance and locality, outweighing the disadvantages of
     * being prone to contention and inability to release a worker
     * unless it is topmost on stack.  We block/unblock workers after
     * pushing on the idle worker stack (represented by the lower
     * 32bit subfield of ctl) when they cannot find work.  The top
     * stack state holds the value of the "scanState" field of the
     * worker: its index and status, plus a version counter that, in
     * addition to the count subfields (also serving as version
     * stamps) provide protection against Treiber stack ABA effects.
     *
     * Creating workers. To create a worker, we pre-increment total
     * count (serving as a reservation), and attempt to construct a
     * ForkJoinWorkerThread via its factory. Upon construction, the
     * new thread invokes registerWorker, where it constructs a
     * WorkQueue and is assigned an index in the workQueues array
     * (expanding the array if necessary). The thread is then started.
     * Upon any exception across these steps, or null return from
     * factory, deregisterWorker adjusts counts and records
     * accordingly.  If a null return, the pool continues running with
     * fewer than the target number workers. If exceptional, the
     * exception is propagated, generally to some external caller.
     * Worker index assignment avoids the bias in scanning that would
     * occur if entries were sequentially packed starting at the front
     * of the workQueues array. We treat the array as a simple
     * power-of-two hash table, expanding as needed. The seedIndex
     * increment ensures no collisions until a resize is needed or a
     * worker is deregistered and replaced, and thereafter keeps
     * probability of collision low. We cannot use
     * ThreadLocalRandom.getProbe() for similar purposes here because
     * the thread has not started yet, but do so for creating
     * submission queues for existing external threads (see
     * externalPush).
     *
     * WorkQueue field scanState is used by both workers and the pool
     * to manage and track whether a worker is UNSIGNALLED (possibly
     * blocked waiting for a signal).  When a worker is inactivated,
     * its scanState field is set, and is prevented from executing
     * tasks, even though it must scan once for them to avoid queuing
     * races. Note that scanState updates lag queue CAS releases so
     * usage requires care. When queued, the lower 16 bits of
     * scanState must hold its pool index. So we place the index there
     * upon initialization (see registerWorker) and otherwise keep it
     * there or restore it when necessary.
     *
     * The ctl field also serves as the basis for memory
     * synchronization surrounding activation. This uses a more
     * efficient version of a Dekker-like rule that task producers and
     * consumers sync with each other by both writing/CASing ctl (even
     * if to its current value).  This would be extremely costly. So
     * we relax it in several ways: (1) Producers only signal when
     * their queue is empty. Other workers propagate this signal (in
     * method scan) when they find tasks. (2) Workers only enqueue
     * after scanning (see below) and not finding any tasks.  (3)
     * Rather than CASing ctl to its current value in the common case
     * where no action is required, we reduce write contention by
     * equivalently prefacing signalWork when called by an external
     * task producer using a memory access with full-volatile
     * semantics or a "fullFence". (4) For internal task producers we
     * rely on the fact that even if no other workers awaken, the
     * producer itself will eventually see the task and execute it.
     *
     * Almost always, too many signals are issued. A task producer
     * cannot in general tell if some existing worker is in the midst
     * of finishing one task (or already scanning) and ready to take
     * another without being signalled. So the producer might instead
     * activate a different worker that does not find any work, and
     * then inactivates. This scarcely matters in steady-state
     * computations involving all workers, but can create contention
     * and bookkeeping bottlenecks during ramp-up, ramp-down, and small
     * computations involving only a few workers.
     *
     * Scanning. Method scan() performs top-level scanning for tasks.
     * Each scan traverses (and tries to poll from) each queue in
     * pseudorandom permutation order by randomly selecting an origin
     * index and a step value.  (The pseudorandom generator need not
     * have high-quality statistical properties in the long term, but
     * just within computations; We use 64bit and 32bit Marsaglia
     * XorShifts, which are cheap and suffice here.)  Scanning also
     * employs contention reduction: When scanning workers fail a CAS
     * polling for work, they soon restart with a different
     * pseudorandom scan order (thus likely retrying at different
     * intervals). This improves throughput when many threads are
     * trying to take tasks from few queues.  Scans do not otherwise
     * explicitly take into account core affinities, loads, cache
     * localities, etc, However, they do exploit temporal locality
     * (which usually approximates these) by preferring to re-poll (up
     * to POLL_LIMIT times) from the same queue after a successful
     * poll before trying others.  Restricted forms of scanning occur
     * in methods helpComplete and findNonEmptyStealQueue, and take
     * similar but simpler forms.
     *
     * Deactivation and waiting. Queuing encounters several intrinsic
     * races; most notably that an inactivating scanning worker can
     * miss seeing a task produced during a scan.  So when a worker
     * cannot find a task to steal, it inactivates and enqueues, and
     * then rescans to ensure that it didn't miss one, reactivating
     * upon seeing one with probability approximately proportional to
     * probability of a miss.  (In most cases, the worker will be
     * signalled before self-signalling, avoiding cascades of multiple
     * signals for the same task).
     *
     * Workers block (in method awaitWork) using park/unpark;
     * advertising the need for signallers to unpark by setting their
     * "parker" fields.
     *
     * Trimming workers. To release resources after periods of lack of
     * use, a worker starting to wait when the pool is quiescent will
     * time out and terminate (see awaitWork) if the pool has remained
     * quiescent for period given by IDLE_TIMEOUT_MS, increasing the
     * period as the number of threads decreases, eventually removing
     * all workers.
     *
     * Shutdown and Termination. A call to shutdownNow invokes
     * tryTerminate to atomically set a runState bit. The calling
     * thread, as well as every other worker thereafter terminating,
     * helps terminate others by setting their (qlock) status,
     * cancelling their unprocessed tasks, and waking them up, doing
     * so repeatedly until stable. Calls to non-abrupt shutdown()
     * preface this by checking whether termination should commence.
     * This relies primarily on the active count bits of "ctl"
     * maintaining consensus -- tryTerminate is called from awaitWork
     * whenever quiescent. However, external submitters do not take
     * part in this consensus.  So, tryTerminate sweeps through queues
     * (until stable) to ensure lack of in-flight submissions and
     * workers about to process them before triggering the "STOP"
     * phase of termination. (Note: there is an intrinsic conflict if
     * helpQuiescePool is called when shutdown is enabled. Both wait
     * for quiescence, but tryTerminate is biased to not trigger until
     * helpQuiescePool completes.)
     *
     * Joining Tasks
     * =============
     *
     * Any of several actions may be taken when one worker is waiting
     * to join a task stolen (or always held) by another.  Because we
     * are multiplexing many tasks on to a pool of workers, we can't
     * just let them block (as in Thread.join).  We also cannot just
     * reassign the joiner's run-time stack with another and replace
     * it later, which would be a form of "continuation", that even if
     * possible is not necessarily a good idea since we may need both
     * an unblocked task and its continuation to progress.  Instead we
     * combine two tactics:
     *
     *   Helping: Arranging for the joiner to execute some task that it
     *      would be running if the steal had not occurred.
     *
     *   Compensating: Unless there are already enough live threads,
     *      method tryCompensate() may create or re-activate a spare
     *      thread to compensate for blocked joiners until they unblock.
     *
     * A third form (implemented in tryRemoveAndExec) amounts to
     * helping a hypothetical compensator: If we can readily tell that
     * a possible action of a compensator is to steal and execute the
     * task being joined, the joining thread can do so directly,
     * without the need for a compensation thread (although at the
     * expense of larger run-time stacks, but the tradeoff is
     * typically worthwhile).
     *
     * The ManagedBlocker extension API can't use helping so relies
     * only on compensation in method awaitBlocker.
     *
     * The algorithm in helpStealer entails a form of "linear
     * helping".  Each worker records (in field currentSteal) the most
     * recent task it stole from some other worker (or a submission).
     * It also records (in field currentJoin) the task it is currently
     * actively joining. Method helpStealer uses these markers to try
     * to find a worker to help (i.e., steal back a task from and
     * execute it) that could hasten completion of the actively joined
     * task.  Thus, the joiner executes a task that would be on its
     * own local deque had the to-be-joined task not been stolen. This
     * is a conservative variant of the approach described in Wagner &
     * Calder "Leapfrogging: a portable technique for implementing
     * efficient futures" SIGPLAN Notices, 1993
     * (http://portal.acm.org/citation.cfm?id=155354). It differs in
     * that: (1) We only maintain dependency links across workers upon
     * steals, rather than use per-task bookkeeping.  This sometimes
     * requires a linear scan of workQueues array to locate stealers,
     * but often doesn't because stealers leave hints (that may become
     * stale/wrong) of where to locate them.  It is only a hint
     * because a worker might have had multiple steals and the hint
     * records only one of them (usually the most current).  Hinting
     * isolates cost to when it is needed, rather than adding to
     * per-task overhead.  (2) It is "shallow", ignoring nesting and
     * potentially cyclic mutual steals.  (3) It is intentionally
     * racy: field currentJoin is updated only while actively joining,
     * which means that we miss links in the chain during long-lived
     * tasks, GC stalls etc (which is OK since blocking in such cases
     * is usually a good idea).  (4) We bound the number of attempts
     * to find work using checksums and fall back to suspending the
     * worker and if necessary replacing it with another.
     *
     * Helping actions for CountedCompleters do not require tracking
     * currentJoins: Method helpComplete takes and executes any task
     * with the same root as the task being waited on (preferring
     * local pops to non-local polls). However, this still entails
     * some traversal of completer chains, so is less efficient than
     * using CountedCompleters without explicit joins.
     *
     * Compensation does not aim to keep exactly the target
     * parallelism number of unblocked threads running at any given
     * time. Some previous versions of this class employed immediate
     * compensations for any blocked join. However, in practice, the
     * vast majority of blockages are transient byproducts of GC and
     * other JVM or OS activities that are made worse by replacement.
     * Currently, compensation is attempted only after validating that
     * all purportedly active threads are processing tasks by checking
     * field WorkQueue.scanState, which eliminates most false
     * positives.  Also, compensation is bypassed (tolerating fewer
     * threads) in the most common case in which it is rarely
     * beneficial: when a worker with an empty queue (thus no
     * continuation tasks) blocks on a join and there still remain
     * enough threads to ensure liveness.
     *
     * Spare threads are removed as soon as they notice that the
     * target parallelism level has been exceeded, in method
     * tryDropSpare. (Method scan arranges returns for rechecks upon
     * each probe via the "bound" parameter.)
     *
     * The compensation mechanism may be bounded.  Bounds for the
     * commonPool (see COMMON_MAX_SPARES) better enable JVMs to cope
     * with programming errors and abuse before running out of
     * resources to do so. In other cases, users may supply factories
     * that limit thread construction. The effects of bounding in this
     * pool (like all others) is imprecise.  Total worker counts are
     * decremented when threads deregister, not when they exit and
     * resources are reclaimed by the JVM and OS. So the number of
     * simultaneously live threads may transiently exceed bounds.
     *
     * Common Pool
     * ===========
     *
     * The static common pool always exists after static
     * initialization.  Since it (or any other created pool) need
     * never be used, we minimize initial construction overhead and
     * footprint to the setup of about a dozen fields, with no nested
     * allocation. Most bootstrapping occurs within method
     * externalSubmit during the first submission to the pool.
     *
     * When external threads submit to the common pool, they can
     * perform subtask processing (see externalHelpComplete and
     * related methods) upon joins.  This caller-helps policy makes it
     * sensible to set common pool parallelism level to one (or more)
     * less than the total number of available cores, or even zero for
     * pure caller-runs.  We do not need to record whether external
     * submissions are to the common pool -- if not, external help
     * methods return quickly. These submitters would otherwise be
     * blocked waiting for completion, so the extra effort (with
     * liberally sprinkled task status checks) in inapplicable cases
     * amounts to an odd form of limited spin-wait before blocking in
     * ForkJoinTask.join.
     *
     * As a more appropriate default in managed environments, unless
     * overridden by system properties, we use workers of subclass
     * InnocuousForkJoinWorkerThread when there is a SecurityManager
     * present. These workers have no permissions set, do not belong
     * to any user-defined ThreadGroup, and erase all ThreadLocals
     * after executing any top-level task (see WorkQueue.runTask).
     * The associated mechanics (mainly in ForkJoinWorkerThread) may
     * be JVM-dependent and must access particular Thread class fields
     * to achieve this effect.
     *
     * Style notes
     * ===========
     *
     * Memory ordering relies mainly on Unsafe intrinsics that carry
     * the further responsibility of explicitly performing null- and
     * bounds- checks otherwise carried out implicitly by JVMs.  This
     * can be awkward and ugly, but also reflects the need to control
     * outcomes across the unusual cases that arise in very racy code
     * with very few invariants. So these explicit checks would exist
     * in some form anyway.  All fields are read into locals before
     * use, and null-checked if they are references.  This is usually
     * done in a "C"-like style of listing declarations at the heads
     * of methods or blocks, and using inline assignments on first
     * encounter.  Array bounds-checks are usually performed by
     * masking with array.length-1, which relies on the invariant that
     * these arrays are created with positive lengths, which is itself
     * paranoically checked. Nearly all explicit checks lead to
     * bypass/return, not exception throws, because they may
     * legitimately arise due to cancellation/revocation during
     * shutdown.
     *
     * There is a lot of representation-level coupling among classes
     * ForkJoinPool, ForkJoinWorkerThread, and ForkJoinTask.  The
     * fields of WorkQueue maintain data structures managed by
     * ForkJoinPool, so are directly accessed.  There is little point
     * trying to reduce this, since any associated future changes in
     * representations will need to be accompanied by algorithmic
     * changes anyway. Several methods intrinsically sprawl because
     * they must accumulate sets of consistent reads of fields held in
     * local variables.  There are also other coding oddities
     * (including several unnecessary-looking hoisted null checks)
     * that help some methods perform reasonably even when interpreted
     * (not compiled).
     *
     * The order of declarations in this file is (with a few exceptions):
     * (1) Static utility functions
     * (2) Nested (static) classes
     * (3) Static fields
     * (4) Fields, along with constants used when unpacking some of them
     * (5) Internal control methods
     * (6) Callbacks and other support for ForkJoinTask methods
     * (7) Exported methods
     * (8) Static block initializing statics in minimally dependent order
     */

    // Static utilities

    /**
     * If there is a security manager, makes sure caller has
     * permission to modify threads.
     */
    private static void checkPermission() {
        SecurityManager security = System.getSecurityManager();
        if (security != null)
            security.checkPermission(modifyThreadPermission);
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
     * new ForkJoinWorkerThread.
     */
    private static final class DefaultForkJoinWorkerThreadFactory
        implements ForkJoinWorkerThreadFactory {
        public final ForkJoinWorkerThread newThread(ForkJoinPool pool) {
            return new ForkJoinWorkerThread(pool);
        }
    }

    /**
     * Class for artificial tasks that are used to replace the target
     * of local joins if they are removed from an interior queue slot
     * in WorkQueue.tryRemoveAndExec. We don't need the proxy to
     * actually do anything beyond having a unique identity.
     */
    private static final class EmptyTask extends ForkJoinTask<Void> {
        private static final long serialVersionUID = -7721805057305804111L;
        EmptyTask() { status = ForkJoinTask.NORMAL; } // force done
        public final Void getRawResult() { return null; }
        public final void setRawResult(Void x) {}
        public final boolean exec() { return true; }
    }

    /**
     * Additional fields and lock created upon initialization.
     */
    private static final class AuxState extends ReentrantLock {
        private static final long serialVersionUID = -6001602636862214147L;
        volatile long stealCount;     // cumulative steal count
        long indexSeed;               // index bits for registerWorker
        AuxState() {}
    }

    // Constants shared across ForkJoinPool and WorkQueue

    // Bounds
    static final int SMASK        = 0xffff;        // short bits == max index
    static final int MAX_CAP      = 0x7fff;        // max #workers - 1
    static final int EVENMASK     = 0xfffe;        // even short bits
    static final int SQMASK       = 0x007e;        // max 64 (even) slots

    // Masks and units for WorkQueue.scanState and ctl sp subfield
    static final int UNSIGNALLED  = 1 << 31;       // must be negative
    static final int SS_SEQ       = 1 << 16;       // version count

    // Mode bits for ForkJoinPool.config and WorkQueue.config
    static final int MODE_MASK    = 0xffff << 16;  // top half of int
    static final int SPARE_WORKER = 1 << 17;       // set if tc > 0 on creation
    static final int UNREGISTERED = 1 << 18;       // to skip some of deregister
    static final int FIFO_QUEUE   = 1 << 31;       // must be negative
    static final int LIFO_QUEUE   = 0;             // for clarity
    static final int IS_OWNED     = 1;             // low bit 0 if shared

    /**
     * The maximum number of task executions from the same queue
     * before checking other queues, bounding unfairness and impact of
     * infinite user task recursion.  Must be a power of two minus 1.
     */
    static final int POLL_LIMIT = (1 << 10) - 1;

    /**
     * Queues supporting work-stealing as well as external task
     * submission. See above for descriptions and algorithms.
     * Performance on most platforms is very sensitive to placement of
     * instances of both WorkQueues and their arrays -- we absolutely
     * do not want multiple WorkQueue instances or multiple queue
     * arrays sharing cache lines. The @Contended annotation alerts
     * JVMs to try to keep instances apart.
     */
    @jdk.internal.vm.annotation.Contended
    static final class WorkQueue {

        /**
         * Capacity of work-stealing queue array upon initialization.
         * Must be a power of two; at least 4, but should be larger to
         * reduce or eliminate cacheline sharing among queues.
         * Currently, it is much larger, as a partial workaround for
         * the fact that JVMs often place arrays in locations that
         * share GC bookkeeping (especially cardmarks) such that
         * per-write accesses encounter serious memory contention.
         */
        static final int INITIAL_QUEUE_CAPACITY = 1 << 13;

        /**
         * Maximum size for queue arrays. Must be a power of two less
         * than or equal to 1 << (31 - width of array entry) to ensure
         * lack of wraparound of index calculations, but defined to a
         * value a bit less than this to help users trap runaway
         * programs before saturating systems.
         */
        static final int MAXIMUM_QUEUE_CAPACITY = 1 << 26; // 64M

        // Instance fields

        volatile int scanState;    // versioned, negative if inactive
        int stackPred;             // pool stack (ctl) predecessor
        int nsteals;               // number of steals
        int hint;                  // randomization and stealer index hint
        int config;                // pool index and mode
        volatile int qlock;        // 1: locked, < 0: terminate; else 0
        volatile int base;         // index of next slot for poll
        int top;                   // index of next slot for push
        ForkJoinTask<?>[] array;   // the elements (initially unallocated)
        final ForkJoinPool pool;   // the containing pool (may be null)
        final ForkJoinWorkerThread owner; // owning thread or null if shared
        volatile Thread parker;    // == owner during call to park; else null
        volatile ForkJoinTask<?> currentJoin; // task being joined in awaitJoin

        @jdk.internal.vm.annotation.Contended("group2") // segregate
        volatile ForkJoinTask<?> currentSteal; // nonnull when running some task

        WorkQueue(ForkJoinPool pool, ForkJoinWorkerThread owner) {
            this.pool = pool;
            this.owner = owner;
            // Place indices in the center of array (that is not yet allocated)
            base = top = INITIAL_QUEUE_CAPACITY >>> 1;
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
            int n = base - top;       // read base first
            return (n >= 0) ? 0 : -n; // ignore transient negative
        }

        /**
         * Provides a more accurate estimate of whether this queue has
         * any tasks than does queueSize, by checking whether a
         * near-empty queue has at least one unclaimed task.
         */
        final boolean isEmpty() {
            ForkJoinTask<?>[] a; int n, al, s;
            return ((n = base - (s = top)) >= 0 || // possibly one task
                    (n == -1 && ((a = array) == null ||
                                 (al = a.length) == 0 ||
                                 a[(al - 1) & (s - 1)] == null)));
        }

        /**
         * Pushes a task. Call only by owner in unshared queues.
         *
         * @param task the task. Caller must ensure non-null.
         * @throws RejectedExecutionException if array cannot be resized
         */
        final void push(ForkJoinTask<?> task) {
            U.storeFence();              // ensure safe publication
            int s = top, al, d; ForkJoinTask<?>[] a;
            if ((a = array) != null && (al = a.length) > 0) {
                a[(al - 1) & s] = task;  // relaxed writes OK
                top = s + 1;
                ForkJoinPool p = pool;
                if ((d = base - s) == 0 && p != null) {
                    U.fullFence();
                    p.signalWork();
                }
                else if (al + d == 1)
                    growArray();
            }
        }

        /**
         * Initializes or doubles the capacity of array. Call either
         * by owner or with lock held -- it is OK for base, but not
         * top, to move while resizings are in progress.
         */
        final ForkJoinTask<?>[] growArray() {
            ForkJoinTask<?>[] oldA = array;
            int size = oldA != null ? oldA.length << 1 : INITIAL_QUEUE_CAPACITY;
            if (size < INITIAL_QUEUE_CAPACITY || size > MAXIMUM_QUEUE_CAPACITY)
                throw new RejectedExecutionException("Queue capacity exceeded");
            int oldMask, t, b;
            ForkJoinTask<?>[] a = array = new ForkJoinTask<?>[size];
            if (oldA != null && (oldMask = oldA.length - 1) > 0 &&
                (t = top) - (b = base) > 0) {
                int mask = size - 1;
                do { // emulate poll from old array, push to new array
                    int index = b & oldMask;
                    long offset = ((long)index << ASHIFT) + ABASE;
                    ForkJoinTask<?> x = (ForkJoinTask<?>)
                        U.getObjectVolatile(oldA, offset);
                    if (x != null &&
                        U.compareAndSwapObject(oldA, offset, x, null))
                        a[b & mask] = x;
                } while (++b != t);
                U.storeFence();
            }
            return a;
        }

        /**
         * Takes next task, if one exists, in LIFO order.  Call only
         * by owner in unshared queues.
         */
        final ForkJoinTask<?> pop() {
            int b = base, s = top, al, i; ForkJoinTask<?>[] a;
            if ((a = array) != null && b != s && (al = a.length) > 0) {
                int index = (al - 1) & --s;
                long offset = ((long)index << ASHIFT) + ABASE;
                ForkJoinTask<?> t = (ForkJoinTask<?>)
                    U.getObject(a, offset);
                if (t != null &&
                    U.compareAndSwapObject(a, offset, t, null)) {
                    top = s;
                    return t;
                }
            }
            return null;
        }

        /**
         * Takes a task in FIFO order if b is base of queue and a task
         * can be claimed without contention. Specialized versions
         * appear in ForkJoinPool methods scan and helpStealer.
         */
        final ForkJoinTask<?> pollAt(int b) {
            ForkJoinTask<?>[] a; int al;
            if ((a = array) != null && (al = a.length) > 0) {
                int index = (al - 1) & b;
                long offset = ((long)index << ASHIFT) + ABASE;
                ForkJoinTask<?> t = (ForkJoinTask<?>)
                    U.getObjectVolatile(a, offset);
                if (t != null && b++ == base &&
                    U.compareAndSwapObject(a, offset, t, null)) {
                    base = b;
                    return t;
                }
            }
            return null;
        }

        /**
         * Takes next task, if one exists, in FIFO order.
         */
        final ForkJoinTask<?> poll() {
            for (;;) {
                int b = base, s = top, d, al; ForkJoinTask<?>[] a;
                if ((a = array) != null && (d = b - s) < 0 &&
                    (al = a.length) > 0) {
                    int index = (al - 1) & b;
                    long offset = ((long)index << ASHIFT) + ABASE;
                    ForkJoinTask<?> t = (ForkJoinTask<?>)
                        U.getObjectVolatile(a, offset);
                    if (b++ == base) {
                        if (t != null) {
                            if (U.compareAndSwapObject(a, offset, t, null)) {
                                base = b;
                                return t;
                            }
                        }
                        else if (d == -1)
                            break; // now empty
                    }
                }
                else
                    break;
            }
            return null;
        }

        /**
         * Takes next task, if one exists, in order specified by mode.
         */
        final ForkJoinTask<?> nextLocalTask() {
            return (config < 0) ? poll() : pop();
        }

        /**
         * Returns next task, if one exists, in order specified by mode.
         */
        final ForkJoinTask<?> peek() {
            int al; ForkJoinTask<?>[] a;
            return ((a = array) != null && (al = a.length) > 0) ?
                a[(al - 1) & (config < 0 ? base : top - 1)] : null;
        }

        /**
         * Pops the given task only if it is at the current top.
         */
        final boolean tryUnpush(ForkJoinTask<?> task) {
            int b = base, s = top, al; ForkJoinTask<?>[] a;
            if ((a = array) != null && b != s && (al = a.length) > 0) {
                int index = (al - 1) & --s;
                long offset = ((long)index << ASHIFT) + ABASE;
                if (U.compareAndSwapObject(a, offset, task, null)) {
                    top = s;
                    return true;
                }
            }
            return false;
        }

        /**
         * Shared version of push. Fails if already locked.
         *
         * @return status: > 0 locked, 0 possibly was empty, < 0 was nonempty
         */
        final int sharedPush(ForkJoinTask<?> task) {
            int stat;
            if (U.compareAndSwapInt(this, QLOCK, 0, 1)) {
                int b = base, s = top, al, d; ForkJoinTask<?>[] a;
                if ((a = array) != null && (al = a.length) > 0 &&
                    al - 1 + (d = b - s) > 0) {
                    a[(al - 1) & s] = task;
                    top = s + 1;                 // relaxed writes OK here
                    qlock = 0;
                    stat = (d < 0 && b == base) ? d : 0;
                }
                else {
                    growAndSharedPush(task);
                    stat = 0;
                }
            }
            else
                stat = 1;
            return stat;
        }

        /**
         * Helper for sharedPush; called only when locked and resize
         * needed.
         */
        private void growAndSharedPush(ForkJoinTask<?> task) {
            try {
                growArray();
                int s = top, al; ForkJoinTask<?>[] a;
                if ((a = array) != null && (al = a.length) > 0) {
                    a[(al - 1) & s] = task;
                    top = s + 1;
                }
            } finally {
                qlock = 0;
            }
        }

        /**
         * Shared version of tryUnpush.
         */
        final boolean trySharedUnpush(ForkJoinTask<?> task) {
            boolean popped = false;
            int s = top - 1, al; ForkJoinTask<?>[] a;
            if ((a = array) != null && (al = a.length) > 0) {
                int index = (al - 1) & s;
                long offset = ((long)index << ASHIFT) + ABASE;
                ForkJoinTask<?> t = (ForkJoinTask<?>) U.getObject(a, offset);
                if (t == task &&
                    U.compareAndSwapInt(this, QLOCK, 0, 1)) {
                    if (top == s + 1 && array == a &&
                        U.compareAndSwapObject(a, offset, task, null)) {
                        popped = true;
                        top = s;
                    }
                    U.putOrderedInt(this, QLOCK, 0);
                }
            }
            return popped;
        }

        /**
         * Removes and cancels all known tasks, ignoring any exceptions.
         */
        final void cancelAll() {
            ForkJoinTask<?> t;
            if ((t = currentJoin) != null) {
                currentJoin = null;
                ForkJoinTask.cancelIgnoringExceptions(t);
            }
            if ((t = currentSteal) != null) {
                currentSteal = null;
                ForkJoinTask.cancelIgnoringExceptions(t);
            }
            while ((t = poll()) != null)
                ForkJoinTask.cancelIgnoringExceptions(t);
        }

        // Specialized execution methods

        /**
         * Pops and executes up to POLL_LIMIT tasks or until empty.
         */
        final void localPopAndExec() {
            for (int nexec = 0;;) {
                int b = base, s = top, al; ForkJoinTask<?>[] a;
                if ((a = array) != null && b != s && (al = a.length) > 0) {
                    int index = (al - 1) & --s;
                    long offset = ((long)index << ASHIFT) + ABASE;
                    ForkJoinTask<?> t = (ForkJoinTask<?>)
                        U.getAndSetObject(a, offset, null);
                    if (t != null) {
                        top = s;
                        (currentSteal = t).doExec();
                        if (++nexec > POLL_LIMIT)
                            break;
                    }
                    else
                        break;
                }
                else
                    break;
            }
        }

        /**
         * Polls and executes up to POLL_LIMIT tasks or until empty.
         */
        final void localPollAndExec() {
            for (int nexec = 0;;) {
                int b = base, s = top, al; ForkJoinTask<?>[] a;
                if ((a = array) != null && b != s && (al = a.length) > 0) {
                    int index = (al - 1) & b++;
                    long offset = ((long)index << ASHIFT) + ABASE;
                    ForkJoinTask<?> t = (ForkJoinTask<?>)
                        U.getAndSetObject(a, offset, null);
                    if (t != null) {
                        base = b;
                        t.doExec();
                        if (++nexec > POLL_LIMIT)
                            break;
                    }
                }
                else
                    break;
            }
        }

        /**
         * Executes the given task and (some) remaining local tasks.
         */
        final void runTask(ForkJoinTask<?> task) {
            if (task != null) {
                task.doExec();
                if (config < 0)
                    localPollAndExec();
                else
                    localPopAndExec();
                int ns = ++nsteals;
                ForkJoinWorkerThread thread = owner;
                currentSteal = null;
                if (ns < 0)           // collect on overflow
                    transferStealCount(pool);
                if (thread != null)
                    thread.afterTopLevelExec();
            }
        }

        /**
         * Adds steal count to pool steal count if it exists, and resets.
         */
        final void transferStealCount(ForkJoinPool p) {
            AuxState aux;
            if (p != null && (aux = p.auxState) != null) {
                long s = nsteals;
                nsteals = 0;            // if negative, correct for overflow
                if (s < 0) s = Integer.MAX_VALUE;
                aux.lock();
                try {
                    aux.stealCount += s;
                } finally {
                    aux.unlock();
                }
            }
        }

        /**
         * If present, removes from queue and executes the given task,
         * or any other cancelled task. Used only by awaitJoin.
         *
         * @return true if queue empty and task not known to be done
         */
        final boolean tryRemoveAndExec(ForkJoinTask<?> task) {
            if (task != null && task.status >= 0) {
                int b, s, d, al; ForkJoinTask<?>[] a;
                while ((d = (b = base) - (s = top)) < 0 &&
                       (a = array) != null && (al = a.length) > 0) {
                    for (;;) {      // traverse from s to b
                        int index = --s & (al - 1);
                        long offset = (index << ASHIFT) + ABASE;
                        ForkJoinTask<?> t = (ForkJoinTask<?>)
                            U.getObjectVolatile(a, offset);
                        if (t == null)
                            break;                   // restart
                        else if (t == task) {
                            boolean removed = false;
                            if (s + 1 == top) {      // pop
                                if (U.compareAndSwapObject(a, offset, t, null)) {
                                    top = s;
                                    removed = true;
                                }
                            }
                            else if (base == b)      // replace with proxy
                                removed = U.compareAndSwapObject(a, offset, t,
                                                                 new EmptyTask());
                            if (removed) {
                                ForkJoinTask<?> ps = currentSteal;
                                (currentSteal = task).doExec();
                                currentSteal = ps;
                            }
                            break;
                        }
                        else if (t.status < 0 && s + 1 == top) {
                            if (U.compareAndSwapObject(a, offset, t, null)) {
                                top = s;
                            }
                            break;                  // was cancelled
                        }
                        else if (++d == 0) {
                            if (base != b)          // rescan
                                break;
                            return false;
                        }
                    }
                    if (task.status < 0)
                        return false;
                }
            }
            return true;
        }

        /**
         * Pops task if in the same CC computation as the given task,
         * in either shared or owned mode. Used only by helpComplete.
         */
        final CountedCompleter<?> popCC(CountedCompleter<?> task, int mode) {
            int b = base, s = top, al; ForkJoinTask<?>[] a;
            if ((a = array) != null && b != s && (al = a.length) > 0) {
                int index = (al - 1) & (s - 1);
                long offset = ((long)index << ASHIFT) + ABASE;
                ForkJoinTask<?> o = (ForkJoinTask<?>)
                    U.getObjectVolatile(a, offset);
                if (o instanceof CountedCompleter) {
                    CountedCompleter<?> t = (CountedCompleter<?>)o;
                    for (CountedCompleter<?> r = t;;) {
                        if (r == task) {
                            if ((mode & IS_OWNED) == 0) {
                                boolean popped = false;
                                if (U.compareAndSwapInt(this, QLOCK, 0, 1)) {
                                    if (top == s && array == a &&
                                        U.compareAndSwapObject(a, offset,
                                                               t, null)) {
                                        popped = true;
                                        top = s - 1;
                                    }
                                    U.putOrderedInt(this, QLOCK, 0);
                                    if (popped)
                                        return t;
                                }
                            }
                            else if (U.compareAndSwapObject(a, offset,
                                                            t, null)) {
                                top = s - 1;
                                return t;
                            }
                            break;
                        }
                        else if ((r = r.completer) == null) // try parent
                            break;
                    }
                }
            }
            return null;
        }

        /**
         * Steals and runs a task in the same CC computation as the
         * given task if one exists and can be taken without
         * contention. Otherwise returns a checksum/control value for
         * use by method helpComplete.
         *
         * @return 1 if successful, 2 if retryable (lost to another
         * stealer), -1 if non-empty but no matching task found, else
         * the base index, forced negative.
         */
        final int pollAndExecCC(CountedCompleter<?> task) {
            ForkJoinTask<?>[] a;
            int b = base, s = top, al, h;
            if ((a = array) != null && b != s && (al = a.length) > 0) {
                int index = (al - 1) & b;
                long offset = ((long)index << ASHIFT) + ABASE;
                ForkJoinTask<?> o = (ForkJoinTask<?>)
                    U.getObjectVolatile(a, offset);
                if (o == null)
                    h = 2;                      // retryable
                else if (!(o instanceof CountedCompleter))
                    h = -1;                     // unmatchable
                else {
                    CountedCompleter<?> t = (CountedCompleter<?>)o;
                    for (CountedCompleter<?> r = t;;) {
                        if (r == task) {
                            if (b++ == base &&
                                U.compareAndSwapObject(a, offset, t, null)) {
                                base = b;
                                t.doExec();
                                h = 1;          // success
                            }
                            else
                                h = 2;          // lost CAS
                            break;
                        }
                        else if ((r = r.completer) == null) {
                            h = -1;             // unmatched
                            break;
                        }
                    }
                }
            }
            else
                h = b | Integer.MIN_VALUE;      // to sense movement on re-poll
            return h;
        }

        /**
         * Returns true if owned and not known to be blocked.
         */
        final boolean isApparentlyUnblocked() {
            Thread wt; Thread.State s;
            return (scanState >= 0 &&
                    (wt = owner) != null &&
                    (s = wt.getState()) != Thread.State.BLOCKED &&
                    s != Thread.State.WAITING &&
                    s != Thread.State.TIMED_WAITING);
        }

        // Unsafe mechanics. Note that some are (and must be) the same as in FJP
        private static final jdk.internal.misc.Unsafe U = jdk.internal.misc.Unsafe.getUnsafe();
        private static final long QLOCK;
        private static final int ABASE;
        private static final int ASHIFT;
        static {
            try {
                QLOCK = U.objectFieldOffset
                    (WorkQueue.class.getDeclaredField("qlock"));
                ABASE = U.arrayBaseOffset(ForkJoinTask[].class);
                int scale = U.arrayIndexScale(ForkJoinTask[].class);
                if ((scale & (scale - 1)) != 0)
                    throw new Error("array index scale not a power of two");
                ASHIFT = 31 - Integer.numberOfLeadingZeros(scale);
            } catch (ReflectiveOperationException e) {
                throw new Error(e);
            }
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
     * Permission required for callers of methods that may start or
     * kill threads.  Also used as a static lock in tryInitialize.
     */
    static final RuntimePermission modifyThreadPermission;

    /**
     * Common (static) pool. Non-null for public use unless a static
     * construction exception, but internal usages null-check on use
     * to paranoically avoid potential initialization circularities
     * as well as to simplify generated code.
     */
    static final ForkJoinPool common;

    /**
     * Common pool parallelism. To allow simpler use and management
     * when common pool threads are disabled, we allow the underlying
     * common.parallelism field to be zero, but in that case still report
     * parallelism as 1 to reflect resulting caller-runs mechanics.
     */
    static final int COMMON_PARALLELISM;

    /**
     * Limit on spare thread construction in tryCompensate.
     */
    private static final int COMMON_MAX_SPARES;

    /**
     * Sequence number for creating workerNamePrefix.
     */
    private static int poolNumberSequence;

    /**
     * Returns the next sequence number. We don't expect this to
     * ever contend, so use simple builtin sync.
     */
    private static final synchronized int nextPoolId() {
        return ++poolNumberSequence;
    }

    // static configuration constants

    /**
     * Initial timeout value (in milliseconds) for the thread
     * triggering quiescence to park waiting for new work. On timeout,
     * the thread will instead try to shrink the number of workers.
     * The value should be large enough to avoid overly aggressive
     * shrinkage during most transient stalls (long GCs etc).
     */
    private static final long IDLE_TIMEOUT_MS = 2000L; // 2sec

    /**
     * Tolerance for idle timeouts, to cope with timer undershoots.
     */
    private static final long TIMEOUT_SLOP_MS =   20L; // 20ms

    /**
     * The default value for COMMON_MAX_SPARES.  Overridable using the
     * "java.util.concurrent.ForkJoinPool.common.maximumSpares" system
     * property.  The default value is far in excess of normal
     * requirements, but also far short of MAX_CAP and typical OS
     * thread limits, so allows JVMs to catch misuse/abuse before
     * running out of resources needed to do so.
     */
    private static final int DEFAULT_COMMON_MAX_SPARES = 256;

    /**
     * Increment for seed generators. See class ThreadLocal for
     * explanation.
     */
    private static final int SEED_INCREMENT = 0x9e3779b9;

    /*
     * Bits and masks for field ctl, packed with 4 16 bit subfields:
     * AC: Number of active running workers minus target parallelism
     * TC: Number of total workers minus target parallelism
     * SS: version count and status of top waiting thread
     * ID: poolIndex of top of Treiber stack of waiters
     *
     * When convenient, we can extract the lower 32 stack top bits
     * (including version bits) as sp=(int)ctl.  The offsets of counts
     * by the target parallelism and the positionings of fields makes
     * it possible to perform the most common checks via sign tests of
     * fields: When ac is negative, there are not enough active
     * workers, when tc is negative, there are not enough total
     * workers.  When sp is non-zero, there are waiting workers.  To
     * deal with possibly negative fields, we use casts in and out of
     * "short" and/or signed shifts to maintain signedness.
     *
     * Because it occupies uppermost bits, we can add one active count
     * using getAndAddLong of AC_UNIT, rather than CAS, when returning
     * from a blocked join.  Other updates entail multiple subfields
     * and masking, requiring CAS.
     */

    // Lower and upper word masks
    private static final long SP_MASK    = 0xffffffffL;
    private static final long UC_MASK    = ~SP_MASK;

    // Active counts
    private static final int  AC_SHIFT   = 48;
    private static final long AC_UNIT    = 0x0001L << AC_SHIFT;
    private static final long AC_MASK    = 0xffffL << AC_SHIFT;

    // Total counts
    private static final int  TC_SHIFT   = 32;
    private static final long TC_UNIT    = 0x0001L << TC_SHIFT;
    private static final long TC_MASK    = 0xffffL << TC_SHIFT;
    private static final long ADD_WORKER = 0x0001L << (TC_SHIFT + 15); // sign

    // runState bits: SHUTDOWN must be negative, others arbitrary powers of two
    private static final int  STARTED    = 1;
    private static final int  STOP       = 1 << 1;
    private static final int  TERMINATED = 1 << 2;
    private static final int  SHUTDOWN   = 1 << 31;

    // Instance fields
    volatile long ctl;                   // main pool control
    volatile int runState;
    final int config;                    // parallelism, mode
    AuxState auxState;                   // lock, steal counts
    volatile WorkQueue[] workQueues;     // main registry
    final String workerNamePrefix;       // to create worker name string
    final ForkJoinWorkerThreadFactory factory;
    final UncaughtExceptionHandler ueh;  // per-worker UEH

    /**
     * Instantiates fields upon first submission, or upon shutdown if
     * no submissions. If checkTermination true, also responds to
     * termination by external calls submitting tasks.
     */
    private void tryInitialize(boolean checkTermination) {
        if (runState == 0) { // bootstrap by locking static field
            int p = config & SMASK;
            int n = (p > 1) ? p - 1 : 1; // ensure at least 2 slots
            n |= n >>> 1;    // create workQueues array with size a power of two
            n |= n >>> 2;
            n |= n >>> 4;
            n |= n >>> 8;
            n |= n >>> 16;
            n = ((n + 1) << 1) & SMASK;
            AuxState aux = new AuxState();
            WorkQueue[] ws = new WorkQueue[n];
            synchronized (modifyThreadPermission) { // double-check
                if (runState == 0) {
                    workQueues = ws;
                    auxState = aux;
                    runState = STARTED;
                }
            }
        }
        if (checkTermination && runState < 0) {
            tryTerminate(false, false); // help terminate
            throw new RejectedExecutionException();
        }
    }

    // Creating, registering and deregistering workers

    /**
     * Tries to construct and start one worker. Assumes that total
     * count has already been incremented as a reservation.  Invokes
     * deregisterWorker on any failure.
     *
     * @param isSpare true if this is a spare thread
     * @return true if successful
     */
    private boolean createWorker(boolean isSpare) {
        ForkJoinWorkerThreadFactory fac = factory;
        Throwable ex = null;
        ForkJoinWorkerThread wt = null;
        WorkQueue q;
        try {
            if (fac != null && (wt = fac.newThread(this)) != null) {
                if (isSpare && (q = wt.workQueue) != null)
                    q.config |= SPARE_WORKER;
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
     * Tries to add one worker, incrementing ctl counts before doing
     * so, relying on createWorker to back out on failure.
     *
     * @param c incoming ctl value, with total count negative and no
     * idle workers.  On CAS failure, c is refreshed and retried if
     * this holds (otherwise, a new worker is not needed).
     */
    private void tryAddWorker(long c) {
        do {
            long nc = ((AC_MASK & (c + AC_UNIT)) |
                       (TC_MASK & (c + TC_UNIT)));
            if (ctl == c && U.compareAndSwapLong(this, CTL, c, nc)) {
                createWorker(false);
                break;
            }
        } while (((c = ctl) & ADD_WORKER) != 0L && (int)c == 0);
    }

    /**
     * Callback from ForkJoinWorkerThread constructor to establish and
     * record its WorkQueue.
     *
     * @param wt the worker thread
     * @return the worker's queue
     */
    final WorkQueue registerWorker(ForkJoinWorkerThread wt) {
        UncaughtExceptionHandler handler;
        AuxState aux;
        wt.setDaemon(true);                           // configure thread
        if ((handler = ueh) != null)
            wt.setUncaughtExceptionHandler(handler);
        WorkQueue w = new WorkQueue(this, wt);
        int i = 0;                                    // assign a pool index
        int mode = config & MODE_MASK;
        if ((aux = auxState) != null) {
            aux.lock();
            try {
                int s = (int)(aux.indexSeed += SEED_INCREMENT), n, m;
                WorkQueue[] ws = workQueues;
                if (ws != null && (n = ws.length) > 0) {
                    i = (m = n - 1) & ((s << 1) | 1); // odd-numbered indices
                    if (ws[i] != null) {              // collision
                        int probes = 0;               // step by approx half n
                        int step = (n <= 4) ? 2 : ((n >>> 1) & EVENMASK) + 2;
                        while (ws[i = (i + step) & m] != null) {
                            if (++probes >= n) {
                                workQueues = ws = Arrays.copyOf(ws, n <<= 1);
                                m = n - 1;
                                probes = 0;
                            }
                        }
                    }
                    w.hint = s;                       // use as random seed
                    w.config = i | mode;
                    w.scanState = i | (s & 0x7fff0000); // random seq bits
                    ws[i] = w;
                }
            } finally {
                aux.unlock();
            }
        }
        wt.setName(workerNamePrefix.concat(Integer.toString(i >>> 1)));
        return w;
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
        WorkQueue w = null;
        if (wt != null && (w = wt.workQueue) != null) {
            AuxState aux; WorkQueue[] ws;          // remove index from array
            int idx = w.config & SMASK;
            int ns = w.nsteals;
            if ((aux = auxState) != null) {
                aux.lock();
                try {
                    if ((ws = workQueues) != null && ws.length > idx &&
                        ws[idx] == w)
                        ws[idx] = null;
                    aux.stealCount += ns;
                } finally {
                    aux.unlock();
                }
            }
        }
        if (w == null || (w.config & UNREGISTERED) == 0) { // else pre-adjusted
            long c;                                   // decrement counts
            do {} while (!U.compareAndSwapLong
                         (this, CTL, c = ctl, ((AC_MASK & (c - AC_UNIT)) |
                                               (TC_MASK & (c - TC_UNIT)) |
                                               (SP_MASK & c))));
        }
        if (w != null) {
            w.currentSteal = null;
            w.qlock = -1;                             // ensure set
            w.cancelAll();                            // cancel remaining tasks
        }
        while (tryTerminate(false, false) >= 0) {     // possibly replace
            WorkQueue[] ws; int wl, sp; long c;
            if (w == null || w.array == null ||
                (ws = workQueues) == null || (wl = ws.length) <= 0)
                break;
            else if ((sp = (int)(c = ctl)) != 0) {    // wake up replacement
                if (tryRelease(c, ws[(wl - 1) & sp], AC_UNIT))
                    break;
            }
            else if (ex != null && (c & ADD_WORKER) != 0L) {
                tryAddWorker(c);                      // create replacement
                break;
            }
            else                                      // don't need replacement
                break;
        }
        if (ex == null)                               // help clean on way out
            ForkJoinTask.helpExpungeStaleExceptions();
        else                                          // rethrow
            ForkJoinTask.rethrow(ex);
    }

    // Signalling

    /**
     * Tries to create or activate a worker if too few are active.
     */
    final void signalWork() {
        for (;;) {
            long c; int sp, i; WorkQueue v; WorkQueue[] ws;
            if ((c = ctl) >= 0L)                      // enough workers
                break;
            else if ((sp = (int)c) == 0) {            // no idle workers
                if ((c & ADD_WORKER) != 0L)           // too few workers
                    tryAddWorker(c);
                break;
            }
            else if ((ws = workQueues) == null)
                break;                                // unstarted/terminated
            else if (ws.length <= (i = sp & SMASK))
                break;                                // terminated
            else if ((v = ws[i]) == null)
                break;                                // terminating
            else {
                int ns = sp & ~UNSIGNALLED;
                int vs = v.scanState;
                long nc = (v.stackPred & SP_MASK) | (UC_MASK & (c + AC_UNIT));
                if (sp == vs && U.compareAndSwapLong(this, CTL, c, nc)) {
                    v.scanState = ns;
                    LockSupport.unpark(v.parker);
                    break;
                }
            }
        }
    }

    /**
     * Signals and releases worker v if it is top of idle worker
     * stack.  This performs a one-shot version of signalWork only if
     * there is (apparently) at least one idle worker.
     *
     * @param c incoming ctl value
     * @param v if non-null, a worker
     * @param inc the increment to active count (zero when compensating)
     * @return true if successful
     */
    private boolean tryRelease(long c, WorkQueue v, long inc) {
        int sp = (int)c, ns = sp & ~UNSIGNALLED;
        if (v != null) {
            int vs = v.scanState;
            long nc = (v.stackPred & SP_MASK) | (UC_MASK & (c + inc));
            if (sp == vs && U.compareAndSwapLong(this, CTL, c, nc)) {
                v.scanState = ns;
                LockSupport.unpark(v.parker);
                return true;
            }
        }
        return false;
    }

    /**
     * With approx probability of a missed signal, tries (once) to
     * reactivate worker w (or some other worker), failing if stale or
     * known to be already active.
     *
     * @param w the worker
     * @param ws the workQueue array to use
     * @param r random seed
     */
    private void tryReactivate(WorkQueue w, WorkQueue[] ws, int r) {
        long c; int sp, wl; WorkQueue v;
        if ((sp = (int)(c = ctl)) != 0 && w != null &&
            ws != null && (wl = ws.length) > 0 &&
            ((sp ^ r) & SS_SEQ) == 0 &&
            (v = ws[(wl - 1) & sp]) != null) {
            long nc = (v.stackPred & SP_MASK) | (UC_MASK & (c + AC_UNIT));
            int ns = sp & ~UNSIGNALLED;
            if (w.scanState < 0 &&
                v.scanState == sp &&
                U.compareAndSwapLong(this, CTL, c, nc)) {
                v.scanState = ns;
                LockSupport.unpark(v.parker);
            }
        }
    }

    /**
     * If worker w exists and is active, enqueues and sets status to inactive.
     *
     * @param w the worker
     * @param ss current (non-negative) scanState
     */
    private void inactivate(WorkQueue w, int ss) {
        int ns = (ss + SS_SEQ) | UNSIGNALLED;
        long lc = ns & SP_MASK, nc, c;
        if (w != null) {
            w.scanState = ns;
            do {
                nc = lc | (UC_MASK & ((c = ctl) - AC_UNIT));
                w.stackPred = (int)c;
            } while (!U.compareAndSwapLong(this, CTL, c, nc));
        }
    }

    /**
     * Possibly blocks worker w waiting for signal, or returns
     * negative status if the worker should terminate. May return
     * without status change if multiple stale unparks and/or
     * interrupts occur.
     *
     * @param w the calling worker
     * @return negative if w should terminate
     */
    private int awaitWork(WorkQueue w) {
        int stat = 0;
        if (w != null && w.scanState < 0) {
            long c = ctl;
            if ((int)(c >> AC_SHIFT) + (config & SMASK) <= 0)
                stat = timedAwaitWork(w, c);     // possibly quiescent
            else if ((runState & STOP) != 0)
                stat = w.qlock = -1;             // pool terminating
            else if (w.scanState < 0) {
                w.parker = Thread.currentThread();
                if (w.scanState < 0)             // recheck after write
                    LockSupport.park(this);
                w.parker = null;
                if ((runState & STOP) != 0)
                    stat = w.qlock = -1;         // recheck
                else if (w.scanState < 0)
                    Thread.interrupted();        // clear status
            }
        }
        return stat;
    }

    /**
     * Possibly triggers shutdown and tries (once) to block worker
     * when pool is (or may be) quiescent. Waits up to a duration
     * determined by number of workers.  On timeout, if ctl has not
     * changed, terminates the worker, which will in turn wake up
     * another worker to possibly repeat this process.
     *
     * @param w the calling worker
     * @return negative if w should terminate
     */
    private int timedAwaitWork(WorkQueue w, long c) {
        int stat = 0;
        int scale = 1 - (short)(c >>> TC_SHIFT);
        long deadline = (((scale <= 0) ? 1 : scale) * IDLE_TIMEOUT_MS +
                         System.currentTimeMillis());
        if ((runState >= 0 || (stat = tryTerminate(false, false)) > 0) &&
            w != null && w.scanState < 0) {
            int ss; AuxState aux;
            w.parker = Thread.currentThread();
            if (w.scanState < 0)
                LockSupport.parkUntil(this, deadline);
            w.parker = null;
            if ((runState & STOP) != 0)
                stat = w.qlock = -1;         // pool terminating
            else if ((ss = w.scanState) < 0 && !Thread.interrupted() &&
                     (int)c == ss && (aux = auxState) != null && ctl == c &&
                     deadline - System.currentTimeMillis() <= TIMEOUT_SLOP_MS) {
                aux.lock();
                try {                        // pre-deregister
                    WorkQueue[] ws;
                    int cfg = w.config, idx = cfg & SMASK;
                    long nc = ((UC_MASK & (c - TC_UNIT)) |
                               (SP_MASK & w.stackPred));
                    if ((runState & STOP) == 0 &&
                        (ws = workQueues) != null &&
                        idx < ws.length && idx >= 0 && ws[idx] == w &&
                        U.compareAndSwapLong(this, CTL, c, nc)) {
                        ws[idx] = null;
                        w.config = cfg | UNREGISTERED;
                        stat = w.qlock = -1;
                    }
                } finally {
                    aux.unlock();
                }
            }
        }
        return stat;
    }

    /**
     * If the given worker is a spare with no queued tasks, and there
     * are enough existing workers, drops it from ctl counts and sets
     * its state to terminated.
     *
     * @param w the calling worker -- must be a spare
     * @return true if dropped (in which case it must not process more tasks)
     */
    private boolean tryDropSpare(WorkQueue w) {
        if (w != null && w.isEmpty()) {           // no local tasks
            long c; int sp, wl; WorkQueue[] ws; WorkQueue v;
            while ((short)((c = ctl) >> TC_SHIFT) > 0 &&
                   ((sp = (int)c) != 0 || (int)(c >> AC_SHIFT) > 0) &&
                   (ws = workQueues) != null && (wl = ws.length) > 0) {
                boolean dropped, canDrop;
                if (sp == 0) {                    // no queued workers
                    long nc = ((AC_MASK & (c - AC_UNIT)) |
                               (TC_MASK & (c - TC_UNIT)) | (SP_MASK & c));
                    dropped = U.compareAndSwapLong(this, CTL, c, nc);
                }
                else if (
                    (v = ws[(wl - 1) & sp]) == null || v.scanState != sp)
                    dropped = false;              // stale; retry
                else {
                    long nc = v.stackPred & SP_MASK;
                    if (w == v || w.scanState >= 0) {
                        canDrop = true;           // w unqueued or topmost
                        nc |= ((AC_MASK & c) |    // ensure replacement
                               (TC_MASK & (c - TC_UNIT)));
                    }
                    else {                        // w may be queued
                        canDrop = false;          // help uncover
                        nc |= ((AC_MASK & (c + AC_UNIT)) |
                               (TC_MASK & c));
                    }
                    if (U.compareAndSwapLong(this, CTL, c, nc)) {
                        v.scanState = sp & ~UNSIGNALLED;
                        LockSupport.unpark(v.parker);
                        dropped = canDrop;
                    }
                    else
                        dropped = false;
                }
                if (dropped) {                    // pre-deregister
                    int cfg = w.config, idx = cfg & SMASK;
                    if (idx >= 0 && idx < ws.length && ws[idx] == w)
                        ws[idx] = null;
                    w.config = cfg | UNREGISTERED;
                    w.qlock = -1;
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Top-level runloop for workers, called by ForkJoinWorkerThread.run.
     */
    final void runWorker(WorkQueue w) {
        w.growArray();                                  // allocate queue
        int bound = (w.config & SPARE_WORKER) != 0 ? 0 : POLL_LIMIT;
        long seed = w.hint * 0xdaba0b6eb09322e3L;       // initial random seed
        if ((runState & STOP) == 0) {
            for (long r = (seed == 0L) ? 1L : seed;;) { // ensure nonzero
                if (bound == 0 && tryDropSpare(w))
                    break;
                // high bits of prev seed for step; current low bits for idx
                int step = (int)(r >>> 48) | 1;
                r ^= r >>> 12; r ^= r << 25; r ^= r >>> 27; // xorshift
                if (scan(w, bound, step, (int)r) < 0 && awaitWork(w) < 0)
                    break;
            }
        }
    }

    // Scanning for tasks

    /**
     * Repeatedly scans for and tries to steal and execute (via
     * workQueue.runTask) a queued task. Each scan traverses queues in
     * pseudorandom permutation. Upon finding a non-empty queue, makes
     * at most the given bound attempts to re-poll (fewer if
     * contended) on the same queue before returning (impossible
     * scanState value) 0 to restart scan. Else returns after at least
     * 1 and at most 32 full scans.
     *
     * @param w the worker (via its WorkQueue)
     * @param bound repoll bound as bitmask (0 if spare)
     * @param step (circular) index increment per iteration (must be odd)
     * @param r a random seed for origin index
     * @return negative if should await signal
     */
    private int scan(WorkQueue w, int bound, int step, int r) {
        int stat = 0, wl; WorkQueue[] ws;
        if ((ws = workQueues) != null && w != null && (wl = ws.length) > 0) {
            for (int m = wl - 1,
                     origin = m & r, idx = origin,
                     npolls = 0,
                     ss = w.scanState;;) {         // negative if inactive
                WorkQueue q; ForkJoinTask<?>[] a; int b, al;
                if ((q = ws[idx]) != null && (b = q.base) - q.top < 0 &&
                    (a = q.array) != null && (al = a.length) > 0) {
                    int index = (al - 1) & b;
                    long offset = ((long)index << ASHIFT) + ABASE;
                    ForkJoinTask<?> t = (ForkJoinTask<?>)
                        U.getObjectVolatile(a, offset);
                    if (t == null)
                        break;                     // empty or busy
                    else if (b++ != q.base)
                        break;                     // busy
                    else if (ss < 0) {
                        tryReactivate(w, ws, r);
                        break;                     // retry upon rescan
                    }
                    else if (!U.compareAndSwapObject(a, offset, t, null))
                        break;                     // contended
                    else {
                        q.base = b;
                        w.currentSteal = t;
                        if (b != q.top)            // propagate signal
                            signalWork();
                        w.runTask(t);
                        if (++npolls > bound)
                            break;
                    }
                }
                else if (npolls != 0)              // rescan
                    break;
                else if ((idx = (idx + step) & m) == origin) {
                    if (ss < 0) {                  // await signal
                        stat = ss;
                        break;
                    }
                    else if (r >= 0) {
                        inactivate(w, ss);
                        break;
                    }
                    else
                        r <<= 1;                   // at most 31 rescans
                }
            }
        }
        return stat;
    }

    // Joining tasks

    /**
     * Tries to steal and run tasks within the target's computation.
     * Uses a variant of the top-level algorithm, restricted to tasks
     * with the given task as ancestor: It prefers taking and running
     * eligible tasks popped from the worker's own queue (via
     * popCC). Otherwise it scans others, randomly moving on
     * contention or execution, deciding to give up based on a
     * checksum (via return codes from pollAndExecCC). The maxTasks
     * argument supports external usages; internal calls use zero,
     * allowing unbounded steps (external calls trap non-positive
     * values).
     *
     * @param w caller
     * @param maxTasks if non-zero, the maximum number of other tasks to run
     * @return task status on exit
     */
    final int helpComplete(WorkQueue w, CountedCompleter<?> task,
                           int maxTasks) {
        WorkQueue[] ws; int s = 0, wl;
        if ((ws = workQueues) != null && (wl = ws.length) > 1 &&
            task != null && w != null) {
            for (int m = wl - 1,
                     mode = w.config,
                     r = ~mode,                  // scanning seed
                     origin = r & m, k = origin, // first queue to scan
                     step = 3,                   // first scan step
                     h = 1,                      // 1:ran, >1:contended, <0:hash
                     oldSum = 0, checkSum = 0;;) {
                CountedCompleter<?> p; WorkQueue q; int i;
                if ((s = task.status) < 0)
                    break;
                if (h == 1 && (p = w.popCC(task, mode)) != null) {
                    p.doExec();                  // run local task
                    if (maxTasks != 0 && --maxTasks == 0)
                        break;
                    origin = k;                  // reset
                    oldSum = checkSum = 0;
                }
                else {                           // poll other worker queues
                    if ((i = k | 1) < 0 || i > m || (q = ws[i]) == null)
                        h = 0;
                    else if ((h = q.pollAndExecCC(task)) < 0)
                        checkSum += h;
                    if (h > 0) {
                        if (h == 1 && maxTasks != 0 && --maxTasks == 0)
                            break;
                        step = (r >>> 16) | 3;
                        r ^= r << 13; r ^= r >>> 17; r ^= r << 5; // xorshift
                        k = origin = r & m;      // move and restart
                        oldSum = checkSum = 0;
                    }
                    else if ((k = (k + step) & m) == origin) {
                        if (oldSum == (oldSum = checkSum))
                            break;
                        checkSum = 0;
                    }
                }
            }
        }
        return s;
    }

    /**
     * Tries to locate and execute tasks for a stealer of the given
     * task, or in turn one of its stealers. Traces currentSteal ->
     * currentJoin links looking for a thread working on a descendant
     * of the given task and with a non-empty queue to steal back and
     * execute tasks from. The first call to this method upon a
     * waiting join will often entail scanning/search, (which is OK
     * because the joiner has nothing better to do), but this method
     * leaves hints in workers to speed up subsequent calls.
     *
     * @param w caller
     * @param task the task to join
     */
    private void helpStealer(WorkQueue w, ForkJoinTask<?> task) {
        if (task != null && w != null) {
            ForkJoinTask<?> ps = w.currentSteal;
            WorkQueue[] ws; int wl, oldSum = 0;
            outer: while (w.tryRemoveAndExec(task) && task.status >= 0 &&
                          (ws = workQueues) != null && (wl = ws.length) > 0) {
                ForkJoinTask<?> subtask;
                int m = wl - 1, checkSum = 0;          // for stability check
                WorkQueue j = w, v;                    // v is subtask stealer
                descent: for (subtask = task; subtask.status >= 0; ) {
                    for (int h = j.hint | 1, k = 0, i;;) {
                        if ((v = ws[i = (h + (k << 1)) & m]) != null) {
                            if (v.currentSteal == subtask) {
                                j.hint = i;
                                break;
                            }
                            checkSum += v.base;
                        }
                        if (++k > m)                   // can't find stealer
                            break outer;
                    }

                    for (;;) {                         // help v or descend
                        ForkJoinTask<?>[] a; int b, al;
                        if (subtask.status < 0)        // too late to help
                            break descent;
                        checkSum += (b = v.base);
                        ForkJoinTask<?> next = v.currentJoin;
                        ForkJoinTask<?> t = null;
                        if ((a = v.array) != null && (al = a.length) > 0) {
                            int index = (al - 1) & b;
                            long offset = ((long)index << ASHIFT) + ABASE;
                            t = (ForkJoinTask<?>)
                                U.getObjectVolatile(a, offset);
                            if (t != null && b++ == v.base) {
                                if (j.currentJoin != subtask ||
                                    v.currentSteal != subtask ||
                                    subtask.status < 0)
                                    break descent;     // stale
                                if (U.compareAndSwapObject(a, offset, t, null)) {
                                    v.base = b;
                                    w.currentSteal = t;
                                    for (int top = w.top;;) {
                                        t.doExec();    // help
                                        w.currentSteal = ps;
                                        if (task.status < 0)
                                            break outer;
                                        if (w.top == top)
                                            break;     // run local tasks
                                        if ((t = w.pop()) == null)
                                            break descent;
                                        w.currentSteal = t;
                                    }
                                }
                            }
                        }
                        if (t == null && b == v.base && b - v.top >= 0) {
                            if ((subtask = next) == null) {  // try to descend
                                if (next == v.currentJoin &&
                                    oldSum == (oldSum = checkSum))
                                    break outer;
                                break descent;
                            }
                            j = v;
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * Tries to decrement active count (sometimes implicitly) and
     * possibly release or create a compensating worker in preparation
     * for blocking. Returns false (retryable by caller), on
     * contention, detected staleness, instability, or termination.
     *
     * @param w caller
     */
    private boolean tryCompensate(WorkQueue w) {
        boolean canBlock; int wl;
        long c = ctl;
        WorkQueue[] ws = workQueues;
        int pc = config & SMASK;
        int ac = pc + (int)(c >> AC_SHIFT);
        int tc = pc + (short)(c >> TC_SHIFT);
        if (w == null || w.qlock < 0 || pc == 0 ||  // terminating or disabled
            ws == null || (wl = ws.length) <= 0)
            canBlock = false;
        else {
            int m = wl - 1, sp;
            boolean busy = true;                    // validate ac
            for (int i = 0; i <= m; ++i) {
                int k; WorkQueue v;
                if ((k = (i << 1) | 1) <= m && k >= 0 && (v = ws[k]) != null &&
                    v.scanState >= 0 && v.currentSteal == null) {
                    busy = false;
                    break;
                }
            }
            if (!busy || ctl != c)
                canBlock = false;                   // unstable or stale
            else if ((sp = (int)c) != 0)            // release idle worker
                canBlock = tryRelease(c, ws[m & sp], 0L);
            else if (tc >= pc && ac > 1 && w.isEmpty()) {
                long nc = ((AC_MASK & (c - AC_UNIT)) |
                           (~AC_MASK & c));         // uncompensated
                canBlock = U.compareAndSwapLong(this, CTL, c, nc);
            }
            else if (tc >= MAX_CAP ||
                     (this == common && tc >= pc + COMMON_MAX_SPARES))
                throw new RejectedExecutionException(
                    "Thread limit exceeded replacing blocked worker");
            else {                                  // similar to tryAddWorker
                boolean isSpare = (tc >= pc);
                long nc = (AC_MASK & c) | (TC_MASK & (c + TC_UNIT));
                canBlock = (U.compareAndSwapLong(this, CTL, c, nc) &&
                            createWorker(isSpare)); // throws on exception
            }
        }
        return canBlock;
    }

    /**
     * Helps and/or blocks until the given task is done or timeout.
     *
     * @param w caller
     * @param task the task
     * @param deadline for timed waits, if nonzero
     * @return task status on exit
     */
    final int awaitJoin(WorkQueue w, ForkJoinTask<?> task, long deadline) {
        int s = 0;
        if (w != null) {
            ForkJoinTask<?> prevJoin = w.currentJoin;
            if (task != null && (s = task.status) >= 0) {
                w.currentJoin = task;
                CountedCompleter<?> cc = (task instanceof CountedCompleter) ?
                    (CountedCompleter<?>)task : null;
                for (;;) {
                    if (cc != null)
                        helpComplete(w, cc, 0);
                    else
                        helpStealer(w, task);
                    if ((s = task.status) < 0)
                        break;
                    long ms, ns;
                    if (deadline == 0L)
                        ms = 0L;
                    else if ((ns = deadline - System.nanoTime()) <= 0L)
                        break;
                    else if ((ms = TimeUnit.NANOSECONDS.toMillis(ns)) <= 0L)
                        ms = 1L;
                    if (tryCompensate(w)) {
                        task.internalWait(ms);
                        U.getAndAddLong(this, CTL, AC_UNIT);
                    }
                    if ((s = task.status) < 0)
                        break;
                }
                w.currentJoin = prevJoin;
            }
        }
        return s;
    }

    // Specialized scanning

    /**
     * Returns a (probably) non-empty steal queue, if one is found
     * during a scan, else null.  This method must be retried by
     * caller if, by the time it tries to use the queue, it is empty.
     */
    private WorkQueue findNonEmptyStealQueue() {
        WorkQueue[] ws; int wl;  // one-shot version of scan loop
        int r = ThreadLocalRandom.nextSecondarySeed();
        if ((ws = workQueues) != null && (wl = ws.length) > 0) {
            int m = wl - 1, origin = r & m;
            for (int k = origin, oldSum = 0, checkSum = 0;;) {
                WorkQueue q; int b;
                if ((q = ws[k]) != null) {
                    if ((b = q.base) - q.top < 0)
                        return q;
                    checkSum += b;
                }
                if ((k = (k + 1) & m) == origin) {
                    if (oldSum == (oldSum = checkSum))
                        break;
                    checkSum = 0;
                }
            }
        }
        return null;
    }

    /**
     * Runs tasks until {@code isQuiescent()}. We piggyback on
     * active count ctl maintenance, but rather than blocking
     * when tasks cannot be found, we rescan until all others cannot
     * find tasks either.
     */
    final void helpQuiescePool(WorkQueue w) {
        ForkJoinTask<?> ps = w.currentSteal; // save context
        int wc = w.config;
        for (boolean active = true;;) {
            long c; WorkQueue q; ForkJoinTask<?> t;
            if (wc >= 0 && (t = w.pop()) != null) { // run locals if LIFO
                (w.currentSteal = t).doExec();
                w.currentSteal = ps;
            }
            else if ((q = findNonEmptyStealQueue()) != null) {
                if (!active) {      // re-establish active count
                    active = true;
                    U.getAndAddLong(this, CTL, AC_UNIT);
                }
                if ((t = q.pollAt(q.base)) != null) {
                    (w.currentSteal = t).doExec();
                    w.currentSteal = ps;
                    if (++w.nsteals < 0)
                        w.transferStealCount(this);
                }
            }
            else if (active) {      // decrement active count without queuing
                long nc = (AC_MASK & ((c = ctl) - AC_UNIT)) | (~AC_MASK & c);
                if (U.compareAndSwapLong(this, CTL, c, nc))
                    active = false;
            }
            else if ((int)((c = ctl) >> AC_SHIFT) + (config & SMASK) <= 0 &&
                     U.compareAndSwapLong(this, CTL, c, c + AC_UNIT))
                break;
        }
    }

    /**
     * Gets and removes a local or stolen task for the given worker.
     *
     * @return a task, if available
     */
    final ForkJoinTask<?> nextTaskFor(WorkQueue w) {
        for (ForkJoinTask<?> t;;) {
            WorkQueue q;
            if ((t = w.nextLocalTask()) != null)
                return t;
            if ((q = findNonEmptyStealQueue()) == null)
                return null;
            if ((t = q.pollAt(q.base)) != null)
                return t;
        }
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
        if ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) {
            int p = (pool = (wt = (ForkJoinWorkerThread)t).pool).config & SMASK;
            int n = (q = wt.workQueue).top - q.base;
            int a = (int)(pool.ctl >> AC_SHIFT) + p;
            return n - (a > (p >>>= 1) ? 0 :
                        a > (p >>>= 1) ? 1 :
                        a > (p >>>= 1) ? 2 :
                        a > (p >>>= 1) ? 4 :
                        8);
        }
        return 0;
    }

    //  Termination

    /**
     * Possibly initiates and/or completes termination.
     *
     * @param now if true, unconditionally terminate, else only
     * if no work and no active workers
     * @param enable if true, terminate when next possible
     * @return -1: terminating/terminated, 0: retry if internal caller, else 1
     */
    private int tryTerminate(boolean now, boolean enable) {
        int rs; // 3 phases: try to set SHUTDOWN, then STOP, then TERMINATED

        while ((rs = runState) >= 0) {
            if (!enable || this == common)        // cannot shutdown
                return 1;
            else if (rs == 0)
                tryInitialize(false);             // ensure initialized
            else
                U.compareAndSwapInt(this, RUNSTATE, rs, rs | SHUTDOWN);
        }

        if ((rs & STOP) == 0) {                   // try to initiate termination
            if (!now) {                           // check quiescence
                for (long oldSum = 0L;;) {        // repeat until stable
                    WorkQueue[] ws; WorkQueue w; int b;
                    long checkSum = ctl;
                    if ((int)(checkSum >> AC_SHIFT) + (config & SMASK) > 0)
                        return 0;                 // still active workers
                    if ((ws = workQueues) != null) {
                        for (int i = 0; i < ws.length; ++i) {
                            if ((w = ws[i]) != null) {
                                checkSum += (b = w.base);
                                if (w.currentSteal != null || b != w.top)
                                    return 0;     // retry if internal caller
                            }
                        }
                    }
                    if (oldSum == (oldSum = checkSum))
                        break;
                }
            }
            do {} while (!U.compareAndSwapInt(this, RUNSTATE,
                                              rs = runState, rs | STOP));
        }

        for (long oldSum = 0L;;) {                // repeat until stable
            WorkQueue[] ws; WorkQueue w; ForkJoinWorkerThread wt;
            long checkSum = ctl;
            if ((ws = workQueues) != null) {      // help terminate others
                for (int i = 0; i < ws.length; ++i) {
                    if ((w = ws[i]) != null) {
                        w.cancelAll();            // clear queues
                        checkSum += w.base;
                        if (w.qlock >= 0) {
                            w.qlock = -1;         // racy set OK
                            if ((wt = w.owner) != null) {
                                try {             // unblock join or park
                                    wt.interrupt();
                                } catch (Throwable ignore) {
                                }
                            }
                        }
                    }
                }
            }
            if (oldSum == (oldSum = checkSum))
                break;
        }

        if ((short)(ctl >>> TC_SHIFT) + (config & SMASK) <= 0) {
            runState = (STARTED | SHUTDOWN | STOP | TERMINATED); // final write
            synchronized (this) {
                notifyAll();                      // for awaitTermination
            }
        }

        return -1;
    }

    // External operations

    /**
     * Constructs and tries to install a new external queue,
     * failing if the workQueues array already has a queue at
     * the given index.
     *
     * @param index the index of the new queue
     */
    private void tryCreateExternalQueue(int index) {
        AuxState aux;
        if ((aux = auxState) != null && index >= 0) {
            WorkQueue q = new WorkQueue(this, null);
            q.config = index;
            q.scanState = ~UNSIGNALLED;
            q.qlock = 1;                   // lock queue
            boolean installed = false;
            aux.lock();
            try {                          // lock pool to install
                WorkQueue[] ws;
                if ((ws = workQueues) != null && index < ws.length &&
                    ws[index] == null) {
                    ws[index] = q;         // else throw away
                    installed = true;
                }
            } finally {
                aux.unlock();
            }
            if (installed) {
                try {
                    q.growArray();
                } finally {
                    q.qlock = 0;
                }
            }
        }
    }

    /**
     * Adds the given task to a submission queue at submitter's
     * current queue. Also performs secondary initialization upon the
     * first submission of the first task to the pool, and detects
     * first submission by an external thread and creates a new shared
     * queue if the one at index if empty or contended.
     *
     * @param task the task. Caller must ensure non-null.
     */
    final void externalPush(ForkJoinTask<?> task) {
        int r;                            // initialize caller's probe
        if ((r = ThreadLocalRandom.getProbe()) == 0) {
            ThreadLocalRandom.localInit();
            r = ThreadLocalRandom.getProbe();
        }
        for (;;) {
            WorkQueue q; int wl, k, stat;
            int rs = runState;
            WorkQueue[] ws = workQueues;
            if (rs <= 0 || ws == null || (wl = ws.length) <= 0)
                tryInitialize(true);
            else if ((q = ws[k = (wl - 1) & r & SQMASK]) == null)
                tryCreateExternalQueue(k);
            else if ((stat = q.sharedPush(task)) < 0)
                break;
            else if (stat == 0) {
                signalWork();
                break;
            }
            else                          // move if busy
                r = ThreadLocalRandom.advanceProbe(r);
        }
    }

    /**
     * Pushes a possibly-external submission.
     */
    private <T> ForkJoinTask<T> externalSubmit(ForkJoinTask<T> task) {
        Thread t; ForkJoinWorkerThread w; WorkQueue q;
        if (task == null)
            throw new NullPointerException();
        if (((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) &&
            (w = (ForkJoinWorkerThread)t).pool == this &&
            (q = w.workQueue) != null)
            q.push(task);
        else
            externalPush(task);
        return task;
    }

    /**
     * Returns common pool queue for an external thread.
     */
    static WorkQueue commonSubmitterQueue() {
        ForkJoinPool p = common;
        int r = ThreadLocalRandom.getProbe();
        WorkQueue[] ws; int wl;
        return (p != null && (ws = p.workQueues) != null &&
                (wl = ws.length) > 0) ?
            ws[(wl - 1) & r & SQMASK] : null;
    }

    /**
     * Performs tryUnpush for an external submitter.
     */
    final boolean tryExternalUnpush(ForkJoinTask<?> task) {
        int r = ThreadLocalRandom.getProbe();
        WorkQueue[] ws; WorkQueue w; int wl;
        return ((ws = workQueues) != null &&
                (wl = ws.length) > 0 &&
                (w = ws[(wl - 1) & r & SQMASK]) != null &&
                w.trySharedUnpush(task));
    }

    /**
     * Performs helpComplete for an external submitter.
     */
    final int externalHelpComplete(CountedCompleter<?> task, int maxTasks) {
        WorkQueue[] ws; int wl;
        int r = ThreadLocalRandom.getProbe();
        return ((ws = workQueues) != null && (wl = ws.length) > 0) ?
            helpComplete(ws[(wl - 1) & r & SQMASK], task, maxTasks) : 0;
    }

    // Exported methods

    // Constructors

    /**
     * Creates a {@code ForkJoinPool} with parallelism equal to {@link
     * java.lang.Runtime#availableProcessors}, using the {@linkplain
     * #defaultForkJoinWorkerThreadFactory default thread factory},
     * no UncaughtExceptionHandler, and non-async LIFO processing mode.
     *
     * @throws SecurityException if a security manager exists and
     *         the caller is not permitted to modify threads
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}{@code ("modifyThread")}
     */
    public ForkJoinPool() {
        this(Math.min(MAX_CAP, Runtime.getRuntime().availableProcessors()),
             defaultForkJoinWorkerThreadFactory, null, false);
    }

    /**
     * Creates a {@code ForkJoinPool} with the indicated parallelism
     * level, the {@linkplain
     * #defaultForkJoinWorkerThreadFactory default thread factory},
     * no UncaughtExceptionHandler, and non-async LIFO processing mode.
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
        this(parallelism, defaultForkJoinWorkerThreadFactory, null, false);
    }

    /**
     * Creates a {@code ForkJoinPool} with the given parameters.
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
        this(checkParallelism(parallelism),
             checkFactory(factory),
             handler,
             asyncMode ? FIFO_QUEUE : LIFO_QUEUE,
             "ForkJoinPool-" + nextPoolId() + "-worker-");
        checkPermission();
    }

    private static int checkParallelism(int parallelism) {
        if (parallelism <= 0 || parallelism > MAX_CAP)
            throw new IllegalArgumentException();
        return parallelism;
    }

    private static ForkJoinWorkerThreadFactory checkFactory
        (ForkJoinWorkerThreadFactory factory) {
        if (factory == null)
            throw new NullPointerException();
        return factory;
    }

    /**
     * Creates a {@code ForkJoinPool} with the given parameters, without
     * any security checks or parameter validation.  Invoked directly by
     * makeCommonPool.
     */
    private ForkJoinPool(int parallelism,
                         ForkJoinWorkerThreadFactory factory,
                         UncaughtExceptionHandler handler,
                         int mode,
                         String workerNamePrefix) {
        this.workerNamePrefix = workerNamePrefix;
        this.factory = factory;
        this.ueh = handler;
        this.config = (parallelism & SMASK) | mode;
        long np = (long)(-parallelism); // offset ctl counts
        this.ctl = ((np << AC_SHIFT) & AC_MASK) | ((np << TC_SHIFT) & TC_MASK);
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
        if (task == null)
            throw new NullPointerException();
        externalSubmit(task);
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
        externalSubmit(task);
    }

    // AbstractExecutorService methods

    /**
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     */
    public void execute(Runnable task) {
        if (task == null)
            throw new NullPointerException();
        ForkJoinTask<?> job;
        if (task instanceof ForkJoinTask<?>) // avoid re-wrap
            job = (ForkJoinTask<?>) task;
        else
            job = new ForkJoinTask.RunnableExecuteAction(task);
        externalSubmit(job);
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
        return externalSubmit(task);
    }

    /**
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     */
    public <T> ForkJoinTask<T> submit(Callable<T> task) {
        return externalSubmit(new ForkJoinTask.AdaptedCallable<T>(task));
    }

    /**
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     */
    public <T> ForkJoinTask<T> submit(Runnable task, T result) {
        return externalSubmit(new ForkJoinTask.AdaptedRunnable<T>(task, result));
    }

    /**
     * @throws NullPointerException if the task is null
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     */
    public ForkJoinTask<?> submit(Runnable task) {
        if (task == null)
            throw new NullPointerException();
        ForkJoinTask<?> job;
        if (task instanceof ForkJoinTask<?>) // avoid re-wrap
            job = (ForkJoinTask<?>) task;
        else
            job = new ForkJoinTask.AdaptedRunnableAction(task);
        return externalSubmit(job);
    }

    /**
     * @throws NullPointerException       {@inheritDoc}
     * @throws RejectedExecutionException {@inheritDoc}
     */
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) {
        // In previous versions of this class, this method constructed
        // a task to run ForkJoinTask.invokeAll, but now external
        // invocation of multiple tasks is at least as efficient.
        ArrayList<Future<T>> futures = new ArrayList<>(tasks.size());

        try {
            for (Callable<T> t : tasks) {
                ForkJoinTask<T> f = new ForkJoinTask.AdaptedCallable<T>(t);
                futures.add(f);
                externalSubmit(f);
            }
            for (int i = 0, size = futures.size(); i < size; i++)
                ((ForkJoinTask<?>)futures.get(i)).quietlyJoin();
            return futures;
        } catch (Throwable t) {
            for (int i = 0, size = futures.size(); i < size; i++)
                futures.get(i).cancel(false);
            throw t;
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
        int par;
        return ((par = config & SMASK) > 0) ? par : 1;
    }

    /**
     * Returns the targeted parallelism level of the common pool.
     *
     * @return the targeted parallelism level of the common pool
     * @since 1.8
     */
    public static int getCommonPoolParallelism() {
        return COMMON_PARALLELISM;
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
        return (config & SMASK) + (short)(ctl >>> TC_SHIFT);
    }

    /**
     * Returns {@code true} if this pool uses local first-in-first-out
     * scheduling mode for forked tasks that are never joined.
     *
     * @return {@code true} if this pool uses async mode
     */
    public boolean getAsyncMode() {
        return (config & FIFO_QUEUE) != 0;
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
        int rc = 0;
        WorkQueue[] ws; WorkQueue w;
        if ((ws = workQueues) != null) {
            for (int i = 1; i < ws.length; i += 2) {
                if ((w = ws[i]) != null && w.isApparentlyUnblocked())
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
        int r = (config & SMASK) + (int)(ctl >> AC_SHIFT);
        return (r <= 0) ? 0 : r; // suppress momentarily negative values
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
        return (config & SMASK) + (int)(ctl >> AC_SHIFT) <= 0;
    }

    /**
     * Returns an estimate of the total number of tasks stolen from
     * one thread's work queue by another. The reported value
     * underestimates the actual total number of steals when the pool
     * is not quiescent. This value may be useful for monitoring and
     * tuning fork/join programs: in general, steal counts should be
     * high enough to keep threads busy, but low enough to avoid
     * overhead and contention across threads.
     *
     * @return the number of steals
     */
    public long getStealCount() {
        AuxState sc = auxState;
        long count = (sc == null) ? 0L : sc.stealCount;
        WorkQueue[] ws; WorkQueue w;
        if ((ws = workQueues) != null) {
            for (int i = 1; i < ws.length; i += 2) {
                if ((w = ws[i]) != null)
                    count += w.nsteals;
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
        long count = 0;
        WorkQueue[] ws; WorkQueue w;
        if ((ws = workQueues) != null) {
            for (int i = 1; i < ws.length; i += 2) {
                if ((w = ws[i]) != null)
                    count += w.queueSize();
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
        int count = 0;
        WorkQueue[] ws; WorkQueue w;
        if ((ws = workQueues) != null) {
            for (int i = 0; i < ws.length; i += 2) {
                if ((w = ws[i]) != null)
                    count += w.queueSize();
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
        WorkQueue[] ws; WorkQueue w;
        if ((ws = workQueues) != null) {
            for (int i = 0; i < ws.length; i += 2) {
                if ((w = ws[i]) != null && !w.isEmpty())
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
        WorkQueue[] ws; int wl; WorkQueue w; ForkJoinTask<?> t;
        int r = ThreadLocalRandom.nextSecondarySeed();
        if ((ws = workQueues) != null && (wl = ws.length) > 0) {
            for (int m = wl - 1, i = 0; i < wl; ++i) {
                if ((w = ws[(i << 1) & m]) != null && (t = w.poll()) != null)
                    return t;
            }
        }
        return null;
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
        WorkQueue[] ws; WorkQueue w; ForkJoinTask<?> t;
        if ((ws = workQueues) != null) {
            for (int i = 0; i < ws.length; ++i) {
                if ((w = ws[i]) != null) {
                    while ((t = w.poll()) != null) {
                        c.add(t);
                        ++count;
                    }
                }
            }
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
        // Use a single pass through workQueues to collect counts
        long qt = 0L, qs = 0L; int rc = 0;
        AuxState sc = auxState;
        long st = (sc == null) ? 0L : sc.stealCount;
        long c = ctl;
        WorkQueue[] ws; WorkQueue w;
        if ((ws = workQueues) != null) {
            for (int i = 0; i < ws.length; ++i) {
                if ((w = ws[i]) != null) {
                    int size = w.queueSize();
                    if ((i & 1) == 0)
                        qs += size;
                    else {
                        qt += size;
                        st += w.nsteals;
                        if (w.isApparentlyUnblocked())
                            ++rc;
                    }
                }
            }
        }
        int pc = (config & SMASK);
        int tc = pc + (short)(c >>> TC_SHIFT);
        int ac = pc + (int)(c >> AC_SHIFT);
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
            ", submissions = " + qs +
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
        int rs = runState;
        return (rs & STOP) != 0 && (rs & TERMINATED) == 0;
    }

    /**
     * Returns {@code true} if this pool has been shut down.
     *
     * @return {@code true} if this pool has been shut down
     */
    public boolean isShutdown() {
        return (runState & SHUTDOWN) != 0;
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
        if (Thread.interrupted())
            throw new InterruptedException();
        if (this == common) {
            awaitQuiescence(timeout, unit);
            return false;
        }
        long nanos = unit.toNanos(timeout);
        if (isTerminated())
            return true;
        if (nanos <= 0L)
            return false;
        long deadline = System.nanoTime() + nanos;
        synchronized (this) {
            for (;;) {
                if (isTerminated())
                    return true;
                if (nanos <= 0L)
                    return false;
                long millis = TimeUnit.NANOSECONDS.toMillis(nanos);
                wait(millis > 0L ? millis : 1L);
                nanos = deadline - System.nanoTime();
            }
        }
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
        long nanos = unit.toNanos(timeout);
        ForkJoinWorkerThread wt;
        Thread thread = Thread.currentThread();
        if ((thread instanceof ForkJoinWorkerThread) &&
            (wt = (ForkJoinWorkerThread)thread).pool == this) {
            helpQuiescePool(wt.workQueue);
            return true;
        }
        long startTime = System.nanoTime();
        WorkQueue[] ws;
        int r = 0, wl;
        boolean found = true;
        while (!isQuiescent() && (ws = workQueues) != null &&
               (wl = ws.length) > 0) {
            if (!found) {
                if ((System.nanoTime() - startTime) > nanos)
                    return false;
                Thread.yield(); // cannot block
            }
            found = false;
            for (int m = wl - 1, j = (m + 1) << 2; j >= 0; --j) {
                ForkJoinTask<?> t; WorkQueue q; int b, k;
                if ((k = r++ & m) <= m && k >= 0 && (q = ws[k]) != null &&
                    (b = q.base) - q.top < 0) {
                    found = true;
                    if ((t = q.pollAt(b)) != null)
                        t.doExec();
                    break;
                }
            }
        }
        return true;
    }

    /**
     * Waits and/or attempts to assist performing tasks indefinitely
     * until the {@link #commonPool()} {@link #isQuiescent}.
     */
    static void quiesceCommonPool() {
        common.awaitQuiescence(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
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
     * thread invoking {@link ForkJoinPool#managedBlock(ManagedBlocker)}.
     * The unusual methods in this API accommodate synchronizers that
     * may, but don't usually, block for long periods. Similarly, they
     * allow more efficient internal handling of cases in which
     * additional workers may be, but usually are not, needed to
     * ensure sufficient parallelism.  Toward this end,
     * implementations of method {@code isReleasable} must be amenable
     * to repeated invocation.
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
        ForkJoinPool p;
        ForkJoinWorkerThread wt;
        Thread t = Thread.currentThread();
        if ((t instanceof ForkJoinWorkerThread) &&
            (p = (wt = (ForkJoinWorkerThread)t).pool) != null) {
            WorkQueue w = wt.workQueue;
            while (!blocker.isReleasable()) {
                if (p.tryCompensate(w)) {
                    try {
                        do {} while (!blocker.isReleasable() &&
                                     !blocker.block());
                    } finally {
                        U.getAndAddLong(p, CTL, AC_UNIT);
                    }
                    break;
                }
            }
        }
        else {
            do {} while (!blocker.isReleasable() &&
                         !blocker.block());
        }
    }

    // AbstractExecutorService overrides.  These rely on undocumented
    // fact that ForkJoinTask.adapt returns ForkJoinTasks that also
    // implement RunnableFuture.

    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        return new ForkJoinTask.AdaptedRunnable<T>(runnable, value);
    }

    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        return new ForkJoinTask.AdaptedCallable<T>(callable);
    }

    // Unsafe mechanics
    private static final jdk.internal.misc.Unsafe U = jdk.internal.misc.Unsafe.getUnsafe();
    private static final long CTL;
    private static final long RUNSTATE;
    private static final int ABASE;
    private static final int ASHIFT;

    static {
        try {
            CTL = U.objectFieldOffset
                (ForkJoinPool.class.getDeclaredField("ctl"));
            RUNSTATE = U.objectFieldOffset
                (ForkJoinPool.class.getDeclaredField("runState"));
            ABASE = U.arrayBaseOffset(ForkJoinTask[].class);
            int scale = U.arrayIndexScale(ForkJoinTask[].class);
            if ((scale & (scale - 1)) != 0)
                throw new Error("array index scale not a power of two");
            ASHIFT = 31 - Integer.numberOfLeadingZeros(scale);
        } catch (ReflectiveOperationException e) {
            throw new Error(e);
        }

        // Reduce the risk of rare disastrous classloading in first call to
        // LockSupport.park: https://bugs.openjdk.java.net/browse/JDK-8074773
        Class<?> ensureLoaded = LockSupport.class;

        int commonMaxSpares = DEFAULT_COMMON_MAX_SPARES;
        try {
            String p = System.getProperty
                ("java.util.concurrent.ForkJoinPool.common.maximumSpares");
            if (p != null)
                commonMaxSpares = Integer.parseInt(p);
        } catch (Exception ignore) {}
        COMMON_MAX_SPARES = commonMaxSpares;

        defaultForkJoinWorkerThreadFactory =
            new DefaultForkJoinWorkerThreadFactory();
        modifyThreadPermission = new RuntimePermission("modifyThread");

        common = java.security.AccessController.doPrivileged
            (new java.security.PrivilegedAction<ForkJoinPool>() {
                public ForkJoinPool run() { return makeCommonPool(); }});

        // report 1 even if threads disabled
        COMMON_PARALLELISM = Math.max(common.config & SMASK, 1);
    }

    /**
     * Creates and returns the common pool, respecting user settings
     * specified via system properties.
     */
    static ForkJoinPool makeCommonPool() {
        int parallelism = -1;
        ForkJoinWorkerThreadFactory factory = null;
        UncaughtExceptionHandler handler = null;
        try {  // ignore exceptions in accessing/parsing properties
            String pp = System.getProperty
                ("java.util.concurrent.ForkJoinPool.common.parallelism");
            String fp = System.getProperty
                ("java.util.concurrent.ForkJoinPool.common.threadFactory");
            String hp = System.getProperty
                ("java.util.concurrent.ForkJoinPool.common.exceptionHandler");
            if (pp != null)
                parallelism = Integer.parseInt(pp);
            if (fp != null)
                factory = ((ForkJoinWorkerThreadFactory)ClassLoader.
                           getSystemClassLoader().loadClass(fp).newInstance());
            if (hp != null)
                handler = ((UncaughtExceptionHandler)ClassLoader.
                           getSystemClassLoader().loadClass(hp).newInstance());
        } catch (Exception ignore) {
        }
        if (factory == null) {
            if (System.getSecurityManager() == null)
                factory = defaultForkJoinWorkerThreadFactory;
            else // use security-managed default
                factory = new InnocuousForkJoinWorkerThreadFactory();
        }
        if (parallelism < 0 && // default 1 less than #cores
            (parallelism = Runtime.getRuntime().availableProcessors() - 1) <= 0)
            parallelism = 1;
        if (parallelism > MAX_CAP)
            parallelism = MAX_CAP;
        return new ForkJoinPool(parallelism, factory, handler, LIFO_QUEUE,
                                "ForkJoinPool.commonPool-worker-");
    }

    /**
     * Factory for innocuous worker threads.
     */
    private static final class InnocuousForkJoinWorkerThreadFactory
        implements ForkJoinWorkerThreadFactory {

        /**
         * An ACC to restrict permissions for the factory itself.
         * The constructed workers have no permissions set.
         */
        private static final AccessControlContext innocuousAcc;
        static {
            Permissions innocuousPerms = new Permissions();
            innocuousPerms.add(modifyThreadPermission);
            innocuousPerms.add(new RuntimePermission(
                                   "enableContextClassLoaderOverride"));
            innocuousPerms.add(new RuntimePermission(
                                   "modifyThreadGroup"));
            innocuousAcc = new AccessControlContext(new ProtectionDomain[] {
                    new ProtectionDomain(null, innocuousPerms)
                });
        }

        public final ForkJoinWorkerThread newThread(ForkJoinPool pool) {
            return java.security.AccessController.doPrivileged(
                new java.security.PrivilegedAction<ForkJoinWorkerThread>() {
                    public ForkJoinWorkerThread run() {
                        return new ForkJoinWorkerThread.
                            InnocuousForkJoinWorkerThread(pool);
                    }}, innocuousAcc);
        }
    }

}
