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
import jdk.internal.util.Architecture;
import jdk.internal.vm.annotation.ForceInline;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.Objects;

import static jdk.internal.lang.stable.StableFieldUpdaterGenerator.*;

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
 *         private static final MethodHandle HASH_UPDATER;
 *
 *         static {
 *             MethodHandles.Lookup lookup = MethodHandles.lookup();
 *             try {
 *                 VarHandle accessor = lookup.findVarHandle(LazyFoo.class, "hash", int.class);
 *                 MethodHandle underlying = lookup.findStatic(LazyFoo.class, "hashCodeFor", MethodType.methodType(int.class, LazyFoo.class));
 *                 HASH_UPDATER = StableFieldUpdater.atMostOnce(accessor, underlying);
 *             } catch (ReflectiveOperationException e) {
 *                 throw new InternalError(e);
 *             }
 *         }
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
 *             try {
 *                 return (int) HASH_UPDATER.invokeExact(this);
 *             } catch (Throwable e) {
 *                 throw new RuntimeException(e);
 *             }
 *         }
 *
 *         private static int hashCodeFor(LazyFoo foo) {
 *             return Objects.hash(foo.bar, foo.baz);
 *         }
 *     }
 *}
 * <p>
 * If the underlying hash lamba returns zero, the hash code will be {@code 0}. In such
 * cases, {@link @Stable} fields cannot be constant-folded.
 * <p>
 * In cases where the entire range of hash codes are strictly specified (as it is for
 * {@code String}), a {@code long} field can be used instead, and a value of
 * {@code 1 << 32} can be used as a token for zero (as the lower 32 bits are zero) and
 * then just cast to an {@code int} as shown in this example:
 *
 * {@snippet lang = java:
 *    public final class LazySpecifiedFoo {
 *
 *         private final Bar bar;
 *         private final Baz baz;
 *
 *         private static final MethodHandle HASH_UPDATER;
 *
 *         static {
 *             MethodHandles.Lookup lookup = MethodHandles.lookup();
 *             try {
 *                 VarHandle accessor = lookup.findVarHandle(LazySpecifiedFoo.class, "hash", long.class);
 *                 MethodHandle underlying = lookup.findStatic(LazySpecifiedFoo.class, "hashCodeFor", MethodType.methodType(long.class, LazySpecifiedFoo.class));
 *
 *                 // Replaces zero with 2^32. Bits 32-63 will then be masked away by the
 *                 // `hashCode()` method using an (int) cast.
 *                 underlying = StableFieldUpdater.replaceLongZero(underlying, 1L << 32);
 *
 *                 HASH_UPDATER = StableFieldUpdater.atMostOnce(accessor, underlying);
 *             } catch (ReflectiveOperationException e) {
 *                 throw new InternalError(e);
 *             }
 *         }
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
 *             return o instanceof Foo that
 *                     && Objects.equals(this.bar, that.bar)
 *                     && Objects.equals(this.baz, that.baz);
 *         }
 *
 *         @Override
 *         public int hashCode() {
 *             try {
 *                 return (int) (long) HASH_UPDATER.invokeExact(this);
 *             } catch (Throwable e) {
 *                 throw new RuntimeException(e);
 *             }
 *         }
 *
 *         private static long hashCodeFor(LazySpecifiedFoo foo) {
 *             return Objects.hash(foo.bar, foo.baz);
 *         }
 *     }
 *}
 * The example above also features a static method {@code hashCodeFor()} that acts as
 * the underlying hash function. This method can reside in another class.
 * <p>
 *
 * The provided {@code underlying} function must not recurse or an IllegalStateException
 * will be thrown.
 * <p>
 * If a reference value of {@code null} is used as a parameter in any of the methods
 * in this class, a {@link NullPointerException} is thrown.
 */
public final class StableFieldUpdater {

    private StableFieldUpdater() {}

    private static final MethodHandles.Lookup LOCAL_LOOKUP = MethodHandles.lookup();

    /**
     * {@return a function that lazily sets the field accessible via the provided
     *          {@code accessor} by invoking the provided {@code underlying} function
     *          if the field has its default value (e.g. zero). Otherwise, the
     *          returned function returns the set field value}
     * <p>
     *
     * @param accessor                   used to access a field
     * @param underlying                 function which is invoked if a field has its
     *                                   default value.
     * @throws NullPointerException     if any of the provided parameters are {@code null}
     * @throws IllegalArgumentException if the accessor's {@linkplain VarHandle#varType()}
     *                                  is not equal to the return type of the
     *                                  underlying's {@linkplain MethodHandle#type()}
     * @throws IllegalArgumentException if the provided {@code underlying} function does
     *                                  take exactly one parameter of a reference type.
     */
    public static MethodHandle atMostOnce(VarHandle accessor,
                                          MethodHandle underlying) {
        // Implicit null check
        final var accessorVarType = accessor.varType();
        final var accessorCoordinateTypes = accessor.coordinateTypes();
        final var underlyingType = underlying.type();

        if (accessorVarType != underlyingType.returnType()) {
            throw new IllegalArgumentException("Return type mismatch: accessor: " + accessor + ", underlying: " + underlying);
        }

        if (!accessorCoordinateTypes.equals(underlyingType.parameterList())) {
            throw new IllegalArgumentException("Parameter type mismatch: accessor: " + accessor + ", underlying: " + underlying);
        }

        if (!accessor.isAccessModeSupported(VarHandle.AccessMode.SET)) {
            throw new IllegalArgumentException("The accessor is read only: " + accessor);
        }

        // Allow `invokeExact()` of the `apply(Object)` method
        final MethodHandle adaptedUnderlying = underlyingType.parameterType(0).equals(Object.class)
                || underlyingType.parameterType(0).isArray()
                ? underlying
                : underlying.asType(underlyingType.changeParameterType(0, Object.class));

        final MethodHandle initialAccessor = accessor.toMethodHandle(initialAccessMode(accessorVarType));

        return StableFieldUpdaterGenerator.handle(accessor, initialAccessor, underlying);
    }

    private static VarHandle.AccessMode initialAccessMode(Class<?> varType) {
        if (varType.isPrimitive()) {
            if (!Architecture.is64bit() && (varType.equals(long.class) || varType.equals(double.class))) {
                // JLS (24) 17.4 states that 64-bit fields tear under plain memory semantics.
                // Opaque semantics provides "Bitwise Atomicity" thus avoiding tearing.
                return VarHandle.AccessMode.GET_OPAQUE;
            } else {
                // Reordering does not affect primitive values
                // Todo: This does not establish a happens-before relation.
                // Plain semantics suffice here as we are not dealing with a reference (for
                // a reference, the internal state initialization can be reordered with
                // other store ops).
                return VarHandle.AccessMode.GET;
            }
        } else {
            // Acquire semantics is needed for reference variables to prevent the internal
            // state initialization can be reordered with other store ops.
            return VarHandle.AccessMode.GET_ACQUIRE;
        }
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

    /**
     * {@return a method handle that will replace any null value returned by the provided
     *          {@code underlying} handle with the provided {@code nullReplacement}}
     * @param underlying      function to filter return values from
     * @param nullReplacement to replace any zero values returned by the {@code underlying}
     *                        method handle.
     */
    public static MethodHandle replaceReferenceNull(MethodHandle underlying, Object nullReplacement) {

        final class Holder {
            private static final MethodHandle RETURN_FILTER =
                    MhUtil.findStatic(LOCAL_LOOKUP, "replaceNull", MethodType.methodType(Object.class, Object.class, Object.class));
        }
        check(underlying, Object.class);
        return MethodHandles.filterReturnValue(underlying,
                MethodHandles.insertArguments(Holder.RETURN_FILTER, 1, nullReplacement));
    }

    // Used reflectively
    @ForceInline
    private static int replaceZero(int value, int zeroReplacement) {
        return value == 0 ? zeroReplacement : value;
    }

    // Used reflectively
    @ForceInline
    private static long replaceZero(long value, long zeroReplacement) {
        return value == 0 ? zeroReplacement : value;
    }

    // Used reflectively
    // Cannot reuse `Objects::requireNonNullElse` as it prohibits `null` replacements
    @ForceInline
    private static Object replaceNull(Object value, Object nullReplacement) {
        return value == null ? nullReplacement : value;
    }

    public static CallSite lazyAtMostOnce(MethodHandles.Lookup lookup,
                                          String unused,
                                          VarHandle accessor,
                                          MethodHandle underlying) {
        Objects.requireNonNull(accessor);
        Objects.requireNonNull(underlying);
        var handle = MhUtil.findStatic(LOCAL_LOOKUP,
                "atMostOnce", MethodType.methodType(MethodHandle.class, VarHandle.class, MethodHandle.class));
        return new ConstantCallSite(MethodHandles.insertArguments(handle, 0, accessor, underlying));
    }

    // Static support functions

    private static void check(MethodHandle underlying, Class<?> returnType) {
        // Implicit null check
        final var underlyingType = underlying.type();
        if (underlyingType.returnType() != returnType || underlyingType.parameterCount() != 1) {
            throw new IllegalArgumentException("Illegal underlying function: " + underlying);
        }
    }

}
