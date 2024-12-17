/*
 * Copyright (c) 2020 SAP SE. All rights reserved.
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

import java.io.File;
import java.nio.file.Files;
import java.io.IOException;
import java.util.List;

import jdk.test.lib.hprof.HprofParser;
import jdk.test.lib.hprof.parser.Reader;
import jdk.test.lib.hprof.model.Snapshot;

import jdk.test.lib.Asserts;
import jdk.test.lib.dcmd.PidJcmdExecutor;
import jdk.test.lib.process.OutputAnalyzer;

/*
 * @test id=Serial
 * @requires vm.gc.Serial
 * @summary Test of diagnostic command GC.heap_dump with gzipped output (Serial GC)
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.compiler
 *          java.management
 *          jdk.internal.jvmstat/sun.jvmstat.monitor
 * @run main/othervm -XX:+UseSerialGC HeapDumpCompressedTest
 */

/*
 * @test id=Parallel
 * @requires vm.gc.Parallel
 * @summary Test of diagnostic command GC.heap_dump with gzipped output (Parallel GC)
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.compiler
 *          java.management
 *          jdk.internal.jvmstat/sun.jvmstat.monitor
 * @run main/othervm -XX:+UseParallelGC HeapDumpCompressedTest
 */

/*
 * @test id=G1
 * @requires vm.gc.G1
 * @summary Test of diagnostic command GC.heap_dump with gzipped output (G1 GC)
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.compiler
 *          java.management
 *          jdk.internal.jvmstat/sun.jvmstat.monitor
 * @run main/othervm -XX:+UseG1GC HeapDumpCompressedTest
 */

/*
 * @test id=Z
 * @requires vm.gc.Z
 * @summary Test of diagnostic command GC.heap_dump with gzipped output (Z GC)
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.compiler
 *          java.management
 *          jdk.internal.jvmstat/sun.jvmstat.monitor
 * @run main/othervm -XX:+UseZGC HeapDumpCompressedTest
 */

/*
 * @test id=Shenandoah
 * @requires vm.gc.Shenandoah
 * @summary Test of diagnostic command GC.heap_dump with gzipped output (Shenandoah GC)
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.compiler
 *          java.management
 *          jdk.internal.jvmstat/sun.jvmstat.monitor
 * @run main/othervm -XX:+UseShenandoahGC HeapDumpCompressedTest
 */

/*
 * @test id=Epsilon
 * @requires vm.gc.Epsilon
 * @summary Test of diagnostic command GC.heap_dump with gzipped output (Epsilon GC)
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.compiler
 *          java.management
 *          jdk.internal.jvmstat/sun.jvmstat.monitor
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC HeapDumpCompressedTest
 */

public class HeapDumpCompressedTest {
    public static HeapDumpCompressedTest ref;

    public static void main(String[] args) throws Exception {
        PidJcmdExecutor executor = new PidJcmdExecutor();
        ref = new HeapDumpCompressedTest();
        File dump = new File("jcmd.gc.heap_dump." + System.currentTimeMillis() + ".hprof.gz");

        if (dump.exists()) {
            dump.delete();
        }

        // Check we detect an invalid compression level.
        OutputAnalyzer output = executor.execute("GC.heap_dump -gz=0 " +
                                                  dump.getAbsolutePath());
        output.shouldContain("Compression level out of range");

        // Check we can create a gzipped dump.
        output = executor.execute("GC.heap_dump -gz=1 " + dump.getAbsolutePath());
        output.shouldContain("Heap dump file created");

        // Check we detect an already present heap dump.
        output = executor.execute("GC.heap_dump -gz=1 " + dump.getAbsolutePath());
        output.shouldContain("Unable to create ");

        HprofParser.parseAndVerify(dump);
        dump.delete();
    }
}
