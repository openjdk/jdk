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
 * @bug 8266766
 * @summary An array property of a type that is no longer of a type that is a legal member of an
 *          annotation should throw an AnnotationTypeMismatchException.
 * @enablePreview
 * @run main ArrayTypeMismatchTest
 */

import java.lang.annotation.Annotation;
import java.lang.annotation.AnnotationTypeMismatchException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.InvocationTargetException;

import static java.lang.classfile.ClassFile.ACC_ABSTRACT;
import static java.lang.classfile.ClassFile.ACC_PUBLIC;
import static java.lang.constant.ConstantDescs.CD_Object;

public class ArrayTypeMismatchTest {

    public static void main(String[] args) throws Exception {
        /*
         * This test creates an annotation where the annotation member's type is an array with
         * a component type that cannot be legally used for an annotation member. This can happen
         * if a class is recompiled independencly of the annotation type and linked at runtime
         * in this new version. For a test, a class is created as:
         *
         * package sample;
         * @Carrier(value = { @NoAnnotation })
         * class Host { }
         *
         * where NoAnnotation is defined as a regular interface and not as an annotation type.
         * The classes are created by using ASM to emulate this state.
         */
        ByteArrayClassLoader cl = new ByteArrayClassLoader(NoAnnotation.class.getClassLoader());
        cl.init(annotationType(), carrierType());
        @SuppressWarnings("unchecked")
        Class<? extends Annotation> host = (Class<? extends Annotation>) cl.loadClass("sample.Host");
        Annotation sample = cl.loadClass("sample.Carrier").getAnnotation(host);
        try {
            Object value = host.getMethod("value").invoke(sample);
            throw new IllegalStateException("Found value: " + value);
        } catch (InvocationTargetException ite) {
            Throwable cause = ite.getCause();
            if (cause instanceof AnnotationTypeMismatchException e) {
                if (!e.element().getName().equals("value")) {
                    throw new IllegalStateException("Unexpected element: " + e.element());
                } else if (!e.foundType().equals("Array with component tag: @")) {
                    throw new IllegalStateException("Unexpected type: " + e.foundType());
                }
            } else {
                throw new IllegalStateException(cause);
            }
        }
    }

    private static byte[] carrierType() {
        return ClassFile.of().build(ClassDesc.of("sample", "Carrier"), clb -> {
            clb.withSuperclass(CD_Object);
            var badAnnotationArray = AnnotationValue.ofArray(AnnotationValue.ofAnnotation(
                    java.lang.classfile.Annotation.of(
                            NoAnnotation.class.describeConstable().orElseThrow()
                    )));
            clb.with(RuntimeVisibleAnnotationsAttribute.of(
                    java.lang.classfile.Annotation.of(ClassDesc.of("sample", "Host"),
                            AnnotationElement.of("value", badAnnotationArray)
                    )
            ));
        });
    }

    private static byte[] annotationType() {
        return ClassFile.of().build(ClassDesc.of("sample", "Host"), clb -> {
            clb.withSuperclass(CD_Object);
            clb.withInterfaceSymbols(Annotation.class.describeConstable().orElseThrow());
            clb.withFlags(AccessFlag.PUBLIC, AccessFlag.ABSTRACT, AccessFlag.INTERFACE,
                    AccessFlag.ANNOTATION);
            clb.with(RuntimeVisibleAnnotationsAttribute.of(
                    java.lang.classfile.Annotation.of(
                            Retention.class.describeConstable().orElseThrow(),
                            AnnotationElement.of("value", AnnotationValue.of(RetentionPolicy.RUNTIME))
                    )
            ));
            clb.withMethod("value", MethodTypeDesc.of(NoAnnotation[].class.describeConstable()
                    .orElseThrow()), ACC_PUBLIC | ACC_ABSTRACT, mb -> {});
        });
    }

    public interface NoAnnotation { }

    public static class ByteArrayClassLoader extends ClassLoader {

        public ByteArrayClassLoader(ClassLoader parent) {
            super(parent);
        }

        void init(byte[] annotationType, byte[] carrierType) {
            defineClass("sample.Host", annotationType, 0, annotationType.length);
            defineClass("sample.Carrier", carrierType, 0, carrierType.length);
        }
    }
}
