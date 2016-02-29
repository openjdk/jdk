/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
 */
package java.net.http;

import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * Wraps the supplied user ExecutorService.
 *
 * 1) when a Security manager set, the correct access control context
 *    is used to execute task
 *
 * 2) memory fence implemented
 */
class ExecutorWrapper {

    final ExecutorService userExecutor; // the actual executor service used
    final Executor executor;

    public static ExecutorWrapper wrap(ExecutorService userExecutor) {
        return new ExecutorWrapper(userExecutor);
    }

    /**
     * Returns a dummy ExecutorWrapper which uses the calling thread
     */
    public static ExecutorWrapper callingThread() {
        return new ExecutorWrapper();
    }

    private ExecutorWrapper(ExecutorService userExecutor) {
        // used for executing in calling thread
        this.userExecutor = userExecutor;
        this.executor = userExecutor;
    }

    private ExecutorWrapper() {
        this.userExecutor = null;
        this.executor = (Runnable command) -> {
            command.run();
        };
    }

    public ExecutorService userExecutor() {
        return userExecutor;
    }

    public synchronized void synchronize() {}

    public void execute(Runnable r, Supplier<AccessControlContext> ctxSupplier) {
        synchronize();
        Runnable r1 = () -> {
            try {
                r.run();
            } catch (Throwable t) {
                Log.logError(t);
            }
        };

        if (ctxSupplier != null && System.getSecurityManager() != null) {
            AccessControlContext acc = ctxSupplier.get();
            if (acc == null) {
                throw new InternalError();
            }
            AccessController.doPrivilegedWithCombiner(
                (PrivilegedAction<Void>)() -> {
                    executor.execute(r1); // all throwables must be caught
                    return null;
                }, acc);
        } else {
            executor.execute(r1); // all throwables must be caught
        }
    }
}
