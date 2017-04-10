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

import org.graalvm.compiler.debug.Debug.Scope;

/**
 * A mechanism for re-executing a task upon failure.
 */
public abstract class DebugRetryableTask<T> extends DelegatingDebugConfig {

    /**
     * Calls {@link #run} on this task and if it results in an exception, calls
     * {@link #onRetry(Throwable)} and if that returns {@code true}, calls {@link #run}.
     */
    @SuppressWarnings("try")
    public final T execute() {
        try {
            return run(null);
        } catch (Throwable t) {
            if (onRetry(t)) {
                try (Scope d = Debug.sandbox("Retrying: " + this, this)) {
                    return run(t);
                } catch (Throwable t2) {
                    throw Debug.handle(t2);
                }
            } else {
                throw t;
            }
        }
    }

    /**
     * Runs this task.
     *
     * @param failure the cause of the first execution to fail or {@code null} if this is the first
     *            execution of {@link #run(Throwable)}
     */
    protected abstract T run(Throwable failure);

    /**
     * Notifies this object that the initial execution failed with exception {@code t} and is about
     * to be re-executed. The re-execution will use this object as the active {@link DebugConfig}.
     * As such, this method can be overridden to enable more detailed debug facilities.
     *
     * @param t an exception that terminated the first execution of this task
     * @return whether this task should be re-executed. If false, {@code t} will be re-thrown.
     */
    protected boolean onRetry(Throwable t) {
        return true;
    }
}
