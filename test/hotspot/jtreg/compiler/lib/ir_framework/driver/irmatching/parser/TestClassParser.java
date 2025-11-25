/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework.driver.irmatching.parser;

import compiler.lib.ir_framework.driver.irmatching.Matchable;
import compiler.lib.ir_framework.driver.irmatching.NonIRTestClass;
import compiler.lib.ir_framework.driver.irmatching.TestClass;
import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethod;
import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethodMatchable;
import compiler.lib.ir_framework.driver.irmatching.parser.hotspot.HotSpotPidFileParser;
import compiler.lib.ir_framework.driver.irmatching.parser.hotspot.LoggedMethods;
import compiler.lib.ir_framework.shared.TestFormat;

import java.util.SortedSet;

/**
 * Class to parse the ideal compile phase and PrintOptoAssembly outputs of the test class and store them into a
 * collection of dedicated IRMethod objects used throughout IR matching.
 *
 * @see IRMethod
 */
public class TestClassParser {
    private final Class<?> testClass;
    private final boolean allowNotCompilable;

    public TestClassParser(Class<?> testClass, boolean allowNotCompilable) {
        this.testClass = testClass;
        this.allowNotCompilable = allowNotCompilable;
    }

    /**
     * Parse the IR encoding and hotspot_pid* file to create a collection of {@link IRMethod} objects.
     * Return a default/empty TestClass object if there are no applicable @IR rules in any method of the test class.
     */
    public Matchable parse(String hotspotPidFileName, String irEncoding) {
        IREncodingParser irEncodingParser = new IREncodingParser(testClass);
        TestMethods testMethods = irEncodingParser.parse(irEncoding);
        VMInfo vmInfo = VMInfoParser.parseVMInfo(irEncoding);
        if (testMethods.hasTestMethods()) {
            HotSpotPidFileParser hotSpotPidFileParser = new HotSpotPidFileParser(testClass.getName(), testMethods);
            LoggedMethods loggedMethods = hotSpotPidFileParser.parse(hotspotPidFileName);
            return createTestClass(testMethods, loggedMethods, vmInfo);
        }
        return new NonIRTestClass();
    }

    /**
     * Create test class with IR methods for all test methods identified by {@link IREncodingParser} by combining them
     * with the parsed compilation output from {@link HotSpotPidFileParser}.
     */
    private Matchable createTestClass(TestMethods testMethods, LoggedMethods loggedMethods, VMInfo vmInfo) {
        IRMethodBuilder irMethodBuilder = new IRMethodBuilder(testMethods, loggedMethods, allowNotCompilable);
        SortedSet<IRMethodMatchable> irMethods = irMethodBuilder.build(vmInfo);
        TestFormat.throwIfAnyFailures();
        return new TestClass(irMethods);
    }

}

