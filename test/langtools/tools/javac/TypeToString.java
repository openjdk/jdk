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
 * @bug 8309881
 * @library /tools/javac/lib
 * @modules java.compiler
 *          jdk.compiler
 * @build JavacTestingAbstractProcessor TypeToString
 * @compile -cp . -processor TypeToString -proc:only TypeToString.java
 * @compile/process -cp . -processor TypeToString -proc:only Test
 */
import java.lang.Runtime.Version;
import java.util.*;
import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.lang.model.util.*;

@SupportedAnnotationTypes("*")
public class TypeToString extends JavacTestingAbstractProcessor {

    public boolean process(Set<? extends TypeElement> typeElementSet,RoundEnvironment renv) {
        if (renv.processingOver()) {
            TypeElement testClass = processingEnv.getElementUtils().getTypeElement("Test");
            ExecutableElement method = ElementFilter.methodsIn(testClass.getEnclosedElements())
                                                    .iterator()
                                                    .next();
            String expectedTypeToString = "java.lang.Runtime.Version";
            String actualToString = method.getReturnType().toString();

            if (!Objects.equals(expectedTypeToString, actualToString)) {
                throw new AssertionError("Unexpected toString value. " +
                                         "Expected: " + expectedTypeToString + ", " +
                                         "but got: " + actualToString);
            }

            actualToString = method.getParameters().get(0).asType().toString();

            if (!Objects.equals(expectedTypeToString, actualToString)) {
                throw new AssertionError("Unexpected toString value. " +
                                         "Expected: " + expectedTypeToString + ", " +
                                         "but got: " + actualToString);
            }
        }
        return false;
    }
}

class Test {
    public Version get(Version v) {
        return null;
    }
}
