/*
 * Copyright (c) 1994, 2026, Oracle and/or its affiliates. All rights reserved.
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

package java.lang;

import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.StructureViolationException;
import java.util.concurrent.locks.LockSupport;
import jdk.internal.event.ThreadSleepEvent;
import jdk.internal.misc.TerminatingThreadLocal;
import jdk.internal.misc.Unsafe;
import jdk.internal.misc.VM;
import jdk.internal.vm.Continuation;
import jdk.internal.vm.ScopedValueContainer;
import jdk.internal.vm.StackableScope;
import jdk.internal.vm.ThreadContainer;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Hidden;
import jdk.internal.vm.annotation.IntrinsicCandidate;
import jdk.internal.vm.annotation.Stable;
import sun.nio.ch.Interruptible;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * A <i>thread</i> is a thread of execution in a program. The Java
 * virtual machine allows an application to have multiple threads of
 * execution running concurrently.
 *
 * <p> {@code Thread} defines constructors and a {@link Builder} to create threads.
 * {@linkplain #start() Starting} a thread schedules it to execute its {@link #run() run}
 * method. The newly started thread executes concurrently with the thread that caused
 * it to start.
 *
 * <p> A thread <i>terminates</i> if either its {@code run} method completes normally,
 * or if its {@code run} method completes abruptly and the appropriate {@linkplain
 * Thread.UncaughtExceptionHandler uncaught exception handler} completes normally or
 * abruptly. With no code left to run, the thread has completed execution. The {@link
 * #isAlive isAlive} method can be used to test if a started thread has terminated.
 * The {@link #join() join} method can be used to wait for a thread to terminate.
 *
 * <p> Threads have a unique {@linkplain #threadId() identifier} and a {@linkplain
 * #getName() name}. The identifier is generated when a {@code Thread} is created
 * and cannot be changed. The thread name can be specified when creating a thread
 * or can be {@linkplain #setName(String) changed} at a later time.
 *
 * <p> Threads support {@link ThreadLocal} variables. These are variables that are
 * local to a thread, meaning a thread can have a copy of a variable that is set to
 * a value that is independent of the value set by other threads. {@code Thread} also
 * supports {@link InheritableThreadLocal} variables that are thread local variables
 * that are inherited at thread creation time from the parent {@code Thread}.
 * {@code Thread} supports a special inheritable thread local for the thread
 * {@linkplain #getContextClassLoader() context-class-loader}.
 *
 * <h2><a id="platform-threads">Platform Threads</a></h2>
 * <p> {@code Thread} supports the creation of <i>platform threads</i> that are
 * typically mapped 1:1 to kernel threads scheduled by the operating system.
 * Platform threads will usually have a large stack and other resources that are
 * maintained by the operating system. Platforms threads are suitable for executing
 * all types of tasks but may be a limited resource.
 *
 * <p> Platform threads get an automatically generated thread name by default.
 *
 * <p> Platform threads are designated <i>daemon</i> or <i>non-daemon</i> threads.
 * When the Java virtual machine starts up, there is usually one non-daemon
 * thread (the thread that typically calls the application's {@code main} method).
 * The <a href="Runtime.html#shutdown">shutdown sequence</a> begins when all started
 * non-daemon threads have terminated. Unstarted non-daemon threads do not prevent
 * the shutdown sequence from beginning.
 *
 * <p> In addition to the daemon status, platform threads have a {@linkplain
 * #getPriority() thread priority} and are members of a {@linkplain ThreadGroup
 * thread group}.
 *
 * <h2><a id="virtual-threads">Virtual Threads</a></h2>
 * <p> {@code Thread} also supports the creation of <i>virtual threads</i>.
 * Virtual threads are typically <i>user-mode threads</i> scheduled by the Java
 * runtime rather than the operating system. Virtual threads will typically require
 * few resources and a single Java virtual machine may support millions of virtual
 * threads. Virtual threads are suitable for executing tasks that spend most of
 * the time blocked, often waiting for I/O operations to complete. Virtual threads
 * are not intended for long running CPU intensive operations.
 *
 * <p> Virtual threads typically employ a small set of platform threads used as
 * <em>carrier threads</em>. Locking and I/O operations are examples of operations
 * where a carrier thread may be re-scheduled from one virtual thread to another.
 * Code executing in a virtual thread is not aware of the underlying carrier thread.
 * The {@linkplain Thread#currentThread()} method, used to obtain a reference
 * to the <i>current thread</i>, will always return the {@code Thread} object
 * for the virtual thread.
 *
 * <p> Virtual threads do not have a thread name by default. The {@link #getName()
 * getName} method returns the empty string if a thread name is not set.
 *
 * <p> Virtual threads are daemon threads and so do not prevent the
 * <a href="Runtime.html#shutdown">shutdown sequence</a> from beginning.
 * Virtual threads have a fixed {@linkplain #getPriority() thread priority}
 * that cannot be changed.
 *
 * <h2>Creating And Starting Threads</h2>
 *
 * <p> {@code Thread} defines public constructors for creating platform threads and
 * the {@link #start() start} method to schedule threads to execute. {@code Thread}
 * may be extended for customization and other advanced reasons although most
 * applications should have little need to do this.
 *
 * <p> {@code Thread} defines a {@link Builder} API for creating and starting both
 * platform and virtual threads. The following are examples that use the builder:
 * {@snippet :
 *   Runnable runnable = ...
 *
 *   // Start a daemon thread to run a task
 *   Thread thread = Thread.ofPlatform().daemon().start(runnable);
 *
 *   // Create an unstarted thread with name "duke", its start() method
 *   // must be invoked to schedule it to execute.
 *   Thread thread = Thread.ofPlatform().name("duke").unstarted(runnable);
 *
 *   // A ThreadFactory that creates daemon threads named "worker-0", "worker-1", ...
 *   ThreadFactory factory = Thread.ofPlatform().daemon().name("worker-", 0).factory();
 *
 *   // Start a virtual thread to run a task
 *   Thread thread = Thread.ofVirtual().start(runnable);
 *
 *   // A ThreadFactory that creates virtual threads
 *   ThreadFactory factory = Thread.ofVirtual().factory();
 * }
 *
 * <h2><a id="inheritance">Inheritance When Creating Threads</a></h2>
 * A {@code Thread} created with one of the public constructors inherits the daemon
 * status and thread priority from the parent thread at the time that the child {@code
 * Thread} is created. The {@linkplain ThreadGroup thread group} is also inherited when
 * not provided to the constructor. When using a {@code Thread.Builder} to create a
 * platform thread, the daemon status, thread priority, and thread group are inherited
 * when not set on the builder. As with the constructors, inheriting from the parent
 * thread is done when the child {@code Thread} is created.
 *
 * <p> A {@code Thread} inherits its initial values of {@linkplain InheritableThreadLocal
 * inheritable-thread-local} variables (including the context class loader) from
 * the parent thread values at the time that the child {@code Thread} is created.
 * The 5-param {@linkplain Thread#Thread(ThreadGroup, Runnable, String, long, boolean)
 * constructor} can be used to create a thread that does not inherit its initial
 * values from the constructing thread. When using a {@code Thread.Builder}, the
 * {@link Builder#inheritInheritableThreadLocals(boolean) inheritInheritableThreadLocals}
 * method can be used to select if the initial values are inherited.
 *
 * <h2><a id="thread-interruption">Thread Interruption</a></h2>
 * A {@code Thread} has an <em>interrupted status</em> which serves as a "request" for
 * code executing in the thread to "stop or cancel its current activity". The interrupted
 * status is set by invoking the target thread's {@link #interrupt()} method. Many methods
 * that cause a thread to block or wait are <em>interruptible</em>, meaning they detect
 * that the thread's interrupted status is set and cause execution to return early from
 * the method, usually by throwing an exception.
 *
 * <p> If a thread executing {@link #sleep(long) Thread.sleep} or {@link Object#wait()
 * Object.wait} is interrupted then it causes the method to throw {@link InterruptedException}.
 * Methods that throw {@code InterruptedException} do so after first clearing the
 * interrupted status. Code that catches {@code InterruptedException} should rethrow the
 * exception, or restore the current thread's interrupted status, with
 * {@link #currentThread() Thread.currentThread()}.{@link #interrupt()}, before
 * continuing normally or handling it by throwing another type of exception. Code that
 * throws another type of exception with the {@code InterruptedException} as {@linkplain
 * Throwable#getCause() cause}, or the {@code InterruptedException} as a {@linkplain
 * Throwable#addSuppressed(Throwable) suppressed exception}, should also restore the
 * interrupted status before throwing the exception.
 *
 * <p> If a thread executing a blocking I/O operation on an {@link
 * java.nio.channels.InterruptibleChannel} is interrupted then it causes the channel to be
 * closed, and the blocking I/O operation to throw {@link java.nio.channels.ClosedByInterruptException}
 * with the thread's interrupted status set. If a thread blocked in a {@linkplain
 * java.nio.channels.Selector selection operation} is interrupted then it causes the
 * selection operation to return early, with the thread's interrupted status set.
 *
 * <p> Code that doesn't invoke any interruptible methods can still respond to interrupt
 * by polling the current thread's interrupted status with
 * {@link Thread#currentThread() Thread.currentThread()}.{@link #isInterrupted()
 * isInterrupted()}.
 *
 * <p> In addition to the {@link #interrupt()} and {@link #isInterrupted()} methods,
 * {@code Thread} also defines the static {@link #interrupted() Thread.interrupted()}
 * method to test the current thread's interrupted status and clear it. It should be rare
 * to need to use this method.
 *
 * <h2>Null Handling</h2>
 * Unless otherwise specified, passing a {@code null} argument to a constructor
 * or method in this class will cause a {@link NullPointerException} to be thrown.
 *
 * @implNote
 * In the JDK Reference Implementation, the virtual thread scheduler may be configured
 * with the following system properties:
 * <table class="striped">
 * <caption style="display:none">System properties</caption>
 *   <thead>
 *   <tr>
 *     <th scope="col">System property</th>
 *     <th scope="col">Description</th>
 *   </tr>
 *   </thead>
 *   <tbody>
 *   <tr>
 *     <th scope="row">
 *       {@systemProperty jdk.virtualThreadScheduler.parallelism}
 *     </th>
 *     <td> The scheduler's target parallelism. This is the number of platform threads
 *       available for scheduling virtual threads. It defaults to the number of available
 *       processors. </td>
 *   </tr>
 *   <tr>
 *     <th scope="row">
 *       {@systemProperty jdk.virtualThreadScheduler.maxPoolSize}
 *     </th>
 *     <td> The maximum number of platform threads available to the scheduler.
 *       It defaults to 256. </td>
 *   </tr>
 *   </tbody>
 * </table>
 * <p> The virtual thread scheduler can be monitored and managed with the
 * {@code jdk.management.VirtualThreadSchedulerMXBean} management interface.
 *
 * @since   1.0
 */
public class Thread implements Runnable {
    /* Make sure registerNatives is the first thing <clinit> does. */
    private static native void registerNatives();
    static {
        registerNatives();
    }

    /*
     * Reserved for exclusive use by the JVM. Cannot be moved to the FieldHolder
     * as it needs to be set by the VM for JNI attaching threads, before executing
     * the constructor that will create the FieldHolder. The historically named
     * `eetop` holds the address of the underlying VM JavaThread, and is set to
     * non-zero when the thread is started, and reset to zero when the thread terminates.
     * A non-zero value indicates this thread isAlive().
     */
    private volatile long eetop;

    // thread id
    private final long tid;

    // thread name
    private volatile String name;

    // interrupted status (read/written by VM)
    volatile boolean interrupted;

    // context ClassLoader
    private volatile ClassLoader contextClassLoader;

    // Additional fields for platform threads.
    // All fields, except task and terminatingThreadLocals, are accessed directly by the VM.
    private static class FieldHolder {
        final ThreadGroup group;
        final Runnable task;
        final long stackSize;
        volatile int priority;
        volatile boolean daemon;
        volatile int threadStatus;

        // Used by NativeThread for signalling
        @Stable long nativeThreadID;

        // This map is maintained by the ThreadLocal class
        ThreadLocal.ThreadLocalMap terminatingThreadLocals;

        FieldHolder(ThreadGroup group,
                    Runnable task,
                    long stackSize,
                    int priority,
                    boolean daemon) {
            this.group = group;
            this.task = task;
            this.stackSize = stackSize;
            this.priority = priority;
            if (daemon)
                this.daemon = true;
        }
    }
    private final FieldHolder holder;

    ThreadLocal.ThreadLocalMap terminatingThreadLocals() {
        return holder.terminatingThreadLocals;
    }

    void setTerminatingThreadLocals(ThreadLocal.ThreadLocalMap map) {
        holder.terminatingThreadLocals = map;
    }

    long nativeThreadID() {
        return holder.nativeThreadID;
    }

    void setNativeThreadID(long id) {
        holder.nativeThreadID = id;
    }

    /*
     * ThreadLocal values pertaining to this thread. This map is maintained
     * by the ThreadLocal class.
     */
    private ThreadLocal.ThreadLocalMap threadLocals;

    ThreadLocal.ThreadLocalMap threadLocals() {
        return threadLocals;
    }

    void setThreadLocals(ThreadLocal.ThreadLocalMap map) {
        threadLocals = map;
    }

    /*
     * InheritableThreadLocal values pertaining to this thread. This map is
     * maintained by the InheritableThreadLocal class.
     */
    private ThreadLocal.ThreadLocalMap inheritableThreadLocals;

    ThreadLocal.ThreadLocalMap inheritableThreadLocals() {
        return inheritableThreadLocals;
    }

    void setInheritableThreadLocals(ThreadLocal.ThreadLocalMap map) {
        inheritableThreadLocals = map;
    }

    /*
     * Scoped value bindings are maintained by the ScopedValue class.
     */
    private Object scopedValueBindings;

    // Special value to indicate this is a newly-created Thread
    // Note that his must match the declaration in ScopedValue.
    private static final Object NEW_THREAD_BINDINGS = Thread.class;

    static Object scopedValueBindings() {
        return currentThread().scopedValueBindings;
    }

    static void setScopedValueBindings(Object bindings) {
        currentThread().scopedValueBindings = bindings;
    }

    /**
     * Search the stack for the most recent scoped-value bindings.
     */
    @IntrinsicCandidate
    static native Object findScopedValueBindings();

    /**
     * Inherit the scoped-value bindings from the given container.
     * Invoked when starting a thread.
     */
    void inheritScopedValueBindings(ThreadContainer container) {
        ScopedValueContainer.BindingsSnapshot snapshot;
        if (container.owner() != null
                && (snapshot = container.scopedValueBindings()) != null) {

            // bindings established for running/calling an operation
            Object bindings = snapshot.scopedValueBindings();
            if (currentThread().scopedValueBindings != bindings) {
                throw new StructureViolationException("Scoped value bindings have changed");
            }

            this.scopedValueBindings = bindings;
        }
    }

    /*
     * Lock object for thread interrupt.
     */
    final Object interruptLock = new Object();

    /**
     * The argument supplied to the current call to
     * java.util.concurrent.locks.LockSupport.park.
     * Set by (private) java.util.concurrent.locks.LockSupport.setBlocker
     * Accessed using java.util.concurrent.locks.LockSupport.getBlocker
     */
    private volatile Object parkBlocker;

    /* The object in which this thread is blocked in an interruptible I/O
     * operation, if any.  The blocker's interrupt method should be invoked
     * after setting this thread's interrupted status.
     */
    private Interruptible nioBlocker;

    Interruptible nioBlocker() {
        //assert Thread.holdsLock(interruptLock);
        return nioBlocker;
    }

    /* Set the blocker field; invoked via jdk.internal.access.SharedSecrets
     * from java.nio code
     */
    void blockedOn(Interruptible b) {
        //assert Thread.currentThread() == this;
        synchronized (interruptLock) {
            nioBlocker = b;
        }
    }

    /**
     * The minimum priority that a thread can have.
     */
    public static final int MIN_PRIORITY = 1;

    /**
     * The default priority that is assigned to a thread.
     */
    public static final int NORM_PRIORITY = 5;

    /**
     * The maximum priority that a thread can have.
     */
    public static final int MAX_PRIORITY = 10;

    /*
     * Current inner-most continuation.
     */
    private Continuation cont;

    /**
     * Returns the current continuation.
     */
    Continuation getContinuation() {
        return cont;
    }

    /**
     * Sets the current continuation.
     */
    void setContinuation(Continuation cont) {
        this.cont = cont;
    }

    /**
     * Returns the Thread object for the current platform thread. If the
     * current thread is a virtual thread then this method returns the carrier.
     */
    @IntrinsicCandidate
    static native Thread currentCarrierThread();

    /**
     * Returns the Thread object for the current thread.
     * @return  the current thread
     */
    @IntrinsicCandidate
    public static native Thread currentThread();

    /**
     * Sets the Thread object to be returned by Thread.currentThread().
     */
    @IntrinsicCandidate
    native void setCurrentThread(Thread thread);

    // ScopedValue support:

    @IntrinsicCandidate
    static native Object[] scopedValueCache();

    @IntrinsicCandidate
    static native void setScopedValueCache(Object[] cache);

    @IntrinsicCandidate
    static native void ensureMaterializedForStackWalk(Object o);

    /**
     * A hint to the scheduler that the current thread is willing to yield
     * its current use of a processor. The scheduler is free to ignore this
     * hint.
     *
     * <p> Yield is a heuristic attempt to improve relative progression
     * between threads that would otherwise over-utilise a CPU. Its use
     * should be combined with detailed profiling and benchmarking to
     * ensure that it actually has the desired effect.
     *
     * <p> It is rarely appropriate to use this method. It may be useful
     * for debugging or testing purposes, where it may help to reproduce
     * bugs due to race conditions. It may also be useful when designing
     * concurrency control constructs such as the ones in the
     * {@link java.util.concurrent.locks} package.
     */
    public static void yield() {
        if (currentThread() instanceof VirtualThread vthread) {
            vthread.tryYield();
        } else {
            yield0();
        }
    }

    private static native void yield0();

    /**
     * Called before sleeping to create a jdk.ThreadSleep event.
     */
    private static ThreadSleepEvent beforeSleep(long nanos) {
        try {
            ThreadSleepEvent event = new ThreadSleepEvent();
            if (event.isEnabled()) {
                event.time = nanos;
                event.begin();
                return event;
            }
        } catch (OutOfMemoryError e) {
            // ignore
        }
        return null;
    }


    /**
     * Called after sleeping to commit the jdk.ThreadSleep event.
     */
    private static void afterSleep(ThreadSleepEvent event) {
        if (event != null) {
            try {
                event.commit();
            } catch (OutOfMemoryError e) {
                // ignore
            }
        }
    }

    /**
     * Sleep for the specified number of nanoseconds, subject to the precision
     * and accuracy of system timers and schedulers.
     */
    private static void sleepNanos(long nanos) throws InterruptedException {
        ThreadSleepEvent event = beforeSleep(nanos);
        try {
            if (currentThread() instanceof VirtualThread vthread) {
                vthread.sleepNanos(nanos);
            } else {
                sleepNanos0(nanos);
            }
        } finally {
            afterSleep(event);
        }
    }

    private static native void sleepNanos0(long nanos) throws InterruptedException;

    /**
     * Causes the currently executing thread to sleep (temporarily cease
     * execution) for the specified number of milliseconds, subject to
     * the precision and accuracy of system timers and schedulers. The thread
     * does not lose ownership of any monitors.
     *
     * @param  millis
     *         the length of time to sleep in milliseconds
     *
     * @throws  IllegalArgumentException
     *          if the value of {@code millis} is negative
     *
     * @throws  InterruptedException
     *          if any thread has interrupted the current thread. The
     *          <i>interrupted status</i> of the current thread is
     *          cleared when this exception is thrown.
     */
    public static void sleep(long millis) throws InterruptedException {
        if (millis < 0) {
            throw new IllegalArgumentException("timeout value is negative");
        }
        long nanos = MILLISECONDS.toNanos(millis);
        sleepNanos(nanos);
    }

    /**
     * Causes the currently executing thread to sleep (temporarily cease
     * execution) for the specified number of milliseconds plus the specified
     * number of nanoseconds, subject to the precision and accuracy of system
     * timers and schedulers. The thread does not lose ownership of any
     * monitors.
     *
     * @param  millis
     *         the length of time to sleep in milliseconds
     *
     * @param  nanos
     *         {@code 0-999999} additional nanoseconds to sleep
     *
     * @throws  IllegalArgumentException
     *          if the value of {@code millis} is negative, or the value of
     *          {@code nanos} is not in the range {@code 0-999999}
     *
     * @throws  InterruptedException
     *          if any thread has interrupted the current thread. The
     *          <i>interrupted status</i> of the current thread is
     *          cleared when this exception is thrown.
     */
    public static void sleep(long millis, int nanos) throws InterruptedException {
        if (millis < 0) {
            throw new IllegalArgumentException("timeout value is negative");
        }

        if (nanos < 0 || nanos > 999999) {
            throw new IllegalArgumentException("nanosecond timeout value out of range");
        }

        // total sleep time, in nanoseconds
        long totalNanos = MILLISECONDS.toNanos(millis);
        totalNanos += Math.min(Long.MAX_VALUE - totalNanos, nanos);
        sleepNanos(totalNanos);
    }

    /**
     * Causes the currently executing thread to sleep (temporarily cease
     * execution) for the specified duration, subject to the precision and
     * accuracy of system timers and schedulers. This method is a no-op if
     * the duration is {@linkplain Duration#isNegative() negative}.
     *
     * @param  duration
     *         the duration to sleep
     *
     * @throws  InterruptedException
     *          if the current thread is interrupted while sleeping. The
     *          <i>interrupted status</i> of the current thread is
     *          cleared when this exception is thrown.
     *
     * @since 19
     */
    public static void sleep(Duration duration) throws InterruptedException {
        long nanos = NANOSECONDS.convert(duration);  // MAX_VALUE if > 292 years
        if (nanos < 0) {
            return;
        }
        sleepNanos(nanos);
    }

    /**
     * Indicates that the caller is momentarily unable to progress, until the
     * occurrence of one or more actions on the part of other activities. By
     * invoking this method within each iteration of a spin-wait loop construct,
     * the calling thread indicates to the runtime that it is busy-waiting.
     * The runtime may take action to improve the performance of invoking
     * spin-wait loop constructions.
     *
     * @apiNote
     * As an example consider a method in a class that spins in a loop until
     * some flag is set outside of that method. A call to the {@code onSpinWait}
     * method should be placed inside the spin loop.
     * {@snippet :
     *     class EventHandler {
     *         volatile boolean eventNotificationNotReceived;
     *         void waitForEventAndHandleIt() {
     *             while ( eventNotificationNotReceived ) {
     *                 Thread.onSpinWait();
     *             }
     *             readAndProcessEvent();
     *         }
     *
     *         void readAndProcessEvent() {
     *             // Read event from some source and process it
     *              . . .
     *         }
     *     }
     * }
     * <p>
     * The code above would remain correct even if the {@code onSpinWait}
     * method was not called at all. However on some architectures the Java
     * Virtual Machine may issue the processor instructions to address such
     * code patterns in a more beneficial way.
     *
     * @since 9
     */
    @IntrinsicCandidate
    public static void onSpinWait() {}

    /**
     * Characteristic value signifying that initial values for {@link
     * InheritableThreadLocal inheritable-thread-locals} are not inherited from
     * the constructing thread.
     * See Thread initialization.
     */
    static final int NO_INHERIT_THREAD_LOCALS = 1 << 2;

    /**
     * Thread identifier assigned to the primordial thread.
     */
    static final long PRIMORDIAL_TID = 3;

    /**
     * Helper class to generate thread identifiers. The identifiers start at
     * {@link Thread#PRIMORDIAL_TID}&nbsp;+1 as this class cannot be used during
     * early startup to generate the identifier for the primordial thread. The
     * counter is off-heap and shared with the VM to allow it to assign thread
     * identifiers to non-Java threads.
     * See Thread initialization.
     */
    private static class ThreadIdentifiers {
        private static final Unsafe U;
        private static final long NEXT_TID_OFFSET;
        static {
            U = Unsafe.getUnsafe();
            NEXT_TID_OFFSET = Thread.getNextThreadIdOffset();
        }
        static long next() {
            return U.getAndAddLong(null, NEXT_TID_OFFSET, 1);
        }
    }

    /**
     * Initializes a platform Thread.
     *
     * @param g the Thread group, can be null
     * @param name the name of the new Thread
     * @param characteristics thread characteristics
     * @param task the object whose run() method gets called
     * @param stackSize the desired stack size for the new thread, or
     *        zero to indicate that this parameter is to be ignored.
     */
    Thread(ThreadGroup g, String name, int characteristics, Runnable task, long stackSize) {

        Thread parent = currentThread();
        boolean attached = (parent == this);   // primordial or JNI attached

        if (attached) {
            if (g == null) {
                throw new InternalError("group cannot be null when attaching");
            }
            this.holder = new FieldHolder(g, task, stackSize, NORM_PRIORITY, false);
        } else {
            if (g == null) {
                // default to current thread's group
                g = parent.getThreadGroup();
            }
            int priority = Math.min(parent.getPriority(), g.getMaxPriority());
            this.holder = new FieldHolder(g, task, stackSize, priority, parent.isDaemon());
        }

        if (attached && VM.initLevel() < 1) {
            this.tid = PRIMORDIAL_TID;  // primordial thread
        } else {
            this.tid = ThreadIdentifiers.next();
        }

        this.name = (name != null) ? name : genThreadName();

        // thread locals
        if (!attached) {
            if ((characteristics & NO_INHERIT_THREAD_LOCALS) == 0) {
                ThreadLocal.ThreadLocalMap parentMap = parent.inheritableThreadLocals;
                if (parentMap != null && parentMap.size() > 0) {
                    this.inheritableThreadLocals = ThreadLocal.createInheritedMap(parentMap);
                }
                if (VM.isBooted()) {
                    this.contextClassLoader = parent.getContextClassLoader();
                }
            } else if (VM.isBooted()) {
                // default CCL to the system class loader when not inheriting
                this.contextClassLoader = ClassLoader.getSystemClassLoader();
            }
        }

        // special value to indicate this is a newly-created Thread
        // Note that his must match the declaration in ScopedValue.
        this.scopedValueBindings = NEW_THREAD_BINDINGS;
    }

    /**
     * Initializes a virtual Thread.
     *
     * @param name thread name, can be null
     * @param characteristics thread characteristics
     * @param bound true when bound to an OS thread
     */
    Thread(String name, int characteristics, boolean bound) {
        this.tid = ThreadIdentifiers.next();
        this.name = (name != null) ? name : "";

        // thread locals
        if ((characteristics & NO_INHERIT_THREAD_LOCALS) == 0) {
            Thread parent = currentThread();
            ThreadLocal.ThreadLocalMap parentMap = parent.inheritableThreadLocals;
            if (parentMap != null && parentMap.size() > 0) {
                this.inheritableThreadLocals = ThreadLocal.createInheritedMap(parentMap);
            }
            this.contextClassLoader = parent.getContextClassLoader();
        } else {
            // default CCL to the system class loader when not inheriting
            this.contextClassLoader = ClassLoader.getSystemClassLoader();
        }

        // special value to indicate this is a newly-created Thread
        this.scopedValueBindings = NEW_THREAD_BINDINGS;

        // create a FieldHolder object, needed when bound to an OS thread
        if (bound) {
            ThreadGroup g = Constants.VTHREAD_GROUP;
            int pri = NORM_PRIORITY;
            this.holder = new FieldHolder(g, null, -1, pri, true);
        } else {
            this.holder = null;
        }
    }

    /**
     * Returns a builder for creating a platform {@code Thread} or {@code ThreadFactory}
     * that creates platform threads.
     *
     * @apiNote The following are examples using the builder:
     * {@snippet :
     *   // Start a daemon thread to run a task
     *   Thread thread = Thread.ofPlatform().daemon().start(runnable);
     *
     *   // Create an unstarted thread with name "duke", its start() method
     *   // must be invoked to schedule it to execute.
     *   Thread thread = Thread.ofPlatform().name("duke").unstarted(runnable);
     *
     *   // A ThreadFactory that creates daemon threads named "worker-0", "worker-1", ...
     *   ThreadFactory factory = Thread.ofPlatform().daemon().name("worker-", 0).factory();
     * }
     *
     * @return A builder for creating {@code Thread} or {@code ThreadFactory} objects.
     * @since 21
     */
    public static Builder.OfPlatform ofPlatform() {
        return new ThreadBuilders.PlatformThreadBuilder();
    }

    /**
     * Returns a builder for creating a virtual {@code Thread} or {@code ThreadFactory}
     * that creates virtual threads.
     *
     * @apiNote The following are examples using the builder:
     * {@snippet :
     *   // Start a virtual thread to run a task.
     *   Thread thread = Thread.ofVirtual().start(runnable);
     *
     *   // A ThreadFactory that creates virtual threads
     *   ThreadFactory factory = Thread.ofVirtual().factory();
     * }
     *
     * @return A builder for creating {@code Thread} or {@code ThreadFactory} objects.
     * @since 21
     */
    public static Builder.OfVirtual ofVirtual() {
        return new ThreadBuilders.VirtualThreadBuilder();
    }

    /**
     * A builder for {@link Thread} and {@link ThreadFactory} objects.
     *
     * <p> {@code Builder} defines methods to set {@code Thread} properties such
     * as the thread {@link #name(String) name}. This includes properties that would
     * otherwise be <a href="Thread.html#inheritance">inherited</a>. Once set, a
     * {@code Thread} or {@code ThreadFactory} is created with the following methods:
     *
     * <ul>
     *     <li> The {@linkplain #unstarted(Runnable) unstarted} method creates a new
     *          <em>unstarted</em> {@code Thread} to run a task. The {@code Thread}'s
     *          {@link Thread#start() start} method must be invoked to schedule the
     *          thread to execute.
     *     <li> The {@linkplain #start(Runnable) start} method creates a new {@code
     *          Thread} to run a task and schedules the thread to execute.
     *     <li> The {@linkplain #factory() factory} method creates a {@code ThreadFactory}.
     * </ul>
     *
     * <p> A {@code Thread.Builder} is not thread safe. The {@code ThreadFactory}
     * returned by the builder's {@code factory()} method is thread safe.
     *
     * <p> Unless otherwise specified, passing a null argument to a method in
     * this interface causes a {@code NullPointerException} to be thrown.
     *
     * @see Thread#ofPlatform()
     * @see Thread#ofVirtual()
     * @since 21
     */
    public sealed interface Builder
            permits Builder.OfPlatform, Builder.OfVirtual {

        /**
         * Sets the thread name.
         * @param name thread name
         * @return this builder
         */
        Builder name(String name);

        /**
         * Sets the thread name to be the concatenation of a string prefix and
         * the string representation of a counter value. The counter's initial
         * value is {@code start}. It is incremented after a {@code Thread} is
         * created with this builder so that the next thread is named with
         * the new counter value. A {@code ThreadFactory} created with this
         * builder is seeded with the current value of the counter. The {@code
         * ThreadFactory} increments its copy of the counter after {@link
         * ThreadFactory#newThread(Runnable) newThread} is used to create a
         * {@code Thread}.
         *
         * @apiNote
         * The following example creates a builder that is invoked twice to start
         * two threads named "{@code worker-0}" and "{@code worker-1}".
         * {@snippet :
         *   Thread.Builder builder = Thread.ofPlatform().name("worker-", 0);
         *   Thread t1 = builder.start(task1);   // name "worker-0"
         *   Thread t2 = builder.start(task2);   // name "worker-1"
         * }
         *
         * @param prefix thread name prefix
         * @param start the starting value of the counter
         * @return this builder
         * @throws IllegalArgumentException if start is negative
         */
        Builder name(String prefix, long start);

        /**
         * Sets whether the thread inherits the initial values of {@linkplain
         * InheritableThreadLocal inheritable-thread-local} variables from the
         * constructing thread. The default is to inherit.
         *
         * @param inherit {@code true} to inherit, {@code false} to not inherit
         * @return this builder
         */
        Builder inheritInheritableThreadLocals(boolean inherit);

        /**
         * Sets the uncaught exception handler.
         * @param ueh uncaught exception handler
         * @return this builder
         */
        Builder uncaughtExceptionHandler(UncaughtExceptionHandler ueh);

        /**
         * Creates a new {@code Thread} from the current state of the builder to
         * run the given task. The {@code Thread}'s {@link Thread#start() start}
         * method must be invoked to schedule the thread to execute.
         *
         * @param task the object to run when the thread executes
         * @return a new unstarted Thread
         *
         * @see <a href="Thread.html#inheritance">Inheritance when creating threads</a>
         */
        Thread unstarted(Runnable task);

        /**
         * Creates a new {@code Thread} from the current state of the builder and
         * schedules it to execute.
         *
         * @param task the object to run when the thread executes
         * @return a new started Thread
         *
         * @see <a href="Thread.html#inheritance">Inheritance when creating threads</a>
         */
        Thread start(Runnable task);

        /**
         * Returns a {@code ThreadFactory} to create threads from the current
         * state of the builder. The returned thread factory is safe for use by
         * multiple concurrent threads.
         *
         * @return a thread factory to create threads
         */
        ThreadFactory factory();

        /**
         * A builder for creating a platform {@link Thread} or {@link ThreadFactory}
         * that creates platform threads.
         *
         * <p> Unless otherwise specified, passing a null argument to a method in
         * this interface causes a {@code NullPointerException} to be thrown.
         *
         * @see Thread#ofPlatform()
         * @since 21
         */
        sealed interface OfPlatform extends Builder
                permits ThreadBuilders.PlatformThreadBuilder {

            @Override OfPlatform name(String name);

            /**
             * @throws IllegalArgumentException {@inheritDoc}
             */
            @Override OfPlatform name(String prefix, long start);

            @Override OfPlatform inheritInheritableThreadLocals(boolean inherit);
            @Override OfPlatform uncaughtExceptionHandler(UncaughtExceptionHandler ueh);

            /**
             * Sets the thread group.
             * @param group the thread group
             * @return this builder
             */
            OfPlatform group(ThreadGroup group);

            /**
             * Sets the daemon status.
             * @param on {@code true} to create daemon threads
             * @return this builder
             */
            OfPlatform daemon(boolean on);

            /**
             * Sets the daemon status to {@code true}.
             * @implSpec The default implementation invokes {@linkplain #daemon(boolean)} with
             * a value of {@code true}.
             * @return this builder
             */
            default OfPlatform daemon() {
                return daemon(true);
            }

            /**
             * Sets the thread priority.
             * @param priority priority
             * @return this builder
             * @throws IllegalArgumentException if the priority is less than
             *        {@link Thread#MIN_PRIORITY} or greater than {@link Thread#MAX_PRIORITY}
             */
            OfPlatform priority(int priority);

            /**
             * Sets the desired stack size.
             *
             * <p> The stack size is the approximate number of bytes of address space
             * that the Java virtual machine is to allocate for the thread's stack. The
             * effect is highly platform dependent and the Java virtual machine is free
             * to treat the {@code stackSize} parameter as a "suggestion". If the value
             * is unreasonably low for the platform then a platform specific minimum
             * may be used. If the value is unreasonably high then a platform specific
             * maximum may be used. A value of zero is always ignored.
             *
             * @param stackSize the desired stack size
             * @return this builder
             * @throws IllegalArgumentException if the stack size is negative
             */
            OfPlatform stackSize(long stackSize);
        }

        /**
         * A builder for creating a virtual {@link Thread} or {@link ThreadFactory}
         * that creates virtual threads.
         *
         * <p> Unless otherwise specified, passing a null argument to a method in
         * this interface causes a {@code NullPointerException} to be thrown.
         *
         * @see Thread#ofVirtual()
         * @since 21
         */
        sealed interface OfVirtual extends Builder
                permits ThreadBuilders.VirtualThreadBuilder {

            @Override OfVirtual name(String name);

            /**
             * @throws IllegalArgumentException {@inheritDoc}
             */
            @Override OfVirtual name(String prefix, long start);

            @Override OfVirtual inheritInheritableThreadLocals(boolean inherit);
            @Override OfVirtual uncaughtExceptionHandler(UncaughtExceptionHandler ueh);
        }
    }

    /**
     * Throws CloneNotSupportedException as a Thread can not be meaningfully
     * cloned. Construct a new Thread instead.
     *
     * @throws  CloneNotSupportedException
     *          always
     */
    @Override
    protected Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    /**
     * Helper class for auto-numbering platform threads. The numbers start at
     * 0 and are separate from the thread identifier for historical reasons.
     */
    private static class ThreadNumbering {
        private static final Unsafe U;
        private static final Object NEXT_BASE;
        private static final long NEXT_OFFSET;
        static {
            U = Unsafe.getUnsafe();
            try {
                Field nextField = ThreadNumbering.class.getDeclaredField("next");
                NEXT_BASE = U.staticFieldBase(nextField);
                NEXT_OFFSET = U.staticFieldOffset(nextField);
            } catch (NoSuchFieldException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
        private static volatile int next;
        static int next() {
            return U.getAndAddInt(NEXT_BASE, NEXT_OFFSET, 1);
        }
    }

    /**
     * Generates a thread name of the form {@code Thread-<n>}.
     */
    static String genThreadName() {
        return "Thread-" + ThreadNumbering.next();
    }

    /**
     * Throws NullPointerException if the name is null. Avoids use of
     * Objects.requireNonNull in early startup.
     */
    private static String checkName(String name) {
        if (name == null)
            throw new NullPointerException("'name' is null");
        return name;
    }

    /**
     * Initializes a new platform {@code Thread}. This constructor has the same
     * effect as {@linkplain #Thread(ThreadGroup,Runnable,String) Thread}
     * {@code (null, null, gname)}, where {@code gname} is a newly generated
     * name. Automatically generated names are of the form
     * {@code "Thread-"+}<i>n</i>, where <i>n</i> is an integer.
     *
     * <p> This constructor is only useful when extending {@code Thread} to
     * override the {@link #run()} method.
     *
     * @see <a href="#inheritance">Inheritance when creating threads</a>
     */
    public Thread() {
        this(null, null, 0, null, 0);
    }

    /**
     * Initializes a new platform {@code Thread}. This constructor has the same
     * effect as {@linkplain #Thread(ThreadGroup,Runnable,String) Thread}
     * {@code (null, task, gname)}, where {@code gname} is a newly generated
     * name. Automatically generated names are of the form
     * {@code "Thread-"+}<i>n</i>, where <i>n</i> is an integer.
     *
     * <p> For a non-null task, invoking this constructor directly is equivalent to:
     * <pre>{@code Thread.ofPlatform().unstarted(task); }</pre>
     *
     * @param  task
     *         the object whose {@code run} method is invoked when this thread
     *         is started. If {@code null}, this classes {@code run} method does
     *         nothing.
     *
     * @see <a href="#inheritance">Inheritance when creating threads</a>
     */
    public Thread(Runnable task) {
        this(null, null, 0, task, 0);
    }

    /**
     * Initializes a new platform {@code Thread}. This constructor has the same
     * effect as {@linkplain #Thread(ThreadGroup,Runnable,String) Thread}
     * {@code (group, task, gname)}, where {@code gname} is a newly generated
     * name. Automatically generated names are of the form
     * {@code "Thread-"+}<i>n</i>, where <i>n</i> is an integer.
     *
     * <p> For a non-null group and task, invoking this constructor directly is
     * equivalent to:
     * <pre>{@code Thread.ofPlatform().group(group).unstarted(task); }</pre>
     *
     * @param  group
     *         the thread group. If {@code null} the group
     *         is set to the current thread's thread group.
     *
     * @param  task
     *         the object whose {@code run} method is invoked when this thread
     *         is started. If {@code null}, this thread's run method is invoked.
     *
     * @see <a href="#inheritance">Inheritance when creating threads</a>
     */
    public Thread(ThreadGroup group, Runnable task) {
        this(group, null, 0, task, 0);
    }

    /**
     * Initializes a new platform {@code Thread}. This constructor has the same
     * effect as {@linkplain #Thread(ThreadGroup,Runnable,String) Thread}
     * {@code (null, null, name)}.
     *
     * <p> This constructor is only useful when extending {@code Thread} to
     * override the {@link #run()} method.
     *
     * @param   name
     *          the name of the new thread
     *
     * @see <a href="#inheritance">Inheritance when creating threads</a>
     */
    public Thread(String name) {
        this(null, checkName(name), 0, null, 0);
    }

    /**
     * Initializes a new platform {@code Thread}. This constructor has the same
     * effect as {@linkplain #Thread(ThreadGroup,Runnable,String) Thread}
     * {@code (group, null, name)}.
     *
     * <p> This constructor is only useful when extending {@code Thread} to
     * override the {@link #run()} method.
     *
     * @param  group
     *         the thread group. If {@code null}, the group
     *         is set to the current thread's thread group.
     *
     * @param  name
     *         the name of the new thread
     *
     * @see <a href="#inheritance">Inheritance when creating threads</a>
     */
    public Thread(ThreadGroup group, String name) {
        this(group, checkName(name), 0, null, 0);
    }

    /**
     * Initializes a new platform {@code Thread}. This constructor has the same
     * effect as {@linkplain #Thread(ThreadGroup,Runnable,String) Thread}
     * {@code (null, task, name)}.
     *
     * <p> For a non-null task and name, invoking this constructor directly is
     * equivalent to:
     * <pre>{@code Thread.ofPlatform().name(name).unstarted(task); }</pre>
     *
     * @param  task
     *         the object whose {@code run} method is invoked when this thread
     *         is started. If {@code null}, this thread's run method is invoked.
     *
     * @param  name
     *         the name of the new thread
     *
     * @see <a href="#inheritance">Inheritance when creating threads</a>
     */
    public Thread(Runnable task, String name) {
        this(null, checkName(name), 0, task, 0);
    }

    /**
     * Initializes a new platform {@code Thread} so that it has {@code task}
     * as its run object, has the specified {@code name} as its name,
     * and belongs to the thread group referred to by {@code group}.
     *
     * <p>The priority of the newly created thread is the smaller of
     * priority of the thread creating it and the maximum permitted
     * priority of the thread group. The method {@linkplain #setPriority
     * setPriority} may be used to change the priority to a new value.
     *
     * <p>The newly created thread is initially marked as being a daemon
     * thread if and only if the thread creating it is currently marked
     * as a daemon thread. The method {@linkplain #setDaemon setDaemon}
     * may be used to change whether or not a thread is a daemon.
     *
     * <p>For a non-null group, task, and name, invoking this constructor directly
     * is equivalent to:
     * <pre>{@code Thread.ofPlatform().group(group).name(name).unstarted(task); }</pre>
     *
     * @param  group
     *         the thread group. If {@code null}, the group
     *         is set to the current thread's thread group.
     *
     * @param  task
     *         the object whose {@code run} method is invoked when this thread
     *         is started. If {@code null}, this thread's run method is invoked.
     *
     * @param  name
     *         the name of the new thread
     *
     * @see <a href="#inheritance">Inheritance when creating threads</a>
     */
    public Thread(ThreadGroup group, Runnable task, String name) {
        this(group, checkName(name), 0, task, 0);
    }

    /**
     * Initializes a new platform {@code Thread} so that it has {@code task}
     * as its run object, has the specified {@code name} as its name,
     * and belongs to the thread group referred to by {@code group}, and has
     * the specified <i>stack size</i>.
     *
     * <p>This constructor is identical to {@link
     * #Thread(ThreadGroup,Runnable,String)} with the exception of the fact
     * that it allows the thread stack size to be specified.  The stack size
     * is the approximate number of bytes of address space that the virtual
     * machine is to allocate for this thread's stack.  <b>The effect of the
     * {@code stackSize} parameter, if any, is highly platform dependent.</b>
     *
     * <p>On some platforms, specifying a higher value for the
     * {@code stackSize} parameter may allow a thread to achieve greater
     * recursion depth before throwing a {@link StackOverflowError}.
     * Similarly, specifying a lower value may allow a greater number of
     * threads to exist concurrently without throwing an {@link
     * OutOfMemoryError} (or other internal error).  The details of
     * the relationship between the value of the {@code stackSize} parameter
     * and the maximum recursion depth and concurrency level are
     * platform-dependent.  <b>On some platforms, the value of the
     * {@code stackSize} parameter may have no effect whatsoever.</b>
     *
     * <p>The virtual machine is free to treat the {@code stackSize}
     * parameter as a suggestion.  If the specified value is unreasonably low
     * for the platform, the virtual machine may instead use some
     * platform-specific minimum value; if the specified value is unreasonably
     * high, the virtual machine may instead use some platform-specific
     * maximum.  Likewise, the virtual machine is free to round the specified
     * value up or down as it sees fit (or to ignore it completely).
     *
     * <p>Specifying a value of zero for the {@code stackSize} parameter will
     * cause this constructor to behave exactly like the
     * {@code Thread(ThreadGroup, Runnable, String)} constructor.
     *
     * <p><i>Due to the platform-dependent nature of the behavior of this
     * constructor, extreme care should be exercised in its use.
     * The thread stack size necessary to perform a given computation will
     * likely vary from one JRE implementation to another.  In light of this
     * variation, careful tuning of the stack size parameter may be required,
     * and the tuning may need to be repeated for each JRE implementation on
     * which an application is to run.</i>
     *
     * <p>Implementation note: Java platform implementers are encouraged to
     * document their implementation's behavior with respect to the
     * {@code stackSize} parameter.
     *
     * <p>For a non-null group, task, and name, invoking this constructor directly
     * is equivalent to:
     * <pre>{@code Thread.ofPlatform().group(group).name(name).stackSize(stackSize).unstarted(task); }</pre>
     *
     * @param  group
     *         the thread group. If {@code null}, the group
     *         is set to the current thread's thread group.
     *
     * @param  task
     *         the object whose {@code run} method is invoked when this thread
     *         is started. If {@code null}, this thread's run method is invoked.
     *
     * @param  name
     *         the name of the new thread
     *
     * @param  stackSize
     *         the desired stack size for the new thread, or zero to indicate
     *         that this parameter is to be ignored.
     *
     * @since 1.4
     * @see <a href="#inheritance">Inheritance when creating threads</a>
     */
    public Thread(ThreadGroup group, Runnable task, String name, long stackSize) {
        this(group, checkName(name), 0, task, stackSize);
    }

    /**
     * Initializes a new platform {@code Thread} so that it has {@code task}
     * as its run object, has the specified {@code name} as its name,
     * belongs to the thread group referred to by {@code group}, has
     * the specified {@code stackSize}, and inherits initial values for
     * {@linkplain InheritableThreadLocal inheritable thread-local} variables
     * if {@code inheritThreadLocals} is {@code true}.
     *
     * <p> This constructor is identical to {@link
     * #Thread(ThreadGroup,Runnable,String,long)} with the added ability to
     * suppress, or not, the inheriting of initial values for inheritable
     * thread-local variables from the constructing thread. This allows for
     * finer grain control over inheritable thread-locals. Care must be taken
     * when passing a value of {@code false} for {@code inheritThreadLocals},
     * as it may lead to unexpected behavior if the new thread executes code
     * that expects a specific thread-local value to be inherited.
     *
     * <p> Specifying a value of {@code true} for the {@code inheritThreadLocals}
     * parameter will cause this constructor to behave exactly like the
     * {@code Thread(ThreadGroup, Runnable, String, long)} constructor.
     *
     * <p> For a non-null group, task, and name, invoking this constructor directly
     * is equivalent to:
     * <pre>{@code Thread.ofPlatform()
     *      .group(group)
     *      .name(name)
     *      .stackSize(stackSize)
     *      .inheritInheritableThreadLocals(inheritInheritableThreadLocals)
     *      .unstarted(task); }</pre>
     *
     * @param  group
     *         the thread group. If {@code null}, the group
     *         is set to the current thread's thread group.
     *
     * @param  task
     *         the object whose {@code run} method is invoked when this thread
     *         is started. If {@code null}, this thread's run method is invoked.
     *
     * @param  name
     *         the name of the new thread
     *
     * @param  stackSize
     *         the desired stack size for the new thread, or zero to indicate
     *         that this parameter is to be ignored
     *
     * @param  inheritInheritableThreadLocals
     *         if {@code true}, inherit initial values for inheritable
     *         thread-locals from the constructing thread, otherwise no initial
     *         values are inherited
     *
     * @since 9
     * @see <a href="#inheritance">Inheritance when creating threads</a>
     */
    public Thread(ThreadGroup group, Runnable task, String name,
                  long stackSize, boolean inheritInheritableThreadLocals) {
        this(group, checkName(name),
                (inheritInheritableThreadLocals ? 0 : NO_INHERIT_THREAD_LOCALS),
                task, stackSize);
    }

    /**
     * Creates a virtual thread to execute a task and schedules it to execute.
     *
     * <p> This method is equivalent to:
     * <pre>{@code Thread.ofVirtual().start(task); }</pre>
     *
     * @param task the object to run when the thread executes
     * @return a new, and started, virtual thread
     * @see <a href="#inheritance">Inheritance when creating threads</a>
     * @since 21
     */
    public static Thread startVirtualThread(Runnable task) {
        Objects.requireNonNull(task);
        var thread = ThreadBuilders.newVirtualThread(null, null, 0, task);
        thread.start();
        return thread;
    }

    /**
     * Returns {@code true} if this thread is a virtual thread. A virtual thread
     * is scheduled by the Java virtual machine rather than the operating system.
     *
     * @return {@code true} if this thread is a virtual thread
     *
     * @since 21
     */
    public final boolean isVirtual() {
        return (this instanceof BaseVirtualThread);
    }

    /**
     * Schedules this thread to begin execution. The thread will execute
     * independently of the current thread.
     *
     * <p> A thread can be started at most once. In particular, a thread can not
     * be restarted after it has terminated.
     *
     * @throws IllegalThreadStateException if the thread was already started
     */
    public void start() {
        synchronized (this) {
            // zero status corresponds to state "NEW".
            if (holder.threadStatus != 0)
                throw new IllegalThreadStateException();
            start0();
        }
    }

    /**
     * Schedules this thread to begin execution in the given thread container.
     * @throws IllegalStateException if the container is shutdown or closed
     * @throws IllegalThreadStateException if the thread has already been started
     */
    void start(ThreadContainer container) {
        synchronized (this) {
            // zero status corresponds to state "NEW".
            if (holder.threadStatus != 0)
                throw new IllegalThreadStateException();

            // bind thread to container
            if (this.container != null)
                throw new IllegalThreadStateException();
            setThreadContainer(container);

            // start thread
            boolean started = false;
            container.add(this);  // may throw
            try {
                // scoped values may be inherited
                inheritScopedValueBindings(container);

                start0();
                started = true;
            } finally {
                if (!started) {
                    container.remove(this);
                }
            }
        }
    }

    private native void start0();

    /**
     * This method is run by the thread when it executes. Subclasses of {@code
     * Thread} may override this method.
     *
     * <p> This method is not intended to be invoked directly. If this thread is a
     * platform thread created with a {@link Runnable} task then invoking this method
     * will invoke the task's {@code run} method. If this thread is a virtual thread
     * then invoking this method directly does nothing.
     *
     * @implSpec The default implementation executes the {@link Runnable} task that
     * the {@code Thread} was created with. If the thread was created without a task
     * then this method does nothing.
     */
    @Override
    public void run() {
        Runnable task = holder.task;
        if (task != null) {
            Object bindings = scopedValueBindings();
            runWith(bindings, task);
        }
    }

    /**
     * The VM recognizes this method as special, so any changes to the
     * name or signature require corresponding changes in
     * JVM_FindScopedValueBindings().
     */
    @Hidden
    @ForceInline
    final void runWith(Object bindings, Runnable op) {
        ensureMaterializedForStackWalk(bindings);
        op.run();
        Reference.reachabilityFence(bindings);
    }

    /**
     * Null out reference after Thread termination.
     */
    void clearReferences() {
        threadLocals = null;
        inheritableThreadLocals = null;
        if (uncaughtExceptionHandler != null)
            uncaughtExceptionHandler = null;
        if (nioBlocker != null)
            nioBlocker = null;
    }

    /**
     * This method is called by the VM to give a Thread
     * a chance to clean up before it actually exits.
     */
    private void exit() {
        try {
            // pop any remaining scopes from the stack, this may block
            if (headStackableScopes != null) {
                StackableScope.popAll();
            }
        } finally {
            // notify container that thread is exiting
            ThreadContainer container = threadContainer();
            if (container != null) {
                container.remove(this);
            }
        }

        try {
            if (terminatingThreadLocals() != null) {
                TerminatingThreadLocal.threadTerminated();
            }
        } finally {
            clearReferences();
        }
    }

    /**
     * Interrupts this thread.
     *
     * <p> If this thread is blocked in an invocation of the {@link
     * Object#wait() wait()}, {@link Object#wait(long) wait(long)}, or {@link
     * Object#wait(long, int) wait(long, int)} methods of the {@link Object}
     * class, or of the {@link #join()}, {@link #join(long)}, {@link
     * #join(long, int)}, {@link #sleep(long)}, or {@link #sleep(long, int)}
     * methods of this class, then its interrupted status will be cleared and it
     * will receive an {@link InterruptedException}.
     *
     * <p> If this thread is blocked in an I/O operation upon an {@link
     * java.nio.channels.InterruptibleChannel InterruptibleChannel}
     * then the channel will be closed, the thread's interrupted
     * status will be set, and the thread will receive a {@link
     * java.nio.channels.ClosedByInterruptException}.
     *
     * <p> If this thread is blocked in a {@link java.nio.channels.Selector}
     * then the thread's interrupted status will be set and it will return
     * immediately from the selection operation, possibly with a non-zero
     * value, just as if the selector's {@link
     * java.nio.channels.Selector#wakeup wakeup} method were invoked.
     *
     * <p> If none of the previous conditions hold then this thread's interrupted
     * status will be set. </p>
     *
     * <p> Interrupting a thread that is not alive need not have any effect.
     *
     * @implNote In the JDK Reference Implementation, interruption of a thread
     * that is not alive still records that the interrupt request was made and
     * will report it via {@link #interrupted()} and {@link #isInterrupted()}.
     *
     * @see ##thread-interruption Thread Interruption
     * @see #isInterrupted()
     */
    public void interrupt() {
        // Setting the interrupted status must be done before reading nioBlocker.
        interrupted = true;
        interrupt0();  // inform VM of interrupt

        // thread may be blocked in an I/O operation
        if (this != Thread.currentThread()) {
            Interruptible blocker;
            synchronized (interruptLock) {
                blocker = nioBlocker;
                if (blocker != null) {
                    blocker.interrupt(this);
                }
            }
            if (blocker != null) {
                blocker.postInterrupt();
            }
        }
    }

    /**
     * Tests whether the current thread has been interrupted.  The
     * <i>interrupted status</i> of the thread is cleared by this method.  In
     * other words, if this method were to be called twice in succession, the
     * second call would return false (unless the current thread were
     * interrupted again, after the first call had cleared its interrupted
     * status and before the second call had examined it).
     *
     * @apiNote It should be rare to use this method directly. It is intended
     * for cases that detect {@linkplain ##thread-interruption thread interruption}
     * and clear the interrupted status before throwing {@link InterruptedException}.
     * It may also be useful for cases that implement an <em>uninterruptible</em>
     * method that makes use of an <em>interruptible</em> method such as
     * {@link LockSupport#park()}. The {@code interrupted()} method can be used
     * to test if interrupted and clear the interrupted status to allow the code
     * retry the <em>interruptible</em> method. The <em>uninterruptible</em> method
     * should restore the interrupted status before it completes.
     *
     * @return  {@code true} if the current thread has been interrupted;
     *          {@code false} otherwise.
     * @see ##thread-interruption Thread Interruption
     * @see #isInterrupted()
     */
    public static boolean interrupted() {
        return currentThread().getAndClearInterrupt();
    }

    /**
     * Tests whether this thread has been interrupted.  The <i>interrupted
     * status</i> of the thread is unaffected by this method.
     *
     * @return  {@code true} if this thread has been interrupted;
     *          {@code false} otherwise.
     * @see ##thread-interruption Thread Interruption
     * @see #interrupt()
     */
    public boolean isInterrupted() {
        return interrupted;
    }

    final void setInterrupt() {
        // assert Thread.currentCarrierThread() == this;
        if (!interrupted) {
            interrupted = true;
            interrupt0();  // inform VM of interrupt
        }
    }

    final void clearInterrupt() {
        // assert Thread.currentCarrierThread() == this;
        if (interrupted) {
            interrupted = false;
            clearInterruptEvent();
        }
    }

    boolean getAndClearInterrupt() {
        boolean oldValue = interrupted;
        // We may have been interrupted the moment after we read the field,
        // so only clear the field if we saw that it was set and will return
        // true; otherwise we could lose an interrupt.
        if (oldValue) {
            interrupted = false;
            clearInterruptEvent();
        }
        return oldValue;
    }

    /**
     * Tests if this thread is alive. A thread is alive if it has
     * been started and has not yet terminated.
     *
     * @return  {@code true} if this thread is alive;
     *          {@code false} otherwise.
     */
    public final boolean isAlive() {
        return alive();
    }

    /**
     * Returns true if this thread is alive.
     * This method is non-final so it can be overridden.
     */
    boolean alive() {
        return eetop != 0;
    }

    /**
     * Changes the priority of this thread.
     *
     * For platform threads, the priority is set to the smaller of the specified
     * {@code newPriority} and the maximum permitted priority of the thread's
     * {@linkplain ThreadGroup thread group}.
     *
     * The priority of a virtual thread is always {@link Thread#NORM_PRIORITY}
     * and {@code newPriority} is ignored.
     *
     * @param newPriority the new thread priority
     * @throws  IllegalArgumentException if the priority is not in the
     *          range {@code MIN_PRIORITY} to {@code MAX_PRIORITY}.
     * @see #setPriority(int)
     * @see ThreadGroup#getMaxPriority()
     */
    public final void setPriority(int newPriority) {
        if (newPriority > MAX_PRIORITY || newPriority < MIN_PRIORITY) {
            throw new IllegalArgumentException();
        }
        if (!isVirtual()) {
            priority(newPriority);
        }
    }

    void priority(int newPriority) {
        ThreadGroup g = holder.group;
        if (g != null) {
            int maxPriority = g.getMaxPriority();
            if (newPriority > maxPriority) {
                newPriority = maxPriority;
            }
            setPriority0(holder.priority = newPriority);
        }
    }

    /**
     * Returns this thread's priority.
     *
     * <p> The priority of a virtual thread is always {@link Thread#NORM_PRIORITY}.
     *
     * @return  this thread's priority.
     * @see     #setPriority
     */
    public final int getPriority() {
        if (isVirtual()) {
            return Thread.NORM_PRIORITY;
        } else {
            return holder.priority;
        }
    }

    /**
     * Changes the name of this thread to be equal to the argument {@code name}.
     *
     * @implNote In the JDK Reference Implementation, if this thread is the
     * current thread, and it's a platform thread that was not attached to the
     * VM with the Java Native Interface
     * <a href="{@docRoot}/../specs/jni/invocation.html#attachcurrentthread">
     * AttachCurrentThread</a> function, then this method will set the operating
     * system thread name. This may be useful for debugging and troubleshooting
     * purposes.
     *
     * @param      name   the new name for this thread.
     *
     * @spec jni/index.html Java Native Interface Specification
     * @see        #getName
     */
    public final synchronized void setName(String name) {
        if (name == null) {
            throw new NullPointerException("name cannot be null");
        }
        this.name = name;
        if (!isVirtual() && Thread.currentThread() == this) {
            setNativeName(name);
        }
    }

    /**
     * Returns this thread's name.
     *
     * @return  this thread's name.
     * @see     #setName(String)
     */
    public final String getName() {
        return name;
    }

    /**
     * Returns the thread's thread group or {@code null} if the thread has
     * terminated.
     *
     * <p> The thread group returned for a virtual thread is the special
     * <a href="ThreadGroup.html#virtualthreadgroup"><em>ThreadGroup for
     * virtual threads</em></a>.
     *
     * @return  this thread's thread group or {@code null}
     */
    public final ThreadGroup getThreadGroup() {
        if (isTerminated()) {
            return null;
        } else {
            return isVirtual() ? virtualThreadGroup() : holder.group;
        }
    }

    /**
     * Returns an estimate of the number of {@linkplain #isAlive() live}
     * platform threads in the current thread's thread group and its subgroups.
     * Virtual threads are not included in the estimate.
     *
     * <p> The value returned is only an estimate because the number of
     * threads may change dynamically while this method traverses internal
     * data structures, and might be affected by the presence of certain
     * system threads. This method is intended primarily for debugging
     * and monitoring purposes.
     *
     * @return  an estimate of the number of live platform threads in the
     *          current thread's thread group and in any other thread group
     *          that has the current thread's thread group as an ancestor
     */
    public static int activeCount() {
        return currentThread().getThreadGroup().activeCount();
    }

    /**
     * Copies into the specified array every {@linkplain #isAlive() live}
     * platform thread in the current thread's thread group and its subgroups.
     * This method simply invokes the {@link java.lang.ThreadGroup#enumerate(Thread[])}
     * method of the current thread's thread group. Virtual threads are
     * not enumerated by this method.
     *
     * <p> An application might use the {@linkplain #activeCount activeCount}
     * method to get an estimate of how big the array should be, however
     * <i>if the array is too short to hold all the threads, the extra threads
     * are silently ignored.</i>  If it is critical to obtain every live
     * thread in the current thread's thread group and its subgroups, the
     * invoker should verify that the returned int value is strictly less
     * than the length of {@code tarray}.
     *
     * <p> Due to the inherent race condition in this method, it is recommended
     * that the method only be used for debugging and monitoring purposes.
     *
     * @param  tarray
     *         an array into which to put the list of threads
     *
     * @return  the number of threads put into the array
     */
    public static int enumerate(Thread[] tarray) {
        return currentThread().getThreadGroup().enumerate(tarray);
    }

    /**
     * Waits at most {@code millis} milliseconds for this thread to terminate.
     * A timeout of {@code 0} means to wait forever.
     * This method returns immediately, without waiting, if the thread has not
     * been {@link #start() started}.
     *
     * @implNote
     * For platform threads, the implementation uses a loop of {@code this.wait}
     * calls conditioned on {@code this.isAlive}. As a thread terminates the
     * {@code this.notifyAll} method is invoked. It is recommended that
     * applications not use {@code wait}, {@code notify}, or
     * {@code notifyAll} on {@code Thread} instances.
     *
     * @param  millis
     *         the time to wait in milliseconds
     *
     * @throws  IllegalArgumentException
     *          if the value of {@code millis} is negative
     *
     * @throws  InterruptedException
     *          if any thread has interrupted the current thread. The
     *          <i>interrupted status</i> of the current thread is
     *          cleared when this exception is thrown.
     */
    public final void join(long millis) throws InterruptedException {
        if (millis < 0)
            throw new IllegalArgumentException("timeout value is negative");

        if (this instanceof VirtualThread vthread) {
            if (isAlive()) {
                long nanos = MILLISECONDS.toNanos(millis);
                vthread.joinNanos(nanos);
            }
            return;
        }

        synchronized (this) {
            if (millis > 0) {
                if (isAlive()) {
                    final long startTime = System.nanoTime();
                    long delay = millis;
                    do {
                        wait(delay);
                    } while (isAlive() && (delay = millis -
                             NANOSECONDS.toMillis(System.nanoTime() - startTime)) > 0);
                }
            } else {
                while (isAlive()) {
                    wait(0);
                }
            }
        }
    }

    /**
     * Waits at most {@code millis} milliseconds plus
     * {@code nanos} nanoseconds for this thread to terminate.
     * If both arguments are {@code 0}, it means to wait forever.
     * This method returns immediately, without waiting, if the thread has not
     * been {@link #start() started}.
     *
     * @implNote
     * For platform threads, the implementation uses a loop of {@code this.wait}
     * calls conditioned on {@code this.isAlive}. As a thread terminates the
     * {@code this.notifyAll} method is invoked. It is recommended that
     * applications not use {@code wait}, {@code notify}, or
     * {@code notifyAll} on {@code Thread} instances.
     *
     * @param  millis
     *         the time to wait in milliseconds
     *
     * @param  nanos
     *         {@code 0-999999} additional nanoseconds to wait
     *
     * @throws  IllegalArgumentException
     *          if the value of {@code millis} is negative, or the value
     *          of {@code nanos} is not in the range {@code 0-999999}
     *
     * @throws  InterruptedException
     *          if any thread has interrupted the current thread. The
     *          <i>interrupted status</i> of the current thread is
     *          cleared when this exception is thrown.
     */
    public final void join(long millis, int nanos) throws InterruptedException {
        if (millis < 0) {
            throw new IllegalArgumentException("timeout value is negative");
        }

        if (nanos < 0 || nanos > 999999) {
            throw new IllegalArgumentException("nanosecond timeout value out of range");
        }

        if (this instanceof VirtualThread vthread) {
            if (isAlive()) {
                // convert arguments to a total in nanoseconds
                long totalNanos = MILLISECONDS.toNanos(millis);
                totalNanos += Math.min(Long.MAX_VALUE - totalNanos, nanos);
                vthread.joinNanos(totalNanos);
            }
            return;
        }

        if (nanos > 0 && millis < Long.MAX_VALUE) {
            millis++;
        }
        join(millis);
    }

    /**
     * Waits for this thread to terminate.
     *
     * <p> An invocation of this method behaves in exactly the same
     * way as the invocation
     *
     * <blockquote>
     * {@linkplain #join(long) join}{@code (0)}
     * </blockquote>
     *
     * @throws  InterruptedException
     *          if any thread has interrupted the current thread. The
     *          <i>interrupted status</i> of the current thread is
     *          cleared when this exception is thrown.
     */
    public final void join() throws InterruptedException {
        join(0);
    }

    /**
     * Waits for this thread to terminate for up to the given waiting duration.
     *
     * <p> This method does not wait if the duration to wait is less than or
     * equal to zero. In this case, the method just tests if the thread has
     * terminated.
     *
     * @param   duration
     *          the maximum duration to wait
     *
     * @return  {@code true} if the thread has terminated, {@code false} if the
     *          thread has not terminated
     *
     * @throws  InterruptedException
     *          if the current thread is interrupted while waiting.
     *          The <i>interrupted status</i> of the current thread is cleared
     *          when this exception is thrown.
     *
     * @throws  IllegalThreadStateException
     *          if this thread has not been started.
     *
     * @since 19
     */
    public final boolean join(Duration duration) throws InterruptedException {
        long nanos = NANOSECONDS.convert(duration); // MAX_VALUE if > 292 years

        Thread.State state = threadState();
        if (state == State.NEW)
            throw new IllegalThreadStateException("Thread not started");
        if (state == State.TERMINATED)
            return true;
        if (nanos <= 0)
            return false;

        if (this instanceof VirtualThread vthread) {
            return vthread.joinNanos(nanos);
        }

        // convert to milliseconds
        long millis = MILLISECONDS.convert(nanos, NANOSECONDS);
        if (nanos > NANOSECONDS.convert(millis, MILLISECONDS)) {
            millis += 1L;
        }
        join(millis);
        return isTerminated();
    }

    /**
     * Prints a stack trace of the current thread to the standard error stream.
     * This method is useful for debugging.
     */
    public static void dumpStack() {
        new Exception("Stack trace").printStackTrace();
    }

    /**
     * Marks this thread as either a <i>daemon</i> or <i>non-daemon</i> thread.
     * The <a href="Runtime.html#shutdown">shutdown sequence</a> begins when all
     * started non-daemon threads have terminated.
     *
     * <p> The daemon status of a virtual thread is always {@code true} and cannot be
     * changed by this method to {@code false}.
     *
     * <p> This method must be invoked before the thread is started. The behavior
     * of this method when the thread has terminated is not specified.
     *
     * @param  on
     *         if {@code true}, marks this thread as a daemon thread
     *
     * @throws  IllegalArgumentException
     *          if this is a virtual thread and {@code on} is false
     * @throws  IllegalThreadStateException
     *          if this thread is {@linkplain #isAlive alive}
     */
    public final void setDaemon(boolean on) {
        if (isVirtual() && !on)
            throw new IllegalArgumentException("'false' not legal for virtual threads");
        if (isAlive())
            throw new IllegalThreadStateException();
        if (!isVirtual())
            daemon(on);
    }

    void daemon(boolean on) {
        holder.daemon = on;
    }

    /**
     * Tests if this thread is a daemon thread.
     * The daemon status of a virtual thread is always {@code true}.
     *
     * @return  {@code true} if this thread is a daemon thread;
     *          {@code false} otherwise.
     * @see     #setDaemon(boolean)
     */
    public final boolean isDaemon() {
        if (isVirtual()) {
            return true;
        } else {
            return holder.daemon;
        }
    }

    /**
     * Does nothing.
     *
     * @deprecated This method originally determined if the currently running
     * thread had permission to modify this thread. This method was only useful
     * in conjunction with {@linkplain SecurityManager the Security Manager},
     * which is no longer supported. There is no replacement for the Security
     * Manager or this method.
     */
    @Deprecated(since="17", forRemoval=true)
    public final void checkAccess() { }

    /**
     * Returns a string representation of this thread. The string representation
     * will usually include the thread's {@linkplain #threadId() identifier} and
     * name. The default implementation for platform threads includes the thread's
     * identifier, name, priority, and the name of the thread group.
     *
     * @return  a string representation of this thread.
     */
    public String toString() {
        StringBuilder sb = new StringBuilder("Thread[#");
        sb.append(threadId());
        sb.append(",");
        sb.append(getName());
        sb.append(",");
        sb.append(getPriority());
        sb.append(",");
        ThreadGroup group = getThreadGroup();
        if (group != null)
            sb.append(group.getName());
        sb.append("]");
        return sb.toString();
    }

    /**
     * Returns the context {@code ClassLoader} for this thread.
     * The context {@code ClassLoader} may be set by the creator of the thread
     * for use by code running in this thread when loading classes and resources.
     * If not {@linkplain #setContextClassLoader set}, the default is to inherit
     * the context class loader from the parent thread.
     *
     * <p> The context {@code ClassLoader} of the primordial thread is typically
     * set to the class loader used to load the application.
     *
     * @return  the context {@code ClassLoader} for this thread, or {@code null}
     *          indicating the system class loader (or, failing that, the
     *          bootstrap class loader)
     *
     * @since 1.2
     */
    public ClassLoader getContextClassLoader() {
        return contextClassLoader;
    }

    /**
     * Sets the context {@code ClassLoader} for this thread.
     *
     * <p> The context {@code ClassLoader} may be set by the creator of the thread
     * for use by code running in this thread when loading classes and resources.
     *
     * @param  cl
     *         the context ClassLoader for this Thread, or null  indicating the
     *         system class loader (or, failing that, the bootstrap class loader)
     *
     * @since 1.2
     */
    public void setContextClassLoader(ClassLoader cl) {
        contextClassLoader = cl;
    }

    /**
     * Returns {@code true} if and only if the current thread holds the
     * monitor lock on the specified object.
     *
     * <p>This method is designed to allow a program to assert that
     * the current thread already holds a specified lock:
     * <pre>
     *     assert Thread.holdsLock(obj);
     * </pre>
     *
     * @param  obj the object on which to test lock ownership
     * @return {@code true} if the current thread holds the monitor lock on
     *         the specified object.
     * @since 1.4
     */
    public static native boolean holdsLock(Object obj);

    private static final StackTraceElement[] EMPTY_STACK_TRACE
        = new StackTraceElement[0];

    /**
     * Returns an array of stack trace elements representing the stack dump
     * of this thread.  This method will return a zero-length array if
     * this thread has not started, has started but has not yet been
     * scheduled to run by the system, or has terminated.
     * If the returned array is of non-zero length then the first element of
     * the array represents the top of the stack, which is the most recent
     * method invocation in the sequence.  The last element of the array
     * represents the bottom of the stack, which is the least recent method
     * invocation in the sequence.
     *
     * <p>Some virtual machines may, under some circumstances, omit one
     * or more stack frames from the stack trace.  In the extreme case,
     * a virtual machine that has no stack trace information concerning
     * this thread is permitted to return a zero-length array from this
     * method.
     *
     * @return an array of {@code StackTraceElement},
     * each represents one stack frame.
     *
     * @see Throwable#getStackTrace
     * @since 1.5
     */
    public StackTraceElement[] getStackTrace() {
        if (Thread.currentThread() != this) {
            // optimization so we do not call into the vm for threads that
            // have not yet started or have terminated
            if (!isAlive()) {
                return EMPTY_STACK_TRACE;
            }
            StackTraceElement[] stackTrace = getStackTrace0();
            if (stackTrace != null) {
                return StackTraceElement.finishInit(stackTrace);
            }
            return EMPTY_STACK_TRACE;
        } else {
            return (new Exception()).getStackTrace();
        }
    }

    private native StackTraceElement[] getStackTrace0();

    /**
     * Returns a map of stack traces for all live platform threads. The map
     * does not include virtual threads.
     * The map keys are threads and each map value is an array of
     * {@code StackTraceElement} that represents the stack dump
     * of the corresponding {@code Thread}.
     * The returned stack traces are in the format specified for
     * the {@link #getStackTrace getStackTrace} method.
     *
     * <p>The threads may be executing while this method is called.
     * The stack trace of each thread only represents a snapshot and
     * each stack trace may be obtained at different time.  A zero-length
     * array will be returned in the map value if the virtual machine has
     * no stack trace information about a thread.
     *
     * @return a {@code Map} from {@code Thread} to an array of
     * {@code StackTraceElement} that represents the stack trace of
     * the corresponding thread.
     *
     * @see #getStackTrace
     * @see Throwable#getStackTrace
     *
     * @since 1.5
     */
    public static Map<Thread, StackTraceElement[]> getAllStackTraces() {
        // Get a snapshot of the list of all threads
        Thread[] threads = getThreads();
        StackTraceElement[][] traces = dumpThreads(threads);
        Map<Thread, StackTraceElement[]> m = HashMap.newHashMap(threads.length);
        for (int i = 0; i < threads.length; i++) {
            StackTraceElement[] stackTrace = traces[i];
            if (stackTrace != null) {
                m.put(threads[i], stackTrace);
            }
            // else terminated so we don't put it in the map
        }
        return m;
    }

    /**
     * Return an array of all live threads.
     */
    static Thread[] getAllThreads() {
        return getThreads();
    }

    private static native StackTraceElement[][] dumpThreads(Thread[] threads);
    private static native Thread[] getThreads();

    /**
     * Returns the identifier of this Thread.  The thread ID is a positive
     * {@code long} number generated when this thread was created.
     * The thread ID is unique and remains unchanged during its lifetime.
     *
     * @return this thread's ID
     *
     * @deprecated This method is not final and may be overridden to return a
     * value that is not the thread ID. Use {@link #threadId()} instead.
     *
     * @since 1.5
     */
    @Deprecated(since="19")
    public long getId() {
        return threadId();
    }

    /**
     * Returns the identifier of this Thread.  The thread ID is a positive
     * {@code long} number generated when this thread was created.
     * The thread ID is unique and remains unchanged during its lifetime.
     *
     * @return this thread's ID
     * @since 19
     */
    public final long threadId() {
        return tid;
    }

    /**
     * A thread state.  A thread can be in one of the following states:
     * <ul>
     * <li>{@link #NEW}<br>
     *     A thread that has not yet started is in this state.
     *     </li>
     * <li>{@link #RUNNABLE}<br>
     *     A thread executing in the Java virtual machine is in this state.
     *     </li>
     * <li>{@link #BLOCKED}<br>
     *     A thread that is blocked waiting for a monitor lock
     *     is in this state.
     *     </li>
     * <li>{@link #WAITING}<br>
     *     A thread that is waiting indefinitely for another thread to
     *     perform a particular action is in this state.
     *     </li>
     * <li>{@link #TIMED_WAITING}<br>
     *     A thread that is waiting for another thread to perform an action
     *     for up to a specified waiting time is in this state.
     *     </li>
     * <li>{@link #TERMINATED}<br>
     *     A thread that has exited is in this state.
     *     </li>
     * </ul>
     *
     * <p>
     * A thread can be in only one state at a given point in time.
     * These states are virtual machine states which do not reflect
     * any operating system thread states.
     *
     * @since   1.5
     * @see #getState
     */
    public enum State {
        /**
         * Thread state for a thread which has not yet started.
         */
        NEW,

        /**
         * Thread state for a runnable thread.  A thread in the runnable
         * state is executing in the Java virtual machine but it may
         * be waiting for other resources from the operating system
         * such as processor.
         */
        RUNNABLE,

        /**
         * Thread state for a thread blocked waiting for a monitor lock.
         * A thread in the blocked state is waiting for a monitor lock
         * to enter a synchronized block/method or
         * reenter a synchronized block/method after calling
         * {@link Object#wait() Object.wait}.
         */
        BLOCKED,

        /**
         * Thread state for a waiting thread.
         * A thread is in the waiting state due to calling one of the
         * following methods:
         * <ul>
         *   <li>{@link Object#wait() Object.wait} with no timeout</li>
         *   <li>{@link #join() Thread.join} with no timeout</li>
         *   <li>{@link LockSupport#park() LockSupport.park}</li>
         * </ul>
         *
         * <p>A thread in the waiting state is waiting for another thread to
         * perform a particular action.
         *
         * For example, a thread that has called {@code Object.wait()}
         * on an object is waiting for another thread to call
         * {@code Object.notify()} or {@code Object.notifyAll()} on
         * that object. A thread that has called {@code Thread.join()}
         * is waiting for a specified thread to terminate.
         */
        WAITING,

        /**
         * Thread state for a waiting thread with a specified waiting time.
         * A thread is in the timed waiting state due to calling one of
         * the following methods with a specified positive waiting time:
         * <ul>
         *   <li>{@link #sleep Thread.sleep}</li>
         *   <li>{@link Object#wait(long) Object.wait} with timeout</li>
         *   <li>{@link #join(long) Thread.join} with timeout</li>
         *   <li>{@link LockSupport#parkNanos LockSupport.parkNanos}</li>
         *   <li>{@link LockSupport#parkUntil LockSupport.parkUntil}</li>
         * </ul>
         */
        TIMED_WAITING,

        /**
         * Thread state for a terminated thread.
         * The thread has completed execution.
         */
        TERMINATED;
    }

    /**
     * Returns the state of this thread.
     * This method is designed for use in monitoring of the system state,
     * not for synchronization control.
     *
     * @return this thread's state.
     * @since 1.5
     */
    public State getState() {
        return threadState();
    }

    /**
     * Returns the state of this thread.
     * This method can be used instead of getState as getState is not final and
     * so can be overridden to run arbitrary code.
     */
    State threadState() {
        return jdk.internal.misc.VM.toThreadState(holder.threadStatus);
    }

    /**
     * Returns true if the thread has terminated.
     */
    boolean isTerminated() {
        return threadState() == State.TERMINATED;
    }

    /**
     * Interface for handlers invoked when a {@code Thread} abruptly
     * terminates due to an uncaught exception.
     * <p>When a thread is about to terminate due to an uncaught exception
     * the Java Virtual Machine will query the thread for its
     * {@code UncaughtExceptionHandler} using
     * {@link #getUncaughtExceptionHandler} and will invoke the handler's
     * {@code uncaughtException} method, passing the thread and the
     * exception as arguments.
     * If a thread has not had its {@code UncaughtExceptionHandler}
     * explicitly set, then its {@code ThreadGroup} object acts as its
     * {@code UncaughtExceptionHandler}. If the {@code ThreadGroup} object
     * has no
     * special requirements for dealing with the exception, it can forward
     * the invocation to the {@linkplain #getDefaultUncaughtExceptionHandler
     * default uncaught exception handler}.
     *
     * @see #setDefaultUncaughtExceptionHandler
     * @see #setUncaughtExceptionHandler
     * @see ThreadGroup#uncaughtException
     * @since 1.5
     */
    @FunctionalInterface
    public interface UncaughtExceptionHandler {
        /**
         * Method invoked when the given thread terminates due to the
         * given uncaught exception.
         * <p>Any exception thrown by this method will be ignored by the
         * Java Virtual Machine.
         * @param t the thread
         * @param e the exception
         */
        void uncaughtException(Thread t, Throwable e);
    }

    // null unless explicitly set
    private volatile UncaughtExceptionHandler uncaughtExceptionHandler;

    // null unless explicitly set
    private static volatile UncaughtExceptionHandler defaultUncaughtExceptionHandler;

    /**
     * Set the default handler invoked when a thread abruptly terminates
     * due to an uncaught exception, and no other handler has been defined
     * for that thread.
     *
     * <p>Uncaught exception handling is controlled first by the thread, then
     * by the thread's {@link ThreadGroup} object and finally by the default
     * uncaught exception handler. If the thread does not have an explicit
     * uncaught exception handler set, and the thread's thread group
     * (including parent thread groups)  does not specialize its
     * {@code uncaughtException} method, then the default handler's
     * {@code uncaughtException} method will be invoked.
     * <p>By setting the default uncaught exception handler, an application
     * can change the way in which uncaught exceptions are handled (such as
     * logging to a specific device, or file) for those threads that would
     * already accept whatever &quot;default&quot; behavior the system
     * provided.
     *
     * <p>Note that the default uncaught exception handler should not usually
     * defer to the thread's {@code ThreadGroup} object, as that could cause
     * infinite recursion.
     *
     * @param ueh the object to use as the default uncaught exception handler.
     * If {@code null} then there is no default handler.
     *
     * @see #setUncaughtExceptionHandler
     * @see #getUncaughtExceptionHandler
     * @see ThreadGroup#uncaughtException
     * @since 1.5
     */
    public static void setDefaultUncaughtExceptionHandler(UncaughtExceptionHandler ueh) {
        defaultUncaughtExceptionHandler = ueh;
    }

    /**
     * Returns the default handler invoked when a thread abruptly terminates
     * due to an uncaught exception. If the returned value is {@code null},
     * there is no default.
     * @since 1.5
     * @see #setDefaultUncaughtExceptionHandler
     * @return the default uncaught exception handler for all threads
     */
    public static UncaughtExceptionHandler getDefaultUncaughtExceptionHandler(){
        return defaultUncaughtExceptionHandler;
    }

    /**
     * Returns the handler invoked when this thread abruptly terminates
     * due to an uncaught exception. If this thread has not had an
     * uncaught exception handler explicitly set then this thread's
     * {@code ThreadGroup} object is returned, unless this thread
     * has terminated, in which case {@code null} is returned.
     * @since 1.5
     * @return the uncaught exception handler for this thread
     */
    public UncaughtExceptionHandler getUncaughtExceptionHandler() {
        if (isTerminated()) {
            // uncaughtExceptionHandler may be set to null after thread terminates
            return null;
        } else {
            UncaughtExceptionHandler ueh = uncaughtExceptionHandler;
            return (ueh != null) ? ueh : getThreadGroup();
        }
    }

    /**
     * Set the handler invoked when this thread abruptly terminates
     * due to an uncaught exception.
     * <p>A thread can take full control of how it responds to uncaught
     * exceptions by having its uncaught exception handler explicitly set.
     * If no such handler is set then the thread's {@code ThreadGroup}
     * object acts as its handler.
     * @param ueh the object to use as this thread's uncaught exception
     * handler. If {@code null} then this thread has no explicit handler.
     * @see #setDefaultUncaughtExceptionHandler
     * @see ThreadGroup#uncaughtException
     * @since 1.5
     */
    public void setUncaughtExceptionHandler(UncaughtExceptionHandler ueh) {
        uncaughtExceptionHandler(ueh);
    }

    void uncaughtExceptionHandler(UncaughtExceptionHandler ueh) {
        uncaughtExceptionHandler = ueh;
    }

    /**
     * Dispatch an uncaught exception to the handler. This method is
     * called when a thread terminates with an exception.
     */
    void dispatchUncaughtException(Throwable e) {
        getUncaughtExceptionHandler().uncaughtException(this, e);
    }

    /**
     * Holder class for constants.
     */
    private static class Constants {
        // Thread group for virtual threads.
        static final ThreadGroup VTHREAD_GROUP;

        static {
            ThreadGroup root = Thread.currentCarrierThread().getThreadGroup();
            for (ThreadGroup p; (p = root.getParent()) != null; ) {
                root = p;
            }
            VTHREAD_GROUP = new ThreadGroup(root, "VirtualThreads", MAX_PRIORITY, false);
        }
    }

    /**
     * Returns the special ThreadGroup for virtual threads.
     */
    static ThreadGroup virtualThreadGroup() {
        return Constants.VTHREAD_GROUP;
    }

    // The following three initially uninitialized fields are exclusively
    // managed by class java.util.concurrent.ThreadLocalRandom. These
    // fields are used to build the high-performance PRNGs in the
    // concurrent code.

    /** The current seed for a ThreadLocalRandom */
    long threadLocalRandomSeed;

    /** Probe hash value; nonzero if threadLocalRandomSeed initialized */
    int threadLocalRandomProbe;

    /** Secondary seed isolated from public ThreadLocalRandom sequence */
    int threadLocalRandomSecondarySeed;

    /** The thread container that this thread is in */
    private @Stable ThreadContainer container;
    ThreadContainer threadContainer() {
        return container;
    }
    void setThreadContainer(ThreadContainer container) {
        // assert this.container == null;
        this.container = container;
    }

    /** The top of this stack of stackable scopes owned by this thread */
    private volatile StackableScope headStackableScopes;
    StackableScope headStackableScopes() {
        return headStackableScopes;
    }
    static void setHeadStackableScope(StackableScope scope) {
        currentThread().headStackableScopes = scope;
    }

    /* Some private helper methods */
    private native void setPriority0(int newPriority);
    private native void interrupt0();
    private static native void clearInterruptEvent();
    private native void setNativeName(String name);

    // The address of the next thread identifier, see ThreadIdentifiers.
    private static native long getNextThreadIdOffset();
}
