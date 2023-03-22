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

package compiler.lib.ir_framework.driver.irmatching.report;

import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.CountsConstraintFailure;
import compiler.lib.ir_framework.shared.Comparison;

/**
 * This class creates a failure message for a {@link IR#counts} constraint failure.
 */
public class CountsConstraintFailureMessageBuilder {
    private final ConstraintFailureMessageBuilder constrainFailureMessageBuilder;
    private final Comparison<Integer> comparison;
    private final int matchedNodesSize;
    private final Indentation indentation;

    public CountsConstraintFailureMessageBuilder(CountsConstraintFailure countsConstraintMatchResult,
                                                 Indentation indentation) {
        this.constrainFailureMessageBuilder = new ConstraintFailureMessageBuilder(countsConstraintMatchResult,
                                                                                  indentation);
        this.comparison = countsConstraintMatchResult.comparison();
        this.matchedNodesSize = countsConstraintMatchResult.matchedNodes().size();
        this.indentation = indentation;
    }

    public String build() {
        String header = constrainFailureMessageBuilder.buildConstraintHeader();
        indentation.add();
        String body = buildFailedComparisonMessage() + buildMatchedCountsNodesMessage();
        indentation.sub();
        return header + body;
    }

    private String buildFailedComparisonMessage() {
        String failedComparison = "[found] " + matchedNodesSize + " "
                                  + comparison.getComparator() + " " + comparison.getGivenValue() + " [given]";
        return indentation + "- Failed comparison: " + failedComparison + System.lineSeparator();
    }

    private String buildMatchedCountsNodesMessage() {
        if (matchedNodesSize == 0) {
            return buildEmptyNodeMatchesMessage();
        } else {
            return constrainFailureMessageBuilder.buildMatchedNodesMessage("Matched");
        }
    }

    private String buildEmptyNodeMatchesMessage() {
        return indentation + "- No nodes matched!" + System.lineSeparator();
    }
}
