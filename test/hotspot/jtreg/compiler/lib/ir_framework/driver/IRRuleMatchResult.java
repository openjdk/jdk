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

package compiler.lib.ir_framework.driver;

import java.util.List;

/**
 * Class used to store an IR matching result of an IR rule.
 *
 * @see Failure
 * @see IRRule
 */
class IRRuleMatchResult {
    private final IRRule irRule;
    private List<? extends Failure> failOnFailures = null;
    private List<? extends Failure> countsFailures = null;
    private OutputMatch outputMatch;

    public IRRuleMatchResult(IRRule irRule) {
        this.irRule = irRule;
        this.outputMatch = OutputMatch.NONE;
    }

    public OutputMatch getOutputMatch() {
        return outputMatch;
    }

    public void setIdealMatch() {
        switch (outputMatch) {
            case NONE -> outputMatch = OutputMatch.IDEAL;
            case OPTO_ASSEMBLY -> outputMatch = OutputMatch.BOTH;
        }
    }

    public void setOptoAssemblyMatch() {
        switch (outputMatch) {
            case NONE -> outputMatch = OutputMatch.OPTO_ASSEMBLY;
            case IDEAL -> outputMatch = OutputMatch.BOTH;
        }
    }


    private boolean hasFailOnFailures() {
        return failOnFailures != null;
    }

    public void setFailOnFailures(List<? extends Failure> failOnFailures) {
        this.failOnFailures = failOnFailures;
    }

    private boolean hasCountsFailures() {
        return countsFailures != null;
    }

    public List<? extends Failure> getCountsFailures() {
        return countsFailures;
    }

    public void setCountsFailures(List<? extends Failure> countsFailures) {
        this.countsFailures = countsFailures;
    }

    /**
     * Does this result represent a failure?
     */
    public boolean fail() {
        return failOnFailures != null || countsFailures != null;
    }

    /**
     * Build a failure message based on the collected failures of this object.
     */
    public String buildFailureMessage() {
        StringBuilder failMsg = new StringBuilder();
        failMsg.append("   * @IR rule ").append(irRule.getRuleId()).append(": \"")
               .append(irRule.getIRAnno()).append("\"").append(System.lineSeparator());
        if (hasFailOnFailures()) {
            failMsg.append("     - failOn: Graph contains forbidden nodes:").append(System.lineSeparator());
            failMsg.append(getFormattedFailureMessage(failOnFailures));
        }
        if (hasCountsFailures()) {
            failMsg.append("     - counts: Graph contains wrong number of nodes:").append(System.lineSeparator());
            failMsg.append(getFormattedFailureMessage(countsFailures));
        }
        return failMsg.toString();
    }

    private String getFormattedFailureMessage(List<? extends Failure> failures) {
        StringBuilder builder = new StringBuilder();
        for (Failure failure : failures) {
            builder.append(failure.getFormattedFailureMessage());
        }
        return builder.toString();
    }
}
