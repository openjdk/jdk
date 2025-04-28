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
import compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.CheckAttributeType;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.CountsConstraintFailure;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.FailOnConstraintFailure;
import compiler.lib.ir_framework.driver.irmatching.visitor.AcceptChildren;
import compiler.lib.ir_framework.driver.irmatching.visitor.MatchResultVisitor;

import java.lang.reflect.Method;

/**
 * Visitor to collect the number of IR method and IR rule failures.
 */
class FailCountVisitor implements MatchResultVisitor {
    private int irMethodCount;
    private int irRuleCount;

    @Override
    public void visitTestClass(AcceptChildren acceptChildren) {
        acceptChildren.accept(this);
    }

    @Override
    public void visitIRMethod(AcceptChildren acceptChildren, Method method, int failedIRRules) {
        irMethodCount++;
        acceptChildren.accept(this);
    }

    @Override
    public void visitIRRule(AcceptChildren acceptChildren, int irRuleId, IR irAnno) {
        irRuleCount++;
        // Do not need to visit compile phase IR rules
    }

    @Override
    public void visitMethodNotCompiled(Method method, int failedIRRules) {
        irMethodCount++;
        irRuleCount += failedIRRules;
    }

    @Override
    public void visitMethodNotCompilable(Method method, int failedIRRules) {
        irMethodCount++;
    }

    public int getIrRuleCount() {
        return irRuleCount;
    }

    public int getIrMethodCount() {
        return irMethodCount;
    }

    @Override
    public void visitCompilePhaseIRRule(AcceptChildren acceptChildren, CompilePhase compilePhase, String compilationOutput) {}

    @Override
    public void visitNoCompilePhaseCompilation(CompilePhase compilePhase) {}

    @Override
    public void visitCheckAttribute(AcceptChildren acceptChildren, CheckAttributeType checkAttributeType) {}

    @Override
    public void visitFailOnConstraint(FailOnConstraintFailure failOnConstraintFailure) {}

    @Override
    public void visitCountsConstraint(CountsConstraintFailure countsConstraintFailure) {}
}
