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
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.ConstraintFailure;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.CountsConstraintFailure;
import compiler.lib.ir_framework.shared.Comparison;

/**
 * This class creates a failure message for a {@link IR#counts} constraint failure.
 */
public class CountsConstraintFailureMessageBuilder extends ConstraintFailureMessageBuilder {
    private final CountsConstraintFailure constraintFailure;

    public CountsConstraintFailureMessageBuilder(CountsConstraintFailure constraintFailure, int indentation) {
        super(indentation);
        this.constraintFailure = constraintFailure;
    }

    @Override
    protected String getMatchedPrefix(ConstraintFailure constraintFailure) {
        return "Matched";
    }


    @Override
    public String build() {
        return buildConstraintHeader(constraintFailure) + buildFailedComparisonMessage(constraintFailure) +
               buildMatchedCountsNodesMessage(constraintFailure);
    }

    private String buildFailedComparisonMessage(CountsConstraintFailure constraintFailure) {
        Comparison<Integer> comparison = constraintFailure.getComparison();
        String failedComparison = "[found] " + constraintFailure.getMatchedNodes().size() + " "
                                  + comparison.getComparator() + " " + comparison.getGivenValue() + " [given]";
        return ReportBuilder.getIndentation(indentation + 2) + "- Failed comparison: " + failedComparison
               + System.lineSeparator();
    }

    private String buildMatchedCountsNodesMessage(CountsConstraintFailure constraintFailure) {
        if (constraintFailure.getMatchedNodes().isEmpty()) {
            return buildEmptyNodeMatchesMessage();
        } else {
            return buildMatchedNodesMessage(constraintFailure);
        }
    }

    private String buildEmptyNodeMatchesMessage() {
        return ReportBuilder.getIndentation(indentation + 2) + "- No nodes matched!" + System.lineSeparator();
    }
}
