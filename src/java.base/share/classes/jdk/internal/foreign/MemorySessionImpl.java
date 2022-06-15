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
import java.nio.ByteBuffer;
import java.util.Objects;

import jdk.internal.misc.ScopedMemoryAccess;
import jdk.internal.vm.annotation.ForceInline;

/**
 * This class manages the temporal bounds associated with a memory segment as well
 * as thread confinement. A session is associated with a {@link State#state liveness bit},
 * which is updated when the session is closed (this operation is triggered by {@link MemorySession#close()}).
 * This bit is consulted prior to memory access (see {@link State#checkValidState()}).
 * <p>
 * Since the API allows the creation of non-closeable session views, the implementation of this class encapsulates
 * the state of a memory session into a separate class, namely {@link State}. This allows to create views that are
 * backed by the very same state.
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

    @Override
    public final boolean equals(Object obj) {
        return obj instanceof MemorySessionImpl sessionImpl &&
                sessionImpl.state == state;
    }

    @Override
    public final int hashCode() {
        return state.hashCode();
    }

    // helper functions

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

    /** The global memory session */
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

    // session factories

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

    /**
     * This class is used to model the state of a memory session. It contains a {@link State#state liveness bit},
     * which can also be used to implement reference counting. There are two three main kinds of session states:
     * {@linkplain ConfinedSessionState confined state}, {@link SharedSessionState shared state} and
     * {@link SharedSessionState.OfImplicit implicit state}, each of which is implemented in its own subclass.
     * Different kinds of session state implementations feature different performance characteristics: for instance
     * closing a confined session state is much cheaper than closing a shared session state.
     * <p>
     * Memory session state support reference counting: the state can be acquired and released; when the state
     * is in the acquired state, it cannot be closed until it is released. This is useful to make sure that
     * memory sessions cannot be closed prematurely, e.g. while a native function call is executing,
     * or while the segment is manipulated by some asynchronous IO operation, like
     * {@link java.nio.channels.AsynchronousSocketChannel#read(ByteBuffer)}.
     */
    public abstract static class State {

        static final int OPEN = 0;
        static final int CLOSING = -1;
        static final int CLOSED = -2;

        int state = OPEN;
        final Thread owner;

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

        State(Thread owner, ResourceList resourceList, Cleaner cleaner) {
            this.resourceList = resourceList;
            this.owner = owner;
            cleanable = cleaner != null ?
                    cleaner.register(this, resourceList) :
                    null;
        }

        final void close() {
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

        abstract boolean isAlive();

        public final Thread ownerThread() {
            return owner;
        }

        /*
         * This is the liveness check used by classes such as ScopedMemoryAccess. To allow for better inlining,
         * this method is kept monomorphic. Note that we cannot create exceptions while executing this method,
         * as doing so will end up creating a deep stack trace; this interferes with the algorithm we use
         * to make sure that no other thread is accessing a shared memory session while the session is closed.
         * Note also that this routine performs only plain access checks: this is by design, see comments
         * in ScopedMemoryAccess.
         */
        @ForceInline
        public final void checkValidState() {
            if (owner != null && owner != Thread.currentThread()) {
                throw WRONG_THREAD;
            } else if (state < OPEN) {
                throw ALREADY_CLOSED;
            }
        }

        public abstract void acquire();

        public abstract void release();

        // helper functions to centralize error handling

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
         * A list of all cleanup actions associated with a memory session state. Cleanup actions are modelled as instances
         * of the {@link ResourceCleanup} class, and, together, form a linked list. Depending on whether a session
         * is shared or confined, different implementations of this class will be used, see {@link ConfinedSessionState.ConfinedList}
         * and {@link SharedSessionState.SharedList}.
         */
        abstract static class ResourceList implements Runnable {
            ResourceCleanup fst;

            abstract void add(ResourceCleanup cleanup);

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

        /**
         * This class is used to model a resource that can be managed by a memory session. It features
         * a method that can be used to cleanup the resource (this method is typically called when
         * the memory session is closed).
         */
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

            static ResourceCleanup ofRunnable(Runnable cleanupAction) {
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
