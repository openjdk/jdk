/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.misc;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.StructureViolationException;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Stream;
import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.invoke.MhUtil;
import jdk.internal.vm.ScopedValueContainer;
import jdk.internal.vm.ThreadContainer;
import jdk.internal.vm.ThreadContainers;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * A grouping of threads that typically run closely related tasks. Threads started
 * in a flock remain in the flock until they terminate.
 *
 * <p> ThreadFlock defines the {@link #open(String) open} method to open a new flock,
 * the {@link #start(Thread) start} method to start a thread in the flock, and the
 * {@link #close() close} method to close the flock. The {@code close} waits for all
 * threads in the flock to finish. The {@link #awaitAll() awaitAll} method may be used
 * to wait for all threads to finish without closing the flock. The {@link #wakeup()}
 * method will cause {@code awaitAll} method to complete early, which can be used to
 * support cancellation in higher-level APIs. ThreadFlock also defines the {@link
 * #shutdown() shutdown} method to prevent new threads from starting while allowing
 * existing threads in the flock to continue.
 *
 * <p> Thread flocks are intended to be used in a <em>structured manner</em>. The
 * thread that opens a new flock is the {@link #owner() owner}. The owner closes the
 * flock when done, failure to do so may result in a resource leak. The {@code open}
 * and {@code close} should be matched to avoid closing an <em>enclosing</em> flock
 * while a <em>nested</em> flock is open. A ThreadFlock can be used with the
 * try-with-resources construct if required but more likely, the close method of a
 * higher-level API that implements {@link AutoCloseable} will close the flock.
 *
 * <p> Thread flocks are conceptually nodes in a tree. A thread {@code T} started in
 * flock "A" may itself open a new flock "B", implicitly forming a tree where flock
 * "A" is the parent of flock "B". When nested, say where thread {@code T} opens
 * flock "B" and then invokes a method that opens flock "C", then the enclosing
 * flock "B" is conceptually the parent of the nested flock "C". ThreadFlock does
 * not define APIs that exposes the tree structure. It does define the {@link
 * #containsThread(Thread) containsThread} method to test if a flock contains a
 * thread, a test that is equivalent to testing membership of flocks in the tree.
 * The {@code start} and {@code shutdown} methods are confined to the flock
 * owner or threads contained in the flock. The confinement check is equivalent to
 * invoking the {@code containsThread} method to test if the caller is contained
 * in the flock.
 *
 * <p> Unless otherwise specified, passing a {@code null} argument to a method
 * in this class will cause a {@link NullPointerException} to be thrown.
 */
public class ThreadFlock implements AutoCloseable {
    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();
    private static final VarHandle THREAD_COUNT;
    private static final VarHandle PERMIT;
    static {
        MethodHandles.Lookup l = MethodHandles.lookup();
        THREAD_COUNT = MhUtil.findVarHandle(l, "threadCount", int.class);
        PERMIT = MhUtil.findVarHandle(l, "permit", boolean.class);
    }

    private final Set<Thread> threads = ConcurrentHashMap.newKeySet();

    // thread count, need to re-examine contention once API is stable
    private volatile int threadCount;

    private final String name;
    private final ScopedValueContainer.BindingsSnapshot scopedValueBindings;
    private final ThreadContainerImpl container; // encapsulate for now

    // state
    private volatile boolean shutdown;
    private volatile boolean closed;

    // set by wakeup, cleared by awaitAll
    private volatile boolean permit;

    ThreadFlock(String name) {
        this.name = name;
        this.scopedValueBindings = ScopedValueContainer.captureBindings();
        this.container = new ThreadContainerImpl(this);
    }

    private long threadCount() {
        return threadCount;
    }

    private ScopedValueContainer.BindingsSnapshot scopedValueBindings() {
        return scopedValueBindings;
    }

    private void incrementThreadCount() {
        THREAD_COUNT.getAndAdd(this, 1);
    }

    /**
     * Decrement the thread count. Unpark the owner if the count goes to zero.
     */
    private void decrementThreadCount() {
        int count = (int) THREAD_COUNT.getAndAdd(this, -1) - 1;

        // signal owner when the count goes to zero
        if (count == 0) {
            LockSupport.unpark(owner());
        }
    }

    /**
     * Invoked on the parent thread when starting {@code thread}.
     */
    private void onStart(Thread thread) {
        incrementThreadCount();
        boolean done = false;
        try {
            boolean added = threads.add(thread);
            assert added;
            if (shutdown)
                throw new IllegalStateException("Shutdown");
            done = true;
        } finally {
            if (!done) {
                threads.remove(thread);
                decrementThreadCount();
            }
        }
    }

    /**
     * Invoked on the terminating thread or the parent thread when starting
     * {@code thread} failed. This method is only called if onStart succeeded.
     */
    private void onExit(Thread thread) {
        boolean removed = threads.remove(thread);
        assert removed;
        decrementThreadCount();
    }

    /**
     * Clear wakeup permit.
     */
    private void clearPermit() {
        if (permit)
            permit = false;
    }

    /**
     * Sets the wakeup permit to the given value, returning the previous value.
     */
    private boolean getAndSetPermit(boolean newValue) {
        if (permit != newValue) {
            return (boolean) PERMIT.getAndSet(this, newValue);
        } else {
            return newValue;
        }
    }

    /**
     * Throws WrongThreadException if the current thread is not the owner.
     */
    private void ensureOwner() {
        if (Thread.currentThread() != owner())
            throw new WrongThreadException("Current thread not owner");
    }

    /**
     * Throws WrongThreadException if the current thread is not the owner
     * or a thread contained in the flock.
     */
    private void ensureOwnerOrContainsThread() {
        Thread currentThread = Thread.currentThread();
        if (currentThread != owner() && !containsThread(currentThread))
            throw new WrongThreadException("Current thread not owner or thread in flock");
    }

    /**
     * Opens a new thread flock. The flock is owned by the current thread. It can be
     * named to aid debugging.
     *
     * <p> This method captures the current thread's {@linkplain ScopedValue scoped value}
     * bindings for inheritance by threads created in the flock.
     *
     * <p> For the purposes of containment, monitoring, and debugging, the parent
     * of the new flock is determined as follows:
     * <ul>
     * <li> If the current thread is the owner of open flocks then the most recently
     * created, and open, flock is the parent of the new flock. In other words, the
     * <em>enclosing flock</em> is the parent.
     * <li> If the current thread is not the owner of any open flocks then the
     * parent of the new flock is the current thread's flock. If the current thread
     * was not started in a flock then the new flock does not have a parent.
     * </ul>
     *
     * @param name the name of the flock, can be null
     * @return a new thread flock
     */
    public static ThreadFlock open(String name) {
        var flock = new ThreadFlock(name);
        flock.container.push();
        return flock;
    }

    /**
     * {@return the name of this flock or {@code null} if unnamed}
     */
    public String name() {
        return name;
    }

    /**
     * {@return the owner of this flock}
     */
    public Thread owner() {
        return container.owner();
    }

    /**
     * Starts the given unstarted thread in this flock.
     *
     * <p> The thread is started with the scoped value bindings that were captured
     * when opening the flock. The bindings must match the current thread's bindings.
     *
     * <p> This method may only be invoked by the flock owner or threads {@linkplain
     * #containsThread(Thread) contained} in the flock.
     *
     * @param thread the unstarted thread
     * @return the thread, started
     * @throws IllegalStateException if this flock is shutdown or closed
     * @throws IllegalThreadStateException if the given thread was already started
     * @throws WrongThreadException if the current thread is not the owner or a thread
     * contained in the flock
     * @throws StructureViolationException if the current scoped value bindings are
     * not the same as when the flock was created
     */
    public Thread start(Thread thread) {
        ensureOwnerOrContainsThread();
        JLA.start(thread, container);
        return thread;
    }

    /**
     * Shutdown this flock so that no new threads can be started, existing threads
     * in the flock will continue to run. This method is a no-op if the flock is
     * already shutdown or closed.
     */
    public void shutdown() {
        if (!shutdown) {
            shutdown = true;
        }
    }

    /**
     * Wait for all threads in the flock to finish executing their tasks. This method
     * waits until all threads finish, the {@link #wakeup() wakeup} method is invoked,
     * or the current thread is interrupted.
     *
     * <p> This method may only be invoked by the flock owner. The method trivially
     * returns true when the flock is closed.
     *
     * <p> This method clears the effect of any previous invocations of the
     * {@code wakeup} method.
     *
     * @return true if there are no threads in the flock, false if wakeup was invoked
     * and there are unfinished threads
     * @throws InterruptedException if interrupted while waiting
     * @throws WrongThreadException if invoked by a thread that is not the owner
     */
    public boolean awaitAll() throws InterruptedException {
        ensureOwner();

        if (getAndSetPermit(false))
            return (threadCount == 0);

        while (threadCount > 0 && !permit) {
            LockSupport.park();
            if (Thread.interrupted())
                throw new InterruptedException();
        }
        clearPermit();
        return (threadCount == 0);
    }

    /**
     * Wait, up to the given waiting timeout, for all threads in the flock to finish
     * executing their tasks. This method waits until all threads finish, the {@link
     * #wakeup() wakeup} method is invoked, the current thread is interrupted, or
     * the timeout expires.
     *
     * <p> This method may only be invoked by the flock owner. The method trivially
     * returns true when the flock is closed.
     *
     * <p> This method clears the effect of any previous invocations of the {@code wakeup}
     * method.
     *
     * @param timeout the maximum duration to wait
     * @return true if there are no threads in the flock, false if wakeup was invoked
     * and there are unfinished threads
     * @throws InterruptedException if interrupted while waiting
     * @throws TimeoutException if the wait timed out
     * @throws WrongThreadException if invoked by a thread that is not the owner
     */
    public boolean awaitAll(Duration timeout)
            throws InterruptedException, TimeoutException {
        Objects.requireNonNull(timeout);
        ensureOwner();

        if (getAndSetPermit(false))
            return (threadCount == 0);

        long startNanos = System.nanoTime();
        long nanos = NANOSECONDS.convert(timeout);
        long remainingNanos = nanos;
        while (threadCount > 0 && remainingNanos > 0 && !permit) {
            LockSupport.parkNanos(remainingNanos);
            if (Thread.interrupted())
                throw new InterruptedException();
            remainingNanos = nanos - (System.nanoTime() - startNanos);
        }

        boolean done = (threadCount == 0);
        if (!done && remainingNanos <= 0 && !permit) {
            throw new TimeoutException();
        } else {
            clearPermit();
            return done;
        }
    }

    /**
     * Causes the call to {@link #awaitAll()} or {@link #awaitAll(Duration)} by the
     * {@linkplain #owner() owner} to return immediately.
     *
     * <p> If the owner is blocked in {@code awaitAll} then it will return immediately.
     * If the owner is not blocked in {@code awaitAll} then its next call to wait
     * will return immediately. The method does nothing when the flock is closed.
     */
    public void wakeup() {
        if (!getAndSetPermit(true) && Thread.currentThread() != owner()) {
            LockSupport.unpark(owner());
        }
    }

    /**
     * Closes this flock. This method first shuts down the flock to prevent
     * new threads from starting. It then waits for the threads in the flock
     * to finish executing their tasks. In other words, this method blocks until
     * all threads in the flock finish.
     *
     * <p> This method may only be invoked by the flock owner.
     *
     * <p> If interrupted then this method continues to wait until all threads
     * finish, before completing with the interrupt status set.
     *
     * <p> A ThreadFlock is intended to be used in a <em>structured manner</em>. If
     * this method is called to close a flock before nested flocks are closed then it
     * closes the nested flocks (in the reverse order that they were created in),
     * closes this flock, and then throws {@code StructureViolationException}.
     * Similarly, if this method is called to close a thread flock while executing with
     * scoped value bindings, and the thread flock was created before the scoped values
     * were bound, then {@code StructureViolationException} is thrown after closing the
     * thread flock.
     *
     * @throws WrongThreadException if invoked by a thread that is not the owner
     * @throws StructureViolationException if a structure violation was detected
     */
    public void close() {
        ensureOwner();
        if (closed)
            return;

        // shutdown, if not already shutdown
        if (!shutdown)
            shutdown = true;

        // wait for threads to finish
        boolean interrupted = false;
        try {
            while (threadCount > 0) {
                LockSupport.park();
                if (Thread.interrupted()) {
                    interrupted = true;
                }
            }

        } finally {
            try {
                container.close(); // may throw
            } finally {
                closed = true;
                if (interrupted) Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * {@return true if the flock has been {@linkplain #shutdown() shut down}}
     */
    public boolean isShutdown() {
        return shutdown;
    }

    /**
     * {@return true if the flock has been {@linkplain #close() closed}}
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * {@return a stream of the threads in this flock}
     * The elements of the stream are threads that were started in this flock
     * but have not terminated. The stream will reflect the set of threads in the
     * flock at some point at or since the creation of the stream. It may or may
     * not reflect changes to the set of threads subsequent to creation of the
     * stream.
     */
    public Stream<Thread> threads() {
        return threads.stream();
    }

    /**
     * Tests if this flock contains the given thread. This method returns {@code true}
     * if the thread was started in this flock and has not finished. If the thread
     * is not in this flock then it tests if the thread is in flocks owned by threads
     * in this flock, essentially equivalent to invoking {@code containsThread} method
     * on all flocks owned by the threads in this flock.
     *
     * @param thread the thread
     * @return true if this flock contains the thread
     */
    public boolean containsThread(Thread thread) {
        var c = JLA.threadContainer(thread);
        if (c == this.container)
            return true;
        if (c != null && c != ThreadContainers.root()) {
            var parent = c.parent();
            while (parent != null) {
                if (parent == this.container)
                    return true;
                parent = parent.parent();
            }
        }
        return false;
    }

    @Override
    public String toString() {
        String id = Objects.toIdentityString(this);
        if (name != null) {
            return name + "/" + id;
        } else {
            return id;
        }
    }

    /**
     * A ThreadContainer backed by a ThreadFlock.
     */
    private static class ThreadContainerImpl extends ThreadContainer {
        private final ThreadFlock flock;
        private volatile Object key;
        private boolean closing;

        ThreadContainerImpl(ThreadFlock flock) {
            super(/*shared*/ false);
            this.flock = flock;
        }

        @Override
        public ThreadContainerImpl push() {
            // Virtual threads in the root containers may not be tracked so need
            // to register container to ensure that it is found
            if (!ThreadContainers.trackAllThreads()) {
                Thread thread = Thread.currentThread();
                if (thread.isVirtual()
                        && JLA.threadContainer(thread) == ThreadContainers.root()) {
                    this.key = ThreadContainers.registerContainer(this);
                }
            }
            super.push();
            return this;
        }

        /**
         * Invoked by ThreadFlock.close when closing the flock. This method pops the
         * container from the current thread's scope stack.
         */
        void close() {
            assert Thread.currentThread() == owner();
            if (!closing) {
                closing = true;
                boolean atTop = popForcefully(); // may block
                Object key = this.key;
                if (key != null)
                    ThreadContainers.deregisterContainer(key);
                if (!atTop)
                    throw new StructureViolationException();
            }
        }

        /**
         * Invoked when an enclosing scope is closing. Invokes ThreadFlock.close to
         * close the flock. This method does not pop the container from the current
         * thread's scope stack.
         */
        @Override
        protected boolean tryClose() {
            assert Thread.currentThread() == owner();
            if (!closing) {
                closing = true;
                flock.close();
                Object key = this.key;
                if (key != null)
                    ThreadContainers.deregisterContainer(key);
                return true;
            } else {
                assert false : "Should not get there";
                return false;
            }
        }

        @Override
        public String name() {
            return flock.name();
        }
        @Override
        public long threadCount() {
            return flock.threadCount();
        }
        @Override
        public Stream<Thread> threads() {
            return flock.threads().filter(Thread::isAlive);
        }
        @Override
        public void onStart(Thread thread) {
            flock.onStart(thread);
        }
        @Override
        public void onExit(Thread thread) {
            flock.onExit(thread);
        }
        @Override
        public ScopedValueContainer.BindingsSnapshot scopedValueBindings() {
            return flock.scopedValueBindings();
        }
    }
}
