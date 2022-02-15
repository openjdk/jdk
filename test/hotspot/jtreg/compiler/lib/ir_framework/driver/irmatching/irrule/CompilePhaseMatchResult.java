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
import compiler.lib.ir_framework.driver.irmatching.MatchResult;

/**
 * This class represents an IR matching result of an IR rule applied to a compile phase.
 *
 * @see CheckAttributeMatchResult
 * @see IRRule
 */
public class CompilePhaseMatchResult implements MatchResult {
    private final CompilePhase compilePhase;
    private FailOnMatchResult failOnFailures = null;
    private CountsMatchResult countsFailures = null;

    public CompilePhaseMatchResult(CompilePhase compilePhase) {
        this.compilePhase = compilePhase;
    }

    @Override
    public boolean fail() {
        return failOnFailures != null || countsFailures != null;
    }

    public CompilePhase getCompilePhase() {
        return compilePhase;
    }

    private boolean hasFailOnFailures() {
        return failOnFailures != null;
    }

    public void setFailOnMatchResult(FailOnMatchResult failOnFailures) {
        this.failOnFailures = failOnFailures;
    }

    private boolean hasCountsFailures() {
        return countsFailures != null;
    }

    public void setCountsMatchResult(CountsMatchResult countsFailures) {
        this.countsFailures = countsFailures;
    }

    public int getTotalMatchedNodesCount() {
        return getFailOnMatchedNodesCount() + getCountsMatchedNodesCount();
    }

    private int getFailOnMatchedNodesCount() {
        return hasFailOnFailures() ? failOnFailures.getMatchedNodesCount() : 0;
    }

    private int getCountsMatchedNodesCount() {
        return hasCountsFailures() ? countsFailures.getMatchedNodesCount() : 0;
    }

    /**
     * Build a failure message based on the collected failures of this object.
     */
    @Override
    public String buildFailureMessage(int indentationSize) {
        StringBuilder failMsg = new StringBuilder();
        failMsg.append(getPhaseLine(indentationSize));
        if (hasFailOnFailures()) {
            failMsg.append(failOnFailures.buildFailureMessage(indentationSize + 2));
        }
        if (hasCountsFailures()) {
            failMsg.append(countsFailures.buildFailureMessage(indentationSize + 2));
        }
        return failMsg.toString();
    }

    private String getPhaseLine(int indentation) {
        return getIndentation(indentation) + "> Phase \"" + compilePhase.getName() + "\":" + System.lineSeparator();
    }
}
