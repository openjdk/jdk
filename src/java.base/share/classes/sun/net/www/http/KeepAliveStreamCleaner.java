/*
 * Copyright (c) 2005, 2008, Oracle and/or its affiliates. All rights reserved.
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
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * This class is used to cleanup any remaining data that may be on a KeepAliveStream
 * so that the connection can be cached in the KeepAliveCache.
 * Instances of this class can be used as a FIFO queue for KeepAliveCleanerEntry objects.
 * Executing this Runnable removes each KeepAliveCleanerEntry from the Queue, reads
 * the reamining bytes on its KeepAliveStream, and if successful puts the connection in
 * the KeepAliveCache.
 *
 * @author Chris Hegarty
 */

@SuppressWarnings("serial")  // never serialized
class KeepAliveStreamCleaner
    extends LinkedList<KeepAliveCleanerEntry>
    implements Runnable
{
    // maximum amount of remaining data that we will try to cleanup
    protected static int MAX_DATA_REMAINING = 512;

    // maximum amount of KeepAliveStreams to be queued
    protected static int MAX_CAPACITY = 10;

    // timeout for both socket and poll on the queue
    protected static final int TIMEOUT = 5000;

    // max retries for skipping data
    private static final int MAX_RETRIES = 5;

    static {
        final String maxDataKey = "http.KeepAlive.remainingData";
        int maxData = AccessController.doPrivileged(
            new PrivilegedAction<Integer>() {
                public Integer run() {
                    return NetProperties.getInteger(maxDataKey, MAX_DATA_REMAINING);
                }}).intValue() * 1024;
        MAX_DATA_REMAINING = maxData;

        final String maxCapacityKey = "http.KeepAlive.queuedConnections";
        int maxCapacity = AccessController.doPrivileged(
            new PrivilegedAction<Integer>() {
                public Integer run() {
                    return NetProperties.getInteger(maxCapacityKey, MAX_CAPACITY);
                }}).intValue();
        MAX_CAPACITY = maxCapacity;

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
                synchronized(this) {
                    long before = System.currentTimeMillis();
                    long timeout = TIMEOUT;
                    while ((kace = poll()) == null) {
                        this.wait(timeout);

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
                }

                if(kace == null)
                    break;

                KeepAliveStream kas = kace.getKeepAliveStream();

                if (kas != null) {
                    synchronized(kas) {
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
                    }
                }
            } catch (InterruptedException ie) { }
        } while (kace != null);
    }
}
