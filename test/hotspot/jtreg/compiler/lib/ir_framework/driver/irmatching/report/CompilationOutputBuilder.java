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
import compiler.lib.ir_framework.driver.irmatching.irrule.IRRuleMatchResult;
import compiler.lib.ir_framework.driver.irmatching.visitor.MatchResultVisitor;
import compiler.lib.ir_framework.driver.irmatching.TestClassResult;
import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethod;
import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethodMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irmethod.NotCompiledResult;
import compiler.lib.ir_framework.driver.irmatching.irrule.phase.CompilePhaseIRRuleMatchResult;

import java.util.EnumSet;
import java.util.stream.Collectors;

/**
 * This class collects the compilation output of each compile phase that was part of an IR matching failure by visiting
 * each match result element. Multiple compile phase compilation outputs for a single method are collected in the same
 * order as specified in {@link CompilePhase}.
 */
public class CompilationOutputBuilder extends ReportBuilder implements MatchResultVisitor {
    private final EnumSet<CompilePhase> failedCompilePhases = EnumSet.noneOf(CompilePhase.class);
    /**
     * Number of collected distinct compile phases.
     */
    private int compilePhaseCount = 0;

    public CompilationOutputBuilder(TestClassResult testClassResult) {
        super(testClassResult);
    }

    @Override
    public void visit(TestClassResult testClassResult) {
        testClassResult.acceptChildren(this);
        StringBuilder builder = new StringBuilder();
        builder.append("Compilation");
        if (compilePhaseCount > 1) {
            builder.append("s (").append(compilePhaseCount).append(")");
        }
        builder.append(" of Failed Method");
        int failedIRMethods = getMethodNumber();
        if (failedIRMethods > 1) {
            builder.append("s (").append(failedIRMethods).append(")");
        }
        builder.append(System.lineSeparator())
               .append(getTitleSeparator(failedIRMethods))
               .append(System.lineSeparator());
        msg.insert(0, builder);
    }

    private String getTitleSeparator(int failedIRMethods) {
        return "-".repeat(36 + digitCount(compilePhaseCount) + digitCount(failedIRMethods));
    }

    @Override
    public void visit(IRMethodMatchResult irMethodMatchResult) {
        irMethodMatchResult.acceptChildren(this);
        appendIRMethodHeader(irMethodMatchResult);
        appendMatchedCompilationOutputOfPhases(irMethodMatchResult);
        failedCompilePhases.clear();
    }

    private void appendIRMethodHeader(IRMethodMatchResult irMethodMatchResult) {
        appendIRMethodPrefix();
        msg.append("Compilation");
        if (failedCompilePhases.size() > 1) {
            msg.append("s (").append(failedCompilePhases.size()).append(")");
        }
        msg.append(" of \"").append(irMethodMatchResult.getIRMethod().getMethod()).append("\":")
           .append(System.lineSeparator());
    }

    private void appendMatchedCompilationOutputOfPhases(IRMethodMatchResult irMethodMatchResult) {
        IRMethod irMethod = irMethodMatchResult.getIRMethod();
        msg.append(failedCompilePhases.stream()
                                      .map(irMethod::getOutput)
                                      .collect(Collectors.joining(System.lineSeparator()
                                                                  + System.lineSeparator())));
    }

    @Override
    public void visit(NotCompiledResult notCompiledResult) {
        appendIRMethodHeader(notCompiledResult);
        compilePhaseCount++; // Count this as one phase
        msg.append("<empty>").append(System.lineSeparator());
    }

    @Override
    public void visit(IRRuleMatchResult irRuleMatchResult) {
        irRuleMatchResult.acceptChildren(this);
    }

    @Override
    public void visit(CompilePhaseIRRuleMatchResult compilePhaseIRRuleMatchResult) {
        if (failedCompilePhases.add(compilePhaseIRRuleMatchResult.getCompilePhase())) {
            compilePhaseCount++;
        }
    }

    @Override
    public String build() {
        visitResults(this);
        return msg.toString();
    }
}
