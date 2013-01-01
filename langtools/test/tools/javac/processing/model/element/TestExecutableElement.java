/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8005046
 * @summary Test basic properties of javax.lang.element.Element
 * @author  Joseph D. Darcy
 * @library /tools/javac/lib
 * @build   JavacTestingAbstractProcessor TestExecutableElement
 * @compile -processor TestExecutableElement -proc:only TestExecutableElement.java
 */

import java.lang.annotation.*;
import java.util.Formatter;
import java.util.Set;
import java.util.Objects;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import static javax.lang.model.SourceVersion.*;
import javax.lang.model.element.*;
import javax.lang.model.util.*;
import static javax.lang.model.util.ElementFilter.*;
import static javax.tools.Diagnostic.Kind.*;
import static javax.tools.StandardLocation.*;

/**
 * Test some basic workings of javax.lang.element.ExecutableElement
 */
public class TestExecutableElement extends JavacTestingAbstractProcessor implements ProviderOfDefault {
    @IsDefault(false)
    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnv) {
        int errors = 0;
        if (!roundEnv.processingOver()) {
            boolean hasRun = false;
            for (Element element : roundEnv.getRootElements()) {
                for (ExecutableElement method : methodsIn(element.getEnclosedElements())) {
                    hasRun = true;
                    errors += checkIsDefault(method);
                }
            }

            if (!hasRun) {
                messager.printMessage(ERROR, "No test cases run; test fails.");
            }
        }
        return true;
    }

    @IsDefault(false)
    int checkIsDefault(ExecutableElement method) {
        System.out.println("Testing " + method);
        IsDefault expectedIsDefault = method.getAnnotation(IsDefault.class);

        boolean expectedDefault = (expectedIsDefault != null) ?
            expectedIsDefault.value() :
            false;

        boolean methodIsDefault = method.isDefault();

        if (methodIsDefault != expectedDefault) {
            messager.printMessage(ERROR,
                                  new Formatter().format("Unexpected Executable.isDefault result: got %s, expected %s",
                                                         expectedDefault,
                                                         methodIsDefault).toString(),
                                  method);
            return 1;
        }
        return 0;
    }
}

/**
 * Expected value of the ExecutableElement.isDefault method.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface IsDefault {
    boolean value();
}

/**
 * Test interface to provide a default method.
 */
interface ProviderOfDefault {
    @IsDefault(false)
    boolean process(Set<? extends TypeElement> annotations,
                    RoundEnvironment roundEnv);

    @IsDefault(true)
    default void quux() {};
}
