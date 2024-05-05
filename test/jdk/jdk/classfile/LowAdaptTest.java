/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Testing ClassFile low adaptation.
 * @run junit LowAdaptTest
 */
import java.lang.constant.ClassDesc;
import static java.lang.constant.ConstantDescs.*;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.net.URI;
import java.nio.file.Paths;

import java.lang.classfile.AccessFlags;
import java.lang.reflect.AccessFlag;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassFile;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import helpers.ByteArrayClassLoader;
import java.lang.classfile.attribute.SourceFileAttribute;
import jdk.internal.classfile.impl.DirectClassBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LowAdaptTest {

    static final String test = "LowAdaptTest$TestClass";

    @Test
    void testAdapt() throws Exception {
        var cc = ClassFile.of();
        ClassModel cl = cc.parse(Paths.get(URI.create(LowAdaptTest.class.getResource(test + ".class").toString())));

        DirectMethodHandleDesc bsm = MethodHandleDesc.ofMethod(DirectMethodHandleDesc.Kind.STATIC,
                                                               ClassDesc.of("java.lang.invoke.LambdaMetafactory"),
                                                               "metafactory",
                                                               MethodTypeDesc.ofDescriptor("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
                                                                                           + "Ljava/lang/invoke/MethodType;"
                                                                                           + "Ljava/lang/invoke/MethodType;"
                                                                                           + "Ljava/lang/invoke/MethodHandle;"
                                                                                           + "Ljava/lang/invoke/MethodType;"
                                                                                           + ")Ljava/lang/invoke/CallSite;"));
        DynamicCallSiteDesc indy = DynamicCallSiteDesc.of(bsm,
                                                          "applyAsInt", MethodTypeDesc.ofDescriptor("()Ljava/util/function/IntUnaryOperator;"),
                                                          MethodTypeDesc.ofDescriptor("(I)I"),
                                                          MethodHandleDesc.of(DirectMethodHandleDesc.Kind.STATIC, ClassDesc.of(test), "fib", "(I)I"),
                                                          MethodTypeDesc.ofDescriptor("(I)I"));

        byte[] clazz = cc.build(ClassDesc.of(test), cb -> {
            cb.withFlags(AccessFlag.PUBLIC);
            cb.with(SourceFileAttribute.of("/some/madeup/TestClass.java"));
            cl.methods().forEach(m -> ((DirectClassBuilder) cb).withMethod(m));

            cb.withMethod("doit", MethodTypeDesc.of(CD_int, CD_int),
                          AccessFlags.ofMethod(AccessFlag.PUBLIC, AccessFlag.STATIC).flagsMask(),
                          mb -> mb.withCode(xb -> {
                              xb.invokedynamic(indy);
                              xb.astore(1);
                              xb.aload(1);
                              xb.iload(0);
                              xb.invokeinterface(ClassDesc.of("java.util.function.IntUnaryOperator"),
                                                   "applyAsInt", MethodTypeDesc.ofDescriptor("(I)I"));
                              xb.istore(2);
                              xb.iload(2);
                              xb.ireturn();
                          }));
        });


        int result = (Integer)
                new ByteArrayClassLoader(LowAdaptTest.class.getClassLoader(), test, clazz)
                        .getMethod(test,"doit")
                        .invoke(null, 10);
        assertEquals(result, 55);
    }

    public static class TestClass {

        static int fib(int n) {
            if (n <= 1)
                return n;
            return fib(n - 1) + fib(n - 2);
        }
    }
}
