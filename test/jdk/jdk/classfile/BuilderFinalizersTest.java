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
 * @summary Testing Classfile builder blocks.
 * @run junit BuilderFinalizersTest
 */


import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import static java.lang.constant.ConstantDescs.*;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.function.Consumer;
import static jdk.internal.classfile.Classfile.*;
import jdk.internal.classfile.CodeBuilder;
import jdk.internal.classfile.components.ClassPrinter;
import jdk.internal.classfile.instruction.NopInstruction;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class BuilderFinalizersTest {

    public void testFinalizers(int cases, int nops, Consumer<CodeBuilder> gen) throws Throwable {
        byte[] bytes = build(ClassDesc.of("TestFinalizer"), List.of(Option.patchDeadCode(false)), //fail when dead code is generated
                clb -> clb.withFlags(ACC_PUBLIC)
                          .withMethodBody("main", MethodTypeDesc.of(CD_int, CD_int), ACC_PUBLIC | ACC_STATIC, gen));
        var clm = parse(bytes);
        ClassPrinter.toYaml(clm, ClassPrinter.Verbosity.CRITICAL_ATTRIBUTES, System.out::print);
        //counting the finalizers
        assertEquals(nops, (int)clm.methods().get(0).code().get().elementStream().filter(e -> e instanceof NopInstruction).count());
        MethodHandles.Lookup lookup = MethodHandles.lookup().defineHiddenClass(bytes, true);
        MethodHandle main = lookup.findStatic(lookup.lookupClass(), "main",
                MethodType.methodType(Integer.TYPE, Integer.TYPE));
        for (int i = 0; i < cases; i++) {
            assertEquals(42, main.invoke(i));
        }
    }

    @Test
    public void testFinalizers() throws Throwable {
        testFinalizers(14, 4, cob -> {
            var externalLabel1 = cob.newBoundLabel();
            var externalLabel2 = cob.newBoundLabel();
            cob.tryWithFinalizer(
                tryb -> tryb.iload(0)
                            .tableswitch(swb -> swb
                                    .switchCase(0, b -> b.iload(0)
                                                         .ireturn())
                                    .switchCase(1, b -> b.iload(0)
                                                         .ireturn())
                                    .switchCase(2, b -> b.iload(0)
                                                         .ireturn())
                                    .switchCase(3, b -> b.iload(0)
                                                         .ireturn())
                                    .switchCase(4, b -> b.new_(CD_Throwable)
                                                         .dup()
                                                         .invokespecial(CD_Throwable, INIT_NAME, MethodTypeDesc.of(CD_void))
                                                         .athrow())
                                    .switchCase(5, b -> b.goto_(externalLabel1))
                                    .switchCase(6, b -> b.goto_(externalLabel1))
                                    .switchCase(7, b -> b.goto_(externalLabel2))
                                    .switchCase(8, b -> b.goto_(externalLabel2))
                                    .switchCase(9, b -> b.goto_(externalLabel1))
                                    .switchCase(10, b -> b.goto_(externalLabel1))
                                    .switchCase(11, b -> b.goto_(externalLabel2))
                                    .switchCase(12, b -> b.goto_(externalLabel2))
                                    .switchCase(13, b -> {}))
                            .iload(0).ireturn(),
                finb -> finb.nop()
                            .constantInstruction(42).ireturn(), externalLabel1, externalLabel2);;
        });
    }
}