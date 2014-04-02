/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8005166
 * @summary Add support for static interface methods
 *          Smoke test for static interface method hiding
 * @run main/timeout=600 InterfaceMethodHidingTest
 */

import com.sun.source.util.JavacTask;
import java.net.URI;
import java.util.Arrays;
import javax.tools.Diagnostic;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;


public class InterfaceMethodHidingTest {

    static int checkCount = 0;

    enum SignatureKind {
        VOID_INTEGER("void m(Integer s)", "return;"),
        STRING_INTEGER("String m(Integer s)", "return null;"),
        VOID_STRING("void m(String s)", "return;"),
        STRING_STRING("String m(String s)", "return null;");

        String sigStr;
        String retStr;

        SignatureKind(String sigStr, String retStr) {
            this.sigStr = sigStr;
            this.retStr = retStr;
        }

        boolean overrideEquivalentWith(SignatureKind s2) {
            switch (this) {
                case VOID_INTEGER:
                case STRING_INTEGER:
                    return s2 == VOID_INTEGER || s2 == STRING_INTEGER;
                case VOID_STRING:
                case STRING_STRING:
                    return s2 == VOID_STRING || s2 == STRING_STRING;
                default:
                    throw new AssertionError("bad signature kind");
            }
        }
    }

    enum MethodKind {
        VIRTUAL("", "#M #S;"),
        STATIC("static", "#M #S { #BE; #R }"),
        DEFAULT("default", "#M #S { #BE; #R }");

        String modStr;
        String methTemplate;

        MethodKind(String modStr, String methTemplate) {
            this.modStr = modStr;
            this.methTemplate = methTemplate;
        }

        boolean inherithed() {
            return this != STATIC;
        }

        static boolean overrides(MethodKind mk1, SignatureKind sk1, MethodKind mk2, SignatureKind sk2) {
            return sk1 == sk2 &&
                    mk2.inherithed() &&
                    mk1 != STATIC;
        }

        String getBody(BodyExpr be, SignatureKind sk) {
            return methTemplate.replaceAll("#BE", be.bodyExprStr)
                    .replaceAll("#R", sk.retStr)
                    .replaceAll("#M", modStr)
                    .replaceAll("#S", sk.sigStr);
        }
    }

    enum BodyExpr {
        NONE(""),
        THIS("Object o = this");

        String bodyExprStr;

        BodyExpr(String bodyExprStr) {
            this.bodyExprStr = bodyExprStr;
        }

        boolean allowed(MethodKind mk) {
            return this == NONE ||
                    mk != MethodKind.STATIC;
        }
    }

    public static void main(String... args) throws Exception {

        //create default shared JavaCompiler - reused across multiple compilations
        JavaCompiler comp = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fm = comp.getStandardFileManager(null, null, null);

        for (MethodKind mk1 : MethodKind.values()) {
            for (SignatureKind sk1 : SignatureKind.values()) {
                for (BodyExpr be1 : BodyExpr.values()) {
                    for (MethodKind mk2 : MethodKind.values()) {
                        for (SignatureKind sk2 : SignatureKind.values()) {
                            for (BodyExpr be2 : BodyExpr.values()) {
                                for (MethodKind mk3 : MethodKind.values()) {
                                    for (SignatureKind sk3 : SignatureKind.values()) {
                                        for (BodyExpr be3 : BodyExpr.values()) {
                                            new InterfaceMethodHidingTest(mk1, mk2, mk3, sk1, sk2, sk3, be1, be2, be3).run(comp, fm);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        System.out.println("Total check executed: " + checkCount);
    }

    MethodKind mk1, mk2, mk3;
    SignatureKind sk1, sk2, sk3;
    BodyExpr be1, be2, be3;
    JavaSource source;
    DiagnosticChecker diagChecker;

    InterfaceMethodHidingTest(MethodKind mk1, MethodKind mk2, MethodKind mk3,
            SignatureKind sk1, SignatureKind sk2, SignatureKind sk3, BodyExpr be1, BodyExpr be2, BodyExpr be3) {
        this.mk1 = mk1;
        this.mk2 = mk2;
        this.mk3 = mk3;
        this.sk1 = sk1;
        this.sk2 = sk2;
        this.sk3 = sk3;
        this.be1 = be1;
        this.be2 = be2;
        this.be3 = be3;
        this.source = new JavaSource();
        this.diagChecker = new DiagnosticChecker();
    }

    class JavaSource extends SimpleJavaFileObject {

        String template = "interface Sup {\n" +
                          "   default void sup() { }\n" +
                          "}\n" +
                          "interface A extends Sup {\n" +
                          "   #M1\n" +
                          "}\n" +
                          "interface B extends A, Sup {\n" +
                          "   #M2\n" +
                          "}\n" +
                          "interface C extends B, Sup {\n" +
                          "   #M3\n" +
                          "}\n";

        String source;

        public JavaSource() {
            super(URI.create("myfo:/Test.java"), JavaFileObject.Kind.SOURCE);
            source = template.replaceAll("#M1", mk1.getBody(be1, sk1))
                    .replaceAll("#M2", mk2.getBody(be2, sk2))
                    .replaceAll("#M3", mk3.getBody(be3, sk3));
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }

    void run(JavaCompiler tool, StandardJavaFileManager fm) throws Exception {
        JavacTask ct = (JavacTask)tool.getTask(null, fm, diagChecker,
                Arrays.asList("-XDallowStaticInterfaceMethods"), null, Arrays.asList(source));
        try {
            ct.analyze();
        } catch (Throwable ex) {
            throw new AssertionError("Error thrown when analyzing the following source:\n" + source.getCharContent(true));
        }
        check();
    }

    void check() {
        boolean errorExpected =
                !be1.allowed(mk1) || !be2.allowed(mk2) || !be3.allowed(mk3);

        if (mk1.inherithed()) {
            errorExpected |=
                    sk2.overrideEquivalentWith(sk1) && !MethodKind.overrides(mk2, sk2, mk1, sk1) ||
                    sk3.overrideEquivalentWith(sk1) && !MethodKind.overrides(mk3, sk3, mk1, sk1);
        }

        if (mk2.inherithed()) {
            errorExpected |=
                    sk3.overrideEquivalentWith(sk2) && !MethodKind.overrides(mk3, sk3, mk2, sk2);
        }

        checkCount++;
        if (diagChecker.errorFound != errorExpected) {
            throw new AssertionError("Problem when compiling source:\n" + source.getCharContent(true) +
                    "\nfound error: " + diagChecker.errorFound);
        }
    }

    static class DiagnosticChecker implements javax.tools.DiagnosticListener<JavaFileObject> {

        boolean errorFound;

        public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                errorFound = true;
            }
        }
    }
}
