/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8314448 8288660
 * @library /tools/lib ../../lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.javadoc/jdk.javadoc.internal.tool
 * @build toolbox.ToolBox javadoc.tester.*
 * @run main TestUnknownTags
 */

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Pattern;

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

public class TestUnknownTags extends JavadocTester {

    public static void main(String... args) throws Exception {
        new TestUnknownTags().runTests();
    }

    private final ToolBox tb = new ToolBox();

    // DocLint or not, there should be exactly one diagnostic message about
    // an unknown tag. No doubled, no "swallowed" messages, just one.
    @Test
    public void testExactlyOneMessage(Path base) throws Exception {
        var src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package x;

                /** @mytag */
                public class MyClass { }
                """);
        // don't check exit status: we don't care if it's an error or warning

        // DocLint is explicit
        int i = 0;
        for (var check : new String[]{":all", ":none", ""}) {
            var outputDir = "out-DocLint-" + i++; // use separate output directories
            javadoc("-Xdoclint" + check,
                    "-d", base.resolve(outputDir).toString(),
                    "--source-path", src.toString(),
                    "x");
            new OutputChecker(Output.OUT)
                    .setExpectFound(true)
                    .checkUnique(Pattern.compile("unknown tag."));
        }
        // DocLint is default
        javadoc("-d", base.resolve("out").toString(),
                "--source-path", src.toString(),
                "x");
        new OutputChecker(Output.OUT)
                .setExpectFound(true)
                .checkUnique(Pattern.compile("unknown tag."));
    }

    // Disabled simple tags are treated as known tags, but aren't checked
    // for misuse. Additionally, they don't prevent other tags from being
    // checked.
    @Test
    public void testDisabledSimpleTags(Path base) throws Exception {
        var src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package x;

                /**
                 * @myDisabledTag foo
                 * @myEnabledTag bar
                 */
                public class MyClass extends RuntimeException { }
                """);
        javadoc("-d", base.resolve("out").toString(),
                "-sourcepath", src.toString(),
                "-tag", "myDisabledTag:mX:Disabled Tag", // may appear in methods; disabled
                "-tag", "myEnabledTag:mf:Enabled Tag", // may appear in method and fields; enabled
                "x");
        checkOutput(Output.OUT, false, "unknown tag");
        checkOutput(Output.OUT, false, "Tag @myDisabledTag cannot be used in class documentation");
        checkOutput(Output.OUT, true, "Tag @myEnabledTag cannot be used in class documentation");
    }

    // This tests two assertions:
    //
    //   - the "helpful note" is output exactly once,
    //   - some typos have fix suggestions, and
    //   - there's no difference between inline and block tags as
    //     far as the diagnostic output is concerned
    @Test
    public void testSimilarTags(Path base) throws Exception {
        var src = base.resolve("src");
        // put some tags as inline in the main description, so that they are
        // not parsed as contents of the immediately preceding unknown
        // block tags
        tb.writeJavaFiles(src, """
                package x;

                /**
                 * {@cod}
                 * {@codejdk.net.hosts.file}
                 * {@coe}
                 * {@cpde}
                 * {@ocde}
                 * {@ode}
                 *
                 * @auther
                 *
                 * @Depricated
                 * @deprecation
                 *
                 * @DocRoot
                 * @dccRoot
                 * @docroot
                 *
                 * @ecception
                 * @excception
                 * @exceptbion
                 * @exceptino
                 * @exceptions
                 * @exceptoin
                 * @execption
                 *
                 * @implnote
                 *
                 * @inheritdoc
                 * @inherotDoc
                 * @inheretdoc
                 * @inhertitDoc
                 *
                 * @jvm
                 * @jmvs
                 *
                 * @Link
                 * @linK
                 * @linbk
                 * @lini
                 * @linke
                 * @linked
                 *
                 * @linkplan
                 *
                 * @params
                 * @pararm
                 * @parasm
                 * @parem
                 * @parm
                 * @parma
                 * @praam
                 * @prarm
                 *
                 * @Return
                 * @eturn
                 * @result
                 * @retrun
                 * @retuen
                 * @retun
                 * @retunr
                 * @retur
                 * @returns
                 * @returnss
                 * @retursn
                 * @rturn
                 *
                 * @See
                 * @gsee
                 *
                 * @serialdata
                 *
                 * @sinc
                 * @sine
                 *
                 * @systemproperty
                 *
                 * @thows
                 * @thrown
                 * @throwss
                 */
                public class MyClass { }
                """);
        // don't check exit status: we don't care if it's an error or warning

        // DocLint is explicit
        int i = 0;
        for (var check : new String[]{":all", ":none", "", null}) {
            var outputDir = "out-DocLint-" + i++; // use separate output directories

            var args = new ArrayList<String>();
            if (check != null) // check == null means DocLint is default
                args.add("-Xdoclint" + check);
            args.addAll(Arrays.asList(
                    "-d", base.resolve(outputDir).toString(),
                    "-tag", "apiNote:a:API Note:",
                    "-tag", "implSpec:a:Implementation Requirements:",
                    "-tag", "implNote:a:Implementation Note:",
                    "-tag", "jls:a:JLS", // this tag isn't exactly that of JDK, for simplicity reasons
                    "-tag", "jvms:a:JVMS", // this tag isn't exactly that of JDK, for simplicity reasons
                    "--source-path", src.toString(),
                    "x"));

            javadoc(args.toArray(new String[]{}));

            new OutputChecker(Output.OUT)
                    .setExpectFound(true)
                    .setExpectOrdered(false)
                    .check("author", "code", "deprecated", "docRoot",
                            "exception", "implNote", "inheritDoc", "jvms",
                            "link", "linkplain", "param", "return", "see",
                            "serialData", "since", "systemProperty", "throws");
        }
    }
}
