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
 * questions.
 */

package jdk.incubator.http;

import java.net.SocketPermission;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.Executor;
import jdk.internal.misc.InnocuousThread;

/**
 * Wraps the supplied user Executor
 *
 * when a Security manager set, the correct access control context is used to execute task
 *
 * The access control context is captured at creation time of this object
 */
class ExecutorWrapper {

    final Executor userExecutor; // the undeerlying executor provided by user
    final Executor executor; // the executur which wraps the user's one
    final AccessControlContext acc;
    final ClassLoader ccl;

    public ExecutorWrapper(Executor userExecutor, AccessControlContext acc) {
        this.userExecutor = userExecutor;
        this.acc = acc;
        this.ccl = getCCL();
        if (System.getSecurityManager() == null) {
            this.executor = userExecutor;
        } else {
            this.executor = this::run;
        }
    }

    private ClassLoader getCCL() {
        return AccessController.doPrivileged(
            (PrivilegedAction<ClassLoader>) () -> {
                return Thread.currentThread().getContextClassLoader();
            }
        );
    }

    /**
     * This is only used for the default HttpClient to deal with
     * different application contexts that might be using it.
     * The default client uses InnocuousThreads in its Executor.
     */
    private void prepareThread() {
        final Thread me = Thread.currentThread();
        if (!(me instanceof InnocuousThread))
            return;
        InnocuousThread innocuousMe = (InnocuousThread)me;

        AccessController.doPrivileged(
            (PrivilegedAction<Void>) () -> {
                innocuousMe.setContextClassLoader(ccl);
                innocuousMe.eraseThreadLocals();
                return null;
            }
        );
    }


    void run(Runnable r) {
        prepareThread();
        try {
            userExecutor.execute(r); // all throwables must be caught
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public Executor userExecutor() {
        return userExecutor;
    }

    public Executor executor() {
        return executor;
    }
}
