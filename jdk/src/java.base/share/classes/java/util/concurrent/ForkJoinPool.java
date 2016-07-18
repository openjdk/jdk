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
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.security.AccessControlContext;
import java.security.Permissions;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountedCompleter;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.ForkJoinWorkerThread;
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
     * There are several variants of each of these. In particular,
     * almost all uses of poll occur within scan operations that also
     * interleave contention tracking (with associated code sprawl.)
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
     * complete.
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
     * tasks that they submitted.  Insertion of tasks in shared mode
     * requires a lock but we use only a simple spinlock (using field
     * phase), because submitters encountering a busy queue move to a
     * different position to use or create other queues -- they block
     * only when creating and registering new queues. Because it is
     * used only as a spinlock, unlocking requires only a "releasing"
     * store (using setRelease).
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
     * Nearly all essentially atomic control state is held in a few
     * volatile variables that are by far most often read (not
     * written) as status and consistency checks. We pack as much
     * information into them as we can.
     *
     * Field "ctl" contains 64 bits holding information needed to
     * atomically decide to add, enqueue (on an event queue), and
     * dequeue (and release)-activate workers.  To enable this
     * packing, we restrict maximum parallelism to (1<<15)-1 (which is
     * far in excess of normal operating range) to allow ids, counts,
     * and their negations (used for thresholding) to fit into 16bit
     * subfields.
     *
     * Field "mode" holds configuration parameters as well as lifetime
     * status, atomically and monotonically setting SHUTDOWN, STOP,
     * and finally TERMINATED bits.
     *
     * Field "workQueues" holds references to WorkQueues.  It is
     * updated (only during worker creation and termination) under
     * lock (using field workerNamePrefix as lock), but is otherwise
     * concurrently readable, and accessed directly. We also ensure
     * that uses of the array reference itself never become too stale
     * in case of resizing.  To simplify index-based operations, the
     * array size is always a power of two, and all readers must
     * tolerate null slots. Worker queues are at odd indices. Shared
     * (submission) queues are at even indices, up to a maximum of 64
     * slots, to limit growth even if array needs to expand to add
     * more workers. Grouping them together in this way simplifies and
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
     * is the main limiting factor in overall performance, which is
     * compounded at program start-up by JIT compilation and
     * allocation. So we streamline this as much as possible.
     *
     * The "ctl" field atomically maintains total worker and
     * "released" worker counts, plus the head of the available worker
     * queue (actually stack, represented by the lower 32bit subfield
     * of ctl).  Released workers are those known to be scanning for
     * and/or running tasks. Unreleased ("available") workers are
     * recorded in the ctl stack. These workers are made available for
     * signalling by enqueuing in ctl (see method runWorker).  The
     * "queue" is a form of Treiber stack. This is ideal for
     * activating threads in most-recently used order, and improves
     * performance and locality, outweighing the disadvantages of
     * being prone to contention and inability to release a worker
     * unless it is topmost on stack.  To avoid missed signal problems
     * inherent in any wait/signal design, available workers rescan
     * for (and if found run) tasks after enqueuing.  Normally their
     * release status will be updated while doing so, but the released
     * worker ctl count may underestimate the number of active
     * threads. (However, it is still possible to determine quiescence
     * via a validation traversal -- see isQuiescent).  After an
     * unsuccessful rescan, available workers are blocked until
     * signalled (see signalWork).  The top stack state holds the
     * value of the "phase" field of the worker: its index and status,
     * plus a version counter that, in addition to the count subfields
     * (also serving as version stamps) provide protection against
     * Treiber stack ABA effects.
     *
     * Creating workers. To create a worker, we pre-increment counts
     * (serving as a reservation), and attempt to construct a
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
     * WorkQueue field "phase" is used by both workers and the pool to
     * manage and track whether a worker is UNSIGNALLED (possibly
     * blocked waiting for a signal).  When a worker is enqueued its
     * phase field is set. Note that phase field updates lag queue CAS
     * releases so usage requires care -- seeing a negative phase does
     * not guarantee that the worker is available. When queued, the
     * lower 16 bits of scanState must hold its pool index. So we
     * place the index there upon initialization (see registerWorker)
     * and otherwise keep it there or restore it when necessary.
     *
     * The ctl field also serves as the basis for memory
     * synchronization surrounding activation. This uses a more
     * efficient version of a Dekker-like rule that task producers and
     * consumers sync with each other by both writing/CASing ctl (even
     * if to its current value).  This would be extremely costly. So
     * we relax it in several ways: (1) Producers only signal when
     * their queue is empty. Other workers propagate this signal (in
     * method scan) when they find tasks; to further reduce flailing,
     * each worker signals only one other per activation. (2) Workers
     * only enqueue after scanning (see below) and not finding any
     * tasks.  (3) Rather than CASing ctl to its current value in the
     * common case where no action is required, we reduce write
     * contention by equivalently prefacing signalWork when called by
     * an external task producer using a memory access with
     * full-volatile semantics or a "fullFence".
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
     * Scanning. Method runWorker performs top-level scanning for
     * tasks.  Each scan traverses and tries to poll from each queue
     * starting at a random index and circularly stepping. Scans are
     * not performed in ideal random permutation order, to reduce
     * cacheline contention.  The pseudorandom generator need not have
     * high-quality statistical properties in the long term, but just
     * within computations; We use Marsaglia XorShifts (often via
     * ThreadLocalRandom.nextSecondarySeed), which are cheap and
     * suffice. Scanning also employs contention reduction: When
     * scanning workers fail to extract an apparently existing task,
     * they soon restart at a different pseudorandom index.  This
     * improves throughput when many threads are trying to take tasks
     * from few queues, which can be common in some usages.  Scans do
     * not otherwise explicitly take into account core affinities,
     * loads, cache localities, etc, However, they do exploit temporal
     * locality (which usually approximates these) by preferring to
     * re-poll (at most #workers times) from the same queue after a
     * successful poll before trying others.
     *
     * Trimming workers. To release resources after periods of lack of
     * use, a worker starting to wait when the pool is quiescent will
     * time out and terminate (see method scan) if the pool has
     * remained quiescent for period given by field keepAlive.
     *
     * Shutdown and Termination. A call to shutdownNow invokes
     * tryTerminate to atomically set a runState bit. The calling
     * thread, as well as every other worker thereafter terminating,
     * helps terminate others by cancelling their unprocessed tasks,
     * and waking them up, doing so repeatedly until stable. Calls to
     * non-abrupt shutdown() preface this by checking whether
     * termination should commence by sweeping through queues (until
     * stable) to ensure lack of in-flight submissions and workers
     * about to process them before triggering the "STOP" phase of
     * termination.
     *
     * Joining Tasks
     * =============
     *
     * Any of several actions may be taken when one worker is waiting
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
     * without the need for a compensation thread.
     *
     * The ManagedBlocker extension API can't use helping so relies
     * only on compensation in method awaitBlocker.
     *
     * The algorithm in awaitJoin entails a form of "linear helping".
     * Each worker records (in field source) the id of the queue from
     * which it last stole a task.  The scan in method awaitJoin uses
     * these markers to try to find a worker to help (i.e., steal back
     * a task from and execute it) that could hasten completion of the
     * actively joined task.  Thus, the joiner executes a task that
     * would be on its own local deque if the to-be-joined task had
     * not been stolen. This is a conservative variant of the approach
     * described in Wagner & Calder "Leapfrogging: a portable
     * technique for implementing efficient futures" SIGPLAN Notices,
     * 1993 (http://portal.acm.org/citation.cfm?id=155354). It differs
     * mainly in that we only record queue ids, not full dependency
     * links.  This requires a linear scan of the workQueues array to
     * locate stealers, but isolates cost to when it is needed, rather
     * than adding to per-task overhead. Searches can fail to locate
     * stealers GC stalls and the like delay recording sources.
     * Further, even when accurately identified, stealers might not
     * ever produce a task that the joiner can in turn help with. So,
     * compensation is tried upon failure to find tasks to run.
     *
     * Compensation does not by default aim to keep exactly the target
     * parallelism number of unblocked threads running at any given
     * time. Some previous versions of this class employed immediate
     * compensations for any blocked join. However, in practice, the
     * vast majority of blockages are transient byproducts of GC and
     * other JVM or OS activities that are made worse by replacement.
     * Rather than impose arbitrary policies, we allow users to
     * override the default of only adding threads upon apparent
     * starvation.  The compensation mechanism may also be bounded.
     * Bounds for the commonPool (see COMMON_MAX_SPARES) better enable
     * JVMs to cope with programming errors and abuse before running
     * out of resources to do so.
     *
     * Common Pool
     * ===========
     *
     * The static common pool always exists after static
     * initialization.  Since it (or any other created pool) need
     * never be used, we minimize initial construction overhead and
     * footprint to the setup of about a dozen fields.
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
     * after executing any top-level task (see
     * WorkQueue.afterTopLevelExec).  The associated mechanics (mainly
     * in ForkJoinWorkerThread) may be JVM-dependent and must access
     * particular Thread class fields to achieve this effect.
     *
     * Style notes
     * ===========
     *
     * Memory ordering relies mainly on VarHandles.  This can be
     * awkward and ugly, but also reflects the need to control
     * outcomes across the unusual cases that arise in very racy code
     * with very few invariants. All fields are read into locals
     * before use, and null-checked if they are references.  This is
     * usually done in a "C"-like style of listing declarations at the
     * heads of methods or blocks, and using inline assignments on
     * first encounter.  Nearly all explicit checks lead to
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
         *         to create a thread is rejected.
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

    // Constants shared across ForkJoinPool and WorkQueue

    // Bounds
    static final int SWIDTH       = 16;            // width of short
    static final int SMASK        = 0xffff;        // short bits == max index
    static final int MAX_CAP      = 0x7fff;        // max #workers - 1
    static final int SQMASK       = 0x007e;        // max 64 (even) slots

    // Masks and units for WorkQueue.phase and ctl sp subfield
    static final int UNSIGNALLED  = 1 << 31;       // must be negative
    static final int SS_SEQ       = 1 << 16;       // version count
    static final int QLOCK        = 1;             // must be 1

    // Mode bits and sentinels, some also used in WorkQueue id and.source fields
    static final int OWNED        = 1;             // queue has owner thread
    static final int FIFO         = 1 << 16;       // fifo queue or access mode
    static final int SHUTDOWN     = 1 << 18;
    static final int TERMINATED   = 1 << 19;
    static final int STOP         = 1 << 31;       // must be negative
    static final int QUIET        = 1 << 30;       // not scanning or working
    static final int DORMANT      = QUIET | UNSIGNALLED;

    /**
     * The maximum number of local polls from the same queue before
     * checking others. This is a safeguard against infinitely unfair
     * looping under unbounded user task recursion, and must be larger
     * than plausible cases of intentional bounded task recursion.
     */
    static final int POLL_LIMIT = 1 << 10;

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
        volatile int phase;        // versioned, negative: queued, 1: locked
        int stackPred;             // pool stack (ctl) predecessor link
        int nsteals;               // number of steals
        int id;                    // index, mode, tag
        volatile int source;       // source queue id, or sentinel
        volatile int base;         // index of next slot for poll
        int top;                   // index of next slot for push
        ForkJoinTask<?>[] array;   // the elements (initially unallocated)
        final ForkJoinPool pool;   // the containing pool (may be null)
        final ForkJoinWorkerThread owner; // owning thread or null if shared

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
            return (id & 0xffff) >>> 1; // ignore odd/even tag bit
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
            ForkJoinTask<?>[] a; int n, al, b;
            return ((n = (b = base) - top) >= 0 || // possibly one task
                    (n == -1 && ((a = array) == null ||
                                 (al = a.length) == 0 ||
                                 a[(al - 1) & b] == null)));
        }


        /**
         * Pushes a task. Call only by owner in unshared queues.
         *
         * @param task the task. Caller must ensure non-null.
         * @throws RejectedExecutionException if array cannot be resized
         */
        final void push(ForkJoinTask<?> task) {
            int s = top; ForkJoinTask<?>[] a; int al, d;
            if ((a = array) != null && (al = a.length) > 0) {
                int index = (al - 1) & s;
                ForkJoinPool p = pool;
                top = s + 1;
                QA.setRelease(a, index, task);
                if ((d = base - s) == 0 && p != null) {
                    VarHandle.fullFence();
                    p.signalWork();
                }
                else if (d + al == 1)
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
            int oldSize = oldA != null ? oldA.length : 0;
            int size = oldSize > 0 ? oldSize << 1 : INITIAL_QUEUE_CAPACITY;
            if (size < INITIAL_QUEUE_CAPACITY || size > MAXIMUM_QUEUE_CAPACITY)
                throw new RejectedExecutionException("Queue capacity exceeded");
            int oldMask, t, b;
            ForkJoinTask<?>[] a = array = new ForkJoinTask<?>[size];
            if (oldA != null && (oldMask = oldSize - 1) > 0 &&
                (t = top) - (b = base) > 0) {
                int mask = size - 1;
                do { // emulate poll from old array, push to new array
                    int index = b & oldMask;
                    ForkJoinTask<?> x = (ForkJoinTask<?>)
                        QA.getAcquire(oldA, index);
                    if (x != null &&
                        QA.compareAndSet(oldA, index, x, null))
                        a[b & mask] = x;
                } while (++b != t);
                VarHandle.releaseFence();
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
                ForkJoinTask<?> t = (ForkJoinTask<?>)
                    QA.get(a, index);
                if (t != null &&
                    QA.compareAndSet(a, index, t, null)) {
                    top = s;
                    VarHandle.releaseFence();
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
                    ForkJoinTask<?> t = (ForkJoinTask<?>)
                        QA.getAcquire(a, index);
                    if (b++ == base) {
                        if (t != null) {
                            if (QA.compareAndSet(a, index, t, null)) {
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
            return ((id & FIFO) != 0) ? poll() : pop();
        }

        /**
         * Returns next task, if one exists, in order specified by mode.
         */
        final ForkJoinTask<?> peek() {
            int al; ForkJoinTask<?>[] a;
            return ((a = array) != null && (al = a.length) > 0) ?
                a[(al - 1) &
                  ((id & FIFO) != 0 ? base : top - 1)] : null;
        }

        /**
         * Pops the given task only if it is at the current top.
         */
        final boolean tryUnpush(ForkJoinTask<?> task) {
            int b = base, s = top, al; ForkJoinTask<?>[] a;
            if ((a = array) != null && b != s && (al = a.length) > 0) {
                int index = (al - 1) & --s;
                if (QA.compareAndSet(a, index, task, null)) {
                    top = s;
                    VarHandle.releaseFence();
                    return true;
                }
            }
            return false;
        }

        /**
         * Removes and cancels all known tasks, ignoring any exceptions.
         */
        final void cancelAll() {
            for (ForkJoinTask<?> t; (t = poll()) != null; )
                ForkJoinTask.cancelIgnoringExceptions(t);
        }

        // Specialized execution methods

        /**
         * Pops and executes up to limit consecutive tasks or until empty.
         *
         * @param limit max runs, or zero for no limit
         */
        final void localPopAndExec(int limit) {
            for (;;) {
                int b = base, s = top, al; ForkJoinTask<?>[] a;
                if ((a = array) != null && b != s && (al = a.length) > 0) {
                    int index = (al - 1) & --s;
                    ForkJoinTask<?> t = (ForkJoinTask<?>)
                        QA.getAndSet(a, index, null);
                    if (t != null) {
                        top = s;
                        VarHandle.releaseFence();
                        t.doExec();
                        if (limit != 0 && --limit == 0)
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
         * Polls and executes up to limit consecutive tasks or until empty.
         *
         * @param limit, or zero for no limit
         */
        final void localPollAndExec(int limit) {
            for (int polls = 0;;) {
                int b = base, s = top, d, al; ForkJoinTask<?>[] a;
                if ((a = array) != null && (d = b - s) < 0 &&
                    (al = a.length) > 0) {
                    int index = (al - 1) & b++;
                    ForkJoinTask<?> t = (ForkJoinTask<?>)
                        QA.getAndSet(a, index, null);
                    if (t != null) {
                        base = b;
                        t.doExec();
                        if (limit != 0 && ++polls == limit)
                            break;
                    }
                    else if (d == -1)
                        break;     // now empty
                    else
                        polls = 0; // stolen; reset
                }
                else
                    break;
            }
        }

        /**
         * If present, removes task from queue and executes it.
         */
        final void tryRemoveAndExec(ForkJoinTask<?> task) {
            ForkJoinTask<?>[] wa; int s, wal;
            if (base - (s = top) < 0 && // traverse from top
                (wa = array) != null && (wal = wa.length) > 0) {
                for (int m = wal - 1, ns = s - 1, i = ns; ; --i) {
                    int index = i & m;
                    ForkJoinTask<?> t = (ForkJoinTask<?>)
                        QA.get(wa, index);
                    if (t == null)
                        break;
                    else if (t == task) {
                        if (QA.compareAndSet(wa, index, t, null)) {
                            top = ns;   // safely shift down
                            for (int j = i; j != ns; ++j) {
                                ForkJoinTask<?> f;
                                int pindex = (j + 1) & m;
                                f = (ForkJoinTask<?>)QA.get(wa, pindex);
                                QA.setVolatile(wa, pindex, null);
                                int jindex = j & m;
                                QA.setRelease(wa, jindex, f);
                            }
                            VarHandle.releaseFence();
                            t.doExec();
                        }
                        break;
                    }
                }
            }
        }

        /**
         * Tries to steal and run tasks within the target's
         * computation until done, not found, or limit exceeded.
         *
         * @param task root of CountedCompleter computation
         * @param limit max runs, or zero for no limit
         * @return task status on exit
         */
        final int localHelpCC(CountedCompleter<?> task, int limit) {
            int status = 0;
            if (task != null && (status = task.status) >= 0) {
                for (;;) {
                    boolean help = false;
                    int b = base, s = top, al; ForkJoinTask<?>[] a;
                    if ((a = array) != null && b != s && (al = a.length) > 0) {
                        int index = (al - 1) & (s - 1);
                        ForkJoinTask<?> o = (ForkJoinTask<?>)
                            QA.get(a, index);
                        if (o instanceof CountedCompleter) {
                            CountedCompleter<?> t = (CountedCompleter<?>)o;
                            for (CountedCompleter<?> f = t;;) {
                                if (f != task) {
                                    if ((f = f.completer) == null) // try parent
                                        break;
                                }
                                else {
                                    if (QA.compareAndSet(a, index, t, null)) {
                                        top = s - 1;
                                        VarHandle.releaseFence();
                                        t.doExec();
                                        help = true;
                                    }
                                    break;
                                }
                            }
                        }
                    }
                    if ((status = task.status) < 0 || !help ||
                        (limit != 0 && --limit == 0))
                        break;
                }
            }
            return status;
        }

        // Operations on shared queues

        /**
         * Tries to lock shared queue by CASing phase field.
         */
        final boolean tryLockSharedQueue() {
            return PHASE.compareAndSet(this, 0, QLOCK);
        }

        /**
         * Shared version of tryUnpush.
         */
        final boolean trySharedUnpush(ForkJoinTask<?> task) {
            boolean popped = false;
            int s = top - 1, al; ForkJoinTask<?>[] a;
            if ((a = array) != null && (al = a.length) > 0) {
                int index = (al - 1) & s;
                ForkJoinTask<?> t = (ForkJoinTask<?>) QA.get(a, index);
                if (t == task &&
                    PHASE.compareAndSet(this, 0, QLOCK)) {
                    if (top == s + 1 && array == a &&
                        QA.compareAndSet(a, index, task, null)) {
                        popped = true;
                        top = s;
                    }
                    PHASE.setRelease(this, 0);
                }
            }
            return popped;
        }

        /**
         * Shared version of localHelpCC.
         */
        final int sharedHelpCC(CountedCompleter<?> task, int limit) {
            int status = 0;
            if (task != null && (status = task.status) >= 0) {
                for (;;) {
                    boolean help = false;
                    int b = base, s = top, al; ForkJoinTask<?>[] a;
                    if ((a = array) != null && b != s && (al = a.length) > 0) {
                        int index = (al - 1) & (s - 1);
                        ForkJoinTask<?> o = (ForkJoinTask<?>)
                            QA.get(a, index);
                        if (o instanceof CountedCompleter) {
                            CountedCompleter<?> t = (CountedCompleter<?>)o;
                            for (CountedCompleter<?> f = t;;) {
                                if (f != task) {
                                    if ((f = f.completer) == null)
                                        break;
                                }
                                else {
                                    if (PHASE.compareAndSet(this, 0, QLOCK)) {
                                        if (top == s && array == a &&
                                            QA.compareAndSet(a, index, t, null)) {
                                            help = true;
                                            top = s - 1;
                                        }
                                        PHASE.setRelease(this, 0);
                                        if (help)
                                            t.doExec();
                                    }
                                    break;
                                }
                            }
                        }
                    }
                    if ((status = task.status) < 0 || !help ||
                        (limit != 0 && --limit == 0))
                        break;
                }
            }
            return status;
        }

        /**
         * Returns true if owned and not known to be blocked.
         */
        final boolean isApparentlyUnblocked() {
            Thread wt; Thread.State s;
            return ((wt = owner) != null &&
                    (s = wt.getState()) != Thread.State.BLOCKED &&
                    s != Thread.State.WAITING &&
                    s != Thread.State.TIMED_WAITING);
        }

        // VarHandle mechanics.
        private static final VarHandle PHASE;
        static {
            try {
                MethodHandles.Lookup l = MethodHandles.lookup();
                PHASE = l.findVarHandle(WorkQueue.class, "phase", int.class);
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
     * kill threads.
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
     * Default idle timeout value (in milliseconds) for the thread
     * triggering quiescence to park waiting for new work
     */
    private static final long DEFAULT_KEEPALIVE = 60000L;

    /**
     * Undershoot tolerance for idle timeouts
     */
    private static final long TIMEOUT_SLOP = 20L;

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
     * RC: Number of released (unqueued) workers minus target parallelism
     * TC: Number of total workers minus target parallelism
     * SS: version count and status of top waiting thread
     * ID: poolIndex of top of Treiber stack of waiters
     *
     * When convenient, we can extract the lower 32 stack top bits
     * (including version bits) as sp=(int)ctl.  The offsets of counts
     * by the target parallelism and the positionings of fields makes
     * it possible to perform the most common checks via sign tests of
     * fields: When ac is negative, there are not enough unqueued
     * workers, when tc is negative, there are not enough total
     * workers.  When sp is non-zero, there are waiting workers.  To
     * deal with possibly negative fields, we use casts in and out of
     * "short" and/or signed shifts to maintain signedness.
     *
     * Because it occupies uppermost bits, we can add one release count
     * using getAndAddLong of RC_UNIT, rather than CAS, when returning
     * from a blocked join.  Other updates entail multiple subfields
     * and masking, requiring CAS.
     *
     * The limits packed in field "bounds" are also offset by the
     * parallelism level to make them comparable to the ctl rc and tc
     * fields.
     */

    // Lower and upper word masks
    private static final long SP_MASK    = 0xffffffffL;
    private static final long UC_MASK    = ~SP_MASK;

    // Release counts
    private static final int  RC_SHIFT   = 48;
    private static final long RC_UNIT    = 0x0001L << RC_SHIFT;
    private static final long RC_MASK    = 0xffffL << RC_SHIFT;

    // Total counts
    private static final int  TC_SHIFT   = 32;
    private static final long TC_UNIT    = 0x0001L << TC_SHIFT;
    private static final long TC_MASK    = 0xffffL << TC_SHIFT;
    private static final long ADD_WORKER = 0x0001L << (TC_SHIFT + 15); // sign

    // Instance fields

    volatile long stealCount;            // collects worker nsteals
    final long keepAlive;                // milliseconds before dropping if idle
    int indexSeed;                       // next worker index
    final int bounds;                    // min, max threads packed as shorts
    volatile int mode;                   // parallelism, runstate, queue mode
    WorkQueue[] workQueues;              // main registry
    final String workerNamePrefix;       // for worker thread string; sync lock
    final ForkJoinWorkerThreadFactory factory;
    final UncaughtExceptionHandler ueh;  // per-worker UEH
    final Predicate<? super ForkJoinPool> saturate;

    @jdk.internal.vm.annotation.Contended("fjpctl") // segregate
    volatile long ctl;                   // main pool control

    // Creating, registering and deregistering workers

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
            if (fac != null && (wt = fac.newThread(this)) != null) {
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
            long nc = ((RC_MASK & (c + RC_UNIT)) |
                       (TC_MASK & (c + TC_UNIT)));
            if (ctl == c && CTL.compareAndSet(this, c, nc)) {
                createWorker();
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
        wt.setDaemon(true);                             // configure thread
        if ((handler = ueh) != null)
            wt.setUncaughtExceptionHandler(handler);
        WorkQueue w = new WorkQueue(this, wt);
        int tid = 0;                                    // for thread name
        int fifo = mode & FIFO;
        String prefix = workerNamePrefix;
        if (prefix != null) {
            synchronized (prefix) {
                WorkQueue[] ws = workQueues; int n;
                int s = indexSeed += SEED_INCREMENT;
                if (ws != null && (n = ws.length) > 1) {
                    int m = n - 1;
                    tid = s & m;
                    int i = m & ((s << 1) | 1);         // odd-numbered indices
                    for (int probes = n >>> 1;;) {      // find empty slot
                        WorkQueue q;
                        if ((q = ws[i]) == null || q.phase == QUIET)
                            break;
                        else if (--probes == 0) {
                            i = n | 1;                  // resize below
                            break;
                        }
                        else
                            i = (i + 2) & m;
                    }

                    int id = i | fifo | (s & ~(SMASK | FIFO | DORMANT));
                    w.phase = w.id = id;                // now publishable

                    if (i < n)
                        ws[i] = w;
                    else {                              // expand array
                        int an = n << 1;
                        WorkQueue[] as = new WorkQueue[an];
                        as[i] = w;
                        int am = an - 1;
                        for (int j = 0; j < n; ++j) {
                            WorkQueue v;                // copy external queue
                            if ((v = ws[j]) != null)    // position may change
                                as[v.id & am & SQMASK] = v;
                            if (++j >= n)
                                break;
                            as[j] = ws[j];              // copy worker
                        }
                        workQueues = as;
                    }
                }
            }
            wt.setName(prefix.concat(Integer.toString(tid)));
        }
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
        int phase = 0;
        if (wt != null && (w = wt.workQueue) != null) {
            Object lock = workerNamePrefix;
            long ns = (long)w.nsteals & 0xffffffffL;
            int idx = w.id & SMASK;
            if (lock != null) {
                WorkQueue[] ws;                       // remove index from array
                synchronized (lock) {
                    if ((ws = workQueues) != null && ws.length > idx &&
                        ws[idx] == w)
                        ws[idx] = null;
                    stealCount += ns;
                }
            }
            phase = w.phase;
        }
        if (phase != QUIET) {                         // else pre-adjusted
            long c;                                   // decrement counts
            do {} while (!CTL.weakCompareAndSetVolatile
                         (this, c = ctl, ((RC_MASK & (c - RC_UNIT)) |
                                          (TC_MASK & (c - TC_UNIT)) |
                                          (SP_MASK & c))));
        }
        if (w != null)
            w.cancelAll();                            // cancel remaining tasks

        if (!tryTerminate(false, false) &&            // possibly replace worker
            w != null && w.array != null)             // avoid repeated failures
            signalWork();

        if (ex == null)                               // help clean on way out
            ForkJoinTask.helpExpungeStaleExceptions();
        else                                          // rethrow
            ForkJoinTask.rethrow(ex);
    }

    /**
     * Tries to create or release a worker if too few are running.
     */
    final void signalWork() {
        for (;;) {
            long c; int sp; WorkQueue[] ws; int i; WorkQueue v;
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
                int np = sp & ~UNSIGNALLED;
                int vp = v.phase;
                long nc = (v.stackPred & SP_MASK) | (UC_MASK & (c + RC_UNIT));
                Thread vt = v.owner;
                if (sp == vp && CTL.compareAndSet(this, c, nc)) {
                    v.phase = np;
                    if (v.source < 0)
                        LockSupport.unpark(vt);
                    break;
                }
            }
        }
    }

    /**
     * Tries to decrement counts (sometimes implicitly) and possibly
     * arrange for a compensating worker in preparation for blocking:
     * If not all core workers yet exist, creates one, else if any are
     * unreleased (possibly including caller) releases one, else if
     * fewer than the minimum allowed number of workers running,
     * checks to see that they are all active, and if so creates an
     * extra worker unless over maximum limit and policy is to
     * saturate.  Most of these steps can fail due to interference, in
     * which case 0 is returned so caller will retry. A negative
     * return value indicates that the caller doesn't need to
     * re-adjust counts when later unblocked.
     *
     * @return 1: block then adjust, -1: block without adjust, 0 : retry
     */
    private int tryCompensate(WorkQueue w) {
        int t, n, sp;
        long c = ctl;
        WorkQueue[] ws = workQueues;
        if ((t = (short)(c >>> TC_SHIFT)) >= 0) {
            if (ws == null || (n = ws.length) <= 0 || w == null)
                return 0;                        // disabled
            else if ((sp = (int)c) != 0) {       // replace or release
                WorkQueue v = ws[sp & (n - 1)];
                int wp = w.phase;
                long uc = UC_MASK & ((wp < 0) ? c + RC_UNIT : c);
                int np = sp & ~UNSIGNALLED;
                if (v != null) {
                    int vp = v.phase;
                    Thread vt = v.owner;
                    long nc = ((long)v.stackPred & SP_MASK) | uc;
                    if (vp == sp && CTL.compareAndSet(this, c, nc)) {
                        v.phase = np;
                        if (v.source < 0)
                            LockSupport.unpark(vt);
                        return (wp < 0) ? -1 : 1;
                    }
                }
                return 0;
            }
            else if ((int)(c >> RC_SHIFT) -      // reduce parallelism
                     (short)(bounds & SMASK) > 0) {
                long nc = ((RC_MASK & (c - RC_UNIT)) | (~RC_MASK & c));
                return CTL.compareAndSet(this, c, nc) ? 1 : 0;
            }
            else {                               // validate
                int md = mode, pc = md & SMASK, tc = pc + t, bc = 0;
                boolean unstable = false;
                for (int i = 1; i < n; i += 2) {
                    WorkQueue q; Thread wt; Thread.State ts;
                    if ((q = ws[i]) != null) {
                        if (q.source == 0) {
                            unstable = true;
                            break;
                        }
                        else {
                            --tc;
                            if ((wt = q.owner) != null &&
                                ((ts = wt.getState()) == Thread.State.BLOCKED ||
                                 ts == Thread.State.WAITING))
                                ++bc;            // worker is blocking
                        }
                    }
                }
                if (unstable || tc != 0 || ctl != c)
                    return 0;                    // inconsistent
                else if (t + pc >= MAX_CAP || t >= (bounds >>> SWIDTH)) {
                    Predicate<? super ForkJoinPool> sat;
                    if ((sat = saturate) != null && sat.test(this))
                        return -1;
                    else if (bc < pc) {          // lagging
                        Thread.yield();          // for retry spins
                        return 0;
                    }
                    else
                        throw new RejectedExecutionException(
                            "Thread limit exceeded replacing blocked worker");
                }
            }
        }

        long nc = ((c + TC_UNIT) & TC_MASK) | (c & ~TC_MASK); // expand pool
        return CTL.compareAndSet(this, c, nc) && createWorker() ? 1 : 0;
    }

    /**
     * Top-level runloop for workers, called by ForkJoinWorkerThread.run.
     * See above for explanation.
     */
    final void runWorker(WorkQueue w) {
        WorkQueue[] ws;
        w.growArray();                                  // allocate queue
        int r = w.id ^ ThreadLocalRandom.nextSecondarySeed();
        if (r == 0)                                     // initial nonzero seed
            r = 1;
        int lastSignalId = 0;                           // avoid unneeded signals
        while ((ws = workQueues) != null) {
            boolean nonempty = false;                   // scan
            for (int n = ws.length, j = n, m = n - 1; j > 0; --j) {
                WorkQueue q; int i, b, al; ForkJoinTask<?>[] a;
                if ((i = r & m) >= 0 && i < n &&        // always true
                    (q = ws[i]) != null && (b = q.base) - q.top < 0 &&
                    (a = q.array) != null && (al = a.length) > 0) {
                    int qid = q.id;                     // (never zero)
                    int index = (al - 1) & b;
                    ForkJoinTask<?> t = (ForkJoinTask<?>)
                        QA.getAcquire(a, index);
                    if (t != null && b++ == q.base &&
                        QA.compareAndSet(a, index, t, null)) {
                        if ((q.base = b) - q.top < 0 && qid != lastSignalId)
                            signalWork();               // propagate signal
                        w.source = lastSignalId = qid;
                        t.doExec();
                        if ((w.id & FIFO) != 0)         // run remaining locals
                            w.localPollAndExec(POLL_LIMIT);
                        else
                            w.localPopAndExec(POLL_LIMIT);
                        ForkJoinWorkerThread thread = w.owner;
                        ++w.nsteals;
                        w.source = 0;                   // now idle
                        if (thread != null)
                            thread.afterTopLevelExec();
                    }
                    nonempty = true;
                }
                else if (nonempty)
                    break;
                else
                    ++r;
            }

            if (nonempty) {                             // move (xorshift)
                r ^= r << 13; r ^= r >>> 17; r ^= r << 5;
            }
            else {
                int phase;
                lastSignalId = 0;                       // clear for next scan
                if ((phase = w.phase) >= 0) {           // enqueue
                    int np = w.phase = (phase + SS_SEQ) | UNSIGNALLED;
                    long c, nc;
                    do {
                        w.stackPred = (int)(c = ctl);
                        nc = ((c - RC_UNIT) & UC_MASK) | (SP_MASK & np);
                    } while (!CTL.weakCompareAndSetVolatile(this, c, nc));
                }
                else {                                  // already queued
                    int pred = w.stackPred;
                    w.source = DORMANT;                 // enable signal
                    for (int steps = 0;;) {
                        int md, rc; long c;
                        if (w.phase >= 0) {
                            w.source = 0;
                            break;
                        }
                        else if ((md = mode) < 0)       // shutting down
                            return;
                        else if ((rc = ((md & SMASK) +  // possibly quiescent
                                        (int)((c = ctl) >> RC_SHIFT))) <= 0 &&
                                 (md & SHUTDOWN) != 0 &&
                                 tryTerminate(false, false))
                            return;                     // help terminate
                        else if ((++steps & 1) == 0)
                            Thread.interrupted();       // clear between parks
                        else if (rc <= 0 && pred != 0 && phase == (int)c) {
                            long d = keepAlive + System.currentTimeMillis();
                            LockSupport.parkUntil(this, d);
                            if (ctl == c &&
                                d - System.currentTimeMillis() <= TIMEOUT_SLOP) {
                                long nc = ((UC_MASK & (c - TC_UNIT)) |
                                           (SP_MASK & pred));
                                if (CTL.compareAndSet(this, c, nc)) {
                                    w.phase = QUIET;
                                    return;             // drop on timeout
                                }
                            }
                        }
                        else
                            LockSupport.park(this);
                    }
                }
            }
        }
    }

    /**
     * Helps and/or blocks until the given task is done or timeout.
     * First tries locally helping, then scans other queues for a task
     * produced by one of w's stealers; compensating and blocking if
     * none are found (rescanning if tryCompensate fails).
     *
     * @param w caller
     * @param task the task
     * @param deadline for timed waits, if nonzero
     * @return task status on exit
     */
    final int awaitJoin(WorkQueue w, ForkJoinTask<?> task, long deadline) {
        int s = 0;
        if (w != null && task != null &&
            (!(task instanceof CountedCompleter) ||
             (s = w.localHelpCC((CountedCompleter<?>)task, 0)) >= 0)) {
            w.tryRemoveAndExec(task);
            int src = w.source, id = w.id;
            s = task.status;
            while (s >= 0) {
                WorkQueue[] ws;
                boolean nonempty = false;
                int r = ThreadLocalRandom.nextSecondarySeed() | 1; // odd indices
                if ((ws = workQueues) != null) {       // scan for matching id
                    for (int n = ws.length, m = n - 1, j = -n; j < n; j += 2) {
                        WorkQueue q; int i, b, al; ForkJoinTask<?>[] a;
                        if ((i = (r + j) & m) >= 0 && i < n &&
                            (q = ws[i]) != null && q.source == id &&
                            (b = q.base) - q.top < 0 &&
                            (a = q.array) != null && (al = a.length) > 0) {
                            int qid = q.id;
                            int index = (al - 1) & b;
                            ForkJoinTask<?> t = (ForkJoinTask<?>)
                                QA.getAcquire(a, index);
                            if (t != null && b++ == q.base && id == q.source &&
                                QA.compareAndSet(a, index, t, null)) {
                                q.base = b;
                                w.source = qid;
                                t.doExec();
                                w.source = src;
                            }
                            nonempty = true;
                            break;
                        }
                    }
                }
                if ((s = task.status) < 0)
                    break;
                else if (!nonempty) {
                    long ms, ns; int block;
                    if (deadline == 0L)
                        ms = 0L;                       // untimed
                    else if ((ns = deadline - System.nanoTime()) <= 0L)
                        break;                         // timeout
                    else if ((ms = TimeUnit.NANOSECONDS.toMillis(ns)) <= 0L)
                        ms = 1L;                       // avoid 0 for timed wait
                    if ((block = tryCompensate(w)) != 0) {
                        task.internalWait(ms);
                        CTL.getAndAdd(this, (block > 0) ? RC_UNIT : 0L);
                    }
                    s = task.status;
                }
            }
        }
        return s;
    }

    /**
     * Runs tasks until {@code isQuiescent()}. Rather than blocking
     * when tasks cannot be found, rescans until all others cannot
     * find tasks either.
     */
    final void helpQuiescePool(WorkQueue w) {
        int prevSrc = w.source, fifo = w.id & FIFO;
        for (int source = prevSrc, released = -1;;) { // -1 until known
            WorkQueue[] ws;
            if (fifo != 0)
                w.localPollAndExec(0);
            else
                w.localPopAndExec(0);
            if (released == -1 && w.phase >= 0)
                released = 1;
            boolean quiet = true, empty = true;
            int r = ThreadLocalRandom.nextSecondarySeed();
            if ((ws = workQueues) != null) {
                for (int n = ws.length, j = n, m = n - 1; j > 0; --j) {
                    WorkQueue q; int i, b, al; ForkJoinTask<?>[] a;
                    if ((i = (r - j) & m) >= 0 && i < n && (q = ws[i]) != null) {
                        if ((b = q.base) - q.top < 0 &&
                            (a = q.array) != null && (al = a.length) > 0) {
                            int qid = q.id;
                            if (released == 0) {    // increment
                                released = 1;
                                CTL.getAndAdd(this, RC_UNIT);
                            }
                            int index = (al - 1) & b;
                            ForkJoinTask<?> t = (ForkJoinTask<?>)
                                QA.getAcquire(a, index);
                            if (t != null && b++ == q.base &&
                                QA.compareAndSet(a, index, t, null)) {
                                q.base = b;
                                w.source = source = q.id;
                                t.doExec();
                                w.source = source = prevSrc;
                            }
                            quiet = empty = false;
                            break;
                        }
                        else if ((q.source & QUIET) == 0)
                            quiet = false;
                    }
                }
            }
            if (quiet) {
                if (released == 0)
                    CTL.getAndAdd(this, RC_UNIT);
                w.source = prevSrc;
                break;
            }
            else if (empty) {
                if (source != QUIET)
                    w.source = source = QUIET;
                if (released == 1) {                 // decrement
                    released = 0;
                    CTL.getAndAdd(this, RC_MASK & -RC_UNIT);
                }
            }
        }
    }

    /**
     * Scans for and returns a polled task, if available.
     * Used only for untracked polls.
     *
     * @param submissionsOnly if true, only scan submission queues
     */
    private ForkJoinTask<?> pollScan(boolean submissionsOnly) {
        WorkQueue[] ws; int n;
        rescan: while ((mode & STOP) == 0 && (ws = workQueues) != null &&
                      (n = ws.length) > 0) {
            int m = n - 1;
            int r = ThreadLocalRandom.nextSecondarySeed();
            int h = r >>> 16;
            int origin, step;
            if (submissionsOnly) {
                origin = (r & ~1) & m;         // even indices and steps
                step = (h & ~1) | 2;
            }
            else {
                origin = r & m;
                step = h | 1;
            }
            for (int k = origin, oldSum = 0, checkSum = 0;;) {
                WorkQueue q; int b, al; ForkJoinTask<?>[] a;
                if ((q = ws[k]) != null) {
                    checkSum += b = q.base;
                    if (b - q.top < 0 &&
                        (a = q.array) != null && (al = a.length) > 0) {
                        int index = (al - 1) & b;
                        ForkJoinTask<?> t = (ForkJoinTask<?>)
                            QA.getAcquire(a, index);
                        if (t != null && b++ == q.base &&
                            QA.compareAndSet(a, index, t, null)) {
                            q.base = b;
                            return t;
                        }
                        else
                            break; // restart
                    }
                }
                if ((k = (k + step) & m) == origin) {
                    if (oldSum == (oldSum = checkSum))
                        break rescan;
                    checkSum = 0;
                }
            }
        }
        return null;
    }

    /**
     * Gets and removes a local or stolen task for the given worker.
     *
     * @return a task, if available
     */
    final ForkJoinTask<?> nextTaskFor(WorkQueue w) {
        ForkJoinTask<?> t;
        if (w != null &&
            (t = (w.id & FIFO) != 0 ? w.poll() : w.pop()) != null)
            return t;
        else
            return pollScan(false);
    }

    // External operations

    /**
     * Adds the given task to a submission queue at submitter's
     * current queue, creating one if null or contended.
     *
     * @param task the task. Caller must ensure non-null.
     */
    final void externalPush(ForkJoinTask<?> task) {
        int r;                                // initialize caller's probe
        if ((r = ThreadLocalRandom.getProbe()) == 0) {
            ThreadLocalRandom.localInit();
            r = ThreadLocalRandom.getProbe();
        }
        for (;;) {
            int md = mode, n;
            WorkQueue[] ws = workQueues;
            if ((md & SHUTDOWN) != 0 || ws == null || (n = ws.length) <= 0)
                throw new RejectedExecutionException();
            else {
                WorkQueue q;
                boolean push = false, grow = false;
                if ((q = ws[(n - 1) & r & SQMASK]) == null) {
                    Object lock = workerNamePrefix;
                    int qid = (r | QUIET) & ~(FIFO | OWNED);
                    q = new WorkQueue(this, null);
                    q.id = qid;
                    q.source = QUIET;
                    q.phase = QLOCK;          // lock queue
                    if (lock != null) {
                        synchronized (lock) { // lock pool to install
                            int i;
                            if ((ws = workQueues) != null &&
                                (n = ws.length) > 0 &&
                                ws[i = qid & (n - 1) & SQMASK] == null) {
                                ws[i] = q;
                                push = grow = true;
                            }
                        }
                    }
                }
                else if (q.tryLockSharedQueue()) {
                    int b = q.base, s = q.top, al, d; ForkJoinTask<?>[] a;
                    if ((a = q.array) != null && (al = a.length) > 0 &&
                        al - 1 + (d = b - s) > 0) {
                        a[(al - 1) & s] = task;
                        q.top = s + 1;        // relaxed writes OK here
                        q.phase = 0;
                        if (d < 0 && q.base - s < -1)
                            break;            // no signal needed
                    }
                    else
                        grow = true;
                    push = true;
                }
                if (push) {
                    if (grow) {
                        try {
                            q.growArray();
                            int s = q.top, al; ForkJoinTask<?>[] a;
                            if ((a = q.array) != null && (al = a.length) > 0) {
                                a[(al - 1) & s] = task;
                                q.top = s + 1;
                            }
                        } finally {
                            q.phase = 0;
                        }
                    }
                    signalWork();
                    break;
                }
                else                          // move if busy
                    r = ThreadLocalRandom.advanceProbe(r);
            }
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
        WorkQueue[] ws; int n;
        return (p != null && (ws = p.workQueues) != null &&
                (n = ws.length) > 0) ?
            ws[(n - 1) & r & SQMASK] : null;
    }

    /**
     * Performs tryUnpush for an external submitter.
     */
    final boolean tryExternalUnpush(ForkJoinTask<?> task) {
        int r = ThreadLocalRandom.getProbe();
        WorkQueue[] ws; WorkQueue w; int n;
        return ((ws = workQueues) != null &&
                (n = ws.length) > 0 &&
                (w = ws[(n - 1) & r & SQMASK]) != null &&
                w.trySharedUnpush(task));
    }

    /**
     * Performs helpComplete for an external submitter.
     */
    final int externalHelpComplete(CountedCompleter<?> task, int maxTasks) {
        int r = ThreadLocalRandom.getProbe();
        WorkQueue[] ws; WorkQueue w; int n;
        return ((ws = workQueues) != null && (n = ws.length) > 0 &&
                (w = ws[(n - 1) & r & SQMASK]) != null) ?
            w.sharedHelpCC(task, maxTasks) : 0;
    }

    /**
     * Tries to steal and run tasks within the target's computation.
     * The maxTasks argument supports external usages; internal calls
     * use zero, allowing unbounded steps (external calls trap
     * non-positive values).
     *
     * @param w caller
     * @param maxTasks if non-zero, the maximum number of other tasks to run
     * @return task status on exit
     */
    final int helpComplete(WorkQueue w, CountedCompleter<?> task,
                           int maxTasks) {
        return (w == null) ? 0 : w.localHelpCC(task, maxTasks);
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
            int p = pool.mode & SMASK;
            int a = p + (int)(pool.ctl >> RC_SHIFT);
            int n = q.top - q.base;
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
     * Possibly initiates and/or completes termination.
     *
     * @param now if true, unconditionally terminate, else only
     * if no work and no active workers
     * @param enable if true, terminate when next possible
     * @return true if terminating or terminated
     */
    private boolean tryTerminate(boolean now, boolean enable) {
        int md; // 3 phases: try to set SHUTDOWN, then STOP, then TERMINATED

        while (((md = mode) & SHUTDOWN) == 0) {
            if (!enable || this == common)        // cannot shutdown
                return false;
            else
                MODE.compareAndSet(this, md, md | SHUTDOWN);
        }

        while (((md = mode) & STOP) == 0) {       // try to initiate termination
            if (!now) {                           // check if quiescent & empty
                for (long oldSum = 0L;;) {        // repeat until stable
                    boolean running = false;
                    long checkSum = ctl;
                    WorkQueue[] ws = workQueues;
                    if ((md & SMASK) + (int)(checkSum >> RC_SHIFT) > 0)
                        running = true;
                    else if (ws != null) {
                        WorkQueue w; int b;
                        for (int i = 0; i < ws.length; ++i) {
                            if ((w = ws[i]) != null) {
                                checkSum += (b = w.base) + w.id;
                                if (b != w.top ||
                                    ((i & 1) == 1 && w.source >= 0)) {
                                    running = true;
                                    break;
                                }
                            }
                        }
                    }
                    if (((md = mode) & STOP) != 0)
                        break;                 // already triggered
                    else if (running)
                        return false;
                    else if (workQueues == ws && oldSum == (oldSum = checkSum))
                        break;
                }
            }
            if ((md & STOP) == 0)
                MODE.compareAndSet(this, md, md | STOP);
        }

        while (((md = mode) & TERMINATED) == 0) { // help terminate others
            for (long oldSum = 0L;;) {            // repeat until stable
                WorkQueue[] ws; WorkQueue w;
                long checkSum = ctl;
                if ((ws = workQueues) != null) {
                    for (int i = 0; i < ws.length; ++i) {
                        if ((w = ws[i]) != null) {
                            ForkJoinWorkerThread wt = w.owner;
                            w.cancelAll();        // clear queues
                            if (wt != null) {
                                try {             // unblock join or park
                                    wt.interrupt();
                                } catch (Throwable ignore) {
                                }
                            }
                            checkSum += w.base + w.id;
                        }
                    }
                }
                if (((md = mode) & TERMINATED) != 0 ||
                    (workQueues == ws && oldSum == (oldSum = checkSum)))
                    break;
            }
            if ((md & TERMINATED) != 0)
                break;
            else if ((md & SMASK) + (short)(ctl >>> TC_SHIFT) > 0)
                break;
            else if (MODE.compareAndSet(this, md, md | TERMINATED)) {
                synchronized (this) {
                    notifyAll();                  // for awaitTermination
                }
                break;
            }
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
        // check, encode, pack parameters
        if (parallelism <= 0 || parallelism > MAX_CAP ||
            maximumPoolSize < parallelism || keepAliveTime <= 0L)
            throw new IllegalArgumentException();
        if (factory == null)
            throw new NullPointerException();
        long ms = Math.max(unit.toMillis(keepAliveTime), TIMEOUT_SLOP);

        String prefix = "ForkJoinPool-" + nextPoolId() + "-worker-";
        int corep = Math.min(Math.max(corePoolSize, parallelism), MAX_CAP);
        long c = ((((long)(-corep)       << TC_SHIFT) & TC_MASK) |
                  (((long)(-parallelism) << RC_SHIFT) & RC_MASK));
        int m = parallelism | (asyncMode ? FIFO : 0);
        int maxSpares = Math.min(maximumPoolSize, MAX_CAP) - parallelism;
        int minAvail = Math.min(Math.max(minimumRunnable, 0), MAX_CAP);
        int b = ((minAvail - parallelism) & SMASK) | (maxSpares << SWIDTH);
        int n = (parallelism > 1) ? parallelism - 1 : 1; // at least 2 slots
        n |= n >>> 1; n |= n >>> 2; n |= n >>> 4; n |= n >>> 8; n |= n >>> 16;
        n = (n + 1) << 1; // power of two, including space for submission queues

        this.workQueues = new WorkQueue[n];
        this.workerNamePrefix = prefix;
        this.factory = factory;
        this.ueh = handler;
        this.saturate = saturate;
        this.keepAlive = ms;
        this.bounds = b;
        this.mode = m;
        this.ctl = c;
        checkPermission();
    }

    /**
     * Constructor for common pool using parameters possibly
     * overridden by system properties
     */
    @SuppressWarnings("deprecation") // Class.newInstance
    private ForkJoinPool(byte forCommonPoolOnly) {
        int parallelism = -1;
        ForkJoinWorkerThreadFactory fac = null;
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
                fac = ((ForkJoinWorkerThreadFactory)ClassLoader.
                           getSystemClassLoader().loadClass(fp).newInstance());
            if (hp != null)
                handler = ((UncaughtExceptionHandler)ClassLoader.
                           getSystemClassLoader().loadClass(hp).newInstance());
        } catch (Exception ignore) {
        }

        if (fac == null) {
            if (System.getSecurityManager() == null)
                fac = defaultForkJoinWorkerThreadFactory;
            else // use security-managed default
                fac = new InnocuousForkJoinWorkerThreadFactory();
        }
        if (parallelism < 0 && // default 1 less than #cores
            (parallelism = Runtime.getRuntime().availableProcessors() - 1) <= 0)
            parallelism = 1;
        if (parallelism > MAX_CAP)
            parallelism = MAX_CAP;

        long c = ((((long)(-parallelism) << TC_SHIFT) & TC_MASK) |
                  (((long)(-parallelism) << RC_SHIFT) & RC_MASK));
        int b = ((1 - parallelism) & SMASK) | (COMMON_MAX_SPARES << SWIDTH);
        int n = (parallelism > 1) ? parallelism - 1 : 1;
        n |= n >>> 1; n |= n >>> 2; n |= n >>> 4; n |= n >>> 8; n |= n >>> 16;
        n = (n + 1) << 1;

        this.workQueues = new WorkQueue[n];
        this.workerNamePrefix = "ForkJoinPool.commonPool-worker-";
        this.factory = fac;
        this.ueh = handler;
        this.saturate = null;
        this.keepAlive = DEFAULT_KEEPALIVE;
        this.bounds = b;
        this.mode = parallelism;
        this.ctl = c;
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
        int par = mode & SMASK;
        return (par > 0) ? par : 1;
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
        return ((mode & SMASK) + (short)(ctl >>> TC_SHIFT));
    }

    /**
     * Returns {@code true} if this pool uses local first-in-first-out
     * scheduling mode for forked tasks that are never joined.
     *
     * @return {@code true} if this pool uses async mode
     */
    public boolean getAsyncMode() {
        return (mode & FIFO) != 0;
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
        int r = (mode & SMASK) + (int)(ctl >> RC_SHIFT);
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
        for (;;) {
            long c = ctl;
            int md = mode, pc = md & SMASK;
            int tc = pc + (short)(c >>> TC_SHIFT);
            int rc = pc + (int)(c >> RC_SHIFT);
            if ((md & (STOP | TERMINATED)) != 0)
                return true;
            else if (rc > 0)
                return false;
            else {
                WorkQueue[] ws; WorkQueue v;
                if ((ws = workQueues) != null) {
                    for (int i = 1; i < ws.length; i += 2) {
                        if ((v = ws[i]) != null) {
                            if ((v.source & QUIET) == 0)
                                return false;
                            --tc;
                        }
                    }
                }
                if (tc == 0 && ctl == c)
                    return true;
            }
        }
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
        long count = stealCount;
        WorkQueue[] ws; WorkQueue w;
        if ((ws = workQueues) != null) {
            for (int i = 1; i < ws.length; i += 2) {
                if ((w = ws[i]) != null)
                    count += (long)w.nsteals & 0xffffffffL;
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
        long st = stealCount;
        WorkQueue[] ws; WorkQueue w;
        if ((ws = workQueues) != null) {
            for (int i = 0; i < ws.length; ++i) {
                if ((w = ws[i]) != null) {
                    int size = w.queueSize();
                    if ((i & 1) == 0)
                        qs += size;
                    else {
                        qt += size;
                        st += (long)w.nsteals & 0xffffffffL;
                        if (w.isApparentlyUnblocked())
                            ++rc;
                    }
                }
            }
        }

        int md = mode;
        int pc = (md & SMASK);
        long c = ctl;
        int tc = pc + (short)(c >>> TC_SHIFT);
        int ac = pc + (int)(c >> RC_SHIFT);
        if (ac < 0) // ignore transient negative
            ac = 0;
        String level = ((md & TERMINATED) != 0 ? "Terminated" :
                        (md & STOP)       != 0 ? "Terminating" :
                        (md & SHUTDOWN)   != 0 ? "Shutting down" :
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
        return (mode & TERMINATED) != 0;
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
        int md = mode;
        return (md & STOP) != 0 && (md & TERMINATED) == 0;
    }

    /**
     * Returns {@code true} if this pool has been shut down.
     *
     * @return {@code true} if this pool has been shut down
     */
    public boolean isShutdown() {
        return (mode & SHUTDOWN) != 0;
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
        else {
            for (long startTime = System.nanoTime();;) {
                ForkJoinTask<?> t;
                if ((t = pollScan(false)) != null)
                    t.doExec();
                else if (isQuiescent())
                    return true;
                else if ((System.nanoTime() - startTime) > nanos)
                    return false;
                else
                    Thread.yield(); // cannot block
            }
        }
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
        WorkQueue w;
        Thread t = Thread.currentThread();
        if ((t instanceof ForkJoinWorkerThread) &&
            (p = (wt = (ForkJoinWorkerThread)t).pool) != null &&
            (w = wt.workQueue) != null) {
            int block;
            while (!blocker.isReleasable()) {
                if ((block = p.tryCompensate(w)) != 0) {
                    try {
                        do {} while (!blocker.isReleasable() &&
                                     !blocker.block());
                    } finally {
                        CTL.getAndAdd(p, (block > 0) ? RC_UNIT : 0L);
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

    /**
     * If the given executor is a ForkJoinPool, poll and execute
     * AsynchronousCompletionTasks from worker's queue until none are
     * available or blocker is released.
     */
    static void helpAsyncBlocker(Executor e, ManagedBlocker blocker) {
        if (blocker != null && (e instanceof ForkJoinPool)) {
            WorkQueue w; ForkJoinWorkerThread wt; WorkQueue[] ws; int r, n;
            ForkJoinPool p = (ForkJoinPool)e;
            Thread thread = Thread.currentThread();
            if (thread instanceof ForkJoinWorkerThread &&
                (wt = (ForkJoinWorkerThread)thread).pool == p)
                w = wt.workQueue;
            else if ((r = ThreadLocalRandom.getProbe()) != 0 &&
                     (ws = p.workQueues) != null && (n = ws.length) > 0)
                w = ws[(n - 1) & r & SQMASK];
            else
                w = null;
            if (w != null) {
                for (;;) {
                    int b = w.base, s = w.top, d, al; ForkJoinTask<?>[] a;
                    if ((a = w.array) != null && (d = b - s) < 0 &&
                        (al = a.length) > 0) {
                        int index = (al - 1) & b;
                        ForkJoinTask<?> t = (ForkJoinTask<?>)
                            QA.getAcquire(a, index);
                        if (blocker.isReleasable())
                            break;
                        else if (b++ == w.base) {
                            if (t == null) {
                                if (d == -1)
                                    break;
                            }
                            else if (!(t instanceof CompletableFuture.
                                  AsynchronousCompletionTask))
                                break;
                            else if (QA.compareAndSet(a, index, t, null)) {
                                w.base = b;
                                t.doExec();
                            }
                        }
                    }
                    else
                        break;
                }
            }
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

    // VarHandle mechanics
    private static final VarHandle CTL;
    private static final VarHandle MODE;
    private static final VarHandle QA;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            CTL = l.findVarHandle(ForkJoinPool.class, "ctl", long.class);
            MODE = l.findVarHandle(ForkJoinPool.class, "mode", int.class);
            QA = MethodHandles.arrayElementVarHandle(ForkJoinTask[].class);
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
                    public ForkJoinPool run() {
                        return new ForkJoinPool((byte)0); }});

        COMMON_PARALLELISM = Math.max(common.mode & SMASK, 1);
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
