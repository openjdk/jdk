/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.nio.ch;

import java.nio.channels.spi.AsynchronousChannelProvider;
import java.nio.channels.*;
import java.io.IOException;
import java.io.Closeable;
import java.io.FileDescriptor;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Base implementation of AsynchronousChannelGroupImpl for Unix systems.
 */

abstract class Port extends AsynchronousChannelGroupImpl {
    static final short POLLIN       = 0x0001;
    static final short POLLOUT      = 0x0004;
    static final short POLLERR      = 0x0008;
    static final short POLLHUP      = 0x0010;

    /**
     * Implemented by clients registered with this port.
     */
    interface PollableChannel extends Closeable {
        void onEvent(int events, boolean mayInvokeDirect);
    }

    // maps fd to "pollable" channel
    protected final ReadWriteLock fdToChannelLock = new ReentrantReadWriteLock();
    protected final Map<Integer,PollableChannel> fdToChannel =
        new HashMap<Integer,PollableChannel>();


    Port(AsynchronousChannelProvider provider, ThreadPool pool) {
        super(provider, pool);
    }

    /**
     * Register channel identified by its file descriptor
     */
    final void register(int fd, PollableChannel ch) {
        fdToChannelLock.writeLock().lock();
        try {
            if (isShutdown())
                throw new ShutdownChannelGroupException();
            fdToChannel.put(Integer.valueOf(fd), ch);
        } finally {
            fdToChannelLock.writeLock().unlock();
        }
    }

    /**
     * Unregister channel identified by its file descriptor
     */
    final void unregister(int fd) {
        boolean checkForShutdown = false;

        fdToChannelLock.writeLock().lock();
        try {
            fdToChannel.remove(Integer.valueOf(fd));

            // last key to be removed so check if group is shutdown
            if (fdToChannel.isEmpty())
                checkForShutdown = true;

        } finally {
            fdToChannelLock.writeLock().unlock();
        }

        // continue shutdown
        if (checkForShutdown && isShutdown()) {
            try {
                shutdownNow();
            } catch (IOException ignore) { }
        }
    }
    /**
     * Register file descriptor with polling mechanism for given events.
     * The implementation should translate the events as required.
     */
    abstract void startPoll(int fd, int events);

    @Override
    final boolean isEmpty() {
        fdToChannelLock.writeLock().lock();
        try {
            return fdToChannel.isEmpty();
        } finally {
            fdToChannelLock.writeLock().unlock();
        }
    }

    @Override
    final Object attachForeignChannel(final Channel channel, FileDescriptor fd) {
        int fdVal = IOUtil.fdVal(fd);
        register(fdVal, new PollableChannel() {
            public void onEvent(int events, boolean mayInvokeDirect) { }
            public void close() throws IOException {
                channel.close();
            }
        });
        return Integer.valueOf(fdVal);
    }

    @Override
    final void detachForeignChannel(Object key) {
        unregister((Integer)key);
    }

    @Override
    final void closeAllChannels() {
        /**
         * Close channels in batches of up to 128 channels. This allows close
         * to remove the channel from the map without interference.
         */
        final int MAX_BATCH_SIZE = 128;
        PollableChannel channels[] = new PollableChannel[MAX_BATCH_SIZE];
        int count;
        do {
            // grab a batch of up to 128 channels
            fdToChannelLock.writeLock().lock();
            count = 0;
            try {
                for (Integer fd: fdToChannel.keySet()) {
                    channels[count++] = fdToChannel.get(fd);
                    if (count >= MAX_BATCH_SIZE)
                        break;
                }
            } finally {
                fdToChannelLock.writeLock().unlock();
            }

            // close them
            for (int i=0; i<count; i++) {
                try {
                    channels[i].close();
                } catch (IOException ignore) { }
            }
        } while (count > 0);
    }
}
