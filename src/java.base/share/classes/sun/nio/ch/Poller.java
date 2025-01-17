/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
package sun.nio.ch;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import jdk.internal.misc.InnocuousThread;
import jdk.internal.vm.annotation.Stable;

/**
 * Polls file descriptors. Virtual threads invoke the poll method to park
 * until a given file descriptor is ready for I/O.
 */
public abstract class Poller {
    private static final Pollers POLLERS;
    static {
        try {
            var pollers = new Pollers();
            pollers.start();
            POLLERS = pollers;
        } catch (IOException ioe) {
            throw new ExceptionInInitializerError(ioe);
        }
    }

    // the poller or sub-poller thread
    private @Stable Thread owner;

    // maps file descriptors to parked Thread
    private final Map<Integer, Thread> map = new ConcurrentHashMap<>();

    /**
     * Poller mode.
     */
    enum Mode {
        /**
         * ReadPoller and WritePoller are dedicated platform threads that block waiting
         * for events and unpark virtual threads when file descriptors are ready for I/O.
         */
        SYSTEM_THREADS,

        /**
         * ReadPoller and WritePoller threads are virtual threads that poll for events,
         * yielding between polls and unparking virtual threads when file descriptors are
         * ready for I/O. If there are no events then the poller threads park until there
         * are I/O events to poll. This mode helps to integrate polling with virtual
         * thread scheduling. The approach is similar to the default scheme in "User-level
         * Threading: Have Your Cake and Eat It Too" by Karsten and Barghi 2020
         * (https://dl.acm.org/doi/10.1145/3379483).
         */
        VTHREAD_POLLERS
    }

    /**
     * Initialize a Poller.
     */
    protected Poller() {
    }

    /**
     * Returns the poller's file descriptor, used when the read and write poller threads
     * are virtual threads.
     *
     * @throws UnsupportedOperationException if not supported
     */
    int fdVal() {
        throw new UnsupportedOperationException();
    }

    /**
     * Register the file descriptor. The registration is "one shot", meaning it should
     * be polled at most once.
     */
    abstract void implRegister(int fdVal) throws IOException;

    /**
     * Deregister the file descriptor.
     * @param polled true if the file descriptor has already been polled
     */
    abstract void implDeregister(int fdVal, boolean polled);

    /**
     * Poll for events. The {@link #polled(int)} method is invoked for each
     * polled file descriptor.
     *
     * @param timeout if positive then block for up to {@code timeout} milliseconds,
     *     if zero then don't block, if -1 then block indefinitely
     * @return the number of file descriptors polled
     */
    abstract int poll(int timeout) throws IOException;

    /**
     * Callback by the poll method when a file descriptor is polled.
     */
    final void polled(int fdVal) {
        wakeup(fdVal);
    }

    /**
     * Parks the current thread until a file descriptor is ready for the given op.
     * @param fdVal the file descriptor
     * @param event POLLIN or POLLOUT
     * @param nanos the waiting time or 0 to wait indefinitely
     * @param supplier supplies a boolean to indicate if the enclosing object is open
     */
    static void poll(int fdVal, int event, long nanos, BooleanSupplier supplier)
        throws IOException
    {
        assert nanos >= 0L;
        if (event == Net.POLLIN) {
            POLLERS.readPoller(fdVal).poll(fdVal, nanos, supplier);
        } else if (event == Net.POLLOUT) {
            POLLERS.writePoller(fdVal).poll(fdVal, nanos, supplier);
        } else {
            assert false;
        }
    }

    /**
     * Parks the current thread until a Selector's file descriptor is ready.
     * @param fdVal the Selector's file descriptor
     * @param nanos the waiting time or 0 to wait indefinitely
     */
    static void pollSelector(int fdVal, long nanos) throws IOException {
        assert nanos >= 0L;
        Poller poller = POLLERS.masterPoller();
        if (poller == null) {
            poller = POLLERS.readPoller(fdVal);
        }
        poller.poll(fdVal, nanos, () -> true);
    }

    /**
     * If there is a thread polling the given file descriptor for the given event then
     * the thread is unparked.
     */
    static void stopPoll(int fdVal, int event) {
        if (event == Net.POLLIN) {
            POLLERS.readPoller(fdVal).wakeup(fdVal);
        } else if (event == Net.POLLOUT) {
            POLLERS.writePoller(fdVal).wakeup(fdVal);
        } else {
            throw new IllegalArgumentException();
        }
    }

    /**
     * If there are any threads polling the given file descriptor then they are unparked.
     */
    static void stopPoll(int fdVal) {
        stopPoll(fdVal, Net.POLLIN);
        stopPoll(fdVal, Net.POLLOUT);
    }

    /**
     * Parks the current thread until a file descriptor is ready.
     */
    private void poll(int fdVal, long nanos, BooleanSupplier supplier) throws IOException {
        register(fdVal);
        try {
            boolean isOpen = supplier.getAsBoolean();
            if (isOpen) {
                if (nanos > 0) {
                    LockSupport.parkNanos(nanos);
                } else {
                    LockSupport.park();
                }
            }
        } finally {
            deregister(fdVal);
        }
    }

    /**
     * Registers the file descriptor to be polled at most once when the file descriptor
     * is ready for I/O.
     */
    private void register(int fdVal) throws IOException {
        Thread previous = map.put(fdVal, Thread.currentThread());
        assert previous == null;
        try {
            implRegister(fdVal);
        } catch (Throwable t) {
            map.remove(fdVal);
            throw t;
        }
    }

    /**
     * Deregister the file descriptor so that the file descriptor is not polled.
     */
    private void deregister(int fdVal) {
        Thread previous = map.remove(fdVal);
        boolean polled = (previous == null);
        assert polled || previous == Thread.currentThread();
        implDeregister(fdVal, polled);
    }

    /**
     * Unparks any thread that is polling the given file descriptor.
     */
    private void wakeup(int fdVal) {
        Thread t = map.remove(fdVal);
        if (t != null) {
            LockSupport.unpark(t);
        }
    }

    /**
     * Master polling loop. The {@link #polled(int)} method is invoked for each file
     * descriptor that is polled.
     */
    private void pollerLoop() {
        owner = Thread.currentThread();
        try {
            for (;;) {
                poll(-1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Sub-poller polling loop. The {@link #polled(int)} method is invoked for each file
     * descriptor that is polled.
     *
     * The sub-poller registers its file descriptor with the master poller to park until
     * there are events to poll. When unparked, it does non-blocking polls and parks
     * again when there are no more events. The sub-poller yields after each poll to help
     * with fairness and to avoid re-registering with the master poller where possible.
     */
    private void subPollerLoop(Poller masterPoller) {
        assert Thread.currentThread().isVirtual();
        owner = Thread.currentThread();
        try {
            int polled = 0;
            for (;;) {
                if (polled == 0) {
                    masterPoller.poll(fdVal(), 0, () -> true);  // park
                } else {
                    Thread.yield();
                }
                polled = poll(0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns the number I/O operations currently registered with this poller.
     */
    public int registered() {
        return map.size();
    }

    @Override
    public String toString() {
        return String.format("%s [registered = %d, owner = %s]",
                Objects.toIdentityString(this), registered(), owner);
    }

    /**
     * The Pollers used for read and write events.
     */
    private static class Pollers {
        private final PollerProvider provider;
        private final Poller.Mode pollerMode;
        private final Poller masterPoller;
        private final Poller[] readPollers;
        private final Poller[] writePollers;

        // used by start method to executor is kept alive
        private Executor executor;

        /**
         * Creates the Poller instances based on configuration.
         */
        Pollers() throws IOException {
            PollerProvider provider = PollerProvider.provider();
            Poller.Mode mode;
            String s = System.getProperty("jdk.pollerMode");
            if (s != null) {
                if (s.equalsIgnoreCase(Mode.SYSTEM_THREADS.name()) || s.equals("1")) {
                    mode = Mode.SYSTEM_THREADS;
                } else if (s.equalsIgnoreCase(Mode.VTHREAD_POLLERS.name()) || s.equals("2")) {
                    mode = Mode.VTHREAD_POLLERS;
                } else {
                    throw new RuntimeException("Can't parse '" + s + "' as polling mode");
                }
            } else {
                mode = provider.defaultPollerMode();
            }

            // vthread poller mode needs a master poller
            Poller masterPoller = (mode == Mode.VTHREAD_POLLERS)
                    ? provider.readPoller(false)
                    : null;

            // read pollers (or sub-pollers)
            int readPollerCount = pollerCount("jdk.readPollers", provider.defaultReadPollers(mode));
            Poller[] readPollers = new Poller[readPollerCount];
            for (int i = 0; i < readPollerCount; i++) {
                readPollers[i] = provider.readPoller(mode == Mode.VTHREAD_POLLERS);
            }

            // write pollers (or sub-pollers)
            int writePollerCount = pollerCount("jdk.writePollers", provider.defaultWritePollers(mode));
            Poller[] writePollers = new Poller[writePollerCount];
            for (int i = 0; i < writePollerCount; i++) {
                writePollers[i] = provider.writePoller(mode == Mode.VTHREAD_POLLERS);
            }

            this.provider = provider;
            this.pollerMode = mode;
            this.masterPoller = masterPoller;
            this.readPollers = readPollers;
            this.writePollers = writePollers;
        }

        /**
         * Starts the Poller threads.
         */
        void start() {
            if (pollerMode == Mode.VTHREAD_POLLERS) {
                startPlatformThread("MasterPoller", masterPoller::pollerLoop);
                ThreadFactory factory = Thread.ofVirtual()
                        .inheritInheritableThreadLocals(false)
                        .name("SubPoller-", 0)
                        .uncaughtExceptionHandler((t, e) -> e.printStackTrace())
                        .factory();
                executor = Executors.newThreadPerTaskExecutor(factory);
                Arrays.stream(readPollers).forEach(p -> {
                    executor.execute(() -> p.subPollerLoop(masterPoller));
                });
                Arrays.stream(writePollers).forEach(p -> {
                    executor.execute(() -> p.subPollerLoop(masterPoller));
                });
            } else {
                Arrays.stream(readPollers).forEach(p -> {
                    startPlatformThread("Read-Poller", p::pollerLoop);
                });
                Arrays.stream(writePollers).forEach(p -> {
                    startPlatformThread("Write-Poller", p::pollerLoop);
                });
            }
        }

        /**
         * Returns the master poller, or null if there is no master poller.
         */
        Poller masterPoller() {
            return masterPoller;
        }

        /**
         * Returns the read poller for the given file descriptor.
         */
        Poller readPoller(int fdVal) {
            int index = provider.fdValToIndex(fdVal, readPollers.length);
            return readPollers[index];
        }

        /**
         * Returns the write poller for the given file descriptor.
         */
        Poller writePoller(int fdVal) {
            int index = provider.fdValToIndex(fdVal, writePollers.length);
            return writePollers[index];
        }

        /**
         * Return the list of read pollers.
         */
        List<Poller> readPollers() {
            return List.of(readPollers);
        }

        /**
         * Return the list of write pollers.
         */
        List<Poller> writePollers() {
            return List.of(writePollers);
        }


        /**
         * Reads the given property name to get the poller count. If the property is
         * set then the value must be a power of 2. Returns 1 if the property is not
         * set.
         * @throws IllegalArgumentException if the property is set to a value that
         * is not a power of 2.
         */
        private static int pollerCount(String propName, int defaultCount) {
            String s = System.getProperty(propName);
            int count = (s != null) ? Integer.parseInt(s) : defaultCount;

            // check power of 2
            if (count != Integer.highestOneBit(count)) {
                String msg = propName + " is set to a value that is not a power of 2";
                throw new IllegalArgumentException(msg);
            }
            return count;
        }

        /**
         * Starts a platform thread to run the given task.
         */
        private void startPlatformThread(String name, Runnable task) {
            try {
                Thread thread = InnocuousThread.newSystemThread(name, task);
                thread.setDaemon(true);
                thread.setUncaughtExceptionHandler((t, e) -> e.printStackTrace());
                thread.start();
            } catch (Exception e) {
                throw new InternalError(e);
            }
        }
    }

    /**
     * Return the master poller or null if there is no master poller.
     */
    public static Poller masterPoller() {
        return POLLERS.masterPoller();
    }

    /**
     * Return the list of read pollers.
     */
    public static List<Poller> readPollers() {
        return POLLERS.readPollers();
    }

    /**
     * Return the list of write pollers.
     */
    public static List<Poller> writePollers() {
        return POLLERS.writePollers();
    }
}
