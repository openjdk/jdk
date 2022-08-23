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

package compiler.lib.ir_framework.driver.irmatching.irrule.constraint;

import compiler.lib.ir_framework.shared.Comparison;

import java.util.List;

/**
 * This class represents a failure when applying a {@link CountsConstraint} on a compile phase output.
 *
 * @see CountsConstraint
 * @see Counts
 * @see CountsMatchResult
 */
class CountsConstraintFailure extends ConstraintFailure {
    private final String failedComparison;

    public CountsConstraintFailure(CountsConstraint constraint, List<String> matches) {
        super(constraint, matches);
        Comparison<Integer> comparison = constraint.getComparison();
        this.failedComparison = "[found] " + matches.size() + " " + comparison.getComparator() + " "
                                + comparison.getGivenValue() + " [given]";
    }

    @Override
    public String buildFailureMessage(int indentationSize) {
        return buildConstraintHeader(indentationSize)
               + buildFailedComparisonMessage(indentationSize + 2)
               + buildMatchedNodesMessage(indentationSize + 2);
    }

    private String buildFailedComparisonMessage(int indentation) {
        return getIndentation(indentation) + "- Failed comparison: " + failedComparison + System.lineSeparator();
    }

    @Override
    protected String buildMatchedNodesMessage(int indentation) {
        if (matchedNodes.isEmpty()) {
            return buildEmptyNodeMatchesMessage(indentation);
        } else {
            return super.buildMatchedNodesMessage(indentation);
        }
    }

    private String buildEmptyNodeMatchesMessage(int indentation) {
        return getIndentation(indentation) + "- No nodes matched!" + System.lineSeparator();
    }

    @Override
    protected String getMatchedPrefix() {
        return "Matched";
    }
}
