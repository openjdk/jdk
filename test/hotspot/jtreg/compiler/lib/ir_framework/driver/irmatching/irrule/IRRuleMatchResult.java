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

package compiler.lib.ir_framework.driver.irmatching.irrule;

import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.driver.irmatching.MatchResult;
import compiler.lib.ir_framework.driver.irmatching.irrule.phase.CompilePhaseIRRuleMatchResult;
import compiler.lib.ir_framework.driver.irmatching.visitor.MatchResultVisitor;

import java.util.Comparator;
import java.util.TreeSet;

/**
 * This class represents an IR matching result of an {@link IRRule} (applied to all compile phases specified in
 * {@link IR#phase()}). The {@link CompilePhaseIRRuleMatchResult} are kept in definition order as defined in
 * {@link CompilePhase}.
 *
 * @see IRRule
 */
public class IRRuleMatchResult implements MatchResult {
    private final int irRuleId;
    private final IR irAnno;
    /**
     * List of all compile phase match results for this IR rule which is sorted by the {@link CompilePhase} enum
     * definition order.
     */
    private final TreeSet<CompilePhaseIRRuleMatchResult> compilePhaseIRRuleMatchResults
            = new TreeSet<>(Comparator.comparingInt(r -> r.getCompilePhase().ordinal()));

    public IRRuleMatchResult(IRRule irRule) {
        this.irRuleId = irRule.getRuleId();
        this.irAnno = irRule.getIRAnno();
    }

    public int getRuleId() {
        return irRuleId;
    }

    public IR getIRAnno() {
        return irAnno;
    }

    public void addCompilePhaseIRMatchResult(CompilePhaseIRRuleMatchResult result) {
        compilePhaseIRRuleMatchResults.add(result);
    }

    @Override
    public boolean fail() {
        return !compilePhaseIRRuleMatchResults.isEmpty();
    }

    @Override
    public void accept(MatchResultVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public void acceptChildren(MatchResultVisitor visitor) {
        acceptChildren(visitor, compilePhaseIRRuleMatchResults);
    }
}
