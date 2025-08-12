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
            TypeElement juSetElt = eltUtils.getTypeElement("java.util.Set");
            TypeElement testElt  = elements.getTypeElement("TestAnnotationStripping");
            TypeElement boxElt   = elements.getTypeElement("TestAnnotationStripping.Box");

            TypeMirror expectedAnnotation = eltUtils.getTypeElement("TestTypeAnnotation").asType();

            for (ExecutableElement m :
                     methodsIn(eltUtils.getTypeElement("HostClass").getEnclosedElements())) {
                /*
                 * The kinds of types include:
                 *
                 * arrays
                 * declared types (classes, interfaces, etc.)
                 * error types
                 * executable types
                 * intersection types
                 * no-type
                 * null type
                 * primitive types
                 * type variable
                 * union type
                 * wildcards
                 *
                 * A subset of these can appear at the return type of
                 * a method. The general methodology is to verify that
                 * types that can appear as return types when
                 * annotated with type annotations appear as specified
                 * as the result of type operations or when new types
                 * are constructed.
                 */

                TypeMirror returnType = m.getReturnType();

                System.err.println("Checking " + returnType);

                testVacuous(returnType);
                checkDeepEmptyAnnotations(typeUtils.stripAnnotations(returnType));

                checkExpectedTypeAnnotations(returnType, expectedAnnotation);

                // Note: the result of Types.asElement is *not*
                // checked for its annotations since the return value
                // is an Element and not a TypeMirror.

                System.err.print("\tcapture()");
                checkDeepEmptyAnnotations(typeUtils.capture(returnType));

                System.err.print("\terasure()");
                checkDeepEmptyAnnotations(typeUtils.erasure(returnType));

                System.err.print("\tgetArrayType()");
                ArrayType arrayType = typeUtils.getArrayType(returnType);
                checkEmptyAnnotations(arrayType);
                /*
                 * "Annotations on the component type are preserved."
                 */
                checkEqualTypeAndAnnotations(returnType, arrayType.getComponentType());

                if (!returnType.getKind().isPrimitive()) {
                    /*
                     * For getWildcardType()
                     * "Annotations on the bounds are preserved."
                     */
                    WildcardType wcType;
                    checkEmptyAnnotations(wcType = typeUtils.getWildcardType(returnType, null));
                    checkEqualTypeAndAnnotations(returnType, wcType.getExtendsBound());

                    checkEmptyAnnotations(wcType = typeUtils.getWildcardType(null,       returnType));
                    checkEqualTypeAndAnnotations(returnType, wcType.getSuperBound());

                    /*
                     * For getDeclaredType()
                     * "Annotations on the type arguments are preserved."
                     */
                    DeclaredType declaredType = typeUtils.getDeclaredType(juSetElt, returnType);
                    checkEqualTypeAndAnnotations(returnType, declaredType.getTypeArguments().get(0));

                    // Check both overloads
                    declaredType = typeUtils.getDeclaredType(typeUtils.getDeclaredType(testElt), // outer type
                                                             boxElt,
                                                             returnType);
                    checkEqualTypeAndAnnotations(returnType, declaredType.getTypeArguments().get(0));
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
            messager.printError("Unexpected non-exceptional result returned " +  result);
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
            int count = annotations.size();
            if (count != 0) {
                failures++;
                System.err.println(ac.getClass());
                System.err.println("\t\t\tUnexpected nonzero annotations size: " + annotations);
            }
        }
    }

    void checkDeepEmptyAnnotations(TypeMirror ac) {
        System.err.println("\t" + ac);
        if (ac == null) {
            return;
        }
        new SimpleTypeVisitor14<Void, Void>() {
            @Override
            protected Void defaultAction(TypeMirror t, Void o) {
                checkEmptyAnnotations(t);
                return null;
            }

            @Override
            public Void visitArray(ArrayType t, Void o) {
                scan(t.getComponentType());
                return super.visitArray(t, o);
            }

            @Override
            public Void visitDeclared(DeclaredType t, Void o) {
                scan(t.getEnclosingType());
                t.getTypeArguments().stream().forEach(this::scan);
                return super.visitDeclared(t, o);
            }

            @Override
            public Void visitTypeVariable(TypeVariable t, Void o) {
                // the bounds correspond to the type variable declaration, not its use
                // scan(t.getUpperBound());
                // scan(t.getLowerBound());
                return super.visitTypeVariable(t, o);
            }

            @Override
            public Void visitWildcard(WildcardType t, Void o) {
                scan(t.getExtendsBound());
                scan(t.getSuperBound());
                return super.visitWildcard(t, o);
            }

            private void scan(TypeMirror t) {
                if (t != null) {
                    visit(t);
                }
            }
        }.visit(ac);
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

    // Nested class to test getDeclaredType overload.
    class Box<T> {
        private T contents;

        public Box(T t){
            contents = t;
        }

        T value() { return contents;};
    }
}

/*
 * Class to host annotations for testing
 */
class HostClass {
    // Declared type Integer
    public static @TestTypeAnnotation("foo") Integer foo() {return null;}

    // Primitive type int
    public static @TestTypeAnnotation("foo2") int foo2() {return 0;}

    public static @TestTypeAnnotation("foo3") String foo3() {return null;}

    // Declared raw type Set
    public static  java.util.@TestTypeAnnotation("foo4")Set foo4() {return null;}

    // Array type
    public static  String @TestTypeAnnotation("foo5")[]  foo5() {return null;}

    // Declared type Set with instantiated type parameter
    public static  java.util. @TestTypeAnnotation("foo6") Set < @TestTypeAnnotation("foo7") String> foo6() {return null;}

    // Type variable
    public static <@TestTypeAnnotation("foo8") T extends @TestTypeAnnotation("foo9") String> @TestTypeAnnotation("foo10") T foo7() {return null;}

    // Declared type including wildcard
    public static  java.util. @TestTypeAnnotation("foo11") Set < @TestTypeAnnotation("foo12") ? extends @TestTypeAnnotation("foo13") Number> foo8() {return null;}

    // Type variable with intersection type
    public static <@TestTypeAnnotation("foo14") S extends Number &  Runnable> @TestTypeAnnotation("foo15") S foo9() {return null;}

}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface TestTypeAnnotation {
    String value() default "";
}
