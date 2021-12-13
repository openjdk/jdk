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
 * @bug 8189591
 * @summary No way to locally suppress doclint warnings
 * @library /tools/lib
 * @modules jdk.javadoc/jdk.javadoc.internal.doclint
 * @build toolbox.ToolBox SuppressWarningsTest
 * @run main SuppressWarningsTest
 */

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import jdk.javadoc.internal.doclint.DocLint;
import toolbox.ToolBox;

public class SuppressWarningsTest {
    public static void main(String... args) throws Exception {
        new SuppressWarningsTest().run();
    }

    enum Kind {
        NONE("// no annotation"),
        MISSING("@SuppressWarnings(\"doclint:missing\")"),
        OTHER("@SuppressWarnings(\"doclint:html\")"),
        ALL("@SuppressWarnings(\"doclint\")");
        final String anno;
        Kind(String anno) {
            this.anno = anno;
        }
    }

    ToolBox tb = new ToolBox();
    PrintStream log = System.err;

    void run() throws Exception {
        for (Kind ok : Kind.values()) {
            for (Kind ik : Kind.values()) {
                for (Kind mk : Kind.values()) {
                    test(ok, ik, mk);
                }
            }
        }

        if (errorCount == 0) {
            log.println("No errors");
        } else {
            log.println(errorCount + " errors");
            throw new Exception(errorCount + " errors");
        }
    }

    void test(Kind outerKind, Kind innerKind, Kind memberKind) throws Exception {
        log.println("Test: outer:" + outerKind + " inner: " + innerKind + " member:" + memberKind);
        Path base = Path.of(outerKind + "-" + innerKind + "-" + memberKind);
        Files.createDirectories(base);
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                /** . */
                ##OUTER##
                public class Outer {
                    /** . */
                    private Outer() { }
                    /** . */
                    ##INNER##
                    public class Inner {
                        /** . */
                        private Inner() { }
                        ##MEMBER##
                        public void m() { }
                    }
                }
                """
                .replace("##OUTER##", outerKind.anno)
                .replace("##INNER##", innerKind.anno)
                .replace("##MEMBER##", memberKind.anno));

        DocLint dl = new DocLint();
        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            dl.run(pw, src.resolve("Outer.java").toString());
        }
        String out = sw.toString();
        out.lines().forEach(System.err::println);

        boolean expectSuppressed = false;
        for (Kind k : List.of(outerKind, innerKind, memberKind)) {
            expectSuppressed |= k == Kind.ALL || k == Kind.MISSING;
        }
        boolean foundWarning = out.contains("warning: no comment");
        if (expectSuppressed) {
            if (foundWarning) {
                error("found unexpected warning");
            } else {
                log.println("Passed: no warning, as expected");
            }
        } else {
            if (foundWarning) {
                log.println("Passed: found warning, as expected");
            } else {
                error("no warning");
            }
        }
        log.println();
    }

    int errorCount;

    void error(String message) {
        log.println("Error: " + message);
        errorCount++;
    }
}
