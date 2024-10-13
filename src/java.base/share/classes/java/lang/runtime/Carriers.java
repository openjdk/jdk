/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.runtime;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import jdk.internal.misc.Unsafe;
import jdk.internal.util.ReferencedKeyMap;

import static java.lang.invoke.MethodType.methodType;

/**
 * A <em>carrier</em> is an opaque object that can be used to store component values
 * while avoiding primitive boxing associated with collection objects. Component values
 * can be primitive or Object.
 * <p>
 * Clients can create new carrier instances by describing a carrier <em>shape</em>, that
 * is, a {@linkplain MethodType method type} whose parameter types describe the types of
 * the carrier component values, or by providing the parameter types directly.
 *
 * {@snippet :
 * // Create a carrier for a string and an integer
 * CarrierElements elements = CarrierFactory.of(String.class, int.class);
 * // Fetch the carrier constructor MethodHandle
 * MethodHandle initializingConstructor = elements.initializingConstructor();
 * // Fetch the list of carrier component MethodHandles
 * List<MethodHandle> components = elements.components();
 *
 * // Create an instance of the carrier with a string and an integer
 * Object carrier = initializingConstructor.invokeExact("abc", 10);
 * // Extract the first component, type string
 * String string = (String)components.get(0).invokeExact(carrier);
 * // Extract the second component, type int
 * int i = (int)components.get(1).invokeExact(carrier);
 * }
 *
 * Alternatively, the client can use static methods when the carrier use is scattered.
 * This is possible since {@link Carriers} ensures that the same underlying carrier
 * class is used when the same component types are provided.
 *
 * {@snippet :
 * // Describe carrier using a MethodType
 * MethodType mt = MethodType.methodType(Object.class, String.class, int.class);
 * // Fetch the carrier constructor MethodHandle
 * MethodHandle constructor = Carriers.constructor(mt);
 * // Fetch the list of carrier component MethodHandles
 * List<MethodHandle> components = Carriers.components(mt);
 * }
 *
 * @implNote The strategy for storing components is deliberately left unspecified
 * so that future improvements will not be hampered by issues of backward compatibility.
 *
 * @since 21
 *
 * Warning: This class is part of PreviewFeature.Feature.STRING_TEMPLATES.
 *          Do not rely on its availability.
 */
final class Carriers {
    /**
     * Maximum number of components in a carrier (based on the maximum
     * number of args to a constructor.)
     */
    public static final int MAX_COMPONENTS = 255 - /* this */ 1;

    /**
     * Number of integer slots used by a long.
     */
    static final int LONG_SLOTS = Long.SIZE / Integer.SIZE;

    /*
     * Initialize {@link MethodHandle} constants.
     */
    static {
        try {
            Lookup lookup = MethodHandles.lookup();
            FLOAT_TO_INT = lookup.findStatic(Float.class, "floatToRawIntBits",
                    methodType(int.class, float.class));
            INT_TO_FLOAT = lookup.findStatic(Float.class, "intBitsToFloat",
                    methodType(float.class, int.class));
            DOUBLE_TO_LONG = lookup.findStatic(Double.class, "doubleToRawLongBits",
                    methodType(long.class, double.class));
            LONG_TO_DOUBLE = lookup.findStatic(Double.class, "longBitsToDouble",
                    methodType(double.class, long.class));
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("carrier static init fail", ex);
        }
    }

    /*
     * float/double conversions.
     */
    private static final MethodHandle FLOAT_TO_INT;
    private static final MethodHandle INT_TO_FLOAT;
    private static final MethodHandle DOUBLE_TO_LONG;
    private static final MethodHandle LONG_TO_DOUBLE;

    /**
     * Given an initializer {@link MethodHandle} recast and reorder arguments to
     * match shape.
     *
     * @param carrierShape  carrier shape
     * @param initializer   carrier constructor to reshape
     *
     * @return constructor with arguments recasted and reordered
     */
    static MethodHandle reshapeInitializer(CarrierShape carrierShape,
                                           MethodHandle initializer) {
        int count = carrierShape.count();
        Class<?>[] ptypes = carrierShape.ptypes();
        int objectIndex = carrierShape.objectOffset() + 1;
        int intIndex = carrierShape.intOffset() + 1;
        int longIndex = carrierShape.longOffset() + 1;
        int[] reorder = new int[count + 1];
        Class<?>[] permutePTypes = new Class<?>[count + 1];
        MethodHandle[] filters = new MethodHandle[count + 1];
        boolean hasFilters = false;
        permutePTypes[0] = CarrierObject.class;
        reorder[0] = 0;
        int index = 1;

        for (Class<?> ptype : ptypes) {
            MethodHandle filter = null;
            int from;

            if (!ptype.isPrimitive()) {
                from = objectIndex++;
                ptype = Object.class;
            } else if (ptype == double.class) {
                from = longIndex++;
                filter = DOUBLE_TO_LONG;
            } else if (ptype == float.class) {
                from = intIndex++;
                filter = FLOAT_TO_INT;
            } else if (ptype == long.class) {
                from = longIndex++;
            } else {
                from = intIndex++;
                ptype = int.class;
            }

            permutePTypes[index] = ptype;
            reorder[from] = index++;

            if (filter != null) {
                filters[from] = filter;
                hasFilters = true;
            }
        }

        if (hasFilters) {
            initializer = MethodHandles.filterArguments(initializer, 0, filters);
        }

        MethodType permutedMethodType =
                methodType(initializer.type().returnType(), permutePTypes);
        initializer = MethodHandles.permuteArguments(initializer,
                permutedMethodType, reorder);
        initializer = MethodHandles.explicitCastArguments(initializer,
                methodType(CarrierObject.class, ptypes).insertParameterTypes(0, CarrierObject.class));

        return initializer;
    }

    /**
     * Given components array, recast and reorder components to match shape.
     *
     * @param carrierShape  carrier reshape
     * @param components    carrier components to reshape
     *
     * @return list of components reshaped
     */
    static List<MethodHandle> reshapeComponents(CarrierShape carrierShape,
                                                MethodHandle[] components) {
        int count = carrierShape.count();
        Class<?>[] ptypes = carrierShape.ptypes();
        MethodHandle[] reorder = new MethodHandle[count];
        int objectIndex = carrierShape.objectOffset();
        int intIndex = carrierShape.intOffset();
        int longIndex = carrierShape.longOffset();
        int index = 0;

        for (Class<?> ptype : ptypes) {
            MethodHandle component;

            if (!ptype.isPrimitive()) {
                component = components[objectIndex++];
            } else if (ptype == double.class) {
                component = MethodHandles.filterReturnValue(
                        components[longIndex++], LONG_TO_DOUBLE);
            } else if (ptype == float.class) {
                component = MethodHandles.filterReturnValue(
                        components[intIndex++], INT_TO_FLOAT);
            } else if (ptype == long.class) {
                component = components[longIndex++];
            } else {
                component = components[intIndex++];
            }

            MethodType methodType = methodType(ptype, CarrierObject.class);
            reorder[index++] =
                    MethodHandles.explicitCastArguments(component, methodType);
        }

        return List.of(reorder);
    }

    /**
     * Factory for carriers that are backed by long[] and Object[].
     */
    static final class CarrierObjectFactory {
        /**
         * Unsafe access.
         */
        private static final Unsafe UNSAFE;

        /*
         * Constructor accessor MethodHandles.
         */
        private static final MethodHandle CONSTRUCTOR;
        private static final MethodHandle GET_LONG;
        private static final MethodHandle PUT_LONG;
        private static final MethodHandle GET_INTEGER;
        private static final MethodHandle PUT_INTEGER;
        private static final MethodHandle GET_OBJECT;
        private static final MethodHandle PUT_OBJECT;

        static {
            try {
                UNSAFE = Unsafe.getUnsafe();
                Lookup lookup = MethodHandles.lookup();
                CONSTRUCTOR = lookup.findConstructor(CarrierObject.class,
                        methodType(void.class, int.class, int.class));
                GET_LONG = lookup.findVirtual(CarrierObject.class, "getLong",
                        methodType(long.class, int.class));
                PUT_LONG = lookup.findVirtual(CarrierObject.class, "putLong",
                        methodType(CarrierObject.class, int.class, long.class));
                GET_INTEGER = lookup.findVirtual(CarrierObject.class, "getInteger",
                        methodType(int.class, int.class));
                PUT_INTEGER = lookup.findVirtual(CarrierObject.class, "putInteger",
                        methodType(CarrierObject.class, int.class, int.class));
                GET_OBJECT = lookup.findVirtual(CarrierObject.class, "getObject",
                        methodType(Object.class, int.class));
                PUT_OBJECT = lookup.findVirtual(CarrierObject.class, "putObject",
                        methodType(CarrierObject.class, int.class, Object.class));
            } catch (ReflectiveOperationException ex) {
                throw new AssertionError("carrier static init fail", ex);
            }
        }

        /**
         * Constructor builder.
         *
         * @param carrierShape  carrier object shape
         *
         * @return {@link MethodHandle} to generic carrier constructor.
         */
        MethodHandle constructor(CarrierShape carrierShape) {
            int objectCount = carrierShape.objectCount();
            int primitiveCount = carrierShape.primitiveCount();

            MethodHandle constructor = MethodHandles.insertArguments(CONSTRUCTOR,
                    0, primitiveCount, objectCount);

            return constructor;
        }

        /**
         * Adds constructor arguments for each of the allocated slots.
         *
         * @param carrierShape  carrier object shape
         *
         * @return {@link MethodHandle} to specific carrier constructor.
         */
        MethodHandle initializer(CarrierShape carrierShape) {
            int longCount = carrierShape.longCount();
            int intCount = carrierShape.intCount();
            int objectCount = carrierShape.objectCount();
            MethodHandle initializer = MethodHandles.identity(CarrierObject.class);

            // long array index
            int index = 0;
            for (int i = 0; i < longCount; i++) {
                MethodHandle put = MethodHandles.insertArguments(PUT_LONG, 1, index++);
                initializer = MethodHandles.collectArguments(put, 0, initializer);
            }

            // transition to int array index (double number of longs)
            index *= LONG_SLOTS;
            for (int i = 0; i < intCount; i++) {
                MethodHandle put = MethodHandles.insertArguments(PUT_INTEGER, 1, index++);
                initializer = MethodHandles.collectArguments(put, 0, initializer);
            }

            for (int i = 0; i < objectCount; i++) {
                MethodHandle put = MethodHandles.insertArguments(PUT_OBJECT, 1, i);
                initializer = MethodHandles.collectArguments(put, 0, initializer);
            }

            return initializer;
        }

        /**
         * Utility to construct the basic accessors from the components.
         *
         * @param carrierShape  carrier object shape
         *
         * @return array of carrier accessors
         */
        MethodHandle[] createComponents(CarrierShape carrierShape) {
            int longCount = carrierShape.longCount();
            int intCount = carrierShape.intCount();
            int objectCount = carrierShape.objectCount();
            MethodHandle[] components =
                    new MethodHandle[carrierShape.ptypes().length];

            // long array index
            int index = 0;
            // component index
            int comIndex = 0;
            for (int i = 0; i < longCount; i++) {
                components[comIndex++] = MethodHandles.insertArguments(GET_LONG, 1, index++);
            }

            // transition to int array index (double number of longs)
            index *= LONG_SLOTS;
            for (int i = 0; i < intCount; i++) {
                components[comIndex++] = MethodHandles.insertArguments(GET_INTEGER, 1, index++);
            }

            for (int i = 0; i < objectCount; i++) {
                components[comIndex++] = MethodHandles.insertArguments(GET_OBJECT, 1, i);
            }
            return components;
        }

        /**
         * Cache mapping {@link MethodType} to previously defined {@link CarrierElements}.
         */
        private static final Map<MethodType, CarrierElements>
                methodTypeCache = ReferencedKeyMap.create(false, ConcurrentHashMap::new);

        /**
         * Permute a raw constructor and component accessor {@link MethodHandle MethodHandles} to
         * match the order and types of the parameter types.
         *
         * @param carrierShape  carrier object shape
         *
         * @return {@link CarrierElements} instance
         */
        CarrierElements carrier(CarrierShape carrierShape) {
            return methodTypeCache.computeIfAbsent(carrierShape.methodType, (mt) -> {
                MethodHandle constructor = constructor(carrierShape);
                MethodHandle initializer = initializer(carrierShape);
                MethodHandle[] components = createComponents(carrierShape);
                return new CarrierElements(
                        carrierShape,
                        CarrierObject.class,
                        constructor,
                        reshapeInitializer(carrierShape, initializer),
                        reshapeComponents(carrierShape, components));
            });
        }
    }

    /**
     * Wrapper object for carrier data. Instance types are stored in the {@code objects}
     * array, while primitive types are recast to {@code int/long} and stored in the
     * {@code primitives} array. Primitive byte, short, char, boolean and int are stored as
     * integers. Longs and doubles are stored as longs.  Longs take up the first part of the
     * primitives array using normal indices. Integers follow using int[] indices offset beyond
     * the longs using unsafe getInt/putInt.
     */
    static class CarrierObject {
        /**
         * Carrier for primitive values.
         */
        private final long[] primitives;

        /**
         * Carrier for objects;
         */
        private final Object[] objects;

        /**
         * Constructor.
         *
         * @param primitiveCount  slot count required for primitives
         * @param objectCount     slot count required for objects
         */
        protected CarrierObject(int primitiveCount, int objectCount) {
            this.primitives = createPrimitivesArray(primitiveCount);
            this.objects = createObjectsArray(objectCount);
        }

        /**
         * Create a primitives array of an appropriate length.
         *
         * @param primitiveCount  slot count required for primitives
         *
         * @return primitives array of an appropriate length.
         */
        private long[] createPrimitivesArray(int primitiveCount) {
            return primitiveCount != 0 ? new long[(primitiveCount + 1) / LONG_SLOTS] : null;
        }

        /**
         * Create a objects array of an appropriate length.
         *
         * @param objectCount  slot count required for objects
         *
         * @return objects array of an appropriate length.
         */
        private Object[] createObjectsArray(int objectCount) {
            return objectCount != 0 ? new Object[objectCount] : null;
        }

        /**
         * Compute offset for unsafe access to long.
         *
         * @param i  index in primitive[]
         *
         * @return offset for unsafe access
         */
        private static long offsetToLong(int i) {
            return Unsafe.ARRAY_LONG_BASE_OFFSET +
                    (long)i * Unsafe.ARRAY_LONG_INDEX_SCALE;
        }

        /**
         * Compute offset for unsafe access to int.
         *
         * @param i  index in primitive[]
         *
         * @return offset for unsafe access
         */
        private static long offsetToInt(int i) {
            return Unsafe.ARRAY_LONG_BASE_OFFSET +
                    (long)i * Unsafe.ARRAY_INT_INDEX_SCALE;
        }

        /**
         * Compute offset for unsafe access to object.
         *
         * @param i  index in objects[]
         *
         * @return offset for unsafe access
         */
        private static long offsetToObject(int i) {
            return Unsafe.ARRAY_OBJECT_BASE_OFFSET +
                    (long)i * Unsafe.ARRAY_OBJECT_INDEX_SCALE;
        }

        /**
         * {@return long value at index}
         *
         * @param i  array index
         */
        private long getLong(int i) {
            return CarrierObjectFactory.UNSAFE.getLong(primitives, offsetToLong(i));
        }

        /**
         * Put a long value into the primitive[].
         *
         * @param i      array index
         * @param value  long value to store
         *
         * @return this object
         */
        private CarrierObject putLong(int i, long value) {
            CarrierObjectFactory.UNSAFE.putLong(primitives, offsetToLong(i), value);

            return this;
        }

        /**
         * {@return int value at index}
         *
         * @param i  array index
         */
        private int getInteger(int i) {
            return CarrierObjectFactory.UNSAFE.getInt(primitives, offsetToInt(i));
        }

        /**
         * Put a int value into the int[].
         *
         * @param i      array index
         * @param value  int value to store
         *
         * @return this object
         */
        private CarrierObject putInteger(int i, int value) {
            CarrierObjectFactory.UNSAFE.putInt(primitives, offsetToInt(i), value);

            return this;
        }

        /**
         * {@return Object value at index}
         *
         * @param i  array index
         */
        private Object getObject(int i) {
            return CarrierObjectFactory.UNSAFE.getReference(objects, offsetToObject(i));
        }

        /**
         * Put a object value into the objects[].
         *
         * @param i      array index
         * @param value  object value to store
         *
         * @return this object
         */
        private CarrierObject putObject(int i, Object value) {
            CarrierObjectFactory.UNSAFE.putReference(objects, offsetToObject(i), value);

            return this;
        }
    }

    /**
     * Class used to tally and track the number of ints, longs and objects.
     *
     * @param longCount    number of longs and doubles
     * @param intCount     number of byte, short, int, chars and booleans
     * @param objectCount  number of objects
     */
    private record CarrierCounts(int longCount, int intCount, int objectCount) {
        /**
         * Count the number of fields required in each of Object, int and long.
         *
         * @param ptypes  parameter types
         *
         * @return a {@link CarrierCounts} instance containing counts
         */
        static CarrierCounts tally(Class<?>[] ptypes) {
            return tally(ptypes, ptypes.length);
        }

        /**
         * Count the number of fields required in each of Object, int and long
         * limited to the first {@code n} parameters.
         *
         * @param ptypes  parameter types
         * @param n       number of parameters to check
         *
         * @return a {@link CarrierCounts} instance containing counts
         */
        private static CarrierCounts tally(Class<?>[] ptypes, int n) {
            int longCount = 0;
            int intCount = 0;
            int objectCount = 0;

            for (int i = 0; i < n; i++) {
                Class<?> ptype = ptypes[i];

                if (!ptype.isPrimitive()) {
                    objectCount++;
                } else if (ptype == long.class || ptype == double.class) {
                    longCount++;
                } else {
                    intCount++;
                }
            }

            return new CarrierCounts(longCount, intCount, objectCount);
        }

        /**
         * {@return total number of components}
         */
        private int count() {
            return longCount + intCount + objectCount;
        }

        /**
         * {@return total number of slots}
         */
        private int slotCount() {
            return longCount * LONG_SLOTS + intCount + objectCount;
        }

    }

    /**
     * Constructor
     */
    private Carriers() {
        throw new AssertionError("private constructor");
    }

    /**
     * Shape of carrier based on counts of each of the three fundamental data
     * types.
     */
    private static class CarrierShape {
        /**
         * {@link MethodType} providing types for the carrier's components.
         */
        final MethodType methodType;

        /**
         * Counts of different parameter types.
         */
        final CarrierCounts counts;

        /**
         * Constructor.
         *
         * @param methodType  {@link MethodType} providing types for the
         *                    carrier's components
         */
        public CarrierShape(MethodType methodType) {
            this.methodType = methodType;
            this.counts = CarrierCounts.tally(methodType.parameterArray());
        }

        /**
         * {@return number of long fields needed}
         */
        int longCount() {
            return counts.longCount();
        }

        /**
         * {@return number of int fields needed}
         */
        int intCount() {
            return counts.intCount();
        }

        /**
         * {@return number of object fields needed}
         */
        int objectCount() {
            return counts.objectCount();
        }

        /**
         * {@return slot count required for primitives}
         */
        int primitiveCount() {
            return counts.longCount() * LONG_SLOTS + counts.intCount();
        }

        /**
         * {@return array of parameter types}
         */
        Class<?>[] ptypes() {
            return methodType.parameterArray();
        }

        /**
         * {@return number of components}
         */
        int count() {
            return counts.count();
        }

        /**
         * {@return number of slots used}
         */
        int slotCount() {
            return counts.slotCount();
        }

        /**
         * {@return index of first long component}
         */
        int longOffset() {
            return 0;
        }

        /**
         * {@return index of first int component}
         */
        int intOffset() {
            return longCount();
        }

        /**
         * {@return index of first object component}
         */
        int objectOffset() {
            return longCount() + intCount();
        }
    }

    /**
     * This factory class generates {@link CarrierElements} instances containing the
     * {@link MethodHandle MethodHandles} to the constructor and accessors of a carrier
     * object.
     * <p>
     * Clients can create instances by describing a carrier <em>shape</em>, that
     * is, a {@linkplain MethodType method type} whose parameter types describe the types of
     * the carrier component values, or by providing the parameter types directly.
     */
    static final class CarrierFactory {
        /**
         * Constructor
         */
        private CarrierFactory() {
            throw new AssertionError("private constructor");
        }

        private static final CarrierObjectFactory FACTORY = new CarrierObjectFactory();

        /**
         * Factory method to return a {@link CarrierElements} instance that matches the shape of
         * the supplied {@link MethodType}. The return type of the {@link MethodType} is ignored.
         *
         * @param methodType  {@link MethodType} whose parameter types supply the
         *                    the shape of the carrier's components
         *
         * @return {@link CarrierElements} instance
         *
         * @throws NullPointerException is methodType is null
         * @throws IllegalArgumentException if number of component slots exceeds maximum
         */
        static CarrierElements of(MethodType methodType) {
            Objects.requireNonNull(methodType, "methodType must not be null");
            MethodType constructorMT = methodType.changeReturnType(Object.class);
            CarrierShape carrierShape = new CarrierShape(constructorMT);
            int slotCount = carrierShape.slotCount();

            if (MAX_COMPONENTS < slotCount) {
                throw new IllegalArgumentException("Exceeds maximum number of component slots");
            }

            return FACTORY.carrier(carrierShape);
        }

        /**
         * Factory method to return  a {@link CarrierElements} instance that matches the shape of
         * the supplied parameter types.
         *
         * @param ptypes   parameter types that supply the shape of the carrier's components
         *
         * @return {@link CarrierElements} instance
         *
         * @throws NullPointerException is ptypes is null
         * @throws IllegalArgumentException if number of component slots exceeds maximum
         */
        static CarrierElements of(Class<?>...ptypes) {
            Objects.requireNonNull(ptypes, "ptypes must not be null");
            return of(methodType(Object.class, ptypes));
        }
    }

    /**
     * Instances of this class provide the {@link MethodHandle MethodHandles} to the
     * constructor and accessors of a carrier object. The original component types can be
     * gleaned from the parameter types of the constructor {@link MethodHandle} or by the
     * return types of the components' {@link MethodHandle MethodHandles}.
     */
    static final class CarrierElements {
        /**
         * Slot count required for objects.
         */
        private final int objectCount;

        /**
         * Slot count required for primitives.
         */
        private final int primitiveCount;

        /**
         * Underlying carrier class.
         */
        private final Class<?> carrierClass;

        /**
         * Constructor {@link MethodHandle}.
         */
        private final MethodHandle constructor;

        /**
         * Initializer {@link MethodHandle}.
         */
        private final MethodHandle initializer;

        /**
         * List of component {@link MethodHandle MethodHandles}
         */
        private final List<MethodHandle> components;

        /**
         * Constructor
         */
        private CarrierElements() {
            throw new AssertionError("private constructor");
        }

        /**
         * Constructor
         */
        CarrierElements(CarrierShape carrierShape,
                        Class<?> carrierClass,
                        MethodHandle constructor,
                        MethodHandle initializer,
                        List<MethodHandle> components) {
            this.objectCount = carrierShape.objectCount();
            this.primitiveCount = carrierShape.primitiveCount();
            this.carrierClass = carrierClass;
            this.constructor = constructor;
            this.initializer = initializer;
            this.components = components;
        }

        /**
         * {@return slot count required for objects}
         */
        int objectCount() {
            return objectCount;
        }

        /**
         * {@return slot count required for primitives}
         */
        int primitiveCount() {
            return primitiveCount;
        }

        /**
         * {@return the underlying carrier class}
         */
        Class<?> carrierClass() {
            return carrierClass;
        }

        /**
         * {@return the constructor {@link MethodHandle} for the carrier. The
         * carrier constructor will always have a return type of {@link Object} }
         */
        MethodHandle constructor() {
            return constructor;
        }

        /**
         * {@return the initializer {@link MethodHandle} for the carrier}
         */
        MethodHandle initializer() {
            return initializer;
        }

        /**
         * Return the constructor plus initializer {@link MethodHandle} for the carrier.
         * The {@link MethodHandle} will always have a return type of {@link Object}.
         * @return the constructor plus initializer {@link MethodHandle}
         */
        MethodHandle initializingConstructor() {
            return MethodHandles.foldArguments(initializer, 0, constructor);
        }

        /**
         * {@return immutable list of component accessor {@link MethodHandle MethodHandles}
         * for all the carrier's components. The receiver type of the accessors
         * will always be {@link Object} }
         */
        List<MethodHandle> components() {
            return components;
        }

        /**
         * {@return a component accessor {@link MethodHandle} for component {@code i}.
         * The receiver type of the accessor will be {@link Object} }
         *
         * @param i  component index
         *
         * @throws IllegalArgumentException if {@code i} is out of bounds
         */
        MethodHandle component(int i) {
            if (i < 0 || components.size() <= i) {
                throw new IllegalArgumentException("i is out of bounds " + i +
                        " of " + components.size());
            }

            return components.get(i);
        }

        @Override
        public String toString() {
            return "Carrier" + constructor.type().parameterList();
        }
    }

    /**
     * {@return the underlying carrier class of the carrier representing {@code methodType} }
     *
     * @param methodType  {@link MethodType} whose parameter types supply the shape of the
     *                    carrier's components
     */
    static Class<?> carrierClass(MethodType methodType) {
        return CarrierFactory.of(methodType).carrierClass();
    }

    /**
     * {@return the constructor {@link MethodHandle} for the carrier representing {@code
     * methodType}. The carrier constructor will always have a return type of {@link Object} }
     *
     * @param methodType  {@link MethodType} whose parameter types supply the shape of the
     *                    carrier's components
     */
    static MethodHandle constructor(MethodType methodType) {
        MethodHandle constructor = CarrierFactory.of(methodType).constructor();
        constructor = constructor.asType(constructor.type().changeReturnType(Object.class));
        return constructor;
    }

    /**
     * {@return the initializer {@link MethodHandle} for the carrier representing {@code
     * methodType}. The carrier initializer will always take an {@link Object} along with
     * component values and a return type of {@link Object} }
     *
     * @param methodType  {@link MethodType} whose parameter types supply the shape of the
     *                    carrier's components
     */
    static MethodHandle initializer(MethodType methodType) {
        MethodHandle initializer = CarrierFactory.of(methodType).initializer();
        initializer = initializer.asType(initializer.type()
                .changeReturnType(Object.class).changeParameterType(0, Object.class));
        return initializer;
    }

    /**
     * {@return the combination {@link MethodHandle} of the constructor and initializer
     * for the carrier representing {@code methodType}. The carrier constructor/initializer
     * will always take the component values and a return type of {@link Object} }
     *
     * @param methodType  {@link MethodType} whose parameter types supply the shape of the
     *                    carrier's components
     */
    static MethodHandle initializingConstructor(MethodType methodType) {
        MethodHandle constructor = CarrierFactory.of(methodType).initializingConstructor();
        constructor = constructor.asType(constructor.type().changeReturnType(Object.class));
        return constructor;
    }

    /**
     * {@return immutable list of component accessor {@link MethodHandle MethodHandles} for
     * all the components of the carrier representing {@code methodType}. The receiver type of
     * the accessors will always be {@link Object} }
     *
     * @param methodType  {@link MethodType} whose parameter types supply the shape of the
     *                    carrier's components
     */
    static List<MethodHandle> components(MethodType methodType) {
        return CarrierFactory
                .of(methodType)
                .components()
                .stream()
                .map(c -> c.asType(c.type().changeParameterType(0, Object.class)))
                .toList();
    }

    /**
     * {@return a component accessor {@link MethodHandle} for component {@code i} of the
     * carrier representing {@code methodType}. The receiver type of the accessor will always
     * be {@link Object} }
     *
     * @param methodType  {@link MethodType} whose parameter types supply the shape of the
     *                    carrier's components
     * @param i           component index
     *
     * @throws IllegalArgumentException if {@code i} is out of bounds
     */
    static MethodHandle component(MethodType methodType, int i) {
        MethodHandle component = CarrierFactory.of(methodType).component(i);
        component = component.asType(component.type().changeParameterType(0, Object.class));
        return component;
    }

}
