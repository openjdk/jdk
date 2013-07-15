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
 * @bug 8013576
 * @summary Add stat support to LambdaToMethod
 * @library ../lib
 * @build JavacTestingAbstractThreadedTest
 * @run main/othervm TestLambdaToMethodStats
 */

// use /othervm to avoid jtreg timeout issues (CODETOOLS-7900047)
// see JDK-8006746

import java.net.URI;
import java.util.Arrays;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;

import com.sun.source.util.JavacTask;
import com.sun.tools.javac.api.ClientCodeWrapper;
import com.sun.tools.javac.util.JCDiagnostic;

public class TestLambdaToMethodStats
    extends JavacTestingAbstractThreadedTest
    implements Runnable {

    enum ExprKind {
        LAMBDA("()->null"),
        MREF1("this::g"),
        MREF2("this::h");

        String exprStr;

        ExprKind(String exprStr) {
            this.exprStr = exprStr;
        }
    }

    enum TargetKind {
        IMPLICIT(""),
        SERIALIZABLE("(A & java.io.Serializable)");

        String targetStr;

        TargetKind(String targetStr) {
            this.targetStr = targetStr;
        }
    }

    public static void main(String... args) throws Exception {
        for (ExprKind ek : ExprKind.values()) {
            for (TargetKind tk : TargetKind.values()) {
                pool.execute(new TestLambdaToMethodStats(ek, tk));
            }
        }

        checkAfterExec(true);
    }

    ExprKind ek;
    TargetKind tk;
    JavaSource source;
    DiagnosticChecker diagChecker;


    TestLambdaToMethodStats(ExprKind ek, TargetKind tk) {
        this.ek = ek;
        this.tk = tk;
        this.source = new JavaSource();
        this.diagChecker = new DiagnosticChecker();
    }

    class JavaSource extends SimpleJavaFileObject {

        String template = "interface A {\n" +
                          "   Object o();\n" +
                          "}\n" +
                          "class Test {\n" +
                          "   A a = #C#E;\n" +
                          "   Object g() { return null; }\n" +
                          "   Object h(Object... o) { return null; }\n" +
                          "}";

        String source;

        public JavaSource() {
            super(URI.create("myfo:/Test.java"), JavaFileObject.Kind.SOURCE);
            source = template.replaceAll("#E", ek.exprStr)
                    .replaceAll("#C", tk.targetStr);
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }

    public void run() {
        JavacTask ct = (JavacTask)comp.getTask(null, fm.get(), diagChecker,
                Arrays.asList("-XDdumpLambdaToMethodStats"),
                null, Arrays.asList(source));
        try {
            ct.generate();
        } catch (Throwable ex) {
            throw new
                AssertionError("Error thron when analyzing the following source:\n" +
                    source.getCharContent(true));
        }
        check();
    }

    void check() {
        checkCount.incrementAndGet();

        boolean error = diagChecker.lambda !=
                (ek == ExprKind.LAMBDA);

        error |= diagChecker.bridge !=
                (ek == ExprKind.MREF2);

        error |= diagChecker.altMetafactory !=
                (tk == TargetKind.SERIALIZABLE);

        if (error) {
            throw new AssertionError("Bad stat diagnostic found for source\n" +
                    "lambda = " + diagChecker.lambda + "\n" +
                    "bridge = " + diagChecker.bridge + "\n" +
                    "altMF = " + diagChecker.altMetafactory + "\n" +
                    source.source);
        }
    }

    static class DiagnosticChecker
        implements javax.tools.DiagnosticListener<JavaFileObject> {

        boolean altMetafactory;
        boolean bridge;
        boolean lambda;

        public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
            try {
                if (diagnostic.getKind() == Diagnostic.Kind.NOTE) {
                    switch (diagnostic.getCode()) {
                        case "compiler.note.lambda.stat":
                            lambda = true;
                            break;
                        case "compiler.note.mref.stat":
                            lambda = false;
                            bridge = false;
                            break;
                        case "compiler.note.mref.stat.1":
                            lambda = false;
                            bridge = true;
                            break;
                        default:
                            throw new AssertionError("unexpected note: " + diagnostic.getCode());
                    }
                    ClientCodeWrapper.DiagnosticSourceUnwrapper dsu =
                        (ClientCodeWrapper.DiagnosticSourceUnwrapper)diagnostic;
                    altMetafactory = (Boolean)dsu.d.getArgs()[0];
                }
            } catch (RuntimeException t) {
                t.printStackTrace();
                throw t;
            }
        }
    }
}
