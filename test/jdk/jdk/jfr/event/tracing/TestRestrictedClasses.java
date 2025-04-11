/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.event.tracing;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import jdk.jfr.FlightRecorder;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

/**
 * @test
 * @summary Tests that restricted classes cannot be timed or traced.
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm jdk.jfr.event.tracing.TestRestrictedClasses
 **/
public class TestRestrictedClasses {

    public static void main(String... args) throws Exception {
        testJdkJfr();
        testConcurrentHashMap();
        testConcurrentHashMapNode();
        testAtomicLong();
    }

    private static void testJdkJfr() throws Exception {
        testDebug(FlightRecorder.class.getName(), null);
    }

    private static void testConcurrentHashMapNode() throws Exception {
        testDebug(ConcurrentHashMap.class.getName() + "$Node", "Risk of recursion, skipping bytecode generation for java.util.concurrent.ConcurrentHashMap$Node");
    }

    private static void testConcurrentHashMap() throws Exception {
        testDebug(ConcurrentHashMap.class.getName(), "Risk of recursion, skipping bytecode generation for java.util.concurrent.ConcurrentHashMap");
    }

    private static void testAtomicLong() throws Exception {
        testDebug(AtomicLong.class.getName(), "Risk of recursion, skipping bytecode generation for java.util.concurrent.atomic.AtomicLong");
    }

    private static void testDebug(String clazz, String expected) throws Exception {
        List<String> cmds = new ArrayList<>();
        cmds.add("-Xlog:jfr+methodtrace=debug");
        cmds.add("-XX:StartFlightRecording:method-trace=" + clazz);
        cmds.add("-version");
        OutputAnalyzer out = ProcessTools.executeTestJava(cmds);
        out.shouldHaveExitValue(0);
        if (expected != null) {
            out.shouldContain(expected);
        }
        // Check that bytecode was not generated
        out.shouldNotMatch("Bytecode generation");
    }
}
