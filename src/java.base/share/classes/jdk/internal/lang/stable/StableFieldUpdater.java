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

import jdk.internal.invoke.MhUtil;
import jdk.internal.misc.Unsafe;
import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;
import jdk.internal.util.Architecture;
import jdk.internal.vm.annotation.ForceInline;
import sun.reflect.misc.ReflectUtil;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
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
 * <p>
 * {@snippet lang = java:
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
 *             return o instanceof Foo that
 *                     && Objects.equals(this.bar, that.bar)
 *                     && Objects.equals(this.baz, that.baz);
 *         }
 *
 *         @Override
 *         public int hashCode() {
 *             return Objects.hash(bar, baz);
 *         }
 *     }
 *} to use {@code @Stable} lazy hashing:
 * <p>
 * {@snippet lang = java:
 *    public final class LazyFoo {
 *
 *         private final Bar bar;
 *         private final Baz baz;
 *
 *         private static final ToIntFunction<LazyFoo> HASH_UPDATER =
 *                 StableFieldUpdater.ofInt(LazyFoo.class, "hash",
 *                         l -> Objects.hash(l.bar, l.baz));
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
 *             return o instanceof Foo that
 *                     && Objects.equals(this.bar, that.bar)
 *                     && Objects.equals(this.baz, that.baz);
 *         }
 *
 *         @Override
 *         public int hashCode() {
 *             return HASH_UPDATER.applyAsInt(this);
 *         }
 *     }
 *}
 * <p>
 * If the underlying hash lamba returns zero, it is replaced with {@code -1}. It is legal
 * to provide {@code 0} as a replacement in which case there will be no replacement and
 * the hash code will be {@code 0}. In such cases, {@link @Stable} fields cannot be
 * constant-folded.
 * <p>
 * In cases where the entire range of hash codes are strictly specified (as it is for
 * {@code String}), a {@code long} field can be used instead, and a value of
 * {@code 1 << 32} can be used as a token for zero (as the lower 32 bits are zero) and
 * then just cast to an {@code int} as shown in this example:
 *
 * {@snippet lang = java:
 *     public final class LazySpecifiedFoo {
 *
 *         private final Bar bar;
 *         private final Baz baz;
 *
 *         private static final ToLongFunction<LazySpecifiedFoo> HASH_UPDATER =
 *                 StableFieldUpdater.ofLong(LazySpecifiedFoo.class, "hash",
 *                 StableFieldUpdater.replaceLongZero(LazySpecifiedFoo::hashCodeFor, 1L << 32));
 *
 *         @Stable
 *         private long hash;
 *
 *         public LazySpecifiedFoo(Bar bar, Baz baz) {
 *             this.bar = bar;
 *             this.baz = baz;
 *         }
 *
 *         @Override
 *         public boolean equals(Object o) {
 *             return (o instanceof Foo that)
 *                     && Objects.equals(this.bar, that.bar)
 *                     && Objects.equals(this.baz, that.baz);
 *         }
 *
 *         @Override
 *         public int hashCode() {
 *             return (int) HASH_UPDATER.applyAsLong(this);
 *         }
 *
 *         static long hashCodeFor(LazySpecifiedFoo foo) {
 *             return Objects.hash(foo.bar, foo.baz);
 *         }
 *     }
 *}
 * The example above also features a static method {@code hashCodeFor()} that acts as
 * the underlying hash function. This method can reside in another class.
 * <p>
 * Here is another example where a more low-level approach with VarHandle and MethodHandle
 * parameters is used:
 *
 * {@snippet lang=java:
    public final class MhFoo {

        private final Bar bar;
        private final Baz baz;

        private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

        private static final ToIntFunction<MhFoo> HASH_UPDATER =
                StableFieldUpdater.ofInt(
                        MhUtil.findVarHandle(LOOKUP, "hash", int.class),
                        MhUtil.findStatic(LOOKUP, "hashCodeFor", MethodType.methodType(int.class, MhFoo.class)));

        @Stable
        private int hash;

        public MhFoo(Bar bar, Baz baz) {
            this.bar = bar;
            this.baz = baz;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Foo that
                    && Objects.equals(this.bar, that.bar)
                    && Objects.equals(this.baz, that.baz);
        }

        @Override
        public int hashCode() {
            return HASH_UPDATER.applyAsInt(this);
        }

        // Used reflectively
        static int hashCodeFor(MhFoo foo) {
            return Objects.hash(foo.bar, foo.baz);
        }
    }
 * }
 * There is a convenience method for the idiomatic case above that looks like this:
 * {@snippet lang=java:

        private static final ToIntFunction<MhFoo> HASH_UPDATER =
                StableFieldUpdater.ofInt(MethodHandles.lookup(), "hash", "hashCodeFor"));
 * }
 * This will use the provided {@link MethodHandles#lookup()} to look up the field
 * {@code hash} and also use the same lookup to look up a static method that takes
 * a {@code MhFoo} and returns an {@code int}.
 *
 * The provided {@code underlying} function must not recurse or the result of the
 * operation is unspecified.
 * <p>
 * If a reference value of {@code null} is used as a parameter in any of the methods
 * in this class, a {@link NullPointerException} is thrown.
 */
public final class StableFieldUpdater {

    private StableFieldUpdater() {}

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final MethodHandles.Lookup LOCAL_LOOKUP = MethodHandles.lookup();

    /**
     * {@return a function that lazily sets the field named {@code fieldName} by invoking
     *          the static method in the {@link MethodHandles.Lookup#lookupClass()}
     *          named {@code staticUnderlyingMethodName} if the field was not previously
     *          set. Otherwise, the function returns the set field value}
     * <p>
     * If the {@code staticUnderlyingMethodName} returns zero, the value is replaced with
     * {@code -1} which is then stored in the field with the provided {@code fieldName}.
     *
     * @param lookup                     used to reflectively lookup entities
     * @param fieldName                  the name of the lazily set field (of type {@code int})
     * @param staticUnderlyingMethodName the name of the method to invoke when computing
     *                                   the hash value (invoked at most once)
     * @param <T>                        target type
     * @throws NullPointerException     if any of the provided parameters are {@code null}
     * @throws IllegalArgumentException if no {@code int} field can be found using the
     *                                  provided {@code lookup} and {@code fieldName}
     * @throws IllegalArgumentException if no {@code int} function can be found using the
     *                                  provided {@code lookup} and
     *                                  {@code staticUnderlyingMethodName} where the
     *                                  single method parameter is of type {@code T}.
     */
    public static <T> ToIntFunction<T> ofInt(MethodHandles.Lookup lookup,
                                             String fieldName,
                                             String staticUnderlyingMethodName) {
        return ofInt(
                MhUtil.findVarHandle(lookup, fieldName, int.class),
                MhUtil.findStatic(lookup, staticUnderlyingMethodName, MethodType.methodType(int.class, lookup.lookupClass())));
    }

    public static <T> ToIntFunction<T> ofInt(VarHandle accessor,
                                             MethodHandle underlying) {
        check(accessor, int.class);
        var adaptedUnderlying = checkAndAdapt(underlying, int.class);

        return new StableIntFieldUpdaterVarHandle<>(accessor, adaptedUnderlying);
    }

    @CallerSensitive
    public static <T> ToIntFunction<T> ofInt(Class<T> holderType,
                                             String fieldName,
                                             ToIntFunction<? super T> underlying) {
        Objects.requireNonNull(holderType);
        Objects.requireNonNull(fieldName);
        Objects.requireNonNull(underlying);

        final long offset = offset(holderType, fieldName, int.class, Reflection.getCallerClass());
        return new StableIntFieldUpdater<>(holderType, offset, underlying);
    }

    /**
     * {@return a function that lazily sets the field named {@code fieldName} by invoking
     *          the static method in the {@link MethodHandles.Lookup#lookupClass()}
     *          named {@code staticUnderlyingMethodName} if the field was not previously
     *          set. Otherwise, the function returns the set field value}
     * <p>
     * If the {@code staticUnderlyingMethodName} returns zero, the value is replaced with
     * {@code 1L<<32} which is then stored in the field with the provided {@code fieldName}.
     *
     * @param lookup                     used to reflectively lookup entities
     * @param fieldName                  the name of the lazily set field (of type {@code long})
     * @param staticUnderlyingMethodName the name of the method to invoke when computing
     *                                   the hash value (invoked at most once)
     * @param <T>                        target type
     * @throws NullPointerException      if any of the provided parameters are {@code null}
     * @throws IllegalArgumentException  if no {@code int} field can be found using the
     *                                   provided {@code lookup} and {@code fieldName}
     * @throws IllegalArgumentException if no {@code long} function can be found using the
     *                                  provided {@code lookup} and
     *                                  {@code staticUnderlyingMethodName} where the
     *                                  single method parameter is of type {@code T}.
     */
    public static <T> ToLongFunction<T> ofLong(MethodHandles.Lookup lookup,
                                               String fieldName,
                                               String staticUnderlyingMethodName) {
        return ofLong(
                MhUtil.findVarHandle(lookup, fieldName, long.class),
                MhUtil.findStatic(lookup, staticUnderlyingMethodName, MethodType.methodType(long.class, lookup.lookupClass())));
    }

    public static <T> ToLongFunction<T> ofLong(VarHandle accessor,
                                               MethodHandle underlying) {
        check(accessor, long.class);
        var adaptedUnderlying = checkAndAdapt(underlying, long.class);

        return makeLong(accessor, adaptedUnderlying);
    }

    @CallerSensitive
    public static <T> ToLongFunction<T> ofLong(Class<T> holderType,
                                               String fieldName,
                                               ToLongFunction<? super T> underlying) {
        Objects.requireNonNull(holderType);
        Objects.requireNonNull(fieldName);
        Objects.requireNonNull(underlying);

        final long offset = offset(holderType, fieldName, long.class, Reflection.getCallerClass());
        return makeLong(holderType, offset, underlying);
    }

    /**
     * {@return a function that will replace any zero value returned by the provided
     *          {@code underlying} function with the provided {@code zeroReplacement}}
     * @param underlying      function to filter return values from
     * @param zeroReplacement to replace any zero values returned by the {@code underlying}
     *                        function.
     * @param <T>             The function's type parameter
     */
    public static <T> ToIntFunction<T> replaceIntZero(ToIntFunction<T> underlying, int zeroReplacement) {

        record IntZeroReplacer<T>(ToIntFunction<T> underlying, int zeroReplacement) implements ToIntFunction<T> {
            @ForceInline
            @Override
            public int applyAsInt(T value) {
                return replaceZero(underlying.applyAsInt(value), zeroReplacement);
            }
        }

        Objects.requireNonNull(underlying);
        return new IntZeroReplacer<>(underlying, zeroReplacement);
    }

    /**
     * {@return a method handle that will replace any zero value returned by the provided
     *          {@code underlying} handle with the provided {@code zeroReplacement}}
     * @param underlying      function to filter return values from
     * @param zeroReplacement to replace any zero values returned by the {@code underlying}
     *                        method handle.
     */
    public static MethodHandle replaceIntZero(MethodHandle underlying, int zeroReplacement) {

        final class Holder {
            private static final MethodHandle RETURN_FILTER =
                    MhUtil.findStatic(LOCAL_LOOKUP, "replaceZero", MethodType.methodType(int.class, int.class, int.class));
        }
        check(underlying, int.class);
        return MethodHandles.filterReturnValue(underlying,
                MethodHandles.insertArguments(Holder.RETURN_FILTER, 1, zeroReplacement));
    }

    /**
     * {@return a function that will replace any zero value returned by the provided
     *          {@code underlying} function with the provided {@code zeroReplacement}}
     * @param underlying      function to filter return values from
     * @param zeroReplacement to replace any zero values returned by the {@code underlying}
     *                        function.
     * @param <T>             The function's type parameter
     */
    public static <T> ToLongFunction<T> replaceLongZero(ToLongFunction<T> underlying, long zeroReplacement) {

        record LongZeroReplacer<T>(ToLongFunction<T> underlying, long zeroReplacement) implements ToLongFunction<T> {
            @ForceInline
            @Override
            public long applyAsLong(T value) {
                return replaceZero(underlying.applyAsLong(value), zeroReplacement);
            }
        }

        Objects.requireNonNull(underlying);
        return new LongZeroReplacer<>(underlying, zeroReplacement);
    }

    /**
     * {@return a method handle that will replace any zero value returned by the provided
     *          {@code underlying} handle with the provided {@code zeroReplacement}}
     * @param underlying      function to filter return values from
     * @param zeroReplacement to replace any zero values returned by the {@code underlying}
     *                        method handle.
     */
    public static MethodHandle replaceLongZero(MethodHandle underlying, long zeroReplacement) {

        final class Holder {
            private static final MethodHandle RETURN_FILTER =
                    MhUtil.findStatic(LOCAL_LOOKUP, "replaceZero", MethodType.methodType(long.class, long.class, long.class));
        }

        check(underlying, long.class);
        return MethodHandles.filterReturnValue(underlying,
                MethodHandles.insertArguments(Holder.RETURN_FILTER, 1, zeroReplacement));
    }

    // Also used reflectively
    @ForceInline
    private static int replaceZero(int value, int zeroReplacement) {
        return value == 0
                ? zeroReplacement
                : value;
    }

    // Also used reflectively
    @ForceInline
    private static long replaceZero(long value, long zeroReplacement) {
        return value == 0
                ? zeroReplacement
                : value;
    }


    public static CallSite lazyOfInt(MethodHandles.Lookup lookup,
                                     String unused,
                                     VarHandle accessor,
                                     MethodHandle underlying) {
        check(accessor, int.class);
        var adaptedUnderlying = checkAndAdapt(underlying, int.class);
        var handle = MhUtil.findStatic(LOCAL_LOOKUP,
                "ofInt", MethodType.methodType(ToIntFunction.class, VarHandle.class, MethodHandle.class));
        return new ConstantCallSite(MethodHandles.insertArguments(handle, 0, accessor, adaptedUnderlying));
    }

    public static CallSite lazyOfLong(MethodHandles.Lookup lookup,
                                      String unused,
                                      VarHandle accessor,
                                      MethodHandle underlying) {
        check(accessor, long.class);
        var adaptedUnderlying = checkAndAdapt(underlying, long.class);
        var handle = MhUtil.findStatic(LOCAL_LOOKUP,
                "makeLong", MethodType.methodType(ToLongFunction.class, VarHandle.class, MethodHandle.class));
        return new ConstantCallSite(MethodHandles.insertArguments(handle, 0, accessor, adaptedUnderlying));
    }

    // Only to be used by classes that are used "early" in the init sequence.
    public static final class Raw {

        private Raw() {}

        public static <T> ToIntFunction<T> ofInt(Class<T> holderType,
                                                 long offset,
                                                 ToIntFunction<? super T> underlying) {
            Objects.requireNonNull(holderType);
            if (offset < 0) {
                throw new IllegalArgumentException();
            }
            Objects.requireNonNull(underlying);
            return new StableIntFieldUpdater<>(holderType, offset, underlying);
        }

        public static <T> ToLongFunction<T> ofLong(Class<T> holderType,
                                                   long offset,
                                                   ToLongFunction<? super T> underlying) {
            Objects.requireNonNull(holderType);
            if (offset < 0) {
                throw new IllegalArgumentException();
            }
            Objects.requireNonNull(underlying);
            return makeLong(holderType, offset, underlying);
        }

    }

    private static <T> ToLongFunction<T> makeLong(Class<T> holderType,
                                                  long offset,
                                                  ToLongFunction<? super T> underlying) {
        if (Architecture.is64bit()) {
            // We are also relying on the fact that the VM will not place 64-bit
            // instance fields that can cross cache lines.
            return new StableLongFieldUpdater<>(holderType, offset, underlying);
        } else {
            return new TearingStableLongFieldUpdater<>(holderType, offset, underlying);
        }
    }

    private static <T> ToLongFunction<T> makeLong(VarHandle accessor,
                                                  MethodHandle underlying) {
        if (Architecture.is64bit()) {
            // We are also relying on the fact that the VM will not place 64-bit
            // instance fields that can cross cache lines.
            return new StableLongFieldUpdaterVarHandle<>(accessor, underlying);
        } else {
            return new TearingStableLongFieldUpdaterVarHandle<>(accessor, underlying);
        }
    }

    private record StableIntFieldUpdater<T>(Class<T> holderType,
                                            long offset,
                                            ToIntFunction<? super T> underlying) implements ToIntFunction<T> {

        @ForceInline
        @Override
        public int applyAsInt(T t) {
            checkInstanceOf(holderType, t);
            // Plain semantics suffice here as we are not dealing with a reference (for
            // a reference, the internal state initialization can be reordered with
            // other store ops). JLS (24) 17.4 states that 64-bit fields tear under
            // plain memory semantics. But, `int` is 32-bit.
            int v = UNSAFE.getInt(t, offset);
            if (v == 0) {
                synchronized (t) {
                    v = UNSAFE.getIntAcquire(t, offset);
                    if (v == 0) {
                        v = underlying.applyAsInt(t);
                        UNSAFE.putIntRelease(t, offset, v);
                    }
                }
            }
            return v;
        }
    }

    private record StableIntFieldUpdaterVarHandle<T>(VarHandle accessor,
                                                     MethodHandle underlying) implements ToIntFunction<T> {

        @ForceInline
        @Override
        public int applyAsInt(T t) {
            // Plain semantics suffice here as we are not dealing with a reference (for
            // a reference, the internal state initialization can be reordered with
            // other store ops). JLS (24) 17.4 states that 64-bit fields tear under
            // plain memory semantics. But, `int` is 32-bit.
            int v = (int) accessor.get(t);
            if (v == 0) {
                synchronized (t) {
                    v = (int) accessor.getAcquire(t);
                    if (v == 0) {
                        try {
                            v = (int) underlying.invokeExact(t);
                        } catch (Throwable e) {
                            throw new RuntimeException(e);
                        }
                        accessor.setRelease(t, v);
                    }
                }
            }
            return v;
        }
    }

    private record StableLongFieldUpdater<T>(Class<T> holderType,
                                             long offset,
                                             ToLongFunction<? super T> underlying) implements ToLongFunction<T> {

        @ForceInline
        @Override
        public long applyAsLong(T t) {
            checkInstanceOf(holderType, t);
            // Plain semantics suffice here as we are not dealing with a reference (for
            // a reference, the internal state initialization can be reordered with
            // other store ops). JLS (24) 17.4 states that 64-bit fields tear under
            // plain memory semantics. But, the VM is 64-bit.
            long v = UNSAFE.getLong(t, offset);
            if (v == 0) {
                synchronized (t) {
                    v = UNSAFE.getLongAcquire(t, offset);
                    if (v == 0) {
                        v = underlying.applyAsLong(t);
                        UNSAFE.putLongRelease(t, offset, v);
                    }
                }
            }
            return v;
        }
    }

    private record TearingStableLongFieldUpdater<T>(Class<T> holderType,
                                                    long offset,
                                                    ToLongFunction<? super T> underlying) implements ToLongFunction<T> {

        @ForceInline
        @Override
        public long applyAsLong(T t) {
            checkInstanceOf(holderType, t);
            // Plain semantics suffice here as we are not dealing with a reference (for
            // a reference, the internal state initialization can be reordered with
            // other store ops). JLS (24) 17.4 states that 64-bit fields tear under
            // plain memory semantics and this VM is not 64-bit.
            long v = UNSAFE.getLongOpaque(t, offset);
            if (v == 0) {
                synchronized (t) {
                    v = UNSAFE.getLongAcquire(t, offset);
                    if (v == 0) {
                        v = underlying.applyAsLong(t);
                        UNSAFE.putLongRelease(t, offset, v);
                    }
                }
            }
            return v;
        }
    }

    private record StableLongFieldUpdaterVarHandle<T>(VarHandle accessor,
                                                      MethodHandle underlying) implements ToLongFunction<T> {

        @ForceInline
        @Override
        public long applyAsLong(T t) {
            // Plain semantics suffice here as we are not dealing with a reference (for
            // a reference, the internal state initialization can be reordered with
            // other store ops). JLS (24) 17.4 states that 64-bit fields tear under
            // plain memory semantics. But, the VM is 64-bit.
            long v = (long) accessor.get(t);
            if (v == 0) {
                synchronized (t) {
                    v = (long) accessor.getAcquire(t);
                    if (v == 0) {
                        try {
                            v = (long) underlying.invokeExact(t);
                        } catch (Throwable e) {
                            throw new RuntimeException(e);
                        }
                        accessor.setRelease(t, v);
                    }
                }
            }
            return v;
        }
    }

    private record TearingStableLongFieldUpdaterVarHandle<T>(VarHandle accessor,
                                                             MethodHandle underlying) implements ToLongFunction<T> {

        @ForceInline
        @Override
        public long applyAsLong(T t) {
            // Plain semantics suffice here as we are not dealing with a reference (for
            // a reference, the internal state initialization can be reordered with
            // other store ops). JLS (24) 17.4 states that 64-bit fields tear under
            // plain memory semantics and this VM is not 64-bit.
            long v = (long) accessor.getOpaque(t);
            if (v == 0) {
                synchronized (t) {
                    v = (long) accessor.getAcquire(t);
                    if (v == 0) {
                        try {
                            v = (long) underlying.invokeExact(t);
                        } catch (Throwable e) {
                            throw new RuntimeException(e);
                        }
                        accessor.setRelease(t, v);
                    }
                }
            }
            return v;
        }
    }

    // Static support functions

    @ForceInline
    private static void checkInstanceOf(Class<?> holderType, Object t) {
        if (!holderType.isInstance(t)) {
            throw new IllegalArgumentException("The provided t is not an instance of " + holderType);
        }
    }

    private static void check(VarHandle accessor, Class<?> varType) {
        // Implicit null check
        if (accessor.varType() != varType) {
            throw new IllegalArgumentException("Illegal accessor: " + accessor);
        }
    }

    private static MethodHandle checkAndAdapt(MethodHandle underlying, Class<?> returnType) {
        check(underlying, returnType);
        final var underlyingType = underlying.type();
        if (!underlyingType.parameterType(0).equals(Object.class)) {
            underlying = underlying.asType(underlyingType.changeParameterType(0, Object.class));
        }
        return underlying;
    }

    private static void check(MethodHandle underlying, Class<?> returnType) {
        // Implicit null check
        final var underlyingType = underlying.type();
        if (underlyingType.returnType() != returnType || underlyingType.parameterCount() != 1) {
            throw new IllegalArgumentException("Illegal underlying function: " + underlying);
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
            ReflectUtil.ensureMemberAccess(caller, holderType, null, modifiers);
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
        for (Field f : holderType.getDeclaredFields()) {
            if (f.getName().equals(fieldName)) {
                return f;
            }
        }
        return findField(holderType.getSuperclass(), fieldName);
    }

}
