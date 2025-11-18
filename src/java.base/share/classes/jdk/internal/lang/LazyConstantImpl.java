/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

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

    // Underlying computing function to be used to compute the `constant` field.
    // The field needs to be `volatile` as a lazy constant can be
    // created by one thread and computed by another thread.
    // After the function is successfully invoked, the field is set to
    // `null` to allow the function to be collected.
    @Stable
    private volatile Supplier<? extends T> computingFunction;

    private LazyConstantImpl(Supplier<? extends T> computingFunction) {
        this.computingFunction = computingFunction;
    }

    @ForceInline
    @Override
    public T get() {
        final T t = getAcquire();
        return (t != null) ? t : getSlowPath();
    }

    private T getSlowPath() {
        preventReentry();
        synchronized (this) {
            T t = getAcquire();
            if (t == null) {
                t = computingFunction.get();
                Objects.requireNonNull(t);
                setRelease(t);
                // Allow the underlying supplier to be collected after successful use
                computingFunction = null;
            }
            return t;
        }
    }

    @ForceInline
    @Override
    public T orElse(T other) {
        final T t = getAcquire();
        return (t == null) ? other : t;
    }

    @ForceInline
    @Override
    public boolean isInitialized() {
        return getAcquire() != null;
    }

    @Override
    public String toString() {
        return super.toString() + "[" + toStringSuffix() + "]";
    }

    private String toStringSuffix() {
        final T t = getAcquire();
        if (t == this) {
            return "(this LazyConstant)";
        } else if (t != null) {
            return t.toString();
        }
        // Volatile read
        final Supplier<? extends T> cf = computingFunction;
        // There could be a race here
        if (cf != null) {
            return "computing function=" + computingFunction.toString();
        }
        // As we know `computingFunction` is `null` via a volatile read, we
        // can now be sure that this lazy constant is initialized
        return getAcquire().toString();
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
    private T getAcquire() {
        return (T) UNSAFE.getReferenceAcquire(this, CONSTANT_OFFSET);
    }

    private void setRelease(T newValue) {
        UNSAFE.putReferenceRelease(this, CONSTANT_OFFSET, newValue);
    }

    private void preventReentry() {
        if (Thread.holdsLock(this)) {
            throw new IllegalStateException("Recursive invocation of a LazyConstant's computing function: " + computingFunction);
        }
    }

    // Factory

    public static <T> LazyConstantImpl<T> ofLazy(Supplier<? extends T> computingFunction) {
        return new LazyConstantImpl<>(computingFunction);
    }

}
