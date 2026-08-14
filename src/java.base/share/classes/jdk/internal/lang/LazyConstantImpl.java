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

    // Unsafe offset for access of the `status` field
    private static final long STATUS_OFFSET =
            UNSAFE.objectFieldOffset(LazyConstantImpl.class, "state");

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

    // State of the lazy constant. The field needs somtimes be accessed by
    // stronger-than-plain memory semantics.
    //
    // | Value                  | Meaning                                      |
    // | ---------------------- | -------------------------------------------- |
    // | `Supplier`             | Computing function, before computation       |
    // | `Long`                 | Identifier of the thread computing the value |
    // | `null`                 | Computation completed successfully           |
    // | `String`               | Fully qualified name of a thrown exception   |
    //
    // The state moves monotonically as described in the above table and where either of
    // the two last states can exist. I.e. the following transitions are possible:
    //
    // Supplier ---> Long -+-> null (completed successfully)
    //                     +-> String (completed exeptionally)
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
    @DontInline
    private T getSlowPath() {
        preventReentry();
        synchronized (this) {
            T t = (T) getAcquire(CONSTANT_OFFSET);
            if (t == null) {
                final Object state = getAcquire(STATUS_OFFSET);
                // Don't use switch pattern matching here in order to improve startup time.
                if (state instanceof Supplier<?> computingFunction) {
                    // This also allows the underlying supplier to be collected
                    this.state = Thread.currentThread().threadId();
                    try {
                        @SuppressWarnings("unchecked")
                        final T newT = (T) computingFunction.get();
                        t = newT;
                        Objects.requireNonNull(t);
                        setRelease(CONSTANT_OFFSET, t);
                        // Publication is needed here for toString to work correctly
                        setRelease(STATUS_OFFSET, null);
                    } catch (Throwable ex) {
                        // Release the original computing function and replace it with
                        // an exception marker
                        final String exceptionType = ex.getClass().getName().intern();
                        this.state = exceptionType;
                        throw unableToAccessConstant(exceptionType, ex);
                    }
                } else if (state instanceof String exceptionType) {
                    throw unableToAccessConstant(exceptionType, null);
                } else {
                    throw new InternalError("Cannot reach here");
                }
            }
            return t;
        }
    }

    static NoSuchElementException unableToAccessConstant(String exceptionType, Throwable cause) {
        return new NoSuchElementException("Unable to access the constant because " +
                exceptionType + " was thrown at initial computation", cause);
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
                if (state instanceof Long threadId) {
                    return "computing thread=" + threadId;
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

    // This method can use plain semantics as the threadId is only relevant for the same
    // thread that set it. Aother thread would observe something that is not an instance
    // of a Long or a long value that is not the same as the other thread's id.
    private void preventReentry() {
        if (state instanceof Long threadId && threadId == Thread.currentThread().threadId()) {
            throw new IllegalStateException("Recursive invocation of a LazyConstant's computing function");
        }
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
