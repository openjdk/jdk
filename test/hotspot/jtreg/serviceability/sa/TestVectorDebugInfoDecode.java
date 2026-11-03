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

import sun.jvm.hotspot.HotSpotAgent;
import sun.jvm.hotspot.code.ConstantOopReadValue;
import sun.jvm.hotspot.code.Location;
import sun.jvm.hotspot.code.LocationValue;
import sun.jvm.hotspot.code.ObjectValue;
import sun.jvm.hotspot.code.ScopeDesc;
import sun.jvm.hotspot.code.ScopeValue;
import sun.jvm.hotspot.oops.java_lang_Class;
import sun.jvm.hotspot.runtime.CompiledVFrame;
import sun.jvm.hotspot.runtime.VM;

import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.SA.SATestUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @test
 * @bug 8394577
 * @summary Test SA debug info decoding with Vector API locations in a compiled frame
 * @requires vm.hasSA
 * @requires vm.compiler2.enabled
 * @requires os.simpleArch == "x64" | os.arch == "aarch64"
 * @requires vm.opt.final.MaxVectorSize == null | vm.opt.final.MaxVectorSize >= 16
 * @library /test/lib
 * @modules jdk.hotspot.agent/sun.jvm.hotspot
 *          jdk.hotspot.agent/sun.jvm.hotspot.code
 *          jdk.hotspot.agent/sun.jvm.hotspot.oops
 *          jdk.hotspot.agent/sun.jvm.hotspot.runtime
 *          jdk.incubator.vector
 * @build LingeredAppWithVector
 * @run driver TestVectorDebugInfoDecode
 */

public class TestVectorDebugInfoDecode {

    private static final Set<String> EXPECTED_EXTERNAL_NAME_SET = Set.of(
            "jdk/incubator/vector/ByteVector128",
            "jdk/incubator/vector/ShortVector128",
            "jdk/incubator/vector/IntVector128",
            "jdk/incubator/vector/LongVector128",
            "jdk/incubator/vector/FloatVector128",
            "jdk/incubator/vector/DoubleVector128",
            "jdk/incubator/vector/ByteVector64",
            "jdk/incubator/vector/ShortVector64",
            "jdk/incubator/vector/IntVector64",
            "jdk/incubator/vector/FloatVector64"
    );

    private static LingeredAppWithVector theApp = null;

    private static void checkDecode(String pid) throws Exception {
        HotSpotAgent agent = new HotSpotAgent();
        Set<String> externalNameSet = new HashSet<>();

        try {
            agent.attach(Integer.parseInt(pid));

            // Find the "main" thread, and find the compiled "test" frame.
            var threads = VM.getVM().getThreads();
            for (int i = 0; i < threads.getNumberOfThreads(); i++) {
                var thread = threads.getJavaThreadAt(i);

                if (!thread.getThreadName().equals("main")) continue;

                for (var frame = thread.getLastJavaVFrameDbg(); frame != null; frame = frame.javaSender()) {
                    if (frame instanceof CompiledVFrame compiled &&
                            compiled.getMethod().getName().asString().equals("test")) {
                        ScopeDesc scope = compiled.getScope();
                        System.out.println("### ScopeDesc:");
                        scope.printValueOn(System.out);
                        System.out.println();

                        List<ObjectValue> objects = scope.getObjects();

                        if (objects == null) continue;

                        for (ObjectValue object : objects) {
                            System.out.println("## ObjectValue:");
                            object.print();
                            System.out.println();

                            System.out.println("## -> ExternalName:");
                            var mirror = (ConstantOopReadValue) object.getKlass();
                            var objectClass = VM.getVM().getObjectHeap().newOop(mirror.getValue());
                            var externalName = java_lang_Class.asExternalName(objectClass);
                            System.out.println(externalName);

                            for (ScopeValue value : object.getFieldsValue()) {
                                System.out.println("# ScopeValue:");
                                value.printOn(System.out);
                                System.out.println();

                                if (value instanceof LocationValue lv) {
                                    Location loc = lv.getLocation();
                                    if (loc.getType() == Location.Type.VECTOR) {
                                        externalNameSet.add(externalName);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            agent.detach();
        }

        if (!externalNameSet.equals(EXPECTED_EXTERNAL_NAME_SET)) {
            Set<String> missing = new HashSet<>(EXPECTED_EXTERNAL_NAME_SET);
            missing.removeAll(externalNameSet);
            Set<String> unexpected = new HashSet<>(externalNameSet);
            unexpected.removeAll(EXPECTED_EXTERNAL_NAME_SET);

            throw new RuntimeException("missing: " + missing + ", unexpected: " + unexpected);
        }
    }

    private static void createAnotherToAttach(long lingeredAppPid) throws Exception {
        ProcessBuilder processBuilder = ProcessTools.createLimitedTestJavaProcessBuilder(
                "--add-modules=jdk.hotspot.agent",
                "--add-exports=jdk.hotspot.agent/sun.jvm.hotspot=ALL-UNNAMED",
                "--add-exports=jdk.hotspot.agent/sun.jvm.hotspot.code=ALL-UNNAMED",
                "--add-exports=jdk.hotspot.agent/sun.jvm.hotspot.oops=ALL-UNNAMED",
                "--add-exports=jdk.hotspot.agent/sun.jvm.hotspot.runtime=ALL-UNNAMED",
                "TestVectorDebugInfoDecode",
                Long.toString(lingeredAppPid)
        );
        SATestUtils.addPrivilegesIfNeeded(processBuilder);
        OutputAnalyzer SAOutput = ProcessTools.executeProcess(processBuilder);
        SAOutput.shouldHaveExitValue(0);
        System.out.println(SAOutput.getOutput());
    }

    public static void main(String[] args) throws Exception {
        SATestUtils.skipIfCannotAttach();

        if (args == null || args.length == 0) {
            try {
                theApp = new LingeredAppWithVector();
                LingeredApp.startApp(theApp,
                        "-XX:-TieredCompilation",
                        "-Xbatch",
                        "--add-modules=jdk.incubator.vector",
                        "-XX:CompileCommand=compileonly," + LingeredAppWithVector.class.getName() + "::test",
                        "-XX:CompileCommand=dontinline," + LingeredAppWithVector.class.getName() + "::checkpoint"
                );

                createAnotherToAttach(theApp.getPid());
            } finally {
                LingeredApp.stopApp(theApp);
            }
        } else {
            checkDecode(args[0]);
        }
    }
}
