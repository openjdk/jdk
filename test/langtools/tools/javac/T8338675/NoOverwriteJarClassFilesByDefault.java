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

package T8338675;

import toolbox.JarTask;
import toolbox.JavacTask;
import toolbox.ToolBox;

import java.io.IOException;

/*
 * @test
 * @bug 8338675
 * @summary javac shouldn't silently change .jar files on the classpath
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JarTask toolbox.JavacTask
 * @run main NoOverwriteJarClassFilesByDefault
 */
public class NoOverwriteJarClassFilesByDefault {

    private static final String OLD_JAR_SOURCE = """
            class InJarFile {
                static final String X = "ABCD";
            }
            """;

    private static final String NEW_JAR_SOURCE = """
            class InJarFile {
                static final String X = "XYZ";
            }
            """;

    private static final String TARGET_SOURCE = """
            class TargetClass {
                static final String Y = InJarFile.X;
            }
            """;

    public static void main(String[] args) throws IOException {
        ToolBox tb = new ToolBox();

        new JavacTask(tb)
                .sources(OLD_JAR_SOURCE)
                .run();

        // Jar file has the "new" source in (which doesn't match the compiled class file).
        tb.writeFile("InJarFile.java", NEW_JAR_SOURCE);
        new JarTask(tb, "lib.jar")
                .files("InJarFile.java", "InJarFile.class")
                .run();

        new JavacTask(tb)
                .sources(TARGET_SOURCE)
                .classpath("lib.jar")
                .run();
    }
}
