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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * <p>Manage a renewable lease.  The lease can be renewed indefinitely
 * but if the lease runs to its current expiry date without being renewed
 * then the expiry callback is invoked.  If the lease has already expired
 * when renewal is attempted then the lease method returns zero.</p>
 * @author sjiang
 * @author emcmanus
 */
// The synchronization logic of this class is tricky to deal correctly with the
// case where the lease expires at the same time as the |lease| or |stop| method
// is called.  If the lease is active then the field |scheduled| represents
// the expiry task; otherwise |scheduled| is null.  Renewing or stopping the
// lease involves canceling this task and setting |scheduled| either to a new
// task (to renew) or to null (to stop).
//
// Suppose the expiry task runs at the same time as the |lease| method is called.
// If the task enters its synchronized block before the method starts, then
// it will set |scheduled| to null and the method will return 0.  If the method
// starts before the task enters its synchronized block, then the method will
// cancel the task which will see that when it later enters the block.
// Similar reasoning applies to the |stop| method.  It is not expected that
// different threads will call |lease| or |stop| simultaneously, although the
// logic should be correct then too.
public class LeaseManager {
    public LeaseManager(Runnable callback) {
        this(callback, EventParams.getLeaseTimeout());
    }

    public LeaseManager(Runnable callback, long timeout) {
        if (logger.traceOn()) {
            logger.trace("LeaseManager", "new manager with lease: "+timeout);
        }
        if (callback == null) {
            throw new NullPointerException("Null callback.");
        }
        if (timeout <= 0)
            throw new IllegalArgumentException("Timeout must be positive: " + timeout);

        this.callback = callback;
        schedule(timeout);
    }

    /**
     * <p>Renew the lease for the given time.  The new time can be shorter
     * than the previous one, in which case the lease will expire earlier
     * than it would have.</p>
     *
     * <p>Calling this method after the lease has expired will return zero
     * immediately and have no other effect.</p>
     *
     * @param timeout the new lifetime.  If zero, the lease
     * will expire immediately.
     */
    public synchronized long lease(long timeout) {
        if (logger.traceOn()) {
            logger.trace("lease", "new lease to: "+timeout);
        }

        if (timeout < 0)
            throw new IllegalArgumentException("Negative lease: " + timeout);

        if (scheduled == null)
            return 0L;

        scheduled.cancel(false);

        if (logger.traceOn())
            logger.trace("lease", "start lease: "+timeout);
        schedule(timeout);

        return timeout;
    }

    private class Expire implements Runnable {
        ScheduledFuture<?> task;

        public void run() {
            synchronized (LeaseManager.this) {
                if (task.isCancelled())
                    return;
                scheduled = null;
            }
            callback.run();
            executor.shutdown();
        }
    }

    private synchronized void schedule(long timeout) {
        Expire expire = new Expire();
        scheduled = executor.schedule(expire, timeout, TimeUnit.MILLISECONDS);
        expire.task = scheduled;
    }

    /**
     * <p>Cancel the lease without calling the expiry callback.</p>
     */
    public synchronized void stop() {
        logger.trace("stop", "canceling lease");
        scheduled.cancel(false);
        scheduled = null;
        try {
            executor.shutdown();
        } catch (SecurityException e) {
            // OK: caller doesn't have RuntimePermission("modifyThread")
            // which is unlikely in reality but triggers a test failure otherwise
            logger.trace("stop", "exception from executor.shutdown", e);
        }
    }

    private final Runnable callback;
    private ScheduledFuture<?> scheduled;  // If null, the lease has expired.

    private final ScheduledExecutorService executor
            = Executors.newScheduledThreadPool(1,
            new DaemonThreadFactory("JMX LeaseManager %d"));

    private static final ClassLogger logger =
            new ClassLogger("javax.management.event", "LeaseManager");

}
