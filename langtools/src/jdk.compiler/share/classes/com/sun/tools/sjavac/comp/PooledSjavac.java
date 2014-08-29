/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.tools.sjavac.comp;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.tools.sjavac.Log;
import com.sun.tools.sjavac.server.CompilationResult;
import com.sun.tools.sjavac.server.Sjavac;
import com.sun.tools.sjavac.server.SysInfo;

/**
 * An sjavac implementation that limits the number of concurrent calls by
 * wrapping invocations in Callables and delegating them to a FixedThreadPool.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class PooledSjavac implements Sjavac {

    final Sjavac delegate;
    final ExecutorService pool;

    public PooledSjavac(Sjavac delegate, int poolsize) {
        Objects.requireNonNull(delegate);
        this.delegate = delegate;
        pool = Executors.newFixedThreadPool(poolsize, new ThreadFactory() {
            AtomicInteger count = new AtomicInteger();
            @Override
            public Thread newThread(Runnable runnable) {
                String cls = PooledSjavac.class.getSimpleName();
                int num = count.incrementAndGet();
                Thread t = new Thread(runnable, cls + "-" + num);
                t.setDaemon(true);
                return t;
            }
        });
    }

    @Override
    public SysInfo getSysInfo() {
        try {
            return pool.submit(new Callable<SysInfo>() {
                @Override
                public SysInfo call() throws Exception {
                    return delegate.getSysInfo();
                }
            }).get();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error during getSysInfo", e);
        }
    }

    @Override
    public CompilationResult compile(final String protocolId,
                                     final String invocationId,
                                     final String[] args,
                                     final List<File> explicitSources,
                                     final Set<URI> sourcesToCompile,
                                     final Set<URI> visibleSources) {
        try {
            return pool.submit(new Callable<CompilationResult>() {
                @Override
                public CompilationResult call() throws Exception {
                    return delegate.compile(protocolId,
                                            invocationId,
                                            args,
                                            explicitSources,
                                            sourcesToCompile,
                                            visibleSources);
                }
            }).get();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error during compile", e);
        }
    }

    @Override
    public void shutdown() {
        pool.shutdown(); // Disable new tasks from being submitted
        try {
            // Wait a while for existing tasks to terminate
            if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                pool.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!pool.awaitTermination(60, TimeUnit.SECONDS))
                    Log.error("ThreadPool did not terminate");
            }
            // Grace period for thread termination
            Thread.sleep(1000);
        } catch (InterruptedException ie) {
          // (Re-)Cancel if current thread also interrupted
          pool.shutdownNow();
          // Preserve interrupt status
          Thread.currentThread().interrupt();
        }

        delegate.shutdown();
    }

    @Override
    public String serverSettings() {
        return delegate.serverSettings();
    }
}
