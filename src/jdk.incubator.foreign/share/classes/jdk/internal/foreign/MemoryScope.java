/*
 *  Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.misc.ScopedMemoryAccess;
import jdk.internal.ref.PhantomCleanable;
import jdk.internal.vm.annotation.ForceInline;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.ref.Cleaner;
import java.lang.ref.Reference;
import java.util.Objects;

/**
 * This class manages the temporal bounds associated with a memory segment as well
 * as thread confinement. A scope has a liveness bit, which is updated when the scope is closed
 * (this operation is triggered by {@link AbstractMemorySegmentImpl#close()}). This bit is consulted prior
 * to memory access (see {@link #checkValidState()}).
 * There are two kinds of memory scope: confined memory scope and shared memory scope.
 * A confined memory scope has an associated owner thread that confines some operations to
 * associated owner thread such as {@link #close()} or {@link #checkValidState()}.
 * Shared scopes do not feature an owner thread - meaning their operations can be called, in a racy
 * manner, by multiple threads. To guarantee temporal safety in the presence of concurrent thread,
 * shared scopes use a more sophisticated synchronization mechanism, which guarantees that no concurrent
 * access is possible when a scope is being closed (see {@link jdk.internal.misc.ScopedMemoryAccess}).
 */
abstract class MemoryScope implements ScopedMemoryAccess.Scope {

    static final Runnable DUMMY_CLEANUP_ACTION = () -> { };

    private MemoryScope(Object ref, Runnable cleanupAction, Cleaner cleaner) {
        Objects.requireNonNull(cleanupAction);
        this.ref = ref;
        this.cleanupAction = cleanupAction;
        this.scopeCleanable = cleaner != null ?
                new ScopeCleanable(this, cleaner, cleanupAction) :
                null;
    }

    /**
     * Creates a confined memory scope with given attachment and cleanup action. The returned scope
     * is assumed to be confined on the current thread.
     * @param ref           an optional reference to an instance that needs to be kept reachable
     * @param cleanupAction a cleanup action to be executed when returned scope is closed
     * @return a confined memory scope
     */
    static MemoryScope createConfined(Object ref, Runnable cleanupAction, Cleaner cleaner) {
        return new ConfinedScope(Thread.currentThread(), ref, cleanupAction, cleaner);
    }

    /**
     * Creates a shared memory scope with given attachment and cleanup action.
     * @param ref           an optional reference to an instance that needs to be kept reachable
     * @param cleanupAction a cleanup action to be executed when returned scope is closed
     * @return a shared memory scope
     */
    static MemoryScope createShared(Object ref, Runnable cleanupAction, Cleaner cleaner) {
        return new SharedScope(ref, cleanupAction, cleaner);
    }

    protected final Object ref;
    protected final ScopeCleanable scopeCleanable;
    protected final Runnable cleanupAction;

    /**
     * Closes this scope, executing any cleanup action (where provided).
     * @throws IllegalStateException if this scope is already closed or if this is
     * a confined scope and this method is called outside of the owner thread.
     */
    final void close() {
        try {
            justClose();
            cleanupAction.run();
            if (scopeCleanable != null) {
                scopeCleanable.clear();
            }
        } finally {
            Reference.reachabilityFence(this);
        }
    }

    abstract void justClose();

    /**
     * Duplicates this scope with given new "owner" thread and {@link #close() closes} it.
     * @param newOwner new owner thread of the returned memory scope
     * @return a new confined scope, which is a duplicate of this scope, but with a new owner thread.
     * @throws IllegalStateException if this scope is already closed or if this is
     * a confined scope and this method is called outside of the owner thread.
     */
    final MemoryScope confineTo(Thread newOwner) {
        try {
            justClose();
            if (scopeCleanable != null) {
                scopeCleanable.clear();
            }
            return new ConfinedScope(newOwner, ref, cleanupAction, scopeCleanable != null ?
                    scopeCleanable.cleaner : null);
        } finally {
            Reference.reachabilityFence(this);
        }
    }

    /**
     * Duplicates this scope with given new "owner" thread and {@link #close() closes} it.
     * @return a new shared scope, which is a duplicate of this scope.
     * @throws IllegalStateException if this scope is already closed or if this is
     * a confined scope and this method is called outside of the owner thread,
     * or if this is already a shared scope.
     */
    final MemoryScope share() {
        try {
            justClose();
            if (scopeCleanable != null) {
                scopeCleanable.clear();
            }
            return new SharedScope(ref, cleanupAction, scopeCleanable != null ?
                    scopeCleanable.cleaner : null);
        } finally {
            Reference.reachabilityFence(this);
        }
    }

    final MemoryScope cleanable(Cleaner cleaner) {
        if (scopeCleanable != null) {
            throw new IllegalStateException("Already registered with a cleaner");
        }
        try {
            justClose();
            return ownerThread() == null ?
                    new SharedScope(ref, cleanupAction, cleaner) :
                    new ConfinedScope(ownerThread(), ref, cleanupAction, cleaner);
        } finally {
            Reference.reachabilityFence(this);
        }
    }

    /**
     * Returns "owner" thread of this scope.
     * @return owner thread (or null for a shared scope)
     */
    public abstract Thread ownerThread();

    /**
     * Returns true, if this scope is still alive. This method may be called in any thread.
     * @return {@code true} if this scope is not closed yet.
     */
    public abstract boolean isAlive();

    /**
     * Checks that this scope is still alive (see {@link #isAlive()}).
     * @throws IllegalStateException if this scope is already closed or if this is
     * a confined scope and this method is called outside of the owner thread.
     */
    public abstract void checkValidState();

    @Override
    protected Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    /**
     * A confined scope, which features an owner thread. The liveness check features an additional
     * confinement check - that is, calling any operation on this scope from a thread other than the
     * owner thread will result in an exception. Because of this restriction, checking the liveness bit
     * can be performed in plain mode (see {@link #checkAliveRaw(MemoryScope)}).
     */
    static class ConfinedScope extends MemoryScope {

        private boolean closed; // = false
        final Thread owner;

        public ConfinedScope(Thread owner, Object ref, Runnable cleanupAction, Cleaner cleaner) {
            super(ref, cleanupAction, cleaner);
            this.owner = owner;
        }

        @ForceInline
        public final void checkValidState() {
            if (owner != Thread.currentThread()) {
                throw new IllegalStateException("Attempted access outside owning thread");
            }
            if (closed) {
                throw ScopedAccessError.INSTANCE;
            }
        }

        @Override
        public boolean isAlive() {
            return !closed;
        }

        void justClose() {
            checkValidState();
            closed = true;
        }

        @Override
        public Thread ownerThread() {
            return owner;
        }
    }

    /**
     * A shared scope, which can be shared across multiple threads. Closing a shared scope has to ensure that
     * (i) only one thread can successfully close a scope (e.g. in a close vs. close race) and that
     * (ii) no other thread is accessing the memory associated with this scope while the segment is being
     * closed. To ensure the former condition, a CAS is performed on the liveness bit. Ensuring the latter
     * is trickier, and require a complex synchronization protocol (see {@link jdk.internal.misc.ScopedMemoryAccess}).
     * Since it is the responsibility of the closing thread to make sure that no concurrent access is possible,
     * checking the liveness bit upon access can be performed in plain mode (see {@link #checkAliveRaw(MemoryScope)}),
     * as in the confined case.
     */
    static class SharedScope extends MemoryScope {

        static ScopedMemoryAccess SCOPED_MEMORY_ACCESS = ScopedMemoryAccess.getScopedMemoryAccess();

        final static int ALIVE = 0;
        final static int CLOSING = 1;
        final static int CLOSED = 2;

        int state = ALIVE;

        private static final VarHandle STATE;

        static {
            try {
                STATE = MethodHandles.lookup().findVarHandle(SharedScope.class, "state", int.class);
            } catch (Throwable ex) {
                throw new ExceptionInInitializerError(ex);
            }
        }

        SharedScope(Object ref, Runnable cleanupAction, Cleaner cleaner) {
            super(ref, cleanupAction, cleaner);
        }

        @Override
        public Thread ownerThread() {
            return null;
        }

        @Override
        public void checkValidState() {
            if (state != ALIVE) {
                throw ScopedAccessError.INSTANCE;
            }
        }

        void justClose() {
            if (!STATE.compareAndSet(this, ALIVE, CLOSING)) {
                throw new IllegalStateException("Already closed");
            }
            boolean success = SCOPED_MEMORY_ACCESS.closeScope(this);
            STATE.setVolatile(this, success ? CLOSED : ALIVE);
            if (!success) {
                throw new IllegalStateException("Cannot close while another thread is accessing the segment");
            }
        }

        @Override
        public boolean isAlive() {
            return (int)STATE.getVolatile(this) != CLOSED;
        }
    }

    static class ScopeCleanable extends PhantomCleanable<MemoryScope> {
        final Cleaner cleaner;
        final Runnable cleanupAction;

        public ScopeCleanable(MemoryScope referent, Cleaner cleaner, Runnable cleanupAction) {
            super(referent, cleaner);
            this.cleaner = cleaner;
            this.cleanupAction = cleanupAction;
        }

        @Override
        protected void performCleanup() {
            cleanupAction.run();
        }
    }
}
