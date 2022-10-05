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

package compiler.lib.ir_framework.driver.irmatching.report;

import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.driver.irmatching.MatchResult;
import compiler.lib.ir_framework.driver.irmatching.TestClassMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethod;
import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethodMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irmethod.MethodNotCompiledResult;
import compiler.lib.ir_framework.driver.irmatching.irrule.IRRuleMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irrule.phase.CompilePhaseIRRuleMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irrule.phase.NoCompilePhaseCompilationResult;
import compiler.lib.ir_framework.driver.irmatching.visitor.MatchResultVisitor;

import java.lang.reflect.Method;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * This class collects the compilation output of each compile phase that was part of an IR matching failure by visiting
 * each match result element. Multiple compile phase compilation outputs for a single method are collected in the same
 * order as specified in {@link CompilePhase}.
 */
public class CompilationOutputBuilder implements MatchResultVisitor {
    private final StringBuilder msg;
    private final MatchResult testClassMatchResult;
    private final SortedMap<CompilePhase, String> failedCompilePhases = new TreeMap<>();
    private int methodIndex;
    /**
     * Number of collected distinct compile phases.
     */
    private int compilePhaseCount = 0;
    private IRMethod irMethod;

    public CompilationOutputBuilder(MatchResult testClassMatchResult) {
        this.msg = new StringBuilder();
        this.testClassMatchResult = testClassMatchResult;
    }

    @Override
    public void visit(TestClassMatchResult testClassMatchResult) {
        testClassMatchResult.acceptChildren(this);
        StringBuilder builder = new StringBuilder();
        builder.append("Compilation");
        if (compilePhaseCount > 1) {
            builder.append("s (").append(compilePhaseCount).append(")");
        }
        builder.append(" of Failed Method");
        int failedIRMethods = methodIndex;
        if (failedIRMethods > 1) {
            builder.append("s (").append(failedIRMethods).append(")");
        }
        builder.append(System.lineSeparator())
               .append(getTitleSeparator(failedIRMethods))
               .append(System.lineSeparator());
        msg.insert(0, builder);
    }

    private String getTitleSeparator(int failedIRMethods) {
        int failedMethodDashes = failedIRMethods > 1 ? digitCount(failedIRMethods) + 4 : 0;
        int compilePhaseDashes = compilePhaseCount > 1 ? digitCount(compilePhaseCount) + 4 : 0;
        return "-".repeat(28 + compilePhaseDashes + failedMethodDashes);
    }

    @Override
    public void visit(IRMethodMatchResult irMethodMatchResult) {
        irMethod = irMethodMatchResult.getIRMethod();
        irMethodMatchResult.acceptChildren(this);
        appendIRMethodHeader(irMethodMatchResult.getIRMethod().getMethod());
        appendMatchedCompilationOutputOfPhases();
        failedCompilePhases.clear();
    }

    private void appendMethodIndex() {
        methodIndex++;
        if (methodIndex > 1) {
            msg.append(System.lineSeparator());
        }
        msg.append(methodIndex).append(") ");
    }

    private void appendIRMethodHeader(Method method) {
        appendMethodIndex();
        msg.append("Compilation");
        if (failedCompilePhases.size() > 1) {
            msg.append("s (").append(failedCompilePhases.size()).append(")");
        }
        msg.append(" of \"").append(method).append("\":")
           .append(System.lineSeparator());
    }

    private void appendMatchedCompilationOutputOfPhases() {
        msg.append(failedCompilePhases.values()
                                      .stream()
                                      .collect(Collectors.joining(System.lineSeparator())));
    }

    @Override
    public void visit(MethodNotCompiledResult methodNotCompiledResult) {
        appendIRMethodHeader(methodNotCompiledResult.getMethod());
        compilePhaseCount++; // Count this as one phase
        msg.append("<empty>").append(System.lineSeparator());
    }

    @Override
    public void visit(IRRuleMatchResult irRuleMatchResult) {
        irRuleMatchResult.acceptChildren(this);
    }

    @Override
    public void visit(CompilePhaseIRRuleMatchResult compilePhaseIRRuleMatchResult) {
        CompilePhase compilePhase = compilePhaseIRRuleMatchResult.compilePhase();
        if (!failedCompilePhases.containsKey(compilePhase)) {
            failedCompilePhases.put(compilePhase, irMethod.getOutput(compilePhase));
            compilePhaseCount++;
        }
    }

    @Override
    public void visit(NoCompilePhaseCompilationResult noCompilePhaseCompilationResult) {
        CompilePhase compilePhase = noCompilePhaseCompilationResult.compilePhase();
        if (!failedCompilePhases.containsKey(compilePhase)) {
            failedCompilePhases.put(compilePhase,
                                    "> Phase \"" + compilePhase.getName() + "\":" + System.lineSeparator() + "<empty>" +
                                    System.lineSeparator());
            compilePhaseCount++;
        }
    }

    public String build() {
        testClassMatchResult.accept(this);
        return msg.toString();
    }

    private static int digitCount(int digit) {
        return String.valueOf(digit).length();
    }
}
