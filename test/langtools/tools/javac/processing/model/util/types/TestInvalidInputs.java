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
 * @bug 8340721 8341483
 * @summary Test invalid inputs to javax.lang.model.util.Types methods
 * @library /tools/javac/lib
 * @modules java.compiler
 * @build JavacTestingAbstractProcessor TestInvalidInputs
 * @compile -processor TestInvalidInputs -proc:only TestInvalidInputs.java
 */

import java.util.*;
import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;

/**
 * Test if exceptions are thrown for invalid arguments as expected.
 */
public class TestInvalidInputs extends JavacTestingAbstractProcessor {

    // Reference types are ArrayType, DeclaredType, ErrorType, NullType, and TypeVariable

    private TypeMirror       objectType; // Notable DeclaredType
    private TypeMirror       stringType; // Another notable DeclaredType
    private ArrayType        arrayType;
    // private ErrorType        errorType; // skip for now
    private ExecutableType   executableType;
    private IntersectionType intersectionType;

    private NoType           noTypeVoid;
    private NoType           noTypeNone;
    private NoType           noTypePackage;
    private NoType           noTypeModule;

    private NullType         nullType;
    private PrimitiveType    primitiveType;
    private UnionType        unionType;
    private WildcardType     wildcardType;

    /**
     * Check expected behavior on classes and packages.
     */
    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnv) {
        if (!roundEnv.processingOver()) {
            initializeTypes();

            // isSubType
            // isAssignable
            // contains
            // directSupertypes
            testUnboxedType();
            // capture
            // getPrimitiveType
            // getNoType
            testGetArrayType();
            testGetWildcardType();
            // getDeclaredType
            // getDeclaredType (overload)
            // asMemberOf
        }
        return true;
    }

    void initializeTypes() {
        objectType = elements.getTypeElement("java.lang.Object").asType();
        stringType = elements.getTypeElement("java.lang.String").asType();

        arrayType = types.getArrayType(objectType); // Object[]
        executableType = extractExecutableType();
        intersectionType = extractIntersectionType();

        noTypeVoid = types.getNoType(TypeKind.VOID);
        noTypeNone = types.getNoType(TypeKind.NONE);
        noTypePackage = (NoType)(elements.getPackageElement("java.lang").asType());
        noTypeModule  = (NoType)(elements.getModuleElement("java.base").asType());

        nullType = types.getNullType();
        primitiveType = types.getPrimitiveType(TypeKind.DOUBLE);
        // unionType; // more work here
        wildcardType = types.getWildcardType(objectType, null);

        return;
    }

    ExecutableType extractExecutableType() {
        var typeElement = elements.getTypeElement("TestInvalidInputs.InvalidInputsHost");
        for (var method : ElementFilter.methodsIn(typeElement.getEnclosedElements())) {
            if ("foo7".equals(method.getSimpleName().toString())) {
                return (ExecutableType)method.asType();
            }
        }
        throw new RuntimeException("Expected method not found");
    }

    IntersectionType extractIntersectionType() {
        var typeElement = elements.getTypeElement("TestInvalidInputs.InvalidInputsHost");
        for (var method : ElementFilter.methodsIn(typeElement.getEnclosedElements())) {
            if ("foo9".equals(method.getSimpleName().toString())) {
                return (IntersectionType) ((TypeVariable)method.getReturnType()).getUpperBound();
            }
        }
        throw new RuntimeException("Expected method not found");
    }

    /*
     * Class to host inputs for testing.
     */
    class InvalidInputsHost {
        // Use a method to get an ExecutableType
        public static String foo7(int arg) {return null;}

        // Type variable with intersection type
        public static <S extends Number &  Runnable>  S foo9() {return null;}
    }

    /**
     * @throws IllegalArgumentException if the given type has no
     *         unboxing conversion, including for types that are not
     *         {@linkplain ReferenceType reference types}
     */
    void testUnboxedType() {
        // Only DeclaredType's for wrapper classes should have unboxing conversions defined.

        // Reference types are ArrayType, DeclaredType, ErrorType, NullType, TypeVariable
        // non-reference: ExecutableType, IntersectionType, NoType, PrimitiveType, UnionType, WildcardType
        var invalidInputs =
            List.of(primitiveType, executableType,
                    objectType, stringType, arrayType,
                    intersectionType, /*unionType, */
                    noTypeVoid, noTypeNone, noTypePackage, noTypeModule,
                    nullType,
                    wildcardType);

        for (TypeMirror tm : invalidInputs) {
            try {
                PrimitiveType pt = types.unboxedType(tm);
                shouldNotReach(tm);
            } catch(IllegalArgumentException iae) {
                ; // Expected
            }
        }
        return;
    }

    private void shouldNotReach(TypeMirror tm) {
        throw new RuntimeException("Should not reach " + tm +
                                   " " + tm.getKind());
    }

    /**
     * @throws IllegalArgumentException if bounds are not valid,
     * including for types that are not {@linkplain ReferenceType
     * reference types}
     */
    void testGetWildcardType() {
        // Reference types are ArrayType, DeclaredType, ErrorType, NullType, TypeVariable
        // non-reference: ExecutableType, IntersectionType, NoType, PrimitiveType, UnionType, WildcardType
        var invalidInputs =
            List.of(primitiveType, executableType,
                    intersectionType, /*unionType, */
                    noTypeVoid, noTypeNone, noTypePackage, noTypeModule,
                    nullType,
                    wildcardType);

        for (TypeMirror tm : invalidInputs) {
            try {
                WildcardType wc1 = types.getWildcardType(tm,   null);
                shouldNotReach(tm);
            } catch(IllegalArgumentException iae) {
                ; // Expected
            }

            try {
                WildcardType wc2 = types.getWildcardType(null, tm);
                shouldNotReach(tm);
            } catch(IllegalArgumentException iae) {
                ; // Expected
            }
        }
        return;
    }

    /**
     * @throws IllegalArgumentException if the component type is not valid for
     *          an array. All valid types are {@linkplain ReferenceType
     *          reference types} or {@linkplain PrimitiveType primitive types}.
     *          Invalid types include null, executable, package, module, and wildcard types.
     */
    void testGetArrayType() {
        var invalidInputs =
            List.of(executableType,
                    noTypeVoid, noTypeNone, noTypePackage, noTypeModule,
                    nullType,
                    /*unionType, */ wildcardType);

        for (TypeMirror tm : invalidInputs) {
            try {
                ArrayType arrayType = types.getArrayType(tm);
                shouldNotReach(tm);
            } catch(IllegalArgumentException iae) {
                ; // Expected
            }
        }
    }
}
