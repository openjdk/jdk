/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8309595
 * @summary Allow javadoc to process implicit classes
 * @library  /tools/lib ../../lib
 * @modules  jdk.javadoc/jdk.javadoc.internal.tool
 * @build    toolbox.ToolBox javadoc.tester.*
 * @run main TestImplicit
 */

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

public class TestImplicit extends JavadocTester {

    private static final String thisVersion = System.getProperty("java.specification.version");

    private static final ToolBox tb = new ToolBox();

    public static void main(String... args) throws Exception {
        new TestImplicit().runTests();
    }

    @Test
    public void testImplicit(Path base) throws IOException {
        String className = "Sample";
        Files.createDirectories(base);
        Path out = base.resolve("out");
        Path src = base.resolve("src");
        Path sample = src.resolve(className + ".java");

        Files.createDirectories(out);
        Files.createDirectories(src);
        Files.writeString(sample, """
            /**
             * This is a comment for the main method.
             */
            void main() {
                System.out.println("Done");
            }
            """);

         javadoc(
             "--enable-preview",
             "--source", thisVersion,
             "-private",
             "-d", out.toString(),
             sample.toString()
         );

        checkOutput(className + ".html", true, "This is a comment for the main method.");
    }

}
