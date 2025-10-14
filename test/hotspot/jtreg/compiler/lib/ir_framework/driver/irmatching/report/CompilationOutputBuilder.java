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

package compiler.lib.ir_framework.driver.irmatching.report;

import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.shared.TestFrameworkException;
import compiler.lib.ir_framework.driver.irmatching.MatchResult;
import compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.CheckAttributeType;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.CountsConstraintFailure;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.FailOnConstraintFailure;
import compiler.lib.ir_framework.driver.irmatching.visitor.AcceptChildren;
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
    private final StringBuilder output;
    private final MatchResult testClassMatchResult;
    private final SortedMap<CompilePhase, String> failedCompilePhases = new TreeMap<>();
    private int methodIndex;
    /**
     * Number of collected distinct compile phases.
     */
    private int compilePhaseCount = 0;

    public CompilationOutputBuilder(MatchResult testClassMatchResult) {
        this.output = new StringBuilder();
        this.testClassMatchResult = testClassMatchResult;
    }

    @Override
    public void visitTestClass(AcceptChildren acceptChildren) {
        acceptChildren.accept(this);
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
        output.insert(0, builder);
    }

    private String getTitleSeparator(int failedIRMethods) {
        int failedMethodDashes = failedIRMethods > 1 ? digitCount(failedIRMethods) + 4 : 0;
        int compilePhaseDashes = compilePhaseCount > 1 ? digitCount(compilePhaseCount) + 4 : 0;
        return "-".repeat(28 + compilePhaseDashes + failedMethodDashes);
    }

    @Override
    public void visitIRMethod(AcceptChildren acceptChildren, Method method, int failedIRRules) {
        acceptChildren.accept(this);
        appendIRMethodHeader(method);
        appendMatchedCompilationOutputOfPhases();
        failedCompilePhases.clear();
    }

    private void appendMethodIndex() {
        methodIndex++;
        if (methodIndex > 1) {
            output.append(System.lineSeparator());
        }
        output.append(methodIndex).append(") ");
    }

    private void appendIRMethodHeader(Method method) {
        appendMethodIndex();
        output.append("Compilation");
        if (failedCompilePhases.size() > 1) {
            output.append("s (").append(failedCompilePhases.size()).append(")");
        }
        output.append(" of \"").append(method).append("\":")
              .append(System.lineSeparator());
    }

    private void appendMatchedCompilationOutputOfPhases() {
        output.append(failedCompilePhases.values()
                                         .stream()
                                         .collect(Collectors.joining(System.lineSeparator())));
    }

    @Override
    public void visitMethodNotCompiled(Method method, int failedIRRules) {
        appendIRMethodHeader(method);
        compilePhaseCount++; // Count this as one phase
        output.append("<empty>").append(System.lineSeparator());
    }

    @Override
    public void visitMethodNotCompilable(Method method, int failedIRRules) {
        throw new TestFrameworkException("Sould not reach here");
    }

    @Override
    public void visitIRRule(AcceptChildren acceptChildren, int irRuleId, IR irAnno) {
        acceptChildren.accept(this);
    }

    @Override
    public void visitCompilePhaseIRRule(AcceptChildren acceptChildren, CompilePhase compilePhase, String compilationOutput) {
        if (!failedCompilePhases.containsKey(compilePhase)) {
            failedCompilePhases.put(compilePhase, compilationOutput);
            compilePhaseCount++;
        }
        // No need to visit check attributes
    }

    @Override
    public void visitNoCompilePhaseCompilation(CompilePhase compilePhase) {
        if (!failedCompilePhases.containsKey(compilePhase)) {
            failedCompilePhases.put(compilePhase,
                                    "> Phase \"" + compilePhase.getName() + "\":" + System.lineSeparator() + "<empty>" +
                                    System.lineSeparator());
            compilePhaseCount++;
        }
    }

    @Override
    public void visitCheckAttribute(AcceptChildren acceptChildren, CheckAttributeType checkAttributeType) {}

    @Override
    public void visitFailOnConstraint(FailOnConstraintFailure failOnConstraintFailure) {}

    @Override
    public void visitCountsConstraint(CountsConstraintFailure countsConstraintFailure) {}

    public String build() {
        testClassMatchResult.accept(this);
        return output.toString();
    }

    private static int digitCount(int digit) {
        return String.valueOf(digit).length();
    }
}
