/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
 * @test
 * @summary Ensures preloading.
 * @library /test/lib
 * @enablePreview
 * @run junit runtime.valhalla.inlinetypes.classloading.LoadableDescriptorsTest
 */

package runtime.valhalla.inlinetypes.classloading;

import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.LoadableDescriptorsAttribute;
import java.lang.constant.ClassDesc;
import java.lang.reflect.Field;

import jdk.test.lib.ByteCodeLoader;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static java.lang.classfile.ClassFile.*;
import static java.lang.constant.ConstantDescs.*;

// NOTE: Needs further work for JDK-8367134.
class LoadableDescriptorsTest {
    private static final boolean DEBUG = false;

    @ParameterizedTest
    @ValueSource(strings = {
        "LTest;",
        "I",
        "[[LTest;",
    })
    void test(String descriptorString) throws ReflectiveOperationException {
        ClassDesc loadableClass = ClassDesc.ofDescriptor(descriptorString);
        var bytes = ClassFile.of().build(ClassDesc.of("Test"), clb ->
            clb
                .withVersion(latestMajorVersion(), PREVIEW_MINOR_VERSION)
                .withFlags(ACC_PUBLIC | ACC_IDENTITY)
                .withMethodBody(INIT_NAME, MTD_void, ACC_PUBLIC, cob ->
                cob.aload(0)
                    .invokespecial(CD_Object, INIT_NAME, MTD_void)
                    .return_())
                    .withField("theField", loadableClass, ACC_PUBLIC)
                .with(LoadableDescriptorsAttribute.of(clb.constantPool().utf8Entry(loadableClass)))
        );

        Class<?> clazz = ByteCodeLoader.load("Test", bytes);
        Object instance = clazz.getDeclaredConstructor().newInstance();
        Field field = clazz.getDeclaredField("theField");
        field.get(instance);
    }
}
