/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8042981
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
    private Types vacuousTypes = new VacuousTypes();

    /**
     * Check expected behavior on classes and packages.
     */
    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnv) {
        if (!roundEnv.processingOver()) {
            TypeElement hostClassElt = eltUtils.getTypeElement("HostClass");
            TypeMirror expectedAnnotation = eltUtils.getTypeElement("TestTypeAnnotation").asType();

            for (ExecutableElement m : methodsIn(hostClassElt.getEnclosedElements())) {
                TypeMirror returnType = m.getReturnType();

                System.err.println("Checking " + returnType);

                testVacuous(returnType);
                checkEmptyAnnotations(typeUtils.stripAnnotations(returnType));

                checkExpectedTypeAnnotations(returnType, expectedAnnotation);

                System.err.print("\tasElement()");
                checkEmptyAnnotations(typeUtils.asElement(returnType));

                System.err.print("\tcapture()");
                checkEmptyAnnotations(typeUtils.capture(returnType));

                System.err.print("\terasure()");
                checkEmptyAnnotations(typeUtils.erasure(returnType));

                System.err.print("\tgetArrayType()");
                ArrayType arrayType = typeUtils.getArrayType(returnType);
                checkEmptyAnnotations(arrayType);
                /*
                 * "Annotations on the component type are preserved."
                 */
                checkEqualTypeAndAnnotations(returnType, arrayType.getComponentType());

                if (!returnType.getKind().isPrimitive()) {
                    /*
                     * "Annotations on the bounds are preserved."
                     */
                    WildcardType wcType;
                    checkEmptyAnnotations(wcType = typeUtils.getWildcardType(returnType, null));
                    checkEqualTypeAndAnnotations(returnType, wcType.getExtendsBound());

                    checkEmptyAnnotations(wcType = typeUtils.getWildcardType(null,       returnType));
                    checkEqualTypeAndAnnotations(returnType, wcType.getSuperBound());
                }

                 System.out.println(returnType.getAnnotation(TestTypeAnnotation.class));
                 System.out.println(returnType.getAnnotationsByType(TestTypeAnnotation.class).length);
                 TestTypeAnnotation ta = requireNonNull(returnType.getAnnotation(TestTypeAnnotation.class),
                                                        returnType.toString());

                System.err.println();
                System.err.println();
            }

            if (failures > 0)
                throw new RuntimeException(failures + " failures occured.");
        }
        return true;
    }

    void testVacuous(TypeMirror tm ) {
        try {
            var result = vacuousTypes.stripAnnotations(tm);
            messager.printError("Unexpected non-exceptional result returned" +  result);
        } catch(UnsupportedOperationException uoe) {
            ; // Expected
        }
    }

    private int failures = 0;

    void checkExpectedTypeAnnotations(AnnotatedConstruct ac, TypeMirror expectedAnnotation) {
        List<? extends AnnotationMirror> annotations = ac.getAnnotationMirrors();
        if (annotations.size() != 1) {
            failures++;
            System.err.println("\t\t\tUnexpected annotations size: " + annotations.size());
        } else if (!typeUtils.isSameType(annotations.get(0).getAnnotationType(), expectedAnnotation)) {
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
            int count = 0;
            for (AnnotationMirror annotation : annotations) {
              if (((TypeElement) annotation.getAnnotationType().asElement()).getQualifiedName().contentEquals("jdk.internal.ValueBased")) {
                continue;
              }
              count++;
            }
            if (count != 0) {
                failures++;
                System.err.println(ac.getClass());
                System.err.println("\t\t\tUnexpected nonzero annotations size: " + annotations);
            }
        }
    }

    void checkEqualTypeAndAnnotations(TypeMirror tm1, TypeMirror tm2) {
        if (!typeUtils.isSameType(tm1, tm2)) {
            failures++;
            System.err.printf("Unequal types %s and %s.%n", tm1, tm2);
        }

        if (!Objects.equals(tm1.getAnnotationMirrors(), tm1.getAnnotationMirrors())) {
            failures++;
            System.err.printf("Unequal annotations on and %s.%n", tm1, tm2);
        }
    }

}

/*
 * Class to host annotations for testing
 */
class HostClass {
    public static @TestTypeAnnotation("foo") Integer foo() {return null;}

    public static @TestTypeAnnotation("foo2") int foo2() {return 0;}

    public static @TestTypeAnnotation("foo3") String foo3() {return null;}

    public static  java.util.@TestTypeAnnotation("foo4")Set foo4() {return null;}

    public static  String @TestTypeAnnotation("foo5")[]  foo5() {return null;}

    public static  java.util. @TestTypeAnnotation("foo6") Set < @TestTypeAnnotation("foo7") String> foo6() {return null;}
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface TestTypeAnnotation {
    String value() default "";
}
