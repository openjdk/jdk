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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.invoke.MethodType.methodType;

import jdk.internal.misc.Unsafe;
import jdk.internal.misc.VM;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.FieldVisitor;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Type;

import static jdk.internal.org.objectweb.asm.Opcodes.*;

/**
 * A <em>carrier</em> is an opaque object that can be used to store component values
 * while avoiding primitive boxing associated with collection objects. Component values
 * can be primitive or Object. Clients can create new carrier instances by describing a
 * carrier <em>shape</em>, that is, a {@linkplain MethodType method type} whose parameter
 * types describe the types of the carrier component values.
 *
 * @implNote The strategy for storing components is deliberately left ambiguous
 * so that future improvements will not be hampered by backward compatibility
 * issues.
 *
 * @since 19
 */
public final class Carrier {
    /**
     * Class file version.
     */
    private static final int CLASSFILE_VERSION = VM.classFileVersion();

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

    /**
     * Number of integer slots used by a long.
     */
    private static final int LONG_SLOTS = 2;

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
     * Cache mapping {@link MethodType} to previously defined
     * {@link Carrier Carriers}.
     */
    private static ConcurrentHashMap<MethodType, Carrier>
            methodTypeCache = new ConcurrentHashMap<>();

    /**
     * Constructor {@link MethodHandle}.
     */
    private final MethodHandle constructor;

    /**
     * List of component {@link MethodHandle MethodHandles}
     */
    private final List<MethodHandle> components;

    /**
     * Given a constructor {@link MethodHandle} recast and reorder arguments to
     * match shape.
     *
     * @param carrierShape  carrier shape
     * @param constructor   carrier constructor to reshape
     *
     * @return constructor with arguments recasted and reordered
     */
    private static MethodHandle reshapeConstructor(CarrierShape carrierShape,
                                                   MethodHandle constructor) {
        int count = carrierShape.count();
        Class<?>[] ptypes = carrierShape.ptypes();
        int objectIndex = carrierShape.objectOffset();
        int intIndex = carrierShape.intOffset();
        int longIndex = carrierShape.longOffset();
        int[] reorder = new int[count];
        Class<?>[] permutePTypes = new Class<?>[count];
        MethodHandle[] filters = new MethodHandle[count];
        boolean hasFilters = false;
        int index = 0;

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
     * Given components array, recast and reorder components to match shape.
     *
     * @param carrierShape  carrier reshape
     * @param components    carrier components to reshape
     *
     * @return list of components reshaped
     */
    private static List<MethodHandle> reshapeComponents(CarrierShape carrierShape,
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

            MethodType methodType = methodType(ptype, Object.class);
            reorder[index++] =
                    MethodHandles.explicitCastArguments(component, methodType);
        }

        return List.of(reorder);
    }

    /**
     * Factory for carriers that are backed by int[] and Object[]. This strategy is
     * used when the number of components exceeds {@link Carrier#MAX_OBJECT_COMPONENTS}.
     */
    private static class CarrierArrayFactory {
        /**
         * Unsafe access.
         */
        private static final Unsafe UNSAFE;

        static {
            try {
                UNSAFE = Unsafe.getUnsafe();
                Lookup lookup = MethodHandles.lookup();
                CONSTRUCTOR = lookup.findConstructor(CarrierArray.class,
                        methodType(void.class, int.class, int.class));
                GET_LONG = lookup.findVirtual(CarrierArray.class, "getLong",
                        methodType(long.class, int.class));
                PUT_LONG = lookup.findVirtual(CarrierArray.class, "putLong",
                        methodType(CarrierArray.class, int.class, long.class));
                GET_INTEGER = lookup.findVirtual(CarrierArray.class, "getInteger",
                        methodType(int.class, int.class));
                PUT_INTEGER = lookup.findVirtual(CarrierArray.class, "putInteger",
                        methodType(CarrierArray.class, int.class, int.class));
                GET_OBJECT = lookup.findVirtual(CarrierArray.class, "getObject",
                        methodType(Object.class, int.class));
                PUT_OBJECT = lookup.findVirtual(CarrierArray.class, "putObject",
                        methodType(CarrierArray.class, int.class, Object.class));
            } catch (ReflectiveOperationException ex) {
                throw new AssertionError("carrier static init fail", ex);
            }
        }

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

        /**
         * Wrapper object for carrier arrays. Instances types are stored in the {@code objects}
         * array, while primitive types are recast to {@code int/long} and stored in the
         * {@code primitives} array. Primitive byte, short, char, boolean and int are stored as
         * integers. Longs and doubles are stored as longs.  Longs take up the first part of the
         * primitives array using normal indices. Integers follow using int[] indices offset beyond
         * the longs using unsafe getInt/putInt.
         */
        private static class CarrierArray {
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
            CarrierArray(int primitiveCount, int objectCount) {
                this.primitives = new long[primitiveCount];
                this.objects = new Object[objectCount];
            }

            /**
             * Check index and compute offset for unsafe access.
             *
             * @param i  index in primitive[]
             *
             * @return offset for unsafe access
             */
            private long offset(int i) {
                if (i < 0 || primitives.length <= i) {
                    throw new RuntimeException("primitive index out of range: " + i);
                }

                return Unsafe.ARRAY_LONG_BASE_OFFSET +
                        (long)i * Unsafe.ARRAY_INT_INDEX_SCALE;
            }

            /**
             * {@return long value at index}
             *
             * @param i  array index
             */
            private long getLong(int i) {
                return primitives[i];
            }

            /**
             * Put a long value into the primitive[].
             *
             * @param i      array index
             * @param value  long value to store
             *
             * @return this object
             */
            private CarrierArray putLong(int i, long value) {
                primitives[i] = value;

                return this;
            }

            /**
             * {@return int value at index}
             *
             * @param i  array index
             */
            private int getInteger(int i) {
                return UNSAFE.getInt(primitives, offset(i));
            }

            /**
             * Put a int value into the int[].
             *
             * @param i      array index
             * @param value  int value to store
             *
             * @return this object
             */
            private CarrierArray putInteger(int i, int value) {
                UNSAFE.putInt(primitives, offset(i), value);

                return this;
            }

            /**
             * {@return Object value at index}
             *
             * @param i  array index
             */
            private Object getObject(int i) {
                return objects[i];
            }

            /**
             * Put a object value into the objects[].
             *
             * @param i      array index
             * @param value  object value to store
             *
             * @return this object
             */
            private CarrierArray putObject(int i, Object value) {
                objects[i] = value;

                return this;
            }
        }

        /**
         * Constructor
         *
         * @param carrierShape  carrier object shape
         *
         * @return {@link MethodHandle} to generic carrier constructor.
         */
        private static MethodHandle constructor(CarrierShape carrierShape) {
            int longCount = carrierShape.longCount();
            int intCount = carrierShape.intCount();
            int objectCount = carrierShape.objectCount();
            int primitiveSlots = longCount * LONG_SLOTS + intCount;

            MethodHandle constructor = MethodHandles.insertArguments(CONSTRUCTOR,
                    0, primitiveSlots, objectCount);

            // long array index
            int index = 0;
            for (int i = 0; i < longCount; i++) {
                MethodHandle put = MethodHandles.insertArguments(PUT_LONG, 1, index++);
                constructor = MethodHandles.collectArguments(put, 0, constructor);
            }

            // transition to int array index (double number of longs)
            index *= LONG_SLOTS;
            for (int i = 0; i < intCount; i++) {
                MethodHandle put = MethodHandles.insertArguments(PUT_INTEGER, 1, index++);
                constructor = MethodHandles.collectArguments(put, 0, constructor);
            }

            for (int i = 0; i < objectCount; i++) {
                MethodHandle put = MethodHandles.insertArguments(PUT_OBJECT, 1, i);
                constructor = MethodHandles.collectArguments(put, 0, constructor);
            }

            return constructor;
        }

        /**
         * Utility to construct the basic accessors from the components.
         *
         * @param carrierShape  carrier object shape
         *
         * @return array of carrier accessors
         */
        private static MethodHandle[] createComponents(CarrierShape carrierShape) {
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
         * Permute a raw constructor and component accessor
         * {@link MethodHandle MethodHandles} to match the order and types of
         * the parameter types.
         *
         * @param carrierShape  carrier object shape
         *
         * @return {@link Carrier} instance
         */
        private static Carrier carrier(CarrierShape carrierShape) {
            MethodHandle constructor = constructor(carrierShape);
            MethodHandle[] components = createComponents(carrierShape);

            return new Carrier(reshapeConstructor(carrierShape, constructor),
                               reshapeComponents(carrierShape, components));
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
     * of fields is reordered by the component accessor {@link MethodHandles}. So a
     * carrier requiring an int and then object will use the same anonymous class as
     * a carrier requiring an object then int.
     * <p>
     * The carrier constructor recasts/translates values that are not long, int or
     * object. The component accessors reverse the effect of the recasts/translates.
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
                Lookup lookup = MethodHandles.lookup();
                return lookup.defineHiddenClass(bytes, false, ClassOption.STRONG);
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
                arg += LONG_SLOTS;
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
         * Build up a {@link MethodType} based in a carrier shape.
         *
         * @param carrierShape  shape of carrier
         *
         * @return the constructor method type
         */
        private static MethodType constructorMethodType(CarrierShape carrierShape) {
            int longCount = carrierShape.longCount();
            int intCount = carrierShape.intCount();
            int objectCount = carrierShape.objectCount();
            int count = carrierShape.count();
            Class<?>[] ptypes = new Class<?>[count];
            int arg = 0;

            for(int i = 0; i < longCount; i++) {
                ptypes[arg++] = long.class;
            }

            for(int i = 0; i < intCount; i++) {
                ptypes[arg++] = int.class;
            }

            for(int i = 0; i < objectCount; i++) {
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
         * Returns an array of raw component accessors for the carrier class.
         *
         * @param carrierShape           shape of carrier
         * @param carrierClassLookup     lookup for carrier class
         * @param carrierClass           newly constructed carrier class
         *
         * @return {@link MethodHandle MethodHandles} to carrier component
         *         accessors
         *
         * @throws ReflectiveOperationException if lookup failure
         */
        private static MethodHandle[] createComponents(CarrierShape carrierShape,
                                                       Lookup carrierClassLookup,
                                                       Class<?> carrierClass)
                throws ReflectiveOperationException {
            int longCount = carrierShape.longCount();
            int intCount = carrierShape.intCount();
            int objectCount = carrierShape.objectCount();
            int count = carrierShape.count();
            MethodHandle[] components = new MethodHandle[count];
            int arg = 0;

            for(int i = 0; i < longCount; i++) {
                components[arg++] = carrierClassLookup.findGetter(carrierClass,
                        longFieldName(i), long.class);
            }

            for(int i = 0; i < intCount; i++) {
                components[arg++] = carrierClassLookup.findGetter(carrierClass,
                        intFieldName(i), int.class);
            }

            for(int i = 0; i < objectCount; i++) {
                components[arg++] = carrierClassLookup.findGetter(carrierClass,
                        objectFieldName(i), Object.class);
            }

            return components;
        }

        /**
         * Construct a new object carrier class based on shape.
         *
         * @param carrierShape  shape of carrier
         *
         * @return a {@link CarrierClass} object containing constructor and
         *         component accessors.
         */
        private static CarrierClass newCarrierClass(CarrierShape carrierShape) {
            byte[] bytes = buildCarrierClass(carrierShape);

            try {
                Lookup carrierCLassLookup = defineHiddenClass(bytes);
                Class<?> carrierClass = carrierCLassLookup.lookupClass();
                MethodType constructorMethodType = constructorMethodType(carrierShape);
                MethodHandle constructor = constructor(carrierCLassLookup,
                        carrierClass, constructorMethodType);
                MethodHandle[] components = createComponents(carrierShape,
                        carrierCLassLookup, carrierClass);

                return new CarrierClass(constructor, components);
            } catch (ReflectiveOperationException ex) {
                throw new AssertionError("carrier class static init fail", ex);
            }
        }

        /**
         * Permute a raw constructor and component accessor
         * {@link MethodHandle MethodHandles} to match the order and types of
         * the parameter types.
         *
         * @param carrierShape  carrier object shape
         *
         * @return {@link Carrier} instance
         */
        private static Carrier carrier(CarrierShape carrierShape) {
            CarrierClass carrierClass = findCarrierClass(carrierShape);
            MethodHandle constructor = carrierClass.constructor();
            MethodHandle[] components = carrierClass.components();

            return new Carrier(reshapeConstructor(carrierShape, constructor),
                               reshapeComponents(carrierShape, components));
        }
    }

    /**
     * Provides raw constructor and component MethodHandles for a constructed
     * carrier class.
     */
    private record CarrierClass(
            /*
             * A raw {@link MethodHandle} for a carrier object constructor.
             * This constructor will only have Object, int and long type arguments.
             */
            MethodHandle constructor,

            /*
             * All the raw {@link MethodHandle MethodHandles} for a carrier
             * component accessors. These accessors will only return Object, int and
             * long types.
             */
            MethodHandle[] components) {
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
        throw new AssertionError("private constructor");
    }

    /**
     * Constructor
     */
    private Carrier(MethodHandle constructor, List<MethodHandle> components) {
        this.constructor = constructor;
        this.components = components;
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
            this.counts = CarrierCounts.tally(methodType.parameterArray());
        }

        /**
         * {@return number of long fields needed}
         */
        private int longCount() {
            return counts.longCount();
        }

        /**
         * {@return number of int fields needed}
         */
        private int intCount() {
            return counts.intCount();
        }

        /**
         * {@return number of object fields needed}
         */
        private int objectCount() {
            return counts.objectCount();
        }

        /**
         * {@return array of parameter types}
         */
        private Class<?>[] ptypes() {
            return methodType.parameterArray();
        }

        /**
         * {@return number of components}
         */
        private int count() {
            return counts.count();
        }

        /**
         * {@return number of slots used}
         */
        private int slotCount() {
            return counts.slotCount();
        }

        /**
         * {@return index of first long component}
         */
        private int longOffset() {
            return 0;
        }

        /**
         * {@return index of first int component}
         */
        private int intOffset() {
            return longCount();
        }

        /**
         * {@return index of first object component}
         */
        private int objectOffset() {
            return longCount() + intCount();
        }

        /**
         * {@return true if primitive components present}
         */
        private boolean hasPrimitives() {
            return longCount() != 0 ||  intCount() != 0;
        }
    }

    /**
     * Factory method to return {@link Carrier} that matches the shape
     * of the supplied {@link MethodType}.
     *
     * @param methodType  {@link MethodType} providing types for the carrier's
     *                    components
     *
     * @return {@link Carrier} instance
     *
     * @throws NullPointerException is methodType is null
     * @throws IllegalArgumentException if number of component slots exceeds maximum
     */
    public static Carrier of(MethodType methodType) {
        Objects.requireNonNull(methodType, "methodType must be not be null");
        MethodType constructorMT = methodType.changeReturnType(Object.class);
        CarrierShape carrierShape = new CarrierShape(constructorMT);
        int slotCount = carrierShape.slotCount();

        if (MAX_COMPONENTS < slotCount) {
            throw new IllegalArgumentException("Exceeds maximum number of component slots");
        }

        return methodTypeCache.computeIfAbsent(constructorMT, (mt) -> {
            if (slotCount <= MAX_OBJECT_COMPONENTS && carrierShape.hasPrimitives()) {
                return CarrierObjectFactory.carrier(carrierShape);
            } else {
                return CarrierArrayFactory.carrier(carrierShape);
            }
        });
    }

    /**
     * Factory method to return {@link Carrier} that matches the shape
     * of the supplied parameter types.
     *
     * @param ptypes   types of the carrier's components
     *
     * @return {@link Carrier} instance
     *
     * @throws NullPointerException is ptypes is null
     * @throws IllegalArgumentException if number of component slots exceeds maximum
     */
    public static Carrier of(Class<?>... ptypes) {
        Objects.requireNonNull(ptypes, "ptypes must be not be null");
        return of(methodType(Object.class, ptypes));
    }

    /**
     * {@return the constructor {@link MethodHandle} for the carrier.}
     */
    public MethodHandle constructor() {
        return constructor;
    }

    /**
     * {@return immutable list of component accessor {@link MethodHandle MethodHandles}
     * for all the carrier's components.}
     */
    public List<MethodHandle> components() {
        return components;
    }

    /**
     * {@return a component accessor {@link MethodHandle} for component {@code i}.}
     *
     * @param i  component index
     *
     * @throws IllegalArgumentException if {@code i} is out of bounds
     */
    public MethodHandle component(int i) {
        if (i < 0 || components.size() <= i) {
            throw new IllegalArgumentException("i is out of bounds " + i +
                    " of " + components.size());
        }

        return components.get(i);
    }
}
