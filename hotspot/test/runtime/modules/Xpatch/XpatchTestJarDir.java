/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Make sure -Xpatch works when a jar file and a directory is specified for a module
 * @library /testlibrary
 * @modules java.base/jdk.internal.misc
 *          jdk.jartool/sun.tools.jar
 * @build BasicJarBuilder
 * @compile Xpatch2DirsMain.java
 * @run main XpatchTestJarDir
 */

import java.io.File;
import java.nio.file.Files;
import jdk.test.lib.*;

public class XpatchTestJarDir {
    private static String moduleJar;

    public static void main(String[] args) throws Exception {

        // Create a class file in the module java.naming. This class file
        // will be put in the javanaming.jar file.
        String source = "package javax.naming.spi; "                    +
                        "public class NamingManager1 { "                +
                        "    static { "                                 +
                        "        System.out.println(\"I pass one!\"); " +
                        "    } "                                        +
                        "}";

        ClassFileInstaller.writeClassToDisk("javax/naming/spi/NamingManager1",
             InMemoryJavaCompiler.compile("javax.naming.spi.NamingManager1", source, "-Xmodule:java.naming"),
             System.getProperty("test.classes"));

        // Build the jar file that will be used for the module "java.naming".
        BasicJarBuilder.build("javanaming", "javax/naming/spi/NamingManager1");
        moduleJar = BasicJarBuilder.getTestJar("javanaming.jar");

        // Just to make sure we are not fooled by the class file being on the
        // class path where all the test classes are stored, write the NamingManager.class
        // file out again with output that does not contain what OutputAnalyzer
        // expects. This will provide confidence that the contents of the class
        // is truly coming from the jar file and not the class file.
        source = "package javax.naming.spi; "                +
                 "public class NamingManager1 { "            +
                 "    static { "                             +
                 "        System.out.println(\"Fail!\"); "   +
                 "    } "                                    +
                 "}";

        ClassFileInstaller.writeClassToDisk("javax/naming/spi/NamingManager1",
             InMemoryJavaCompiler.compile("javax.naming.spi.NamingManager1", source, "-Xmodule:java.naming"),
             System.getProperty("test.classes"));

        // Create a second class file in the module java.naming. This class file
        // will be put in the mods/java.naming directory.
        source = "package javax.naming.spi; "                    +
                 "public class NamingManager2 { "                +
                 "    static { "                                 +
                 "        System.out.println(\"I pass two!\"); " +
                 "    } "                                        +
                 "}";

        ClassFileInstaller.writeClassToDisk("javax/naming/spi/NamingManager2",
             InMemoryJavaCompiler.compile("javax.naming.spi.NamingManager2", source, "-Xmodule:java.naming"),
             (System.getProperty("test.classes") + "/mods/java.naming"));


        // Supply -Xpatch with the name of the jar file for the module java.naming.
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder("-Xpatch:java.naming=" +
                                                                           moduleJar +
                                                                           File.pathSeparator +
                                                                           System.getProperty("test.classes") + "/mods/java.naming",
                                                                  "Xpatch2DirsMain",
                                                                  "javax.naming.spi.NamingManager1",
                                                                  "javax.naming.spi.NamingManager2");

        new OutputAnalyzer(pb.start())
            .shouldContain("I pass one!")
            .shouldContain("I pass two!")
            .shouldHaveExitValue(0);
    }
}
