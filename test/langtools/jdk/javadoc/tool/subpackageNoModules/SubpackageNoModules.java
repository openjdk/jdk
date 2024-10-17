/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8325090
 * @summary javadoc fails when -subpackages option is used with non-modular -source
 * @modules jdk.javadoc/jdk.javadoc.internal.api
 *          jdk.javadoc/jdk.javadoc.internal.tool
 * @library /tools/lib
 * @build toolbox.TestRunner toolbox.ToolBox
 * @run main SubpackageNoModules
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import toolbox.*;
import toolbox.Task.Expect;

public class SubpackageNoModules extends TestRunner {

    final ToolBox tb = new ToolBox();

    public SubpackageNoModules() {
        super(System.err);
    }

    public static void main(String[] args) throws Exception {
        SubpackageNoModules t = new SubpackageNoModules();
        t.runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    @Test
    public void testSubpackageNoModules(Path base) throws Exception {
        Files.createDirectories(base);
        tb.writeFile(base.resolve("pkg/A.java"), "package pkg;\npublic class A {}\n");

        Path outDir = base.resolve("out");
        Files.createDirectory(outDir);
        // Combine -subpackages option with -source release that doesn't support modules
        new JavadocTask(tb)
                .outdir(outDir)
                .sourcepath(base)
                .options("-source", "8",
                         "-subpackages", "pkg")
                .run(Expect.SUCCESS);
        // Check for presence of generated docs
        if (!Files.isRegularFile(outDir.resolve("pkg/A.html"))) {
            error("File not found: " + outDir.resolve("pkg/A.html"));
        }
    }
}
