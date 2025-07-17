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

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.util.Map;
import java.util.Set;

/**
 * Agent used by TraceUsageTest. The premain and agentmain methods invoke Instrumentation
 * methods so the usages can be traced by the test.
 */
public class TraceUsageAgent {
    public static void premain(String methodNames, Instrumentation inst) throws Exception {
        test(methodNames, inst);
    }

    public static void agentmain(String methodNames, Instrumentation inst) throws Exception {
        test(methodNames, inst);
    }

    private static void test(String methodNames, Instrumentation inst) throws Exception {
        for (String methodName : methodNames.split(",")) {
            switch (methodName) {
                case "addTransformer" -> {
                    var transformer = new ClassFileTransformer() { };
                    inst.addTransformer(transformer);
                }
                case "retransformClasses" -> {
                    inst.retransformClasses(Object.class);
                }
                case "redefineModule" -> {
                    Module base = Object.class.getModule();
                    inst.redefineModule(base, Set.of(), Map.of(), Map.of(), Set.of(), Map.of());
                }
                default -> {
                    throw new RuntimeException("Unknown method name: " + methodName);
                }
            }
        }
    }
}
