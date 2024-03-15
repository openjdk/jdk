/*
 * Copyright (c) 2024 SAP SE. All rights reserved.
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

/**
 * @test
 * @summary Test jcmd GC.heap_dump without path option; path is set via HeapDumpPath
 * @library /test/lib
 * @run main/othervm -XX:HeapDumpPath=testjcmd.hprof HeapDumpJcmdPresetPathTest
 */

import java.io.File;

import jdk.test.lib.Asserts;
import jdk.test.lib.dcmd.PidJcmdExecutor;
import jdk.test.lib.process.OutputAnalyzer;

public class HeapDumpJcmdPresetPathTest {

    public static void main(String[] args) throws Exception {
        PidJcmdExecutor executor = new PidJcmdExecutor();
        OutputAnalyzer output = executor.execute("GC.heap_dump");
        output.shouldContain("Dumping heap to testjcmd.hprof");
        output.shouldContain("Heap dump file created");

        Asserts.assertTrue(new File("testjcmd.hprof").exists());
    }
}
