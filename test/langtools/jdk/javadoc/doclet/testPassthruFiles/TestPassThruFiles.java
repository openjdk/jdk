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
 * @bug      8323628
 * @summary  Update license on "pass-through" files
 * @library  /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    toolbox.ToolBox javadoc.tester.*
 * @run main TestPassThruFiles
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

public class TestPassThruFiles extends JavadocTester {

    public static void main(String... args) throws Exception {
        var tester = new TestPassThruFiles();
        tester.runTests();
    }

    final ToolBox tb = new ToolBox();

    @Test
    public void testPassThroughFiles(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, "public class C { }");

        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                src.resolve("C.java").toString());
        checkExit(Exit.OK);

        var files = List.of(
                "resource-files/copy.svg",
                "resource-files/link.svg",
                "resource-files/stylesheet.css",
                "script-files/script.js",
                "script-files/search.js",
                "script-files/search-page.js"
        );

        for (var f : files) {
            checkOrder(f,
                    "Copyright",
                    "Oracle and/or its affiliates. All rights reserved.",
                    "DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.",
                    "Licensed under the Universal Permissive License v 1.0 as shown at " +
                            "https://oss.oracle.com/licenses/upl/");
        }

        var foundFiles = new TreeSet<String>();
        for (var d : List.of("resource-files", "script-files")) {
            try (var s = Files.list(outputDir.resolve(d))) {
                s.filter(this::requiresCheck)
                        .map(p -> outputDir.relativize(p).toString())
                        .map(f -> f.replace(FS, "/"))
                        .collect(Collectors.toCollection(() -> foundFiles));
            }
        }

        checking("verifying the set of checked files");
        if (foundFiles.equals(new TreeSet<>(files))) {
            passed("expected files found");
        } else {
            failed("mismatch in expected files");
            var s1 = new TreeSet<>(files);
            s1.removeAll(foundFiles);
            if (!s1.isEmpty()) {
                out.println("expected, but not found: " + s1);
            }
            var s2 = new TreeSet<>(foundFiles);
            s2.removeAll(files);
            if (!s2.isEmpty()) {
                out.println("found, but not expected: " + s2);
            }
        }
    }

    /**
     * {@return {@code true} if a file should be checked}
     * For future robustness, instead of specifying the set of files
     * that should be checked, we specify the set of files that should
     * not be checked.
     *
     * @param p the path for the file
     */
    private boolean requiresCheck(Path p) {
        var fn = p.getFileName().toString();
        return !fn.startsWith("jquery")
                && !fn.endsWith(".png");
    }
}
