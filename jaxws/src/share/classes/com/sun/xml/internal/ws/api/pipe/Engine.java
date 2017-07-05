/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.xml.internal.ws.api.pipe;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.xml.internal.ws.api.message.Packet;

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

    public Engine(String id, Executor threadPool) {
        this(id);
        this.threadPool = threadPool;
    }

    public Engine(String id) {
        this.id = id;
    }

    public void setExecutor(Executor threadPool) {
        this.threadPool = threadPool;
    }

    void addRunnable(Fiber fiber) {
        if(threadPool==null) {
            synchronized(this) {
                threadPool = Executors.newFixedThreadPool(5, new DaemonThreadFactory());
            }
        }
        threadPool.execute(fiber);
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
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        public Thread newThread(Runnable r) {
            Thread daemonThread = new Thread(r, "JAXWS-Engine-"+threadNumber.getAndIncrement());
            daemonThread.setDaemon(Boolean.TRUE);
            return daemonThread;
        }
    }
}
