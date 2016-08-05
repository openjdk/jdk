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
 * @bug 8040237
 * @library /testlibrary
 * @modules java.base/jdk.internal.misc
 *          java.instrument
 *          java.management
 * @build compiler.profiling.spectrapredefineclass_classloaders.Agent
 *        compiler.profiling.spectrapredefineclass_classloaders.Test
 *        compiler.profiling.spectrapredefineclass_classloaders.A
 *        compiler.profiling.spectrapredefineclass_classloaders.B
 * @run driver ClassFileInstaller compiler.profiling.spectrapredefineclass_classloaders.Agent
 * @run driver compiler.profiling.spectrapredefineclass_classloaders.Launcher
 * @run main/othervm -XX:-TieredCompilation -XX:-BackgroundCompilation
 *                   -XX:-UseOnStackReplacement -XX:TypeProfileLevel=222
 *                   -XX:ReservedCodeCacheSize=3M
 *                   compiler.profiling.spectrapredefineclass_classloaders.Agent
 */
package compiler.profiling.spectrapredefineclass_classloaders;

import jdk.test.lib.JDKToolFinder;

import java.io.File;
import java.io.PrintWriter;

public class Launcher {
    public static void main(String[] args) throws Exception {

        PrintWriter pw = new PrintWriter("MANIFEST.MF");

        pw.println("Agent-Class: " + Launcher.class.getPackage().getName() + ".Agent");
        pw.println("Can-Retransform-Classes: true");
        pw.close();

        ProcessBuilder pb = new ProcessBuilder();
        pb.command(new String[]{JDKToolFinder.getJDKTool("jar"), "cmf", "MANIFEST.MF",
                System.getProperty("test.classes", ".") + "/agent.jar",
                "compiler/profiling/spectrapredefineclass/Agent.class".replace('/', File.separatorChar)});
        pb.start().waitFor();
    }
}
