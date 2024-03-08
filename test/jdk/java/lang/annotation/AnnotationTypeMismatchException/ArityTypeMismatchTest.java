/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8266791
 * @summary Annotation property which is compiled as an array property but
 *          changed observed as a singular element should throw an
 *          AnnotationTypeMismatchException
 * @enablePreview
 * @run main ArityTypeMismatchTest
 */

import java.lang.annotation.AnnotationTypeMismatchException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.constant.ClassDesc;

import static java.lang.constant.ConstantDescs.CD_Object;

public class ArityTypeMismatchTest {

    public static void main(String[] args) throws Exception {
        /*
         * This test creates an annotation with a member with a non-array type where the annotation
         * defines an array property of this type. This can happen if the annotation class is recompiled
         * without recompiling the code that declares an annotation of this type. In the example, a
         * class is defined to be annotated as
         *
         * @AnAnnotation(value = {"v"}) // should no longer be an array
         * class Carrier { }
         *
         * where @AnAnnotation expects a singular value.
         */
        byte[] b = ClassFile.of().build(ClassDesc.of("sample", "Carrier"), clb -> {
            clb.withSuperclass(CD_Object);
            clb.with(RuntimeVisibleAnnotationsAttribute.of(
                    Annotation.of(
                            AnAnnotation.class.describeConstable().orElseThrow(),
                            AnnotationElement.of("value", AnnotationValue.of(new String[] {"v"}))
                    )
            ));
        });
        ByteArrayClassLoader cl = new ByteArrayClassLoader(ArityTypeMismatchTest.class.getClassLoader());
        cl.init(b);
        AnAnnotation sample = cl.loadClass("sample.Carrier").getAnnotation(AnAnnotation.class);
        try {
            String value = sample.value();
            throw new IllegalStateException("Found value: " + value);
        } catch (AnnotationTypeMismatchException e) {
            if (!e.element().getName().equals("value")) {
                throw new IllegalStateException("Unexpected element: " + e.element());
            } else if (!e.foundType().equals("Array with component tag: s")) {
                throw new IllegalStateException("Unexpected type: " + e.foundType());
            }
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface AnAnnotation {
        String value();
    }

    public static class ByteArrayClassLoader extends ClassLoader {

        public ByteArrayClassLoader(ClassLoader parent) {
            super(parent);
        }

        public void init(byte[] b) {
            defineClass("sample.Carrier", b, 0, b.length);
        }
    }
}
