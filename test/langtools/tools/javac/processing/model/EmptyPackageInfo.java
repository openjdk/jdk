/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8298727
 * @summary Verify empty package-info.java is handled properly
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.TestRunner toolbox.ToolBox EmptyPackageInfo
 * @run main EmptyPackageInfo
 */

import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import com.sun.source.util.Trees;
import java.util.ArrayList;
import java.util.List;
import javax.tools.ToolProvider;
import toolbox.TestRunner;
import toolbox.TestRunner.Test;
import toolbox.ToolBox;

public class EmptyPackageInfo extends TestRunner {

    public static void main(String... args) throws Exception {
        new EmptyPackageInfo().runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    private final ToolBox tb = new ToolBox();

    public EmptyPackageInfo() {
        super(System.err);
    }

    @Test
    public void testEmptyPackageInfo(Path outerBase) throws Exception {
        Path src = outerBase.resolve("src");
        Path classes = outerBase.resolve("classes");
        Path packInfo = src.resolve("package-info.java");

        tb.writeFile(packInfo, "/**javadoc*/\n");
        Files.createDirectories(classes);

        var compiler = ToolProvider.getSystemJavaCompiler();

        try (var fm = compiler.getStandardFileManager(null,
                                                      null,
                                                      null)) {
            var task =
                    (JavacTask) compiler.getTask(null,
                                                 fm,
                                                 null,
                                                 null,
                                                 null,
                                                 fm.getJavaFileObjects(packInfo));
            task.analyze();
            var pack = task.getElements().getPackageElement("");
            var trees = Trees.instance(task);
            var packPath = trees.getPath(pack);
            var packTree = packPath.getLeaf();
            if (packTree.getKind() != Tree.Kind.COMPILATION_UNIT) {
                throw new AssertionError("Unexpected tree kind: " + packTree.getKind());
            }
            var actualJavadoc = trees.getDocComment(packPath);
            var expectedJavadoc = "javadoc";
            if (!expectedJavadoc.equals(actualJavadoc)) {
                throw new AssertionError("Unexpected javadoc, " +
                                         "expected: " + expectedJavadoc +
                                         ", got: " + actualJavadoc);
            }
        }
    }

    @Test
    public void testMultipleFiles(Path outerBase) throws Exception {
        Path src = outerBase.resolve("src");
        Path classes = outerBase.resolve("classes");
        Path packInfo1 = src.resolve("test1").resolve("package-info.java");
        Path packInfo2 = src.resolve("test2").resolve("package-info.java");

        tb.writeFile(packInfo1, "");
        tb.writeFile(packInfo2, "");
        Files.createDirectories(classes);

        var compiler = ToolProvider.getSystemJavaCompiler();

        try (var fm = compiler.getStandardFileManager(null,
                                                      null,
                                                      null)) {
            var diags = new ArrayList<String>();
            var task =
                    (JavacTask) compiler.getTask(null,
                                                 fm,
                                                 d -> diags.add(d.getCode()),
                                                 null,
                                                 null,
                                                 fm.getJavaFileObjects(packInfo1,
                                                                       packInfo2));
            task.analyze();
            var expectedDiags =
                    List.of("compiler.warn.pkg-info.already.seen");
            if (!expectedDiags.equals(diags)) {
                throw new AssertionError("Unexpected diags, " +
                                         "expected: " + expectedDiags +
                                         ", got: " + diags);
            }
        }
    }
}
