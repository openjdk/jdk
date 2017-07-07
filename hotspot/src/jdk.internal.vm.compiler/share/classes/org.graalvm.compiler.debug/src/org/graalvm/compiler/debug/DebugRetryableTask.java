/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package org.graalvm.compiler.debug;

/**
 * A mechanism for re-executing a task upon failure.
 */
public abstract class DebugRetryableTask<T> {

    /**
     * Calls {@link #run} on this task and if it results in an exception, calls
     * {@link #getRetryContext} and if that returns a non-null value,
     * {@link #run(DebugContext, Throwable)} is called with it.
     *
     * @param initialDebug the debug context to be used for the initial execution
     */
    @SuppressWarnings("try")
    public final T runWithRetry(DebugContext initialDebug) {
        try {
            return run(initialDebug, null);
        } catch (Throwable t) {
            DebugContext retryDebug = getRetryContext(initialDebug, t);
            if (retryDebug != null) {
                return run(retryDebug, t);
            } else {
                throw t;
            }
        }
    }

    /**
     * Runs this body of this task.
     *
     * @param debug the debug context to use for the execution
     * @param failure {@code null} if this is the first execution otherwise the cause of the first
     *            execution to fail
     */
    protected abstract T run(DebugContext debug, Throwable failure);

    /**
     * Notifies this object that the initial execution failed with exception {@code t} and requests
     * a debug context to be used for re-execution.
     *
     * @param initialDebug the debug context used for the initial execution
     * @param t an exception that terminated the first execution of this task
     * @return the debug context to be used for re-executing this task or {@code null} if {@code t}
     *         should immediately be re-thrown without re-executing this task
     */
    protected DebugContext getRetryContext(DebugContext initialDebug, Throwable t) {
        return null;
    }
}
