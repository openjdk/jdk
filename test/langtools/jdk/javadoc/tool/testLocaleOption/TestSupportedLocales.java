/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8372708
 * @summary  Javadoc ignores "-locale" and uses default locale for all messages and texts
 * @library  /tools/lib ../../lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.javadoc/jdk.javadoc.internal.tool
 * @build    toolbox.ToolBox javadoc.tester.*
 * @run main TestSupportedLocales
 */

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

public class TestSupportedLocales extends JavadocTester {

    public static void main(String... args) throws Exception {
        var tester = new TestSupportedLocales();
        tester.runTests();
    }

    private final ToolBox tb = new ToolBox();

    // A locale with an associated output message
    record LocalizedOutput(Locale locale, String message) {}

    // Console messages are determined by the system default locale
    private final LocalizedOutput[] consoleOutput = new LocalizedOutput[] {
            new LocalizedOutput(Locale.CHINA, "\u6b63\u5728\u6784\u9020 Javadoc \u4fe1\u606f..."),
            new LocalizedOutput(Locale.GERMANY, "Javadoc-Informationen werden erstellt..."),
            new LocalizedOutput(Locale.JAPAN, "Javadoc\u60c5\u5831\u3092\u69cb\u7bc9\u3057\u3066\u3044\u307e\u3059..."),
            new LocalizedOutput(Locale.US, "Constructing Javadoc information..."),
    };

    // Documentation messages are determined by the -locale option
    private final LocalizedOutput[] documentationOutput = new LocalizedOutput[] {
            new LocalizedOutput(Locale.CHINA, "\u7c7b\u548c\u63a5\u53e3"),
            new LocalizedOutput(Locale.GERMANY, "Klassen und Schnittstellen"),
            new LocalizedOutput(Locale.JAPAN, "\u30af\u30e9\u30b9\u3068\u30a4\u30f3\u30bf\u30d5\u30a7\u30fc\u30b9"),
            new LocalizedOutput(Locale.US, "Classes and Interfaces"),
    };

    // Test all combinations of system and documentation locales
    @Test
    public void testSupportedLocales(Path base) throws Exception {
        var src = base.resolve("src");
        initSource(src);
        for (var console : consoleOutput) {
            for (var documentation : documentationOutput) {
                test(base, console, documentation);
            }
        }
    }

    void test(Path base, LocalizedOutput console, LocalizedOutput documentation) throws Exception {
        var src = base.resolve("src");
        var out = base.resolve(console.locale + "-" + documentation.locale);
        Locale.setDefault(console.locale);
        javadoc("-d", out.toString(),
                "-locale", documentation.locale.toString(),
                "--source-path", src.toString(),
                "p");
        checkExit(Exit.OK);
        checkOutput(Output.OUT, true, console.message);
        checkOutput("p/package-summary.html", true, documentation.message);
    }

    private void initSource(Path src) throws IOException {
        tb.writeJavaFiles(src, """
            package p;
            /**
             * A class.
             */
            public class C {
                private C() { }
            }""");
    }
}
