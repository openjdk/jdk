/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.internal.util;

import jdk.jfr.ValueDescriptor;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.Objects;
import jdk.jfr.internal.Logger;
import jdk.jfr.internal.LogLevel;
import jdk.jfr.internal.LogTag;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.MethodModel;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassFile;
import jdk.internal.classfile.components.ClassPrinter;

/**
 * Helper class when working with bytecode.
 */
public final class Bytecode {

    private static final ClassDesc CD_Thread = classDesc(Thread.class);

    public record ClassMethodDesc(ClassDesc type, MethodDesc method) {
        public static ClassMethodDesc of(Class<?> clazz, String method, String desrciptor) {
            return new ClassMethodDesc(classDesc(clazz), MethodDesc.of(method, desrciptor));
        }
    }

    public record FieldDesc(ClassDesc type, String name) {
        public static FieldDesc of(ClassDesc type, String name) {
            return new FieldDesc(type, name);
        }

        public static FieldDesc of(Class<?> type, String name) {
            return of(classDesc(type), name);
        }
    }

    public record MethodDesc(String name, MethodTypeDesc descriptor) {
        public static MethodDesc of(String methodName, String descriptor) {
            return new MethodDesc(methodName, MethodTypeDesc.ofDescriptor(descriptor));
        }

        public static MethodDesc of(String methodName, Class<?> returnType, Class<?>... parameters) {
            ClassDesc[] parameterDesc = new ClassDesc[parameters.length];
            for (int i = 0; i < parameterDesc.length; i++) {
                parameterDesc[i] = classDesc(parameters[i]);
            }
            ClassDesc returnDesc = classDesc(returnType);
            MethodTypeDesc mtd = MethodTypeDesc.of(returnDesc, parameterDesc);
            return new MethodDesc(methodName, mtd);
        }

        public boolean matches(MethodModel m) {
            return this.descriptor().equals(m.methodTypeSymbol()) && m.methodName().equalsString(this.name());
        }
    }
    public record SettingDesc(ClassDesc paramType, String methodName) {
    }

    public static ClassDesc classDesc(ValueDescriptor v) {
        String typeName = v.getTypeName();
        return switch (typeName) {
            case "boolean" -> ConstantDescs.CD_boolean;
            case "byte" -> ConstantDescs.CD_byte;
            case "short" -> ConstantDescs.CD_short;
            case "char" -> ConstantDescs.CD_char;
            case "int" -> ConstantDescs.CD_int;
            case "long" -> ConstantDescs.CD_long;
            case "double" -> ConstantDescs.CD_double;
            case "float" -> ConstantDescs.CD_float;
            case "java.lang.String" -> ConstantDescs.CD_String;
            case "java.lang.Class" -> ConstantDescs.CD_Class;
            case "java.lang.Thread" -> CD_Thread;
            default -> throw new InternalError("Unsupported JFR type " + v.getTypeName());
        };
    }

    public static String internalName(String className) {
        return className != null ? className.replace(".", "/") : null;
    }

    public static String descriptorName(String className) {
        return className != null ? ("L" + internalName(className) + ";") : null;
    }

    public static ClassDesc classDesc(Class<?> clazz) {
        return ClassDesc.ofDescriptor(clazz.descriptorString());
    }

    public static void getfield(CodeBuilder codeBuilder, ClassDesc owner, FieldDesc field) {
        codeBuilder.getfield(owner, field.name(), field.type());
    }

    public static void putfield(CodeBuilder codeBuilder, ClassDesc owner, FieldDesc field) {
        codeBuilder.putfield(owner, field.name(), field.type());
    }

    public static void invokestatic(CodeBuilder codeBuilder, ClassDesc owner, MethodDesc method) {
        codeBuilder.invokestatic(owner, method.name(), method.descriptor());
    }

    public static void invokespecial(CodeBuilder codeBuilder, ClassDesc owner, MethodDesc method) {
        codeBuilder.invokespecial(owner, method.name(), method.descriptor());
    }

    public static void invokevirtual(CodeBuilder codeBuilder, ClassDesc owner, MethodDesc method) {
        codeBuilder.invokevirtual(owner, method.name(), method.descriptor());
    }

    public static void invokevirtual(CodeBuilder codeBuilder, ClassMethodDesc cmd) {
        invokevirtual(codeBuilder, cmd.type(), cmd.method());
    }

    public static void unbox(CodeBuilder codeBuilder, ClassDesc type) {
        if (!type.isPrimitive()) {
            codeBuilder.checkcast(type);
            return;
        }
        ClassMethodDesc unboxer = switch (type.descriptorString()) {
            case "B" -> ClassMethodDesc.of(Byte.class, "byteValue", "()B");
            case "S" -> ClassMethodDesc.of(Short.class, "shortValue", "()S");
            case "C" -> ClassMethodDesc.of(Character.class, "charValue", "()C");
            case "I" -> ClassMethodDesc.of(Integer.class, "intValue", "()I");
            case "J" -> ClassMethodDesc.of(Long.class, "longValue", "()J");
            case "F" -> ClassMethodDesc.of(Float.class, "floatValue", "()F");
            case "D" -> ClassMethodDesc.of(Double.class, "doubleValue", "()D");
            case "Z" -> ClassMethodDesc.of(Boolean.class, "booleanValue", "()Z");
            default -> throw new InternalError("Unsupported JFR type " + type.descriptorString());
        };
        codeBuilder.checkcast(unboxer.type());
        invokevirtual(codeBuilder, unboxer);
    }

    public static void throwException(CodeBuilder cb, ClassDesc type, String message) {
        Objects.requireNonNull(message);
        cb.new_(type);
        cb.dup();
        cb.ldc(message);
        MethodDesc md = MethodDesc.of("<init>", void.class, String.class);
        invokespecial(cb, type, md);
        cb.athrow();
    }

    public static void log(String className, byte[] bytes) {
        Logger.log(LogTag.JFR_SYSTEM_BYTECODE, LogLevel.INFO, "Generated bytecode for class " + className);
        if (Logger.shouldLog(LogTag.JFR_SYSTEM_BYTECODE, LogLevel.TRACE)) {
            StringBuilder out = new StringBuilder();
            out.append("Bytecode:");
            out.append(System.lineSeparator());
            ClassModel classModel = ClassFile.of().parse(bytes);
            ClassPrinter.toYaml(classModel, ClassPrinter.Verbosity.TRACE_ALL, out::append);
            Logger.log(LogTag.JFR_SYSTEM_BYTECODE, LogLevel.TRACE, out.toString());
        }
    }
}
