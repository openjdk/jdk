/*
 *  Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.vm.annotation.ForceInline;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.StampedLock;

/**
 * This class manages the temporal bounds associated with a memory segment as well
 * as thread confinement.
 * A scope has a liveness bit, which is updated when the scope is closed
 * (this operation is triggered by {@link AbstractMemorySegmentImpl#close()}).
 * A scope may also have an associated "owner" thread that confines some operations to
 * associated owner thread such as {@link #close()} or {@link #dup(Thread)}.
 * Furthermore, a scope is either root scope ({@link #create(Object, Runnable) created}
 * when memory segment is allocated) or child scope ({@link #acquire() acquired} from root scope).
 * When a child scope is acquired from another child scope, it is actually acquired from
 * the root scope. There is only a single level of children. All child scopes are peers.
 * A child scope can be {@link #close() closed} at any time, but root scope can only
 * be closed after all its children have been closed, at which time any associated
 * cleanup action is executed (the associated memory segment is freed).
 * Besides thread-confined checked scopes, {@linkplain #createUnchecked(Thread, Object, Runnable)}
 * method may be used passing {@code null} as the "owner" thread to create a
 * scope that doesn't check for thread-confinement while its temporal bounds are
 * enforced reliably only under condition that thread that closes the scope is also
 * the single thread performing the checked access or there is an external synchronization
 * in place that prevents concurrent access and closing of the scope.
 */
abstract class MemoryScope {

    /**
     * Creates a root MemoryScope with given ref, cleanupAction and current
     * thread as the "owner" thread.
     * This method may be called in any thread.
     * The returned instance may be published unsafely to and used in any thread,
     * but methods that explicitly state that they may only be called in "owner" thread,
     * must strictly be called in the thread that created the scope
     * or else IllegalStateException is thrown.
     *
     * @param ref           an optional reference to an instance that needs to be kept reachable
     * @param cleanupAction an optional cleanup action to be executed when returned scope is closed
     * @return a root MemoryScope
     */
    static MemoryScope create(Object ref, Runnable cleanupAction) {
        return new Root(Thread.currentThread(), ref, cleanupAction);
    }

    /**
     * Creates a root MemoryScope with given ref, cleanupAction and "owner" thread.
     * This method may be called in any thread.
     * The returned instance may be published unsafely to and used in any thread,
     * but methods that explicitly state that they may only be called in "owner" thread,
     * must strictly be called in given owner thread or else IllegalStateException is thrown.
     * If given owner thread is null, the returned MemoryScope is unchecked, meaning
     * that all methods may be called in any thread and that {@link #checkValidState()}
     * does not check for temporal bounds.
     *
     * @param owner         the desired owner thread. If {@code owner == null},
     *                      the returned scope is <em>not</em> thread-confined and not checked.
     * @param ref           an optional reference to an instance that needs to be kept reachable
     * @param cleanupAction an optional cleanup action to be executed when returned scope is closed
     * @return a root MemoryScope
     */
    static MemoryScope createUnchecked(Thread owner, Object ref, Runnable cleanupAction) {
        return new Root(owner, ref, cleanupAction);
    }

    private final Thread owner;
    private boolean closed; // = false
    private static final VarHandle CLOSED;

    static {
        try {
            CLOSED = MethodHandles.lookup().findVarHandle(MemoryScope.class, "closed", boolean.class);
        } catch (Throwable ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private MemoryScope(Thread owner) {
        this.owner = owner;
    }

    /**
     * Acquires a child scope (or peer scope if this is a child) with current
     * thread as the "owner" thread.
     * This method may be called in any thread.
     * The returned instance may be published unsafely to and used in any thread,
     * but methods that explicitly state that they may only be called in "owner" thread,
     * must strictly be called in the thread that acquired the scope
     * or else IllegalStateException is thrown.
     *
     * @return a child (or peer) scope
     * @throws IllegalStateException if root scope is already closed
     */
    abstract MemoryScope acquire();

    /**
     * Closes this scope, executing any cleanup action if this is the root scope.
     * This method may only be called in the "owner" thread of this scope unless the
     * scope is a root scope with no owner thread - i.e. is not checked.
     *
     * @throws IllegalStateException if this scope is already closed or if this is
     *                               a root scope and there is/are still active child
     *                               scope(s) or if this method is called outside of
     *                               owner thread in checked scope
     */
    abstract void close();

    /**
     * Duplicates this scope with given new "owner" thread and {@link #close() closes} it.
     * If this is a root scope, a new root scope is returned; this root scope is closed, but
     * without executing the cleanup action, which is instead transferred to the duped scope.
     * If this is a child scope, a new child scope is returned.
     * This method may only be called in the "owner" thread of this scope unless the
     * scope is a root scope with no owner thread - i.e. is not checked.
     * The returned instance may be published unsafely to and used in any thread,
     * but methods that explicitly state that they may only be called in "owner" thread,
     * must strictly be called in given new "owner" thread
     * or else IllegalStateException is thrown.
     *
     * @param newOwner new owner thread of the returned MemoryScope
     * @return a duplicate of this scope
     * @throws NullPointerException  if given owner thread is null
     * @throws IllegalStateException if this scope is already closed or if this is
     *                               a root scope and there is/are still active child
     *                               scope(s) or if this method is called outside of
     *                               owner thread in checked scope
     */
    abstract MemoryScope dup(Thread newOwner);

    /**
     * Returns "owner" thread of this scope.
     *
     * @return owner thread (or null for unchecked scope)
     */
    final Thread ownerThread() {
        return owner;
    }

    /**
     * This method may be called in any thread.
     *
     * @return {@code true} if this scope is not closed yet.
     */
    final boolean isAlive() {
        return !((boolean)CLOSED.getVolatile(this));
    }

    /**
     * Checks that this scope is still alive and that this method is executed
     * in the "owner" thread of this scope or this scope is unchecked (not associated
     * with owner thread).
     *
     * @throws IllegalStateException if this scope is already closed or this
     *                               method is executed outside owning thread
     *                               in checked scope
     */
    @ForceInline
    final void checkValidState() {
        if (owner != null && owner != Thread.currentThread()) {
            throw new IllegalStateException("Attempted access outside owning thread");
        }
        checkAliveConfined(this);
    }

    /**
     * Checks that this scope is still alive.
     *
     * @throws IllegalStateException if this scope is already closed
     */
    @ForceInline
    private static void checkAliveConfined(MemoryScope scope) {
        if (scope.closed) {
            throw new IllegalStateException("This segment is already closed");
        }
    }

    private static final class Root extends MemoryScope {
        private final StampedLock lock = new StampedLock();
        private final LongAdder acquired = new LongAdder();
        private final Object ref;
        private final Runnable cleanupAction;

        private Root(Thread owner, Object ref, Runnable cleanupAction) {
            super(owner);
            this.ref = ref;
            this.cleanupAction = cleanupAction;
        }

        @Override
        MemoryScope acquire() {
            // try to optimistically acquire the lock
            long stamp = lock.tryOptimisticRead();
            try {
                for (; ; stamp = lock.readLock()) {
                    if (stamp == 0L)
                        continue;
                    checkAliveConfined(this); // plain read is enough here (either successful optimistic read, or full read lock)

                    // increment acquires
                    acquired.increment();
                    // did a call to close() occur since we acquired the lock?
                    if (lock.validate(stamp)) {
                        // no, just return the acquired scope
                        return new Child(Thread.currentThread());
                    } else {
                        // yes, just back off and retry (close might have failed, after all)
                        acquired.decrement();
                    }
                }
            } finally {
                if (StampedLock.isReadLockStamp(stamp))
                    lock.unlockRead(stamp);
            }
        }

        @Override
        MemoryScope dup(Thread newOwner) {
            Objects.requireNonNull(newOwner, "newOwner");
            // pre-allocate duped scope so we don't get OOME later and be left with this scope closed
            var duped = new Root(newOwner, ref, cleanupAction);
            justClose();
            return duped;
        }

        @Override
        void close() {
            justClose();
            if (cleanupAction != null) {
                cleanupAction.run();
            }
        }

        @ForceInline
        private void justClose() {
            // enter critical section - no acquires are possible past this point
            long stamp = lock.writeLock();
            try {
                checkValidState(); // plain read is enough here (full write lock)
                // check for absence of active acquired children
                if (acquired.sum() > 0) {
                    throw new IllegalStateException("Cannot close this scope as it has active acquired children");
                }
                // now that we made sure there's no active acquired children, we can mark scope as closed
                CLOSED.set(this, true); // plain write is enough here (full write lock)
            } finally {
                // leave critical section
                lock.unlockWrite(stamp);
            }
        }

        private final class Child extends MemoryScope {

            private Child(Thread owner) {
                super(owner);
            }

            @Override
            MemoryScope acquire() {
                return Root.this.acquire();
            }

            @Override
            MemoryScope dup(Thread newOwner) {
                checkValidState(); // child scope is always checked
                // pre-allocate duped scope so we don't get OOME later and be left with this scope closed
                var duped = new Child(newOwner);
                CLOSED.setVolatile(this, true);
                return duped;
            }

            @Override
            void close() {
                checkValidState(); // child scope is always checked
                CLOSED.set(this, true);
                // following acts as a volatile write after plain write above so
                // plain write gets flushed too (which is important for isAliveThreadSafe())
                Root.this.acquired.decrement();
            }
        }
    }
}
