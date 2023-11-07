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
 * @summary Testing Classfile handling JSR and RET instructions.
 * @run junit DiscontinuedInstructionsTest
 */
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.List;
import jdk.internal.classfile.*;
import jdk.internal.classfile.instruction.DiscontinuedInstruction;
import helpers.ByteArrayClassLoader;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static java.lang.constant.ConstantDescs.*;
import static jdk.internal.classfile.Classfile.*;

class DiscontinuedInstructionsTest {

    @Test
    void testJsrAndRetProcessing() throws Exception {
        var testClass = "JsrAndRetSample";
        var testMethod = "testMethod";
        var cd_list = ArrayList.class.describeConstable().get();
        var cc = Classfile.of();
        var bytes = cc.build(ClassDesc.of(testClass), clb -> clb
                .withVersion(JAVA_5_VERSION, 0)
                .withMethodBody(testMethod, MethodTypeDesc.of(CD_void, cd_list), ACC_PUBLIC | ACC_STATIC, cob -> cob
                        .block(bb -> {
                            bb.constantInstruction("Hello")
                              .with(DiscontinuedInstruction.JsrInstruction.of(bb.breakLabel()));
                            bb.constantInstruction("World")
                              .with(DiscontinuedInstruction.JsrInstruction.of(Opcode.JSR_W, bb.breakLabel()))
                              .return_();
                        })
                        .astore(355)
                        .aload(0)
                        .swap()
                        .invokevirtual(cd_list, "add", MethodTypeDesc.of(CD_boolean, CD_Object))
                        .pop()
                        .with(DiscontinuedInstruction.RetInstruction.of(355))));

        var c = cc.parse(bytes).methods().get(0).code().get();
        assertEquals(356, c.maxLocals());
        assertEquals(6, c.maxStack());


        var list = new ArrayList<String>();
        new ByteArrayClassLoader(DiscontinuedInstructionsTest.class.getClassLoader(), testClass, bytes)
                .getMethod(testClass, testMethod)
                .invoke(null, list);
        assertEquals(list, List.of("Hello", "World"));

        bytes = cc.transform(cc.parse(bytes), ClassTransform.transformingMethodBodies(CodeTransform.ACCEPT_ALL));

        new ByteArrayClassLoader(DiscontinuedInstructionsTest.class.getClassLoader(), testClass, bytes)
                .getMethod(testClass, testMethod)
                .invoke(null, list);
        assertEquals(list, List.of("Hello", "World", "Hello", "World"));

        var clm = cc.parse(bytes);

        //test failover stack map generation
        cc.transform(clm, ClassTransform.transformingMethodBodies(CodeTransform.ACCEPT_ALL)
                 .andThen(ClassTransform.endHandler(clb -> clb.withVersion(JAVA_6_VERSION, 0))));

        //test failure of stack map generation for Java 7
        assertThrows(IllegalArgumentException.class, () ->
                cc.transform(clm, ClassTransform.transformingMethodBodies(CodeTransform.ACCEPT_ALL)
                         .andThen(ClassTransform.endHandler(clb -> clb.withVersion(JAVA_7_VERSION, 0)))));

        //test failure of stack map generation when enforced to generate
        assertThrows(IllegalArgumentException.class, () ->
                Classfile.of(Classfile.StackMapsOption.GENERATE_STACK_MAPS)
                         .transform(clm, ClassTransform.transformingMethodBodies(CodeTransform.ACCEPT_ALL)));
    }
}
