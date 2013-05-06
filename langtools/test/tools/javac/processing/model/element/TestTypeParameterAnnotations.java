/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8011027
 * @library /tools/javac/lib
 * @build JavacTestingAbstractProcessor TestTypeParameterAnnotations
 * @compile -processor TestTypeParameterAnnotations -proc:only TestTypeParameterAnnotations.java
 */

import java.util.*;
import java.lang.annotation.*;
import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.lang.model.util.*;
import javax.tools.*;

public class TestTypeParameterAnnotations<@Foo @Bar @Baz T> extends JavacTestingAbstractProcessor {
    int round = 0;

    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (++round == 1) {
            int found = (new Scanner()).scan(roundEnv.getRootElements(), null);
            if (found == expect) {
                ; //nop
            } else {
                error("unexpected number of results: expected " + expect
                        + ", found " + found);
            }

        }
        return true;
    }

    class Scanner extends JavacTestingAbstractProcessor.ElementScanner<Integer,Void> {
        @Override
        public Integer visitExecutable(ExecutableElement e, Void p) {
            super.visitExecutable(e, p);
            found += check(e, e.getTypeParameters());
            return found;
        }

        @Override
        public Integer visitType(TypeElement e, Void p) {
            super.visitType(e, p);
            found += check(e, e.getTypeParameters());
            return found;
        }

        int found;
    }

    int check(Element e, List<? extends TypeParameterElement> typarams) {
        if (typarams.isEmpty())
            return 0;
        if (typarams.size() != 1)
            return 0;

        for (TypeParameterElement tpe: typarams) {
            boolean b1 = checkAnnotationMirrors(tpe, tpe.getAnnotationMirrors());
            boolean b2 = checkAnnotationMirrors(tpe, elements.getAllAnnotationMirrors(tpe));
            boolean b3 = checkGetAnnotation(tpe);
            boolean b4 = checkGetAnnotations(tpe);
            return b1 && b2 && b3 && b4 ? 1 : 0;
        }
        return 0;
    }

    boolean checkAnnotationMirrors(TypeParameterElement tpe, List<? extends AnnotationMirror> l) {
        if (l.size() != 3) {
            error("To few annotations, got " + l.size() +
                    ", should be 3", tpe);
            return false;
        }

        AnnotationMirror m = l.get(0);
        if (!m.getAnnotationType().asElement().equals(elements.getTypeElement("Foo"))) {
            error("Wrong type of annotation, was expecting @Foo", m.getAnnotationType().asElement());
            return false;
        }
        m = l.get(1);
        if (!m.getAnnotationType().asElement().equals(elements.getTypeElement("Bar"))) {
            error("Wrong type of annotation, was expecting @Bar", m.getAnnotationType().asElement());
            return false;
        }
        m = l.get(2);
        if (!m.getAnnotationType().asElement().equals(elements.getTypeElement("Baz"))) {
            error("Wrong type of annotation, was expecting @Baz", m.getAnnotationType().asElement());
            return false;
        }
        return true;
    }

    boolean checkGetAnnotation(TypeParameterElement tpe) {
        Foo f = tpe.getAnnotation(Foo.class);
        if (f == null)
            error("Expecting @Foo to be present in getAnnotation()", tpe);

        Bar b = tpe.getAnnotation(Bar.class);
        if (b == null)
            error("Expecting @Bar to be present in getAnnotation()", tpe);

        Baz z = tpe.getAnnotation(Baz.class);
        if (z == null)
            error("Expecting @Baz to be present in getAnnotation()", tpe);

        return f != null &&
            b != null &&
            z != null;
    }

    boolean checkGetAnnotations(TypeParameterElement tpe) {
        Foo[] f = tpe.getAnnotationsByType(Foo.class);
        if (f.length != 1) {
            error("Expecting 1 @Foo to be present in getAnnotationsByType()", tpe);
            return false;
        }

        Bar[] b = tpe.getAnnotationsByType(Bar.class);
        if (b.length != 1) {
            error("Expecting 1 @Bar to be present in getAnnotationsByType()", tpe);
            return false;
        }

        Baz[] z = tpe.getAnnotationsByType(Baz.class);
        if (z.length != 1) {
            error("Expecting 1 @Baz to be present in getAnnotationsByType()", tpe);
            return false;
        }

        return true;
    }

    void note(String msg) {
        messager.printMessage(Diagnostic.Kind.NOTE, msg);
    }

    void note(String msg, Element e) {
        messager.printMessage(Diagnostic.Kind.NOTE, msg, e);
    }

    void error(String msg, Element e) {
        messager.printMessage(Diagnostic.Kind.ERROR, msg, e);
    }

    void error(String msg) {
        messager.printMessage(Diagnostic.Kind.ERROR, msg);
    }

    // additional generic elements to test
    <@Foo @Bar @Baz X> X m(X x) { return x; }

    interface Intf<@Foo @Bar @Baz X> { X m() ; }

    class Class<@Foo @Bar @Baz X> {
        <@Foo @Bar @Baz Y> Class() { }
    }

    final int expect = 5;  // top level class, plus preceding examples
}

@Target(ElementType.TYPE_PARAMETER)
@interface Foo {}

@Target(ElementType.TYPE_PARAMETER)
@interface Bar {}

@Target(ElementType.TYPE_PARAMETER)
@interface Baz {}
