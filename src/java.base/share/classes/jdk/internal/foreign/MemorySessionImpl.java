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
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.ref.Cleaner;
import java.lang.ref.Reference;
import java.util.Objects;

import jdk.internal.misc.ScopedMemoryAccess;
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

    final State state;

    public MemorySessionImpl(State state) {
        this.state = state;
    }

    public State state() {
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
        state.resourceList.add(State.ResourceCleanup.ofRunnable(runnable));
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
        if (!isCloseable()) return this;
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

    public static void addOrCleanupIfFail(MemorySession session, State.ResourceCleanup cleanup) {
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

    public final static MemorySessionImpl GLOBAL = new MemorySessionImpl(new SharedSessionState.OfImplicit()) {

        @Override
        public void addCloseAction(Runnable runnable) {
            Objects.requireNonNull(runnable);
            // do nothing
        }

        @Override
        public boolean isCloseable() {
            return false;
        }
    };

    public final static MemorySessionImpl heapSession(Object o) {
        return createImplicit(o);
    }

    public final static MemorySessionImpl createConfined(Thread thread, Cleaner cleaner) {
        return new MemorySessionImpl(new ConfinedSessionState(thread, cleaner));
    }

    public final static MemorySessionImpl createShared(Cleaner cleaner) {
        return new MemorySessionImpl(new SharedSessionState(cleaner));
    }

    public final static MemorySessionImpl createImplicit(Object ref) {
        return new MemorySessionImpl(new SharedSessionState.OfImplicit()) {
            final Object o = ref;

            @Override
            public boolean isCloseable() {
                return false;
            }
        };
    }

    public abstract static class State {

        static final int OPEN = 0;
        static final int CLOSING = -1;
        static final int CLOSED = -2;

        int state = OPEN;

        static final VarHandle STATE;

        static {
            try {
                STATE = MethodHandles.lookup().findVarHandle(State.class, "state", int.class);
            } catch (Throwable ex) {
                throw new ExceptionInInitializerError(ex);
            }
        }

        static final int MAX_FORKS = Integer.MAX_VALUE;

        final Cleaner.Cleanable cleanable;
        final ResourceList resourceList;

        State(ResourceList resourceList, Cleaner cleaner) {
            this.resourceList = resourceList;
            cleanable = cleaner != null ?
                    cleaner.register(this, resourceList) :
                    null;
        }

        /**
         * Closes this session, executing any cleanup action (where provided).
         *
         * @throws IllegalStateException if this session is already closed or if this is
         *                               a confined session and this method is called outside of the owner thread.
         */
        public final void close() {
            try {
                justClose();
                if (cleanable != null) {
                    cleanable.clean();
                } else {
                    resourceList.cleanup();
                }
            } finally {
                Reference.reachabilityFence(this);
            }
        }

        abstract void justClose();

        public final void checkValidStateWrapException() {
            try {
                checkValidState();
            } catch (ScopedMemoryAccess.ScopedAccessError ex) {
                throw ex.newRuntimeException();
            }
        }

        public boolean isImplicit() {
            return false;
        }

        public abstract boolean isAlive();

        public abstract Thread ownerThread();

        @ForceInline
        public void checkValidState() {
            if (ownerThread() != null && ownerThread() != Thread.currentThread()) {
                throw WRONG_THREAD;
            } else if (state < OPEN) {
                throw ALREADY_CLOSED;
            }
        }

        public abstract void acquire();

        public abstract void release();

        static IllegalStateException tooManyAcquires() {
            return new IllegalStateException("Session acquire limit exceeded");
        }

        static IllegalStateException alreadyAcquired(int acquires) {
            return new IllegalStateException(String.format("Session is acquired by %d clients", acquires));
        }

        static IllegalStateException alreadyClosed() {
            return new IllegalStateException("Already closed");
        }

        static WrongThreadException wrongThread() {
            return new WrongThreadException("Attempted access outside owning thread");
        }

        static final ScopedMemoryAccess.ScopedAccessError ALREADY_CLOSED = new ScopedMemoryAccess.ScopedAccessError(State::alreadyClosed);

        static final ScopedMemoryAccess.ScopedAccessError WRONG_THREAD = new ScopedMemoryAccess.ScopedAccessError(State::wrongThread);

        /**
         * A list of all cleanup actions associated with a memory session. Cleanup actions are modelled as instances
         * of the {@link ResourceCleanup} class, and, together, form a linked list. Depending on whether a session
         * is shared or confined, different implementations of this class will be used, see {@link ConfinedSessionState.ConfinedList}
         * and {@link SharedSessionState.SharedList}.
         */
        public abstract static class ResourceList implements Runnable {
            ResourceCleanup fst;

            public abstract void add(ResourceCleanup cleanup);

            abstract void cleanup();

            public final void run() {
                cleanup(); // cleaner interop
            }

            static void cleanup(ResourceCleanup first) {
                ResourceCleanup current = first;
                while (current != null) {
                    current.cleanup();
                    current = current.next;
                }
            }
        }

        public abstract static class ResourceCleanup implements Runnable {
            ResourceCleanup next;

            public abstract void cleanup();

            public final void run() {
                cleanup();
            }

            static final ResourceCleanup CLOSED_LIST = new ResourceCleanup() {
                @Override
                public void cleanup() {
                    throw new IllegalStateException("This resource list has already been closed!");
                }
            };

            public static ResourceCleanup ofRunnable(Runnable cleanupAction) {
                return cleanupAction instanceof ResourceCleanup ?
                        (ResourceCleanup)cleanupAction :
                        new ResourceCleanup() {
                            @Override
                            public void cleanup() {
                                cleanupAction.run();
                            }
                        };
            }
        }
    }
}
