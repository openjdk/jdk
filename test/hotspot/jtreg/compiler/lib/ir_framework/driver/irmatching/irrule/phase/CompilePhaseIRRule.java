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

package compiler.lib.ir_framework.driver.irmatching.irrule.phase;

import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.driver.irmatching.Matching;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.CheckAttribute;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.CheckAttributeMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.Counts;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.FailOn;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * This class represents an IR rule of an IR method for a specific compile phase. It contains a fully parsed (i.e.
 * all placeholder strings of {@link IRNode} replaced and composite nodes merged) {@link FailOn} and {@link Counts}
 * attribute which are ready to be IR matched against.
 *
 * @see CompilePhaseIRRule
 */
public class CompilePhaseIRRule implements Matching {
    protected final CompilePhase compilePhase;
    protected final FailOn failOn;
    protected final Counts counts;
    private final String phaseCompilationOutput;

    public CompilePhaseIRRule(CompilePhase compilePhase, FailOn failOn, Counts counts, String phaseCompilationOutput) {
        this.compilePhase = compilePhase;
        this.failOn = failOn;
        this.counts = counts;
        this.phaseCompilationOutput = phaseCompilationOutput;
    }

    @Override
    public CompilePhaseMatchResult match() {
        if (phaseCompilationOutput.isEmpty()) {
            return CompilePhaseMatchResult.createNoCompilationOutput(compilePhase);
        } else {
            return matchWithCompilationOutput();
        }
    }

    private CompilePhaseMatchResult matchWithCompilationOutput() {
        CompilePhaseMatchResult compilePhaseMatchResult = CompilePhaseMatchResult.create(compilePhase);
        applyCheckAttribute(failOn, compilePhaseMatchResult::setFailOnMatchResult);
        applyCheckAttribute(counts, compilePhaseMatchResult::setCountsMatchResult);
        return compilePhaseMatchResult;
    }

    private void applyCheckAttribute(CheckAttribute<?> checkAttribute, Consumer<CheckAttributeMatchResult> consumer) {
        if (checkAttribute != null) {
            CheckAttributeMatchResult matchResult = checkAttribute.check(phaseCompilationOutput);
            if (matchResult.fail()) {
                consumer.accept(matchResult);
            }
        }
    }

    /**
     * To remove duplicated phases created by CompilePhaseIRRuleBuilder.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CompilePhaseIRRule that = (CompilePhaseIRRule) o;
        return compilePhase == that.compilePhase;
    }

    /**
     * To remove duplicated phases created by CompilePhaseIRRuleBuilder
     */
    @Override
    public int hashCode() {
        return Objects.hash(compilePhase);
    }
}
