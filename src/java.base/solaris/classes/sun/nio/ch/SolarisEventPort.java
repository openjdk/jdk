/*
 * Copyright (c) 2008, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.channels.spi.AsynchronousChannelProvider;
import java.util.concurrent.RejectedExecutionException;
import java.io.IOException;
import jdk.internal.misc.Unsafe;

/**
 * Provides an AsynchronousChannelGroup implementation based on the Solaris 10
 * event port framework and also provides direct access to that framework.
 */

class SolarisEventPort
    extends Port
{
    private static final Unsafe unsafe = Unsafe.getUnsafe();
    private static final int addressSize = unsafe.addressSize();

    private static int dependsArch(int value32, int value64) {
        return (addressSize == 4) ? value32 : value64;
    }

    /*
     * typedef struct port_event {
     *     int             portev_events;
     *     ushort_t        portev_source;
     *     ushort_t        portev_pad;
     *     uintptr_t       portev_object;
     *     void            *portev_user;
     * } port_event_t;
     */
    static final int SIZEOF_PORT_EVENT  = dependsArch(16, 24);
    static final int OFFSETOF_EVENTS    = 0;
    static final int OFFSETOF_SOURCE    = 4;
    static final int OFFSETOF_OBJECT    = 8;

    // port sources
    static final short PORT_SOURCE_USER     = 3;
    static final short PORT_SOURCE_FD       = 4;

    // file descriptor to event port.
    private final int port;

    // true when port is closed
    private boolean closed;

    SolarisEventPort(AsynchronousChannelProvider provider, ThreadPool pool)
        throws IOException
    {
        super(provider, pool);

        // create event port
        this.port = port_create();
    }

    SolarisEventPort start() {
        startThreads(new EventHandlerTask());
        return this;
    }

    // releass resources
    private void implClose() {
        synchronized (this) {
            if (closed)
                return;
            closed = true;
        }
        port_close(port);
    }

    private void wakeup() {
        try {
            port_send(port, 0);
        } catch (IOException x) {
            throw new AssertionError(x);
        }
    }

    @Override
    void executeOnHandlerTask(Runnable task) {
        synchronized (this) {
            if (closed)
                throw new RejectedExecutionException();
            offerTask(task);
            wakeup();
        }
    }

    @Override
    void shutdownHandlerTasks() {
       /*
         * If no tasks are running then just release resources; otherwise
         * write to the one end of the socketpair to wakeup any polling threads..
         */
        int nThreads = threadCount();
        if (nThreads == 0) {
            implClose();
        } else {
            // send user event to wakeup each thread
            while (nThreads-- > 0) {
                try {
                    port_send(port, 0);
                } catch (IOException x) {
                    throw new AssertionError(x);
                }
            }
        }
    }

    @Override
    void startPoll(int fd, int events) {
        // (re-)associate file descriptor
        // no need to translate events
        try {
            port_associate(port, PORT_SOURCE_FD, fd, events);
        } catch (IOException x) {
            throw new AssertionError();     // should not happen
        }
    }

    /*
     * Task to read a single event from the port and dispatch it to the
     * channel's onEvent handler.
     */
    private class EventHandlerTask implements Runnable {
        public void run() {
            Invoker.GroupAndInvokeCount myGroupAndInvokeCount =
                Invoker.getGroupAndInvokeCount();
            final boolean isPooledThread = (myGroupAndInvokeCount != null);
            boolean replaceMe = false;
            long address = unsafe.allocateMemory(SIZEOF_PORT_EVENT);
            try {
                for (;;) {
                    // reset invoke count
                    if (isPooledThread)
                        myGroupAndInvokeCount.resetInvokeCount();

                    // wait for I/O completion event
                    // A error here is fatal (thread will not be replaced)
                    replaceMe = false;
                    try {
                        port_get(port, address);
                    } catch (IOException x) {
                        x.printStackTrace();
                        return;
                    }

                    // event source
                    short source = unsafe.getShort(address + OFFSETOF_SOURCE);
                    if (source != PORT_SOURCE_FD) {
                        // user event is trigger to invoke task or shutdown
                        if (source == PORT_SOURCE_USER) {
                            Runnable task = pollTask();
                            if (task == null) {
                                // shutdown request
                                return;
                            }
                            // run task (may throw error/exception)
                            replaceMe = true;
                            task.run();
                        }
                        // ignore
                        continue;
                    }

                    // pe->portev_object is file descriptor
                    int fd = (int)unsafe.getAddress(address + OFFSETOF_OBJECT);
                    // pe->portev_events
                    int events = unsafe.getInt(address + OFFSETOF_EVENTS);

                    // lookup channel
                    PollableChannel ch;
                    fdToChannelLock.readLock().lock();
                    try {
                        ch = fdToChannel.get(fd);
                    } finally {
                        fdToChannelLock.readLock().unlock();
                    }

                    // notify channel
                    if (ch != null) {
                        replaceMe = true;
                        // no need to translate events
                        ch.onEvent(events, isPooledThread);
                    }
                }
            } finally {
                // free per-thread resources
                unsafe.freeMemory(address);
                // last task to exit when shutdown release resources
                int remaining = threadExit(this, replaceMe);
                if (remaining == 0 && isShutdown())
                    implClose();
            }
        }
    }

    /**
     * Creates an event port
     */
    static native int port_create() throws IOException;

    /**
     * Associates specific events of a given object with a port
     */
    static native boolean port_associate(int port, int source, long object, int events)
        throws IOException;

    /**
     * Removes the association of an object with a port.
     */
    static native boolean port_dissociate(int port, int source, long object)
        throws IOException;

    /**
     * Retrieves a single event from a port
     */
    static native void port_get(int port, long pe) throws IOException;

    /**
     * Retrieves at most {@code max} events from a port.
     */
    static native int port_getn(int port, long address, int max, long timeout)
        throws IOException;

    /**
     * Sends a user-defined eventto a specified  port.
     */
    static native void port_send(int port, int events) throws IOException;

    /**
     * Closes a port.
     */
    static native void port_close(int port);


    static {
        IOUtil.load();
    }
}
