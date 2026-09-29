/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.classfile.impl.ChainedClassBuilder;
import org.junit.jupiter.api.Test;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassElement;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassTransform;
import java.lang.constant.ClassDesc;

import static java.lang.classfile.ClassFile.ACC_PUBLIC;
import static java.lang.classfile.ClassFile.ACC_STATIC;
import static java.lang.constant.ConstantDescs.MTD_void;
import static org.junit.jupiter.api.Assertions.*;

/*
 * @test
 * @bug 8336777
 * @summary Testing MethodBuilder correctly rejecting resetting the static
 *          access flag.
 * @run junit MethodBuilderStaticFlagTest
 */
class MethodBuilderStaticFlagTest {

    void testClassBuilder(ClassBuilder clb) {
        clb.withMethod("staticToStatic", MTD_void, ACC_STATIC, mb -> mb.withFlags(ACC_PUBLIC | ACC_STATIC));
        assertThrows(IllegalArgumentException.class, () ->
                clb.withMethod("staticToInstance", MTD_void, ACC_STATIC, mb -> mb.withFlags(ACC_PUBLIC)));
        assertThrows(IllegalArgumentException.class, () ->
                clb.withMethod("instanceToStatic", MTD_void, 0, mb -> mb.withFlags(ACC_PUBLIC | ACC_STATIC)));
        clb.withMethod("instanceToInstance", MTD_void, 0, mb -> mb.withFlags(ACC_PUBLIC));
    }

    @Test
    void testDirectBuilder() {
        ClassFile.of().build(ClassDesc.of("C1"), this::testClassBuilder);
    }

    @Test
    void testBufferedBuilder() {
        var cf = ClassFile.of();
        var bytes = cf.build(ClassDesc.of("C2"), _ -> {});
        var cm = cf.parse(bytes);

        cf.transformClass(cm, new ClassTransform() {
            @Override
            public void accept(ClassBuilder builder, ClassElement element) {
                builder.with(element);
            }

            @Override
            public void atEnd(ClassBuilder clb) {
                assertInstanceOf(ChainedClassBuilder.class, clb);
                testClassBuilder(clb);
            }
        }.andThen(ClassBuilder::with));
    }
}
