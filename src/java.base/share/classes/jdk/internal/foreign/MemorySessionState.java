package jdk.internal.foreign;

import jdk.internal.misc.ScopedMemoryAccess;

import java.lang.foreign.MemorySession;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.ref.Cleaner;
import java.lang.ref.Reference;

public abstract class MemorySessionState {

    static final int OPEN = 0;
    static final int CLOSING = -1;
    static final int CLOSED = -2;

    int state = OPEN;

    static final VarHandle STATE;

    static {
        try {
            STATE = MethodHandles.lookup().findVarHandle(MemorySessionState.class, "state", int.class);
        } catch (Throwable ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    static final int MAX_FORKS = Integer.MAX_VALUE;

    final Cleaner.Cleanable cleanable;
    public final ResourceList resourceList;

    MemorySessionState(ResourceList resourceList, Cleaner cleaner) {
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
            throw new IllegalStateException("Already closed!");
        }
    }

    public abstract boolean isAlive();

    public abstract Thread ownerThread();

    public abstract void checkValidState();

    public abstract void acquire();

    public abstract void release();

    /**
     * A list of all cleanup actions associated with a memory session. Cleanup actions are modelled as instances
     * of the {@link ResourceList.ResourceCleanup} class, and, together, form a linked list. Depending on whether a session
     * is shared or confined, different implementations of this class will be used, see {@link ConfinedSessionState.ConfinedResourceList}
     * and {@link SharedSessionState.SharedResourceList}.
     */
    public abstract static class ResourceList implements Runnable {
        ResourceList.ResourceCleanup fst;

        public abstract void add(ResourceList.ResourceCleanup cleanup);

        abstract void cleanup();

        public final void run() {
            cleanup(); // cleaner interop
        }

        static void cleanup(ResourceList.ResourceCleanup first) {
            ResourceList.ResourceCleanup current = first;
            while (current != null) {
                current.cleanup();
                current = current.next;
            }
        }

        public abstract static class ResourceCleanup implements Runnable {
            ResourceList.ResourceCleanup next;

            public abstract void cleanup();

            public final void run() {
                cleanup();
            }

            static final ResourceList.ResourceCleanup CLOSED_LIST = new ResourceList.ResourceCleanup() {
                @Override
                public void cleanup() {
                    throw new IllegalStateException("This resource list has already been closed!");
                }
            };

            public static ResourceList.ResourceCleanup ofRunnable(Runnable cleanupAction) {
                return cleanupAction instanceof ResourceCleanup ?
                        (ResourceCleanup)cleanupAction :
                        new ResourceList.ResourceCleanup() {
                    @Override
                    public void cleanup() {
                        cleanupAction.run();
                    }
                };
            }
        }

    }
}
