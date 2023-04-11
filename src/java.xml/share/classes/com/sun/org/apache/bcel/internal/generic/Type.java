/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
 */
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.sun.org.apache.bcel.internal.generic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import com.sun.org.apache.bcel.internal.Const;
import com.sun.org.apache.bcel.internal.classfile.ClassFormatException;
import com.sun.org.apache.bcel.internal.classfile.Utility;

/**
 * Abstract super class for all possible java types, namely basic types such as int, object types like String and array
 * types, e.g. int[]
 * @LastModified: May 2021
 */
public abstract class Type {

    /**
     * Predefined constants
     */
    public static final BasicType VOID = new BasicType(Const.T_VOID);

    public static final BasicType BOOLEAN = new BasicType(Const.T_BOOLEAN);
    public static final BasicType INT = new BasicType(Const.T_INT);
    public static final BasicType SHORT = new BasicType(Const.T_SHORT);
    public static final BasicType BYTE = new BasicType(Const.T_BYTE);
    public static final BasicType LONG = new BasicType(Const.T_LONG);
    public static final BasicType DOUBLE = new BasicType(Const.T_DOUBLE);
    public static final BasicType FLOAT = new BasicType(Const.T_FLOAT);
    public static final BasicType CHAR = new BasicType(Const.T_CHAR);
    public static final ObjectType OBJECT = new ObjectType("java.lang.Object");
    public static final ObjectType CLASS = new ObjectType("java.lang.Class");
    public static final ObjectType STRING = new ObjectType("java.lang.String");
    public static final ObjectType STRINGBUFFER = new ObjectType("java.lang.StringBuffer");
    public static final ObjectType THROWABLE = new ObjectType("java.lang.Throwable");

    /**
     * Empty array.
     */
    public static final Type[] NO_ARGS = {};
    public static final ReferenceType NULL = new ReferenceType() {
    };

    public static final Type UNKNOWN = new Type(Const.T_UNKNOWN, "<unknown object>") {
    };

    private static final ThreadLocal<Integer> CONSUMED_CHARS = ThreadLocal.withInitial(() -> Integer.valueOf(0));

    // int consumed_chars=0; // Remember position in string, see getArgumentTypes
    static int consumed(final int coded) {
        return coded >> 2;
    }

    static int encode(final int size, final int consumed) {
        return consumed << 2 | size;
    }

    /**
     * Convert arguments of a method (signature) to an array of Type objects.
     *
     * @param signature signature string such as (Ljava/lang/String;)V
     * @return array of argument types
     */
    public static Type[] getArgumentTypes(final String signature) {
        final List<Type> vec = new ArrayList<>();
        int index;
        try {
            // Skip any type arguments to read argument declarations between '(' and ')'
            index = signature.indexOf('(') + 1;
            if (index <= 0) {
                throw new ClassFormatException("Invalid method signature: " + signature);
            }
            while (signature.charAt(index) != ')') {
                vec.add(getType(signature.substring(index)));
                // corrected concurrent private static field acess
                index += unwrap(CONSUMED_CHARS); // update position
            }
        } catch (final StringIndexOutOfBoundsException e) { // Should never occur
            throw new ClassFormatException("Invalid method signature: " + signature, e);
        }
        final Type[] types = new Type[vec.size()];
        vec.toArray(types);
        return types;
    }

    static int getArgumentTypesSize(final String signature) {
        int res = 0;
        int index;
        try {
            // Skip any type arguments to read argument declarations between '(' and ')'
            index = signature.indexOf('(') + 1;
            if (index <= 0) {
                throw new ClassFormatException("Invalid method signature: " + signature);
            }
            while (signature.charAt(index) != ')') {
                final int coded = getTypeSize(signature.substring(index));
                res += size(coded);
                index += consumed(coded);
            }
        } catch (final StringIndexOutOfBoundsException e) { // Should never occur
            throw new ClassFormatException("Invalid method signature: " + signature, e);
        }
        return res;
    }

    /**
     * Convert type to Java method signature, e.g. int[] f(java.lang.String x) becomes (Ljava/lang/String;)[I
     *
     * @param returnType what the method returns
     * @param argTypes what are the argument types
     * @return method signature for given type(s).
     */
    public static String getMethodSignature(final Type returnType, final Type[] argTypes) {
        final StringBuilder buf = new StringBuilder("(");
        if (argTypes != null) {
            for (final Type argType : argTypes) {
                buf.append(argType.getSignature());
            }
        }
        buf.append(')');
        buf.append(returnType.getSignature());
        return buf.toString();
    }

    /**
     * Convert return value of a method (signature) to a Type object.
     *
     * @param signature signature string such as (Ljava/lang/String;)V
     * @return return type
     */
    public static Type getReturnType(final String signature) {
        try {
            // Read return type after ')'
            final int index = signature.lastIndexOf(')') + 1;
            return getType(signature.substring(index));
        } catch (final StringIndexOutOfBoundsException e) { // Should never occur
            throw new ClassFormatException("Invalid method signature: " + signature, e);
        }
    }

    static int getReturnTypeSize(final String signature) {
        final int index = signature.lastIndexOf(')') + 1;
        return Type.size(getTypeSize(signature.substring(index)));
    }

    public static String getSignature(final java.lang.reflect.Method meth) {
        final StringBuilder sb = new StringBuilder("(");
        final Class<?>[] params = meth.getParameterTypes(); // avoid clone
        for (final Class<?> param : params) {
            sb.append(getType(param).getSignature());
        }
        sb.append(")");
        sb.append(getType(meth.getReturnType()).getSignature());
        return sb.toString();
    }

    /**
     * Convert runtime java.lang.Class to BCEL Type object.
     *
     * @param cls Java class
     * @return corresponding Type object
     */
    public static Type getType(final Class<?> cls) {
        Objects.requireNonNull(cls, "cls");
        /*
         * That's an amzingly easy case, because getName() returns the signature. That's what we would have liked anyway.
         */
        if (cls.isArray()) {
            return getType(cls.getName());
        }
        if (!cls.isPrimitive()) { // "Real" class
            return ObjectType.getInstance(cls.getName());
        }
        if (cls == Integer.TYPE) {
            return INT;
        }
        if (cls == Void.TYPE) {
            return VOID;
        }
        if (cls == Double.TYPE) {
            return DOUBLE;
        }
        if (cls == Float.TYPE) {
            return FLOAT;
        }
        if (cls == Boolean.TYPE) {
            return BOOLEAN;
        }
        if (cls == Byte.TYPE) {
            return BYTE;
        }
        if (cls == Short.TYPE) {
            return SHORT;
        }
        if (cls == Long.TYPE) {
            return LONG;
        }
        if (cls == Character.TYPE) {
            return CHAR;
        }
        throw new IllegalStateException("Unknown primitive type " + cls);
    }

    /**
     * Convert signature to a Type object.
     *
     * @param signature signature string such as Ljava/lang/String;
     * @return type object
     */
    public static Type getType(final String signature) throws StringIndexOutOfBoundsException {
        final byte type = Utility.typeOfSignature(signature);
        if (type <= Const.T_VOID) {
            // corrected concurrent private static field acess
            wrap(CONSUMED_CHARS, 1);
            return BasicType.getType(type);
        }
        if (type != Const.T_ARRAY) { // type == T_REFERENCE
            // Utility.typeSignatureToString understands how to parse generic types.
            final String parsedSignature = Utility.typeSignatureToString(signature, false);
            wrap(CONSUMED_CHARS, parsedSignature.length() + 2); // "Lblabla;" 'L' and ';' are removed
            return ObjectType.getInstance(Utility.pathToPackage(parsedSignature));
        }
        int dim = 0;
        do { // Count dimensions
            dim++;
        } while (signature.charAt(dim) == '[');
        // Recurse, but just once, if the signature is ok
        final Type t = getType(signature.substring(dim));
        // corrected concurrent private static field acess
        // consumed_chars += dim; // update counter - is replaced by
        final int temp = unwrap(CONSUMED_CHARS) + dim;
        wrap(CONSUMED_CHARS, temp);
        return new ArrayType(t, dim);
    }

    /**
     * Convert runtime java.lang.Class[] to BCEL Type objects.
     *
     * @param classes an array of runtime class objects
     * @return array of corresponding Type objects
     */
    public static Type[] getTypes(final Class<?>[] classes) {
        final Type[] ret = new Type[classes.length];
        Arrays.setAll(ret, i -> getType(classes[i]));
        return ret;
    }

    static int getTypeSize(final String signature) throws StringIndexOutOfBoundsException {
        final byte type = Utility.typeOfSignature(signature);
        if (type <= Const.T_VOID) {
            return encode(BasicType.getType(type).getSize(), 1);
        }
        if (type == Const.T_ARRAY) {
            int dim = 0;
            do { // Count dimensions
                dim++;
            } while (signature.charAt(dim) == '[');
            // Recurse, but just once, if the signature is ok
            final int consumed = consumed(getTypeSize(signature.substring(dim)));
            return encode(1, dim + consumed);
        }
        final int index = signature.indexOf(';'); // Look for closing ';'
        if (index < 0) {
            throw new ClassFormatException("Invalid signature: " + signature);
        }
        return encode(1, index + 1);
    }

    static int size(final int coded) {
        return coded & 3;
    }

    private static int unwrap(final ThreadLocal<Integer> tl) {
        return tl.get().intValue();
    }

    private static void wrap(final ThreadLocal<Integer> tl, final int value) {
        tl.set(Integer.valueOf(value));
    }

    /**
     * @deprecated (since 6.0) will be made private; do not access directly, use getter/setter
     */
    @Deprecated
    protected byte type; // TODO should be final (and private)

    /**
     * @deprecated (since 6.0) will be made private; do not access directly, use getter/setter
     */
    @Deprecated
    protected String signature; // signature for the type TODO should be private

    protected Type(final byte type, final String signature) {
        this.type = type;
        this.signature = signature;
    }

    /**
     * @return whether the Types are equal
     */
    @Override
    public boolean equals(final Object o) {
        if (o instanceof Type) {
            final Type t = (Type) o;
            return type == t.type && signature.equals(t.signature);
        }
        return false;
    }

    public String getClassName() {
        return toString();
    }

    /**
     * @return signature for given type.
     */
    public String getSignature() {
        return signature;
    }

    /**
     * @return stack size of this type (2 for long and double, 0 for void, 1 otherwise)
     */
    public int getSize() {
        switch (type) {
        case Const.T_DOUBLE:
        case Const.T_LONG:
            return 2;
        case Const.T_VOID:
            return 0;
        default:
            return 1;
        }
    }

    /**
     * @return type as defined in Constants
     */
    public byte getType() {
        return type;
    }

    /**
     * @return hashcode of Type
     */
    @Override
    public int hashCode() {
        return type ^ signature.hashCode();
    }

    /**
     * boolean, short and char variable are considered as int in the stack or local variable area. Returns {@link Type#INT}
     * for {@link Type#BOOLEAN}, {@link Type#SHORT} or {@link Type#CHAR}, otherwise returns the given type.
     *
     * @since 6.0
     */
    public Type normalizeForStackOrLocal() {
        if (this == Type.BOOLEAN || this == Type.BYTE || this == Type.SHORT || this == Type.CHAR) {
            return Type.INT;
        }
        return this;
    }

    /*
     * Currently only used by the ArrayType constructor. The signature has a complicated dependency on other parameter so
     * it's tricky to do it in a call to the super ctor.
     */
    void setSignature(final String signature) {
        this.signature = signature;
    }

    /**
     * @return Type string, e.g. 'int[]'
     */
    @Override
    public String toString() {
        return this.equals(Type.NULL) || type >= Const.T_UNKNOWN ? signature : Utility.signatureToString(signature, false);
    }
}
