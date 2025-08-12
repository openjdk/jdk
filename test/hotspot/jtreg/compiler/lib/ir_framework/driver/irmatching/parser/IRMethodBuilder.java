/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;
import compiler.lib.ir_framework.driver.irmatching.Compilation;
import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethod;
import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethodMatchable;
import compiler.lib.ir_framework.driver.irmatching.irmethod.NotCompiledIRMethod;
import compiler.lib.ir_framework.driver.irmatching.irmethod.NotCompilableIRMethod;
import compiler.lib.ir_framework.driver.irmatching.parser.hotspot.HotSpotPidFileParser;
import compiler.lib.ir_framework.driver.irmatching.parser.hotspot.LoggedMethod;
import compiler.lib.ir_framework.driver.irmatching.parser.hotspot.LoggedMethods;

import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Class to create {@link IRMethod} instances by combining the elements of {@link TestMethods} and {@link LoggedMethods}.
 */
class IRMethodBuilder {
    private final Map<String, LoggedMethod> loggedMethods;
    private final TestMethods testMethods;
    private final boolean allowNotCompilable;

    public IRMethodBuilder(TestMethods testMethods, LoggedMethods loggedMethods, boolean allowNotCompilable) {
        this.testMethods = testMethods;
        this.loggedMethods = loggedMethods.loggedMethods();
        this.allowNotCompilable = allowNotCompilable;
    }

    /**
     * Create IR methods for all test methods identified by {@link IREncodingParser} by combining them with the parsed
     * compilation output from {@link HotSpotPidFileParser}.
     */
    public SortedSet<IRMethodMatchable> build(VMInfo vmInfo) {
        SortedSet<IRMethodMatchable> irMethods = new TreeSet<>();
        testMethods.testMethods().forEach(
                (methodName, testMethod) -> irMethods.add(createIRMethod(methodName, testMethod, vmInfo)));
        return irMethods;
    }

    private IRMethodMatchable createIRMethod(String methodName, TestMethod testMethod, VMInfo vmInfo) {
        LoggedMethod loggedMethod = loggedMethods.get(methodName);
        if (loggedMethod != null) {
            return new IRMethod(testMethod.method(), testMethod.irRuleIds(), testMethod.irAnnos(),
                                new Compilation(loggedMethod.compilationOutput()), vmInfo);
        } else {
            Test[] testAnnos = testMethod.method().getAnnotationsByType(Test.class);
            boolean allowMethodNotCompilable = allowNotCompilable || testAnnos[0].allowNotCompilable();
            if (allowMethodNotCompilable) {
                return new NotCompilableIRMethod(testMethod.method(), testMethod.irRuleIds().length);
            } else {
                return new NotCompiledIRMethod(testMethod.method(), testMethod.irRuleIds().length);
            }
        }
    }
}
