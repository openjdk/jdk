/*
 *  Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.  Oracle designates this
 *  particular file as subject to the "Classpath" exception as provided
 *  by Oracle in the LICENSE file that accompanied this code.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

package jdk.internal.foreign;

import java.lang.foreign.MemorySession;
import java.lang.foreign.SegmentAllocator;
import java.lang.ref.Cleaner;
import java.lang.ref.Reference;
import java.util.Objects;

import jdk.internal.ref.CleanerFactory;
import jdk.internal.vm.annotation.ForceInline;

/**
 * This class manages the temporal bounds associated with a memory segment as well
 * as thread confinement. A session has a liveness bit, which is updated when the session is closed
 * (this operation is triggered by {@link MemorySession#close()}). This bit is consulted prior
 * to memory access (see {@link #checkValidState()}).
 * There are two kinds of memory session: confined memory session and shared memory session.
 * A confined memory session has an associated owner thread that confines some operations to
 * associated owner thread such as {@link #close()} or {@link #checkValidState()}.
 * Shared sessions do not feature an owner thread - meaning their operations can be called, in a racy
 * manner, by multiple threads. To guarantee temporal safety in the presence of concurrent thread,
 * shared sessions use a more sophisticated synchronization mechanism, which guarantees that no concurrent
 * access is possible when a session is being closed (see {@link jdk.internal.misc.ScopedMemoryAccess}).
 */
public non-sealed class MemorySessionImpl implements MemorySession, SegmentAllocator {

    final MemorySessionState state;

    public MemorySessionImpl(MemorySessionState state) {
        this.state = state;
    }

    public MemorySessionState state() {
        return state;
    }

    @Override
    public final boolean isAlive() {
        return state.isAlive();
    }

    @Override
    public boolean isCloseable() {
        return true;
    }

    @Override
    public final Thread ownerThread() {
        return state.ownerThread();
    }

    @Override
    public final void whileAlive(Runnable action) {
        Objects.requireNonNull(action);
        state.acquire();
        try {
            action.run();
        } finally {
            state.release();
        }
    }

    @Override
    public void addCloseAction(Runnable runnable) {
        Objects.requireNonNull(runnable);
        state.checkValidStateWrapException();
        state.resourceList.add(MemorySessionState.ResourceList.ResourceCleanup.ofRunnable(runnable));
    }

    @Override
    public void close() {
        if (isCloseable()) {
            state.close();
        } else {
            throw new UnsupportedOperationException("Cannot close!");
        }
    }

    @Override
    public final MemorySession asNonCloseable() {
        return new MemorySessionImpl(state) {
            @Override
            public boolean isCloseable() {
                return false;
            }
        };
    }

    @ForceInline
    public static void checkValidState(MemorySession session) {
        ((MemorySessionImpl)session).state.checkValidStateWrapException();
    }

    public static void addOrCleanupIfFail(MemorySession session, MemorySessionState.ResourceList.ResourceCleanup cleanup) {
        try {
            session.addCloseAction(cleanup);
        } catch (Throwable ex) {
            cleanup.cleanup();
            throw ex;
        }
    }

    @Override
    public final boolean equals(Object obj) {
        return obj instanceof MemorySessionImpl sessionImpl &&
                sessionImpl.state == state;
    }

    @Override
    public final int hashCode() {
        return state.hashCode();
    }

    public final static MemorySessionImpl GLOBAL = new ImplicitSession(null);

    public final static MemorySessionImpl heapSession(Object o) {
        return new ImplicitSession(o);
    }

    public final static MemorySessionImpl createConfined(Thread thread, Cleaner cleaner) {
        return new MemorySessionImpl(new ConfinedSessionState(thread, cleaner));
    }

    public final static MemorySessionImpl createShared(Cleaner cleaner) {
        return new MemorySessionImpl(new SharedSessionState(cleaner));
    }

    public final static MemorySessionImpl createImplicit() {
        return new ImplicitSession(null);
    }

    static class ImplicitSession extends MemorySessionImpl {

        final Object ref;

        public ImplicitSession(Object ref) {
            super(new ImplicitSessionState());
            this.ref = ref;
        }

        @Override
        public void addCloseAction(Runnable runnable) {
            Objects.requireNonNull(runnable);
            // do nothing
        }

        @Override
        public boolean isCloseable() {
            return false;
        }

        static class ImplicitSessionState extends SharedSessionState {
            public ImplicitSessionState() {
                super(CleanerFactory.cleaner());
            }

            @Override
            public void checkValidState() {
                // do nothing
            }

            @Override
            public void acquire() {
                // do nothing
            }

            @Override
            public void release() {
                Reference.reachabilityFence(this);
            }
        }
    }
}
