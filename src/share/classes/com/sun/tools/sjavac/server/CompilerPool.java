/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.sjavac.server;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.Stack;
import java.util.concurrent.Future;

/** The compiler pool maintains compiler threads.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 */
public class CompilerPool {
    // The javac server that created this pool.
    private JavacServer javacServer;
    // A semaphore protecting the poolsize number of threads.
    private Semaphore available;
    // The stack of compiler threads.
    private Stack<CompilerThread> compilers = new Stack<CompilerThread>();
    // And the executor server to spawn threads.
    private final ExecutorService executorPool;
    // How many requests are active right now?
    private int concurrentRequests = 0;
    // When was the last request finished?
    private long lastRequestFinished = 0;
    // The total number of requests to this pool.
    private int numRequests = 0;
    // Protect access to the three above values.
    private static final Object conc = new Object();

    /**
     * Return the javac server that this pool belongs to.
     */
    public JavacServer getJavacServer() {
        return javacServer;
    }

    /**
     * Return how many threads are running at this very moment.
     */
    public int numActiveRequests()
    {
        synchronized (conc) {
            return concurrentRequests;
        }
    }

    /**
     * Return when the last request was finished.
     * I.e. the pool has been idle since.
     */
    public long lastRequestFinished()
    {
        synchronized (conc) {
            return lastRequestFinished;
        }
    }

    /**
     * Up the number of active requests.
     */
    public int startRequest() {
        int n;
        synchronized (conc) {
            concurrentRequests++;
            numRequests++;
            n = numRequests;
        }
        return n;
    }

    /**
     * Down the number of active requests. Return the current time.
     */
    public long stopRequest() {
        synchronized (conc) {
            concurrentRequests--;
            lastRequestFinished = System.currentTimeMillis();
        }
        return lastRequestFinished;
    }

    /**
     * Create a new compiler pool.
     */
    CompilerPool(int poolsize, JavacServer server) {
        available = new Semaphore(poolsize, true);
        javacServer = server;
        executorPool = Executors.newFixedThreadPool(poolsize);
        lastRequestFinished = System.currentTimeMillis();
    }

    /**
     * Execute a compiler thread.
     */
    public void execute(CompilerThread ct) {
        executorPool.execute(ct);
    }

    /**
     * Execute a minor task, for example generating bytecodes and writing them to disk,
     * that belong to a major compiler thread task.
     */
    public Future<?> executeSubtask(CompilerThread t, Runnable r) {
        return executorPool.submit(r);
    }

    /**
     * Shutdown the pool.
     */
    public void shutdown() {
        executorPool.shutdown();
    }

    /**
     * Acquire a compiler thread from the pool, or block until a thread is available.
     * If the pools is empty, create a new thread, but never more than is "available".
     */
    public CompilerThread grabCompilerThread() throws InterruptedException {
        available.acquire();
        if (compilers.empty()) {
            return new CompilerThread(this);
        }
        return compilers.pop();
    }

    /**
     * Return the specified compiler thread to the pool.
     */
    public void returnCompilerThread(CompilerThread h) {
        compilers.push(h);
        available.release();
    }
}

