/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import jdk.internal.event.VirtualThreadEndEvent;
import jdk.internal.event.VirtualThreadPinnedEvent;
import jdk.internal.event.VirtualThreadStartEvent;
import jdk.internal.event.VirtualThreadSubmitFailedEvent;
import jdk.internal.misc.CarrierThread;
import jdk.internal.misc.InnocuousThread;
import jdk.internal.misc.Unsafe;
import jdk.internal.vm.Continuation;
import jdk.internal.vm.ContinuationScope;
import jdk.internal.vm.StackableScope;
import jdk.internal.vm.ThreadContainer;
import jdk.internal.vm.ThreadContainers;
import jdk.internal.vm.annotation.ChangesCurrentThread;
import jdk.internal.vm.annotation.Hidden;
import jdk.internal.vm.annotation.IntrinsicCandidate;
import jdk.internal.vm.annotation.JvmtiMountTransition;
import jdk.internal.vm.annotation.ReservedStackAccess;
import sun.nio.ch.Interruptible;
import sun.security.action.GetPropertyAction;
import static java.util.concurrent.TimeUnit.*;

/**
 * A thread that is scheduled by the Java virtual machine rather than the operating system.
 */
final class VirtualThread extends BaseVirtualThread {
    private static final Unsafe U = Unsafe.getUnsafe();
    private static final ContinuationScope VTHREAD_SCOPE = new ContinuationScope("VirtualThreads");
    private static final ForkJoinPool DEFAULT_SCHEDULER = createDefaultScheduler();
    private static final ScheduledExecutorService[] DELAYED_TASK_SCHEDULERS = createDelayedTaskSchedulers();
    private static final int TRACE_PINNING_MODE = tracePinningMode();

    private static final long STATE = U.objectFieldOffset(VirtualThread.class, "state");
    private static final long PARK_PERMIT = U.objectFieldOffset(VirtualThread.class, "parkPermit");
    private static final long CARRIER_THREAD = U.objectFieldOffset(VirtualThread.class, "carrierThread");
    private static final long TERMINATION = U.objectFieldOffset(VirtualThread.class, "termination");

    // scheduler and continuation
    private final Executor scheduler;
    private final Continuation cont;
    private final Runnable runContinuation;

    // virtual thread state, accessed by VM
    private volatile int state;

    /*
     * Virtual thread state transitions:
     *
     *      NEW -> STARTED         // Thread.start, schedule to run
     *  STARTED -> TERMINATED      // failed to start
     *  STARTED -> RUNNING         // first run
     *  RUNNING -> TERMINATED      // done
     *
     *  RUNNING -> PARKING         // Thread parking with LockSupport.park
     *  PARKING -> PARKED          // cont.yield successful, parked indefinitely
     *  PARKING -> PINNED          // cont.yield failed, parked indefinitely on carrier
     *   PARKED -> UNPARKED        // unparked, may be scheduled to continue
     *   PINNED -> RUNNING         // unparked, continue execution on same carrier
     * UNPARKED -> RUNNING         // continue execution after park
     *
     *       RUNNING -> TIMED_PARKING   // Thread parking with LockSupport.parkNanos
     * TIMED_PARKING -> TIMED_PARKED    // cont.yield successful, timed-parked
     * TIMED_PARKING -> TIMED_PINNED    // cont.yield failed, timed-parked on carrier
     *  TIMED_PARKED -> UNPARKED        // unparked, may be scheduled to continue
     *  TIMED_PINNED -> RUNNING         // unparked, continue execution on same carrier
     *
     *  RUNNING -> YIELDING        // Thread.yield
     * YIELDING -> YIELDED         // cont.yield successful, may be scheduled to continue
     * YIELDING -> RUNNING         // cont.yield failed
     *  YIELDED -> RUNNING         // continue execution after Thread.yield
     */
    private static final int NEW      = 0;
    private static final int STARTED  = 1;
    private static final int RUNNING  = 2;     // runnable-mounted

    // untimed and timed parking
    private static final int PARKING       = 3;
    private static final int PARKED        = 4;     // unmounted
    private static final int PINNED        = 5;     // mounted
    private static final int TIMED_PARKING = 6;
    private static final int TIMED_PARKED  = 7;     // unmounted
    private static final int TIMED_PINNED  = 8;     // mounted
    private static final int UNPARKED      = 9;     // unmounted but runnable

    // Thread.yield
    private static final int YIELDING = 10;
    private static final int YIELDED  = 11;         // unmounted but runnable

    private static final int TERMINATED = 99;  // final state

    // can be suspended from scheduling when unmounted
    private static final int SUSPENDED = 1 << 8;

    // parking permit
    private volatile boolean parkPermit;

    // carrier thread when mounted, accessed by VM
    private volatile Thread carrierThread;

    // termination object when joining, created lazily if needed
    private volatile CountDownLatch termination;

    /**
     * Returns the continuation scope used for virtual threads.
     */
    static ContinuationScope continuationScope() {
        return VTHREAD_SCOPE;
    }

    /**
     * Creates a new {@code VirtualThread} to run the given task with the given
     * scheduler. If the given scheduler is {@code null} and the current thread
     * is a platform thread then the newly created virtual thread will use the
     * default scheduler. If given scheduler is {@code null} and the current
     * thread is a virtual thread then the current thread's scheduler is used.
     *
     * @param scheduler the scheduler or null
     * @param name thread name
     * @param characteristics characteristics
     * @param task the task to execute
     */
    VirtualThread(Executor scheduler, String name, int characteristics, Runnable task) {
        super(name, characteristics, /*bound*/ false);
        Objects.requireNonNull(task);

        // choose scheduler if not specified
        if (scheduler == null) {
            Thread parent = Thread.currentThread();
            if (parent instanceof VirtualThread vparent) {
                scheduler = vparent.scheduler;
            } else {
                scheduler = DEFAULT_SCHEDULER;
            }
        }

        this.scheduler = scheduler;
        this.cont = new VThreadContinuation(this, task);
        this.runContinuation = this::runContinuation;
    }

    /**
     * The continuation that a virtual thread executes.
     */
    private static class VThreadContinuation extends Continuation {
        VThreadContinuation(VirtualThread vthread, Runnable task) {
            super(VTHREAD_SCOPE, wrap(vthread, task));
        }
        @Override
        protected void onPinned(Continuation.Pinned reason) {
            if (TRACE_PINNING_MODE > 0) {
                boolean printAll = (TRACE_PINNING_MODE == 1);
                VirtualThread vthread = (VirtualThread) Thread.currentThread();
                int oldState = vthread.state();
                try {
                    // avoid printing when in transition states
                    vthread.setState(RUNNING);
                    PinnedThreadPrinter.printStackTrace(System.out, reason, printAll);
                } finally {
                    vthread.setState(oldState);
                }
            }
        }
        private static Runnable wrap(VirtualThread vthread, Runnable task) {
            return new Runnable() {
                @Hidden
                public void run() {
                    vthread.run(task);
                }
            };
        }
    }

    /**
     * Runs or continues execution on the current thread. The virtual thread is mounted
     * on the current thread before the task runs or continues. It unmounts when the
     * task completes or yields.
     */
    @ChangesCurrentThread // allow mount/unmount to be inlined
    private void runContinuation() {
        // the carrier must be a platform thread
        if (Thread.currentThread().isVirtual()) {
            throw new WrongThreadException();
        }

        // set state to RUNNING
        int initialState = state();
        if (initialState == STARTED || initialState == UNPARKED || initialState == YIELDED) {
            // newly started or continue after parking/blocking/Thread.yield
            if (!compareAndSetState(initialState, RUNNING)) {
                return;
            }
            // consume parking permit when continuing after parking
            if (initialState == UNPARKED) {
                setParkPermit(false);
            }
        } else {
            // not runnable
            return;
        }

        mount();
        try {
            cont.run();
        } finally {
            unmount();
            if (cont.isDone()) {
                afterDone();
            } else {
                afterYield();
            }
        }
    }

    /**
     * Submits the runContinuation task to the scheduler. For the default scheduler,
     * and calling it on a worker thread, the task will be pushed to the local queue,
     * otherwise it will be pushed to an external submission queue.
     * @param scheduler the scheduler
     * @param retryOnOOME true to retry indefinitely if OutOfMemoryError is thrown
     * @throws RejectedExecutionException
     */
    @ChangesCurrentThread
    private void submitRunContinuation(Executor scheduler, boolean retryOnOOME) {
        boolean done = false;
        while (!done) {
            try {
                // The scheduler's execute method is invoked in the context of the
                // carrier thread. For the default scheduler this ensures that the
                // current thread is a ForkJoinWorkerThread so the task will be pushed
                // to the local queue. For other schedulers, it avoids deadlock that
                // would arise due to platform and virtual threads contending for a
                // lock on the scheduler's submission queue.
                if (currentThread() instanceof VirtualThread vthread) {
                    vthread.switchToCarrierThread();
                    try {
                        scheduler.execute(runContinuation);
                    } finally {
                        switchToVirtualThread(vthread);
                    }
                } else {
                    scheduler.execute(runContinuation);
                }
                done = true;
            } catch (RejectedExecutionException ree) {
                submitFailed(ree);
                throw ree;
            } catch (OutOfMemoryError e) {
                if (retryOnOOME) {
                    U.park(false, 100_000_000); // 100ms
                } else {
                    throw e;
                }
            }
        }
    }

    /**
     * Submits the runContinuation task to given scheduler with a lazy submit.
     * If OutOfMemoryError is thrown then the submit will be retried until it succeeds.
     * @throws RejectedExecutionException
     * @see ForkJoinPool#lazySubmit(ForkJoinTask)
     */
    private void lazySubmitRunContinuation(ForkJoinPool pool) {
        assert Thread.currentThread() instanceof CarrierThread;
        try {
            pool.lazySubmit(ForkJoinTask.adapt(runContinuation));
        } catch (RejectedExecutionException ree) {
            submitFailed(ree);
            throw ree;
        } catch (OutOfMemoryError e) {
            submitRunContinuation(pool, true);
        }
    }

    /**
     * Submits the runContinuation task to the given scheduler as an external submit.
     * If OutOfMemoryError is thrown then the submit will be retried until it succeeds.
     * @throws RejectedExecutionException
     * @see ForkJoinPool#externalSubmit(ForkJoinTask)
     */
    private void externalSubmitRunContinuation(ForkJoinPool pool) {
        assert Thread.currentThread() instanceof CarrierThread;
        try {
            pool.externalSubmit(ForkJoinTask.adapt(runContinuation));
        } catch (RejectedExecutionException ree) {
            submitFailed(ree);
            throw ree;
        } catch (OutOfMemoryError e) {
            submitRunContinuation(pool, true);
        }
    }

    /**
     * Submits the runContinuation task to the scheduler. For the default scheduler,
     * and calling it on a worker thread, the task will be pushed to the local queue,
     * otherwise it will be pushed to an external submission queue.
     * If OutOfMemoryError is thrown then the submit will be retried until it succeeds.
     * @throws RejectedExecutionException
     */
    private void submitRunContinuation() {
        submitRunContinuation(scheduler, true);
    }

    /**
     * Submits the runContinuation task to the scheduler. For the default scheduler, and
     * calling it a virtual thread that uses the default scheduler, the task will be
     * pushed to an external submission queue. This method may throw OutOfMemoryError.
     * @throws RejectedExecutionException
     * @throws OutOfMemoryError
     */
    private void externalSubmitRunContinuationOrThrow() {
        if (scheduler == DEFAULT_SCHEDULER && currentCarrierThread() instanceof CarrierThread ct) {
            try {
                ct.getPool().externalSubmit(ForkJoinTask.adapt(runContinuation));
            } catch (RejectedExecutionException ree) {
                submitFailed(ree);
                throw ree;
            }
        } else {
            submitRunContinuation(scheduler, false);
        }
    }

    /**
     * If enabled, emits a JFR VirtualThreadSubmitFailedEvent.
     */
    private void submitFailed(RejectedExecutionException ree) {
        var event = new VirtualThreadSubmitFailedEvent();
        if (event.isEnabled()) {
            event.javaThreadId = threadId();
            event.exceptionMessage = ree.getMessage();
            event.commit();
        }
    }

    /**
     * Runs a task in the context of this virtual thread.
     */
    private void run(Runnable task) {
        assert Thread.currentThread() == this && state == RUNNING;

        // notify JVMTI, may post VirtualThreadStart event
        notifyJvmtiStart();

        // emit JFR event if enabled
        if (VirtualThreadStartEvent.isTurnedOn()) {
            var event = new VirtualThreadStartEvent();
            event.javaThreadId = threadId();
            event.commit();
        }

        Object bindings = Thread.scopedValueBindings();
        try {
            runWith(bindings, task);
        } catch (Throwable exc) {
            dispatchUncaughtException(exc);
        } finally {
            try {
                // pop any remaining scopes from the stack, this may block
                StackableScope.popAll();

                // emit JFR event if enabled
                if (VirtualThreadEndEvent.isTurnedOn()) {
                    var event = new VirtualThreadEndEvent();
                    event.javaThreadId = threadId();
                    event.commit();
                }

            } finally {
                // notify JVMTI, may post VirtualThreadEnd event
                notifyJvmtiEnd();
            }
        }
    }

    /**
     * Mounts this virtual thread onto the current platform thread. On
     * return, the current thread is the virtual thread.
     */
    @ChangesCurrentThread
    @ReservedStackAccess
    private void mount() {
        // notify JVMTI before mount
        notifyJvmtiMount(/*hide*/true);

        // sets the carrier thread
        Thread carrier = Thread.currentCarrierThread();
        setCarrierThread(carrier);

        // sync up carrier thread interrupt status if needed
        if (interrupted) {
            carrier.setInterrupt();
        } else if (carrier.isInterrupted()) {
            synchronized (interruptLock) {
                // need to recheck interrupt status
                if (!interrupted) {
                    carrier.clearInterrupt();
                }
            }
        }

        // set Thread.currentThread() to return this virtual thread
        carrier.setCurrentThread(this);
    }

    /**
     * Unmounts this virtual thread from the carrier. On return, the
     * current thread is the current platform thread.
     */
    @ChangesCurrentThread
    @ReservedStackAccess
    private void unmount() {
        assert !Thread.holdsLock(interruptLock);

        // set Thread.currentThread() to return the platform thread
        Thread carrier = this.carrierThread;
        carrier.setCurrentThread(carrier);

        // break connection to carrier thread, synchronized with interrupt
        synchronized (interruptLock) {
            setCarrierThread(null);
        }
        carrier.clearInterrupt();

        // notify JVMTI after unmount
        notifyJvmtiUnmount(/*hide*/false);
    }

    /**
     * Sets the current thread to the current carrier thread.
     */
    @ChangesCurrentThread
    @JvmtiMountTransition
    private void switchToCarrierThread() {
        notifyJvmtiHideFrames(true);
        Thread carrier = this.carrierThread;
        assert Thread.currentThread() == this
                && carrier == Thread.currentCarrierThread();
        carrier.setCurrentThread(carrier);
    }

    /**
     * Sets the current thread to the given virtual thread.
     */
    @ChangesCurrentThread
    @JvmtiMountTransition
    private static void switchToVirtualThread(VirtualThread vthread) {
        Thread carrier = vthread.carrierThread;
        assert carrier == Thread.currentCarrierThread();
        carrier.setCurrentThread(vthread);
        notifyJvmtiHideFrames(false);
    }

    /**
     * Executes the given value returning task on the current carrier thread.
     */
    @ChangesCurrentThread
    <V> V executeOnCarrierThread(Callable<V> task) throws Exception {
        assert Thread.currentThread() == this;
        switchToCarrierThread();
        try {
            return task.call();
        } finally {
            switchToVirtualThread(this);
        }
     }

    /**
     * Invokes Continuation.yield, notifying JVMTI (if enabled) to hide frames until
     * the continuation continues.
     */
    @Hidden
    private boolean yieldContinuation() {
        notifyJvmtiUnmount(/*hide*/true);
        try {
            return Continuation.yield(VTHREAD_SCOPE);
        } finally {
            notifyJvmtiMount(/*hide*/false);
        }
    }

    /**
     * Invoked after the continuation yields. If parking then it sets the state
     * and also re-submits the task to continue if unparked while parking.
     * If yielding due to Thread.yield then it just submits the task to continue.
     */
    private void afterYield() {
        assert carrierThread == null;

        // re-adjust parallelism if the virtual thread yielded when compensating
        if (currentThread() instanceof CarrierThread ct) {
            ct.endBlocking();
        }

        int s = state();

        // LockSupport.park/parkNanos
        if (s == PARKING || s == TIMED_PARKING) {
            int newState = (s == PARKING) ? PARKED : TIMED_PARKED;
            setState(newState);

            // may have been unparked while parking
            if (parkPermit && compareAndSetState(newState, UNPARKED)) {
                // lazy submit to continue on the current carrier if possible
                if (currentThread() instanceof CarrierThread ct && ct.getQueuedTaskCount() == 0) {
                    lazySubmitRunContinuation(ct.getPool());
                } else {
                    submitRunContinuation();
                }
            }
            return;
        }

        // Thread.yield
        if (s == YIELDING) {
            setState(YIELDED);

            // external submit if there are no tasks in the local task queue
            if (currentThread() instanceof CarrierThread ct && ct.getQueuedTaskCount() == 0) {
                externalSubmitRunContinuation(ct.getPool());
            } else {
                submitRunContinuation();
            }
            return;
        }

        assert false;
    }

    /**
     * Invoked after the continuation completes.
     */
    private void afterDone() {
        afterDone(true);
    }

    /**
     * Invoked after the continuation completes (or start failed). Sets the thread
     * state to TERMINATED and notifies anyone waiting for the thread to terminate.
     *
     * @param notifyContainer true if its container should be notified
     */
    private void afterDone(boolean notifyContainer) {
        assert carrierThread == null;
        setState(TERMINATED);

        // notify anyone waiting for this virtual thread to terminate
        CountDownLatch termination = this.termination;
        if (termination != null) {
            assert termination.getCount() == 1;
            termination.countDown();
        }

        // notify container
        if (notifyContainer) {
            threadContainer().onExit(this);
        }

        // clear references to thread locals
        clearReferences();
    }

    /**
     * Schedules this {@code VirtualThread} to execute.
     *
     * @throws IllegalStateException if the container is shutdown or closed
     * @throws IllegalThreadStateException if the thread has already been started
     * @throws RejectedExecutionException if the scheduler cannot accept a task
     */
    @Override
    void start(ThreadContainer container) {
        if (!compareAndSetState(NEW, STARTED)) {
            throw new IllegalThreadStateException("Already started");
        }

        // bind thread to container
        assert threadContainer() == null;
        setThreadContainer(container);

        // start thread
        boolean addedToContainer = false;
        boolean started = false;
        try {
            container.onStart(this);  // may throw
            addedToContainer = true;

            // scoped values may be inherited
            inheritScopedValueBindings(container);

            // submit task to run thread, using externalSubmit if possible
            externalSubmitRunContinuationOrThrow();
            started = true;
        } finally {
            if (!started) {
                afterDone(addedToContainer);
            }
        }
    }

    @Override
    public void start() {
        start(ThreadContainers.root());
    }

    @Override
    public void run() {
        // do nothing
    }

    /**
     * Parks until unparked or interrupted. If already unparked then the parking
     * permit is consumed and this method completes immediately (meaning it doesn't
     * yield). It also completes immediately if the interrupt status is set.
     */
    @Override
    void park() {
        assert Thread.currentThread() == this;

        // complete immediately if parking permit available or interrupted
        if (getAndSetParkPermit(false) || interrupted)
            return;

        // park the thread
        boolean yielded = false;
        setState(PARKING);
        try {
            yielded = yieldContinuation();  // may throw
        } finally {
            assert (Thread.currentThread() == this) && (yielded == (state() == RUNNING));
            if (!yielded) {
                assert state() == PARKING;
                setState(RUNNING);
            }
        }

        // park on the carrier thread when pinned
        if (!yielded) {
            parkOnCarrierThread(false, 0);
        }
    }

    /**
     * Parks up to the given waiting time or until unparked or interrupted.
     * If already unparked then the parking permit is consumed and this method
     * completes immediately (meaning it doesn't yield). It also completes immediately
     * if the interrupt status is set or the waiting time is {@code <= 0}.
     *
     * @param nanos the maximum number of nanoseconds to wait.
     */
    @Override
    void parkNanos(long nanos) {
        assert Thread.currentThread() == this;

        // complete immediately if parking permit available or interrupted
        if (getAndSetParkPermit(false) || interrupted)
            return;

        // park the thread for the waiting time
        if (nanos > 0) {
            long startTime = System.nanoTime();

            boolean yielded = false;
            Future<?> unparker = scheduleUnpark(nanos);  // may throw OOME
            setState(TIMED_PARKING);
            try {
                yielded = yieldContinuation();  // may throw
            } finally {
                assert (Thread.currentThread() == this) && (yielded == (state() == RUNNING));
                if (!yielded) {
                    assert state() == TIMED_PARKING;
                    setState(RUNNING);
                }
                cancel(unparker);
            }

            // park on carrier thread for remaining time when pinned
            if (!yielded) {
                long remainingNanos = nanos - (System.nanoTime() - startTime);
                parkOnCarrierThread(true, remainingNanos);
            }
        }
    }

    /**
     * Parks the current carrier thread up to the given waiting time or until
     * unparked or interrupted. If the virtual thread is interrupted then the
     * interrupt status will be propagated to the carrier thread.
     * @param timed true for a timed park, false for untimed
     * @param nanos the waiting time in nanoseconds
     */
    private void parkOnCarrierThread(boolean timed, long nanos) {
        assert state() == RUNNING;

        VirtualThreadPinnedEvent event;
        try {
            event = new VirtualThreadPinnedEvent();
            event.begin();
        } catch (OutOfMemoryError e) {
            event = null;
        }

        setState(timed ? TIMED_PINNED : PINNED);
        try {
            if (!parkPermit) {
                if (!timed) {
                    U.park(false, 0);
                } else if (nanos > 0) {
                    U.park(false, nanos);
                }
            }
        } finally {
            setState(RUNNING);
        }

        // consume parking permit
        setParkPermit(false);

        if (event != null) {
            try {
                event.commit();
            } catch (OutOfMemoryError e) {
                // ignore
            }
        }
    }

    /**
     * Schedule this virtual thread to be unparked after a given delay.
     */
    @ChangesCurrentThread
    private Future<?> scheduleUnpark(long nanos) {
        assert Thread.currentThread() == this;
        // need to switch to current carrier thread to avoid nested parking
        switchToCarrierThread();
        try {
            return schedule(this::unpark, nanos, NANOSECONDS);
        } finally {
            switchToVirtualThread(this);
        }
    }

    /**
     * Cancels a task if it has not completed.
     */
    @ChangesCurrentThread
    private void cancel(Future<?> future) {
        assert Thread.currentThread() == this;
        if (!future.isDone()) {
            // need to switch to current carrier thread to avoid nested parking
            switchToCarrierThread();
            try {
                future.cancel(false);
            } finally {
                switchToVirtualThread(this);
            }
        }
    }

    /**
     * Re-enables this virtual thread for scheduling. If this virtual thread is parked
     * then its task is scheduled to continue, otherwise its next call to {@code park} or
     * {@linkplain #parkNanos(long) parkNanos} is guaranteed not to block.
     * @throws RejectedExecutionException if the scheduler cannot accept a task
     */
    @Override
    void unpark() {
        if (!getAndSetParkPermit(true) && currentThread() != this) {
            int s = state();

            // unparked while parked
            if ((s == PARKED || s == TIMED_PARKED) && compareAndSetState(s, UNPARKED)) {
                submitRunContinuation();
                return;
            }

            // unparked while parked when pinned
            if (s == PINNED || s == TIMED_PINNED) {
                // unpark carrier thread when pinned
                disableSuspendAndPreempt();
                try {
                    synchronized (carrierThreadAccessLock()) {
                        Thread carrier = carrierThread;
                        if (carrier != null && ((s = state()) == PINNED || s == TIMED_PINNED)) {
                            U.unpark(carrier);
                        }
                    }
                } finally {
                    enableSuspendAndPreempt();
                }
                return;
            }
        }
    }

    /**
     * Attempts to yield the current virtual thread (Thread.yield).
     */
    void tryYield() {
        assert Thread.currentThread() == this;
        setState(YIELDING);
        boolean yielded = false;
        try {
            yielded = yieldContinuation();  // may throw
        } finally {
            assert (Thread.currentThread() == this) && (yielded == (state() == RUNNING));
            if (!yielded) {
                assert state() == YIELDING;
                setState(RUNNING);
            }
        }
    }

    /**
     * Sleep the current thread for the given sleep time (in nanoseconds). If
     * nanos is 0 then the thread will attempt to yield.
     *
     * @implNote This implementation parks the thread for the given sleeping time
     * and will therefore be observed in PARKED state during the sleep. Parking
     * will consume the parking permit so this method makes available the parking
     * permit after the sleep. This may be observed as a spurious, but benign,
     * wakeup when the thread subsequently attempts to park.
     *
     * @param nanos the maximum number of nanoseconds to sleep
     * @throws InterruptedException if interrupted while sleeping
     */
    void sleepNanos(long nanos) throws InterruptedException {
        assert Thread.currentThread() == this && nanos >= 0;
        if (getAndClearInterrupt())
            throw new InterruptedException();
        if (nanos == 0) {
            tryYield();
        } else {
            // park for the sleep time
            try {
                long remainingNanos = nanos;
                long startNanos = System.nanoTime();
                while (remainingNanos > 0) {
                    parkNanos(remainingNanos);
                    if (getAndClearInterrupt()) {
                        throw new InterruptedException();
                    }
                    remainingNanos = nanos - (System.nanoTime() - startNanos);
                }
            } finally {
                // may have been unparked while sleeping
                setParkPermit(true);
            }
        }
    }

    /**
     * Waits up to {@code nanos} nanoseconds for this virtual thread to terminate.
     * A timeout of {@code 0} means to wait forever.
     *
     * @throws InterruptedException if interrupted while waiting
     * @return true if the thread has terminated
     */
    boolean joinNanos(long nanos) throws InterruptedException {
        if (state() == TERMINATED)
            return true;

        // ensure termination object exists, then re-check state
        CountDownLatch termination = getTermination();
        if (state() == TERMINATED)
            return true;

        // wait for virtual thread to terminate
        if (nanos == 0) {
            termination.await();
        } else {
            boolean terminated = termination.await(nanos, NANOSECONDS);
            if (!terminated) {
                // waiting time elapsed
                return false;
            }
        }
        assert state() == TERMINATED;
        return true;
    }

    @Override
    void blockedOn(Interruptible b) {
        disableSuspendAndPreempt();
        try {
            super.blockedOn(b);
        } finally {
            enableSuspendAndPreempt();
        }
    }

    @Override
    @SuppressWarnings("removal")
    public void interrupt() {
        if (Thread.currentThread() != this) {
            checkAccess();

            // if current thread is a virtual thread then prevent it from being
            // suspended or unmounted when entering or holding interruptLock
            Interruptible blocker;
            disableSuspendAndPreempt();
            try {
                synchronized (interruptLock) {
                    interrupted = true;
                    blocker = nioBlocker();
                    if (blocker != null) {
                        blocker.interrupt(this);
                    }

                    // interrupt carrier thread if mounted
                    Thread carrier = carrierThread;
                    if (carrier != null) carrier.setInterrupt();
                }
            } finally {
                enableSuspendAndPreempt();
            }

            // notify blocker after releasing interruptLock
            if (blocker != null) {
                blocker.postInterrupt();
            }

            // make available parking permit, unpark thread if parked
            unpark();

        } else {
            interrupted = true;
            carrierThread.setInterrupt();
            setParkPermit(true);
        }
    }

    @Override
    public boolean isInterrupted() {
        return interrupted;
    }

    @Override
    boolean getAndClearInterrupt() {
        assert Thread.currentThread() == this;
        boolean oldValue = interrupted;
        if (oldValue) {
            disableSuspendAndPreempt();
            try {
                synchronized (interruptLock) {
                    interrupted = false;
                    carrierThread.clearInterrupt();
                }
            } finally {
                enableSuspendAndPreempt();
            }
        }
        return oldValue;
    }

    @Override
    Thread.State threadState() {
        int s = state();
        switch (s & ~SUSPENDED) {
            case NEW:
                return Thread.State.NEW;
            case STARTED:
                // return NEW if thread container not yet set
                if (threadContainer() == null) {
                    return Thread.State.NEW;
                } else {
                    return Thread.State.RUNNABLE;
                }
            case UNPARKED:
            case YIELDED:
                // runnable, not mounted
                return Thread.State.RUNNABLE;
            case RUNNING:
                // if mounted then return state of carrier thread
                if (Thread.currentThread() != this) {
                    disableSuspendAndPreempt();
                    try {
                        synchronized (carrierThreadAccessLock()) {
                            Thread carrierThread = this.carrierThread;
                            if (carrierThread != null) {
                                return carrierThread.threadState();
                            }
                        }
                    } finally {
                        enableSuspendAndPreempt();
                    }
                }
                // runnable, mounted
                return Thread.State.RUNNABLE;
            case PARKING:
            case TIMED_PARKING:
            case YIELDING:
                // runnable, in transition
                return Thread.State.RUNNABLE;
            case PARKED:
            case PINNED:
                return State.WAITING;
            case TIMED_PARKED:
            case TIMED_PINNED:
                return State.TIMED_WAITING;
            case TERMINATED:
                return Thread.State.TERMINATED;
            default:
                throw new InternalError();
        }
    }

    @Override
    boolean alive() {
        int s = state;
        return (s != NEW && s != TERMINATED);
    }

    @Override
    boolean isTerminated() {
        return (state == TERMINATED);
    }

    @Override
    StackTraceElement[] asyncGetStackTrace() {
        StackTraceElement[] stackTrace;
        do {
            stackTrace = (carrierThread != null)
                    ? super.asyncGetStackTrace()  // mounted
                    : tryGetStackTrace();         // unmounted
            if (stackTrace == null) {
                Thread.yield();
            }
        } while (stackTrace == null);
        return stackTrace;
    }

    /**
     * Returns the stack trace for this virtual thread if it is unmounted.
     * Returns null if the thread is mounted or in transition.
     */
    private StackTraceElement[] tryGetStackTrace() {
        int initialState = state() & ~SUSPENDED;
        switch (initialState) {
            case NEW, STARTED, TERMINATED -> {
                return new StackTraceElement[0];  // unmounted, empty stack
            }
            case RUNNING, PINNED, TIMED_PINNED -> {
                return null;   // mounted
            }
            case PARKED, TIMED_PARKED -> {
                // unmounted, not runnable
            }
            case UNPARKED, YIELDED -> {
                // unmounted, runnable
            }
            case PARKING, TIMED_PARKING, YIELDING -> {
                return null;  // in transition
            }
            default -> throw new InternalError("" + initialState);
        }

        // thread is unmounted, prevent it from continuing
        int suspendedState = initialState | SUSPENDED;
        if (!compareAndSetState(initialState, suspendedState)) {
            return null;
        }

        // get stack trace and restore state
        StackTraceElement[] stack;
        try {
            stack = cont.getStackTrace();
        } finally {
            assert state == suspendedState;
            setState(initialState);
        }
        boolean resubmit = switch (initialState) {
            case UNPARKED, YIELDED -> {
                // resubmit as task may have run while suspended
                yield true;
            }
            case PARKED, TIMED_PARKED -> {
                // resubmit if unparked while suspended
                yield parkPermit && compareAndSetState(initialState, UNPARKED);
            }
            default -> throw new InternalError();
        };
        if (resubmit) {
            submitRunContinuation();
        }
        return stack;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("VirtualThread[#");
        sb.append(threadId());
        String name = getName();
        if (!name.isEmpty()) {
            sb.append(",");
            sb.append(name);
        }
        sb.append("]/");

        // add the carrier state and thread name when mounted
        boolean mounted;
        if (Thread.currentThread() == this) {
            mounted = appendCarrierInfo(sb);
        } else {
            disableSuspendAndPreempt();
            try {
                synchronized (carrierThreadAccessLock()) {
                    mounted = appendCarrierInfo(sb);
                }
            } finally {
                enableSuspendAndPreempt();
            }
        }

        // add virtual thread state when not mounted
        if (!mounted) {
            String stateAsString = threadState().toString();
            sb.append(stateAsString.toLowerCase(Locale.ROOT));
        }

        return sb.toString();
    }

    /**
     * Appends the carrier state and thread name to the string buffer if mounted.
     * @return true if mounted, false if not mounted
     */
    private boolean appendCarrierInfo(StringBuilder sb) {
        assert Thread.currentThread() == this || Thread.holdsLock(carrierThreadAccessLock());
        Thread carrier = carrierThread;
        if (carrier != null) {
            String stateAsString = carrier.threadState().toString();
            sb.append(stateAsString.toLowerCase(Locale.ROOT));
            sb.append('@');
            sb.append(carrier.getName());
            return true;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return (int) threadId();
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this;
    }

    /**
     * Returns the termination object, creating it if needed.
     */
    private CountDownLatch getTermination() {
        CountDownLatch termination = this.termination;
        if (termination == null) {
            termination = new CountDownLatch(1);
            if (!U.compareAndSetReference(this, TERMINATION, null, termination)) {
                termination = this.termination;
            }
        }
        return termination;
    }

    /**
     * Returns the lock object to synchronize on when accessing carrierThread.
     * The lock prevents carrierThread from being reset to null during unmount.
     */
    private Object carrierThreadAccessLock() {
        // return interruptLock as unmount has to coordinate with interrupt
        return interruptLock;
    }

    /**
     * Disallow the current thread be suspended or preempted.
     */
    private void disableSuspendAndPreempt() {
        notifyJvmtiDisableSuspend(true);
        Continuation.pin();
    }

    /**
     * Allow the current thread be suspended or preempted.
     */
    private void enableSuspendAndPreempt() {
        Continuation.unpin();
        notifyJvmtiDisableSuspend(false);
    }

    // -- wrappers for get/set of state, parking permit, and carrier thread --

    private int state() {
        return state;  // volatile read
    }

    private void setState(int newValue) {
        state = newValue;  // volatile write
    }

    private boolean compareAndSetState(int expectedValue, int newValue) {
        return U.compareAndSetInt(this, STATE, expectedValue, newValue);
    }

    private void setParkPermit(boolean newValue) {
        if (parkPermit != newValue) {
            parkPermit = newValue;
        }
    }

    private boolean getAndSetParkPermit(boolean newValue) {
        if (parkPermit != newValue) {
            return U.getAndSetBoolean(this, PARK_PERMIT, newValue);
        } else {
            return newValue;
        }
    }

    private void setCarrierThread(Thread carrier) {
        // U.putReferenceRelease(this, CARRIER_THREAD, carrier);
        this.carrierThread = carrier;
    }

    // -- JVM TI support --

    @IntrinsicCandidate
    @JvmtiMountTransition
    private native void notifyJvmtiStart();

    @IntrinsicCandidate
    @JvmtiMountTransition
    private native void notifyJvmtiEnd();

    @IntrinsicCandidate
    @JvmtiMountTransition
    private native void notifyJvmtiMount(boolean hide);

    @IntrinsicCandidate
    @JvmtiMountTransition
    private native void notifyJvmtiUnmount(boolean hide);

    @IntrinsicCandidate
    @JvmtiMountTransition
    private static native void notifyJvmtiHideFrames(boolean hide);

    @IntrinsicCandidate
    private static native void notifyJvmtiDisableSuspend(boolean enter);

    private static native void registerNatives();
    static {
        registerNatives();

        // ensure VTHREAD_GROUP is created, may be accessed by JVMTI
        var group = Thread.virtualThreadGroup();

        // ensure VirtualThreadPinnedEvent is loaded/initialized
        U.ensureClassInitialized(VirtualThreadPinnedEvent.class);
    }

    /**
     * Creates the default ForkJoinPool scheduler.
     */
    @SuppressWarnings("removal")
    private static ForkJoinPool createDefaultScheduler() {
        ForkJoinWorkerThreadFactory factory = pool -> {
            PrivilegedAction<ForkJoinWorkerThread> pa = () -> new CarrierThread(pool);
            return AccessController.doPrivileged(pa);
        };
        PrivilegedAction<ForkJoinPool> pa = () -> {
            int parallelism, maxPoolSize, minRunnable;
            String parallelismValue = System.getProperty("jdk.virtualThreadScheduler.parallelism");
            String maxPoolSizeValue = System.getProperty("jdk.virtualThreadScheduler.maxPoolSize");
            String minRunnableValue = System.getProperty("jdk.virtualThreadScheduler.minRunnable");
            if (parallelismValue != null) {
                parallelism = Integer.parseInt(parallelismValue);
            } else {
                parallelism = Runtime.getRuntime().availableProcessors();
            }
            if (maxPoolSizeValue != null) {
                maxPoolSize = Integer.parseInt(maxPoolSizeValue);
                parallelism = Integer.min(parallelism, maxPoolSize);
            } else {
                maxPoolSize = Integer.max(parallelism, 256);
            }
            if (minRunnableValue != null) {
                minRunnable = Integer.parseInt(minRunnableValue);
            } else {
                minRunnable = Integer.max(parallelism / 2, 1);
            }
            Thread.UncaughtExceptionHandler handler = (t, e) -> { };
            boolean asyncMode = true; // FIFO
            return new ForkJoinPool(parallelism, factory, handler, asyncMode,
                         0, maxPoolSize, minRunnable, pool -> true, 30, SECONDS);
        };
        return AccessController.doPrivileged(pa);
    }

    /**
     * Schedule a runnable task to run after a delay.
     */
    private static Future<?> schedule(Runnable command, long delay, TimeUnit unit) {
        long tid = Thread.currentThread().threadId();
        int index = (int) tid & (DELAYED_TASK_SCHEDULERS.length - 1);
        return DELAYED_TASK_SCHEDULERS[index].schedule(command, delay, unit);
    }

    /**
     * Creates the ScheduledThreadPoolExecutors used to execute delayed tasks.
     */
    private static ScheduledExecutorService[] createDelayedTaskSchedulers() {
        String propName = "jdk.virtualThreadScheduler.timerQueues";
        String propValue = GetPropertyAction.privilegedGetProperty(propName);
        int queueCount;
        if (propValue != null) {
            queueCount = Integer.parseInt(propValue);
            if (queueCount != Integer.highestOneBit(queueCount)) {
                throw new RuntimeException("Value of " + propName + " must be power of 2");
            }
        } else {
            int ncpus = Runtime.getRuntime().availableProcessors();
            queueCount = Math.max(Integer.highestOneBit(ncpus / 4), 1);
        }
        var schedulers = new ScheduledExecutorService[queueCount];
        for (int i = 0; i < queueCount; i++) {
            ScheduledThreadPoolExecutor stpe = (ScheduledThreadPoolExecutor)
                Executors.newScheduledThreadPool(1, task -> {
                    Thread t = InnocuousThread.newThread("VirtualThread-unparker", task);
                    t.setDaemon(true);
                    return t;
                });
            stpe.setRemoveOnCancelPolicy(true);
            schedulers[i] = stpe;
        }
        return schedulers;
    }

    /**
     * Reads the value of the jdk.tracePinnedThreads property to determine if stack
     * traces should be printed when a carrier thread is pinned when a virtual thread
     * attempts to park.
     */
    private static int tracePinningMode() {
        String propValue = GetPropertyAction.privilegedGetProperty("jdk.tracePinnedThreads");
        if (propValue != null) {
            if (propValue.length() == 0 || "full".equalsIgnoreCase(propValue))
                return 1;
            if ("short".equalsIgnoreCase(propValue))
                return 2;
        }
        return 0;
    }
}
