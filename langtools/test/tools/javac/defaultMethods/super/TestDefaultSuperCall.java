/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7192246 8006694
 * @summary Automatic test for checking correctness of default super/this resolution
 *  temporarily workaround combo tests are causing time out in several platforms
 * @library ../../lib
 * @build JavacTestingAbstractThreadedTest
 * @run main/othervm TestDefaultSuperCall
 */

// use /othervm to avoid jtreg timeout issues (CODETOOLS-7900047)
// see JDK-8006746

import java.net.URI;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;

import com.sun.source.util.JavacTask;

public class TestDefaultSuperCall
    extends JavacTestingAbstractThreadedTest
    implements Runnable {

    enum InterfaceKind {
        DEFAULT("interface A extends B { default void m() { } }"),
        ABSTRACT("interface A extends B { void m(); }"),
        NONE("interface A extends B { }");

        String interfaceStr;

        InterfaceKind(String interfaceStr) {
            this.interfaceStr = interfaceStr;
        }

        boolean methodDefined() {
            return this == DEFAULT;
        }
    }

    enum PruneKind {
        NO_PRUNE("interface C { }"),
        PRUNE("interface C extends A { }");

        boolean methodDefined(InterfaceKind ik) {
            return this == PRUNE &&
                    ik.methodDefined();
        }

        String interfaceStr;

        PruneKind(String interfaceStr) {
            this.interfaceStr = interfaceStr;
        }
    }

    enum QualifierKind {
        DIRECT_1("C"),
        DIRECT_2("A"),
        INDIRECT("B"),
        UNRELATED("E"),
        ENCLOSING_1(null),
        ENCLOSING_2(null);

        String qualifierStr;

        QualifierKind(String qualifierStr) {
            this.qualifierStr = qualifierStr;
        }

        String getQualifier(Shape sh) {
            switch (this) {
                case ENCLOSING_1: return sh.enclosingAt(0);
                case ENCLOSING_2: return sh.enclosingAt(1);
                default:
                    return qualifierStr;
            }
        }

        boolean isEnclosing() {
            return this == ENCLOSING_1 ||
                    this == ENCLOSING_2;
        }

        boolean allowSuperCall(InterfaceKind ik, PruneKind pk) {
            switch (this) {
                case DIRECT_1:
                    return pk.methodDefined(ik);
                case DIRECT_2:
                    return ik.methodDefined() && pk == PruneKind.NO_PRUNE;
                default:
                    return false;
            }
        }
    }

    enum ExprKind {
        THIS("this"),
        SUPER("super");

        String exprStr;

        ExprKind(String exprStr) {
            this.exprStr = exprStr;
        }
    }

    enum ElementKind {
        INTERFACE("interface #N { #B }", true),
        INTERFACE_EXTENDS("interface #N extends A, C { #B }", true),
        CLASS("class #N { #B }", false),
        CLASS_EXTENDS("abstract class #N implements A, C { #B }", false),
        STATIC_CLASS("static class #N { #B }", true),
        STATIC_CLASS_EXTENDS("abstract static class #N implements A, C { #B }", true),
        ANON_CLASS("new Object() { #B };", false),
        METHOD("void test() { #B }", false),
        STATIC_METHOD("static void test() { #B }", true),
        DEFAULT_METHOD("default void test() { #B }", false);

        String templateDecl;
        boolean isStatic;

        ElementKind(String templateDecl, boolean isStatic) {
            this.templateDecl = templateDecl;
            this.isStatic = isStatic;
        }

        boolean isClassDecl() {
            switch(this) {
                case METHOD:
                case STATIC_METHOD:
                case DEFAULT_METHOD:
                    return false;
                default:
                    return true;
            }
        }

        boolean isAllowedEnclosing(ElementKind ek, boolean isTop) {
            switch (this) {
                case CLASS:
                case CLASS_EXTENDS:
                    //class is implicitly static inside interface, so skip this combo
                    return ek.isClassDecl() &&
                            ek != INTERFACE && ek != INTERFACE_EXTENDS;
                case ANON_CLASS:
                    return !ek.isClassDecl();
                case METHOD:
                    return ek == CLASS || ek == CLASS_EXTENDS ||
                            ek == STATIC_CLASS || ek == STATIC_CLASS_EXTENDS ||
                            ek == ANON_CLASS;
                case INTERFACE:
                case INTERFACE_EXTENDS:
                case STATIC_CLASS:
                case STATIC_CLASS_EXTENDS:
                case STATIC_METHOD:
                    return (isTop && (ek == CLASS || ek == CLASS_EXTENDS)) ||
                            ek == STATIC_CLASS || ek == STATIC_CLASS_EXTENDS;
                case DEFAULT_METHOD:
                    return ek == INTERFACE || ek == INTERFACE_EXTENDS;
                default:
                    throw new AssertionError("Bad enclosing element kind" + this);
            }
        }

        boolean isAllowedTop() {
            switch (this) {
                case CLASS:
                case CLASS_EXTENDS:
                case INTERFACE:
                case INTERFACE_EXTENDS:
                    return true;
                default:
                    return false;
            }
        }

        boolean hasSuper() {
            return this == INTERFACE_EXTENDS ||
                    this == STATIC_CLASS_EXTENDS ||
                    this == CLASS_EXTENDS;
        }
    }

    static class Shape {

        String shapeStr;
        List<ElementKind> enclosingElements;
        List<String> enclosingNames;
        List<String> elementsWithMethod;

        Shape(ElementKind... elements) {
            enclosingElements = new ArrayList<>();
            enclosingNames = new ArrayList<>();
            elementsWithMethod = new ArrayList<>();
            int count = 0;
            String prevName = null;
            for (ElementKind ek : elements) {
                String name = "name"+count++;
                if (ek.isStatic) {
                    enclosingElements = new ArrayList<>();
                    enclosingNames = new ArrayList<>();
                }
                if (ek.isClassDecl()) {
                    enclosingElements.add(ek);
                    enclosingNames.add(name);
                } else {
                    elementsWithMethod.add(prevName);
                }
                String element = ek.templateDecl.replaceAll("#N", name);
                shapeStr = shapeStr ==
                        null ? element : shapeStr.replaceAll("#B", element);
                prevName = name;
            }
        }

        String getShape(QualifierKind qk, ExprKind ek) {
            String methName = ek == ExprKind.THIS ? "test" : "m";
            String call = qk.getQualifier(this) + "." +
                    ek.exprStr + "." + methName + "();";
            return shapeStr.replaceAll("#B", call);
        }

        String enclosingAt(int index) {
            return index < enclosingNames.size() ?
                    enclosingNames.get(index) : "BAD";
        }
    }

    public static void main(String... args) throws Exception {
        for (InterfaceKind ik : InterfaceKind.values()) {
            for (PruneKind pk : PruneKind.values()) {
                for (ElementKind ek1 : ElementKind.values()) {
                    if (!ek1.isAllowedTop()) continue;
                    for (ElementKind ek2 : ElementKind.values()) {
                        if (!ek2.isAllowedEnclosing(ek1, true)) continue;
                        for (ElementKind ek3 : ElementKind.values()) {
                            if (!ek3.isAllowedEnclosing(ek2, false)) continue;
                            for (ElementKind ek4 : ElementKind.values()) {
                                if (!ek4.isAllowedEnclosing(ek3, false)) continue;
                                for (ElementKind ek5 : ElementKind.values()) {
                                    if (!ek5.isAllowedEnclosing(ek4, false) ||
                                            ek5.isClassDecl()) continue;
                                    for (QualifierKind qk : QualifierKind.values()) {
                                        for (ExprKind ek : ExprKind.values()) {
                                            pool.execute(
                                                    new TestDefaultSuperCall(ik, pk,
                                                    new Shape(ek1, ek2, ek3,
                                                    ek4, ek5), qk, ek));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        checkAfterExec();
    }

    InterfaceKind ik;
    PruneKind pk;
    Shape sh;
    QualifierKind qk;
    ExprKind ek;
    JavaSource source;
    DiagnosticChecker diagChecker;

    TestDefaultSuperCall(InterfaceKind ik, PruneKind pk, Shape sh,
            QualifierKind qk, ExprKind ek) {
        this.ik = ik;
        this.pk = pk;
        this.sh = sh;
        this.qk = qk;
        this.ek = ek;
        this.source = new JavaSource();
        this.diagChecker = new DiagnosticChecker();
    }

    class JavaSource extends SimpleJavaFileObject {

        String template = "interface E {}\n" +
                          "interface B { }\n" +
                          "#I\n" +
                          "#P\n" +
                          "#C";

        String source;

        public JavaSource() {
            super(URI.create("myfo:/Test.java"), JavaFileObject.Kind.SOURCE);
            source = template.replaceAll("#I", ik.interfaceStr)
                    .replaceAll("#P", pk.interfaceStr)
                    .replaceAll("#C", sh.getShape(qk, ek));
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }

    public void run() {
        JavacTask ct = (JavacTask)comp.getTask(null, fm.get(), diagChecker,
                null, null, Arrays.asList(source));
        try {
            ct.analyze();
        } catch (Throwable ex) {
            processException(ex);
            return;
        }
        check();
    }

    void check() {
        boolean errorExpected = false;

        boolean badEnclosing = false;
        boolean badThis = false;
        boolean badSuper = false;

        if (qk == QualifierKind.ENCLOSING_1 &&
                sh.enclosingNames.size() < 1) {
            errorExpected |= true;
            badEnclosing = true;
        }

        if (qk == QualifierKind.ENCLOSING_2 &&
                sh.enclosingNames.size() < 2) {
            errorExpected |= true;
            badEnclosing = true;
        }

        if (ek == ExprKind.THIS) {
            boolean found = false;
            for (int i = 0; i < sh.enclosingElements.size(); i++) {
                if (sh.enclosingElements.get(i) == ElementKind.ANON_CLASS) continue;
                if (sh.enclosingNames.get(i).equals(qk.getQualifier(sh))) {
                    found = sh.elementsWithMethod.contains(sh.enclosingNames.get(i));
                    break;
                }
            }
            errorExpected |= !found;
            if (!found) {
                badThis = true;
            }
        }

        if (ek == ExprKind.SUPER) {

            int lastIdx = sh.enclosingElements.size() - 1;
            boolean found = lastIdx == -1 ? false :
                    sh.enclosingElements.get(lastIdx).hasSuper() &&
                    qk.allowSuperCall(ik, pk);

            errorExpected |= !found;
            if (!found) {
                badSuper = true;
            }
        }

        checkCount.incrementAndGet();
        if (diagChecker.errorFound != errorExpected) {
            throw new AssertionError("Problem when compiling source:\n" +
                    source.getCharContent(true) +
                    "\nenclosingElems: " + sh.enclosingElements +
                    "\nenclosingNames: " + sh.enclosingNames +
                    "\nelementsWithMethod: " + sh.elementsWithMethod +
                    "\nbad encl: " + badEnclosing +
                    "\nbad this: " + badThis +
                    "\nbad super: " + badSuper +
                    "\nqual kind: " + qk +
                    "\nfound error: " + diagChecker.errorFound);
        }
    }

    static class DiagnosticChecker
        implements javax.tools.DiagnosticListener<JavaFileObject> {

        boolean errorFound;

        public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                errorFound = true;
            }
        }
    }

}
