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
 * @bug 8069469
 * @summary Make sure -XX:+TraceClassLoading works properly with "modules" jimage,
            -Xpatch, and with -Xbootclasspath/a
 * @modules java.base/jdk.internal.misc
 * @library /testlibrary
 * @compile XpatchMain.java
 * @run main XpatchTraceCL
 */

import java.io.File;
import jdk.test.lib.*;

public class XpatchTraceCL {

    public static void main(String[] args) throws Exception {
        String source = "package javax.naming.spi; "                +
                        "public class NamingManager { "             +
                        "    static { "                             +
                        "        System.out.println(\"I pass!\"); " +
                        "    } "                                    +
                        "}";

        // Test -XX:+TraceClassLoading output for -Xpatch
        ClassFileInstaller.writeClassToDisk("javax/naming/spi/NamingManager",
             InMemoryJavaCompiler.compile("javax.naming.spi.NamingManager", source, "-Xmodule:java.naming"),
             "mods/java.naming");

        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder("-Xpatch:mods",
             "-XX:+TraceClassLoading", "XpatchMain", "javax.naming.spi.NamingManager");

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        // "modules" jimage case.
        output.shouldContain("[class,load] java.lang.Thread source: jrt:/java.base");
        // -Xpatch case.
        output.shouldContain("[class,load] javax.naming.spi.NamingManager source: mods" +
            File.separator + "java.naming");
        // -cp case.
        output.shouldContain("[class,load] XpatchMain source: file");

        // Test -XX:+TraceClassLoading output for -Xbootclasspath/a
        source = "package XpatchTraceCL_pkg; "                 +
                 "public class ItIsI { "                          +
                 "    static { "                                  +
                 "        System.out.println(\"I also pass!\"); " +
                 "    } "                                         +
                 "}";

        ClassFileInstaller.writeClassToDisk("XpatchTraceCL_pkg/ItIsI",
             InMemoryJavaCompiler.compile("XpatchTraceCL_pkg.ItIsI", source),
             "xbcp");

        pb = ProcessTools.createJavaProcessBuilder("-Xbootclasspath/a:xbcp",
             "-XX:+TraceClassLoading", "XpatchMain", "XpatchTraceCL_pkg.ItIsI");
        output = new OutputAnalyzer(pb.start());
        // -Xbootclasspath/a case.
        output.shouldContain("[class,load] XpatchTraceCL_pkg.ItIsI source: xbcp");
        output.shouldHaveExitValue(0);
    }
}
