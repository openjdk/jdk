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
 * @bug     1234567
 * @summary Annotations on types
 * @library /tools/javac/lib
 * @ignore
 * @build JavacTestingAbstractProcessor DPrinter BasicAnnoTests
 * @compile/process -processor BasicAnnoTests -proc:only BasicAnnoTests.java
 */

import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.AnnotatedConstruct;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.tools.Diagnostic.Kind;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;

/**
 * The test scans this file looking for test cases annotated with @Test.
 */
public class BasicAnnoTests extends JavacTestingAbstractProcessor {
    DPrinter dprinter;
    PrintWriter out;
    boolean verbose = true;

    @Override
    public void init(ProcessingEnvironment pEnv) {
        super.init(pEnv);
        dprinter = new DPrinter(((JavacProcessingEnvironment) pEnv).getContext());
        out = dprinter.out;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        TestElementScanner s = new TestElementScanner();
        for (Element e: roundEnv.getRootElements()) {
            s.scan(e);
        }
        return true;
    }

    void error(Element e, String msg) {
        messager.printMessage(Kind.ERROR, msg, e);
        errors++;
    }

    int errors;

    /**
     * Scan an element looking for declarations annotated with @Test.
     * Run a TestTypeScanner on the annotations that are found.
     */
    class TestElementScanner extends ElementScanner<Void,Void> {
        public Void scan(Element elem, Void ignore) {
            AnnotationMirror test = getAnnotation(elem, Test.class.getName().replace('$', '.'));
            if (test != null) {
                out.println("Test: " + elem + " " + test);
                TestTypeScanner s = new TestTypeScanner(elem, test);
                s.scan(elem.asType(), null);
                if (getPosn(test) >= s.count)
                    error(elem, "position " + getPosn(test) + " not found");
                if (!s.found) {
                    dprinter.printSymbol("element", (Symbol) elem);
                    dprinter.printType("type", (Type) elem.asType());
                }
                out.println();
            }
            return super.scan(elem, ignore);
        }
    }

    /**
     * Scan the type of an element, looking for an annotation
     * to match the expected annotation specified in the @Test annotation.
     */
    class TestTypeScanner extends TypeScanner<Void, Void> {
        Element elem;
        AnnotationMirror test;
        int count = 0;
        boolean found = false;

        TestTypeScanner(Element elem, AnnotationMirror test) {
            this.elem = elem;
            this.test = test;
        }

        @Override
        Void scan(TypeMirror t, Void ignore) {
            if (t == null)
                return DEFAULT_VALUE;
            if (verbose)
                out.println("scan " + count + ": " + t);
            if (count == getPosn(test)) {
                String annoType = getAnnoType(test);
                AnnotationMirror anno = getAnnotation(t, annoType);
                if (anno == null) {
                    error(elem, "annotation not found on " + count + ": " + t);
                } else {
                    String v = getValue(anno, "value").toString();
                    if (v.equals(getExpect(test))) {
                        out.println("found " + anno + " as expected");
                        found = true;
                    } else {
                        error(elem, "Unexpected value: " + v + ", expected: " + getExpect(test));
                    }
                }
            }
            count++;
            return super.scan(t, ignore);
        }
    }

    /** Get the position value from an @Test annotation mirror. */
    static int getPosn(AnnotationMirror test) {
        AnnotationValue v = getValue(test, "posn");
        return (Integer) v.getValue();
    }

    /** Get the expect value from an @Test annotation mirror. */
    static String getExpect(AnnotationMirror test) {
        AnnotationValue v = getValue(test, "expect");
        return (String) v.getValue();
    }

    /** Get the annoType value from an @Test annotation mirror. */
    static String getAnnoType(AnnotationMirror test) {
        AnnotationValue v = getValue(test, "annoType");
        TypeMirror m = (TypeMirror) v.getValue();
        return m.toString();
    }

    /**
     * Get a specific annotation mirror from an annotated construct.
     */
    static AnnotationMirror getAnnotation(AnnotatedConstruct e, String name) {
        for (AnnotationMirror m: e.getAnnotationMirrors()) {
            TypeElement te = (TypeElement) m.getAnnotationType().asElement();
            if (te.getQualifiedName().contentEquals(name)) {
                return m;
            }
        }
        return null;
    }

    /**
     * Get a specific value from an annotation mirror.
     */
    static AnnotationValue getValue(AnnotationMirror anno, String name) {
        Map<? extends ExecutableElement, ? extends AnnotationValue> map = anno.getElementValues();
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> e: map.entrySet()) {
            if (e.getKey().getSimpleName().contentEquals(name)) {
                return e.getValue();
            }
        }
        return null;
    }

    /**
     * The Language Model API does not provide a type scanner, so provide
     * one sufficient for our needs.
     */
    static class TypeScanner<R, P> extends SimpleTypeVisitor<R, P> {
        @Override
        public R visitArray(ArrayType t, P p) {
            scan(t.getComponentType(), p);
            return super.visitArray(t, p);
        }

        @Override
        public R visitExecutable(ExecutableType t, P p) {
            scan(t.getReceiverType());
            //out.println("  params: " + t.getParameterTypes());
            scan(t.getParameterTypes(), p);
            //out.println("  return: " + t.getReturnType());
            scan(t.getReturnType(), p);
            //out.println("  throws: " + t.getThrownTypes());
            scan(t.getThrownTypes(), p);
            return super.visitExecutable(t, p);
        }

        @Override
        public R visitTypeVariable(TypeVariable t, P p) {
            scan(t.getLowerBound(), p);
            scan(t.getUpperBound(), p);
            return super.visitTypeVariable(t, p);
        }

        @Override
        public R visitWildcard(WildcardType t, P p) {
            scan(t.getExtendsBound(), p);
            scan(t.getSuperBound(), p);
            return super.visitWildcard(t, p);
        }

        R scan(TypeMirror t) {
            return scan(t, null);
        }

        R scan(TypeMirror t, P p) {
            return (t == null) ? DEFAULT_VALUE : t.accept(this, p);
        }

        R scan(Iterable<? extends TypeMirror> iter, P p) {
            if (iter == null)
                return DEFAULT_VALUE;
            R result = DEFAULT_VALUE;
            for (TypeMirror t: iter)
                result = scan(t, p);
            return result;
        }
    }

    /** Annotation to identify test cases. */
    @interface Test {
        /** Where to look for the annotation, expressed as a scan index. */
        int posn();
        /** The annotation to look for. */
        Class<? extends Annotation> annoType();
        /** The string representation of the annotation's value. */
        String expect();
    }

    /** Type annotation to use in test cases. */
    @Target(ElementType.TYPE_USE)
    public @interface TA {
        int value();
    }

    @Test(posn=0, annoType=TA.class, expect="1")
    public @TA(1) int f1;

    @Test(posn=0, annoType=TA.class, expect="2")
    public int @TA(2) [] f2;

    @Test(posn=1, annoType=TA.class, expect="3")
    public @TA(3) int [] f3;

    @Test(posn=1, annoType=TA.class, expect="4")
    public int m1(@TA(4) float a) throws Exception { return 0; }

    @Test(posn=2, annoType=TA.class, expect="5")
    public @TA(5) int m2(float a) throws Exception { return 0; }

    @Test(posn=3, annoType=TA.class, expect="6")
    public int m3(float a) throws @TA(6) Exception { return 0; }
}
