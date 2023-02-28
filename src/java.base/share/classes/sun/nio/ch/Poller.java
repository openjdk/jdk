/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOError;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
import jdk.internal.misc.InnocuousThread;
import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;
import sun.security.action.GetPropertyAction;

/**
 * Polls file descriptors. Virtual threads invoke the poll method to park
 * until a given file descriptor is ready for I/O.
 */
public abstract class Poller {
    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();
    private static final Poller[] READ_POLLERS;
    private static final Poller[] WRITE_POLLERS;
    private static final int READ_MASK, WRITE_MASK;
    private static final boolean USE_DIRECT_REGISTER;

    // true if this is a poller for reading, false for writing
    private final boolean read;

    // maps file descriptors to parked Thread
    private final Map<Integer, Thread> map = new ConcurrentHashMap<>();

    // the queue of updates to the updater Thread
    private final BlockingQueue<Request> queue = new LinkedTransferQueue<>();

    /**
     * Initialize a Poller for reading or writing.
     */
    protected Poller(boolean read) {
        this.read = read;
    }

    /**
     * Returns true if this poller is for read (POLLIN) events.
     */
    final boolean reading() {
        return read;
    }

    /**
     * Parks the current thread until a file descriptor is ready for the given op.
     * @param fdVal the file descriptor
     * @param event POLLIN or POLLOUT
     * @param nanos the waiting time or 0 to wait indefinitely
     * @param supplier supplies a boolean to indicate if the enclosing object is open
     */
    public static void poll(int fdVal, int event, long nanos, BooleanSupplier supplier)
        throws IOException
    {
        assert nanos >= 0L;
        if (event == Net.POLLIN) {
            readPoller(fdVal).poll(fdVal, nanos, supplier);
        } else if (event == Net.POLLOUT) {
            writePoller(fdVal).poll(fdVal, nanos, supplier);
        } else {
            assert false;
        }
    }

    /**
     * Parks the current thread until a file descriptor is ready.
     */
    private void poll(int fdVal, long nanos, BooleanSupplier supplier) throws IOException {
        if (USE_DIRECT_REGISTER) {
            poll1(fdVal, nanos, supplier);
        } else {
            poll2(fdVal, nanos, supplier);
        }
    }

    /**
     * Parks the current thread until a file descriptor is ready. This implementation
     * registers the file descriptor, then parks until the file descriptor is polled.
     */
    private void poll1(int fdVal, long nanos, BooleanSupplier supplier) throws IOException {
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
     * Parks the current thread until a file descriptor is ready. This implementation
     * queues the file descriptor to the update thread, then parks until the file
     * descriptor is polled.
     */
    private void poll2(int fdVal, long nanos, BooleanSupplier supplier) {
        Request request = registerAsync(fdVal);
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
            request.awaitFinish();
            deregister(fdVal);
        }
    }

    /**
     * Registers the file descriptor.
     */
    private void register(int fdVal) throws IOException {
        Thread previous = map.putIfAbsent(fdVal, Thread.currentThread());
        assert previous == null;
        implRegister(fdVal);
    }

    /**
     * Queues the file descriptor to be registered by the updater thread, returning
     * a Request object to track the request.
     */
    private Request registerAsync(int fdVal) {
        Thread previous = map.putIfAbsent(fdVal, Thread.currentThread());
        assert previous == null;
        Request request = new Request(fdVal);
        queue.add(request);
        return request;
    }

    /**
     * Deregister the file descriptor, a no-op if already polled.
     */
    private void deregister(int fdVal) {
        Thread previous = map.remove(fdVal);
        assert previous == null || previous == Thread.currentThread();
        if (previous != null) {
            implDeregister(fdVal);
        }
    }

    /**
     * A registration request queued to the updater thread.
     */
    private static class Request {
        private final int fdVal;
        private volatile boolean done;
        private volatile Thread waiter;

        Request(int fdVal) {
            this.fdVal = fdVal;
        }

        private int fdVal() {
            return fdVal;
        }

        /**
         * Invoked by the updater when the request has been processed.
         */
        void finish() {
            done = true;
            Thread waiter = this.waiter;
            if (waiter != null) {
                LockSupport.unpark(waiter);
            }
        }

        /**
         * Waits for a request to be processed.
         */
        void awaitFinish() {
            if (!done) {
                waiter = Thread.currentThread();
                boolean interrupted = false;
                while (!done) {
                    LockSupport.park();
                    if (Thread.interrupted()) {
                        interrupted = true;
                    }
                }
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * Register the file descriptor.
     */
    abstract void implRegister(int fdVal) throws IOException;

    /**
     * Deregister the file descriptor.
     */
    abstract void implDeregister(int fdVal);

    /**
     * Starts the poller threads.
     */
    private Poller start() {
        String prefix = (read) ? "Read" : "Write";
        startThread(prefix + "-Poller", this::pollLoop);
        if (!USE_DIRECT_REGISTER) {
            startThread(prefix + "-Updater", this::updateLoop);
        }
        return this;
    }

    /**
     * Starts a platform thread to run the given task.
     */
    private void startThread(String name, Runnable task) {
        try {
            Thread thread = JLA.executeOnCarrierThread(() ->
                InnocuousThread.newSystemThread(name, task)
            );
            thread.setDaemon(true);
            thread.start();
        } catch (Exception e) {
            throw new InternalError(e);
        }
    }

    /**
     * Polling loop.
     */
    private void pollLoop() {
        try {
            for (;;) {
                poll();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * The update loop to handle updates to the interest set.
     */
    private void updateLoop() {
        try {
            for (;;) {
                Request req = null;
                while (req == null) {
                    try {
                        req = queue.take();
                    } catch (InterruptedException ignore) { }
                }
                implRegister(req.fdVal());
                req.finish();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Maps the file descriptor value to a read poller.
     */
    private static Poller readPoller(int fdVal) {
        return READ_POLLERS[fdVal & READ_MASK];
    }

    /**
     * Maps the file descriptor value to a write poller.
     */
    private static Poller writePoller(int fdVal) {
        return WRITE_POLLERS[fdVal & WRITE_MASK];
    }

    /**
     * Unparks any thread that is polling the given file descriptor for the
     * given event.
     */
    static void stopPoll(int fdVal, int event) {
        if (event == Net.POLLIN) {
            readPoller(fdVal).wakeup(fdVal);
        } else if (event == Net.POLLOUT) {
            writePoller(fdVal).wakeup(fdVal);
        } else {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Unparks any threads that are polling the given file descriptor.
     */
    static void stopPoll(int fdVal) {
        stopPoll(fdVal, Net.POLLIN);
        stopPoll(fdVal, Net.POLLOUT);
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
     * Called by the polling facility when the file descriptor is polled
     */
    final void polled(int fdVal) {
        wakeup(fdVal);
    }

    /**
     * Poll for events. The {@link #polled(int)} method is invoked for each
     * polled file descriptor.
     *
     * @param timeout if positive then block for up to {@code timeout} milliseconds,
     *     if zero then don't block, if -1 then block indefinitely
     */
    abstract int poll(int timeout) throws IOException;

    /**
     * Poll for events, blocks indefinitely.
     */
    final int poll() throws IOException {
        return poll(-1);
    }

    /**
     * Poll for events, non-blocking.
     */
    final int pollNow() throws IOException {
        return poll(0);
    }

    /**
     * Returns the poller's file descriptor, or -1 if none.
     */
    int fdVal() {
        return -1;
    }

    /**
     * Creates the read and writer pollers.
     */
    static {
        PollerProvider provider = PollerProvider.provider();
        String s = GetPropertyAction.privilegedGetProperty("jdk.useDirectRegister");
        if (s == null) {
            USE_DIRECT_REGISTER = provider.useDirectRegister();
        } else {
            USE_DIRECT_REGISTER = "".equals(s) || Boolean.parseBoolean(s);
        }
        try {
            Poller[] readPollers = createReadPollers(provider);
            READ_POLLERS = readPollers;
            READ_MASK = readPollers.length - 1;
            Poller[] writePollers = createWritePollers(provider);
            WRITE_POLLERS = writePollers;
            WRITE_MASK = writePollers.length - 1;
        } catch (IOException ioe) {
            throw new IOError(ioe);
        }
    }

    /**
     * Create the read poller(s).
     */
    private static Poller[] createReadPollers(PollerProvider provider) throws IOException {
        int readPollerCount = pollerCount("jdk.readPollers");
        Poller[] readPollers = new Poller[readPollerCount];
        for (int i = 0; i< readPollerCount; i++) {
            var poller = provider.readPoller();
            readPollers[i] = poller.start();
        }
        return readPollers;
    }

    /**
     * Create the write poller(s).
     */
    private static Poller[] createWritePollers(PollerProvider provider) throws IOException {
        int writePollerCount = pollerCount("jdk.writePollers");
        Poller[] writePollers = new Poller[writePollerCount];
        for (int i = 0; i< writePollerCount; i++) {
            var poller = provider.writePoller();
            writePollers[i] = poller.start();
        }
        return writePollers;
    }

    /**
     * Reads the given property name to get the poller count. If the property is
     * set then the value must be a power of 2. Returns 1 if the property is not
     * set.
     * @throws IllegalArgumentException if the property is set to a value that
     * is not a power of 2.
     */
    private static int pollerCount(String propName) {
        String s = GetPropertyAction.privilegedGetProperty(propName, "1");
        int count = Integer.parseInt(s);

        // check power of 2
        if (count != (1 << log2(count))) {
            String msg = propName + " is set to a vale that is not a power of 2";
            throw new IllegalArgumentException(msg);
        }
        return count;
    }

    private static int log2(int n) {
        return 31 - Integer.numberOfLeadingZeros(n);
    }

    /**
     * Return a stream of all threads blocked waiting for I/O operations.
     */
    public static Stream<Thread> blockedThreads() {
        Stream<Thread> s = Stream.empty();
        for (int i = 0; i < READ_POLLERS.length; i++) {
            s = Stream.concat(s, READ_POLLERS[i].registeredThreads());
        }
        for (int i = 0; i < WRITE_POLLERS.length; i++) {
            s = Stream.concat(s, WRITE_POLLERS[i].registeredThreads());
        }
        return s;
    }

    private Stream<Thread> registeredThreads() {
        return map.values().stream();
    }
}
