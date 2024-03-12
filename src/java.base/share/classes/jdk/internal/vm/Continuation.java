/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.DontInline;
import jdk.internal.vm.annotation.IntrinsicCandidate;
import sun.security.action.GetPropertyAction;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Supplier;
import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.vm.annotation.Hidden;

/**
 * A one-shot delimited continuation.
 */
public class Continuation {
    private static final Unsafe U = Unsafe.getUnsafe();
    private static final long MOUNTED_OFFSET = U.objectFieldOffset(Continuation.class, "mounted");
    private static final boolean PRESERVE_SCOPED_VALUE_CACHE;
    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();
    static {
        ContinuationSupport.ensureSupported();

        StackChunk.init(); // ensure StackChunk class is initialized

        String value = GetPropertyAction.privilegedGetProperty("jdk.preserveScopedValueCache");
        PRESERVE_SCOPED_VALUE_CACHE = (value == null) || Boolean.parseBoolean(value);
    }

    /** Reason for pinning */
    public enum Pinned {
        /** Native frame on stack */ NATIVE,
        /** Monitor held */          MONITOR,
        /** In critical section */   CRITICAL_SECTION }

    /** Preemption attempt result */
    public enum PreemptStatus {
        /** Success */                                                      SUCCESS(null),
        /** Permanent failure */                                            PERM_FAIL_UNSUPPORTED(null),
        /** Permanent failure: continuation already yielding */             PERM_FAIL_YIELDING(null),
        /** Permanent failure: continuation not mounted on the thread */    PERM_FAIL_NOT_MOUNTED(null),
        /** Transient failure: continuation pinned due to a held CS */      TRANSIENT_FAIL_PINNED_CRITICAL_SECTION(Pinned.CRITICAL_SECTION),
        /** Transient failure: continuation pinned due to native frame */   TRANSIENT_FAIL_PINNED_NATIVE(Pinned.NATIVE),
        /** Transient failure: continuation pinned due to a held monitor */ TRANSIENT_FAIL_PINNED_MONITOR(Pinned.MONITOR);

        final Pinned pinned;
        private PreemptStatus(Pinned reason) { this.pinned = reason; }
        /**
         * Whether or not the continuation is pinned.
         * @return whether or not the continuation is pinned
         **/
        public Pinned pinned() { return pinned; }
    }

    private static Pinned pinnedReason(int reason) {
        return switch (reason) {
            case 2 -> Pinned.CRITICAL_SECTION;
            case 3 -> Pinned.NATIVE;
            case 4 -> Pinned.MONITOR;
            default -> throw new AssertionError("Unknown pinned reason: " + reason);
        };
    }

    private static Thread currentCarrierThread() {
        return JLA.currentCarrierThread();
    }

    static {
        try {
            registerNatives();

            // init Pinned to avoid classloading during mounting
            pinnedReason(2);
        } catch (Exception e) {
            throw new InternalError(e);
        }
    }

    private final Runnable target;

    /* While the native JVM code is aware that every continuation has a scope, it is, for the most part,
     * oblivious to the continuation hierarchy. The only time this hierarchy is traversed in native code
     * is when a hierarchy of continuations is mounted on the native stack.
     */
    private final ContinuationScope scope;
    private Continuation parent; // null for native stack
    private Continuation child; // non-null when we're yielded in a child continuation

    private StackChunk tail;

    private boolean done;
    private volatile boolean mounted;
    private Object yieldInfo;
    private boolean preempted;

    private Object[] scopedValueCache;

    /**
     * Constructs a continuation
     * @param scope the continuation's scope, used in yield
     * @param target the continuation's body
     */
    public Continuation(ContinuationScope scope, Runnable target) {
        this.scope = scope;
        this.target = target;
    }

    @Override
    public String toString() {
        return super.toString() + " scope: " + scope;
    }

    public ContinuationScope getScope() {
        return scope;
    }

    public Continuation getParent() {
        return parent;
    }

    /**
     * Returns the current innermost continuation with the given scope
     * @param scope the scope
     * @return the continuation
     */
    public static Continuation getCurrentContinuation(ContinuationScope scope) {
        Continuation cont = JLA.getContinuation(currentCarrierThread());
        while (cont != null && cont.scope != scope)
            cont = cont.parent;
        return cont;
    }

    /**
     * Creates a StackWalker for this continuation
     * @return a new StackWalker
     */
    public StackWalker stackWalker() {
        return stackWalker(EnumSet.noneOf(StackWalker.Option.class));
    }

    /**
     * Creates a StackWalker for this continuation
     * @param options the StackWalker's configuration options
     * @return a new StackWalker
     */
    public StackWalker stackWalker(Set<StackWalker.Option> options) {
        return stackWalker(options, this.scope);
    }

    /**
     * Creates a StackWalker for this continuation and enclosing ones up to the given scope
     * @param options the StackWalker's configuration options
     * @param scope the delimiting continuation scope for the stack
     * @return a new StackWalker
     */
    public StackWalker stackWalker(Set<StackWalker.Option> options, ContinuationScope scope) {
        return JLA.newStackWalkerInstance(options, scope, innermost());
    }

    /**
     * Obtains a stack trace for this unmounted continuation
     * @return the stack trace
     * @throws IllegalStateException if the continuation is mounted
     */
    public StackTraceElement[] getStackTrace() {
        return stackWalker(EnumSet.of(StackWalker.Option.SHOW_REFLECT_FRAMES))
            .walk(s -> s.map(StackWalker.StackFrame::toStackTraceElement)
            .toArray(StackTraceElement[]::new));
    }

    /// Support for StackWalker
    public static <R> R wrapWalk(Continuation inner, ContinuationScope scope, Supplier<R> walk) {
        try {
            for (Continuation c = inner; c != null && c.scope != scope; c = c.parent)
                c.mount();
            return walk.get();
        } finally {
            for (Continuation c = inner; c != null && c.scope != scope; c = c.parent)
                c.unmount();
        }
    }

    private Continuation innermost() {
        Continuation c = this;
        while (c.child != null)
            c = c.child;
        return c;
    }

    private void mount() {
        if (!compareAndSetMounted(false, true))
            throw new IllegalStateException("Mounted!!!!");
    }

    private void unmount() {
        setMounted(false);
    }

    /**
     * Mounts and runs the continuation body. If suspended, continues it from the last suspend point.
     */
    public final void run() {
        while (true) {
            mount();
            JLA.setScopedValueCache(scopedValueCache);

            if (done)
                throw new IllegalStateException("Continuation terminated");

            Thread t = currentCarrierThread();
            if (parent != null) {
                if (parent != JLA.getContinuation(t))
                    throw new IllegalStateException();
            } else
                this.parent = JLA.getContinuation(t);
            JLA.setContinuation(t, this);

            try {
                boolean isVirtualThread = (scope == JLA.virtualThreadContinuationScope());
                if (!isStarted()) { // is this the first run? (at this point we know !done)
                    enterSpecial(this, false, isVirtualThread);
                } else {
                    assert !isEmpty();
                    enterSpecial(this, true, isVirtualThread);
                }
            } finally {
                fence();
                try {
                    assert isEmpty() == done : "empty: " + isEmpty() + " done: " + done + " cont: " + Integer.toHexString(System.identityHashCode(this));
                    JLA.setContinuation(currentCarrierThread(), this.parent);
                    if (parent != null)
                        parent.child = null;

                    postYieldCleanup();

                    unmount();
                    if (PRESERVE_SCOPED_VALUE_CACHE) {
                        scopedValueCache = JLA.scopedValueCache();
                    } else {
                        scopedValueCache = null;
                    }
                    JLA.setScopedValueCache(null);
                } catch (Throwable e) { e.printStackTrace(); System.exit(1); }
            }
            // we're now in the parent continuation

            assert yieldInfo == null || yieldInfo instanceof ContinuationScope;
            if (yieldInfo == null || yieldInfo == scope) {
                this.parent = null;
                this.yieldInfo = null;
                return;
            } else {
                parent.child = this;
                parent.yield0((ContinuationScope)yieldInfo, this);
                parent.child = null;
            }
        }
    }

    private void postYieldCleanup() {
        if (done) {
            this.tail = null;
        }
    }

    private void finish() {
        done = true;
        assert isEmpty();
    }

    @IntrinsicCandidate
    private static native int doYield();

    @IntrinsicCandidate
    private static native void enterSpecial(Continuation c, boolean isContinue, boolean isVirtualThread);


    @Hidden
    @DontInline
    @IntrinsicCandidate
    private static void enter(Continuation c, boolean isContinue) {
        // This method runs in the "entry frame".
        // A yield jumps to this method's caller as if returning from this method.
        try {
            c.enter0();
        } finally {
            c.finish();
        }
    }

    @Hidden
    private void enter0() {
        target.run();
    }

    private boolean isStarted() {
        return tail != null;
    }

    private boolean isEmpty() {
        for (StackChunk c = tail; c != null; c = c.parent()) {
            if (!c.isEmpty())
                return false;
        }
        return true;
    }

    /**
     * Suspends the current continuations up to the given scope
     *
     * @param scope The {@link ContinuationScope} to suspend
     * @return {@code true} for success; {@code false} for failure
     * @throws IllegalStateException if not currently in the given {@code scope},
     */
    @Hidden
    public static boolean yield(ContinuationScope scope) {
        Continuation cont = JLA.getContinuation(currentCarrierThread());
        Continuation c;
        for (c = cont; c != null && c.scope != scope; c = c.parent)
            ;
        if (c == null)
            throw new IllegalStateException("Not in scope " + scope);

        return cont.yield0(scope, null);
    }

    @Hidden
    private boolean yield0(ContinuationScope scope, Continuation child) {
        preempted = false;

        if (scope != this.scope)
            this.yieldInfo = scope;
        int res = doYield();
        U.storeFence(); // needed to prevent certain transformations by the compiler

        assert scope != this.scope || yieldInfo == null : "scope: " + scope + " this.scope: " + this.scope + " yieldInfo: " + yieldInfo + " res: " + res;
        assert yieldInfo == null || scope == this.scope || yieldInfo instanceof Integer : "scope: " + scope + " this.scope: " + this.scope + " yieldInfo: " + yieldInfo + " res: " + res;

        if (child != null) { // TODO: ugly
            if (res != 0) {
                child.yieldInfo = res;
            } else if (yieldInfo != null) {
                assert yieldInfo instanceof Integer;
                child.yieldInfo = yieldInfo;
            } else {
                child.yieldInfo = res;
            }
            this.yieldInfo = null;
        } else {
            if (res == 0 && yieldInfo != null) {
                res = (Integer)yieldInfo;
            }
            this.yieldInfo = null;

            if (res == 0)
                onContinue();
            else
                onPinned0(res);
        }
        assert yieldInfo == null;

        return res == 0;
    }

    private void onPinned0(int reason) {
        onPinned(pinnedReason(reason));
    }

    /**
     * Called when suspending if the continuation is pinned
     * @param reason the reason for pinning
     */
    protected void onPinned(Pinned reason) {
        throw new IllegalStateException("Pinned: " + reason);
    }

    /**
     * Called when the continuation continues
     */
    protected void onContinue() {
    }

    /**
     * Tests whether this continuation is completed
     * @return whether this continuation is completed
     */
    public boolean isDone() {
        return done;
    }

    /**
     * Tests whether this unmounted continuation was unmounted by forceful preemption (a successful tryPreempt)
     * @return whether this unmounted continuation was unmounted by forceful preemption
     */
    public boolean isPreempted() {
        return preempted;
    }

    /**
     * Pins the current continuation (enters a critical section).
     * This increments an internal semaphore that, when greater than 0, pins the continuation.
     */
    public static native void pin();

    /**
     * Unpins the current continuation (exits a critical section).
     * This decrements an internal semaphore that, when equal 0, unpins the current continuation
     * if pinned with {@link #pin()}.
     */
    public static native void unpin();

    /**
     * Tests whether the given scope is pinned.
     * This method is slow.
     *
     * @param scope the continuation scope
     * @return {@code} true if we're in the give scope and are pinned; {@code false otherwise}
     */
    public static boolean isPinned(ContinuationScope scope) {
        int res = isPinned0(scope);
        return res != 0;
    }

    private static native int isPinned0(ContinuationScope scope);

    private boolean fence() {
        U.storeFence(); // needed to prevent certain transformations by the compiler
        return true;
    }

    private boolean compareAndSetMounted(boolean expectedValue, boolean newValue) {
        return U.compareAndSetBoolean(this, MOUNTED_OFFSET, expectedValue, newValue);
    }

    private void setMounted(boolean newValue) {
        mounted = newValue; // MOUNTED.setVolatile(this, newValue);
    }

    private String id() {
        return Integer.toHexString(System.identityHashCode(this))
                + " [" + currentCarrierThread().threadId() + "]";
    }

    /**
     * Tries to forcefully preempt this continuation if it is currently mounted on the given thread
     * Subclasses may throw an {@link UnsupportedOperationException}, but this does not prevent
     * the continuation from being preempted on a parent scope.
     *
     * @param thread the thread on which to forcefully preempt this continuation
     * @return the result of the attempt
     * @throws UnsupportedOperationException if this continuation does not support preemption
     */
    public PreemptStatus tryPreempt(Thread thread) {
        throw new UnsupportedOperationException("Not implemented");
    }

    // native methods
    private static native void registerNatives();

    private void dump() {
        System.out.println("Continuation@" + Long.toHexString(System.identityHashCode(this)));
        System.out.println("\tparent: " + parent);
        int i = 0;
        for (StackChunk c = tail; c != null; c = c.parent()) {
            System.out.println("\tChunk " + i);
            System.out.println(c);
        }
    }

    private static boolean isEmptyOrTrue(String property) {
        String value = GetPropertyAction.privilegedGetProperty(property);
        if (value == null)
            return false;
        return value.isEmpty() || Boolean.parseBoolean(value);
    }
}
