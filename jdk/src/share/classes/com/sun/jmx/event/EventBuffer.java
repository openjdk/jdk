/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.jmx.event;

import com.sun.jmx.remote.util.ClassLogger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.management.remote.NotificationResult;
import javax.management.remote.TargetedNotification;

public class EventBuffer {

    public EventBuffer() {
        this(Integer.MAX_VALUE, null);
    }

    public EventBuffer(int capacity) {
        this(capacity, new ArrayList<TargetedNotification>());
    }

    public EventBuffer(int capacity, final List<TargetedNotification> list) {
        if (logger.traceOn()) {
            logger.trace("EventBuffer", "New buffer with the capacity: "
                    +capacity);
        }
        if (capacity < 1) {
            throw new IllegalArgumentException(
                    "The capacity must be bigger than 0");
        }

        if (list == null) {
            throw new NullPointerException("Null list.");
        }

        this.capacity = capacity;
        this.list = list;
    }

    public void add(TargetedNotification tn) {
        if (logger.traceOn()) {
            logger.trace("add", "Add one notif.");
        }

        synchronized(lock) {
            if (list.size() == capacity) { // have to throw one
                passed++;
                list.remove(0);

                if (logger.traceOn()) {
                    logger.trace("add", "Over, remove the oldest one.");
                }
            }

            list.add(tn);
            lock.notify();
        }
    }

    public void add(TargetedNotification[] tns) {
        if (tns == null || tns.length == 0) {
            return;
        }

        if (logger.traceOn()) {
            logger.trace("add", "Add notifs: "+tns.length);
        }

        synchronized(lock) {
            final int d = list.size() - capacity + tns.length;
            if (d > 0) { // have to throw
                passed += d;
                if (logger.traceOn()) {
                    logger.trace("add",
                            "Over, remove the oldest: "+d);
                }
                if (tns.length <= capacity){
                    list.subList(0, d).clear();
                } else {
                    list.clear();
                    TargetedNotification[] tmp =
                            new TargetedNotification[capacity];
                    System.arraycopy(tns, tns.length-capacity, tmp, 0, capacity);
                    tns = tmp;
                }
            }

            Collections.addAll(list,tns);
            lock.notify();
        }
    }

    public NotificationResult fetchNotifications(long startSequenceNumber,
            long timeout,
            int maxNotifications) {
        if (logger.traceOn()) {
            logger.trace("fetchNotifications",
                    "Being called: "
                    +startSequenceNumber+" "
                    +timeout+" "+maxNotifications);
        }
        if (startSequenceNumber < 0 ||
                timeout < 0 ||
                maxNotifications < 0) {
            throw new IllegalArgumentException("Negative value.");
        }

        TargetedNotification[] tns = new TargetedNotification[0];
        long earliest = startSequenceNumber < passed ?
            passed : startSequenceNumber;
        long next = earliest;

        final long startTime = System.currentTimeMillis();
        long toWait = timeout;
        synchronized(lock) {
            int toSkip = (int)(startSequenceNumber - passed);

            // skip those before startSequenceNumber.
            while (!closed && toSkip > 0) {
                toWait = timeout - (System.currentTimeMillis() - startTime);
                if (list.size() == 0) {
                    if (toWait <= 0) {
                        // the notification of startSequenceNumber
                        // does not arrive yet.
                        return new NotificationResult(startSequenceNumber,
                                startSequenceNumber,
                                new TargetedNotification[0]);
                    }

                    waiting(toWait);
                    continue;
                }

                if (toSkip <= list.size()) {
                    list.subList(0, toSkip).clear();
                    passed += toSkip;

                    break;
                } else {
                    passed += list.size();
                    toSkip -= list.size();

                    list.clear();
                }
            }

            earliest = passed;

            if (list.size() == 0) {
                toWait = timeout - (System.currentTimeMillis() - startTime);

                waiting(toWait);
            }

            if (list.size() == 0) {
                tns = new TargetedNotification[0];
            } else if (list.size() <= maxNotifications) {
                tns = list.toArray(new TargetedNotification[0]);
            } else {
                tns = new TargetedNotification[maxNotifications];
                for (int i=0; i<maxNotifications; i++) {
                    tns[i] = list.get(i);
                }
            }

            next = earliest + tns.length;
        }

        if (logger.traceOn()) {
            logger.trace("fetchNotifications",
                    "Return: "+earliest+" "+next+" "+tns.length);
        }

        return new NotificationResult(earliest, next, tns);
    }

    public int size() {
        return list.size();
    }

    public void addLost(long nb) {
        synchronized(lock) {
            passed += nb;
        }
    }

    public void close() {
        if (logger.traceOn()) {
            logger.trace("clear", "done");
        }

        synchronized(lock) {
            list.clear();
            closed = true;
            lock.notifyAll();
        }
    }


    // -------------------------------------------
    // private classes
    // -------------------------------------------
    private void waiting(long timeout) {
        final long startTime = System.currentTimeMillis();
        long toWait = timeout;
        synchronized(lock) {
            while (!closed && list.size() == 0 && toWait > 0) {
                try {
                    lock.wait(toWait);

                    toWait = timeout - (System.currentTimeMillis() - startTime);
                } catch (InterruptedException ire) {
                    logger.trace("waiting", ire);
                    break;
                }
            }
        }
    }

    private final int capacity;
    private final List<TargetedNotification> list;
    private boolean closed;

    private long passed = 0;
    private final int[] lock = new int[0];

    private static final ClassLogger logger =
            new ClassLogger("javax.management.event", "EventBuffer");
}
