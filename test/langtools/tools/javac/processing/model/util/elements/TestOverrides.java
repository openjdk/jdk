/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8302019
 * @summary Test basic operation of Elements.overrides
 * @library /tools/javac/lib
 * @build   JavacTestingAbstractProcessor TestOverrides
 * @compile -processor TestOverrides -proc:only TestOverrides.java
 */

import java.util.*;
import java.util.function.*;
import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.lang.model.util.*;

/**
 * Test basic workings of Elements.overrides
 */
public class TestOverrides extends JavacTestingAbstractProcessor {
    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnv) {
        if (!roundEnv.processingOver()) {
            checkObjectOverrides();
        }
        return true;
    }

    private void checkObjectOverrides() {
        boolean elementSeen = false;

        TypeElement objectElt = elements.getTypeElement("java.lang.Object");

        TypeElement objectInterfaceElt = elements.getTypeElement("ObjectInterface");
        for (var method : ElementFilter.methodsIn(objectInterfaceElt.getEnclosedElements())) {
            elementSeen = true;
            Name methodName = method.getSimpleName();
            boolean expectedOverrideResult = method.getAnnotation(OverrideExpected.class).value();
            if (expectedOverrideResult !=
                elements.overrides(method, findMethod(methodName, objectElt), objectInterfaceElt ) ) {
                throw new RuntimeException("Unexpected overriding relation found for " + method);
            }

            if (!elementSeen) {
                throw new RuntimeException("No elements seen.");
            }
        }
    }

    ExecutableElement findMethod(Name name, TypeElement typeElt) {
        for (var method : ElementFilter.methodsIn(typeElt.getEnclosedElements())) {
            if (method.getSimpleName().equals(name)) {
                return method;
            }
        }
        return null;
    }

}

@interface OverrideExpected {
    boolean value() default true;
}

/**
 * Interface that has methods override-equivalent to methods of
 * java.lang.Object.
 */
interface ObjectInterface {
    @Override
    @OverrideExpected
    boolean equals(Object obj);

    @Override
    @OverrideExpected
    int hashCode();

    @Override
    @OverrideExpected
    String toString();

    @OverrideExpected(value=false) // protected, not public method
    Object clone();

    @OverrideExpected(value=false) // protected, not public method
    void finalize();

    // Final methods of Object (getClass, wait, notify[all]) rejected
    // if declared in an interface.
}

