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
 * @bug 8345614 8350704
 * @summary Ensure behavior with duplicated annotations - class, method, or
 *          field fails fast on duplicate annotations, but parameter allows them
 * @library /test/lib
 * @run junit DuplicateAnnotationsTest
 */

import java.io.IOException;
import java.lang.annotation.AnnotationFormatError;
import java.lang.classfile.*;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleParameterAnnotationsAttribute;
import java.lang.constant.ClassDesc;
import java.lang.reflect.AnnotatedElement;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import jdk.test.lib.ByteCodeLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

class DuplicateAnnotationsTest {
    static ClassModel cm;

    @BeforeAll
    static void setup() throws IOException {
        Path annoDuplicatedClass = Path.of(System.getProperty("test.classes")).resolve("AnnotationDuplicated.class");
        cm = ClassFile.of().parse(annoDuplicatedClass);
    }

    interface Extractor {
        AnnotatedElement find(Class<?> cl) throws ReflectiveOperationException;
    }

    // Compiler hint
    static Extractor extract(Extractor e) {
        return e;
    }

    static Arguments[] arguments() {
        Annotation annotationOne = Annotation.of(ClassDesc.of("java.lang.Deprecated"), AnnotationElement.ofBoolean("forRemoval", true));
        Annotation annotationTwo = Annotation.of(ClassDesc.of("java.lang.Deprecated"), AnnotationElement.ofString("since", "24"));
        RuntimeVisibleAnnotationsAttribute rvaa = RuntimeVisibleAnnotationsAttribute.of(
                List.of(annotationOne, annotationTwo)
        );

        return new Arguments[]{
                Arguments.of(
                        "class", true,
                        ClassTransform.endHandler(cob -> cob.with(rvaa)),
                        extract(c -> c)
                ),
                Arguments.of(
                        "field", true,
                        ClassTransform.transformingFields(FieldTransform.endHandler(fb -> fb.with(rvaa))),
                        extract(c -> c.getDeclaredField("field"))
                ),
                Arguments.of(
                        "method", true,
                        ClassTransform.transformingMethods(MethodTransform.endHandler(mb -> mb.with(rvaa))),
                        extract(c -> c.getDeclaredConstructor(int.class))
                ),
                Arguments.of(
                        "parameter", false, // Surprisingly, parameters always allowed duplicate annotations
                        ClassTransform.transformingMethods(MethodTransform.endHandler(mb -> mb.with(
                                RuntimeVisibleParameterAnnotationsAttribute.of(
                                        List.of(List.of(annotationOne, annotationTwo))
                                )
                        ))),
                        extract(c -> c.getDeclaredConstructor(int.class).getParameters()[0])
                ),
        };
    }

    /**
     * A test case represents a declaration that can be annotated.
     * Different declarations have different behaviors when multiple annotations
     * of the same interface are present (without a container annotation).
     *
     * @param caseName the type of declaration, for pretty printing in JUnit
     * @param fails whether this case should fail upon encountering duplicate annotations
     * @param ct transform to install duplicate annotations on the specific declaration
     * @param extractor function to access the AnnotatedElement representing that declaration
     */
    @MethodSource("arguments")
    @ParameterizedTest
    void test(String caseName, boolean fails, ClassTransform ct, Extractor extractor) throws IOException, ReflectiveOperationException {
        var clazz = ByteCodeLoader.load("AnnotationDuplicated", ClassFile.of().transformClass(cm, ct));
        var element = assertDoesNotThrow(() -> extractor.find(clazz));
        Executable exec = () -> element.getAnnotation(Deprecated.class);
        if (fails) {
            var ex = assertThrows(AnnotationFormatError.class, exec, "no duplicate annotation access");
            assertTrue(ex.getMessage().contains("Deprecated"), () -> "missing problematic annotation: " + ex.getMessage());
            assertTrue(ex.getMessage().contains("AnnotationDuplicated"), () -> "missing container class: " + ex.getMessage());
        } else {
            assertDoesNotThrow(exec, "obtaining duplicate annotations should be fine");
            assertEquals(2, Arrays.stream(element.getAnnotations())
                    .filter(anno -> anno instanceof Deprecated)
                    .count());
        }
    }
}

// Duplicate annotations on class, field, method (constructor), method parameter
class AnnotationDuplicated {
    int field;

    AnnotationDuplicated(int arg) {
    }
}
