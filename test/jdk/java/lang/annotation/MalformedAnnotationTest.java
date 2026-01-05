/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8350704
 * @summary Test behaviors with malformed annotations (in class files)
 * @library /test/lib
 * @run junit MalformedAnnotationTest
 */

import jdk.test.lib.ByteCodeLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.constant.ClassDesc;
import java.lang.reflect.GenericSignatureFormatError;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MalformedAnnotationTest {

    /**
     * An annotation that has elements of the Class type.
     * Useful for checking behavior when the string is not a descriptor string.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @interface ClassCarrier {
        Class<?> value();
    }

    static Stream<String> badFieldDescriptors() {
        return Arrays.stream(new String[] {
                "Not a_descriptor",
                "()V",
                "Ljava/lang/Object",
                "Ljava/",
                "Ljava/util/Map.Entry;",
                "[".repeat(256) + "I",
                "Lbad.Name;",
                "Lbad[Name;",
                "L;",
                "L/Missing;",
                "Lmissing/;",
        });
    }

    /**
     * Ensures bad class descriptors in annotations lead to
     * {@link GenericSignatureFormatError} and the error message contains the
     * malformed descriptor string.
     */
    @ParameterizedTest
    @MethodSource("badFieldDescriptors")
    void testMalformedClassValue(String badDescString) throws Exception {
        var cl = spinClass(badDescString);
        var ex = assertThrows(GenericSignatureFormatError.class, () -> cl.getDeclaredAnnotation(ClassCarrier.class));
        assertTrue(ex.getMessage().contains(badDescString), () -> "Uninformative error: " + ex);
    }

    private static Class<?> spinClass(String desc) throws Exception {
        var bytes = ClassFile.of().build(ClassDesc.of("Test"), clb -> clb
                .with(RuntimeVisibleAnnotationsAttribute.of(
                        Annotation.of(ClassCarrier.class.describeConstable().orElseThrow(),
                                AnnotationElement.of("value", AnnotationValue.ofClass(clb
                                        .constantPool().utf8Entry(desc))))
                )));
        return new ByteCodeLoader("Test", bytes, ClassCarrier.class.getClassLoader()).loadClass("Test");
    }

    static Stream<String> goodFieldDescriptors() {
        return Arrays.stream(new String[] {
                "Ljava/lang/Object<*>;", // previously MalformedParameterizedTypeException
                "[Ljava/util/Optional<*>;", // previously ClassCastException
                "Ljava/util/Map$Entry<**>;", // previously ClassCastException
        });
    }

    @ParameterizedTest
    @MethodSource("goodFieldDescriptors")
    void testLegalClassValue(String goodDescString) throws Exception {
        var cl = spinClass(goodDescString);
        var anno = cl.getDeclaredAnnotation(ClassCarrier.class);
        assertThrows(TypeNotPresentException.class, anno::value);
    }
}
