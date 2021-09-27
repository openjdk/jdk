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
 * @test TestOutOfMemoryErrorFromNIO
 * @summary Test OutOfMemoryError thrown from NIO. OnError will react to OOME.
 *     After we transition the current thread into Native, OnError allows jcmd to itself.
 *
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run main/othervm TestOutOfMemoryErrorFromNIO
 * @bug 8155004 8273608
 */
import java.nio.ByteBuffer;
import java.util.LinkedList;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.JDKToolFinder;

public class TestOutOfMemoryErrorFromNIO {
    private static final int  BUFF_SIZE = 10 * 1024 * 1024; // 10MB

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
        StringBuilder before = new StringBuilder(jcmd);
        before.append(" %p");
        before.append(" Thread.print");
        StringBuilder after = new StringBuilder(jcmd);
        after.append(" %p");
        after.append(" GC.heap_dump a.hprof");

        String msg = "Test Succeeded";
        String cmds = before.toString() + ";echo " + msg + ";"
                    + after.toString();

        // else this is the main test
        ProcessBuilder pb_single = ProcessTools.createJavaProcessBuilder(
           "-XX:MaxDirectMemorySize=100M",
           "-XX:+UnlockDiagnosticVMOptions",
           "-XX:AbortVMOnException=java.lang.OutOfMemoryError",
           "-XX:OnError=" + cmds,
           TestOutOfMemoryErrorFromNIO.class.getName(),
           "throwOOME");

        OutputAnalyzer output_single = new OutputAnalyzer(pb_single.start());

        /* Actual output should look like this:
           #
           # A fatal error has been detected by the Java Runtime Environment:
           #
           #  Internal Error (/home/xxinliu/Devel/jdk/src/hotspot/share/utilities/exceptions.cpp:541), pid=36786, tid=36787
           #  fatal error: Saw java.lang.OutOfMemoryError, aborting
           ...
           # -XX:OnError="echo Test1 Succeeded"
           #   Executing /bin/sh -c "echo Test1 Succeeded" ...
        */
        output_single.shouldContain("Saw java.lang.OutOfMemoryError, aborting");
        output_single.stdoutShouldMatch("^" + msg); // match start of line only

        System.out.println("PASSED");
    }
}
