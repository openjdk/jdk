/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Make sure -Xpatch works with multiple directories.
 * @modules java.base/jdk.internal.misc
 * @library /testlibrary
 * @compile Xpatch2DirsMain.java
 * @run main Xpatch2Dirs
 */

import jdk.test.lib.*;
import java.io.File;

public class Xpatch2Dirs {

    public static void main(String[] args) throws Exception {
        String source1 = "package javax.naming.spi; "               +
                        "public class NamingManager { "             +
                        "    static { "                             +
                        "        System.out.println(\"I pass one!\"); " +
                        "    } "                                    +
                        "}";
        String source2 = "package java.beans; "                     +
                        "public class Encoder { "                   +
                        "    static { "                             +
                        "        System.out.println(\"I pass two!\"); " +
                        "    } "                                    +
                        "}";

        ClassFileInstaller.writeClassToDisk("javax/naming/spi/NamingManager",
             InMemoryJavaCompiler.compile("javax.naming.spi.NamingManager", source1, "-Xmodule:java.naming"),
             "mods/java.naming");

        ClassFileInstaller.writeClassToDisk("java/beans/Encoder",
             InMemoryJavaCompiler.compile("java.beans.Encoder", source2, "-Xmodule:java.desktop"),
             "mods2/java.desktop");

        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
             "-Xpatch:mods" + File.pathSeparator + "mods2",
             "Xpatch2DirsMain", "javax.naming.spi.NamingManager", "java.beans.Encoder");

        OutputAnalyzer oa = new OutputAnalyzer(pb.start());
        oa.shouldContain("I pass one!");
        oa.shouldContain("I pass two!");
        oa.shouldHaveExitValue(0);
    }
}
