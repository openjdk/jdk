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

package compiler.lib.ir_framework.driver.irmatching.visitor;

import compiler.lib.ir_framework.driver.irmatching.MatchResult;
import compiler.lib.ir_framework.driver.irmatching.TestClassResult;
import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethodMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irmethod.NotCompiledResult;
import compiler.lib.ir_framework.driver.irmatching.irrule.IRRuleMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.CheckAttributeMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.CountsConstraintFailure;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.FailOnConstraintFailure;
import compiler.lib.ir_framework.driver.irmatching.irrule.phase.CompilePhaseIRRuleMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irrule.phase.NoCompilePhaseCompilationResult;

/**
 * This visitor visits the {@link MatchResult} elements in pre-order and calls the specified {@link MatchResultAction}
 * for each visited element.
 */
public class PreOrderMatchResultVisitor implements MatchResultVisitor {
    private final MatchResultAction action;

    public PreOrderMatchResultVisitor(MatchResultAction action) {
        this.action = action;
    }

    @Override
    public void visit(TestClassResult testClassResult) {
        action.doAction(testClassResult);
        testClassResult.acceptChildren(this);
    }

    @Override
    public void visit(IRMethodMatchResult irMethodMatchResult) {
        action.doAction(irMethodMatchResult);
        irMethodMatchResult.acceptChildren(this);
    }

    @Override
    public void visit(NotCompiledResult notCompiledResult) {
        action.doAction(notCompiledResult);
    }

    @Override
    public void visit(IRRuleMatchResult irRuleMatchResult) {
        action.doAction(irRuleMatchResult);
        irRuleMatchResult.acceptChildren(this);
    }

    @Override
    public void visit(CompilePhaseIRRuleMatchResult compilePhaseIRRuleMatchResult) {
        action.doAction(compilePhaseIRRuleMatchResult);
        compilePhaseIRRuleMatchResult.acceptChildren(this);
    }

    @Override
    public void visit(NoCompilePhaseCompilationResult noCompilePhaseCompilationResult) {
        action.doAction(noCompilePhaseCompilationResult);
    }

    @Override
    public void visit(CheckAttributeMatchResult checkAttributeMatchResult) {
        action.doAction(checkAttributeMatchResult);
        checkAttributeMatchResult.acceptChildren(this);
    }

    @Override
    public void visit(FailOnConstraintFailure failOnConstraintFailure) {
        action.doAction(failOnConstraintFailure);
    }

    @Override
    public void visit(CountsConstraintFailure countsConstraintFailure) {
        action.doAction(countsConstraintFailure);
    }
}

