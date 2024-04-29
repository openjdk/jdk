/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.lang.stable;

import jdk.internal.lang.StableValue;
import jdk.internal.vm.annotation.DontInline;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.util.NoSuchElementException;
import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;

import static jdk.internal.lang.stable.StableUtil.*;

public final class StableValueImpl<V> implements StableValue<V> {

    private static final long MUTEX_OFFSET =
            UNSAFE.objectFieldOffset(StableValueImpl.class, "mutex");

    private static final long VALUE_OFFSET =
            UNSAFE.objectFieldOffset(StableValueImpl.class, "value");

    private static final long STATE_OFFSET =
            UNSAFE.objectFieldOffset(StableValueImpl.class, "state");

    /**
     * An internal mutex used rather than synchronizing on `this`. Lazily created.
     */
    private Object mutex;

    /**
     * If non-null, holds a set value
     * If `null`  , may be unset or hold a set `null` value
     */
    @Stable
    private V value;

    /**
     * If StableUtil.NOT_SET  , a value is not set
     * If StableUtil.NON_NULL , a non-null value is set
     * If StableUtil.NULL     , a `null` value is set
     */
    @Stable
    private int state;

    /**
     * Indicates if a supplier is currently being invoked. Used to
     * detect circular supplier invocations.
     */
    @Stable private boolean supplying;

    private StableValueImpl() {}

    @ForceInline
    @Override
    public boolean isSet() {
        return state != UNSET || stateVolatile() != UNSET;
    }

    @ForceInline
    @Override
    public V orThrow() {
        // Optimistically try plain semantics first
        V v = value;
        if (v != null) {
            // If we happen to see a non-null value under
            // plain semantics, we know a value is set.
            return v;
        }
        if (state == NULL) {
            // If we happen to see a state value of NULL under
            // plain semantics, we know a value is set to `null`.
            return null;
        }
        // Now, fall back to volatile semantics.
        return orThrowVolatile();
    }

    @DontInline // Slow-path taken at most once per thread if set
    private V orThrowVolatile() {
        V v = valueVolatile();
        if (v != null) {
            // If we see a non-null value, we know a value is set.
            return v;
        }
        // Desugar?
        return switch (stateVolatile()) {
            case UNSET    -> throw new NoSuchElementException(); // No value was set
            case NON_NULL -> orThrowVolatile(); // Race: another thread has set a value
            case NULL     -> null;              // A value of `null` was set
            default       -> throw shouldNotReachHere();
        };
    }

    @ForceInline
    @Override
    public V setIfUnset(V value) {
        if (isSet()) {
           return orThrow();
        }
        synchronized (acquireMutex()) {
            if (isSet()) {
                return orThrow();
            }
            setValue(value);
            return value;
        }
    }

    @ForceInline
    @Override
    public boolean trySet(V value) {
        if (isSet()) {
            return false;
        }
        synchronized (acquireMutex()) {
            if (isSet()) {
                return false;
            }
            setValue(value);
            return true;
        }
    }

    @ForceInline
    @Override
    public V computeIfUnset(Supplier<? extends V> supplier) {
        // Optimistically try plain semantics first
        V v = value;
        if (v != null) {
            // If we happen to see a non-null value under
            // plain semantics, we know a value is set.
            return v;
        }
        if (state == NULL) {
            // If we happen to see a state value of NULL under
            // plain semantics, we know a value is set to `null`.
            return null;
        }
        // Now, fall back to volatile semantics.
        return computeIfUnsetVolatile(supplier);
    }

    @DontInline // Slow-path taken at most once per thread if set
    private V computeIfUnsetVolatile(Supplier<? extends V> supplier) {
        // Now, fall back to volatile semantics.
        V v = valueVolatile();
        if (v != null) {
            // If we see a non-null value, we know a value is set.
            return v;
        }
        return switch (stateVolatile()) {
            case UNSET    -> computeIfUnsetVolatile0(supplier);
            case NON_NULL -> orThrow(); // Race
            case NULL     -> null;
            default       -> throw shouldNotReachHere();
        };
    }

    private V computeIfUnsetVolatile0(Supplier<? extends V> supplier) {
        synchronized (acquireMutex()) {
            // A value is already set
            if (state != UNSET) {
                return orThrow();
            }
            // A value is not set
            if (supplying) {
                throw stackOverflow(supplier, null);
            }
            try {
                supplying = true;
                V newValue = supplier.get();
                setValue(newValue);
                return newValue;
            } finally {
                supplying = false;
            }
        }
    }

    @Override
    public String toString() {
        return StableUtil.toString(this);
    }

    @SuppressWarnings("unchecked")
    private V valueVolatile() {
        return (V)UNSAFE.getReferenceVolatile(this, VALUE_OFFSET);
    }

    private void setValue(V value) {
        if (state != UNSET) {
            throw StableUtil.alreadySet(this);
        }
        if (value != null) {
            putValue(value);
        }
        // Crucially, indicate a value is set _after_ it has actually been set.
        putState(value == null ? NULL : NON_NULL);
    }

    private void putValue(V value) {
        // This prevents partially initialized objects to be observed
        // under normal memory semantics.
        freeze();
        UNSAFE.putReferenceVolatile(this, VALUE_OFFSET, value);
    }

    private int stateVolatile() {
        return UNSAFE.getIntVolatile(this, STATE_OFFSET);
    }

    private void putState(int newValue) {
        // This prevents `this.value` to be seen before `this.state` is seen
        freeze();
        UNSAFE.putIntVolatile(this, STATE_OFFSET, newValue);
    }

    private Object acquireMutex() {
        Object mutex = UNSAFE.getReferenceVolatile(this, MUTEX_OFFSET);
        if (mutex == null) {
            mutex = caeMutex();
        }
        return mutex;
    }

    private Object caeMutex() {
        Object created = new Object();
        Object witness = UNSAFE.compareAndExchangeReference(this, MUTEX_OFFSET, null, created);
        return witness == null ? created : witness;
    }

    // Factories

    public static <V> StableValue<V> of() {
        return new StableValueImpl<>();
    }

    public static <V> StableValue<V> ofBackground(ThreadFactory threadFactory,
                                                  Supplier<? extends V> supplier) {
        StableValue<V> stable = StableValue.of();
        Thread bgThread = threadFactory.newThread(new Runnable() {
            @Override
            public void run() {
                try {
                    stable.computeIfUnset(supplier);
                } catch (Throwable _) {}
            }
        });
        bgThread.start();
        return stable;
    }

}
