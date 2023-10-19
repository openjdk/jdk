/*
 * Copyright (c) 2010, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package test.java.lang.invoke.lib;

import jdk.internal.classfile.ClassBuilder;
import jdk.internal.classfile.Classfile;
import jdk.internal.classfile.TypeKind;

import java.lang.constant.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.invoke.MethodType.fromMethodDescriptorString;

public class InstructionHelper {

    static final AtomicInteger COUNT = new AtomicInteger();

    private static void commonBuild(ClassBuilder classBuilder) {
        classBuilder
                .withVersion(55, 0)
                .withSuperclass(ConstantDescs.CD_Object)
                .withMethod(ConstantDescs.INIT_NAME, ConstantDescs.MTD_void, Classfile.ACC_PUBLIC,
                        methodBuilder -> methodBuilder
                                .withCode(codeBuilder -> codeBuilder
                                        .aload(0)
                                        .invokespecial(ConstantDescs.CD_Object, ConstantDescs.INIT_NAME,
                                                ConstantDescs.MTD_void, false)
                                        .return_()));
    }

    private static String className(MethodHandles.Lookup l) {
        return l.lookupClass().getCanonicalName().replace('.', '/') + "$Code_" +
                COUNT.getAndIncrement();
    }

    public static MethodHandle invokedynamic(MethodHandles.Lookup l, String name, MethodType type, String bsmMethodName,
                                             MethodType bsmType,
                                             ConstantDesc[] boostrapArgs) throws Exception {
        byte[] byteArray = Classfile.of().build(ClassDesc.of(className(l)), classBuilder -> {
            commonBuild(classBuilder);
            classBuilder
                    .withMethod("m", MethodTypeDesc.ofDescriptor(type.toMethodDescriptorString()),
                            Classfile.ACC_PUBLIC + Classfile.ACC_STATIC, methodBuilder -> methodBuilder
                                    .withCode(codeBuilder -> {
                                        for (int i = 0; i < type.parameterCount(); i++) {
                                            codeBuilder.loadInstruction(TypeKind.fromDescriptor(
                                                    type.parameterType(i).descriptorString()), i);
                                        }
                                        codeBuilder.invokedynamic(DynamicCallSiteDesc.of(
                                                MethodHandleDesc.ofMethod(
                                                        DirectMethodHandleDesc.Kind.STATIC,
                                                        ClassDesc.of(l.lookupClass().getCanonicalName()),
                                                        bsmMethodName,
                                                        MethodTypeDesc.ofDescriptor(
                                                                bsmType.toMethodDescriptorString())),
                                                name,
                                                MethodTypeDesc.ofDescriptor(type.toMethodDescriptorString()),
                                                boostrapArgs));
                                        codeBuilder.returnInstruction(TypeKind.fromDescriptor(
                                                type.returnType().descriptorString()));
                                    }));
        });
        Class<?> gc = l.defineClass(byteArray);
        return l.findStatic(gc, "m", type);
    }

    public static MethodHandle ldcDynamicConstant(MethodHandles.Lookup l, String name, Class<?> type, String bsmMethodName, MethodType bsmType, ConstantDesc[] bootstrapArgs) throws Exception {
        return ldcDynamicConstant(l, name, type, l.lookupClass(), bsmMethodName, bsmType, bootstrapArgs);
    }

    public static MethodHandle ldcDynamicConstant(MethodHandles.Lookup l, String name, Class<?> type, Class<?> bsmClass, String bsmMethodName, MethodType bsmType, ConstantDesc[] bootstrapArgs) throws Exception {
        return ldcDynamicConstant(l, name, cref(type), csym(bsmClass), bsmMethodName, bsmType.toMethodDescriptorString(), bootstrapArgs);
    }

    public static MethodHandle ldcDynamicConstant(MethodHandles.Lookup l, String name, String type, String bsmMethodName,
                                                  String bsmType, ConstantDesc[] bootstrapArgs) throws Exception {
        return ldcDynamicConstant(l, name, type, csym(l.lookupClass()), bsmMethodName, bsmType, bootstrapArgs);
    }

    public static MethodHandle ldcDynamicConstant(MethodHandles.Lookup l, String name, String type, String bsmClass,
                                                  String bsmMethodName, String bsmType, ConstantDesc[] bootstrapArgs) throws Exception {
        String methodType = "()" + type;
        byte[] bytes = Classfile.of().build(ClassDesc.of(className(l)), classBuilder -> {
            commonBuild(classBuilder);
            classBuilder.withMethod("m", MethodTypeDesc.of(ClassDesc.ofDescriptor(type)),
                    Classfile.ACC_PUBLIC + Classfile.ACC_STATIC, methodBuilder -> methodBuilder
                            .withCode(codeBuilder -> codeBuilder
                                    .ldc(DynamicConstantDesc.ofNamed(
                                            MethodHandleDesc.ofMethod(
                                                    DirectMethodHandleDesc.Kind.STATIC,
                                                    ClassDesc.of(bsmClass),
                                                    bsmMethodName,
                                                    MethodTypeDesc.ofDescriptor(bsmType)),
                                            name,
                                            ClassDesc.ofDescriptor(type),
                                            bootstrapArgs))
                                    .returnInstruction(TypeKind.fromDescriptor(type))));
        });
        Class<?> gc = l.defineClass(bytes);
        return l.findStatic(gc, "m", fromMethodDescriptorString(methodType, l.lookupClass().getClassLoader()));
    }

    public static String csym(Class<?> c) {
        return c.getCanonicalName().replace('.', '/');
    }

    public static String cref(Class<?> c) {
        return c.descriptorString();
    }
}
