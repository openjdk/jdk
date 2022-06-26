/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
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
 * @bug 6453386
 * @summary Test if annotations are stripped from the results of Types' methods
 * @library /tools/javac/lib
 * @modules java.compiler
 * jdk.compiler
 * @build JavacTestingAbstractProcessor TestAnnotationStripping
 * @compile -processor TestAnnotationStripping -proc:only TestAnnotationStripping.java
 */

import java.lang.annotation.*;
import java.util.*;
import static java.util.Objects.*;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import static javax.lang.model.SourceVersion.*;
import javax.lang.model.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;
import static javax.lang.model.util.ElementFilter.*;
import static javax.tools.Diagnostic.Kind.*;
import static javax.tools.StandardLocation.*;

/**
 * Test if annotations are stripped from the results of Types' methods
 */
public class TestAnnotationStripping extends JavacTestingAbstractProcessor {
    /**
     * Check expected behavior on classes and packages.
     */
    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnv) {
        if (!roundEnv.processingOver()) {
            TypeElement hostClassElt = eltUtils.getTypeElement("HostClass");
            TypeMirror expectedAnnotation = eltUtils.getTypeElement("TypeAnnotation").asType();

            for (ExecutableElement m : methodsIn(hostClassElt.getEnclosedElements())) {
                TypeMirror returnType = m.getReturnType();

                System.err.println("Checking " + returnType);


                checkExpectedTypeAnnotations(returnType, expectedAnnotation);

//                 System.err.print("\tasElement()");
//                 checkEmptyAnnotations(typeUtils.asElement(returnType));

                System.err.print("\tcapture()");
                checkEmptyAnnotations(typeUtils.capture(returnType));

                System.err.print("\terasure()");
                checkEmptyAnnotations(typeUtils.erasure(returnType));

//                 System.err.print("\tgetArrayType()");
//                 checkEmptyAnnotations(typeUtils.getArrayType(returnType));

                // System.out.println(returnType.getAnnotation(TypeAnnotation.class));
                // System.out.println(returnType.getAnnotationsByType(TypeAnnotation.class).length);
                // TypeAnnotation ta = requireNonNull(returnType.getAnnotation(TypeAnnotation.class));

                System.err.println();
                System.err.println();
            }

            if (failures > 0)
                throw new RuntimeException(failures + " failures occured.");
        }
        return true;
    }

    private int failures = 0;

    void checkExpectedTypeAnnotations(AnnotatedConstruct ac, TypeMirror expectedAnnotation) {
        List<? extends AnnotationMirror> annotations = ac.getAnnotationMirrors();
        if (annotations.size() != 1) {
            failures++;
            System.err.println("\t\t\tUnexpected annotations size: " + annotations);
        }
        if (!typeUtils.isSameType(annotations.get(0).getAnnotationType(), expectedAnnotation)) {
            failures++;
            System.err.println("\t\t\tUnexpected annotations type: " + annotations);
        }
    }

    void checkEmptyAnnotations(AnnotatedConstruct ac) {
        System.err.println("\t" + ac);
        if (ac == null)
            return;
        else {
            List<? extends AnnotationMirror> annotations = ac.getAnnotationMirrors();
            if (annotations.size() != 0) {
                failures++;
                System.err.println(ac.getClass());
                System.err.println("\t\t\tUnexpected nonzero annotations size: " + annotations);
            }
        }
    }
}

/*
 * Class to host annotations for testing
 */
class HostClass {
    public static @TypeAnnotation("foo") Integer foo() {return null;}

    public static @TypeAnnotation("foo2") int foo2() {return 0;}

    public static @TypeAnnotation("foo3") String foo3() {return null;}

    // public static @TypeAnnotation("foo4") java.util.Set foo4() {return null;}

    // public static @TypeAnnotation("foo4") String[] foo4() {return null;}

    // public static java.util.Set < String> foo5() {return null;}
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface TypeAnnotation {
    String value() default "";
}
