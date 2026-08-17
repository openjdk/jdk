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
import jdk.internal.vm.annotation.AOTSafeClassInitializer;
import jdk.internal.vm.annotation.DontInline;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

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

    // The max number of busy spin loops we should do when checking if computation is
    // completed by another thread before backing off to parking a thread.
    private static final int SPIN_LIMIT = 1 << 8;
    // The initial time in ns to park a thread while waiting for another thread to
    // complete computation. Needs to be a power of two.
    private static final long INITIAL_BACKOFF_NANOS = 1L << 10;
    // This is the maximum progressive waiting time for another thread to complete
    // computation. Needs to be a power of two.
    private static final long MAX_BACKOFF_NANOS = INITIAL_BACKOFF_NANOS << 16;
    // Used by a makeshift random generator allowing us to avoid using
    // ThreadLocalRandom early in the init phase.
    // floor(2^64 / golden ratio); spreads sequential thread IDs in a good way
    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    // Generally, fields annotated with `@Stable` are accessed by the JVM using special
    // memory semantics rules (see `parse.hpp` and `parse(1|2|3).cpp`).
    //
    // This field is used reflectively via Unsafe using explicit memory semantics.
    //
    // | Value           | Meaning        |
    // | --------------- | -------------- |
    // | `null`          | Unset          |
    // | `other`         | Set to `other` |
    //
    @Stable
    private T constant;

    // State of the lazy constant. The field needs sometimes be accessed by
    // stronger-than-plain memory semantics.
    //
    // | Value                  | Meaning                                          |
    // | ---------------------- | ------------------------------------------------ |
    // | `Supplier`             | Computing function, before computation           |
    // |                        |                                                  |
    // | `Thread`               | Thread computing the value                       |
    // | `Long`                 | ID of a computing thread that is also a Supplier |
    // |                        |                                                  |
    // | `null`                 | Computation completed successfully               |
    // | `String`               | Fully qualified name of a thrown exception       |
    //
    // The state moves monotonically as described in the above table and where either of
    // the two last states can exist. I.e. the following transitions are possible:
    //
    // Supplier ---> Thread/Long -+-> null (completed successfully)
    //                            +-> String (completed exceptionally)
    //
    // The exception class is not stored as that would pin its class loader.
    private Object state;

    private LazyConstantImpl(Supplier<? extends T> computingFunction) {
        setRelease(STATUS_OFFSET, computingFunction);
    }

    @SuppressWarnings("unchecked")
    @ForceInline
    @Override
    public T get() {
        final T t = (T) getAcquire(CONSTANT_OFFSET);
        return (t != null) ? t : getSlowPath();
    }

    @SuppressWarnings("unchecked")
    private T getSlowPath() {
        final Thread currentThread = Thread.currentThread();
        final Object state = getAcquire(STATUS_OFFSET);
        // Don't use switch pattern matching here in order to improve startup time.
        if (state instanceof Supplier<?> computingFunction) {
            // A Thread subclass may also implement Supplier, so use the thread ID as
            // a disjoint state marker in that unusual case.
            final Object nextState = currentThread instanceof Supplier<?>
                    ? currentThread.threadId() // Implies autoboxing (in many cases)
                    : currentThread;           // No extra object creation
            final Object witness = UNSAFE.compareAndExchangeReference(this, STATUS_OFFSET, computingFunction, nextState);
            // Did we see the old state?
            if (witness == computingFunction) {
                // Yes: we won the CAE race.
                try {
                    final T newT = (T) computingFunction.get();
                    Objects.requireNonNull(newT);
                    setRelease(CONSTANT_OFFSET, newT);
                    // Publication is needed here for toString to work correctly
                    setRelease(STATUS_OFFSET, null);
                    return newT;
                } catch (Throwable ex) {
                    throw computationFailed(ex);
                }
            }
            // No: We lost the CAE race.
            return awaitComputation(currentThread, witness);
        } else if (state instanceof Thread || state instanceof Long) {
            return awaitComputation(currentThread, state);
        } else if (state instanceof String exceptionType) {
            throw unableToAccessConstant(exceptionType, null);
        } else if (state != null) {
            throw unexpectedState(state);
        }
        // We first observed 'constant == null' and then 'state == null'
        final T t = (T) getAcquire(CONSTANT_OFFSET);
        if (t == null) {
            throw unexpectedState(state);
        }
        return t;
    }

    @SuppressWarnings("unchecked")
    @DontInline
    private T awaitComputation(Thread currentThread, Object computingState) {
        final long currentThreadId = currentThread.threadId();
        if (computingState instanceof Thread computingThread) {
            if (computingThread == currentThread) {
                throw recursiveInvocation(computingThread);
            }
        } else if (computingState instanceof Long computingThreadId) {
            if (computingThreadId.longValue() == currentThreadId) {
                throw recursiveInvocation(currentThread);
            }
        } else if (computingState instanceof String exceptionType) {
            throw unableToAccessConstant(exceptionType, null);
        } else if (computingState != null) {
            throw unexpectedState(computingState);
        } else {
            final T t = (T) getAcquire(CONSTANT_OFFSET);
            if (t == null) {
                throw unexpectedState(computingState);
            }
            return t;
        }

        int spins = SPIN_LIMIT;
        long backoffNanos = INITIAL_BACKOFF_NANOS;
        boolean restoreInterrupt = false;

        // Initial random seed
        long random = currentThreadId * GOLDEN_GAMMA;
        try {
            // Only poll the `state` in the loop to minimize CPU cache
            // contention.
            for (;;) {
                final Object state = getAcquire(STATUS_OFFSET);
                if (state != computingState) {
                    if (state instanceof String exceptionType) {
                        throw unableToAccessConstant(exceptionType, null);
                    } else if (state != null) {
                        throw unexpectedState(state);
                    } else {
                        // state is `null` -> we have a computed constant
                        final T t = (T) getAcquire(CONSTANT_OFFSET);
                        if (t == null) {
                            throw unexpectedState(state);
                        }
                        return t;
                    }
                }

                if (spins > 0) {
                    --spins;
                    Thread.onSpinWait();
                } else {
                    if (Thread.interrupted()) {
                        restoreInterrupt = true;
                    }
                    random = nextRandomLong(random);
                    final long quarterBackoffNanos = backoffNanos >> 2;
                    final long jitter = random & (quarterBackoffNanos - 1);
                    // Prevent threads from being unparked in lock steps by
                    // introducing a random jitter:
                    // 75% fixed + 25% random
                    final long nanos = quarterBackoffNanos * 3 + jitter;
                    LockSupport.parkNanos(this, nanos);
                    // Exponentially bump up the backoff time until the max
                    // backoff time is reached.
                    backoffNanos = Math.min(backoffNanos << 1, MAX_BACKOFF_NANOS);
                }
            }
        } finally {
            if (restoreInterrupt) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static long nextRandomLong(long seed) {
        // Scramble the random seed by shifting some prime-number steps.
        seed ^= seed << 13;
        seed ^= seed >>> 7;
        seed ^= seed << 17;
        return seed;
    }

    @DontInline
    private NoSuchElementException computationFailed(Throwable ex) {
        String exceptionType;
        try {
            // Replace the computing thread with an exception marker.
            exceptionType = ex.getClass().getName().intern();
        } catch (Throwable ex2) {
            // In very rare conditions (e.g., OOME) we might end up here and
            // in order not to hog any waiting threads indefinitely, we need to at least
            // publish something to let them continue.
            exceptionType = "[unknown]";
        }
        setRelease(STATUS_OFFSET, exceptionType);
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
            final Object state = getAcquire(STATUS_OFFSET);
            // There could be a race here
            if (state != null) {
                if (state instanceof Thread computingThread) {
                    return "computing thread=" + computingThread.threadId();
                }
                if (state instanceof Long computingThreadId) {
                    // In this rare case, we only provide the thread id. Looking up the
                    // actual Thread is expensive and deamed not worth the effort.
                    return "computing thread id=" + computingThreadId;
                }
                if (state instanceof Supplier<?> supplier) {
                    return "computing function=" + isolateToString(supplier);
                }
                return "failed with=" + state;
            }
            // As we know `state` is `null` via a volatile read, we
            // can now be sure that this lazy constant is initialized
            return getAcquire(CONSTANT_OFFSET).toString();
        }
    }


    // Discussion on the memory semantics used.
    // ----------------------------------------
    // Using acquire/release semantics on the `constant` field is the cheapest way to
    // establish a happens-before (HB) relation between load and store operations. Every
    // implementation of a method defined in the interface `LazyConstant` except
    // `equals()` starts with a load of the `constant` field using acquire semantics.
    //
    // If the underlying supplier was guaranteed to always create a new object,
    // a fence after creation and subsequent plain loads would suffice to ensure
    // new objects' state are always correctly observed. However, no such restriction is
    // imposed on the underlying supplier. Hence, the docs state there should be an
    // HB relation meaning we will have to pay a price (on certain platforms) on every
    // `get()` operation that is not constant-folded.

    @SuppressWarnings("unchecked")
    @ForceInline
    private Object getAcquire(long offset) {
        return UNSAFE.getReferenceAcquire(this, offset);
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

    // Factory

    public static <T> LazyConstantImpl<T> ofLazy(Supplier<? extends T> computingFunction) {
        return new LazyConstantImpl<>(computingFunction);
    }

}
