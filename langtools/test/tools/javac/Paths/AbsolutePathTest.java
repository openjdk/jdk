/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8030046
 * @summary javac incorrectly handles absolute paths in manifest classpath
 * @author govereau
 * @library /tools/javac/lib
 * @build ToolBox
 * @run main AbsolutePathTest
 */

import java.io.File;

public class AbsolutePathTest {
    public static void main(String... cmdline) throws Exception {
        // compile test.Test
        ToolBox.JavaToolArgs args = new ToolBox.JavaToolArgs();
        args.appendArgs("-d", "."); // this is needed to get the classfiles in test
        ToolBox.javac(args.setSources("package test; public class Test{}"));

        // build test.jar containing test.Test
        // we need the jars in a directory different from the working
        // directory to trigger the bug. I will reuse test/
        ToolBox.jar("cf", "test/test.jar", "test/Test.class");

        // build second jar in test directory using
        // an absolute path reference to the first jar
        String path = new File("test/test.jar").getAbsolutePath();
        ToolBox.mkManifestWithClassPath(null, path);
        ToolBox.jar("cfm", "test/test2.jar", "MANIFEST.MF");

        // this should not fail
        args.appendArgs("-cp", ".");
        ToolBox.javac(args.setSources("import test.Test; class Test2 {}"));
    }
}
