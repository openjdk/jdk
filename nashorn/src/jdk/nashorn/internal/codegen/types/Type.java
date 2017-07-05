/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.codegen.types;

import static jdk.internal.org.objectweb.asm.Opcodes.DALOAD;
import static jdk.internal.org.objectweb.asm.Opcodes.DASTORE;
import static jdk.internal.org.objectweb.asm.Opcodes.DUP;
import static jdk.internal.org.objectweb.asm.Opcodes.DUP2;
import static jdk.internal.org.objectweb.asm.Opcodes.DUP2_X1;
import static jdk.internal.org.objectweb.asm.Opcodes.DUP2_X2;
import static jdk.internal.org.objectweb.asm.Opcodes.DUP_X1;
import static jdk.internal.org.objectweb.asm.Opcodes.DUP_X2;
import static jdk.internal.org.objectweb.asm.Opcodes.IALOAD;
import static jdk.internal.org.objectweb.asm.Opcodes.IASTORE;
import static jdk.internal.org.objectweb.asm.Opcodes.INVOKESTATIC;
import static jdk.internal.org.objectweb.asm.Opcodes.LALOAD;
import static jdk.internal.org.objectweb.asm.Opcodes.LASTORE;
import static jdk.internal.org.objectweb.asm.Opcodes.NEWARRAY;
import static jdk.internal.org.objectweb.asm.Opcodes.POP;
import static jdk.internal.org.objectweb.asm.Opcodes.POP2;
import static jdk.internal.org.objectweb.asm.Opcodes.SWAP;
import static jdk.internal.org.objectweb.asm.Opcodes.T_DOUBLE;
import static jdk.internal.org.objectweb.asm.Opcodes.T_INT;
import static jdk.internal.org.objectweb.asm.Opcodes.T_LONG;

import java.lang.invoke.MethodHandle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.nashorn.internal.codegen.CompilerConstants.Call;


/**
 * This is the representation of a JavaScript type, disassociated from java
 * Classes, with the basis for conversion weight, mapping to ASM types
 * and implementing the ByteCodeOps interface which tells this type
 * how to generate code for various operations.
 *
 * Except for ClassEmitter, this is the only class that has to know
 * about the underlying byte code generation system.
 *
 * The different types know how to generate bytecode for the different
 * operations, inherited from BytecodeOps, that they support. This avoids
 * if/else chains depending on type in several cases and allows for
 * more readable and shorter code
 *
 * The Type class also contains logic used by the type inference and
 * for comparing types against each other, as well as the concepts
 * of narrower to wider types. The widest type is an object. Ideally we
 * would like as narrow types as possible for code to be efficient, e.g
 * INTs rather than OBJECTs
 */

public abstract class Type implements Comparable<Type>, BytecodeOps {

    /** Human readable name for type */
    private final String name;

    /** Descriptor for type */
    private final String descriptor;

    /** The "weight" of the type. Used for picking widest/least specific common type */
    private final int weight;

    /** How many bytecode slots does this type occupy */
    private final int slots;

    /** The class for this type */
    private final Class<?> clazz;

    /** Weights are used to decide which types are "wider" than other types */
    protected static final int MIN_WEIGHT = -1;

    /** Set way below Integer.MAX_VALUE to prevent overflow when adding weights. Objects are still heaviest. */
    protected static final int MAX_WEIGHT = 20;

    /**
     * Constructor
     *
     * @param clazz       class for type
     * @param weight      weight - higher is more generic
     * @param slots       how many bytecode slots the type takes up
     */
    Type(final String name, final Class<?> clazz, final int weight, final int slots) {
        this.name       = name;
        this.clazz      = clazz;
        this.descriptor = jdk.internal.org.objectweb.asm.Type.getDescriptor(clazz);
        this.weight     = weight;
        assert weight >= MIN_WEIGHT && weight <= MAX_WEIGHT : "illegal type weight: " + weight;
        this.slots      = slots;
    }

    /**
     * Get the weight of this type - use this e.g. for sorting method descriptors
     * @return the weight
     */
    public int getWeight() {
        return weight;
    }

    /**
     * Get the Class representing this type
     * @return the class for this type
     */
    public Class<?> getTypeClass() {
        return clazz;
    }

    /**
     * For specialization, return the next, slightly more difficulty, type
     * to test.
     *
     * @return the next Type
     */
    public Type nextWider() {
        return null;
    }

    /**
     * Get the boxed type for this class
     * @return the boxed version of this type or null if N/A
     */
    public Class<?> getBoxedType() {
        assert !getTypeClass().isPrimitive();
        return null;
    }

    /**
     * Generate a method descriptor given a return type and a param array
     *
     * @param returnType return type
     * @param types      parameters
     *
     * @return a descriptor string
     */
    public static String getMethodDescriptor(final Type returnType, final Type... types) {
        final jdk.internal.org.objectweb.asm.Type[] itypes = new jdk.internal.org.objectweb.asm.Type[types.length];
        for (int i = 0; i < types.length; i++) {
            itypes[i] = types[i].getInternalType();
        }
        return jdk.internal.org.objectweb.asm.Type.getMethodDescriptor(returnType.getInternalType(), itypes);
    }

    /**
     * Generate a method descriptor given a return type and a param array
     *
     * @param returnType return type
     * @param types      parameters
     *
     * @return a descriptor string
     */
    public static String getMethodDescriptor(final Class<?> returnType, final Class<?>... types) {
        final jdk.internal.org.objectweb.asm.Type[] itypes = new jdk.internal.org.objectweb.asm.Type[types.length];
        for (int i = 0; i < types.length; i++) {
            itypes[i] = getInternalType(types[i]);
        }
        return jdk.internal.org.objectweb.asm.Type.getMethodDescriptor(getInternalType(returnType), itypes);
    }

    /**
     * Return the type for an internal type, package private - do not use
     * outside code gen
     *
     * @param itype internal type
     * @return Nashorn type
     */
    @SuppressWarnings("fallthrough")
    static Type typeFor(final jdk.internal.org.objectweb.asm.Type itype) {
        switch (itype.getSort()) {
        case jdk.internal.org.objectweb.asm.Type.BOOLEAN:
            return BOOLEAN;
        case jdk.internal.org.objectweb.asm.Type.INT:
            return INT;
        case jdk.internal.org.objectweb.asm.Type.LONG:
            return LONG;
        case jdk.internal.org.objectweb.asm.Type.DOUBLE:
            return NUMBER;
        case jdk.internal.org.objectweb.asm.Type.OBJECT:
            return OBJECT;
        case jdk.internal.org.objectweb.asm.Type.VOID:
            return null;
        case jdk.internal.org.objectweb.asm.Type.ARRAY:
            switch (itype.getElementType().getSort()) {
            case jdk.internal.org.objectweb.asm.Type.DOUBLE:
                return NUMBER_ARRAY;
            case jdk.internal.org.objectweb.asm.Type.INT:
                return INT_ARRAY;
            case jdk.internal.org.objectweb.asm.Type.LONG:
                return LONG_ARRAY;
            default:
                assert false;
            case jdk.internal.org.objectweb.asm.Type.OBJECT:
                return OBJECT_ARRAY;
            }

        default:
            assert false : "Unknown itype : " + itype + " sort " + itype.getSort();
            break;
        }
        return null;
    }

    /**
     * Get the return type for a method
     *
     * @param methodDescriptor method descriptor
     * @return return type
     */
    public static Type getMethodReturnType(final String methodDescriptor) {
        return Type.typeFor(jdk.internal.org.objectweb.asm.Type.getReturnType(methodDescriptor));
    }

    /**
     * Get type array representing arguments of a method in order
     *
     * @param methodDescriptor method descriptor
     * @return parameter type array
     */
    public static Type[] getMethodArguments(final String methodDescriptor) {
        final jdk.internal.org.objectweb.asm.Type itypes[] = jdk.internal.org.objectweb.asm.Type.getArgumentTypes(methodDescriptor);
        final Type types[] = new Type[itypes.length];
        for (int i = 0; i < itypes.length; i++) {
            types[i] = Type.typeFor(itypes[i]);
        }
        return types;
    }

    static jdk.internal.org.objectweb.asm.Type getInternalType(final String className) {
        return jdk.internal.org.objectweb.asm.Type.getType(className);
    }

    private jdk.internal.org.objectweb.asm.Type getInternalType() {
        return jdk.internal.org.objectweb.asm.Type.getType(getTypeClass());
    }

    private static jdk.internal.org.objectweb.asm.Type getInternalType(final Class<?> type) {
        return jdk.internal.org.objectweb.asm.Type.getType(type);
    }

    static void invokeStatic(final MethodVisitor method, final Call call) {
        method.visitMethodInsn(INVOKESTATIC, call.className(), call.name(), call.descriptor(), false);
    }

    /**
     * Get the internal JVM name of a type
     * @return the internal name
     */
    public String getInternalName() {
        return jdk.internal.org.objectweb.asm.Type.getInternalName(getTypeClass());
    }

    /**
     * Get the internal JVM name of type type represented by a given Java class
     * @param clazz the class
     * @return the internal name
     */
    public static String getInternalName(final Class<?> clazz) {
        return jdk.internal.org.objectweb.asm.Type.getInternalName(clazz);
    }

    /**
     * Determines whether a type is the UNKNOWN type, i.e. not set yet
     * Used for type inference.
     *
     * @return true if UNKNOWN, false otherwise
     */
    public boolean isUnknown() {
        return this.equals(Type.UNKNOWN);
    }

    /**
     * Determines whether this type represents an primitive type according to the ECMAScript specification,
     * which includes Boolean, Number, and String.
     *
     * @return true if a JavaScript primitive type, false otherwise.
     */
    public boolean isJSPrimitive() {
        return !isObject() || isString();
    }

    /**
     * Determines whether a type is the BOOLEAN type
     * @return true if BOOLEAN, false otherwise
     */
    public boolean isBoolean() {
        return this.equals(Type.BOOLEAN);
    }

    /**
     * Determines whether a type is the INT type
     * @return true if INTEGER, false otherwise
     */
    public boolean isInteger() {
        return this.equals(Type.INT);
    }

    /**
     * Determines whether a type is the LONG type
     * @return true if LONG, false otherwise
     */
    public boolean isLong() {
        return this.equals(Type.LONG);
    }

    /**
     * Determines whether a type is the NUMBER type
     * @return true if NUMBER, false otherwise
     */
    public boolean isNumber() {
        return this.equals(Type.NUMBER);
    }

    /**
     * Determines whether a type is numeric, i.e. NUMBER,
     * INT, LONG.
     *
     * @return true if numeric, false otherwise
     */
    public boolean isNumeric() {
        return this instanceof NumericType;
    }

    /**
     * Determines whether a type is an array type, i.e.
     * OBJECT_ARRAY or NUMBER_ARRAY (for now)
     *
     * @return true if an array type, false otherwise
     */
    public boolean isArray() {
        return this instanceof ArrayType;
    }

    /**
     * Determines if a type takes up two bytecode slots or not
     *
     * @return true if type takes up two bytecode slots rather than one
     */
    public boolean isCategory2() {
        return getSlots() == 2;
    }

    /**
     * Determines whether a type is an OBJECT type, e.g. OBJECT, STRING,
     * NUMBER_ARRAY etc.
     *
     * @return true if object type, false otherwise
     */
    public boolean isObject() {
        return this instanceof ObjectType;
    }

    /**
     * Determines whether a type is a STRING type
     *
     * @return true if object type, false otherwise
     */
    public boolean isString() {
        return this.equals(Type.STRING);
    }

    /**
     * Determine if two types are equivalent, i.e. need no conversion
     *
     * @param type the second type to check
     *
     * @return true if types are equivalent, false otherwise
     */
    public boolean isEquivalentTo(final Type type) {
        return this.weight() == type.weight() || (isObject() && type.isObject());
    }

    /**
     * Determine if a type can be assigned to from another
     *
     * @param type0 the first type to check
     * @param type1 the second type to check
     *
     * @return true if type1 can be written to type2, false otherwise
     */
    public static boolean isAssignableFrom(final Type type0, final Type type1) {
        if (type0.isObject() && type1.isObject()) {
            return type0.weight() >= type1.weight();
        }

        return type0.weight() == type1.weight();
    }

    /**
     * Determine if this type is assignable from another type
     * @param type the type to check against
     *
     * @return true if "type" can be written to this type, false otherwise
     */
    public boolean isAssignableFrom(final Type type) {
        return Type.isAssignableFrom(this, type);
    }

    /**
     * Determines is this type is equivalent to another, i.e. needs no conversion
     * to be assigned to it.
     *
     * @param type0 the first type to check
     * @param type1 the second type to check
     *
     * @return true if this type is equivalent to type, false otherwise
     */
    public static boolean areEquivalent(final Type type0, final Type type1) {
        return type0.isEquivalentTo(type1);
    }

    /**
     * Determine the number of bytecode slots a type takes up
     *
     * @return the number of slots for this type, 1 or 2.
     */
    public int getSlots() {
        return slots;
    }
    /**
     * Returns the widest or most common of two types
     *
     * @param type0 type one
     * @param type1 type two
     *
     * @return the widest type
     */
    public static Type widest(final Type type0, final Type type1) {
        if (type0.isArray() && type1.isArray()) {
            return ((ArrayType)type0).getElementType() == ((ArrayType)type1).getElementType() ? type0 : Type.OBJECT;
        } else if (type0.isArray() != type1.isArray()) {
            //array and non array is always object, widest(Object[], int) NEVER returns Object[], which has most weight. that does not make sense
            return Type.OBJECT;
        } else if (type0.isObject() && type1.isObject() && type0.getTypeClass() != type1.getTypeClass()) {
            // Object<type=String> and Object<type=ScriptFunction> will produce Object
            // TODO: maybe find most specific common superclass?
            return Type.OBJECT;
        }
        return type0.weight() > type1.weight() ? type0 : type1;
    }

    /**
     * Returns the narrowest or least common of two types
     *
     * @param type0 type one
     * @param type1 type two
     *
     * @return the widest type
     */
    public static Type narrowest(final Type type0, final Type type1) {
        return type0.weight() < type1.weight() ? type0 : type1;
    }

    /**
     * Returns the widest or most common of two types, but no wider than "limit"
     *
     * @param type0 type one
     * @param type1 type two
     * @param limit limiting type
     *
     * @return the widest type, but no wider than limit
     */
    public static Type widest(final Type type0, final Type type1, final Type limit) {
        final Type type = Type.widest(type0,  type1);
        if (type.weight() > limit.weight()) {
            return limit;
        }
        return type;
    }

    /**
     * Returns the widest or most common of two types, but no narrower than "limit"
     *
     * @param type0 type one
     * @param type1 type two
     * @param limit limiting type
     *
     * @return the widest type, but no wider than limit
     */
    public static Type narrowest(final Type type0, final Type type1, final Type limit) {
        final Type type = type0.weight() < type1.weight() ? type0 : type1;
        if (type.weight() < limit.weight()) {
            return limit;
        }
        return type;
    }

    /**
     * Returns the narrowest of this type and another
     *
     * @param  other type to compare against
     *
     * @return the widest type
     */
    public Type narrowest(final Type other) {
        return Type.narrowest(this, other);
    }

    /**
     * Returns the widest of this type and another
     *
     * @param  other type to compare against
     *
     * @return the widest type
     */
    public Type widest(final Type other) {
        return Type.widest(this, other);
    }

    /**
     * Returns the weight of a type, used for type comparison
     * between wider and narrower types
     *
     * @return the weight
     */
    int weight() {
        return weight;
    }

    /**
     * Return the descriptor of a type, used for e.g. signature
     * generation
     *
     * @return the descriptor
     */
    public String getDescriptor() {
        return descriptor;
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * Return the (possibly cached) Type object for this class
     *
     * @param clazz the class to check
     *
     * @return the Type representing this class
     */
    public static Type typeFor(final Class<?> clazz) {
        final Type type = cache.get(clazz);
        if(type != null) {
            return type;
        }
        assert !clazz.isPrimitive() || clazz == void.class;
        final Type newType;
        if (clazz.isArray()) {
            newType = new ArrayType(clazz);
        } else {
            newType = new ObjectType(clazz);
        }
        final Type existingType = cache.putIfAbsent(clazz, newType);
        return existingType == null ? newType : existingType;
    }

    @Override
    public int compareTo(final Type o) {
        return o.weight() - weight();
    }

    /**
     * Common logic for implementing dup for all types
     *
     * @param method method visitor
     * @param depth dup depth
     *
     * @return the type at the top of the stack afterwards
     */
    @Override
    public Type dup(final MethodVisitor method, final int depth) {
        return Type.dup(method, this, depth);
    }

    /**
     * Common logic for implementing swap for all types
     *
     * @param method method visitor
     * @param other  the type to swap with
     *
     * @return the type at the top of the stack afterwards, i.e. other
     */
    @Override
    public Type swap(final MethodVisitor method, final Type other) {
        Type.swap(method, this, other);
        return other;
    }

    /**
     * Common logic for implementing pop for all types
     *
     * @param method method visitor
     *
     * @return the type that was popped
     */
    @Override
    public Type pop(final MethodVisitor method) {
        Type.pop(method, this);
        return this;
    }

    @Override
    public Type loadEmpty(final MethodVisitor method) {
        assert false : "unsupported operation";
        return null;
    }

    /**
     * Superclass logic for pop for all types
     *
     * @param method method emitter
     * @param type   type to pop
     */
    protected static void pop(final MethodVisitor method, final Type type) {
        method.visitInsn(type.isCategory2() ? POP2 : POP);
    }

    private static Type dup(final MethodVisitor method, final Type type, final int depth) {
        final boolean       cat2 = type.isCategory2();

        switch (depth) {
        case 0:
            method.visitInsn(cat2 ? DUP2 : DUP);
            break;
        case 1:
            method.visitInsn(cat2 ? DUP2_X1 : DUP_X1);
            break;
        case 2:
            method.visitInsn(cat2 ? DUP2_X2 : DUP_X2);
            break;
        default:
            return null; //invalid depth
        }

        return type;
    }

    private static void swap(final MethodVisitor method, final Type above, final Type below) {
        if (below.isCategory2()) {
            if (above.isCategory2()) {
                method.visitInsn(DUP2_X2);
                method.visitInsn(POP2);
            } else {
                method.visitInsn(DUP_X2);
                method.visitInsn(POP);
            }
        } else {
            if (above.isCategory2()) {
                method.visitInsn(DUP2_X1);
                method.visitInsn(POP2);
            } else {
                method.visitInsn(SWAP);
            }
        }
    }

    /** Mappings between java classes and their Type singletons */
    private static final ConcurrentMap<Class<?>, Type> cache = new ConcurrentHashMap<>();

    /**
     * This is the boolean singleton, used for all boolean types
     */
    public static final Type BOOLEAN = putInCache(new BooleanType());

    /**
     * This is an integer type, i.e INT, INT32.
     */
    public static final Type INT = putInCache(new IntType());

    /**
     * This is the number singleton, used for all number types
     */
    public static final Type NUMBER = putInCache(new NumberType());

    /**
     * This is the long singleton, used for all long types
     */
    public static final Type LONG = putInCache(new LongType());

    /**
     * A string singleton
     */
    public static final Type STRING = putInCache(new ObjectType(String.class));

    /**
     * This is the object singleton, used for all object types
     */
    public static final Type OBJECT = putInCache(new ObjectType());

    /**
     * This is the singleton for integer arrays
     */
    public static final ArrayType INT_ARRAY = new ArrayType(int[].class) {
        @Override
        public void astore(final MethodVisitor method) {
            method.visitInsn(IASTORE);
        }

        @Override
        public Type aload(final MethodVisitor method) {
            method.visitInsn(IALOAD);
            return INT;
        }

        @Override
        public Type newarray(final MethodVisitor method) {
            method.visitIntInsn(NEWARRAY, T_INT);
            return this;
        }

        @Override
        public Type getElementType() {
            return INT;
        }
    };

    /**
     * This is the singleton for long arrays
     */
    public static final ArrayType LONG_ARRAY = new ArrayType(long[].class) {
        @Override
        public void astore(final MethodVisitor method) {
            method.visitInsn(LASTORE);
        }

        @Override
        public Type aload(final MethodVisitor method) {
            method.visitInsn(LALOAD);
            return LONG;
        }

        @Override
        public Type newarray(final MethodVisitor method) {
            method.visitIntInsn(NEWARRAY, T_LONG);
            return this;
        }

        @Override
        public Type getElementType() {
            return LONG;
        }
    };

    /**
     * This is the singleton for numeric arrays
     */
    public static final ArrayType NUMBER_ARRAY = new ArrayType(double[].class) {
        @Override
        public void astore(final MethodVisitor method) {
            method.visitInsn(DASTORE);
        }

        @Override
        public Type aload(final MethodVisitor method) {
            method.visitInsn(DALOAD);
            return NUMBER;
        }

        @Override
        public Type newarray(final MethodVisitor method) {
            method.visitIntInsn(NEWARRAY, T_DOUBLE);
            return this;
        }

        @Override
        public Type getElementType() {
            return NUMBER;
        }
    };

    /** Singleton for method handle arrays used for properties etc. */
    public static final ArrayType METHODHANDLE_ARRAY = putInCache(new ArrayType(MethodHandle[].class));

    /** This is the singleton for string arrays */
    public static final ArrayType STRING_ARRAY = putInCache(new ArrayType(String[].class));

    /** This is the singleton for object arrays */
    public static final ArrayType OBJECT_ARRAY = putInCache(new ArrayType(Object[].class));

    /** This type, always an object type, just a toString override */
    public static final Type THIS = new ObjectType() {
        @Override
        public String toString() {
            return "this";
        }
    };

    /** Scope type, always an object type, just a toString override */
    public static final Type SCOPE = new ObjectType() {
        @Override
        public String toString() {
            return "scope";
        }
    };

    private static interface Unknown {
        // EMPTY - used as a class that is absolutely not compatible with a type to represent "unknown"
    }

    /**
     * This is the unknown type which is used as initial type for type
     * inference. It has the minimum type width
     */
    public static final Type UNKNOWN = new Type("<unknown>", Unknown.class, MIN_WEIGHT, 1) {

        @Override
        public String getDescriptor() {
            return "<unknown>";
        }

        @Override
        public Type load(final MethodVisitor method, final int slot) {
            assert false : "unsupported operation";
            return null;
        }

        @Override
        public void store(final MethodVisitor method, final int slot) {
            assert false : "unsupported operation";
        }

        @Override
        public Type ldc(final MethodVisitor method, final Object c) {
            assert false : "unsupported operation";
            return null;
        }

        @Override
        public Type loadUndefined(final MethodVisitor method) {
            assert false : "unsupported operation";
            return null;
        }

        @Override
        public Type convert(final MethodVisitor method, final Type to) {
            assert false : "unsupported operation";
            return null;
        }

        @Override
        public void _return(final MethodVisitor method) {
            assert false : "unsupported operation";
        }

        @Override
        public Type add(final MethodVisitor method) {
            assert false : "unsupported operation";
            return null;
        }
    };

    private static <T extends Type> T putInCache(T type) {
        cache.put(type.getTypeClass(), type);
        return type;
    }
}
