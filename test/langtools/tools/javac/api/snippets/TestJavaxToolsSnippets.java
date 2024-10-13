/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Use snippets in java.compiler documentation
 * @library /tools/lib ../../lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @build snippets.SnippetUtils toolbox.JavacTask toolbox.TestRunner toolbox.ToolBox
 * @run main TestJavaxToolsSnippets
 */

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;

import com.sun.source.doctree.SnippetTree;

import snippets.SnippetUtils;
import toolbox.JavacTask;
import toolbox.Task;
import toolbox.TestRunner;
import toolbox.ToolBox;

/**
 * Tests the snippets in the {@code javax.tools} package, by compiling the
 * external snippets and parsing the internal Java snippets.
 */
public class TestJavaxToolsSnippets extends TestRunner {
    public static void main(String... args) throws Exception {
        try {
            new TestJavaxToolsSnippets().runTests(m -> new Object[] { Path.of(m.getName()) });
        } catch (SnippetUtils.ConfigurationException e) {
            System.err.println("NOTE: " + e.getMessage() + "; test skipped");
        }
    }

    SnippetUtils snippets = new SnippetUtils("java.compiler");
    ToolBox tb = new ToolBox();

    TestJavaxToolsSnippets() throws SnippetUtils.ConfigurationException {
        super(System.err);
    }

    @Test
    public void testExternalSnippets(Path base) throws Exception {
        Path snippetFilesDir = snippets.getSourceDir()
                .resolve("java.compiler")  // module
                .resolve("share").resolve("classes")
                .resolve("javax.tools".replace(".", File.separator)) // package
                .resolve("snippet-files");
        new JavacTask(tb)
                .files(tb.findJavaFiles(snippetFilesDir))
                .outdir(Files.createDirectories(base.resolve("classes")))
                .run(Task.Expect.SUCCESS)
                .writeAll();
        out.println("Compilation succeeded");
    }

    @Test
    public void testJavaCompilerSnippets(Path base) {
        TypeElement te = snippets.getElements().getTypeElement("javax.tools.JavaCompiler");
        snippets.scan(te, this::handleSnippet);
    }

    @Test
    public void testJavaFileManagerSnippets(Path base) {
        TypeElement te = snippets.getElements().getTypeElement("javax.tools.JavaFileManager");
        snippets.scan(te, this::handleSnippet);
    }

    @Test
    public void testStandardJavaFileManagerSnippets(Path base) {
        TypeElement te = snippets.getElements().getTypeElement("javax.tools.StandardJavaFileManager");
        snippets.scan(te, this::handleSnippet);
    }

    void handleSnippet(Element e, SnippetTree tree) {
        String lang = snippets.getAttr(tree, "lang");
        if (Objects.equals(lang, "java")) {
            String body = snippets.getBody(tree);
            if (body != null) {
                String id = snippets.getAttr(tree, "id");
                try {
                    out.println("parsing snippet " + e + ":" + id);
                    if (snippets.parse(body, out::println)) {
                        out.println("parsed snippet");
                    } else {
                        error("parse failed");
                    }
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            }
        }
    }
}
