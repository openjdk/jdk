/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import org.testng.annotations.Test;
import org.testng.Assert;

import java.io.IOException;

import com.oracle.java.testlibrary.JDKToolFinder;
import com.oracle.java.testlibrary.OutputAnalyzer;
import com.oracle.java.testlibrary.dcmd.CommandExecutor;
import com.oracle.java.testlibrary.dcmd.PidJcmdExecutor;

/*
 * @test
 * @summary Test of diagnostic command GC.heap_dump
 * @library /testlibrary
 * @modules java.base/sun.misc
 *          java.compiler
 *          java.management
 *          jdk.jvmstat/sun.jvmstat.monitor
 * @build com.oracle.java.testlibrary.*
 * @build com.oracle.java.testlibrary.dcmd.*
 * @run testng HeapDumpTest
 */
public class HeapDumpTest {
    protected String heapDumpArgs = "";

    public void run(CommandExecutor executor) {
        String fileName = "jcmd.gc.heap_dump." + System.currentTimeMillis() + ".hprof";
        String cmd = "GC.heap_dump " + heapDumpArgs + " " + fileName;
        executor.execute(cmd);

        verifyHeapDump(fileName);
    }

    private void verifyHeapDump(String fileName) {
        String jhat = JDKToolFinder.getJDKTool("jhat");
        String[] cmd = { jhat, "-parseonly", "true", fileName };

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = null;
        OutputAnalyzer output = null;

        try {
            p = pb.start();
            output = new OutputAnalyzer(p);

            /*
             * Some hprof dumps of all objects contain constantPoolOop references that cannot be resolved, so we ignore
             * failures about resolving constantPoolOop fields using a negative lookahead
             */
            output.shouldNotMatch(".*WARNING(?!.*Failed to resolve object.*constantPoolOop.*).*");
        } catch (IOException e) {
            Assert.fail("Test error: Caught exception while reading stdout/err of jhat", e);
        } finally {
            if (p != null) {
                p.destroy();
            }
        }

        if (output.getExitValue() != 0) {
            Assert.fail("Test error: jhat exit code was nonzero");
        }
    }

    /* GC.heap_dump is not available over JMX, running jcmd pid executor instead */
    @Test
    public void pid() {
        run(new PidJcmdExecutor());
    }
}

