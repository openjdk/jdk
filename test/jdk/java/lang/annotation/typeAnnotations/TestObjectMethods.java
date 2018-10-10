/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8058202
 * @summary Test java.lang.Object methods on AnnotatedType objects.
 */

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;

/**
 * Test toString, equals, and hashCode on various AnnotatedType objects.
 */
public class TestObjectMethods {
    private static int errors = 0;

    /*
     * There are various subtypes of AnnotatedType implementations:
     *
     * AnnotatedType
     * AnnotatedArrayType
     * AnnotatedParameterizedType
     * AnnotatedTypeVariable
     * AnnotatedWildcardType
     *
     * The implementations of each these implementations are
     * examined. Wildcards don't appear as top-level types and need to
     * be extracted from bounds.
     *
     * AnnotatedTypes with and without annotations are examined as
     * well.
     */
    public static void main(String... args) {
        Class<?>[] testClasses = {TypeHost.class, AnnotatedTypeHost.class};

        for (Class<?> clazz : testClasses) {
            testEqualsReflexivity(clazz);
            testEquals(clazz);
        }

        testToString(TypeHost.class, false);
        testToString(AnnotatedTypeHost.class, true);

        testAnnotationsMatterForEquals(TypeHost.class, AnnotatedTypeHost.class);

        testGetAnnotations(TypeHost.class, false);
        testGetAnnotations(AnnotatedTypeHost.class, true);

        testWildcards();

        if (errors > 0) {
            throw new RuntimeException(errors + " errors");
        }
    }

    /*
     * For non-array types, verify toString version of the annotated
     * type ends with the same string as the generic type.
     */
    static void testToString(Class<?> clazz, boolean leadingAnnotations) {
        System.err.println("Testing toString on methods of class " + clazz.getName());
        Method[] methods = clazz.getDeclaredMethods();
        for (Method m : methods) {
            AnnotatedType annotType = m.getAnnotatedReturnType();
            String annotTypeString = annotType.toString();

            Type type = m.getGenericReturnType();
            String typeString = type.toString();

            boolean isArray = annotType instanceof AnnotatedArrayType;
            boolean isVoid = "void".equals(typeString);

            boolean valid;
            if (!isArray) {
                if (leadingAnnotations && !isVoid) {
                    valid =
                        annotTypeString.endsWith(typeString) &&
                        !annotTypeString.startsWith(typeString);
                } else {
                    valid = annotTypeString.equals(typeString);
                }
            } else {
                // Find final non-array component type and gets its name.
                typeString = null;

                AnnotatedType componentType = annotType;
                while (componentType instanceof AnnotatedArrayType) {
                    AnnotatedArrayType annotatedArrayType = (AnnotatedArrayType) componentType;
                    componentType = annotatedArrayType.getAnnotatedGenericComponentType();
                }

                String componentName = componentType.getType().getTypeName();
                valid = annotTypeString.contains(componentName);
            }

            if (!valid) {
                errors++;
                System.err.println(typeString + "\n" + annotTypeString +
                                   "\n " + valid  +
                                   "\n\n");
            }
        }
    }

    static void testGetAnnotations(Class<?> clazz, boolean annotationsExpectedOnMethods) {
        System.err.println("Testing getAnnotations on methods of class " + clazz.getName());
        Method[] methods = clazz.getDeclaredMethods();
        for (Method m : methods) {
            Type type = m.getGenericReturnType();
            AnnotatedType annotType = m.getAnnotatedReturnType();
            Annotation[] annotations = annotType.getAnnotations();

            boolean isVoid = "void".equals(type.toString());

            if (annotationsExpectedOnMethods && !isVoid) {
                if (annotations.length == 0 ) {
                    errors++;
                    System.err.println("Expected annotations missing on " + annotType);
                }
            } else {
                if (annotations.length > 0 ) {
                    errors++;
                    System.err.println("Unexpected annotations present on " + annotType);
                }
            }
        }
    }

    static void testEqualsReflexivity(Class<?> clazz) {
        System.err.println("Testing reflexivity of equals on methods of class " + clazz.getName());
        Method[] methods = clazz.getDeclaredMethods();
        for (Method m : methods) {
            checkTypesForEquality(m.getAnnotatedReturnType(),
                                  m.getAnnotatedReturnType(),
                                  true);
        }
    }

    private static void checkTypesForEquality(AnnotatedType annotType1,
                                              AnnotatedType annotType2,
                                              boolean expected) {
        boolean comparison = annotType1.equals(annotType2);

        if (comparison) {
            int hash1 = annotType1.hashCode();
            int hash2 = annotType2.hashCode();
            if (hash1 != hash2) {
                errors++;
                System.err.format("Equal AnnotatedTypes with unequal hash codes: %n%s%n%s%n",
                                  annotType1.toString(), annotType2.toString());
            }
        }

        if (comparison != expected) {
            errors++;
            System.err.println(annotType1);
            System.err.println(expected ? " is not equal to " : " is equal to ");
            System.err.println(annotType2);
            System.err.println();
        }
    }

    /*
     * For each of the type host classes, the return type of a method
     * should only equal the return type of that method.
     */
    static void testEquals(Class<?> clazz) {
        Method[] methods = clazz.getDeclaredMethods();

        for (int i = 0; i < methods.length; i++) {
            for (int j = 0; j < methods.length; j++) {
                if (i == j)
                    continue;
                else {
                    checkTypesForEquality(methods[i].getAnnotatedReturnType(),
                                          methods[j].getAnnotatedReturnType(),
                                          false);
                }
            }
        }
    }

    /**
     * Roughly, compare the return types of corresponding methods on
     * TypeHost and AnnotatedtypeHost and verify the AnnotatedType
     * objects are *not* equal even if their underlying generic types
     * are.
     */
    static void testAnnotationsMatterForEquals(Class<?> clazz1, Class<?> clazz2) {
        System.err.println("Testing that presence/absence of annotations matters for equals comparison.");

        String methodName = null;
        for (Method method :  clazz1.getDeclaredMethods()) {
            if ("void".equals(method.getReturnType().toString())) {
                continue;
            }

            methodName = method.getName();
            try {
                checkTypesForEquality(method.getAnnotatedReturnType(),
                                      clazz2.getDeclaredMethod(methodName).getAnnotatedReturnType(),
                                      false);
            } catch (Exception e) {
                errors++;
                System.err.println("Method " + methodName + " not found.");
            }
        }
    }


    static void testWildcards() {
        System.err.println("Testing wildcards");
        // public @AnnotType(10) Set<? extends Number> fooNumberSet() {return null;}
        // public @AnnotType(11) Set<@AnnotType(13) ? extends Number> fooNumberSet2() {return null;}
        AnnotatedWildcardType awt1 = extractWildcard("fooNumberSet");
        AnnotatedWildcardType awt2 = extractWildcard("fooNumberSet2");

        if (!awt1.equals(extractWildcard("fooNumberSet")) ||
            !awt2.equals(extractWildcard("fooNumberSet2"))) {
            errors++;
            System.err.println("Bad equality comparison on wildcards.");
        }

        checkTypesForEquality(awt1, awt2, false);

        if (awt2.getAnnotations().length == 0) {
            errors++;
            System.err.println("Expected annotations not found.");
        }
    }

    private static AnnotatedWildcardType extractWildcard(String methodName) {
        try {
            return (AnnotatedWildcardType)
                (((AnnotatedParameterizedType)(AnnotatedTypeHost.class.getMethod(methodName).
                                               getAnnotatedReturnType())).
                 getAnnotatedActualTypeArguments()[0] );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // The TypeHost and AnnotatedTypeHost classes declare methods with
    // the same name and signatures but with the AnnotatedTypeHost
    // methods having annotations on their return type, where
    // possible.

    static class TypeHost<E, F extends Number> {
        public void fooVoid() {return;}

        public int foo() {return 0;}
        public String fooString() {return null;}

        public int[] fooIntArray() {return null;}
        public String[] fooStringArray() {return null;}
        public String [][] fooStringArrayArray() {return null;}

        public Set<String> fooSetString() {return null;}
        public E fooE() {return null;}
        public F fooF() {return null;}
        public <G> G fooG() {return null;}

        public  Set<? extends Number> fooNumberSet() {return null;}
        public  Set<? extends Integer> fooNumberSet2() {return null;}
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE_USE)
    static @interface AnnotType {
        int value() default 0;
    }

    static class AnnotatedTypeHost<E, F extends Number> {
        public /*@AnnotType(0)*/ void fooVoid() {return;} // Illegal to annotate void

        public @AnnotType(1) int foo() {return 0;}
        public @AnnotType(2) String fooString() {return null;}

        public  int @AnnotType(3) [] fooIntArray() {return null;}
        public  String @AnnotType(4) [] fooStringArray() {return null;}
        public  @AnnotType(5) String  @AnnotType(0) [] @AnnotType(1) [] fooStringArrayArray() {return null;}

        public @AnnotType(6) Set<String> fooSetString() {return null;}
        public @AnnotType(7) E fooE() {return null;}
        public @AnnotType(8) F fooF() {return null;}
        public @AnnotType(9) <G> G fooG() {return null;}

        public @AnnotType(10) Set<? extends Number> fooNumberSet() {return null;}
        public @AnnotType(11) Set<@AnnotType(13) ? extends Number> fooNumberSet2() {return null;}
    }
}
