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
import sun.jvm.hotspot.debugger.OopHandle;
import sun.jvm.hotspot.oops.Field;
import sun.jvm.hotspot.oops.InstanceKlass;
import sun.jvm.hotspot.oops.java_lang_Class;
import sun.jvm.hotspot.oops.Klass;
import sun.jvm.hotspot.oops.Oop;
import sun.jvm.hotspot.oops.OopField;
import sun.jvm.hotspot.runtime.CompiledVFrame;
import sun.jvm.hotspot.runtime.StackValueCollection;
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
 *          jdk.hotspot.agent/sun.jvm.hotspot.debugger
 *          jdk.hotspot.agent/sun.jvm.hotspot.oops
 *          jdk.hotspot.agent/sun.jvm.hotspot.runtime
 *          jdk.incubator.vector
 * @build LingeredAppWithVector
 * @run driver TestVectorDebugInfoDecode
 */

public class TestVectorDebugInfoDecode {

    private static final Set<String> ALL_VECTOR_NAME_SET = Set.of(
            "jdk/incubator/vector/ByteVectorMax",
            "jdk/incubator/vector/ShortVectorMax",
            "jdk/incubator/vector/IntVectorMax",
            "jdk/incubator/vector/LongVectorMax",
            "jdk/incubator/vector/FloatVectorMax",
            "jdk/incubator/vector/DoubleVectorMax",
            "jdk/incubator/vector/Float16VectorMax",
            "jdk/incubator/vector/ByteVector512",
            "jdk/incubator/vector/ShortVector512",
            "jdk/incubator/vector/IntVector512",
            "jdk/incubator/vector/LongVector512",
            "jdk/incubator/vector/FloatVector512",
            "jdk/incubator/vector/DoubleVector512",
            "jdk/incubator/vector/Float16Vector512",
            "jdk/incubator/vector/ByteVector256",
            "jdk/incubator/vector/ShortVector256",
            "jdk/incubator/vector/IntVector256",
            "jdk/incubator/vector/LongVector256",
            "jdk/incubator/vector/FloatVector256",
            "jdk/incubator/vector/DoubleVector256",
            "jdk/incubator/vector/Float16Vector256",
            "jdk/incubator/vector/ByteVector128",
            "jdk/incubator/vector/ShortVector128",
            "jdk/incubator/vector/IntVector128",
            "jdk/incubator/vector/LongVector128",
            "jdk/incubator/vector/FloatVector128",
            "jdk/incubator/vector/DoubleVector128",
            "jdk/incubator/vector/Float16Vector128",
            "jdk/incubator/vector/ByteVector64",
            "jdk/incubator/vector/ShortVector64",
            "jdk/incubator/vector/IntVector64",
            "jdk/incubator/vector/LongVector64",
            "jdk/incubator/vector/FloatVector64",
            "jdk/incubator/vector/DoubleVector64",
            "jdk/incubator/vector/Float16Vector64"
    );

    private static final Set<String> MANDATORY_VECTOR_NAME_SET = Set.of(
            "jdk/incubator/vector/ByteVectorMax",
            "jdk/incubator/vector/ShortVectorMax",
            "jdk/incubator/vector/IntVectorMax",
            "jdk/incubator/vector/LongVectorMax",
            "jdk/incubator/vector/FloatVectorMax",
            "jdk/incubator/vector/DoubleVectorMax",
            "jdk/incubator/vector/Float16VectorMax",
            "jdk/incubator/vector/ByteVector128",
            "jdk/incubator/vector/ShortVector128",
            "jdk/incubator/vector/IntVector128",
            "jdk/incubator/vector/LongVector128",
            "jdk/incubator/vector/FloatVector128",
            "jdk/incubator/vector/DoubleVector128",
            "jdk/incubator/vector/Float16Vector128",
            "jdk/incubator/vector/ByteVector64",
            "jdk/incubator/vector/ShortVector64",
            "jdk/incubator/vector/IntVector64",
            "jdk/incubator/vector/FloatVector64",
            "jdk/incubator/vector/Float16Vector64"
    );

    private static final String VECTOR_PAYLOAD = "jdk/internal/vm/vector/VectorSupport$VectorPayload";

    private static LingeredAppWithVector theApp = null;

    private static void checkDecode(String pid) throws Exception {
        HotSpotAgent agent = new HotSpotAgent();

        Set<String> decodedVectorSet = new HashSet<>();
        Set<String> vectorLocationSet = new HashSet<>();

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

                        List<ScopeValue> locals = scope.getLocals();
                        StackValueCollection resolved = compiled.getLocals();

                        for (int j = 0; j < locals.size(); j++) {
                            ScopeValue sv = locals.get(j);

                            if (sv.isObject()) {
                                ObjectValue object = (ObjectValue) sv;
                                Klass k = klassOf(object);
                                if (!isVectorKlass(k)) continue;

                                System.out.println("## ObjectValue:");
                                object.print();
                                System.out.println();

                                String klassName = k.getName().asString();
                                System.out.println(klassName);
                                decodedVectorSet.add(klassName);

                                for (ScopeValue value : object.getFieldsValue()) {
                                    System.out.println("# ScopeValue:");
                                    value.printOn(System.out);
                                    System.out.println();

                                    if (value instanceof LocationValue lv) {
                                        Location loc = lv.getLocation();
                                        if (loc.getType() == Location.Type.VECTOR) {
                                            vectorLocationSet.add(klassName);
                                        }
                                    }
                                }
                            } else if (sv.isLocation()) {
                                Location loc = ((LocationValue) sv).getLocation();
                                if (!loc.holdsOop() && !loc.holdsNarrowOop()) continue;

                                OopHandle h = resolved.oopHandleAt(j);
                                if (h == null) continue;

                                Oop o = VM.getVM().getObjectHeap().newOop(h);
                                if (o == null || !isVectorKlass(o.getKlass())) continue;

                                System.out.println("## Heap Object:");
                                String klassName = o.getKlass().getName().asString();
                                System.out.println(klassName);

                                Field field = ((InstanceKlass) o.getKlass()).findField("payload", "Ljava/lang/Object;");
                                Oop payload = ((OopField) field).getValue(o);
                                if (payload == null) {
                                    throw new RuntimeException("Payload must not be null. klassName: " + klassName);
                                }

                                String payloadKlassName = payload.getKlass().getName().asString();
                                if (!payload.isTypeArray()) {
                                    throw new RuntimeException("Payload must be a primitive array." +
                                            " klassName: " + klassName + ", payload: " + payloadKlassName);
                                }
                                System.out.println("# Payload:");
                                System.out.println(payloadKlassName);

                                decodedVectorSet.add(klassName);
                            }
                        }
                    }
                }
            }
        } finally {
            agent.detach();
        }

        if (!decodedVectorSet.equals(ALL_VECTOR_NAME_SET)) {
            Set<String> missing = new HashSet<>(ALL_VECTOR_NAME_SET);
            missing.removeAll(decodedVectorSet);

            throw new RuntimeException("Missing decoded vectors. missing: " + missing);
        }

        if (!vectorLocationSet.containsAll(MANDATORY_VECTOR_NAME_SET)) {
            Set<String> missing = new HashSet<>(MANDATORY_VECTOR_NAME_SET);
            missing.removeAll(vectorLocationSet);
            Set<String> extra = new HashSet<>(vectorLocationSet);
            extra.removeAll(MANDATORY_VECTOR_NAME_SET);

            throw new RuntimeException("Mandatory vector locations not found." +
                    " missing: " + missing + ", extra: " + extra);
        }
    }

    private static boolean isVectorKlass(Klass k) {
        for (Klass c = k; c != null; c = c.getSuper()) {
            if (c.getName().asString().equals(VECTOR_PAYLOAD)) return true;
        }
        return false;
    }

    private static Klass klassOf(ObjectValue ov) {
        var mirror = (ConstantOopReadValue) ov.getKlass();
        Oop mirrorOop = VM.getVM().getObjectHeap().newOop(mirror.getValue());
        return java_lang_Class.asKlass(mirrorOop);
    }

    private static void createAnotherToAttach(long lingeredAppPid) throws Exception {
        ProcessBuilder processBuilder = ProcessTools.createLimitedTestJavaProcessBuilder(
                "--add-modules=jdk.hotspot.agent",
                "--add-exports=jdk.hotspot.agent/sun.jvm.hotspot=ALL-UNNAMED",
                "--add-exports=jdk.hotspot.agent/sun.jvm.hotspot.code=ALL-UNNAMED",
                "--add-exports=jdk.hotspot.agent/sun.jvm.hotspot.debugger=ALL-UNNAMED",
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
