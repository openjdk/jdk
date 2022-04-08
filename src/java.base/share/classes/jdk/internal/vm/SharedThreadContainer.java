/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.util.function.Supplier;
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
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            CLOSED = l.findVarHandle(SharedThreadContainer.class, "closed", boolean.class);
        } catch (Exception e) {
            throw new InternalError(e);
        }
    }

    private final ThreadContainer parent;
    private final String name;

    // thread count, null if not tracking
    private final LongAdder threadCount;

    // the virtual threads in the container, null if not tracking
    private final Set<Thread> virtualThreads;

    // supplier of threads, can be set lazily.
    private volatile Supplier<Stream<Thread>> threadsSupplier;

    private volatile Object key;
    private volatile boolean closed;

    /**
     * Initialize a new SharedThreadContainer.
     * @param parent the (unowned) parent
     * @param name the container name, can be null
     * @param trackThreads true to track threads
     */
    private SharedThreadContainer(ThreadContainer parent, String name, boolean trackThreads) {
        super(true);
        this.parent = parent;
        this.name = name;
        if (trackThreads) {
            this.threadCount = new LongAdder();
            this.virtualThreads = ConcurrentHashMap.newKeySet();
        } else {
            this.threadCount = null;
            this.virtualThreads = null;
        }
    }

    private static SharedThreadContainer create(ThreadContainer parent,
                                                String name,
                                                boolean trackThreads) {
        var container = new SharedThreadContainer(parent, name, trackThreads);
        container.key = ThreadContainers.registerContainer(container);
        return container;
    }

    /**
     * Creates a shared thread container with the given parent and name.
     * @throws IllegalArgumentException if the parent has an owner.
     */
    public static SharedThreadContainer create(ThreadContainer parent, String name) {
        if (parent.owner() != null)
            throw new IllegalArgumentException("parent has owner");
        return create(parent, name, true);
    }

    /**
     * Creates a shared thread container with the given name. Its parent will be
     * the root thread container.
     */
    public static SharedThreadContainer create(String name) {
        return create(ThreadContainers.root(), name);
    }

    /**
     * Creates a shared thread container with the given name. Its parent will be
     * the root thread container. The container optionally tracks threads.
     */
    public static SharedThreadContainer create(String name, boolean trackThreads) {
        return create(ThreadContainers.root(), name, trackThreads);
    }

    @Override
    public ThreadContainer parent() {
        return parent;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Thread owner() {
        return null;
    }

    @Override
    public void onStart(Thread thread) {
        if (virtualThreads != null && thread.isVirtual())
            virtualThreads.add(thread);
        if (threadCount != null)
            threadCount.add(1L);
    }

    @Override
    public void onExit(Thread thread) {
        if (threadCount != null)
            threadCount.add(-1L);
        if (virtualThreads != null && thread.isVirtual())
            virtualThreads.remove(thread);
    }

    @Override
    public long threadCount() {
        if (threadCount != null) {
            return threadCount.sum();
        } else {
            return threads().mapToLong(e -> 1L).sum();
        }
    }

    /**
     * Sets the object that enumerates the threads in the container.
     */
    public void threadsSupplier(Supplier<Stream<Thread>> threadsSupplier) {
        this.threadsSupplier = Objects.requireNonNull(threadsSupplier);
    }

    @Override
    public Stream<Thread> threads() {
        Supplier<Stream<Thread>> threadsSupplier = this.threadsSupplier;
        if (threadsSupplier != null) {
            return threadsSupplier.get();
        } else {
            Stream<Thread> platformThreads = Stream.of(JLA.getAllThreads())
                    .filter(t -> JLA.threadContainer(t) == this);
            if (virtualThreads == null) {
                return platformThreads;
            } else {
                return Stream.concat(platformThreads, virtualThreads.stream());
            }
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
        String name = name();
        if (name != null) {
            return name + "/" + id;
        } else {
            return id;
        }
    }
}
