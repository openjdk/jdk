/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

/* @test TestG1ZeroPGCTJcmdThreadPrint
 * @key gc
 * @bug 8005875
 * @summary Use jcmd to generate a thread dump of a Java program being run with PGCT=0 to verify 8005875
 * @library /testlibrary
 * @run main/othervm -XX:+UseG1GC -XX:ParallelGCThreads=0 -XX:+IgnoreUnrecognizedVMOptions TestG1ZeroPGCTJcmdThreadPrint
 */

import com.oracle.java.testlibrary.*;

public class TestG1ZeroPGCTJcmdThreadPrint {
  public static void main(String args[]) throws Exception {

    // Grab the pid from the current java process
    String pid = Integer.toString(ProcessTools.getProcessId());

    // Create a ProcessBuilder
    ProcessBuilder pb = new ProcessBuilder();

    // Run jcmd <pid> Thread.print
    pb.command(JDKToolFinder.getJDKTool("jcmd"), pid, "Thread.print");

    OutputAnalyzer output = new OutputAnalyzer(pb.start());

    // There shouldn't be a work gang for concurrent marking.
    output.shouldNotContain("G1 Parallel Marking Threads");

    // Make sure we didn't crash
    output.shouldHaveExitValue(0);
  }
}
