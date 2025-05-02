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

/**
 * @test
 *
 * @library /test/lib
 * @requires vm.hasSA
 * @modules jdk.hotspot.agent/sun.jvm.hotspot
 *          jdk.hotspot.agent/sun.jvm.hotspot.debugger
 *          jdk.hotspot.agent/sun.jvm.hotspot.types
 *          jdk.hotspot.agent/sun.jvm.hotspot.types.basic
 *
 * @run driver UniqueVtableTest
 */

import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sun.jvm.hotspot.HotSpotAgent;
import sun.jvm.hotspot.debugger.Address;
import sun.jvm.hotspot.types.Type;
import sun.jvm.hotspot.types.basic.BasicTypeDataBase;

import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.SA.SATestUtils;


public class UniqueVtableTest {

    private static String type2String(Type t) {
        return t + " (extends " + t.getSuperclass() + ")";
    }

    private static void log(Object o) {
        System.out.println(o);
    }

    private static void runTest(long pid) throws Throwable {
        HotSpotAgent agent = new HotSpotAgent();
        log("Attaching to process ID " + pid + "...");
        agent.attach((int) pid);
        log("Attached successfully.");

        Throwable reasonToFail = null;

        try {
            runTest(agent);
        } catch (Throwable ex) {
            reasonToFail = ex;
        } finally {
            try {
                agent.detach();
            } catch (Exception ex) {
                log("detach error:");
                ex.printStackTrace(System.out);
                // do not override original error
                if (reasonToFail != null) {
                    reasonToFail = ex;
                }
            }
        }
        if (reasonToFail != null) {
            throw reasonToFail;
        }
    }

    private static void runTest(HotSpotAgent agent) throws Throwable {
        Map<Address, List<Type>> vtableToTypesMap = new HashMap<>();
        Iterator<Type> it = agent.getTypeDataBase().getTypes();
        int dupsFound = 0;
        // TypeDataBase knows nothing about vtables,
        // but actually agent.getTypeDataBase() returns HotSpotTypeDataBase (extends BasicTypeDataBase)
        // and BasicTypeDataBase has a method to get vtable for Types.
        BasicTypeDataBase typeDB = (BasicTypeDataBase)(agent.getTypeDataBase());
        int total = 0;
        int vm_classes_with_vtable = 0;
        int vm_classes_without_vtable = 0;
        while (it.hasNext()) {
            total++;
            Type t = it.next();
            Address vtable = typeDB.vtblForType(t);
            if (vtable != null) {
                vm_classes_with_vtable++;
                List<Type> typeList = vtableToTypesMap.get(vtable);
                if (typeList == null) {
                    vtableToTypesMap.put(vtable, new ArrayList<>(List.of(t)));
                } else {
                    // duplicate found
                    dupsFound++;
                    typeList.add(t);
                }
            }

            // IntegerType/StringType/JavaPrimitiveType/OopType/PointerType types
            // are expected to have no vtable.
            // Log classes which might need vtable.
            if (vtable == null
                    && !t.isCIntegerType()
                    && !t.isCStringType()
                    && !t.isJavaPrimitiveType()
                    && !t.isOopType()
                    && !t.isPointerType()) {
                vm_classes_without_vtable++;
                log("vtable is null for " + type2String(t));
            }
        }
        log("total: " + total
            + ", vm_classes_with_vtable: " + vm_classes_with_vtable
            + ", vm_classes_without_vtable: " + vm_classes_without_vtable);
        if (dupsFound > 0) {
            vtableToTypesMap.forEach((vtable, list) -> {
                if (list.size() > 1) {
                    log("Duplicate vtable: " + vtable + ": ");
                    list.forEach(t -> log("  - " + type2String(t)));
                }
            });
            throw new RuntimeException("Duplicate vtable(s) found: " + dupsFound);
        }
    }

    private static void createAnotherToAttach(long lingeredAppPid) throws Throwable {
        // Start a new process to attach to the lingered app
        ProcessBuilder processBuilder = ProcessTools.createLimitedTestJavaProcessBuilder(
            "--add-modules=jdk.hotspot.agent",
            "--add-exports=jdk.hotspot.agent/sun.jvm.hotspot=ALL-UNNAMED",
            "--add-exports=jdk.hotspot.agent/sun.jvm.hotspot.debugger=ALL-UNNAMED",
            "--add-exports=jdk.hotspot.agent/sun.jvm.hotspot.types=ALL-UNNAMED",
            "--add-exports=jdk.hotspot.agent/sun.jvm.hotspot.types.basic=ALL-UNNAMED",
            "UniqueVtableTest",
            Long.toString(lingeredAppPid));
        SATestUtils.addPrivilegesIfNeeded(processBuilder);
        OutputAnalyzer output = ProcessTools.executeProcess(processBuilder);
        output.shouldHaveExitValue(0);
        System.out.println(output.getOutput());
    }

    private static void runMain() throws Throwable {
        Throwable reasonToFail = null;
        LingeredApp app = null;
        try {
            app = LingeredApp.startApp();
            createAnotherToAttach(app.getPid());
        } catch (Throwable ex) {
            reasonToFail = ex;
        } finally {
            try {
                LingeredApp.stopApp(app);
            } catch (Exception ex) {
                log("LingeredApp.stopApp error:");
                ex.printStackTrace(System.out);
                // do not override original error
                if (reasonToFail != null) {
                    reasonToFail = ex;
                }
            }
        }
        if (reasonToFail != null) {
            throw reasonToFail;
        }
    }

    public static void main(String... args) throws Throwable {
        SATestUtils.skipIfCannotAttach(); // throws SkippedException if attach not expected to work.

        if (args == null || args.length == 0) {
            // Main test process.
            runMain();
        } else {
            // Sub-process to attach, arg[0] is the target process pid.
            runTest(Long.parseLong(args[0]));
        }
    }

 }
