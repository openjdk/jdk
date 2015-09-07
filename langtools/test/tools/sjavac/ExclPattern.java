/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8037085
 * @summary Ensures that sjavac can handle various exclusion patterns.
 *
 * @modules jdk.compiler/com.sun.tools.sjavac
 * @build Wrapper
 * @run main Wrapper ExclPattern
 */

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ExclPattern {

    public static void main(String[] ignore) throws IOException {

        String toBeExcluded = "pkg/excl-dir/excluded.txt";
        String toBeIncluded = "pkg/incl-dir/included.txt";

        // Set up source directory with directory to be excluded
        populate(Paths.get("srcdir"),
            "pkg/SomeClass.java",
            "package pkg; public class SomeClass { }",

            toBeExcluded,
            "This file should not end up in the dest directory.",

            toBeIncluded,
            "This file should end up in the dest directory.");

        String[] args = {
                "-x", "pkg/excl-dir/*",
                "-src", "srcdir",
                "-d", "dest",
                "-j", "1",
                "-copy", ".txt",
                "--server:portfile=testserver,background=false",
                "--log=debug"
        };

        int rc = com.sun.tools.sjavac.Main.go(args);
        if (rc != 0) throw new RuntimeException("Error during compile!");

        if (!Files.exists(Paths.get("dest/" + toBeIncluded)))
            throw new AssertionError("File missing: " + toBeIncluded);

        if (Files.exists(Paths.get("dest/" + toBeExcluded)))
            throw new AssertionError("File present: " + toBeExcluded);
    }

    static void populate(Path root, String... args) throws IOException {
        if (!Files.exists(root))
            Files.createDirectory(root);
        for (int i = 0; i < args.length; i += 2) {
            String filename = args[i];
            String content = args[i+1];
            Path p = root.resolve(filename);
            Files.createDirectories(p.getParent());
            try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(p,
                    Charset.defaultCharset()))) {
                out.println(content);
            }
        }
    }
}
