/*
 *  Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment.Scope;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.ref.Cleaner;
import java.lang.ref.Reference;
import java.util.Objects;

import jdk.internal.misc.ScopedMemoryAccess;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

/**
 * This class manages the temporal bounds associated with a memory segment as well
 * as thread confinement. A session has a liveness bit, which is updated when the session is closed
 * (this operation is triggered by {@link MemorySessionImpl#close()}). This bit is consulted prior
 * to memory access (see {@link #checkValidStateRaw()}).
 * There are two kinds of memory session: confined memory session and shared memory session.
 * A confined memory session has an associated owner thread that confines some operations to
 * associated owner thread such as {@link #close()} or {@link #checkValidStateRaw()}.
 * Shared sessions do not feature an owner thread - meaning their operations can be called, in a racy
 * manner, by multiple threads. To guarantee temporal safety in the presence of concurrent thread,
 * shared sessions use a more sophisticated synchronization mechanism, which guarantees that no concurrent
 * access is possible when a session is being closed (see {@link jdk.internal.misc.ScopedMemoryAccess}).
 */
public final class MemorySessionImpl implements Scope {
    // This is the session of all zero-length memory segments
    public static final MemorySessionImpl GLOBAL_SESSION = new MemorySessionImpl(null, null, null, NONCLOSEABLE);

    private static final int NONCLOSEABLE = 1;
    private static final int OPEN = 0;
    private static final int CLOSED = -1;

    private static final VarHandle STATE;
    private static final VarHandle ACQUIRE_COUNT;
    private static final VarHandle ASYNC_RELEASE_COUNT;
    private static final int MAX_FORKS = Integer.MAX_VALUE;

    private static final ScopedMemoryAccess SCOPED_MEMORY_ACCESS = ScopedMemoryAccess.getScopedMemoryAccess();
    private static final ScopedMemoryAccess.ScopedAccessError ALREADY_CLOSED = new ScopedMemoryAccess.ScopedAccessError(MemorySessionImpl::alreadyClosed);
    private static final ScopedMemoryAccess.ScopedAccessError WRONG_THREAD = new ScopedMemoryAccess.ScopedAccessError(MemorySessionImpl::wrongThread);

    private final Thread owner;
    private final Object ref;
    private final ResourceList resourceList;

    // There are 4 important operations on a MemorySession: checkValidStateRaw,
    // justClose, acquire0, release0.
    //
    // There are 3 kinds of MemorySession:
    //
    // Confined session - owner is not null:
    // - Only the owner can access state and acquireCount.
    // - Only the owner can invoke checkValidStateRaw, justClose, acquire0.
    // - Any thread can invoke release0.
    //
    // Shared session - owner is null, state is not NONCLOSEABLE:
    // - Any thread can access any field, as well as invoke any method.
    // - justClose, acquire0, and release0 synchronize with themselves and with
    //   each other using acquireCount.
    //
    // Implicit session - owner is null, state is NONCLOSEABLE:
    // - No thread can invoke justClose.
    // - All fields are constant.
    // - Any thread can invoke checkValidStateRaw, acquire0 and release0.
    @Stable
    private int state;
    private int acquireCount;
    private int asyncReleaseCount;

    static {
        try {
            var lookup = MethodHandles.lookup();
            STATE = lookup.findVarHandle(MemorySessionImpl.class, "state", int.class);
            ACQUIRE_COUNT = lookup.findVarHandle(MemorySessionImpl.class, "acquireCount", int.class);
            ASYNC_RELEASE_COUNT = lookup.findVarHandle(MemorySessionImpl.class, "asyncReleaseCount", int.class);
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    public Arena asArena() {
        return new ArenaImpl(this, !isCloseable() && resourceList != null);
    }

    @ForceInline
    public static MemorySessionImpl toMemorySession(Arena arena) {
        return (MemorySessionImpl) arena.scope();
    }

    public boolean isCloseableBy(Thread thread) {
        Objects.requireNonNull(thread);
        return isCloseable() &&
                (owner == null || owner == thread);
    }

    public void addCloseAction(Runnable runnable) {
        Objects.requireNonNull(runnable);
        addInternal(ResourceList.ResourceCleanup.ofRunnable(runnable));
    }

    /**
     * Add a cleanup action. If a failure occurred (because of an add vs. close race), call the cleanup action.
     * This semantics is useful when allocating new memory segments, since we first do a malloc/mmap and _then_
     * we register the cleanup (free/munmap) against the session; so, if registration fails, we still have to
     * clean up memory. From the perspective of the client, such a failure would manifest as a factory
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
        checkValidState();
        // Note: from here on we no longer check the session state. Two cases are possible: either the resource cleanup
        // is added to the list when the session is still open, in which case everything works ok; or the resource
        // cleanup is added while the session is being closed. In this latter case, what matters is whether we have already
        // called `ResourceList::cleanup` to run all the cleanup actions. If not, we can still add this resource
        // to the list (and, in case of an add vs. close race, it might happen that the cleanup action will be
        // called immediately after).
        if (resourceList != null) {
            resourceList.add(resource);
        }
    }

    private MemorySessionImpl(Thread owner, Object ref, ResourceList resourceList, int initialState) {
        this.owner = owner;
        this.ref = ref;
        this.resourceList = resourceList;
        if (initialState == NONCLOSEABLE) {
            VarHandle.releaseFence();
            this.state = initialState;
        }
    }

    public static MemorySessionImpl createConfined(Thread thread) {
        return new MemorySessionImpl(thread, null, new ConfinedResourceList(), OPEN);
    }

    public static MemorySessionImpl createShared() {
        return new MemorySessionImpl(null, null, new SharedResourceList(), OPEN);
    }

    public static MemorySessionImpl createImplicit(Cleaner cleaner) {
        var res = new MemorySessionImpl(null, null, new SharedResourceList(), NONCLOSEABLE);
        cleaner.register(res, res.resourceList);
        return res;
    }

    public static MemorySessionImpl createHeap(Object ref) {
        Objects.requireNonNull(ref);
        return new MemorySessionImpl(null, ref, null, NONCLOSEABLE);
    }

    public void release0() {
        if (owner == Thread.currentThread()) {
            acquireCount--;
            return;
        } else if (owner != null) {
            // It is possible to end up here in two cases: this session was kept
            // alive by some other confined session which is implicitly released
            // (in which case the release call comes from the cleaner thread).
            // Or, this session might be kept alive by a shared session, which
            // means the release call can come from any thread.
            int a = (int)ASYNC_RELEASE_COUNT.getAndAdd(this, 1);
            return;
        }

        VarHandle.acquireFence();
        if (state == NONCLOSEABLE) {
            Reference.reachabilityFence(this);
            return;
        }

        int a = (int)ACQUIRE_COUNT.getAndAdd(this, -1);
    }

    public void acquire0() {
        if (owner == Thread.currentThread()) {
            if (state == CLOSED) {
                throw alreadyClosed();
            }
            if (acquireCount == MAX_FORKS) {
                throw tooManyAcquires();
            }
            acquireCount++;
            return;
        } else if (owner != null) {
            throw wrongThread();
        }

        VarHandle.acquireFence();
        if (state == NONCLOSEABLE) {
            return;
        }

        while (true) {
            int acquireCount = (int)ACQUIRE_COUNT.getVolatile(this);
            if (acquireCount < OPEN) {
                throw alreadyClosed();
            } else if (acquireCount == MAX_FORKS) {
                throw tooManyAcquires();
            }
            if (ACQUIRE_COUNT.compareAndSet(this, acquireCount, acquireCount + 1)) {
                break;
            }
        }
    }

    public void whileAlive(Runnable action) {
        Objects.requireNonNull(action);
        acquire0();
        try {
            action.run();
        } finally {
            release0();
        }
    }

    public Thread ownerThread() {
        return owner;
    }

    public boolean isAccessibleBy(Thread thread) {
        Objects.requireNonNull(thread);
        return owner == null || owner == thread;
    }

    /**
     * Returns true, if this session is still open. This method may be called in any thread.
     * @return {@code true} if this session is not closed yet.
     */
    @Override
    public boolean isAlive() {
        return state >= OPEN;
    }

    /**
     * This is a faster version of {@link #checkValidState()}, which is called upon memory access, and which
     * relies on invariants associated with the memory session implementations (volatile access
     * to the closed state bit is replaced with plain access). This method should be monomorphic,
     * to avoid virtual calls in the memory access hot path. This method is not intended as general purpose method
     * and should only be used in the memory access handle hot path; for liveness checks triggered by other API methods,
     * please use {@link #checkValidState()}.
     */
    @ForceInline
    public void checkValidStateRaw() {
        if (owner == Thread.currentThread()) {
            if (state < OPEN) {
                throw ALREADY_CLOSED;
            }
            return;
        } else if (owner != null) {
            throw WRONG_THREAD;
        }
        if (state < OPEN) {
            throw ALREADY_CLOSED;
        }
    }

    /**
     * Checks that this session is still alive (see {@link #isAlive()}).
     * @throws IllegalStateException if this session is already closed or if this is
     * a confined session and this method is called outside the owner thread.
     */
    public void checkValidState() {
        try {
            checkValidStateRaw();
        } catch (ScopedMemoryAccess.ScopedAccessError error) {
            throw error.newRuntimeException();
        }
    }

    public static void checkValidState(MemorySegment segment) {
        ((AbstractMemorySegmentImpl)segment).sessionImpl().checkValidState();
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    public boolean isCloseable() {
        VarHandle.acquireFence();
        return state <= 0;
    }

    /**
     * Closes this session, executing any cleanup action (where provided).
     * @throws IllegalStateException if this session is already closed or if this is
     * a confined session and this method is called outside the owner thread.
     */
    public void close() {
        justClose();
        resourceList.cleanup();
    }

    private void justClose() {
        if (owner == Thread.currentThread()) {
            if (state < OPEN) {
                throw alreadyClosed();
            }
            if (acquireCount > OPEN) {
                int asyncCount = (int)ASYNC_RELEASE_COUNT.getVolatile(this);
                if (asyncCount != acquireCount) {
                    throw alreadyAcquired(acquireCount - asyncCount);
                }
            }
            state = CLOSED;
            return;
        } else if (owner != null) {
            throw wrongThread();
        }

        VarHandle.acquireFence();
        if (state == NONCLOSEABLE) {
            throw nonCloseable();
        }

        int acquireCount = (int)ACQUIRE_COUNT.compareAndExchange(this, OPEN, CLOSED);
        if (acquireCount > OPEN) {
            throw alreadyAcquired(acquireCount);
        } else if (acquireCount < OPEN) {
            throw alreadyClosed();
        }
        STATE.setVolatile(this, CLOSED);
        SCOPED_MEMORY_ACCESS.closeScope(this, ALREADY_CLOSED);
    }

    @Override
    public int hashCode() {
        if (ref != null) {
            return System.identityHashCode(ref);
        }
        return System.identityHashCode(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        return o instanceof MemorySessionImpl m &&
                ref != null && ref == m.ref;
    }

    /**
     * A list of all cleanup actions associated with a memory session. Cleanup actions are modelled as instances
     * of the {@link ResourceCleanup} class, and, together, form a linked list. Depending on whether a session
     * is shared or confined, different implementations of this class will be used, see {@link ConfinedResourceList}
     * and {@link SharedResourceList}.
     */
    private abstract static class ResourceList implements Runnable {
        ResourceCleanup fst;

        abstract void add(ResourceCleanup cleanup);

        abstract void cleanup();

        public final void run() {
            cleanup(); // cleaner interop
        }

        static void cleanup(ResourceCleanup first) {
            RuntimeException pendingException = null;
            ResourceCleanup current = first;
            while (current != null) {
                try {
                    current.cleanup();
                } catch (RuntimeException ex) {
                    if (pendingException == null) {
                        pendingException = ex;
                    } else if (ex != pendingException) {
                        // note: self-suppression is not supported
                        pendingException.addSuppressed(ex);
                    }
                }
                current = current.next;
            }
            if (pendingException != null) {
                throw pendingException;
            }
        }

        private abstract static class ResourceCleanup {
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

    /**
     * A confined resource list; no races are possible here.
     */
    private static final class ConfinedResourceList extends ResourceList {
        @Override
        void add(ResourceCleanup cleanup) {
            if (fst != ResourceCleanup.CLOSED_LIST) {
                cleanup.next = fst;
                fst = cleanup;
            } else {
                throw alreadyClosed();
            }
        }

        @Override
        void cleanup() {
            if (fst != ResourceCleanup.CLOSED_LIST) {
                ResourceCleanup prev = fst;
                fst = ResourceCleanup.CLOSED_LIST;
                cleanup(prev);
            } else {
                throw alreadyClosed();
            }
        }
    }

    /**
     * A shared resource list; this implementation has to handle add vs. add races, as well as add vs. cleanup races.
     */
    private static class SharedResourceList extends ResourceList {

        static final VarHandle FST;

        static {
            try {
                FST = MethodHandles.lookup().findVarHandle(ResourceList.class, "fst", ResourceCleanup.class);
            } catch (Throwable ex) {
                throw new ExceptionInInitializerError();
            }
        }

        @Override
        void add(ResourceCleanup cleanup) {
            while (true) {
                ResourceCleanup prev = (ResourceCleanup) FST.getVolatile(this);
                if (prev == ResourceCleanup.CLOSED_LIST) {
                    // too late
                    throw alreadyClosed();
                }
                cleanup.next = prev;
                if (FST.compareAndSet(this, prev, cleanup)) {
                    return; //victory
                }
                // keep trying
            }
        }

        void cleanup() {
            // At this point we are only interested about add vs. close races - not close vs. close
            // (because MemorySessionImpl::justClose ensured that this thread won the race to close the session).
            // So, the only "bad" thing that could happen is that some other thread adds to this list
            // while we're closing it.
            if (FST.getAcquire(this) != ResourceCleanup.CLOSED_LIST) {
                //ok now we're really closing down
                ResourceCleanup prev = null;
                while (true) {
                    prev = (ResourceCleanup) FST.getVolatile(this);
                    // no need to check for DUMMY, since only one thread can get here!
                    if (FST.compareAndSet(this, prev, ResourceCleanup.CLOSED_LIST)) {
                        break;
                    }
                }
                cleanup(prev);
            } else {
                throw alreadyClosed();
            }
        }
    }

    // helper functions to centralize error handling

    private static IllegalStateException tooManyAcquires() {
        return new IllegalStateException("Session acquire limit exceeded");
    }

    private static IllegalStateException alreadyAcquired(int acquires) {
        return new IllegalStateException(String.format("Session is acquired by %d clients", acquires));
    }

    private static IllegalStateException alreadyClosed() {
        return new IllegalStateException("Already closed");
    }

    private static WrongThreadException wrongThread() {
        return new WrongThreadException("Attempted access outside owning thread");
    }

    private static UnsupportedOperationException nonCloseable() {
        return new UnsupportedOperationException("Attempted to close a non-closeable session");
    }

}
