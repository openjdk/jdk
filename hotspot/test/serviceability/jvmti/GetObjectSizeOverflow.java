/*
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved.
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
import java.io.PrintWriter;
import com.oracle.java.testlibrary.*;

/*
 * Test to verify GetObjectSize does not overflow on a 600M element int[]
 *
 * @test
 * @bug 8027230
 * @library /testlibrary
 * @build GetObjectSizeOverflowAgent
 * @run main ClassFileInstaller GetObjectSizeOverflowAgent
 * @run main GetObjectSizeOverflow
 */
public class GetObjectSizeOverflow {
    public static void main(String[] args) throws Exception  {

        if (!Platform.is64bit()) {
            System.out.println("Test needs a 4GB heap and can only be run as a 64bit process, skipping.");
            return;
        }

        PrintWriter pw = new PrintWriter("MANIFEST.MF");
        pw.println("Premain-Class: GetObjectSizeOverflowAgent");
        pw.close();

        ProcessBuilder pb = new ProcessBuilder();
        pb.command(new String[] { JDKToolFinder.getJDKTool("jar"), "cmf", "MANIFEST.MF", "agent.jar", "GetObjectSizeOverflowAgent.class"});
        pb.start().waitFor();

        ProcessBuilder pt = ProcessTools.createJavaProcessBuilder(true, "-Xmx4000m", "-javaagent:agent.jar",  "GetObjectSizeOverflowAgent");
        OutputAnalyzer output = new OutputAnalyzer(pt.start());

        if (output.getStdout().contains("Could not reserve enough space") || output.getStderr().contains("java.lang.OutOfMemoryError")) {
            System.out.println("stdout: " + output.getStdout());
            System.out.println("stderr: " + output.getStderr());
            System.out.println("Test could not reserve or allocate enough space, skipping");
            return;
        }

        output.stdoutShouldContain("GetObjectSizeOverflow passed");
    }
}
