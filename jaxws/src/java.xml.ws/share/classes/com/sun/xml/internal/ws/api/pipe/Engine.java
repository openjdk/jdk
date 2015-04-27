/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.api.pipe;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.server.Container;
import com.sun.xml.internal.ws.api.server.ContainerResolver;

/**
 * Collection of {@link Fiber}s.
 * Owns an {@link Executor} to run them.
 *
 * @author Kohsuke Kawaguchi
 * @author Jitendra Kotamraju
 */
public class Engine {
    private volatile Executor threadPool;
    public final String id;
    private final Container container;

    String getId() { return id; }
    Container getContainer() { return container; }
    Executor getExecutor() { return threadPool; }

    public Engine(String id, Executor threadPool) {
        this(id, ContainerResolver.getDefault().getContainer(), threadPool);
    }

    public Engine(String id, Container container, Executor threadPool) {
        this(id, container);
        this.threadPool = threadPool != null ? wrap(threadPool) : null;
    }

    public Engine(String id) {
        this(id, ContainerResolver.getDefault().getContainer());
    }

    public Engine(String id, Container container) {
        this.id = id;
        this.container = container;
    }

    public void setExecutor(Executor threadPool) {
        this.threadPool = threadPool != null ? wrap(threadPool) : null;
    }

    void addRunnable(Fiber fiber) {
        if(threadPool==null) {
            synchronized(this) {
                threadPool = wrap(Executors.newCachedThreadPool(new DaemonThreadFactory()));
            }
        }
        threadPool.execute(fiber);
    }

    private Executor wrap(Executor ex) {
        return ContainerResolver.getDefault().wrapExecutor(container, ex);
    }

    /**
     * Creates a new fiber in a suspended state.
     *
     * <p>
     * To start the returned fiber, call {@link Fiber#start(Tube,Packet,Fiber.CompletionCallback)}.
     * It will start executing the given {@link Tube} with the given {@link Packet}.
     *
     * @return new Fiber
     */
    public Fiber createFiber() {
        return new Fiber(this);
    }

    private static class DaemonThreadFactory implements ThreadFactory {
        static final AtomicInteger poolNumber = new AtomicInteger(1);
        final AtomicInteger threadNumber = new AtomicInteger(1);
        final String namePrefix;

        DaemonThreadFactory() {
            namePrefix = "jaxws-engine-" + poolNumber.getAndIncrement() + "-thread-";
        }

        public Thread newThread(Runnable r) {
            Thread t = ThreadHelper.createNewThread(r);
            t.setName(namePrefix + threadNumber.getAndIncrement());
            if (!t.isDaemon()) {
                t.setDaemon(true);
            }
            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }
            return t;
        }
    }
}
