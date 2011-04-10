/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7030606
 * @summary Project-coin: multi-catch types should be pairwise disjoint
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

public class DisjunctiveTypeWellFormednessTest {

    enum Alternative {
        EXCEPTION("Exception"),
        RUNTIME_EXCEPTION("RuntimeException"),
        IO_EXCEPTION("java.io.IOException"),
        FILE_NOT_FOUND_EXCEPTION("java.io.FileNotFoundException"),
        ILLEGAL_ARGUMENT_EXCEPTION("IllegalArgumentException");

        String exceptionStr;

        private Alternative(String exceptionStr) {
            this.exceptionStr = exceptionStr;
        }

        static String makeDisjunctiveType(Alternative... alternatives) {
            StringBuilder buf = new StringBuilder();
            String sep = "";
            for (Alternative alternative : alternatives) {
                buf.append(sep);
                buf.append(alternative.exceptionStr);
                sep = "|";
            }
            return buf.toString();
        }

        boolean disjoint(Alternative that) {
            return disjoint[this.ordinal()][that.ordinal()];
        }

        static boolean[][] disjoint = {
            //                              Exception    RuntimeException    IOException    FileNotFoundException    IllegalArgumentException
            /*Exception*/                {  false,       false,              false,         false,                   false },
            /*RuntimeException*/         {  false,       false,              true,          true,                    false },
            /*IOException*/              {  false,       true,               false,         false,                   true },
            /*FileNotFoundException*/    {  false,       true,               false,         false,                   true },
            /*IllegalArgumentException*/ {  false,       false,              true,          true,                    false }
        };
    }

    enum Arity {
        ONE(1),
        TWO(2),
        THREE(3),
        FOUR(4),
        FIVE(5);

        int n;

        private Arity(int n) {
            this.n = n;
        }
    }

    public static void main(String... args) throws Exception {

        //create default shared JavaCompiler - reused across multiple compilations
        JavaCompiler comp = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fm = comp.getStandardFileManager(null, null, null);

        for (Arity arity : Arity.values()) {
            for (Alternative a1 : Alternative.values()) {
                if (arity == Arity.ONE) {
                    new DisjunctiveTypeWellFormednessTest(a1).run(comp, fm);
                    continue;
                }
                for (Alternative a2 : Alternative.values()) {
                    if (arity == Arity.TWO) {
                        new DisjunctiveTypeWellFormednessTest(a1, a2).run(comp, fm);
                        continue;
                    }
                    for (Alternative a3 : Alternative.values()) {
                        if (arity == Arity.THREE) {
                            new DisjunctiveTypeWellFormednessTest(a1, a2, a3).run(comp, fm);
                            continue;
                        }
                        for (Alternative a4 : Alternative.values()) {
                            if (arity == Arity.FOUR) {
                                new DisjunctiveTypeWellFormednessTest(a1, a2, a3, a4).run(comp, fm);
                                continue;
                            }
                            for (Alternative a5 : Alternative.values()) {
                                new DisjunctiveTypeWellFormednessTest(a1, a2, a3, a4, a5).run(comp, fm);
                            }
                        }
                    }
                }
            }
        }
    }

    Alternative[] alternatives;
    JavaSource source;
    DiagnosticChecker diagChecker;

    DisjunctiveTypeWellFormednessTest(Alternative... alternatives) {
        this.alternatives = alternatives;
        this.source = new JavaSource();
        this.diagChecker = new DiagnosticChecker();
    }

    class JavaSource extends SimpleJavaFileObject {

        String template = "class Test {\n" +
                              "void test() {\n" +
                                 "try {} catch (#T e) {}\n" +
                              "}\n" +
                          "}\n";

        String source;

        public JavaSource() {
            super(URI.create("myfo:/Test.java"), JavaFileObject.Kind.SOURCE);
            source = template.replace("#T", Alternative.makeDisjunctiveType(alternatives));
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }

    void run(JavaCompiler tool, StandardJavaFileManager fm) throws Exception {
        JavacTask ct = (JavacTask)tool.getTask(null, fm, diagChecker,
                null, null, Arrays.asList(source));
        ct.analyze();
        check();
    }

    void check() {

        int non_disjoint = 0;
        int i = 0;
        for (Alternative a1 : alternatives) {
            int j = 0;
            for (Alternative a2 : alternatives) {
                if (i == j) continue;
                if (!a1.disjoint(a2)) {
                    non_disjoint++;
                    break;
                }
                j++;
            }
            i++;
        }

        if (non_disjoint != diagChecker.errorsFound) {
            throw new Error("invalid diagnostics for source:\n" +
                source.getCharContent(true) +
                "\nFound errors: " + diagChecker.errorsFound +
                "\nExpected errors: " + non_disjoint);
        }
    }

    static class DiagnosticChecker implements javax.tools.DiagnosticListener<JavaFileObject> {

        int errorsFound;

        public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR &&
                    diagnostic.getCode().startsWith("compiler.err.multicatch.types.must.be.disjoint")) {
                errorsFound++;
            }
        }
    }
}
