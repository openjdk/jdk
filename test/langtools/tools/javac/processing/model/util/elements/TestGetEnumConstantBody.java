/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8312418
 * @summary Test Elements.getEnumConstantBody
 * @library /tools/javac/lib
 * @build   JavacTestingAbstractProcessor TestGetEnumConstantBody
 * @compile -processor TestGetEnumConstantBody -XDshould.stop-at=FLOW TestGetEnumConstantBody.java
 */

import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.function.*;
import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.lang.model.util.*;
import javax.lang.model.type.*;

/**
 * Test basic workings of Elements.getEnumConstantBody
 */
public class TestGetEnumConstantBody extends JavacTestingAbstractProcessor {
    private Elements vacuousElements = new VacuousElements();
    private Set<Element> allElements = new HashSet<>();
    private int round;

    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnv) {

        allElements.addAll(roundEnv.getRootElements());

        // In the innermost loop, examine the fields defined by the the nested classes
        for (TypeElement typeRoot : ElementFilter.typesIn(allElements) ) {
            if (typeRoot.getQualifiedName().contentEquals("Gen")) {
                continue;
            }

            boolean elementSeen = false;

            for (TypeElement typeElt : ElementFilter.typesIn(typeRoot.getEnclosedElements()) ) {
                System.out.println("Testing type " + typeElt);

                for (VariableElement field : ElementFilter.fieldsIn(typeElt.getEnclosedElements()) ) {
                    elementSeen = true;
                    System.out.println(field);
                    switch (field.getKind()) {
                    case FIELD         -> expectIAE(field);
                    case ENUM_CONSTANT -> testEnumConstant(field, typeElt);
                    default            -> throw new RuntimeException("Unexpected field kind seen");
                    }
                }
            }

            if (!elementSeen) {
                throw new RuntimeException("No elements seen.");
            }
        }
        switch (round++) {
            case 0:
                try (Writer w = processingEnv.getFiler().createSourceFile("Cleaned").openWriter()) {
                    w.write("""
                            class Enclosing {
                                enum Cleaned {
                                    @TestGetEnumConstantBody.ExpectedBinaryName("Enclosing$Cleaned$2")
                                    A(new Object() {}) {
                                        void test(Gen g) {
                                            g.run();
                                        }
                                    },
                                    B,
                                    @TestGetEnumConstantBody.ExpectedBinaryName("Enclosing$Cleaned$4")
                                    C(new Object() {}) {
                                    };

                                    private Cleaned() {}

                                    private Cleaned(Object o) {}
                                }
                            }
                            """);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
                break;
            case 1:
                try (Writer w = processingEnv.getFiler().createSourceFile("Gen").openWriter()) {
                    w.write("""
                            public class Gen {
                                public void run() {}
                            }
                            """);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
                break;
        }
        return true;
    }

    private String computeExpectedBinaryName(VariableElement e) {
        ExpectedBinaryName ebn = e.getAnnotation(ExpectedBinaryName.class);
        return (ebn == null) ? null : ebn.value();
    }

    private void expectIAE(VariableElement variable) {
        expectException0(() ->  elements.getEnumConstantBody(variable),
                         "Expected exception not thrown");

        expectException0(() ->  vacuousElements.getEnumConstantBody(variable),
                         "Expected vacuous exception not thrown");
    }

    private void expectException0(Supplier<TypeElement> supplier, String message) {
        try {
            var typeElement = supplier.get();
            messager.printError(message, typeElement);
        } catch (IllegalArgumentException iae) {
            ; // Expected
        }
    }

    void expectUOE(VariableElement field) {
        try {
            var result = vacuousElements.getEnumConstantBody(field);
            messager.printError("Unexpected non-exceptional result returned", field);

        } catch(UnsupportedOperationException uoe) {
            ; // Expected
        }
    }

    private void testEnumConstant(VariableElement field,
                                  TypeElement enclosingClass) {
        String expectedBinaryName = computeExpectedBinaryName(field);
        boolean expectEnumConstantBody = expectedBinaryName != null;

        System.out.println("\tTesting enum constant " + field + " expected " + expectEnumConstantBody);
        expectUOE(field);

        TypeElement enumConstantBody = elements.getEnumConstantBody(field);

        if (Objects.nonNull(enumConstantBody) != expectEnumConstantBody) {
            messager.printError("Unexpected body value", field);
        }

        if (enumConstantBody != null) {
            testEnumConstantBody(enumConstantBody, expectedBinaryName, enclosingClass);
        }

        System.out.println("\t constant body " + enumConstantBody);
    }

    /*
     * From JLS 8.9.1:
     *
     * "The optional class body of an enum constant implicitly
     * declares an anonymous class (15.9.5) that (i) is a direct
     * subclass of the immediately enclosing enum class (8.1.4), and
     * (ii) is final (8.1.1.2). The class body is governed by the
     * usual rules of anonymous classes; in particular it cannot
     * contain any constructors. Instance methods declared in these
     * class bodies may be invoked outside the enclosing enum class
     * only if they override accessible methods in the enclosing enum
     * class (8.4.8)."
     */
    private void testEnumConstantBody(TypeElement enumConstBody, String expectedBinaryName, TypeElement enumClass) {
        if (enumConstBody.getNestingKind() != NestingKind.ANONYMOUS) {
            messager.printError("Class body not an anonymous class", enumConstBody);
        }

        // Get the TypeElement for the direct superclass.
        TypeElement superClass =
            (TypeElement)(((DeclaredType)enumConstBody.getSuperclass()).asElement());

        if (!superClass.equals(enumClass)) {
            messager.printError("Class body is not a direct subclass of the enum", enumConstBody);
        }

        if (!enumConstBody.getModifiers().contains(Modifier.FINAL)) {
            messager.printError("Modifier final missing on class body", enumConstBody);
        }

        if (!elements.getBinaryName(enumConstBody).contentEquals(expectedBinaryName)) {
            messager.printError("Unexpected binary name, expected: " + expectedBinaryName +
                                                       ", but was: " + elements.getBinaryName(enumConstBody), enumConstBody);
        }

        return;
    }


    @interface ExpectedBinaryName {
        String value();
    }

    // Nested classes hosting a variety of different kinds of fields.

    private static enum Body {
        @ExpectedBinaryName("TestGetEnumConstantBody$Body$1")
        GOLGI(true) {
            public boolean isOrganelle() {return true;}
        },

        @ExpectedBinaryName("TestGetEnumConstantBody$Body$2")
        HEAVENLY(true) {
            public boolean isCelestial() {return true;}
        };

        private Body(boolean predicate) {
            this.predicate = predicate;
        }

        private boolean predicate;

        public static int field = 42;

        public void method() {return;}
    }

    private static enum MetaSyntaxVar {
        FOO("foo"),
        BAR("bar");

        private String lower;
        private MetaSyntaxVar(String lower) {
            this.lower = lower;
        }

        int   BAZ  = 0;
        float QUUX = 0.1f;
    }

    // Instance and static fields.
    public static class FieldHolder {
        public static final int f1 = 1;
        public static final String s = "s";

        private Object data;
        public FieldHolder(Object data) {
            this.data = data;
        }
    }
}
