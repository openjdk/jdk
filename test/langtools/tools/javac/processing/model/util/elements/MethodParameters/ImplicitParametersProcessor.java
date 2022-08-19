/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8292275
 * @summary Check origin of implicit parameters
 * @library /tools/javac/lib
 * @modules java.compiler
 *          jdk.compiler
 * @build   JavacTestingAbstractProcessor ImplicitParametersProcessor
 * @compile -processor ImplicitParametersProcessor -proc:only ClassContainer.java
 */
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static javax.lang.model.util.ElementFilter.constructorsIn;
import static javax.lang.model.util.ElementFilter.methodsIn;
import static javax.lang.model.util.ElementFilter.typesIn;

public class ImplicitParametersProcessor extends JavacTestingAbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return true;
        }
        boolean hasError = false;
        for (TypeElement typeElement : typesIn(roundEnv.getRootElements())) {
            for (TypeElement innerType : typesIn(typeElement.getEnclosedElements())) {
                System.out.println("Visiting " + innerType);if ("MyRecord".contentEquals(innerType.getSimpleName())) {
                    hasError |= checkAllExecutables(innerType, Map.of(
                            "<init>", List.of(Elements.Origin.MANDATED, Elements.Origin.MANDATED)
                    ));
                } else if ("MyEnum".contentEquals(innerType.getSimpleName())) {
                    hasError |= checkAllExecutables(innerType, Map.of(
                            "valueOf", List.of(Elements.Origin.MANDATED)
                    ));
                }
            }
        }
        if (hasError) {
            throw new IllegalStateException("Wrong element origins found");
        }
        return true;
    }

    boolean checkAllExecutables(TypeElement element, Map<String, List<Elements.Origin>> expectations) {
        boolean hasError = false;
        for (ExecutableElement executable : constructorsIn(element.getEnclosedElements())) {
            hasError |= checkExecutable(expectations, executable);
        }
        for (ExecutableElement executable : methodsIn(element.getEnclosedElements())) {
            hasError |= checkExecutable(expectations, executable);
        }
        return hasError;
    }

    private boolean checkExecutable(Map<String, List<Elements.Origin>> expectations, ExecutableElement executable) {
        System.out.println("Looking at executable " + executable);
        List<Elements.Origin> list = expectations.get(executable.getSimpleName().toString());
        if (list == null) {
            System.out.println("ignoring this executable due to missing expectations");
            return false;
        }
        List<? extends VariableElement> parameters = executable.getParameters();
        System.out.println("found " + parameters);
        boolean hasError = false;
        for (int i = 0; i < parameters.size(); i++) {
            VariableElement parameter = parameters.get(i);
            Elements.Origin origin = eltUtils.getOrigin(parameter);
            if (origin != list.get(i)) {
                System.out.println("ERROR: Wrong origin. Expected: " + list.get(i) + " but got " + origin);
                hasError = true;
            }
        }
        return hasError;
    }
}
