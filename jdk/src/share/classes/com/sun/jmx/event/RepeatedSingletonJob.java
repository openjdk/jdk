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
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * <p>A task that is repeatedly run by an Executor.  The task will be
 * repeated as long as the {@link #isSuspended()} method returns true.  Once
 * that method returns false, the task is no longer executed until someone
 * calls {@link #resume()}.</p>
 * @author sjiang
 */
public abstract class RepeatedSingletonJob implements Runnable {
    public RepeatedSingletonJob(Executor executor) {
        if (executor == null) {
            throw new NullPointerException("Null executor!");
        }

        this.executor = executor;
    }

    public boolean isWorking() {
        return working;
    }

    public void resume() {

        synchronized(this) {
            if (!working)  {
                if (logger.traceOn()) {
                    logger.trace("resume", "");
                }
                working = true;
                execute();
            }
        }
    }

    public abstract void task();
    public abstract boolean isSuspended();

    public void run() {
        if (logger.traceOn()) {
            logger.trace("run", "execute the task");
        }
        try {
            task();
        } catch (Exception e) {
            // A correct task() implementation should not throw exceptions.
            // It may cause isSuspended() to start returning true, though.
            logger.trace("run", "failed to execute the task", e);
        }

        synchronized(this) {
            if (!isSuspended()) {
                execute();
            } else {
                if (logger.traceOn()) {
                    logger.trace("run", "suspend the task");
                }
                working = false;
            }
        }

    }

    private void execute() {
        try {
            executor.execute(this);
        } catch (RejectedExecutionException e) {
            logger.warning(
                    "execute",
                    "Executor threw exception (" + this.getClass().getName() + ")",
                    e);
            throw new RejectedExecutionException(
                    "Executor.execute threw exception -" +
                    "should not be possible", e);
            // User-supplied Executor should not be configured in a way that
            // might cause this exception, for example if it is shared between
            // several client objects and doesn't have capacity for one job
            // from each one.  CR 6732037 will add text to the spec explaining
            // the problem.  The rethrown exception will propagate either out
            // of resume() to user code, or out of run() to the Executor
            // (which will probably ignore it).
        }
    }

    private boolean working = false;
    private final Executor executor;

    private static final ClassLogger logger =
            new ClassLogger("javax.management.event", "RepeatedSingletonJob");
}
