/*
 * Copyright 1999-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.jmx.mbeanserver;

import java.lang.ref.WeakReference;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * <p>A factory for ThreadPoolExecutor objects that allows the same object to
 * be shared by all users of the factory that are in the same ThreadGroup.</p>
 */
// We return a ThreadPoolExecutor rather than the more general ExecutorService
// because we need to be able to call allowCoreThreadTimeout so that threads in
// the pool will eventually be destroyed when the pool is no longer in use.
// Otherwise these threads would keep the ThreadGroup alive forever.
public class PerThreadGroupPool<T extends ThreadPoolExecutor> {
    private final WeakIdentityHashMap<ThreadGroup, WeakReference<T>> map =
            WeakIdentityHashMap.make();

    public static interface Create<T extends ThreadPoolExecutor> {
        public T createThreadPool(ThreadGroup group);
    }

    private PerThreadGroupPool() {}

    public static <T extends ThreadPoolExecutor> PerThreadGroupPool<T> make() {
        return new PerThreadGroupPool<T>();
    }

    public synchronized T getThreadPoolExecutor(Create<T> create) {
        // Find out if there's already an existing executor for the calling
        // thread and reuse it. Otherwise, create a new one and store it in
        // the executors map. If there is a SecurityManager, the group of
        // System.getSecurityManager() is used, else the group of the calling
        // thread.
        SecurityManager s = System.getSecurityManager();
        ThreadGroup group = (s != null) ? s.getThreadGroup() :
            Thread.currentThread().getThreadGroup();
        WeakReference<T> wr = map.get(group);
        T executor = (wr == null) ? null : wr.get();
        if (executor == null) {
            executor = create.createThreadPool(group);
            executor.allowCoreThreadTimeOut(true);
            map.put(group, new WeakReference<T>(executor));
        }
        return executor;
    }
}
