/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @test TestOnErrorWithSelfAttachingJCmd
 * @bug 8273608
 * @summary Test OnError commands can utilize jcmd %p to attach to itself.
 *
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run main/othervm TestOnErrorWithSelfAttachingJCmd
 */
import java.nio.ByteBuffer;
import java.util.LinkedList;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.JDKToolFinder;

public class TestOnErrorWithSelfAttachingJCmd {
    private static final int  BUFF_SIZE = 1024 * 1024; // 1MB

    public static void main(String[] args) throws Exception {
        if (args.length == 1) {
            LinkedList<Object> list = new LinkedList<Object>();
            while (true) {
                Object item = ByteBuffer.allocateDirect(BUFF_SIZE);
                list.add(item);
            }
        }

        // Both Thread.print and GC.thread_dump require safepoint synchronization.
        String jcmd = JDKToolFinder.getJDKTool("jcmd");
        String before = jcmd + " %p Thread.print";
        String after = jcmd + " %p GC.heap_dump a.hprof";

        String msg = "Test Succeeded";
        String cmds = before + ";echo " + msg + ";" + after;

        ProcessBuilder pb_single = ProcessTools.createJavaProcessBuilder(
           "-XX:MaxDirectMemorySize=10M",
           "-Xmx10M",
           "-XX:+UnlockDiagnosticVMOptions",
           "-XX:AbortVMOnException=java.lang.OutOfMemoryError",
           "-XX:OnError=" + cmds,
           TestOnErrorWithSelfAttachingJCmd.class.getName(),
           "throwOOME");

        OutputAnalyzer output_single = new OutputAnalyzer(pb_single.start());

        // Actual output should look like this:
        //   #
        //   # A fatal error has been detected by the Java Runtime Environment:
        //   #
        //   #  Internal Error (/home/xxinliu/Devel/jdk/src/hotspot/share/utilities/exceptions.cpp:541), pid=36786, tid=36787
        //   #  fatal error: Saw java.lang.OutOfMemoryError, aborting
        //   ...
        //   # -XX:OnError="echo Test1 Succeeded"
        //   #   Executing /bin/sh -c "echo Test1 Succeeded" ...
        output_single.shouldContain("Saw java.lang.OutOfMemoryError, aborting");
        // before
        output_single.stdoutShouldMatch("^Full thread dump");
        // echo $msg
        output_single.stdoutShouldMatch("^" + msg); // match start of line only
        // after
        output_single.stdoutShouldContain("Heap dump file created");

        System.out.println("PASSED");
    }
}
