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
import compiler.lib.ir_framework.driver.irmatching.MatchResult;
import compiler.lib.ir_framework.driver.irmatching.MatchResultVisitor;
import compiler.lib.ir_framework.driver.irmatching.irrule.IRRule;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.CheckAttributeMatchResult;

/**
 * This class represents an IR matching result of an IR rule applied on a compile phase.
 *
 * @see IRRule
 */
public class CompilePhaseMatchResult implements MatchResult {
    private final CompilePhase compilePhase;
    private final boolean compilationOutput;
    private CheckAttributeMatchResult failOnFailures = null;
    private CheckAttributeMatchResult countsFailures = null;

    private CompilePhaseMatchResult(CompilePhase compilePhase, boolean compilationOutput) {
        this.compilePhase = compilePhase;
        this.compilationOutput = compilationOutput;
    }

    public static CompilePhaseMatchResult create(CompilePhase compilePhase) {
        return new CompilePhaseMatchResult(compilePhase, true);
    }

    public static CompilePhaseMatchResult createNoCompilationOutput(CompilePhase compilePhase) {
        return new CompilePhaseMatchResult(compilePhase, false);
    }

    public boolean hasNoCompilationOutput() {
        return !compilationOutput;
    }

    @Override
    public boolean fail() {
        return failOnFailures != null || countsFailures != null || hasNoCompilationOutput();
    }

    public CompilePhase getCompilePhase() {
        return compilePhase;
    }

    public void setFailOnMatchResult(CheckAttributeMatchResult failOnFailures) {
        this.failOnFailures = failOnFailures;
    }

    public void setCountsMatchResult(CheckAttributeMatchResult countsFailures) {
        this.countsFailures = countsFailures;
    }

    @Override
    public void accept(MatchResultVisitor visitor) {
        visitor.visit(this);
        if (failOnFailures != null) {
            failOnFailures.accept(visitor);
        }
        if (countsFailures != null) {
            countsFailures.accept(visitor);
        }
    }

}
