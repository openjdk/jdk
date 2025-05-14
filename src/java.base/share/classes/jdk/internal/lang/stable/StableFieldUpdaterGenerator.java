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
import jdk.internal.vm.annotation.ForceInline;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stable field updater code generator.
 */
final class StableFieldUpdaterGenerator {

    private StableFieldUpdaterGenerator() {}

    private static final MethodHandles.Lookup LOCAL_LOOKUP = MethodHandles.lookup();

    record Shape(Class<?> varType, List<Class<?>> coordinateTypes){

        static Shape of(VarHandle varHandle) {
            final List<Class<?>> shapeCoordinateTypes = new ArrayList<>(varHandle.coordinateTypes().size());
            for (Class<?> coordinate: varHandle.coordinateTypes()) {
                shapeCoordinateTypes.add(toObjectIfReference(coordinate));
            }
            return new Shape(toObjectIfReference(varHandle.varType()), shapeCoordinateTypes);
        }

        static Class<?> toObjectIfReference(Class<?> type) {
            // Todo: fix arrays of references
            return type.isPrimitive() || type.isArray()
                    ? type
                    : Object.class;
        }

    }

    // Used to hold hand-rolled and bespoke resolvers and shapeConstructors
    record Maker(MethodHandle applyResolver, MethodHandle shapeConstructor) {
        MethodHandle make(VarHandle accessor, MethodHandle initialAccessor, MethodHandle underlying) {
            try {
                return applyResolver
                        .bindTo(shapeConstructor
                                .invoke(accessor, initialAccessor, underlying));
            } catch (Throwable t) {
                throw new InternalError(t);
            }
        }
    }

    static MethodHandle handle(VarHandle accessor,
                               MethodHandle initialAccessor,
                               MethodHandle underlying) {

        final var underlyingType = underlying.type();

        // Allow `invokeExact()` of the `apply(Object)` method
        final var adaptedUnderlying = underlyingType.parameterType(0).equals(Object.class)
                || underlyingType.parameterType(0).isArray()
                ? underlying
                : underlying.asType(underlyingType.changeParameterType(0, Object.class));

        final var shape = Shape.of(accessor);
        final var maker = MAKERS.computeIfAbsent(shape,
                s -> {
                    // System.out.println("shape = " + s);
                    return bespokeMaker(accessor, initialAccessor, underlying);
                });
        final var handle = maker.make(accessor, initialAccessor, adaptedUnderlying);

        return accessor.coordinateTypes().size() == 1
                ? handle.asType(MethodType.methodType(handle.type().returnType(), underlying.type().parameterType(0)))
                : handle;

    }

    private static Maker bespokeMaker(VarHandle accessor,
                                      MethodHandle initialAccessor,
                                      MethodHandle underlying) {

        // Todo: Implement this using bytecode generation
        throw new UnsupportedOperationException("No support for accessor: " + accessor +
                ", initialAccessor: " + initialAccessor + ", underlying: " + underlying);
    }


    // Hand-rolled classes

    static final MethodHandle INT_ONCE_APPLY =
            MhUtil.findVirtual(LOCAL_LOOKUP, IntOnce.class, "apply",
                    MethodType.methodType(int.class, Object.class));

    record IntOnce(VarHandle accessor,
                   MethodHandle initialAccessor,
                   MethodHandle underlying) {

        // Used reflectively
        @ForceInline
        public int apply(Object t) {
            try {
                final int v = (int) initialAccessor.invoke(t);
                return v == 0 ? applySlowPath(t) : v;
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        public int applySlowPath(Object t) {
            preventReentry(this);
            int v;
            synchronized (this) {
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
            return v;
        }
    }

    static final MethodHandle LONG_ONCE_APPLY =
            MhUtil.findVirtual(LOCAL_LOOKUP, LongOnce.class, "apply",
                    MethodType.methodType(long.class, Object.class));

    record LongOnce(VarHandle accessor,
                    MethodHandle initialAccessor,
                    MethodHandle underlying) {

        // Used reflectively
        @ForceInline
        public long apply(Object t) {
            try {
                long v = (long) initialAccessor.invoke(t);
                return v == 0 ? applySlowPath(t) : v;
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        public long applySlowPath(Object t) {
            preventReentry(this);
            long v;
            synchronized (this) {
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
            return v;
        }

    }

    static final MethodHandle REFERENCE_ONCE_APPLY =
            MhUtil.findVirtual(LOCAL_LOOKUP, ReferenceOnce.class, "apply",
                    MethodType.methodType(Object.class, Object.class));

    record ReferenceOnce(VarHandle accessor,
                         MethodHandle initialAccessor,
                         MethodHandle underlying) {

        // Used reflectively
        @ForceInline
        public Object apply(Object t) {
            try {
                final Object v = (Object) initialAccessor.invoke(t);
                return v == null ? applySlowPath(t) : v;
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        public Object applySlowPath(Object t) {
            preventReentry(this);
            Object v;
            synchronized (this) {
                v = (Object) accessor.getAcquire(t);
                if (v == null) {
                    try {
                        v = (int) underlying.invokeExact(t);
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                    accessor.setRelease(t, v);
                }
            }
            return v;
        }
    }

    // Arrays

    static final MethodHandle INT_ARRAY_ONCE_APPLY =
            MhUtil.findVirtual(LOCAL_LOOKUP, IntArrayOnce.class, "apply",
                    MethodType.methodType(int.class, int[].class, int.class));

    record IntArrayOnce(VarHandle accessor,
                        MethodHandle initialAccessor,
                        MethodHandle underlying) {

        // Used reflectively
        @ForceInline
        public int apply(int[] array, int i) {
            try {
                final int v = (int) initialAccessor.invoke(array, i);
                return v == 0 ? applySlowPath(array, i) : v;
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        public int applySlowPath(int[] array, int i) {
            preventReentry(this);
            int v;
            synchronized (this) {
                v = (int) accessor.getAcquire(array, i);
                if (v == 0) {
                    try {
                        v = (int) underlying.invokeExact(array, i);
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                    accessor.setRelease(array, i, v);
                }
            }
            return v;
        }
    }

    static final MethodHandle LONG_ARRAY_ONCE_APPLY =
            MhUtil.findVirtual(LOCAL_LOOKUP, LongArrayOnce.class, "apply",
                    MethodType.methodType(long.class, long[].class, int.class));

    record LongArrayOnce(VarHandle accessor,
                        MethodHandle initialAccessor,
                        MethodHandle underlying) {

        // Used reflectively
        @ForceInline
        public long apply(long[] array, int i) {
            try {
                final long v = (long) initialAccessor.invoke(array, i);
                return v == 0 ? applySlowPath(array, i) : v;
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        public long applySlowPath(long[] array, int i) {
            preventReentry(this);
            long v;
            synchronized (this) {
                v = (long) accessor.getAcquire(array, i);
                if (v == 0) {
                    try {
                        v = (long) underlying.invokeExact(array, i);
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                    accessor.setRelease(array, i, v);
                }
            }
            return v;
        }
    }


    // This method is not annotated with @ForceInline as it is always called
    // in a slow path.
    static void preventReentry(Object obj) {
        if (Thread.holdsLock(obj)) {
            throw new IllegalStateException("Recursive initialization of a stable value is illegal");
        }
    }

    static Class<?> componentTypeDeep(Class<?> type) {
        return type.isArray()
                ? componentTypeDeep(type.componentType())
                : type;
    }

    // Caching of Shapes

    private static final Map<Shape, Maker> MAKERS = new ConcurrentHashMap<>();

    static {
        // Add hand-rolled classes

        // Scalars
        MAKERS.put(new Shape(int.class, List.of(Object.class)), new Maker(INT_ONCE_APPLY, findMakerConstructor(IntOnce.class)));
        MAKERS.put(new Shape(long.class, List.of(Object.class)), new Maker(LONG_ONCE_APPLY, findMakerConstructor(LongOnce.class)));
        MAKERS.put(new Shape(Object.class, List.of(Object.class)), new Maker(REFERENCE_ONCE_APPLY, findMakerConstructor(ReferenceOnce.class)));
        // Arrays
        MAKERS.put(new Shape(int.class, List.of(int[].class, int.class)), new Maker(INT_ARRAY_ONCE_APPLY, findMakerConstructor(IntArrayOnce.class)));
        MAKERS.put(new Shape(long.class, List.of(long[].class, int.class)), new Maker(LONG_ARRAY_ONCE_APPLY, findMakerConstructor(LongArrayOnce.class)));
    }

    private static MethodHandle findMakerConstructor(Class<?> type) {
        try {
            return LOCAL_LOOKUP.findConstructor(type,
                    MethodType.methodType(void.class, VarHandle.class, MethodHandle.class, MethodHandle.class));
        } catch (ReflectiveOperationException e) {
            throw new InternalError(e);
        }
    }

}
