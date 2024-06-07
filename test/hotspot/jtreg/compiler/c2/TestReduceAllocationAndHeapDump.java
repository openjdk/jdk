/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8319784
 * @summary Check that the JVM is able to dump the heap even when there are ReduceAllocationMerge in the scope.
 * @library /test/lib /
 * @run main/othervm compiler.c2.TestReduceAllocationAndHeapDump
 */

package compiler.c2;

import java.io.File;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestReduceAllocationAndHeapDump {
    public static void main(String[] args) throws Exception {
        File dumpDirectory = new File("dumps");

        try {
            if (!dumpDirectory.exists()) {
                dumpDirectory.mkdir();
            }

            String[] dumperArgs = {
                "-server",
                "-XX:CompileThresholdScaling=0.01",
                "-XX:+HeapDumpAfterFullGC",
                "-XX:HeapDumpPath=" + dumpDirectory.getAbsolutePath(),
                "-XX:CompileCommand=compileonly,compiler.c2.HeapDumper::testIt",
                "-XX:CompileCommand=exclude,compiler.c2.HeapDumper::dummy",
                HeapDumper.class.getName()
            };

            ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(dumperArgs);
            Process p = pb.start();
            OutputAnalyzer out = new OutputAnalyzer(p);

            if (out.getExitValue() != 0) {
                throw new IllegalStateException("Subprocess finished with non-zero exit code.");
            }
        } finally {
            File[] files = dumpDirectory.listFiles((dir, name) -> name.endsWith(".hprof"));

            for (File file : files) {
                System.out.println("Deleting " + file.getAbsolutePath());
                file.delete();
            }
        }
    }
}

class HeapDumper {
    public static Point p = new Point(0);

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < 5000; i++) {
            testIt(i);
        }
    }

    public static void testIt(int i) throws Exception {
        Point p = (i % 2 == 0) ? new Point(i) : new Point(i);

        dummy(i);

        if (i < 5000) {
            dummy(i);
        } else {
            dummy(p.x + i);
        }
    }

    public static void dummy(int x) {
        if (x > 4900) {
            System.gc();
        }
    }
}

// Helper class
class Point {
    public int x;

    public Point(int xx) {
        this.x = xx;
    }
}
