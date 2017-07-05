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
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author sjiang
 */
public class LeaseRenewer {
    public LeaseRenewer(ScheduledExecutorService scheduler, Callable<Long> doRenew) {
        if (logger.traceOn()) {
            logger.trace("LeaseRenewer", "New LeaseRenewer.");
        }

        if (doRenew == null) {
            throw new NullPointerException("Null job to call server.");
        }

        this.doRenew = doRenew;
        nextRenewTime = System.currentTimeMillis();

        this.scheduler = scheduler;
        future = this.scheduler.schedule(myRenew, 0, TimeUnit.MILLISECONDS);
    }

    public void close() {
        if (logger.traceOn()) {
            logger.trace("close", "Close the lease.");
        }

        synchronized(lock) {
            if (closed) {
                return;
            } else {
                closed = true;
            }
        }

        try {
            future.cancel(false); // not interrupt if running
        } catch (Exception e) {
            // OK
            if (logger.debugOn()) {
                logger.debug("close", "Failed to cancel the leasing job.", e);
            }
        }
    }

    public boolean closed() {
        synchronized(lock) {
            return closed;
        }
    }

    // ------------------------------
    // private
    // ------------------------------
    private final Runnable myRenew = new Runnable() {
        public void run() {
            synchronized(lock) {
                if (closed()) {
                    return;
                }
            }

            long next = nextRenewTime - System.currentTimeMillis();
            if (next < MIN_MILLIS) {
                try {
                    if (logger.traceOn()) {
                        logger.trace("myRenew-run", "");
                    }
                    next = doRenew.call().longValue();

                } catch (Exception e) {
                    logger.fine("myRenew-run", "Failed to renew lease", e);
                    close();
                }

                if (next > 0 && next < Long.MAX_VALUE) {
                    next = next/2;
                    next = (next < MIN_MILLIS) ? MIN_MILLIS : next;
                } else {
                    close();
                }
            }

            nextRenewTime = System.currentTimeMillis() + next;

            if (logger.traceOn()) {
                logger.trace("myRenew-run", "Next leasing: "+next);
            }

            synchronized(lock) {
                if (!closed) {
                    future = scheduler.schedule(this, next, TimeUnit.MILLISECONDS);
                }
            }
        }
    };

    private final Callable<Long> doRenew;
    private ScheduledFuture<?> future;
    private boolean closed = false;
    private long nextRenewTime;

    private final int[] lock = new int[0];

    private final ScheduledExecutorService scheduler;

    private static final long MIN_MILLIS = 50;

    private static final ClassLogger logger =
            new ClassLogger("javax.management.event", "LeaseRenewer");
}
