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
 * @bug 8289249
 * @summary Test Elements.{isCompactConstructor, isCanonicalConstructor}
 * @library /tools/javac/lib
 * @build   JavacTestingAbstractProcessor TestRecordPredicates
 * @compile -processor TestRecordPredicates -proc:only TestRecordPredicates.java
 */

import java.lang.annotation.*;
import java.util.*;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import static javax.lang.model.SourceVersion.*;
import javax.lang.model.element.*;
import javax.lang.model.util.*;

/**
 * Test Elements.{isCompactConstructor, isCanonicalConstructor}.
 */
public class TestRecordPredicates extends JavacTestingAbstractProcessor {
    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnv) {
        if (!roundEnv.processingOver()) {
            // Test null handling
            Elements vacuousElements = new VacuousElements();
            expectFalse( () -> vacuousElements.isCompactConstructor(null));
            expectFalse( () -> vacuousElements.isCanonicalConstructor(null));

            expectNpe( () -> elements.isCompactConstructor(null));
            expectNpe( () -> elements.isCanonicalConstructor(null));

            for (var typeElt :
                     ElementFilter.typesIn(roundEnv.getElementsAnnotatedWith(ExpectedPredicates.class))) {
                ExpectedPredicates ep = typeElt.getAnnotation(ExpectedPredicates.class);
                for (ExecutableElement ctor : ElementFilter.constructorsIn(typeElt.getEnclosedElements())) {
                    boolean isCompact = elements.isCompactConstructor(ctor);
                    if (isCompact != ep.isCompact()) {
                        messager.printError("Unexpected isCompact value on ", ctor);
                    }

                    boolean isCanonical = elements.isCanonicalConstructor(ctor);
                    if (isCanonical != ep.isCanonical()) {
                        messager.printError("Unexpected isCanonical value on ", ctor);
                    }

                    if (isCompact && !isCanonical) {
                        messager.printError("Compact constructors not reported as canonical ", ctor);
                    }
                }
            }
        }
        return true;
    }

    private void expectNpe(java.util.function.BooleanSupplier bs) {
        try {
            bs.getAsBoolean();
            messager.printError("Did not get expected NPE");
        } catch (NullPointerException npe) {
                ; // Expected
        }
    }

    private void expectFalse(java.util.function.BooleanSupplier bs) {
        try {
            boolean result = bs.getAsBoolean();
            if (result) {
                messager.printError("Unexpected true result");
            }
        } catch (NullPointerException npe) {
            messager.printError("Unexpected NPE thrown");
        }
    }


    /**
     * Annotation the class, not just constructor, since many of the
     * constructors that are of interest are implicitly declared.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @interface ExpectedPredicates {
        boolean isCompact()   default false;
        boolean isCanonical() default false;
    }

    @ExpectedPredicates(isCompact=true, isCanonical=true)
    record RecordCompactCtor(int foo, double bar) {}

    // Example from JLS 8.10.4.2
    @ExpectedPredicates(isCompact=true, isCanonical=true)
    record Rational(int num, int denom) {
        private static int gcd(int a, int b) {
            if (b == 0) return Math.abs(a);
            else return gcd(b, a % b);
        }

        // Compact ctor
        Rational {
            int gcd = gcd(num, denom);
            num    /= gcd;
            denom  /= gcd;
        }
    }

    // Example from JLS 8.10.4.2
    @ExpectedPredicates(isCanonical=true)
    record RationalAlt(int num, int denom) {
        private static int gcd(int a, int b) {
            if (b == 0) return Math.abs(a);
            else return gcd(b, a % b);
        }

        // Non-compact ctor
        RationalAlt(int num, int denom) {
            int gcd = gcd(num, denom);
            num    /= gcd;
            denom  /= gcd;
            this.num   = num;
            this.denom = denom;
        }
    }

    // Only constructors on records can be compact or canonical.
    @ExpectedPredicates
    enum MetaSyntax {
        FOO,
        BAR;
    }

    @ExpectedPredicates
    class NestedClass {
        // A default constructor is neither compact nor canonical.
    }

    @ExpectedPredicates
    static class StaticNestedClass {
        // A default constructor is neither compact nor canonical.
    }

    @ExpectedPredicates
    static class AnotherNestedClass {
        // Non-default constructor
        public AnotherNestedClass() {}
    }
}
