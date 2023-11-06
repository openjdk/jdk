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
 * @summary Testing Classfile builder parameters.
 * @run junit BuilderParamTest
 */
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import jdk.internal.classfile.Classfile;
import org.junit.jupiter.api.Test;

import static java.lang.constant.ConstantDescs.CD_void;
import static jdk.internal.classfile.Classfile.ACC_STATIC;
import static org.junit.jupiter.api.Assertions.*;

/**
 * BuilderParamTest
 */
class BuilderParamTest {
    @Test
    void testDirectBuilder() {
        var cc = Classfile.of();
        cc.build(ClassDesc.of("Foo"), cb -> {
            cb.withMethod("foo", MethodTypeDesc.ofDescriptor("(IJI)V"), 0,
                          mb -> mb.withCode(xb -> {
                assertEquals(xb.receiverSlot(), 0);
                assertEquals(xb.parameterSlot(0), 1);
                assertEquals(xb.parameterSlot(1), 2);
                assertEquals(xb.parameterSlot(2), 4);
                xb.return_();
            }));
        });
        cc.build(ClassDesc.of("Foo"), cb -> {
            cb.withMethod("foo", MethodTypeDesc.ofDescriptor("(IJI)V"), ACC_STATIC,
                          mb -> mb.withCode(xb -> {
                              assertEquals(xb.parameterSlot(0), 0);
                              assertEquals(xb.parameterSlot(1), 1);
                              assertEquals(xb.parameterSlot(2), 3);
                              xb.return_();
                          }));
        });
    }
}
