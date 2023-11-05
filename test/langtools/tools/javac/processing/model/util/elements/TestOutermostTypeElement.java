/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8140442
 * @summary Test Elements.getOutermostTypeElement
 * @library /tools/javac/lib
 * @build   JavacTestingAbstractProcessor TestOutermostTypeElement
 * @compile -processor TestOutermostTypeElement -proc:only TestOutermostTypeElement.java
 */

import java.io.Writer;
import java.util.*;
import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.lang.model.util.*;

/**
 * Test basic workings of Elements.getOutermostTypeElement
 */
public class TestOutermostTypeElement extends JavacTestingAbstractProcessor {
    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnv) {
        if (!roundEnv.processingOver()) {
            Elements vacuousElts = new VacuousElements();

            ModuleElement javaBaseMod = eltUtils.getModuleElement("java.base");
            checkOuter(javaBaseMod, null, vacuousElts);
            checkOuter(javaBaseMod, null, eltUtils);

            PackageElement javaLangPkg = eltUtils.getPackageElement("java.lang");
            checkOuter(javaLangPkg, null, vacuousElts);
            checkOuter(javaLangPkg, null, eltUtils);

            // Starting from the root elements, traverse over all
            // enclosed elements and type parameters. The outermost
            // enclosing type element should equal the root
            // element. This traversal does *not* hit elements
            // corresponding to structures inside of a method.
            for (TypeElement e : ElementFilter.typesIn(roundEnv.getRootElements()) ) {
                var outerScaner = new OuterScanner(e);
                outerScaner.scan(e, vacuousElts);
                outerScaner.scan(e, eltUtils);
             }
        }
        return true;
    }

    private class OuterScanner extends ElementScanner<Void, Elements> {
        private TypeElement expectedOuter;
        public OuterScanner(TypeElement expectedOuter) {
            this.expectedOuter = expectedOuter;
        }

        @Override
        public Void scan(Element e, Elements elts) {
            checkOuter(e, expectedOuter, elts);
            super.scan(e, elts);
            return null;
        }
    }

    private void checkOuter(Element e, TypeElement expectedOuter, Elements elts) {
        var actualOuter = elts.getOutermostTypeElement(e);
        if (!Objects.equals(actualOuter, expectedOuter)) {
            throw new RuntimeException(String.format("Unexpected outermost ``%s''' for %s, expected ``%s.''%n",
                                                     actualOuter,
                                                     e,
                                                     expectedOuter));
        }
    }
}

/**
 * Outer class to host a variety of kinds of inner elements with Outer
 * as their outermost class.
 */
class Outer {
    private Outer() {}

    public enum InnerEnum {
        VALUE1,
        VALUE2;

        private int field;
    }

    public static class InnerClass {
        private static int field;
        static {
            field = 5;
        }

        public <C> InnerClass(C c) {}

        void foo() {return;}
        static void bar() {return;}
        static <R> R baz(Class<? extends R> clazz) {return null;}

        private class InnerInnerClass {
            public InnerInnerClass() {}
        }
    }

    public interface InnerInterface {
        final int field = 42;
        void foo();
    }

    public @interface InnerAnnotation {
        int value() default 1;
    }

    public record InnerRecord(double rpm, double diameter) {
        void foo() {return;}
    }
}
