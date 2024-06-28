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

package jdk.internal.reflect;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * The private serialization bytecode generator used by {@code sun.misc.ReflectionFactory}.
 */
final class SerializationBytecodeGenerator {
    private static final MethodHandles.Lookup lookup = MethodHandles.lookup();

    // no instances
    private SerializationBytecodeGenerator() {}

    static MethodHandle defaultReadObjectForSerialization(Class<?> cl) {
        // build an anonymous+hidden nestmate to perform the read operation
        List<MethodHandle> setters = new ArrayList<>();
        byte[] bytes = ClassFile.of().build(CD_Genearted_readObject, classBuilder -> classBuilder.withMethod("readObject",
            MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_Object, CD_ObjectInputStream),
            Modifier.STATIC | Modifier.PRIVATE,
            mb -> mb.withCode(cb -> {
                // get our GetField
                cb.aload(1);
                cb.invokevirtual(CD_ObjectInputStream, "readFields", MTD_ObjectInputStream_readFields);
                cb.astore(2);
                // iterate the fields of the class
                for (Field field : cl.getDeclaredFields()) {
                    int fieldMods = field.getModifiers();
                    if (Modifier.isStatic(fieldMods) || Modifier.isTransient(fieldMods)) {
                        continue;
                    }
                    String fieldName = field.getName();
                    Class<?> fieldType = field.getType();

                    // generate setter
                    field.setAccessible(true);
                    int idx = setters.size();
                    MethodHandle mh;
                    try {
                        mh = lookup.unreflectSetter(field);
                        if (fieldType.isPrimitive()) {
                            mh = mh.asType(MethodType.methodType(void.class, Object.class, fieldType));
                        } else {
                            mh = mh.asType(MethodType.methodType(void.class, Object.class, Object.class));
                        }
                        setters.add(mh);
                    } catch (IllegalAccessException e) {
                        throw new InternalError("Error generating accessor for field " + field, e);
                    }
                    cb.ldc(DynamicConstantDesc.ofNamed(
                        ConstantDescs.BSM_CLASS_DATA_AT,
                        ConstantDescs.DEFAULT_NAME,
                        ConstantDescs.CD_MethodHandle,
                        Integer.valueOf(idx)
                    ));
                    // stack: <mh>
                    cb.aload(0); // stack: <mh> this
                    cb.aload(2); // stack: <mh> this GetField
                    cb.ldc(fieldName); // stack: <mh> this GetField <name>

                    ClassDesc fieldDesc = fieldType.describeConstable().orElseThrow(InternalError::new);

                    switch (fieldDesc.descriptorString()) {
                        case "B" -> {
                            cb.iconst_0();
                            cb.invokevirtual(CD_ObjectInputStream_GetField, "get", MTD_ObjectInputStream_GetField_get_B);
                        }
                        case "C" -> {
                            cb.iconst_0();
                            cb.invokevirtual(CD_ObjectInputStream_GetField, "get", MTD_ObjectInputStream_GetField_get_C);
                        }
                        case "D" -> {
                            cb.dconst_0();
                            cb.invokevirtual(CD_ObjectInputStream_GetField, "get", MTD_ObjectInputStream_GetField_get_D);
                        }
                        case "F" -> {
                            cb.fconst_0();
                            cb.invokevirtual(CD_ObjectInputStream_GetField, "get", MTD_ObjectInputStream_GetField_get_F);
                        }
                        case "I" -> {
                            cb.iconst_0();
                            cb.invokevirtual(CD_ObjectInputStream_GetField, "get", MTD_ObjectInputStream_GetField_get_I);
                        }
                        case "J" -> {
                            cb.lconst_0();
                            cb.invokevirtual(CD_ObjectInputStream_GetField, "get", MTD_ObjectInputStream_GetField_get_J);
                        }
                        case "S" -> {
                            cb.iconst_0();
                            cb.invokevirtual(CD_ObjectInputStream_GetField, "get", MTD_ObjectInputStream_GetField_get_S);
                        }
                        case "Z" -> {
                            cb.iconst_0();
                            cb.invokevirtual(CD_ObjectInputStream_GetField, "get", MTD_ObjectInputStream_GetField_get_Z);
                        }
                        default -> {
                            cb.aconst_null();
                            cb.invokevirtual(CD_ObjectInputStream_GetField, "get", MTD_ObjectInputStream_GetField_get_L);
                        }
                    }
                    // stack: <mh> this <val>
                    cb.invokevirtual(ConstantDescs.CD_MethodHandle, "invokeExact", mh.type().describeConstable().orElseThrow(InternalError::new));
                }
                cb.return_();
            })
        ));
        try {
            MethodHandles.Lookup hcLookup = lookup.defineHiddenClassWithClassData(bytes, List.copyOf(setters), true);
            return hcLookup.findStatic(hcLookup.lookupClass(), "readObject", MethodType.methodType(void.class, Object.class, ObjectInputStream.class))
                .asType(MethodType.methodType(void.class, cl, ObjectInputStream.class));
        } catch (IllegalAccessException | NoSuchMethodException e) {
            throw new InternalError("Error in readObject generation", e);
        }
    }

    static MethodHandle defaultWriteObjectForSerialization(Class<?> cl) {
        // build an anonymous+hidden nestmate to perform the write operation
        List<MethodHandle> getters = new ArrayList<>();
        byte[] bytes = ClassFile.of().build(CD_Generated_writeObject, classBuilder -> classBuilder.withMethod("writeObject",
            MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_Object, CD_ObjectOutputStream),
            Modifier.STATIC | Modifier.PRIVATE,
            mb -> mb.withCode(cb -> {
                // get our PutField
                cb.aload(1);
                cb.invokevirtual(CD_ObjectOutputStream, "putFields", MTD_ObjectOutputStream_putFields);
                cb.astore(2);
                // iterate the fields of the class
                for (Field field : cl.getDeclaredFields()) {
                    int fieldMods = field.getModifiers();
                    if (Modifier.isStatic(fieldMods) || Modifier.isTransient(fieldMods)) {
                        continue;
                    }
                    String fieldName = field.getName();
                    Class<?> fieldType = field.getType();

                    // stack: (empty)
                    cb.aload(2);
                    // stack: PutField
                    cb.ldc(fieldName);
                    // stack: PutField fieldName
                    // generate getter
                    field.setAccessible(true);
                    int idx = getters.size();
                    MethodHandle getter;
                    try {
                        getter = lookup.unreflectGetter(field);
                        if (fieldType.isPrimitive()) {
                            getter = getter.asType(MethodType.methodType(fieldType, Object.class));
                        } else {
                            getter = getter.asType(MethodType.methodType(Object.class, Object.class));
                        }
                        getters.add(getter);
                    } catch (IllegalAccessException e) {
                        throw new InternalError("Error generating accessor for field " + field, e);
                    }
                    cb.ldc(DynamicConstantDesc.ofNamed(
                        ConstantDescs.BSM_CLASS_DATA_AT,
                        ConstantDescs.DEFAULT_NAME,
                        ConstantDescs.CD_MethodHandle,
                        Integer.valueOf(idx)
                    ));
                    // stack: PutField fieldName <mh>
                    cb.aload(0);
                    // stack: PutField fieldName <mh> this
                    cb.invokevirtual(ConstantDescs.CD_MethodHandle, "invokeExact", getter.type().describeConstable().orElseThrow(InternalError::new));
                    // stack: PutField fieldName <val>
                    switch (fieldType.descriptorString()) {
                        case "B" -> cb.invokevirtual(CD_ObjectOutputStream_PutField, "put", MTD_ObjectOutputStream_PutField_put_B);
                        case "C" -> cb.invokevirtual(CD_ObjectOutputStream_PutField, "put", MTD_ObjectOutputStream_PutField_put_C);
                        case "D" -> cb.invokevirtual(CD_ObjectOutputStream_PutField, "put", MTD_ObjectOutputStream_PutField_put_D);
                        case "F" -> cb.invokevirtual(CD_ObjectOutputStream_PutField, "put", MTD_ObjectOutputStream_PutField_put_F);
                        case "I" -> cb.invokevirtual(CD_ObjectOutputStream_PutField, "put", MTD_ObjectOutputStream_PutField_put_I);
                        case "J" -> cb.invokevirtual(CD_ObjectOutputStream_PutField, "put", MTD_ObjectOutputStream_PutField_put_J);
                        case "S" -> cb.invokevirtual(CD_ObjectOutputStream_PutField, "put", MTD_ObjectOutputStream_PutField_put_S);
                        case "Z" -> cb.invokevirtual(CD_ObjectOutputStream_PutField, "put", MTD_ObjectOutputStream_PutField_put_Z);
                        default -> cb.invokevirtual(CD_ObjectOutputStream_PutField, "put", MTD_ObjectOutputStream_PutField_put_L);
                    }
                    // stack: (empty)
                }
                // commit fields to stream
                cb.aload(1);
                cb.invokevirtual(CD_ObjectOutputStream, "writeFields", ConstantDescs.MTD_void);
                cb.return_();
            })
        ));
        try {
            MethodHandles.Lookup hcLookup = lookup.defineHiddenClassWithClassData(bytes, List.copyOf(getters), true);
            return hcLookup.findStatic(hcLookup.lookupClass(), "writeObject", MethodType.methodType(void.class, Object.class, ObjectOutputStream.class))
                .asType(MethodType.methodType(void.class, cl, ObjectOutputStream.class));
        } catch (IllegalAccessException | NoSuchMethodException e) {
            throw new InternalError("Error in writeObject generation", e);
        }
    }

    private static final ClassDesc CD_ObjectInputStream = ClassDesc.of("java.io.ObjectInputStream");
    private static final ClassDesc CD_ObjectInputStream_GetField = ClassDesc.of("java.io.ObjectInputStream$GetField");

    private static final ClassDesc CD_ObjectOutputStream = ClassDesc.of("java.io.ObjectOutputStream");
    private static final ClassDesc CD_ObjectOutputStream_PutField = ClassDesc.of("java.io.ObjectOutputStream$PutField");

    private static final ClassDesc CD_Generated_writeObject = ClassDesc.of("jdk.internal.reflect", "Generated$$writeObject");
    private static final ClassDesc CD_Genearted_readObject = ClassDesc.of("jdk.internal.reflect", "Generated$$readObject");

    private static final MethodTypeDesc MTD_ObjectInputStream_readFields = MethodTypeDesc.of(CD_ObjectInputStream_GetField);

    private static final MethodTypeDesc MTD_ObjectInputStream_GetField_get_B = MethodTypeDesc.of(ConstantDescs.CD_byte, ConstantDescs.CD_String, ConstantDescs.CD_byte);
    private static final MethodTypeDesc MTD_ObjectInputStream_GetField_get_C = MethodTypeDesc.of(ConstantDescs.CD_char, ConstantDescs.CD_String, ConstantDescs.CD_char);
    private static final MethodTypeDesc MTD_ObjectInputStream_GetField_get_D = MethodTypeDesc.of(ConstantDescs.CD_double, ConstantDescs.CD_String, ConstantDescs.CD_double);
    private static final MethodTypeDesc MTD_ObjectInputStream_GetField_get_F = MethodTypeDesc.of(ConstantDescs.CD_float, ConstantDescs.CD_String, ConstantDescs.CD_float);
    private static final MethodTypeDesc MTD_ObjectInputStream_GetField_get_I = MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_String, ConstantDescs.CD_int);
    private static final MethodTypeDesc MTD_ObjectInputStream_GetField_get_J = MethodTypeDesc.of(ConstantDescs.CD_long, ConstantDescs.CD_String, ConstantDescs.CD_long);
    private static final MethodTypeDesc MTD_ObjectInputStream_GetField_get_L = MethodTypeDesc.of(ConstantDescs.CD_Object, ConstantDescs.CD_String, ConstantDescs.CD_Object);
    private static final MethodTypeDesc MTD_ObjectInputStream_GetField_get_S = MethodTypeDesc.of(ConstantDescs.CD_short, ConstantDescs.CD_String, ConstantDescs.CD_short);
    private static final MethodTypeDesc MTD_ObjectInputStream_GetField_get_Z = MethodTypeDesc.of(ConstantDescs.CD_boolean, ConstantDescs.CD_String, ConstantDescs.CD_boolean);

    private static final MethodTypeDesc MTD_ObjectOutputStream_putFields = MethodTypeDesc.of(CD_ObjectOutputStream_PutField);

    private static final MethodTypeDesc MTD_ObjectOutputStream_PutField_put_B = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_String, ConstantDescs.CD_byte);
    private static final MethodTypeDesc MTD_ObjectOutputStream_PutField_put_C = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_String, ConstantDescs.CD_char);
    private static final MethodTypeDesc MTD_ObjectOutputStream_PutField_put_D = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_String, ConstantDescs.CD_double);
    private static final MethodTypeDesc MTD_ObjectOutputStream_PutField_put_F = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_String, ConstantDescs.CD_float);
    private static final MethodTypeDesc MTD_ObjectOutputStream_PutField_put_I = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_String, ConstantDescs.CD_int);
    private static final MethodTypeDesc MTD_ObjectOutputStream_PutField_put_J = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_String, ConstantDescs.CD_long);
    private static final MethodTypeDesc MTD_ObjectOutputStream_PutField_put_L = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_String, ConstantDescs.CD_Object);
    private static final MethodTypeDesc MTD_ObjectOutputStream_PutField_put_S = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_String, ConstantDescs.CD_short);
    private static final MethodTypeDesc MTD_ObjectOutputStream_PutField_put_Z = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_String, ConstantDescs.CD_boolean);
}
