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
 * @bug 8130399
 * @summary Make sure -Xpatch works for java.base.
 * @library /testlibrary
 * @compile XpatchMain.java
 * @run main XpatchJavaBase
 */

import jdk.test.lib.*;

public class XpatchJavaBase {

    public static void main(String[] args) throws Exception {
        String source = "package java.lang; "                       +
                        "public class NewClass { "                  +
                        "    static { "                             +
                        "        System.out.println(\"I pass!\"); " +
                        "    } "                                    +
                        "}";

        ClassFileInstaller.writeClassToDisk("java/lang/NewClass",
             InMemoryJavaCompiler.compile("java.lang.NewClass", source, "-Xmodule:java.base"),
             "mods/java.base");

        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder("-Xpatch:java.base=mods/java.base",
             "XpatchMain", "java.lang.NewClass");

        new OutputAnalyzer(pb.start())
            .shouldContain("I pass!")
            .shouldHaveExitValue(0);
    }
}
