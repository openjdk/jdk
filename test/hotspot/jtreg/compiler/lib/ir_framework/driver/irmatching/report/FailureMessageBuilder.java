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

/**
 * This class creates the complete failure message of each IR matching failure by visiting each match result element.
 */
public class FailureMessageBuilder implements MatchResultVisitor {
    private final StringBuilder msg;
    private final MatchResult testClassMatchResult;
    private Indentation indentation;
    private int methodIndex = 0;

    public FailureMessageBuilder(MatchResult testClassMatchResult) {
        this.msg = new StringBuilder();
        this.testClassMatchResult = testClassMatchResult;
    }

    @Override
    public void visitTestClass(AcceptChildren acceptChildren) {
        FailCountVisitor failCountVisitor = new FailCountVisitor();
        testClassMatchResult.accept(failCountVisitor);
        int failedMethodCount = failCountVisitor.getIrMethodCount();
        int failedIRRulesCount = failCountVisitor.getIrRuleCount();
        msg.append("One or more @IR rules failed:")
           .append(System.lineSeparator())
           .append(System.lineSeparator())
           .append("Failed IR Rules (").append(failedIRRulesCount).append(") of Methods (").append(failedMethodCount)
           .append(")").append(System.lineSeparator())
           .append(getTitleSeparator(failedMethodCount, failedIRRulesCount))
           .append(System.lineSeparator());
        acceptChildren.accept(this);
    }

    private static String getTitleSeparator(int failedMethodCount, int failedIRRulesCount) {
        return "-".repeat(32 + digitCount(failedIRRulesCount) + digitCount(failedMethodCount));
    }

    @Override
    public void visitIRMethod(AcceptChildren acceptChildren, Method method, int failedIRRules) {
        appendIRMethodHeader(method, failedIRRules);
        acceptChildren.accept(this);
    }

    private void appendIRMethodHeader(Method method, int failedIRRules) {
        methodIndex++;
        indentation = new Indentation(digitCount(methodIndex));
        if (methodIndex > 1) {
            msg.append(System.lineSeparator());
        }
        msg.append(methodIndex).append(") ");
        msg.append("Method \"").append(method)
           .append("\" - [Failed IR rules: ").append(failedIRRules).append("]:")
           .append(System.lineSeparator());
    }

    @Override
    public void visitMethodNotCompiled(Method method, int failedIRRules) {
        appendIRMethodHeader(method, failedIRRules);
        indentation.add();
        msg.append(indentation)
           .append("* Method was not compiled. Did you specify a @Run method in STANDALONE mode? In this case, make " +
                   "sure to always trigger a C2 compilation by invoking the test enough times.")
           .append(System.lineSeparator());
        indentation.sub();
    }

    public void visitMethodNotCompilable(Method method, int failedIRRules) {
        throw new TestFrameworkException("Sould not reach here");
    }

    @Override
    public void visitIRRule(AcceptChildren acceptChildren, int irRuleId, IR irAnno) {
        indentation.add();
        msg.append(indentation).append("* @IR rule ").append(irRuleId).append(": \"")
           .append(irAnno).append("\"").append(System.lineSeparator());
        acceptChildren.accept(this);
        indentation.sub();
    }

    @Override
    public void visitCompilePhaseIRRule(AcceptChildren acceptChildren, CompilePhase compilePhase, String compilationOutput) {
        indentation.add();
        appendCompilePhaseIRRule(compilePhase);
        acceptChildren.accept(this);
        indentation.sub();
    }

    private void appendCompilePhaseIRRule(CompilePhase compilePhase) {
        msg.append(indentation)
           .append("> Phase \"").append(compilePhase.getName()).append("\":")
           .append(System.lineSeparator());
    }

    @Override
    public void visitNoCompilePhaseCompilation(CompilePhase compilePhase) {
        indentation.add();
        appendCompilePhaseIRRule(compilePhase);
        indentation.add();
        msg.append(indentation)
           .append("- NO compilation output found for this phase! Make sure this phase is emitted or remove it from ")
           .append("the list of compile phases in the @IR rule to match on.")
           .append(System.lineSeparator());
        indentation.sub();
        indentation.sub();
    }

    @Override
    public void visitCheckAttribute(AcceptChildren acceptChildren, CheckAttributeType checkAttributeType) {
        indentation.add();
        String checkAttributeFailureMsg;
        switch (checkAttributeType) {
            case FAIL_ON -> checkAttributeFailureMsg = "failOn: Graph contains forbidden nodes";
            case COUNTS -> checkAttributeFailureMsg = "counts: Graph contains wrong number of nodes";
            default ->
                    throw new IllegalStateException("Unexpected value: " + checkAttributeType);
        }
        msg.append(indentation).append("- ").append(checkAttributeFailureMsg)
           .append(":").append(System.lineSeparator());
        acceptChildren.accept(this);
        indentation.sub();
    }

    @Override
    public void visitFailOnConstraint(FailOnConstraintFailure matchResult) {
        indentation.add();
        ConstraintFailureMessageBuilder constrainFailureMessageBuilder =
                new ConstraintFailureMessageBuilder(matchResult, indentation);
        String failureMessage = constrainFailureMessageBuilder.buildConstraintHeader() +
                                constrainFailureMessageBuilder.buildMatchedNodesMessage("Matched forbidden");
        msg.append(failureMessage);
        indentation.sub();
    }

    @Override
    public void visitCountsConstraint(CountsConstraintFailure matchResult) {
        indentation.add();
        msg.append(new CountsConstraintFailureMessageBuilder(matchResult, indentation).build());
        indentation.sub();
    }

    public String build() {
        testClassMatchResult.accept(this);
        msg.append(System.lineSeparator())
           .append(">>> Check stdout for compilation output of the failed methods")
           .append(System.lineSeparator()).append(System.lineSeparator());
        return msg.toString();
    }

    private static int digitCount(int digit) {
        return String.valueOf(digit).length();
    }
}
