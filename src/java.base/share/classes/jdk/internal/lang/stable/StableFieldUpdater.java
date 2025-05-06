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

package jdk.internal.lang.stable;

import jdk.internal.misc.Unsafe;
import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;
import jdk.internal.util.Architecture;
import jdk.internal.vm.annotation.ForceInline;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

/**
 * Stable field updaters.
 * <p>
 * This class allows, for example, effortless conversion of immutable classes to use lazy
 * {@link Object#hashCode()}.
 * <p>
 * Here is an example of how to convert
 *
 * {@snippet lang=java:
 *    public final class Foo {
 *
 *         private final Bar bar;
 *         private final Baz baz;
 *
 *         public Foo(Bar bar, Baz baz) {
 *             this.bar = bar;
 *             this.baz = baz;
 *         }
 *
 *         @Override
 *         public boolean equals(Object o) {
 *             return o instanceof Foo that &&
 *                     Objects.equals(this.bar, that.bar) &&
 *                     Objects.equals(this.baz, that.baz);
 *         }
 *
 *         @Override
 *         public int hashCode() {
 *             return Objects.hash(bar, baz);
 *         }
 *     }
 * }
 * to use {@code @Stable} lazy hashing:
 *
 * {@snippet lang=java:
 *    public final class LazyFoo {
 *
 *         private final Bar bar;
 *         private final Baz baz;
 *
 *         private static final ToIntFunction<LazyFoo> HASH_UPDATER =
 *                 StableFieldUpdater.ofInt(LazyFoo.class, "hash",
 *                         l -> Objects.hash(l.bar, l.baz), -1);
 *
 *         @Stable
 *         private int hash;
 *
 *         public LazyFoo(Bar bar, Baz baz) {
 *             this.bar = bar;
 *             this.baz = baz;
 *         }
 *
 *         @Override
 *         public boolean equals(Object o) {
 *             return o instanceof Foo that &&
 *                     Objects.equals(this.bar, that.bar) &&
 *                     Objects.equals(this.baz, that.baz);
 *         }
 *
 *         @Override
 *         public int hashCode() {
 *             return HASH_UPDATER.applyAsInt(this);
 *         }
 *     }
 * }
 * <p>
 * If the underlying hash lamba returns zero, it is replaced with {@code -1}.
 *
 * In cases where the entire range of hash codes are strictly specified (as it is for
 * {@code String}), a {@code long} field can be used instead, and then we can use
 * {@code 1 << 32} as a token for zero (as the lower 32 bits are zero) and then just
 * cast to an {@code int} as shown in this example:
 *
 * {@snippet lang=java:
        public final class LazySpecifiedFoo {

        private final Bar bar;
        private final Baz baz;

        private static final ToLongFunction<LazySpecifiedFoo> HASH_UPDATER =
                StableFieldUpdater.ofLong(LazySpecifiedFoo.class, "hash",
                        l -> Objects.hash(l.bar, l.baz), 1L << 32);

        @Stable
        private long hash;

        public LazySpecifiedFoo(Bar bar, Baz baz) {
            this.bar = bar;
            this.baz = baz;
        }

        @Override
        public boolean equals(Object o) {
             return (o instanceof Foo that) &&
                     Objects.equals(this.bar, that.bar) &&
                     Objects.equals(this.baz, that.baz);
        }

        @Override
        public int hashCode() {
            return (int) HASH_UPDATER.applyAsLong(this);
        }
    }
 * }
 * <p>
 * The provided {@code underlying} function must not recurse or the result of the
 * operation is unspecified.
 */
public final class StableFieldUpdater {

    private StableFieldUpdater() {}

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    @CallerSensitive
    public static <T> ToIntFunction<T> ofInt(Class<T> holderType,
                                             String fieldName,
                                             ToIntFunction<? super T> underlying,
                                             int zeroReplacement) {
        Objects.requireNonNull(holderType);
        Objects.requireNonNull(fieldName);
        Objects.requireNonNull(underlying);

        final long offset = offset(holderType, fieldName, int.class, Reflection.getCallerClass());
        return new StableIntFieldUpdater<>(holderType, offset, underlying, zeroReplacement);
    }

    // Only to be used by classes that are used "early" in the init sequence.
    public static <T> ToIntFunction<T> ofIntRaw(Class<T> holderType,
                                                long offset,
                                                ToIntFunction<? super T> underlying,
                                                int zeroReplacement) {
        Objects.requireNonNull(holderType);
        if (offset < 0) {
            throw new IllegalArgumentException();
        }
        Objects.requireNonNull(underlying);
        return new StableIntFieldUpdater<>(holderType, offset, underlying, zeroReplacement);
    }

    @CallerSensitive
    public static <T> ToLongFunction<T> ofLong(Class<T> holderType,
                                               String fieldName,
                                               ToLongFunction<? super T> underlying,
                                               long zeroReplacement) {
        Objects.requireNonNull(holderType);
        Objects.requireNonNull(fieldName);
        Objects.requireNonNull(underlying);

        final long offset = offset(holderType, fieldName, long.class, Reflection.getCallerClass());
        if (Architecture.is64bit()) {
            return new StableLongFieldUpdater<>(holderType, offset, underlying, zeroReplacement);
        } else {
            return new TearingStableLongFieldUpdater<>(holderType, offset, underlying, zeroReplacement);
        }
    }

    private record StableIntFieldUpdater<T>(Class<T> holderType,
                                            long offset,
                                            ToIntFunction<? super T> underlying,
                                            int zeroReplacement) implements ToIntFunction<T> {

        @ForceInline
        @Override
        public int applyAsInt(T t) {
            checkType(holderType, t);
            // Plain semantics suffice here as we are not dealing with a reference (for
            // a reference, the internal state initialization can be reordered with
            // other store ops). JLS (24) 17.4 states that 64-bit fields tear under
            // plain memory semantics. But, `int` is 32-bit.
            int v = UNSAFE.getInt(t, offset);
            if (v == 0) {
                // StableUtil.preventReentry(t);
                synchronized (t) {
                    v = UNSAFE.getIntAcquire(t, offset);
                    if (v == 0) {
                        v = underlying.applyAsInt(t);
                        if (v == 0) {
                            v = zeroReplacement;
                        }
                        UNSAFE.putIntRelease(t, offset, v);
                    }
                }
            }
            return v;
        }
    }

    private record StableLongFieldUpdater<T>(Class<T> holderType,
                                             long offset,
                                             ToLongFunction<? super T> underlying,
                                             long zeroReplacement) implements ToLongFunction<T> {

        @ForceInline
        @Override
        public long applyAsLong(T t) {
            checkType(holderType, t);
            // Plain semantics suffice here as we are not dealing with a reference (for
            // a reference, the internal state initialization can be reordered with
            // other store ops). JLS (24) 17.4 states that 64-bit fields tear under
            // plain memory semantics. But, the VM is 64-bit.
            long v = UNSAFE.getLong(t, offset);
            if (v == 0) {
                // StableUtil.preventReentry(t);
                synchronized (t) {
                    v = UNSAFE.getLongAcquire(t, offset);
                    if (v == 0) {
                        v = underlying.applyAsLong(t);
                        if (v == 0) {
                            v = zeroReplacement;
                        }
                        UNSAFE.putLongRelease(t, offset, v);
                    }
                }
            }
            return v;
        }
    }

    private record TearingStableLongFieldUpdater<T>(Class<T> holderType,
                                                    long offset,
                                                    ToLongFunction<? super T> underlying,
                                                    long zeroReplacement) implements ToLongFunction<T> {

        @ForceInline
        @Override
        public long applyAsLong(T t) {
            checkType(holderType, t);
            // Plain semantics suffice here as we are not dealing with a reference (for
            // a reference, the internal state initialization can be reordered with
            // other store ops). JLS (24) 17.4 states that 64-bit fields tear under
            // plain memory semantics and this VM is not 64-bit.
            long v = UNSAFE.getLongOpaque(t, offset);
            if (v == 0) {
                // StableUtil.preventReentry(t);
                synchronized (t) {
                    v = UNSAFE.getLongAcquire(t, offset);
                    if (v == 0) {
                        v = underlying.applyAsLong(t);
                        if (v == 0) {
                            v = zeroReplacement;
                        }
                        UNSAFE.putLongRelease(t, offset, v);
                    }
                }
            }
            return v;
        }
    }

    // Static support functions

    @ForceInline
    private static void checkType(Class<?> holderType, Object t) {
        if (!holderType.isInstance(t)) {
            throw new IllegalArgumentException("The provided t is not an instance of " + holderType);
        }
    }

    private static long offset(Class<?> holderType,
                               String fieldName,
                               Class<?> fieldType,
                               Class<?> caller) {
        final Field field;
        try {
            field = findField(holderType, fieldName);
            int modifiers = field.getModifiers();
            if (Modifier.isFinal(modifiers)) {
                throw illegalField("non final fields", field);
            }
            sun.reflect.misc.ReflectUtil.ensureMemberAccess(
                    caller, holderType, null, modifiers);
            if (field.getType() != fieldType) {
                throw illegalField("fields of type '" + fieldType + "'", field);
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalArgumentException(e);
        }
        return UNSAFE.objectFieldOffset(field);
    }

    private static IllegalArgumentException illegalField(String msg, Field field) {
        return new IllegalArgumentException("Only " + msg + " are supported. The provided field is '" + field + "'");
    }

    private static Field findField(Class<?> holderType, String fieldName) throws NoSuchFieldException {
        if (holderType.equals(Object.class)) {
            throw new NoSuchFieldException("'" + fieldName + "' in '" + holderType + "'");
        }
        final Field[] fields = holderType.getDeclaredFields();
        for (Field f : fields) {
            if (f.getName().equals(fieldName)) {
                return f;
            }
        }
        return findField(holderType.getSuperclass(), fieldName);
    }

}
