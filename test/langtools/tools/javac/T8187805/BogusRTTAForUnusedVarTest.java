/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @test 8187805
 * @summary bogus RuntimeVisibleTypeAnnotations for unused local in a block
 * @enablePreview
 * @modules java.base/jdk.internal.classfile.impl
 *          jdk.compiler/com.sun.tools.javac.util
 * @run main BogusRTTAForUnusedVarTest
 */

import java.io.File;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import java.lang.classfile.*;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.attribute.RuntimeVisibleTypeAnnotationsAttribute;
import com.sun.tools.javac.util.Assert;

public class BogusRTTAForUnusedVarTest {

    class Foo {
        void something() {
            {
                @MyAnno Object o = new Object();
            }
        }
    }

    @Target(ElementType.TYPE_USE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface MyAnno {}

    public static void main(String args[]) throws Throwable {
        new BogusRTTAForUnusedVarTest().run();
    }

    void run() throws Throwable {
        checkRTTA();
    }

    void checkRTTA() throws Throwable {
        File testClasses = new File(System.getProperty("test.classes"));
        File file = new File(testClasses,
                BogusRTTAForUnusedVarTest.class.getName() + "$Foo.class");
        ClassModel classFile = ClassFile.of().parse(file.toPath());
        for (MethodModel m : classFile.methods()) {
            if (m.methodName().equalsString("something")) {
                for (Attribute<?> a : m.attributes()) {
                    if (a instanceof CodeAttribute code) {
                        for (Attribute<?> codeAttrs : code.attributes()) {
                            if (codeAttrs instanceof RuntimeVisibleTypeAnnotationsAttribute) {
                                throw new AssertionError("no RuntimeVisibleTypeAnnotations attribute should have been generated in this case");
                            }
                        }
                    }
                }
            }
        }
    }
}
