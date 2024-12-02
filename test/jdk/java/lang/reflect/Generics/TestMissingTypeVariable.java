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

/*
 * @test
 * @library /test/lib
 * @bug 8337302
 * @enablePreview
 * @summary Tests that an exception is thrown if a type variable is not declared
 */

import jdk.test.lib.ByteCodeLoader;

import java.lang.classfile.ClassFile;
import java.lang.classfile.Signature;
import java.lang.classfile.attribute.SignatureAttribute;
import java.lang.constant.ClassDesc;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Type;

public class TestMissingTypeVariable {

    public static void main(String[] args) throws Exception {
        ClassFile cf = ClassFile.of();
        byte[] bytes = cf.build(
                ClassDesc.of("sample.MissingVariable"),
                classBuilder -> {
                    classBuilder.withSuperclass(ClassDesc.of("java.lang.Object"));
                    classBuilder.withFlags(AccessFlag.PUBLIC);
                    classBuilder.withField("f",
                            ClassDesc.of("java.lang.Object"),
                            fieldBuilder -> fieldBuilder.withFlags(AccessFlag.PUBLIC).with(SignatureAttribute.of(Signature.parseFrom("TA;"))));
                });
        /*
          package sample;
          public class MissingVariable {
            public A f; // undeclared type variable
          }
         */
        Class<?> missing = ByteCodeLoader.load("sample.MissingVariable", bytes);
        try {
            Type type = missing.getField("f").getGenericType();
            throw new IllegalStateException("Expected TypeNotPresentException but got: " + type);
        } catch (TypeNotPresentException e) {
            if (!"A".equals(e.typeName())) {
                throw new IllegalStateException("Unexpected name: " + e.typeName());
            }
        }
    }
}
