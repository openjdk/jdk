/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.lang;

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.*;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

/**
 * The sole implementation of the LazyConstant interface.
 *
 * @param <T> type of the constant
 * @implNote This implementation can be used early in the boot sequence as it does not
 * rely on reflection, MethodHandles, Streams etc.
 */
@AOTSafeClassInitializer
public final class LazyConstantImpl<T> implements LazyConstant<T> {

    // Unsafe allows `LazyConstant` instances to be used early in the boot sequence
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    // Unsafe offset for access of the `constant` field
    private static final long CONSTANT_OFFSET =
            UNSAFE.objectFieldOffset(LazyConstantImpl.class, "constant");

    // Unsafe offset for access of the `state` field
    private static final long STATUS_OFFSET =
            UNSAFE.objectFieldOffset(LazyConstantImpl.class, "state");

    // Maximum number of busy-spin iterations for a platform thread before timed backoff.
    private static final int SPIN_LIMIT_PT = 1 << 8;
    // Maximum number of busy-spin iterations for a virtual thread before timed backoff.
    // We'd like to keep this small as to not hog carrier threads.
    private static final int SPIN_LIMIT_VT = 1 << 4;
    // The initial requested timed backoff in nanoseconds to park a thread while waiting
    // for another thread to complete computation. Needs to be a power of two.
    private static final long INITIAL_BACKOFF_NANOS = 1L << 10;
    // This is the maximum requested timed backoff before a thread registers for
    // completion signalling. Needs to be a power of two.
    private static final long MAX_BACKOFF_NANOS = INITIAL_BACKOFF_NANOS << 6;
    // Used by a small xorshift random generator allowing us to avoid using
    // ThreadLocalRandom early in the init phase.
    // floor(2^64 / golden ratio); spreads sequential thread IDs in a good way
    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    // Generally, fields annotated with `@Stable` are accessed by the JVM using special
    // memory semantics rules (see `parse.hpp`, `parse1.cpp`, `parse2.cpp`, and `parse3.cpp`).
    //
    // This field is accessed via Unsafe using explicit memory semantics.
    //
    // | Value           | Meaning        |
    // | --------------- | -------------- |
    // | `null`          | Unset          |
    // | `other`         | Set to `other` |
    //
    @Stable
    private T constant;

    // State of the lazy constant. Some accesses require stronger-than-plain memory
    // semantics.
    //
    // | Value                  | Meaning                                          |
    // | ---------------------- | ------------------------------------------------ |
    // | `Supplier`             | Computing function, before computation           |
    // |                        |                                                  |
    // | `Thread`               | Thread computing the value                       |
    // | `Long`                 | ID of a computing thread that is also a Supplier |
    // |                        |                                                  |
    // | `Waiter`               | Head of the stack of registered waiting threads  |
    // |                        |                                                  |
    // | `null`                 | Computation completed successfully               |
    // | `String`               | Fully qualified name of a thrown exception       |
    //
    // The state phase moves monotonically as described above, ending in either of the
    // two terminal states. The following transitions are possible:
    //
    //                                                   +--> null
    // Supplier --> Thread/Long --> [Waiter --> ...] --> |
    //                                                   +--> String
    //
    // The exception class is not stored as that would pin its class loader.
    private Object state;


    private LazyConstantImpl(Supplier<? extends T> computingFunction) {
        setRelease(STATUS_OFFSET, computingFunction);
    }

    // First tier: force-inline the initialized fast path and keep it minimal.
    @SuppressWarnings("unchecked")
    @ForceInline
    @Override
    public T get() {
        final T t = (T) getAcquire(CONSTANT_OFFSET);
        return (t != null) ? t : getSecondTier();
    }

    // Second tier: leave inlining to C2's normal heuristics and keep the method small
    // enough to remain eligible at hot call sites.
    @SuppressWarnings("unchecked")
    private T getSecondTier() {
        final Thread currentThread = Thread.currentThread();
        final Object state = getAcquire(STATUS_OFFSET);
        // Don't use switch pattern matching here in order to improve startup time.
        if (state instanceof Supplier<?> computingFunction) {
            // A Thread subclass may also implement Supplier, so use the thread ID as
            // a disjoint state marker in that unusual case.
            final Object nextState = currentThread instanceof Supplier<?>
                    ? currentThread.threadId() // Boxes the thread ID; usually allocates
                    : currentThread;           // No extra object creation
            final Object witness = UNSAFE.compareAndExchangeReference(this, STATUS_OFFSET, computingFunction, nextState);
            // Did we see the old state?
            if (witness == computingFunction) {
                // Yes: we won the CAE race.
                final T newT;
                try {
                    newT = (T) computingFunction.get();
                    Objects.requireNonNull(newT);
                } catch (Throwable ex) {
                    throw computationFailed(ex);
                }
                setRelease(CONSTANT_OFFSET, newT);
                // Atomically publish successful completion and detach any registered
                // waiter stack.
                final Object previousState = UNSAFE.getAndSetReference(this, STATUS_OFFSET, null);
                if (previousState instanceof Waiter waiter) {
                    signalWaiters(waiter);
                }
                return newT;
            }
            // No: We lost the CAE race.
            return awaitComputation(currentThread, witness);
        } else if (state instanceof Thread || state instanceof Long || state instanceof Waiter) {
            return awaitComputation(currentThread, state);
        } else if (state instanceof String exceptionType) {
            throw unableToAccessConstant(exceptionType, null);
        } else if (state != null) {
            throw unexpectedState(state);
        }
        // We first observed `constant == null` and then `state == null`.
        final T t = (T) getAcquire(CONSTANT_OFFSET);
        if (t == null) {
            throw unexpectedState(state);
        }
        return t;
    }

    // Third tier: keep the contention path out of compiled callers.
    // It busy-spins briefly, performs bounded timed parks, and finally registers
    // the waiter and parks until signalled.
    // This creates a balance between CPU usage, scheduler pressure, and
    // completion-detection latency.
    @DontInline
    private T awaitComputation(Thread currentThread, Object computingState) {
        final long currentThreadId = currentThread.threadId();
        final Object computingOwner = computingState instanceof Waiter waiter
                ? waiter.owner
                : computingState;
        if (computingOwner instanceof Thread computingThread) {
            if (computingThread == currentThread) {
                throw recursiveInvocation(computingThread);
            }
        } else if (computingOwner instanceof Long computingThreadId) {
            if (computingThreadId.longValue() == currentThreadId) {
                throw recursiveInvocation(currentThread);
            }
        } else {
            return valueAfterComputation(computingState);
        }

        int spins;
        final long maxBackoffNanos;
        if (currentThread.isVirtual()) {
            spins = SPIN_LIMIT_VT;
            // Repeated timed parking and scheduling could be expensive for
            // virtual threads so keep it to just one park.
            maxBackoffNanos = INITIAL_BACKOFF_NANOS;
        } else {
            spins = SPIN_LIMIT_PT;
            maxBackoffNanos = MAX_BACKOFF_NANOS;
        }
        long backoffNanos = INITIAL_BACKOFF_NANOS;
        boolean restoreInterrupt = false;

        // Initial random seed.
        long random = currentThreadId * GOLDEN_GAMMA;
        try {
            for (;;) {
                // Poll state opaquely while it remains unchanged, avoiding acquire
                // ordering on every iteration. If it changes, reread it with acquire
                // semantics before examining the published state.
                Object state = getOpaque(STATUS_OFFSET);
                if (state != computingState) {
                    // The state changed. Re-read it.
                    state = getAcquire(STATUS_OFFSET);
                    if (!isComputingState(state, computingOwner)) {
                        return valueAfterComputation(state);
                    }
                    computingState = state;
                }

                // If waiter registration has already begun, register immediately.
                if (computingState instanceof Waiter) {
                    break;
                }

                if (spins > 0) {
                    // Spin wait
                    --spins;
                    Thread.onSpinWait();
                } else if (backoffNanos <= maxBackoffNanos) {
                    // Park the thread using progressively longer durations.
                    if (Thread.interrupted()) {
                        restoreInterrupt = true;
                    }
                    random = nextRandomLong(random);
                    final long quarterBackoffNanos = backoffNanos >> 2;
                    final long jitter = random & (quarterBackoffNanos - 1);
                    // Prevent waiters from waking in lockstep by adding a 0–25%
                    // random jitter to a 75% fixed delay.
                    final long nanos = quarterBackoffNanos * 3 + jitter;
                    LockSupport.parkNanos(this, nanos);
                    backoffNanos <<= 1;
                } else {
                    break;
                }
            }

            final Waiter waiter = new Waiter(computingOwner, currentThread);
            // Retry registration while computation remains active; return directly
            // if a terminal state is observed.
            for (;;) {
                final Object state = getAcquire(STATUS_OFFSET);
                if (!isComputingState(state, computingOwner)) {
                    return valueAfterComputation(state);
                }
                waiter.next = state instanceof Waiter head ? head : null;
                // Successful registration only needs release semantics.
                if (UNSAFE.weakCompareAndSetReferenceRelease(this, STATUS_OFFSET, state, waiter)) {
                    break;
                }
            }

            // A permit granted before park is retained, preventing a lost signal.
            // LockSupport.park may also return because of interruption or spuriously, so
            // recheck state after every park return.
            for (;;) {
                final Object state = getAcquire(STATUS_OFFSET);
                if (!isComputingState(state, computingOwner)) {
                    return valueAfterComputation(state);
                }
                if (Thread.interrupted()) {
                    restoreInterrupt = true;
                }
                // Park the current thread and rely on completion signalling by the
                // computing thread.
                LockSupport.park(this);
            }
        } finally {
            if (restoreInterrupt) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static long nextRandomLong(long seed) {
        // Advance the per-waiter xorshift64 state.
        seed ^= seed << 13;
        seed ^= seed >>> 7;
        seed ^= seed << 17;
        return seed;
    }

    private static boolean isComputingState(Object state, Object computingOwner) {
        return state == computingOwner ||
                state instanceof Waiter waiter && waiter.owner == computingOwner;
    }

    @SuppressWarnings("unchecked")
    private T valueAfterComputation(Object state) {
        if (state instanceof String exceptionType) {
            throw unableToAccessConstant(exceptionType, null);
        } else if (state != null) {
            throw unexpectedState(state);
        }
        final T t = (T) getAcquire(CONSTANT_OFFSET);
        if (t == null) {
            throw unexpectedState(state);
        }
        return t;
    }

    // Keep the O(number of registered waiters) signalling path out of the computation
    // path's compiled body.
    @DontInline
    private static void signalWaiters(Waiter waiter) {
        do {
            LockSupport.unpark(waiter.thread);
            waiter = waiter.next;
        } while (waiter != null);
    }

    @DontInline
    private NoSuchElementException computationFailed(Throwable ex) {
        String exceptionType;
        try {
            // Derive the exception marker.
            exceptionType = ex.getClass().getName().intern();
        } catch (Throwable ex2) {
            // If deriving the exception marker fails, for example because of OOME,
            // use a fallback marker so registered waiters are still released.
            exceptionType = "[unknown]";
        }
        // Publish the exception marker and detach any registered waiter stack.
        final Object previousState = UNSAFE.getAndSetReference(this, STATUS_OFFSET, exceptionType);
        if (previousState instanceof Waiter waiter) {
            signalWaiters(waiter);
        }
        return unableToAccessConstant(exceptionType, ex);
    }

    @DontInline
    static NoSuchElementException unableToAccessConstant(String exceptionType, Throwable cause) {
        return new NoSuchElementException("Unable to access the constant because " +
                exceptionType + " was thrown at initial computation", cause);
    }

    @DontInline
    private static InternalError unexpectedState(Object state) {
        return new InternalError("Cannot reach here: " + state);
    }

    @DontInline
    private static IllegalStateException recursiveInvocation(Thread computingThread) {
        return new IllegalStateException("Recursive invocation of a LazyConstant's computing function: " + computingThread);
    }

    // For testing only
    @SuppressWarnings("unchecked")
    @ForceInline
    public T orElse(T other) {
        final T t = (T) getAcquire(CONSTANT_OFFSET);
        return (t == null) ? other : t;
    }

    @Override
    public String toString() {
        return super.toString() + "[" + toStringSuffix() + "]";
    }

    private String toStringSuffix() {
        final Object t = getAcquire(CONSTANT_OFFSET);
        if (t == this) {
            return "(this LazyConstant)";
        } else if (t != null) {
            return t.toString();
        } else {
            Object state = getAcquire(STATUS_OFFSET);
            // Diagnostic snapshot only; computation may complete after this read.
            if (state != null) {
                if (state instanceof Waiter waiter) {
                    state = waiter.owner;
                }
                if (state instanceof Thread computingThread) {
                    return "computing thread=" + computingThread.threadId();
                }
                if (state instanceof Long computingThreadId) {
                    // In this rare case, we only provide the thread id. Looking up the
                    // actual Thread is expensive and deemed not worth the effort.
                    return "computing thread id=" + computingThreadId;
                }
                if (state instanceof Supplier<?> supplier) {
                    return "computing function=" + isolateToString(supplier);
                }
                return "failed with=" + state;
            }
            // As we know `state` is `null` via an acquire read, we
            // can now be sure that this lazy constant is initialized.
            return getAcquire(CONSTANT_OFFSET).toString();
        }
    }


    // Discussion on the memory semantics used.
    // ----------------------------------------
    // A release store publishes the computed reference. An acquire load that observes
    // that publication orders the computing thread's preceding actions before subsequent
    // actions by that reader. This ordering is tied to the observed publication; it is
    // not a global fence. Acquire access is needed because the supplier may return
    // an existing object, for which construction-time final-field guarantees alone are
    // not sufficient.
    //
    @SuppressWarnings("unchecked")
    @ForceInline
    private Object getAcquire(long offset) {
        return UNSAFE.getReferenceAcquire(this, offset);
    }

    @ForceInline
    private Object getOpaque(long offset) {
        return UNSAFE.getReferenceOpaque(this, offset);
    }

    private void setRelease(long offset, Object newValue) {
        UNSAFE.putReferenceRelease(this, offset, newValue);
    }

    public static String isolateToString(Object input) {
        // Protect against user-controlled `input.toString` methods that might throw or recurse.
        try {
            return input.toString();
        } catch (Throwable t) {
            return Objects.toIdentityString(input);
        }
    }

    // A node in a lock-free stack of threads waiting for computation to complete.
    // The owner is repeated in every node so recursive invocation remains an O(1)
    // check as waiters are registered. The next field is only changed before this
    // node is successfully published in `state`.
    @TrustFinalFields
    private static final class Waiter {
        private final Object owner;
        private final Thread thread;
        private Waiter next;

        private Waiter(Object owner, Thread thread) {
            this.owner = owner;
            this.thread = thread;
        }
    }


    // Factory

    public static <T> LazyConstantImpl<T> ofLazy(Supplier<? extends T> computingFunction) {
        return new LazyConstantImpl<>(computingFunction);
    }

}
