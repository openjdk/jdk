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
 * @bug 8272944
 * @summary Use snippets in jdk.javadoc documentation
 * @library /tools/lib ../../lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.javadoc/jdk.javadoc.internal.tool
 * @build snippets.SnippetUtils toolbox.JavacTask toolbox.ToolBox javadoc.tester.*
 * @run main TestDocletExample
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;

import snippets.SnippetUtils;
import toolbox.Task;
import toolbox.TestRunner;
import toolbox.ToolBox;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;


public class TestDocletExample extends TestRunner {
    public static void main(String... args) throws Exception {
        var t = new TestDocletExample();
        t.runTests(m -> new Object[] { Path.of(m.getName()) });
    }

    SnippetUtils snippets = new SnippetUtils("jdk.javadoc");
    ToolBox tb = new ToolBox();

    TestDocletExample() {
        super(System.out);
    }

    @Test
    public void testEntryPoint(Path base) throws Exception {
        var docletPkg = snippets.getElements().getPackageElement("jdk.javadoc.doclet");
        var dc = snippets.getDocTrees().getDocCommentTree(docletPkg);
        var entryPointSnippet = snippets.getSnippetById(dc, "entry-point");
        var entryPointCode = entryPointSnippet.getBody().getBody();
        var code = """
                class C {
                    %s { }
                }
                """.formatted(entryPointCode);
        DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<>();
        snippets.parse(code, null, collector);
        var diags = collector.getDiagnostics();
        if (diags.isEmpty()) {
            out.println("parsed entry point snippet");
        } else {
            diags.forEach(out::println);
            throw new Exception("parse failed");
        }
    }

    @Test
    public void testDocletExample(Path base) throws Exception {

        // get source code
        var docletPkg = snippets.getElements().getPackageElement("jdk.javadoc.doclet");
        var dc = snippets.getDocTrees().getDocCommentTree(docletPkg);
        var exampleSnippet = snippets.getSnippetById(dc, "Example.java");
        var exampleCode = exampleSnippet.getBody().getBody();

        // compile it
        Path src = base.resolve("src");
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);

        tb.writeJavaFiles(src, exampleCode);
        new toolbox.JavacTask(tb)
                .outdir(classes)
                .files(tb.findJavaFiles(src))
                .run(Task.Expect.SUCCESS)
                .writeAll();

        // get demo command
        var cmdSnippet = snippets.getSnippetById(dc, "run-doclet");
        var cmd = cmdSnippet.getBody().getBody()
                .replaceAll("\\s+//.*", "")     // remove markup
                .replaceAll("\\\\\n", " ")      // join lines
                .trim();
        out.println(cmd);

        tb.writeFile(src.resolve("overview.html"),
                """
                        <!doctype html>
                        <html><title>Overview</title>
                        <body>
                        Overview
                        </body>
                        </html>
                        """);

        var cmdWords = Stream.of(cmd.split("\\s+"))
                .map(s -> s.replace("source-location", src.toString()))
                .map(s -> s.replace("doclet-classes", classes.toString()))
                .toList();
        var toolName = cmdWords.get(0);
        var toolArgs = cmdWords.subList(1, cmdWords.size());

        ToolProvider tool = ToolProvider.findFirst(toolName)
                .orElseThrow(() -> new Exception("tool not found: " + toolName));
        int rc = tool.run(System.out, System.err, toolArgs.toArray(new String[0]));
        if (rc != 0) {
            throw new Exception("ecommand return code: " + rc);
        }
    }
}
