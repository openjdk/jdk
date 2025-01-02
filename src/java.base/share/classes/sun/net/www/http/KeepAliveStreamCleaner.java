/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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

package sun.net.www.http;

import java.io.IOException;
import java.util.LinkedList;
import sun.net.NetProperties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This class is used to clean up any remaining data that may be on a KeepAliveStream
 * so that the connection can be cached in the KeepAliveCache.
 * Instances of this class can be used as a FIFO queue for KeepAliveCleanerEntry objects.
 * Executing this Runnable removes each KeepAliveCleanerEntry from the Queue, reads
 * the remaining bytes on its KeepAliveStream, and if successful puts the connection in
 * the KeepAliveCache.
 *
 * @author Chris Hegarty
 */

@SuppressWarnings("serial")  // never serialized
class KeepAliveStreamCleaner
    extends LinkedList<KeepAliveCleanerEntry>
    implements Runnable
{
    // maximum amount of remaining data that we will try to clean up
    protected static final long MAX_DATA_REMAINING;

    // maximum amount of KeepAliveStreams to be queued
    protected static final int MAX_CAPACITY;

    // timeout for both socket and poll on the queue
    protected static final int TIMEOUT = 5000;

    // max retries for skipping data
    private static final int MAX_RETRIES = 5;

    static {
        final String maxDataKey = "http.KeepAlive.remainingData";
        MAX_DATA_REMAINING = NetProperties.getInteger(maxDataKey, 512) * 1024L;

        final String maxCapacityKey = "http.KeepAlive.queuedConnections";
        MAX_CAPACITY = NetProperties.getInteger(maxCapacityKey, 10);

    }

    private final ReentrantLock queueLock = new ReentrantLock();
    private final Condition waiter = queueLock.newCondition();

    final void signalAll() {
        waiter.signalAll();
    }

    final void lock() {
        queueLock.lock();
    }

    final void unlock() {
        queueLock.unlock();
    }

    @Override
    public boolean offer(KeepAliveCleanerEntry e) {
        if (size() >= MAX_CAPACITY)
            return false;

        return super.offer(e);
    }

    @Override
    public void run()
    {
        KeepAliveCleanerEntry kace = null;

        do {
            try {
                lock();
                try {
                    long before = System.currentTimeMillis();
                    long timeout = TIMEOUT;
                    while ((kace = poll()) == null) {
                        waiter.await(timeout, TimeUnit.MILLISECONDS);

                        long after = System.currentTimeMillis();
                        long elapsed = after - before;
                        if (elapsed > timeout) {
                            /* one last try */
                            kace = poll();
                            break;
                        }
                        before = after;
                        timeout -= elapsed;
                    }
                } finally {
                    unlock();
                }

                if(kace == null)
                    break;

                KeepAliveStream kas = kace.getKeepAliveStream();

                if (kas != null) {
                    kas.lock();
                    try {
                        HttpClient hc = kace.getHttpClient();
                        try {
                            if (hc != null && !hc.isInKeepAliveCache()) {
                                int oldTimeout = hc.getReadTimeout();
                                hc.setReadTimeout(TIMEOUT);
                                long remainingToRead = kas.remainingToRead();
                                if (remainingToRead > 0) {
                                    long n = 0;
                                    int retries = 0;
                                    while (n < remainingToRead && retries < MAX_RETRIES) {
                                        remainingToRead = remainingToRead - n;
                                        n = kas.skip(remainingToRead);
                                        if (n == 0)
                                            retries++;
                                    }
                                    remainingToRead = remainingToRead - n;
                                }
                                if (remainingToRead == 0) {
                                    hc.setReadTimeout(oldTimeout);
                                    hc.finished();
                                } else
                                    hc.closeServer();
                            }
                        } catch (IOException ioe) {
                            hc.closeServer();
                        } finally {
                            kas.setClosed();
                        }
                    } finally {
                        kas.unlock();
                    }
                }
            } catch (InterruptedException ie) { }
        } while (kace != null);
    }
}
