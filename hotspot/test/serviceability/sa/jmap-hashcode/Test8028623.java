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
 * @bug 8028623
 * @summary Test hashing of extended characters in Serviceability Agent.
 * @library /testlibrary
 * @build com.oracle.java.testlibrary.*
 * @compile -encoding utf8 Test8028623.java
 * @run main Test8028623
 */

import com.oracle.java.testlibrary.JDKToolLauncher;
import com.oracle.java.testlibrary.OutputBuffer;
import com.oracle.java.testlibrary.ProcessTools;

import java.io.File;

public class Test8028623 {

  public static int Ã = 1;
  public static String dumpFile = "heap.out";

  public static void main (String[] args) {

    System.out.println(Ã);

    try {
        int pid = ProcessTools.getProcessId();
        JDKToolLauncher jmap = JDKToolLauncher.create("jmap")
                                              .addToolArg("-F")
                                              .addToolArg("-dump:live,format=b,file=" + dumpFile)
                                              .addToolArg(Integer.toString(pid));
        ProcessBuilder pb = new ProcessBuilder(jmap.getCommand());
        OutputBuffer output = ProcessTools.getOutput(pb);
        Process p = pb.start();
        int e = p.waitFor();
        System.out.println("stdout:");
        System.out.println(output.getStdout());
        System.out.println("stderr:");
        System.out.println(output.getStderr());

        if (e != 0) {
            throw new RuntimeException("jmap returns: " + e);
        }
        if (! new File(dumpFile).exists()) {
            throw new RuntimeException("dump file NOT created: '" + dumpFile + "'");
        }
    } catch (Throwable t) {
        t.printStackTrace();
        throw new RuntimeException("Test failed with: " + t);
    }
  }
}
