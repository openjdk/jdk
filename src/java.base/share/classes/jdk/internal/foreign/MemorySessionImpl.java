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

import java.lang.foreign.MemorySegment;
import java.lang.foreign.MemorySession;
import java.lang.foreign.SegmentAllocator;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.ref.Cleaner;
import java.lang.ref.Reference;
import java.util.Objects;
import jdk.internal.misc.ScopedMemoryAccess;
import jdk.internal.ref.CleanerFactory;
import jdk.internal.vm.annotation.ForceInline;
import sun.nio.ch.DirectBuffer;

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
public abstract non-sealed class MemorySessionImpl implements Scoped, MemorySession, SegmentAllocator {
    final ResourceList resourceList;
    final Cleaner.Cleanable cleanable;
    final Thread owner;

    static final int OPEN = 0;
    static final int CLOSING = -1;
    static final int CLOSED = -2;

    int state = OPEN;

    static final VarHandle STATE;

    static {
        try {
            STATE = MethodHandles.lookup().findVarHandle(MemorySessionImpl.class, "state", int.class);
        } catch (Throwable ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    static final int MAX_FORKS = Integer.MAX_VALUE;

    @Override
    public void addCloseAction(Runnable runnable) {
        Objects.requireNonNull(runnable);
        addInternal(runnable instanceof ResourceList.ResourceCleanup cleanup ?
                cleanup : ResourceList.ResourceCleanup.ofRunnable(runnable));
    }

    /**
     * Add a cleanup action. If a failure occurred (because of a add vs. close race), call the cleanup action.
     * This semantics is useful when allocating new memory segments, since we first do a malloc/mmap and _then_
     * we register the cleanup (free/munmap) against the session; so, if registration fails, we still have to
     * cleanup memory. From the perspective of the client, such a failure would manifest as a factory
     * returning a segment that is already "closed" - which is always possible anyway (e.g. if the session
     * is closed _after_ the cleanup for the segment is registered but _before_ the factory returns the
     * new segment to the client). For this reason, it's not worth adding extra complexity to the segment
     * initialization logic here - and using an optimistic logic works well in practice.
     */
    public void addOrCleanupIfFail(ResourceList.ResourceCleanup resource) {
        try {
            addInternal(resource);
        } catch (Throwable ex) {
            resource.cleanup();
            throw ex;
        }
    }

    void addInternal(ResourceList.ResourceCleanup resource) {
        checkValidStateSlow();
        // Note: from here on we no longer check the session state. Two cases are possible: either the resource cleanup
        // is added to the list when the session is still open, in which case everything works ok; or the resource
        // cleanup is added while the session is being closed. In this latter case, what matters is whether we have already
        // called `ResourceList::cleanup` to run all the cleanup actions. If not, we can still add this resource
        // to the list (and, in case of an add vs. close race, it might happen that the cleanup action will be
        // called immediately after).
        resourceList.add(resource);
    }

    protected MemorySessionImpl(Thread owner, ResourceList resourceList, Cleaner cleaner) {
        this.owner = owner;
        this.resourceList = resourceList;
        cleanable = (cleaner != null) ?
            cleaner.register(this, resourceList) : null;
    }

    public static MemorySession createConfined(Thread thread, Cleaner cleaner) {
        return new ConfinedSession(thread, cleaner);
    }

    public static MemorySession createShared(Cleaner cleaner) {
        return new SharedSession(cleaner);
    }

    public static MemorySessionImpl createImplicit() {
        return new ImplicitSession();
    }

    @Override
    public MemorySegment allocate(long bytesSize, long bytesAlignment) {
        return MemorySegment.allocateNative(bytesSize, bytesAlignment, this);
    }

    public abstract void release0();

    public abstract void acquire0();

    @Override
    public boolean equals(Object o) {
        return (o instanceof MemorySession other) &&
            toSessionImpl(other) == this;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public void whileAlive(Runnable action) {
        Objects.requireNonNull(action);
        acquire0();
        try {
            action.run();
        } finally {
            release0();
        }
    }

    /**
     * Returns "owner" thread of this session.
     * @return owner thread (or null for a shared session)
     */
    public final Thread ownerThread() {
        return owner;
    }

    /**
     * Returns true, if this session is still open. This method may be called in any thread.
     * @return {@code true} if this session is not closed yet.
     */
    public abstract boolean isAlive();

    @Override
    public MemorySession asNonCloseable() {
        return isCloseable() ?
                new NonCloseableView(this) : this;
    }

    public static MemorySessionImpl toSessionImpl(MemorySession session) {
        return ((Scoped)session).sessionImpl();
    }

    @Override
    public MemorySessionImpl sessionImpl() {
        return this;
    }

    /**
     * This is a faster version of {@link #checkValidStateSlow()}, which is called upon memory access, and which
     * relies on invariants associated with the memory session implementations (volatile access
     * to the closed state bit is replaced with plain access). This method should be monomorphic,
     * to avoid virtual calls in the memory access hot path. This method is not intended as general purpose method
     * and should only be used in the memory access handle hot path; for liveness checks triggered by other API methods,
     * please use {@link #checkValidStateSlow()}.
     */
    @ForceInline
    public final void checkValidState() {
        if (owner != null && owner != Thread.currentThread()) {
            throw new WrongThreadException("Attempted access outside owning thread");
        }
        if (state < OPEN) {
            throw ScopedMemoryAccess.ScopedAccessError.INSTANCE;
        }
    }

    /**
     * Checks that this session is still alive (see {@link #isAlive()}).
     * @throws IllegalStateException if this session is already closed or if this is
     * a confined session and this method is called outside of the owner thread.
     */
    public final void checkValidStateSlow() {
        if (owner != null && Thread.currentThread() != owner) {
            throw new WrongThreadException("Attempted access outside owning thread");
        } else if (!isAlive()) {
            throw new IllegalStateException("Already closed");
        }
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    @Override
    public boolean isCloseable() {
        return true;
    }

    /**
     * Closes this session, executing any cleanup action (where provided).
     * @throws IllegalStateException if this session is already closed or if this is
     * a confined session and this method is called outside of the owner thread.
     */
    @Override
    public void close() {
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

    /**
     * The global, non-closeable, shared session. Similar to a shared session, but its {@link #close()} method throws unconditionally.
     * Adding new resources to the global session, does nothing: as the session can never become not-alive, there is nothing to track.
     * Acquiring and or releasing a memory session similarly does nothing.
     */
    static class GlobalSessionImpl extends MemorySessionImpl {

        final Object ref;

        public GlobalSessionImpl(Object ref) {
            super(null, null ,null);
            this.ref = ref;
        }

        @Override
        @ForceInline
        public void release0() {
            // do nothing
        }

        @Override
        public boolean isCloseable() {
            return false;
        }

        @Override
        @ForceInline
        public void acquire0() {
            // do nothing
        }

        @Override
        void addInternal(ResourceList.ResourceCleanup resource) {
            // do nothing
        }

        @Override
        public boolean isAlive() {
            return true;
        }

        @Override
        public void justClose() {
            throw new UnsupportedOperationException();
        }
    }

    public static final MemorySessionImpl GLOBAL = new GlobalSessionImpl(null);

    public static MemorySessionImpl heapSession(Object ref) {
        return new GlobalSessionImpl(ref);
    }

    /**
     * This is an implicit, GC-backed memory session. Implicit sessions cannot be closed explicitly.
     * While it would be possible to model an implicit session as a non-closeable view of a shared
     * session, it is better to capture the fact that an implicit session is not just a non-closeable
     * view of some session which might be closeable. This is useful e.g. in the implementations of
     * {@link DirectBuffer#address()}, where obtaining an address of a buffer instance associated
     * with a potentially closeable session is forbidden.
     */
    static class ImplicitSession extends SharedSession {

        public ImplicitSession() {
            super(CleanerFactory.cleaner());
        }

        @Override
        public void release0() {
            Reference.reachabilityFence(this);
        }

        @Override
        public void acquire0() {
            // do nothing
        }

        @Override
        public boolean isCloseable() {
            return false;
        }

        @Override
        public boolean isAlive() {
            return true;
        }

        @Override
        public MemorySession asNonCloseable() {
            return this;
        }

        @Override
        public void justClose() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * This is a non-closeable view of another memory session. Instances of this class are used in resource session
     * accessors (see {@link MemorySegment#session()}). This class forwards all session methods to the underlying
     * "root" session implementation, and throws {@link UnsupportedOperationException} on close. This class contains
     * a strong reference to the original session, so even if the original session is dropped by the client
     * it would still be reachable by the GC, which is important if the session is implicitly closed.
     */
    public final static class NonCloseableView implements MemorySession, Scoped {
        final MemorySessionImpl session;

        public NonCloseableView(MemorySessionImpl session) {
            this.session = session;
        }

        public MemorySessionImpl sessionImpl() {
            return session;
        }

        @Override
        public boolean isAlive() {
            return session.isAlive();
        }

        @Override
        public boolean isCloseable() {
            return false;
        }

        @Override
        public Thread ownerThread() {
            return session.ownerThread();
        }

        @Override
        public boolean equals(Object o) {
            return session.equals(o);
        }

        @Override
        public int hashCode() {
            return session.hashCode();
        }

        @Override
        public void whileAlive(Runnable action) {
            session.whileAlive(action);
        }

        @Override
        public MemorySession asNonCloseable() {
            return this;
        }

        @Override
        public void addCloseAction(Runnable runnable) {
            session.addCloseAction(runnable);
        }

        @Override
        public void close() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * A list of all cleanup actions associated with a memory session. Cleanup actions are modelled as instances
     * of the {@link ResourceCleanup} class, and, together, form a linked list. Depending on whether a session
     * is shared or confined, different implementations of this class will be used, see {@link ConfinedSession.ConfinedResourceList}
     * and {@link SharedSession.SharedResourceList}.
     */
    public abstract static class ResourceList implements Runnable {
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

        public abstract static class ResourceCleanup {
            ResourceCleanup next;

            public abstract void cleanup();

            static final ResourceCleanup CLOSED_LIST = new ResourceCleanup() {
                @Override
                public void cleanup() {
                    throw new IllegalStateException("This resource list has already been closed!");
                }
            };

            static ResourceCleanup ofRunnable(Runnable cleanupAction) {
                return new ResourceCleanup() {
                    @Override
                    public void cleanup() {
                        cleanupAction.run();
                    }
                };
            }
        }

    }
}
