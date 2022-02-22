/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.TestFramework;
import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethod;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This class represents a test method that is incrementally updated with new information parsed by {@link IREncodingParser}
 * and {@link HotSpotPidFileParser}. Ones the parsers are finished, an {@link IRMethod} object can be fetched from the
 * collected data with {@link TestMethod#createIRMethod()}.
 *
 * @see IREncodingParser
 * @see HotSpotPidFileParser
 * @see IRMethod
 */
public class TestMethod {
    private final Method method;
    private final int[] irRuleIds;
    private final Map<CompilePhase, String> compilationOutputMap;

    public TestMethod(Method m, int[] irRuleIds) {
        this.method = m;
        this.irRuleIds = irRuleIds;
        this.compilationOutputMap = new LinkedHashMap<>(); // Keep order of insertion
    }

    public IRMethod createIRMethod() {
        IR[] irAnnos = method.getAnnotationsByType(IR.class);
        TestFramework.check(irAnnos.length > 0, "must have at least one IR rule");
        TestFramework.check(irRuleIds.length > 0, "must have at least one IR rule");
        return new IRMethod(method, irRuleIds, irAnnos, compilationOutputMap);
    }

    /**
     * We might parse multiple C2 compilations of this method. Only keep the very last one by overriding the outputMap.
     */
    public void setIdealOutput(String idealOutput, CompilePhase compilePhase) {
        String idealOutputWithHeader = "PrintIdeal" + getPhaseNameString(compilePhase) + ":" + System.lineSeparator() + idealOutput;
        compilationOutputMap.put(compilePhase, idealOutputWithHeader);
        if (compilePhase == CompilePhase.PRINT_IDEAL) {
            // Only need to set default output when parsing PrintIdeal output
            compilationOutputMap.put(CompilePhase.DEFAULT, idealOutputWithHeader);
        }
    }

    private String getPhaseNameString(CompilePhase phase) {
        return " - " + phase.getName();
    }

    /**
     * We might parse multiple C2 compilations of this method. Only keep the very last one by overriding the outputMap.
     */
    public void setOptoAssemblyOutput(String optoAssemblyOutput) {
        optoAssemblyOutput = "PrintOptoAssembly:" + System.lineSeparator() + optoAssemblyOutput;
        compilationOutputMap.put(CompilePhase.PRINT_OPTO_ASSEMBLY, optoAssemblyOutput);
        String idealOutput = compilationOutputMap.get(CompilePhase.DEFAULT);
        TestFramework.check(idealOutput != null && !idealOutput.isEmpty(), "must be non-empty");
        compilationOutputMap.put(CompilePhase.DEFAULT, idealOutput + System.lineSeparator() + System.lineSeparator()
                                                       + optoAssemblyOutput);
    }
}
