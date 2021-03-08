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
import java.io.RandomAccessFile;
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
 * @test
 * @summary Test of diagnostic command GC.heap_dump with -overwrite or -stream
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.compiler
 *          java.management
 *          jdk.internal.jvmstat/sun.jvmstat.monitor
 * @run main/othervm HeapDumpOverwriteStreamTest
 */

public class HeapDumpOverwriteStreamTest {
    public static HeapDumpOverwriteStreamTest ref;

    public static void main(String[] args) throws Exception {
        PidJcmdExecutor executor = new PidJcmdExecutor();
        ref = new HeapDumpOverwriteStreamTest();
        File dump = new File("jcmd.gc.heap_dump." + System.currentTimeMillis() + ".hprof.gz");
        String path = dump.getAbsolutePath();

        if (dump.exists()) {
            dump.delete();
        }

        // Check we can throw an error if -overwrite and -stream are
        // both present
        OutputAnalyzer output = executor.execute("GC.heap_dump -overwrite -stream " + path);
        output.shouldContain("Cannot specify -overwrite and -stream simultaneously.");

        // Check we can create a new dump file with -overwrite
        output = executor.execute("GC.heap_dump -overwrite " + path);
        output.shouldContain("Heap dump file created");
        verifyHeapDump(dump);

        // Check we can overwrite the file. Create a larger file first, so we
        // see if it is completely replaced.
        long len = dump.length();
        Asserts.assertTrue(dump.delete());
        RandomAccessFile file = new RandomAccessFile(dump, "rw");
        file.seek(len * 2);
        file.writeByte(0);
        file.close();
        output = executor.execute("GC.heap_dump -overwrite " + path);
        output.shouldContain("Heap dump file created");
        Asserts.assertLT(dump.length(), 2 * len + 1);
        verifyHeapDump(dump);
        dump.delete();

        // Check that we don't create a new file when using -stream
        output = executor.execute("GC.heap_dump -stream " + path);
        output.shouldContain("Unable to stream to ");

        // Check that we write to a file when using -stream.
        file = new RandomAccessFile(dump, "rw");
        file.close();
        output = executor.execute("GC.heap_dump -stream " + path);
        output.shouldContain("Heap dump file created");
        verifyHeapDump(dump);
    }

    private static void verifyHeapDump(File dump) throws Exception {

        Asserts.assertTrue(dump.exists() && dump.isFile(),
                           "Could not create dump file " + dump.getAbsolutePath());

        try {
            File out = HprofParser.parse(dump);

            Asserts.assertTrue(out != null && out.exists() && out.isFile(),
                               "Could not find hprof parser output file");
            List<String> lines = Files.readAllLines(out.toPath());
            Asserts.assertTrue(lines.size() > 0, "hprof parser output file is empty");
            for (String line : lines) {
                Asserts.assertFalse(line.matches(".*WARNING(?!.*Failed to resolve " +
                                                 "object.*constantPoolOop.*).*"));
            }

            out.delete();
        } catch (Exception e) {
            e.printStackTrace();
            Asserts.fail("Could not parse dump file " + dump.getAbsolutePath());
        }
    }
}

