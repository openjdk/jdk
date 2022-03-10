/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.invoke.MethodHandles.Lookup.ClassOption;
import java.lang.invoke.MethodType;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.invoke.MethodType.methodType;

import jdk.internal.misc.VM;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.FieldVisitor;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Type;

import static jdk.internal.org.objectweb.asm.Opcodes.*;

/**
 * This  class is used to create anonymous objects that have number and types of
 * components determined at runtime.
 *
 * @implNote The strategy for storing components is deliberately left ambiguous
 * so that future improvements will not be hampered by issues of backward
 * compatability.
 *
 * @since 19
 */
/*non-public*/
final class Carrier {
    /**
     * Class file version.
     */
    private static final int CLASSFILE_VERSION = VM.classFileVersion();

    /**
     * Lookup used to define and reference the carrier object classes.
     */
    private static final Lookup LOOKUP;

    /**
     * Maximum number of components in a carrier (based on the maximum
     * number of args to a constructor.)
     */
    private static final int MAX_COMPONENTS = 255 - /* this */ 1;

    /**
     * Maximum number of components in a CarrierClass.
     */
    private static final int MAX_OBJECT_COMPONENTS = 32;

    /**
     * Stable annotation.
     */
    private static final String STABLE = "jdk/internal/vm/annotation/Stable";
    private static final String STABLE_SIG = "L" + STABLE + ";";

    /*
     * Initialize {@link MethodHandle} constants.
     */
    static {
        Lookup lookup = MethodHandles.lookup();
        LOOKUP = lookup;

        try {
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
     * long signature descriptor.
     */
    private static final String LONG_DESCRIPTOR =
            Type.getDescriptor(long.class);

    /**
     * int signature descriptor.
     */
    private static final String INT_DESCRIPTOR =
            Type.getDescriptor(int.class);

    /**
     * Object signature descriptor.
     */
    private static final String OBJECT_DESCRIPTOR =
            Type.getDescriptor(Object.class);

    /**
     * Factory for carriers that are backed by an Object[]. This strategy is used when
     * the number of components exceeds {@link Carrier#MAX_OBJECT_COMPONENTS}. The
     * object returned by the carrier constructor is the backing Object[].
     * <p>
     * Each element of the Object[] corresponds directly, via index, to each component.
     * If the component is a primitive value then the constructor boxes the value before
     * inserting into the array, and the carrier component getter unboxes.
     */
    private static class CarrierArrayFactory {
        /**
         * Constructor
         *
         * @param carrierShape  carrier object shape
         *
         * @return {@link MethodHandle} to generic carrier constructor.
         */
        private static MethodHandle constructor(CarrierShape carrierShape) {
            Class<?>[] ptypes = carrierShape.ptypes();
            MethodType methodType = methodType(Object.class, ptypes);
            MethodHandle collector = MethodHandles.identity(Object[].class)
                    .withVarargs(true);

            return collector.asType(methodType);
        }

        /**
         * Return an array of carrier component getters, aligning with types in
         * {@code ptypes}.
         *
         * @param carrierShape  carrier object shape
         *
         * @return array of carrier getters
         */
        private static MethodHandle[] components(CarrierShape carrierShape) {
            Class<?>[] ptypes = carrierShape.ptypes();
            int length = ptypes.length;
            MethodHandle[] getters = new MethodHandle[length];

            for (int i = 0; i < length; i++) {
                getters[i] = component(carrierShape, i);
            }

            return getters;
        }

        /**
         * Return a carrier getter for component {@code i}.
         *
         * @param carrierShape  carrier object shape
         * @param i             index of parameter to get
         *
         * @return carrier component {@code i} getter {@link MethodHandle}
         */
        private static MethodHandle component(CarrierShape carrierShape, int i) {
            Class<?>[] ptypes = carrierShape.ptypes();
            MethodType methodType = methodType(ptypes[i], Object.class);
            MethodHandle getter =
                    MethodHandles.arrayElementGetter(Object[].class);

            return MethodHandles.insertArguments(
                    getter, 1, i).asType(methodType);
        }
    }

    /**
     * Factory for object based carrier. This strategy is used when the number of
     * components is less than equal {@link Carrier#MAX_OBJECT_COMPONENTS}. The factory
     * constructs an anonymous class that provides a shape that  matches the
     * number of longs, ints and objects required by the {@link CarrierShape}. The
     * factory caches and reuses anonymous classes when looking for a match.
     * <p>
     * The anonymous class that is constructed contains the number of long fields then
     * int fields then object fields required by the {@link CarrierShape}. The order
     * of fields is reordered by the component getter {@link MethodHandles}. So a
     * carrier requiring an int and then object will use the same anonymous class as
     * a carrier requiring an object then int.
     * <p>
     * The carrier constructor recasts/translates values that are not long, int or
     * object. The component getters reverse the effect of the recasts/translates.
     */
    private static class CarrierObjectFactory {
        /**
         * Define the hidden class Lookup object
         *
         * @param bytes  class content
         *
         * @return the Lookup object of the hidden class
         */
        private static Lookup defineHiddenClass(byte[] bytes) {
            try {
                return LOOKUP.defineHiddenClass(bytes, false, ClassOption.STRONG);
            } catch (IllegalAccessException ex) {
                throw new AssertionError("carrier factory static init fail", ex);
            }
        }

        /**
         * Generate the name of a long component.
         *
         * @param index field/component index
         *
         * @return name of long component
         */
        private static String longFieldName(int index) {
            return "l" + index;
        }

        /**
         * Generate the name of an int component.
         *
         * @param index field/component index
         *
         * @return name of int component
         */
        private static String intFieldName(int index) {
            return "i" + index;
        }

        /**
         * Generate the name of an object component.
         *
         * @param index field/component index
         *
         * @return name of object component
         */
        private static String objectFieldName(int index) {
            return "o" + index;
        }

        /**
         * Generate the full name of a carrier class based on shape.
         *
         * @param carrierShape  shape of carrier
         *
         * @return name of a carrier class
         */
        private static String carrierClassName(CarrierShape carrierShape) {
            String packageName = Carrier.class.getPackageName().replace('.', '/');
            String className = "Carrier" +
                    longFieldName(carrierShape.longCount()) +
                    intFieldName(carrierShape.intCount()) +
                    objectFieldName(carrierShape.objectCount());

            return packageName.isEmpty() ? className :
                    packageName + "/" + className;
        }

        /**
         * Build up the byte code for the carrier class.
         *
         * @param carrierShape  shape of carrier
         *
         * @return byte array of byte code for the carrier class
         */
        private static byte[] buildCarrierClass(CarrierShape carrierShape) {
            int maxStack = 3;
            int maxLocals = 1 /* this */ + carrierShape.slotCount();
            String carrierClassName = carrierClassName(carrierShape);
            StringBuilder initDescriptor = new StringBuilder("(");

            ClassWriter cw = new ClassWriter(0);
            cw.visit(CLASSFILE_VERSION, ACC_PRIVATE | ACC_FINAL, carrierClassName,
                    null, "java/lang/Object", null);

            int fieldFlags = ACC_PRIVATE | ACC_FINAL;

            for (int i = 0; i < carrierShape.longCount(); i++) {
                FieldVisitor fw = cw.visitField(fieldFlags, longFieldName(i),
                        LONG_DESCRIPTOR, null, null);
                fw.visitAnnotation(STABLE_SIG, true);
                fw.visitEnd();
                initDescriptor.append(LONG_DESCRIPTOR);
            }

            for (int i = 0; i < carrierShape.intCount(); i++) {
                FieldVisitor fw = cw.visitField(fieldFlags, intFieldName(i),
                        INT_DESCRIPTOR, null, null);
                fw.visitAnnotation(STABLE_SIG, true);
                fw.visitEnd();
                initDescriptor.append(INT_DESCRIPTOR);
            }

            for (int i = 0; i < carrierShape.objectCount(); i++) {
                FieldVisitor fw = cw.visitField(fieldFlags, objectFieldName(i),
                        OBJECT_DESCRIPTOR, null, null);
                fw.visitAnnotation(STABLE_SIG, true);
                fw.visitEnd();
                initDescriptor.append(OBJECT_DESCRIPTOR);
            }

            initDescriptor.append(")V");

            int arg = 1;

            MethodVisitor init = cw.visitMethod(ACC_PUBLIC,
                    "<init>", initDescriptor.toString(), null, null);
            init.visitVarInsn(ALOAD, 0);
            init.visitMethodInsn(INVOKESPECIAL,
                    "java/lang/Object", "<init>", "()V", false);

            for (int i = 0; i < carrierShape.longCount(); i++) {
                init.visitVarInsn(ALOAD, 0);
                init.visitVarInsn(LLOAD, arg);
                arg += 2;
                init.visitFieldInsn(PUTFIELD, carrierClassName,
                        longFieldName(i), LONG_DESCRIPTOR);
            }

            for (int i = 0; i < carrierShape.intCount(); i++) {
                init.visitVarInsn(ALOAD, 0);
                init.visitVarInsn(ILOAD, arg++);
                init.visitFieldInsn(PUTFIELD, carrierClassName,
                        intFieldName(i), INT_DESCRIPTOR);
            }

            for (int i = 0; i < carrierShape.objectCount(); i++) {
                init.visitVarInsn(ALOAD, 0);
                init.visitVarInsn(ALOAD, arg++);
                init.visitFieldInsn(PUTFIELD, carrierClassName,
                        objectFieldName(i), OBJECT_DESCRIPTOR);
            }

            init.visitInsn(RETURN);
            init.visitMaxs(maxStack, maxLocals);
            init.visitEnd();

            cw.visitEnd();

            return cw.toByteArray();
        }

        /**
         * Returns the constructor method type.
         *
         * @return the constructor method type.
         */
        private static MethodType constructorMethodType(CarrierShape carrierShape) {
            int objectCount = carrierShape.objectCount();
            int intCount = carrierShape.intCount();
            int longCount = carrierShape.longCount();
            int argCount = objectCount + intCount + longCount;
            Class<?>[] ptypes = new Class<?>[argCount];
            int arg = 0;

            for(int i = 0; i < carrierShape.longCount(); i++) {
                ptypes[arg++] = long.class;
            }

            for(int i = 0; i < carrierShape.intCount(); i++) {
                ptypes[arg++] = int.class;
            }

            for(int i = 0; i < carrierShape.objectCount(); i++) {
                ptypes[arg++] = Object.class;
            }

            return methodType(void.class, ptypes);
        }

        /**
         * Returns the raw constructor for the carrier class.
         *
         * @param carrierClassLookup     lookup for carrier class
         * @param carrierClass           newly constructed carrier class
         * @param constructorMethodType  constructor method type
         *
         * @return {@link MethodHandle} to carrier class constructor
         *
         * @throws ReflectiveOperationException if lookup failure
         */
        private static MethodHandle constructor(Lookup carrierClassLookup,
                                                Class<?> carrierClass,
                                                MethodType constructorMethodType)
                throws ReflectiveOperationException {
            return carrierClassLookup.findConstructor(carrierClass,
                    constructorMethodType);
        }

        /**
         * Returns an array of raw component getters for the carrier class.
         *
         * @param carrierShape           shape of carrier
         * @param carrierClassLookup     lookup for carrier class
         * @param carrierClass           newly constructed carrier class
         * @param constructorMethodType  constructor method type
         *
         * @return {@link MethodHandle MethodHandles} to carrier component
         *         getters
         *
         * @throws ReflectiveOperationException if lookup failure
         */
        private static MethodHandle[] components(CarrierShape carrierShape,
                                                 Lookup carrierClassLookup,
                                                 Class<?> carrierClass,
                                                 MethodType constructorMethodType)
                throws ReflectiveOperationException {
            MethodHandle[] components;
            components = new MethodHandle[constructorMethodType.parameterCount()];
            int arg = 0;

            for(int i = 0; i < carrierShape.longCount(); i++) {
                components[arg++] = carrierClassLookup.findGetter(carrierClass,
                        CarrierObjectFactory.longFieldName(i), long.class);
            }

            for(int i = 0; i < carrierShape.intCount(); i++) {
                components[arg++] = carrierClassLookup.findGetter(carrierClass,
                        CarrierObjectFactory.intFieldName(i), int.class);
            }

            for(int i = 0; i < carrierShape.objectCount(); i++) {
                components[arg++] = carrierClassLookup.findGetter(carrierClass,
                        CarrierObjectFactory.objectFieldName(i), Object.class);
            }

            return components;
        }

        /**
         * Construct a new object carrier class based on shape.
         *
         * @param carrierShape  shape of carrier
         *
         * @return a {@link CarrierClass} object containing constructor and
         *         component getters.
         */
        private static CarrierClass newCarrierClass(CarrierShape carrierShape) {
            byte[] bytes = buildCarrierClass(carrierShape);

            try {
                Lookup carrierCLassLookup = defineHiddenClass(bytes);
                Class<?> carrierClass = carrierCLassLookup.lookupClass();
                MethodType constructorMethodType = constructorMethodType(carrierShape);
                MethodHandle constructor = constructor(carrierCLassLookup,
                        carrierClass, constructorMethodType);
                MethodHandle[] components = components(carrierShape,
                        carrierCLassLookup, carrierClass, constructorMethodType);

                return new CarrierClass(constructor, components);
            } catch (ReflectiveOperationException ex) {
                throw new AssertionError("carrier class static init fail", ex);
            }
        }

        /**
         * Permute a raw constructor {@link MethodHandle} to match the order and
         * types of the parameter types.
         *
         * @param carrierShape  carrier object shape
         *
         * @return {@link MethodHandle} constructor matching parameter types
         */
        private static MethodHandle constructor(CarrierShape carrierShape) {
            Class<?>[] ptypes = carrierShape.ptypes();
            int length = ptypes.length;
            int objectIndex = carrierShape.objectOffset();
            int intIndex = carrierShape.intOffset();
            int longIndex = carrierShape.longOffset();
            int[] reorder = new int[length];
            Class<?>[] permutePTypes = new Class<?>[length];
            MethodHandle[] filters = new MethodHandle[length];
            boolean hasFilters = false;
            int index = 0;

            for (Class<?> ptype : ptypes) {
                MethodHandle filter = null;
                int from;

                if (!ptype.isPrimitive()) {
                    from = objectIndex++;
                    ptype = Object.class;
                } else if(ptype == double.class) {
                    from = longIndex++;
                    filter = DOUBLE_TO_LONG;
                } else if(ptype == float.class) {
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

            CarrierClass carrierClass = findCarrierClass(carrierShape);
            MethodHandle constructor = carrierClass.constructor();

            if (hasFilters) {
                constructor = MethodHandles.filterArguments(constructor, 0, filters);
            }

            MethodType permutedMethodType =
                    methodType(constructor.type().returnType(), permutePTypes);
            constructor = MethodHandles.permuteArguments(constructor,
                    permutedMethodType, reorder);
            constructor = MethodHandles.explicitCastArguments(constructor,
                    methodType(Object.class, ptypes));

            return constructor;
        }

        /**
         * Permute raw component getters to match order and types of the parameter
         * types.
         *
         * @param carrierShape  carrier object shape
         *
         * @return array of components matching parameter types
         */
        private static MethodHandle[] components(CarrierShape carrierShape) {
            Class<?>[] ptypes = carrierShape.ptypes();
            MethodHandle[] reorder = new MethodHandle[ptypes.length];
            int objectIndex = carrierShape.objectOffset();
            int intIndex = carrierShape.intOffset();
            int longIndex = carrierShape.longOffset();
            int index = 0;
            CarrierClass carrierClass = findCarrierClass(carrierShape);
            MethodHandle[] components = carrierClass.components();

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

                MethodType methodType = methodType(ptype, Object.class);
                reorder[index++] =
                        MethodHandles.explicitCastArguments(component, methodType);
            }

            return reorder;
        }

        /**
         * Returns a carrier component getter {@link MethodHandle} for the
         * component {@code i}.
         *
         * @param carrierShape  shape of the carrier object
         * @param i             index to the component
         *
         * @return carrier component getter {@link MethodHandle}
         *
         * @throws IllegalArgumentException if number of component slots exceeds
         *         maximum
         */
        private static MethodHandle component(CarrierShape carrierShape, int i) {
            Class<?>[] ptypes = carrierShape.ptypes();
            CarrierCounts componentCounts = CarrierCounts.count(ptypes, i);
            Class<?> ptype = ptypes[i];
            int index;
            MethodHandle filter = null;

            if (!ptype.isPrimitive()) {
                index = carrierShape.objectOffset() + componentCounts.objectCount();
            } else if (ptype == double.class) {
                index = carrierShape.longOffset() + componentCounts.longCount();
                filter = LONG_TO_DOUBLE;
            } else if (ptype == float.class) {
                index = carrierShape.intOffset() + componentCounts.intCount();
                filter = INT_TO_FLOAT;
            } else if (ptype == long.class) {
                index = carrierShape.longOffset() + componentCounts.longCount();
            } else {
                index = carrierShape.intOffset() + componentCounts.intCount();
            }

            CarrierClass carrierClass = findCarrierClass(carrierShape);
            MethodHandle component = carrierClass.component(index);

            if (filter != null) {
                component = MethodHandles.filterReturnValue(component, filter);
            }

            component = MethodHandles.explicitCastArguments(component,
                    methodType(ptype, Object.class));

            return component;
        }
    }

    /**
     * Provides raw constructor and component MethodHandles for a constructed
     * carrier class.
     */
    private record CarrierClass(
            /**
             * A raw {@link MethodHandle} for a carrier object constructor.
             * This constructor will only have Object, int and long type arguments.
             */
            MethodHandle constructor,

            /**
             * All the raw {@link MethodHandle MethodHandles} for a carrier
             * component getters. These getters will only return Object, int and
             * long types.
             */
            MethodHandle[] components) {

        /**
         * Create a single raw {@link MethodHandle} for a carrier component
         * getter.
         *
         * @param i  index of component to get
         *
         * @return raw {@link MethodHandle} for the component getter
         */
        MethodHandle component(int i) {
            return components[i];
        }
    }

    /**
     * Cache for all constructed carrier object classes, keyed on class
     * name (i.e., carrier shape.)
     */
    private static final ConcurrentHashMap<String, CarrierClass> carrierCache =
            new ConcurrentHashMap<>();

    /**
     * Constructor
     */
    private Carrier() {
    }

    /**
     * Find or create carrier class for a carrier shape.
     *
     * @param carrierShape  shape of carrier
     *
     * @return {@link Class<>} of carrier class matching carrier shape
     */
    private static CarrierClass findCarrierClass(CarrierShape carrierShape) {
        String carrierClassName =
                CarrierObjectFactory.carrierClassName(carrierShape);

        return carrierCache.computeIfAbsent(carrierClassName,
                cn -> CarrierObjectFactory.newCarrierClass(carrierShape));
    }

    private record CarrierCounts(int longCount, int intCount, int objectCount) {
        /**
         * Count the number of fields required in each of Object, int and long.
         *
         * @param ptypes  parameter types
         *
         * @return a {@link CarrierCounts} instance containing counts
         */
        static CarrierCounts count(Class<?>[] ptypes) {
            return count(ptypes, ptypes.length);
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
        private static CarrierCounts count(Class<?>[] ptypes, int n) {
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
         * Returns total number of slots.
         *
         * @return total number of slots
         */
        private int slotCount() {
            return longCount * 2 + intCount + objectCount;
        }

    }

    /**
     * Shape of carrier based on counts of each of the three fundamental data
     * types.
     */
    private static class CarrierShape {
        /**
         * {@link MethodType} providing types for the carrier's components.
         */
        private final MethodType methodType;

        /**
         * Counts of different parameter types.
         */
        private final CarrierCounts counts;

        /**
         * Constructor.
         *
         * @param methodType  {@link MethodType} providing types for the
         *                    carrier's components
         */
        public CarrierShape(MethodType methodType) {
            this.methodType = methodType;
            this.counts = CarrierCounts.count(methodType.parameterArray());
        }

        /**
         * Return supplied methodType.
         *
         * @return supplied methodType
         */
        private MethodType methodType() {
            return methodType;
        }

        /**
         * Return the number of long fields needed.
         *
         * @return number of long fields needed
         */
        private int longCount() {
            return counts.longCount();
        }

        /**
         * Return the number of int fields needed.
         *
         * @return number of int fields needed
         */
        private int intCount() {
            return counts.intCount();
        }

        /**
         * Return the number of object fields needed.
         *
         * @return number of object fields needed
         */
        private int objectCount() {
            return counts.objectCount();
        }

        /**
         * Return parameter types.
         *
         * @return array of parameter types
         */
        private Class<?>[] ptypes() {
            return methodType.parameterArray();
        }

        /**
         * Return number of constructor parameters.
         *
         * @return number of constructor parameters
         */
        private int parameterCount() {
            return methodType.parameterCount();
        }

        /**
         * Total number of slots used in a {@link CarrierClass} instance.
         *
         * @return number of slots used
         */
        private int slotCount() {
            return counts.slotCount();
        }

        /**
         * Returns index of first long component.
         *
         * @return index of first long component
         */
        private int longOffset() {
            return 0;
        }

        /**
         * Returns index of first int component.
         *
         * @return index of first int component
         */
        private int intOffset() {
            return longCount();
        }

        /**
         * Returns index of first object component.
         *
         * @return index of first object component
         */
        private int objectOffset() {
            return longCount() + intCount();
        }

    }

    /**
     * Return a constructor {@link MethodHandle} for a carrier with components
     * aligning with the parameter types of the supplied
     * {@link MethodType methodType}.
     *
     * @param methodType  {@link MethodType} providing types for the carrier's
     *                    components
     *
     * @return carrier constructor {@link MethodHandle}
     *
     * @throws NullPointerException is any argument is null
     * @throws IllegalArgumentException if number of component slots exceeds maximum
     */
    public static MethodHandle constructor(MethodType methodType) {
        Objects.requireNonNull(methodType);
        CarrierShape carrierShape = new CarrierShape(methodType);
        int slotCount = carrierShape.slotCount();

        if (MAX_COMPONENTS < slotCount) {
            throw new IllegalArgumentException("Exceeds maximum number of component slots");
        } else  if (slotCount <= MAX_OBJECT_COMPONENTS) {
            return CarrierObjectFactory.constructor(carrierShape);
        } else {
            return CarrierArrayFactory.constructor(carrierShape);
        }
    }

    /**
     * Return component getter {@link MethodHandle MethodHandles} for all the
     * carrier's components.
     *
     * @param methodType  {@link MethodType} providing types for the carrier's
     *                    components
     *
     * @return  array of get component {@link MethodHandle MethodHandles,}
     *
     * @throws NullPointerException is any argument is null
     * @throws IllegalArgumentException if number of component slots exceeds maximum
     *
     */
    public static MethodHandle[] components(MethodType methodType) {
        Objects.requireNonNull(methodType);
        CarrierShape carrierShape =  new CarrierShape(methodType);
        int slotCount = carrierShape.slotCount();

        if (MAX_COMPONENTS < slotCount) {
            throw new IllegalArgumentException("Exceeds maximum number of component slots");
        } else  if (slotCount <= MAX_OBJECT_COMPONENTS) {
            return CarrierObjectFactory.components(carrierShape);
        } else {
            return Carrier.CarrierArrayFactory.components(carrierShape);
        }
    }

    /**
     * Return a component getter {@link MethodHandle} for component {@code i}.
     *
     * @param methodType  {@link MethodType} providing types for the carrier's
     *                    components
     * @param i           component index
     *
     * @return a component getter {@link MethodHandle} for component {@code i}
     *
     * @throws NullPointerException is any argument is null
     * @throws IllegalArgumentException if number of component slots exceeds maximum
     *                                  or if {@code i} is out of bounds
     */
    public static MethodHandle component(MethodType methodType, int i) {
        Objects.requireNonNull(methodType);
        CarrierShape carrierShape = new CarrierShape(methodType);
        int slotCount = carrierShape.slotCount();

        if (i < 0 || i >= carrierShape.parameterCount()) {
            throw new IllegalArgumentException("i is out of bounds for parameter types");
        } else if (MAX_COMPONENTS < slotCount) {
            throw new IllegalArgumentException("Exceeds maximum number of component slots");
        } else  if (slotCount <= MAX_OBJECT_COMPONENTS) {
            return CarrierObjectFactory.component(carrierShape, i);
        } else {
            return CarrierArrayFactory.component(carrierShape, i);
        }
    }
}
