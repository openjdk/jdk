/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.spi.ToolProvider;

import jdk.test.lib.util.FileUtils;

/*
 * @test
 * @summary Make sure that jimages are consistent when created by jlink.
 * @bug 8392531
 * @modules jdk.jlink
 *          java.management.rmi
 *          jdk.httpserver
 * @library /test/lib
 * @build jdk.test.lib.util.FileUtils
 * @run main/othervm JLinkReproducible4Test
 */
public class JLinkReproducible4Test {
    static final ToolProvider JLINK_TOOL = ToolProvider.findFirst("jlink")
            .orElseThrow(() ->
                    new RuntimeException("jlink tool not found")
            );

    public static void main(String[] args) throws Exception {
        Path image1 = Paths.get("./image1");
        Path image2 = Paths.get("./image2");

        JLINK_TOOL.run(System.out, System.err, "--add-modules", "java.management.rmi,jdk.httpserver", "--output", image1.toString());

        for (int i = 0; i < 5; i++) {
            JLINK_TOOL.run(System.out, System.err, "--add-modules", "java.management.rmi,jdk.httpserver", "--output", image2.toString());

            if (Files.mismatch(image1.resolve("lib").resolve("modules"), image2.resolve("lib").resolve("modules")) != -1L) {
                throw new RuntimeException("jlink producing inconsistent result");
            }

            FileUtils.deleteFileTreeWithRetry(image2);
        }
        FileUtils.deleteFileTreeWithRetry(image1);

        JLINK_TOOL.run(System.out, System.err, "--add-modules", "jdk.httpserver,jdk.naming.rmi", "--output", image1.toString());

        for (int i = 0; i < 5; i++) {
            JLINK_TOOL.run(System.out, System.err, "--add-modules", "jdk.httpserver,jdk.naming.rmi", "--output", image2.toString());

            if (Files.mismatch(image1.resolve("lib").resolve("modules"), image2.resolve("lib").resolve("modules")) != -1L) {
                throw new RuntimeException("jlink producing inconsistent result");
            }

            FileUtils.deleteFileTreeWithRetry(image2);
        }
        FileUtils.deleteFileTreeWithRetry(image1);
    }
}
