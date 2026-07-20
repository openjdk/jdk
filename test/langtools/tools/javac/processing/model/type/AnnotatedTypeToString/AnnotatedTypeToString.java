/*
 * Copyright (c) 2022, Google LLC. All rights reserved.
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
 * @bug 8284220 8342934
 * @summary Tests DeclaredType.toString with type annotations present, for example that '@A
 * Map.Entry' is printed as 'java.util.@A Map.Entry' (and not '@A java.util.Map.Entry' or
 * 'java.util.@A Entry').
 * @library /tools/javac/lib
 * @build AnnotatedTypeToString JavacTestingAbstractProcessor ExpectedToString
 * @compile -processor AnnotatedTypeToString -proc:only Test.java
 */

import p.ExpectedToString;

import java.util.Set;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/**
 * Verify that the toString representation of the types of elements annotated with {@code
 * ExpectedToString} matches the expected string representation in the annotation.
 */
@SupportedAnnotationTypes("p.ExpectedToString")
public class AnnotatedTypeToString extends JavacTestingAbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }
        for (Element e : roundEnv.getElementsAnnotatedWith(ExpectedToString.class)) {
            String expected = e.getAnnotation(ExpectedToString.class).value();
            String actual = e.asType().toString();
            if (!expected.equals(actual)) {
                processingEnv
                        .getMessager()
                        .printError(String.format("expected: %s, was: %s", expected, actual), e);
            }
        }
        return false;
    }
}
