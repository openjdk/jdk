/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8186046
 * @summary Stress test ldc to ensure HotSpot correctly manages oop maps
 * @library /java/lang/invoke/common
 * @build test.java.lang.invoke.lib.InstructionHelper
 * @enablePreview
 * @run testng CondyWithGarbageTest
 * @run testng/othervm -XX:+UnlockDiagnosticVMOptions -XX:UseBootstrapCallInfo=3 CondyWithGarbageTest
 */


import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.lang.constant.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import static java.lang.invoke.MethodType.methodType;
import static test.java.lang.invoke.lib.InstructionHelper.classDesc;

public class CondyWithGarbageTest {
    static final MethodHandles.Lookup L = MethodHandles.lookup();

    @Test
    public void testString() throws Throwable {
        MethodHandle mh = lcdStringBasher();
        int l = 0;
        for (int i = 0; i < 100000; i++) {
            l += +((String) mh.invoke()).length();
        }
        Assert.assertTrue(l > 0);
    }

    public static Object bsmString(MethodHandles.Lookup l,
                                   String constantName,
                                   Class<?> constantType) {
        return new StringBuilder(constantName).toString();
    }

    static MethodHandle lcdStringBasher() throws Exception {
        ClassDesc cd = classDesc(L.lookupClass(), "$Code$String");
        byte[] bytes = ClassFile.of().build(cd, classBuilder -> classBuilder
                .withVersion(55, 0)
                .withSuperclass(ConstantDescs.CD_Object)
                .withMethod(ConstantDescs.INIT_NAME, ConstantDescs.MTD_void, ClassFile.ACC_PUBLIC,
                        methodBuilder -> methodBuilder
                                .withCode(codeBuilder -> codeBuilder
                                        .aload(0)
                                        .invokespecial(ConstantDescs.CD_Object, ConstantDescs.INIT_NAME,
                                                ConstantDescs.MTD_void, false)
                                        .return_()))
                .withMethod("m", MethodTypeDesc.of(ConstantDescs.CD_String),
                        ClassFile.ACC_PUBLIC + ClassFile.ACC_STATIC, methodBuilder -> methodBuilder
                                .withCode(codeBuilder -> {
                                            codeBuilder
                                                    .new_(classDesc(StringBuilder.class))
                                                    .dup()
                                                    .invokespecial(classDesc(StringBuilder.class), ConstantDescs.INIT_NAME,
                                                            ConstantDescs.MTD_void, false)
                                                    .astore(0);

                                            for (int i = 10; i < 100; i++) {
                                                ldcString(codeBuilder, Integer.toString(i));
                                                codeBuilder
                                                        .astore(1)
                                                        .aload(0)
                                                        .aload(1)
                                                        .invokevirtual(
                                                                classDesc(StringBuilder.class),
                                                                "append",
                                                                MethodTypeDesc.of(
                                                                        classDesc(StringBuilder.class),
                                                                        classDesc(String.class)))
                                                        .pop();
                                            }

                                            codeBuilder
                                                    .aload(0)
                                                    .invokevirtual(
                                                            classDesc(StringBuilder.class),
                                                            "toString",
                                                            MethodTypeDesc.of(classDesc(String.class)))
                                                    .areturn();
                                        }
                                )));
        Class<?> gc = L.defineClass(bytes);
        return L.findStatic(gc, "m", methodType(String.class));
    }

    private static void ldcString(CodeBuilder codeBuilder, String name) {
        codeBuilder.ldc(DynamicConstantDesc.ofNamed(
                MethodHandleDesc.of(
                        DirectMethodHandleDesc.Kind.STATIC,
                        classDesc(L.lookupClass()),
                        "bsmString",
                        methodType(Object.class, MethodHandles.Lookup.class, String.class, Class.class).toMethodDescriptorString()),
                name,
                classDesc(String.class)
        ));
    }


    @Test
    public void testStringArray() throws Throwable {
        MethodHandle mh = lcdStringArrayBasher();
        int l = 0;
        for (int i = 0; i < 100000; i++) {
            l += +((String) mh.invoke()).length();
        }
        Assert.assertTrue(l > 0);
    }

    public static Object bsmStringArray(MethodHandles.Lookup l,
                                        String constantName,
                                        Class<?> constantType) {
        return new String[]{new StringBuilder(constantName).toString()};
    }

    static MethodHandle lcdStringArrayBasher() throws Exception {
        ClassDesc cd = classDesc(L.lookupClass(), "$Code$StringArray");
        byte[] bytes = ClassFile.of().build(cd, classBuilder -> classBuilder
                .withVersion(55, 0)
                .withSuperclass(ConstantDescs.CD_Object)
                .withMethod(ConstantDescs.INIT_NAME, ConstantDescs.MTD_void, ClassFile.ACC_PUBLIC,
                        methodBuilder -> methodBuilder
                                .withCode(codeBuilder -> codeBuilder
                                        .aload(0)
                                        .invokespecial(ConstantDescs.CD_Object, ConstantDescs.INIT_NAME,
                                                ConstantDescs.MTD_void, false)
                                        .return_()))
                .withMethod("m", MethodTypeDesc.of(classDesc(String.class)),
                        ClassFile.ACC_PUBLIC + ClassFile.ACC_STATIC, methodBuilder -> methodBuilder
                                .withCode(codeBuilder -> {
                                            codeBuilder
                                                    .new_(classDesc(StringBuilder.class))
                                                    .dup()
                                                    .invokespecial(classDesc(StringBuilder.class),
                                                            ConstantDescs.INIT_NAME, ConstantDescs.MTD_void, false)
                                                    .astore(0);

                                            for (int i = 10; i < 100; i++) {
                                                ldcStringArray(codeBuilder, Integer.toString(i));
                                                codeBuilder
                                                        .bipush(0)
                                                        .aaload()
                                                        .astore(1)
                                                        .aload(0)
                                                        .aload(1)
                                                        .invokevirtual(
                                                                classDesc(StringBuilder.class),
                                                                "append",
                                                                MethodTypeDesc.of(
                                                                        classDesc(StringBuilder.class),
                                                                        classDesc(String.class)))
                                                        .pop();
                                            }

                                            codeBuilder
                                                    .aload(0)
                                                    .invokevirtual(
                                                            classDesc(StringBuilder.class),
                                                            "toString",
                                                            MethodTypeDesc.of(classDesc(String.class)))
                                                    .areturn();
                                        }
                                )));
        Class<?> gc = L.defineClass(bytes);
        return L.findStatic(gc, "m", methodType(String.class));
    }

    static void ldcStringArray(CodeBuilder codeBuilder, String name) {
        codeBuilder.ldc(DynamicConstantDesc.ofNamed(
                MethodHandleDesc.of(
                        DirectMethodHandleDesc.Kind.STATIC,
                        classDesc(L.lookupClass()),
                        "bsmStringArray",
                        methodType(Object.class, MethodHandles.Lookup.class, String.class, Class.class).toMethodDescriptorString()
                ),
                name,
                classDesc(String[].class)
        ));
    }
}
