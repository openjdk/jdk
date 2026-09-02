/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.util.stream.Collectors;

import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.SA.SATestUtils;
import jdk.test.lib.Utils;
import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.hprof.HprofParser;
import jdk.test.lib.hprof.model.JavaClass;
import jdk.test.lib.hprof.model.JavaHeapObject;
import jdk.test.lib.hprof.model.Root;
import jdk.test.lib.hprof.model.Snapshot;
import jdk.test.lib.hprof.parser.Reader;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

/**
 * @test
 * @bug 8261848
 * @summary an object referenced only from an unmounted virtual thread's stack has a root in a jhsdb heap dump
 * @requires vm.hasSA
 * @requires vm.continuations
 * @requires vm.gc != "Z"
 * @library /test/lib
 * @run driver TestHeapDumpForUnmountedVirtualThread
 */
public class TestHeapDumpForUnmountedVirtualThread {

    private static final String REFERENCED_CLASS = "LingeredAppWithUnmountedVirtualThread$ChunkReferenced";

    private static LingeredAppWithUnmountedVirtualThread theApp = null;

    private static void attachDumpAndVerify(String heapDumpFileName,
                                            long lingeredAppPid) throws Exception {
        JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK("jhsdb");
        launcher.addVMArgs(Utils.getTestJavaOpts());
        launcher.addToolArg("jmap");
        launcher.addToolArg("--binaryheap");
        launcher.addToolArg("--dumpfile");
        launcher.addToolArg(heapDumpFileName);
        launcher.addToolArg("--pid");
        launcher.addToolArg(Long.toString(lingeredAppPid));

        ProcessBuilder processBuilder = SATestUtils.createProcessBuilder(launcher);
        System.out.println(
            processBuilder.command().stream().collect(Collectors.joining(" ")));
        OutputAnalyzer SAOutput = ProcessTools.executeProcess(processBuilder);
        SAOutput.shouldHaveExitValue(0);
        SAOutput.shouldContain("heap written to");
        SAOutput.shouldContain(heapDumpFileName);
        System.out.println(SAOutput.getOutput());

        File dumpFile = new File(heapDumpFileName);
        HprofParser.parseAndVerify(dumpFile);
        try (Snapshot snapshot = Reader.readFile(dumpFile.getPath(), true, 0)) {
            snapshot.resolve(true);
            checkRooted(snapshot, REFERENCED_CLASS);
        }
    }

    // The object lives only in a local of the parked virtual thread, so its
    // root has to come from that thread's stack chunk.
    private static void checkRooted(Snapshot snapshot, String className) {
        JavaClass jClass = snapshot.findClass(className);
        if (jClass == null) {
            throw new RuntimeException("'" + className + "' not found");
        }
        int instanceCount = jClass.getInstancesCount(false);
        if (instanceCount != 1) {
            throw new RuntimeException("Expected 1 instance, " + instanceCount + " instances found");
        }
        JavaHeapObject heapObj = jClass.getInstances(false).nextElement();
        Root root = heapObj.getRoot();
        if (root == null) {
            throw new RuntimeException("No root for " + className + " instance");
        }
        System.out.println("root: " + root.getDescription() + " referrer: " + root.getReferrer());
    }

    public static void main(String... args) throws Exception {
        SATestUtils.skipIfCannotAttach();
        String heapDumpFileName = "vthreadHeapDump.bin";
        File heapDumpFile = new File(heapDumpFileName);
        if (heapDumpFile.exists()) {
            heapDumpFile.delete();
        }
        try {
            theApp = new LingeredAppWithUnmountedVirtualThread();
            LingeredApp.startApp(theApp, "-XX:+UsePerfData", "-Xmx512m");
            attachDumpAndVerify(heapDumpFileName, theApp.getPid());
        } finally {
            LingeredApp.stopApp(theApp);
        }
    }
}
