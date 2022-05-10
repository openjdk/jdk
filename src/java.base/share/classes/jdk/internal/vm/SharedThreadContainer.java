/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.vm;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Stream;
import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;

/**
 * A "shared" thread container. A shared thread container doesn't have an owner
 * and is intended for unstructured uses, e.g. thread pools.
 */
public class SharedThreadContainer extends ThreadContainer implements AutoCloseable {
    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();
    private static final VarHandle CLOSED;
    private static final VarHandle VIRTUAL_THREADS;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            CLOSED = l.findVarHandle(SharedThreadContainer.class,
                    "closed", boolean.class);
            VIRTUAL_THREADS = l.findVarHandle(SharedThreadContainer.class,
                    "virtualThreads", Set.class);
        } catch (Exception e) {
            throw new InternalError(e);
        }
    }

    // name of container, used by toString
    private final String name;

    // the number of threads in the container
    private final LongAdder threadCount;

    // the virtual threads in the container, created lazily
    private volatile Set<Thread> virtualThreads;

    // the key for this container in the registry
    private volatile Object key;

    // set to true when the container is closed
    private volatile boolean closed;

    /**
     * Initialize a new SharedThreadContainer.
     * @param name the container name, can be null
     */
    private SharedThreadContainer(String name) {
        super(/*shared*/ true);
        this.name = name;
        this.threadCount = new LongAdder();
    }

    /**
     * Creates a shared thread container with the given parent and name.
     * @throws IllegalArgumentException if the parent has an owner.
     */
    public static SharedThreadContainer create(ThreadContainer parent, String name) {
        if (parent.owner() != null)
            throw new IllegalArgumentException("parent has owner");
        var container = new SharedThreadContainer(name);
        // register the container to allow discovery by serviceability tools
        container.key = ThreadContainers.registerContainer(container);
        return container;
    }

    /**
     * Creates a shared thread container with the given name. Its parent will be
     * the root thread container.
     */
    public static SharedThreadContainer create(String name) {
        return create(ThreadContainers.root(), name);
    }

    @Override
    public Thread owner() {
        return null;
    }

    @Override
    public void onStart(Thread thread) {
        // virtual threads needs to be tracked
        if (thread.isVirtual()) {
            Set<Thread> vthreads = this.virtualThreads;
            if (vthreads == null) {
                vthreads = ConcurrentHashMap.newKeySet();
                if (!VIRTUAL_THREADS.compareAndSet(this, null, vthreads)) {
                    // lost the race
                    vthreads = this.virtualThreads;
                }
            }
            vthreads.add(thread);
        }
        threadCount.add(1L);
    }

    @Override
    public void onExit(Thread thread) {
        threadCount.add(-1L);
        if (thread.isVirtual())
            virtualThreads.remove(thread);
    }

    @Override
    public long threadCount() {
        return threadCount.sum();
    }

    @Override
    public Stream<Thread> threads() {
        // live platform threads in this container
        Stream<Thread> platformThreads = Stream.of(JLA.getAllThreads())
                .filter(t -> JLA.threadContainer(t) == this);
        Set<Thread> vthreads = this.virtualThreads;
        if (vthreads == null) {
            // live platform threads only, no virtual threads
            return platformThreads;
        } else {
            // all live threads in this container
            return Stream.concat(platformThreads,
                                 vthreads.stream().filter(Thread::isAlive));
        }
    }

    /**
     * Starts a thread in this container.
     * @throws IllegalStateException if the container is closed
     */
    public void start(Thread thread) {
        if (closed)
            throw new IllegalStateException();
        JLA.start(thread, this);
    }

    /**
     * Closes this container. Further attempts to start a thread in this container
     * throw IllegalStateException. This method has no impact on threads that are
     * still running or starting around the time that this method is invoked.
     */
    @Override
    public void close() {
        if (!closed && CLOSED.compareAndSet(this, false, true)) {
            ThreadContainers.deregisterContainer(key);
        }
    }

    @Override
    public String toString() {
        String id = Objects.toIdentityString(this);
        if (name != null) {
            return name + "/" + id;
        } else {
            return id;
        }
    }
}
