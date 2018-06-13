/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @test TestHeapDumpOnOutOfMemoryError
 * @summary Test verifies that -XX:HeapDumpOnOutOfMemoryError dump heap when OutOfMemory is thrown in heap
 * @library /test/lib
 * @run driver TestHeapDumpOnOutOfMemoryError run heap
 */

import jdk.test.lib.Asserts;
import jdk.test.lib.classloader.GeneratingClassLoader;
import jdk.test.lib.hprof.HprofParser;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

import java.io.File;

public class TestHeapDumpOnOutOfMemoryError {

    public static final String HEAP_OOME = "heap";
    public static final String METASPACE_OOME = "metaspace";

    public static void main(String[] args) throws Exception {
        if (args.length == 1) {
             try {
                if (args[0].equals(HEAP_OOME)) {
                    Object[] oa = new Object[Integer.MAX_VALUE];
                    for(int i = 0; i < oa.length; i++) {
                        oa[i] = new Object[Integer.MAX_VALUE];
                    }
                } else {
                    GeneratingClassLoader loader = new GeneratingClassLoader();
                    for (int i = 0; ; i++) {
                        loader.loadClass(loader.getClassName(i));
                    }
                }
                throw new Error("OOME not triggered");
            } catch (OutOfMemoryError err) {
                return;
            }
        }
        test(args[1]);
    }

    static void test(String type) throws Exception {
        String heapdumpFilename = type + ".hprof";
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder("-XX:+HeapDumpOnOutOfMemoryError",
                "-XX:HeapDumpPath=" + heapdumpFilename, "-XX:MaxMetaspaceSize=64m",
                TestHeapDumpOnOutOfMemoryError.class.getName(), type);

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.stdoutShouldNotBeEmpty();
        output.shouldContain("Dumping heap to " + type + ".hprof");
        File dump = new File(heapdumpFilename);
        Asserts.assertTrue(dump.exists() && dump.isFile(),
                "Could not find dump file " + dump.getAbsolutePath());

        HprofParser.parse(new File(heapdumpFilename));
        System.out.println("PASSED");
    }

}
