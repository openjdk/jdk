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
 * @run main/othervm --add-opens=jdk.hotspot.agent/sun.jvm.hotspot.types.basic=ALL-UNNAMED UniqueVtableTest
 */

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
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
import jdk.test.lib.SA.SATestUtils;


public class UniqueVtableTest {

    private HotSpotAgent agent;
    private MethodHandle vtblForType;

    public UniqueVtableTest() {
    }

    private static String type2String(Type t) {
        return t + " (extends " + t.getSuperclass() + ")";
    }

    private static void log(Object o) {
        System.out.println(o);
    }

    private void attach(long pid) throws Throwable {
        agent = new HotSpotAgent();
        log("Attaching to process ID " + pid + "...");
        agent.attach((int) pid);
        log("Attached successfully.");

        // agent.getTypeDataBase() returns HotSpotTypeDataBase (extends BasicTypeDataBase)
        // We need a method from BasicTypeDataBase
        //    Address vtblForType(Type type);

        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodHandles.Lookup classLookup = MethodHandles.privateLookupIn(BasicTypeDataBase.class, lookup);
        vtblForType = classLookup.findVirtual(BasicTypeDataBase.class, "vtblForType",
                 MethodType.methodType(Address.class, Type.class));
    }

    private void detach() {
        if (agent != null) {
            agent.detach();
        }
    }

    private void runTest() throws Throwable {
        Map<Address, List<Type>> types = new HashMap<>();
        Iterator<Type> it = agent.getTypeDataBase().getTypes();
        int dupFound = 0;
        while (it.hasNext()) {
            Type t = it.next();
            Address vtable = (Address) vtblForType.invoke(agent.getTypeDataBase(), t);

            if (vtable != null) {
                List<Type> typeList = types.get(vtable);
                if (typeList == null) {
                    types.put(vtable, new ArrayList<>(List.of(t)));
                } else {
                    // duplicate found
                    dupFound++;
                    typeList.add(t);
                }
            }

            if (vtable == null && t.getSuperclass() != null) {
               log("WARNING: vtable is null for " + type2String(t)
                  + ", CInt: " + t.isCIntegerType()
                  + ", CStr: " + t.isCStringType()
                  + ", JPrimitive: " + t.isJavaPrimitiveType()
                  + ", Oop: " + t.isOopType()
                  + ", Ptr: " + t.isPointerType());
            }
        }

        if (dupFound > 0) {
            types.forEach((vtable, list) -> {
                if (list.size() > 1) {
                    log("Duplicate vtable: " + vtable + ": ");
                    list.forEach(t -> log("  - " + type2String(t)));
                }
            });
            throw new RuntimeException("Duplicate vtable(s) found: " + dupFound);
        }
    }

    private void run() throws Throwable {
        Throwable reasonToFail = null;
        LingeredApp app = null;
        try {
            app = LingeredApp.startApp();
            attach(app.getPid());
            runTest();
        } catch (Throwable ex) {
            reasonToFail = ex;
        } finally {
            try {
                detach();
            } catch (Exception ex) {
                log("detach error:");
                ex.printStackTrace(System.out);
                // do not override original error
                if (reasonToFail != null) {
                    reasonToFail = ex;
                }
            }
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

        UniqueVtableTest test = new UniqueVtableTest();

        test.run();
    }

 }
