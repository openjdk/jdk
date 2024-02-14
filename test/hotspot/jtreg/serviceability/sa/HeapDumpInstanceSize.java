/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collection;
import java.util.List;
import java.io.File;
import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.hprof.model.JavaClass;
import jdk.test.lib.hprof.model.Snapshot;
import jdk.test.lib.hprof.parser.Reader;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jtreg.SkippedException;

/**
 * @test
 * @bug 8240989
 * @summary Test that heap dumpers (VM and SA) report correct instance size in HPROF_GC_CLASS_DUMP records
 * @requires vm.jvmti
 * @requires vm.hasSA
 * @library /test/lib
 * @run main/othervm HeapDumpInstanceSize
 */

public class HeapDumpInstanceSize {

    private static Snapshot readHeapdump(File file) throws Exception {
        System.out.println("Reading " + file + "...");
        Snapshot snapshot = Reader.readFile(file.getPath(), true, 0);
        System.out.println("Resolving snapshot...");
        snapshot.resolve(true);
        System.out.println("Snapshot resolved.");
        return snapshot;
    }

    private static Snapshot heapdumpSA(long pid, String fileName) throws Exception {
        File dumpFile = new File(fileName);
        ClhsdbLauncher launcher = new ClhsdbLauncher();
        String command = "dumpheap " + fileName;
        List<String> cmds = List.of(command);
        launcher.run(pid, cmds, null, null);
        return readHeapdump(dumpFile);
    }

    private static Snapshot heapdumpVM(long pid, String fileName) throws Exception {
        File dumpFile = new File(fileName);
        // jcmd <pid> GC.heap_dump <file_path>
        JDKToolLauncher launcher = JDKToolLauncher
                .createUsingTestJDK("jcmd")
                .addToolArg(Long.toString(pid))
                .addToolArg("GC.heap_dump")
                .addToolArg(dumpFile.getAbsolutePath());
        OutputAnalyzer oa = ProcessTools.executeProcess(new ProcessBuilder(launcher.getCommand()));
        System.out.println("Output: ");
        System.out.println(oa.getOutput());

        return readHeapdump(dumpFile);
    }

    private static void testClasses(String name, Snapshot snapshot, Snapshot otherSnapshot) {
        System.out.println("Testing " + name + " classes...");
        int cnt = 0;
        // save the last error message to throw an exception
        String errorMsg = null;
        Collection<JavaClass> classes = snapshot.getClasses();
        for (JavaClass cls: classes) {
            JavaClass otherClass = otherSnapshot.findClass(cls.getName());
            int instSize = cls.getInstanceSize();
            if (otherClass == null) {
                // it's ok, just log it
                System.out.println("  - " + cls.getName() + ": only in " + name + " heapdump");
            } else {
                if (instSize != otherClass.getInstanceSize()) {
                    errorMsg = "ERROR " + cls.getName() + " - different instance size: "
                               + instSize + " != " + otherClass.getInstanceSize();
                    System.out.println("  - " + errorMsg);
                }
            }

            if (cls.isArray()) {
                // for arrays instance size should be 0
                if (instSize != 0) {
                    errorMsg = "ERROR " + cls.getName()
                               + " - instance size for array is not 0: " + instSize;
                    System.out.println("  - " + errorMsg);
                }
            } else {
                // non-array should have >0 instance size
                if (instSize == 0) {
                    errorMsg = "ERROR " + cls.getName() + ": instance size is 0";
                    System.out.println("  - " + errorMsg);
                }
            }

            cnt++;
        }
        System.out.println(name + ": found " + cnt + " classes.");
        if (errorMsg != null) {
            throw new RuntimeException(errorMsg);
        }
    }

    public static void main(String[] args) throws Exception {
        LingeredApp theApp = null;
        try {
            theApp = new LingeredApp();
            LingeredApp.startApp(theApp);

            try (Snapshot snapshot1 = heapdumpSA(theApp.getPid(), "sa_heapdump.hprof");
                 Snapshot snapshot2 = heapdumpVM(theApp.getPid(), "vm_heapdump.hprof")) {
                testClasses("SA", snapshot1, snapshot2);
                testClasses("VM", snapshot2, snapshot1);
            }
        } finally {
            LingeredApp.stopApp(theApp);
        }
    }
}
