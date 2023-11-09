/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8304031
 * @summary Testing that primitive class descs are encoded properly as loadable constants.
 * @run junit PrimitiveClassConstantTest
 */

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Supplier;
import jdk.internal.classfile.Classfile;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static java.lang.constant.ConstantDescs.CD_Class;
import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_long;
import static java.lang.constant.ConstantDescs.INIT_NAME;
import static java.lang.constant.ConstantDescs.MTD_void;
import static jdk.internal.classfile.Classfile.ACC_PUBLIC;

public final class PrimitiveClassConstantTest {

    @Test
    public void test() throws Throwable {
        ClassDesc ape = ClassDesc.of("Ape");
        var lookup = MethodHandles.lookup();
        Class<?> a = lookup.defineClass(Classfile.of().build(ape, clb -> {
            clb.withSuperclass(CD_Object);
            clb.withInterfaceSymbols(Supplier.class.describeConstable().orElseThrow());
            clb.withMethodBody(INIT_NAME, MTD_void, ACC_PUBLIC, cob -> {
                cob.aload(0);
                cob.invokespecial(CD_Object, INIT_NAME, MTD_void);
                cob.return_();
            });
            clb.withMethodBody("get", MethodTypeDesc.of(CD_Object), ACC_PUBLIC, cob -> {
                cob.constantInstruction(CD_int);
                cob.areturn();
            });
            clb.withMethodBody("get2", MethodTypeDesc.of(CD_Class), ACC_PUBLIC, cob -> {
                Assertions.assertThrows(IllegalArgumentException.class, () -> cob.constantPool().classEntry(CD_long));
                var t = cob.constantPool().loadableConstantEntry(CD_long);
                cob.ldc(t);
                cob.areturn();
            });
        }));
        Supplier<?> t = (Supplier<?>) lookup.findConstructor(a, MethodType.methodType(void.class))
                .asType(MethodType.methodType(Supplier.class))
                .invokeExact();
        Assertions.assertSame(int.class, t.get());
    }

}
