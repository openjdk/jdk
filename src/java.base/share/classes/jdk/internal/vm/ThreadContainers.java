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

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Stream;
import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;
import sun.nio.ch.Poller;
import sun.security.action.GetPropertyAction;

/**
 * This class consists exclusively of static methods to support debugging and
 * monitoring of threads.
 */
public class ThreadContainers {
    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();

    // the set of thread containers registered with this class
    private static final Set<WeakReference<ThreadContainer>> CONTAINER_REGISTRY = ConcurrentHashMap.newKeySet();
    private static final ReferenceQueue<Object> QUEUE = new ReferenceQueue<>();

    private ThreadContainers() { }

    /**
     * Expunge stale entries from the container registry.
     */
    private static void expungeStaleEntries() {
        Object key;
        while ((key = QUEUE.poll()) != null) {
            CONTAINER_REGISTRY.remove(key);
        }
    }

    /**
     * Registers a thread container to be tracked this class, returning a key
     * that is used to remove it from the registry.
     */
    public static Object registerContainer(ThreadContainer container) {
        expungeStaleEntries();
        var ref = new WeakReference<>(container);
        CONTAINER_REGISTRY.add(ref);
        return ref;
    }

    /**
     * Removes a thread container from being tracked by specifying the key
     * returned when the thread container was registered.
     */
    public static void deregisterContainer(Object key) {
        assert key instanceof WeakReference;
        CONTAINER_REGISTRY.remove(key);
    }

    /**
     * Returns the root thread container.
     */
    public static ThreadContainer root() {
        return RootContainer.INSTANCE;
    }

    /**
     * Returns the parent of the given thread container.
     *
     * If the container has an owner then its parent is the enclosing container when
     * nested, or the container that the owner is in, when not nested.
     *
     * If the container does not have an owner then the root container is returned,
     * or null if called with the root container.
     */
    static ThreadContainer parent(ThreadContainer container) {
        Thread owner = container.owner();
        if (owner != null) {
            ThreadContainer parent = container.enclosingScope(ThreadContainer.class);
            if (parent != null)
                return parent;
            if ((parent = ThreadContainers.container(owner)) != null)
                return parent;
        }
        ThreadContainer root = ThreadContainers.root();
        return (container != root) ? root : null;
    }

    /**
     * Returns given thread container's "children".
     */
    static Stream<ThreadContainer> children(ThreadContainer container) {
        // children of registered containers
        Stream<ThreadContainer> s1 = CONTAINER_REGISTRY.stream()
                .map(WeakReference::get)
                .filter(c -> c != null && c.parent() == container);

        // container may enclose another container
        Stream<ThreadContainer> s2 = Stream.empty();
        if (container.owner() != null) {
            ThreadContainer next = next(container);
            if (next != null)
                s2 = Stream.of(next);
        }

        // the top-most container owned by the threads in the container
        Stream<ThreadContainer> s3 = container.threads()
                .map(t -> Optional.ofNullable(top(t)))
                .flatMap(Optional::stream);

        return Stream.concat(s1, Stream.concat(s2, s3));
    }

    /**
     * Returns the thread container that the given Thread is in or the root
     * container if not started in a container.
     */
    public static ThreadContainer container(Thread thread) {
        ThreadContainer container = JLA.threadContainer(thread);
        return (container != null) ? container : root();
    }

    /**
     * Returns the top-most thread container owned by the given thread.
     */
    private static ThreadContainer top(Thread thread) {
        StackableScope current = JLA.headStackableScope(thread);
        ThreadContainer top = null;
        while (current != null) {
            if (current instanceof ThreadContainer tc) {
                top = tc;
            }
            current = current.previous();
        }
        return top;
    }

    /**
     * Returns the thread container that the given thread container encloses.
     */
    private static ThreadContainer next(ThreadContainer container) {
        StackableScope current = JLA.headStackableScope(container.owner());
        if (current != null) {
            ThreadContainer next = null;
            while (current != null) {
                if (current == container) {
                    return next;
                } else if (current instanceof ThreadContainer tc) {
                    next = tc;
                }
                current = current.previous();
            }
        }
        return null;
    }

    /**
     * Root container. Contains all platform threads that are not started in a
     * container, plus some (or all) virtual threads that are started directly
     * with the Thread API.
     */
    private static class RootContainer extends ThreadContainer {
        private static final Set<Thread> VTHREADS;
        private static final LongAdder VTHREAD_COUNT;
        static {
            String s = GetPropertyAction.privilegedGetProperty("jdk.trackAllThreads");
            if (s != null && (s.isEmpty() || Boolean.parseBoolean(s))) {
                VTHREADS = ConcurrentHashMap.newKeySet();
                VTHREAD_COUNT = null; //new LongAdder();
            } else {
                VTHREADS = null;
                VTHREAD_COUNT = new LongAdder();
            }
        }
        static final RootContainer INSTANCE = new RootContainer();

        private RootContainer() {
            super(true);
        }
        @Override
        public String name() {
            return "<root>";
        }
        @Override
        public Thread owner() {
            return null;
        }
        @Override
        public ThreadContainer parent() {
            return null;
        }
        @Override
        public long threadCount() {
            // platform threads that are not in a container
            long platformThreadCount = Stream.of(JLA.getAllThreads())
                    .filter(t -> JLA.threadContainer(t) == null)
                    .count();
            Set<Thread> vthreads = VTHREADS;
            if (vthreads != null) {
                return platformThreadCount + vthreads.size();
            } else {
                return platformThreadCount + VTHREAD_COUNT.sum();
            }
        }
        @Override
        public Stream<Thread> threads() {
            // platform threads that are not in a container
            Stream<Thread> s1 = Stream.of(JLA.getAllThreads())
                    .filter(t -> JLA.threadContainer(t) == null);
            // virtual threads in this container, all or only those blocked on I/O.
            Stream<Thread> s2;
            Set<Thread> vthreads = VTHREADS;
            if (vthreads != null) {
                s2 = vthreads.stream();
            } else {
                s2 = Poller.blockedThreads()
                        .filter(t -> t.isVirtual()
                                && JLA.threadContainer(t) == this);
            }
            return Stream.concat(s1, s2);
        }
        @Override
        public String toString() {
            return name();
        }
        @Override
        public void onStart(Thread thread) {
            assert thread.isVirtual();
            Set<Thread> vthreads = VTHREADS;
            if (vthreads != null) {
                vthreads.add(thread);
            } else {
                VTHREAD_COUNT.add(1L);
            }
        }
        @Override
        public void onExit(Thread thread) {
            assert thread.isVirtual();
            Set<Thread> vthreads = VTHREADS;
            if (vthreads != null) {
                vthreads.remove(thread);
            } else {
                VTHREAD_COUNT.add(-1L);
            }
        }
        @Override
        public StackableScope previous() {
            return null;
        }
    }
}
