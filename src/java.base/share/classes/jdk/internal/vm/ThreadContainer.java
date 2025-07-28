/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Objects;
import java.util.stream.Stream;

/**
 * A container of threads.
 */
public abstract class ThreadContainer extends StackableScope {

    /**
     * Creates a ThreadContainer.
     * @param shared true for a shared container, false for a container
     * owned by the current thread
     */
    protected ThreadContainer(boolean shared) {
        super(shared);
    }

    /**
     * Return the name of this container, may be null.
     */
    public String name() {
        return null;
    }

    /**
     * Returns the parent of this container or null if this is the root container.
     */
    public ThreadContainer parent() {
        return ThreadContainers.parent(this);
    }

    /**
     * Return the stream of children of this container.
     */
    public final Stream<ThreadContainer> children() {
        return ThreadContainers.children(this);
    }

    /**
     * Return a count of the number of threads in this container.
     */
    public long threadCount() {
        return threads().mapToLong(e -> 1L).sum();
    }

    /**
     * Returns a stream of the live threads in this container.
     */
    public abstract Stream<Thread> threads();

    /**
     * Invoked by {@code add} to add a thread to this container before it starts.
     */
    protected void onStart(Thread thread) {
    }

    /**
     * Invoked by {@code remove} to remove a thread from this container when it
     * terminates (or failed to start).
     */
    protected void onExit(Thread thread) {
    }

    /**
     * Adds a thread to this container. This method should be invoked before the
     * thread executes.
     */
    public final void add(Thread thread) {
        // Prevent a virtual thread from being preempted as this could potentially
        // deadlock when scheduled to continue and all carriers are blocked adding
        // or removing virtual threads.
        ContinuationSupport.pinIfSupported();
        try {
            onStart(thread);
        } finally {
            ContinuationSupport.unpinIfSupported();
        }
    }

    /**
     * Remove a thread from this container. This method can be invoked by the thread
     * itself as it terminates, or it can be invoked by another thread after the given
     * thread has terminated (or failed to start).
     */
    public final void remove(Thread thread) {
        // Prevent a virtual thread from being preempted as this could potentially
        // deadlock when scheduled to continue and all carriers are blocked adding
        // or removing virtual threads.
        ContinuationSupport.pinIfSupported();
        try {
            onExit(thread);
        } finally {
            ContinuationSupport.unpinIfSupported();
        }
    }

    /**
     * The scoped values captured when the thread container was created.
     */
    public ScopedValueContainer.BindingsSnapshot scopedValueBindings() {
        return null;
    }

    @Override
    public String toString() {
        String name = name();
        if (name != null && name.indexOf('@') >= 0) {
            return name;
        } else {
            String id = Objects.toIdentityString(this);
            return (name != null) ? name + "/" + id : id;
        }
    }
}
