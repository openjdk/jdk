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
import compiler.lib.ir_framework.driver.irmatching.FailureMessage;
import compiler.lib.ir_framework.driver.irmatching.MatchResult;
import compiler.lib.ir_framework.driver.irmatching.irrule.IRRule;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.CheckAttributeMatchResult;

/**
 * This class represents an IR matching result of an IR rule applied on a compile phase.
 *
 * @see IRRule
 */
public class CompilePhaseMatchResult implements MatchResult, FailureMessage {
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

    @Override
    public boolean fail() {
        return failOnFailures != null || countsFailures != null || !compilationOutput;
    }

    public CompilePhase getCompilePhase() {
        return compilePhase;
    }

    private boolean hasFailOnFailures() {
        return failOnFailures != null;
    }

    public void setFailOnMatchResult(CheckAttributeMatchResult failOnFailures) {
        this.failOnFailures = failOnFailures;
    }

    private boolean hasCountsFailures() {
        return countsFailures != null;
    }

    public void setCountsMatchResult(CheckAttributeMatchResult countsFailures) {
        this.countsFailures = countsFailures;
    }

    /**
     * Build a failure message based on the collected failures of this object.
     */
    @Override
    public String buildFailureMessage(int indentationSize) {
        StringBuilder failMsg = new StringBuilder();
        failMsg.append(buildPhaseHeader(indentationSize));
        indentationSize += 2;
        if (!compilationOutput) {
            failMsg.append(buildNoCompilationOutputMessage(indentationSize));
        } else {
            failMsg.append(buildFailOnFailureMessage(indentationSize));
            failMsg.append(buildCountsFailureMessage(indentationSize));
        }
        return failMsg.toString();
    }


    private String buildNoCompilationOutputMessage(int indentationSize) {
        return getIndentation(indentationSize) + "- NO compilation output found for this phase! Make sure this phase " +
               "is emitted or remove it from the list of compile phases in the @IR rule to match on." +
               System.lineSeparator();
    }

    private String buildFailOnFailureMessage(int indentationSize) {
        if (hasFailOnFailures()) {
            return failOnFailures.buildFailureMessage(indentationSize);
        } else {
            return "";
        }
    }

    private String buildCountsFailureMessage(int indentationSize) {
        if (hasCountsFailures()) {
            return countsFailures.buildFailureMessage(indentationSize);
        } else {
            return "";
        }
    }

    private String buildPhaseHeader(int indentation) {
        return getIndentation(indentation) + "> Phase \"" + compilePhase.getName() + "\":" + System.lineSeparator();
    }
}
