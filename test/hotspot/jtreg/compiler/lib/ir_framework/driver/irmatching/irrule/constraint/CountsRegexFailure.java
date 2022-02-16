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
 * This class represents an IR matching failure of a regex of a counts attribute of an IR rule.
 *
 * @see Counts
 */
class CountsRegexFailure extends RegexFailure {
    private final String failedComparison;
    private final int foundMatchesCount;

    public CountsRegexFailure(String nodeRegex, int nodeId, int foundMatchesCount, Comparison<Integer> comparison, List<String> matches) {
                              List<String> matches) {
        super(nodeRegex, nodeId, matches);
        this.failedComparison = "[found] " + foundMatchesCount + " " + comparison.getComparator() + " "
                                + comparison.getGivenValue() + " [given]";
        this.foundMatchesCount = foundMatchesCount;
    }

    @Override
    public int getMatchedNodesCount() {
        return foundMatchesCount;
    }

    @Override
    public String buildFailureMessage(int indentationSize) {
        return getRegexLine(indentationSize)
               + getFailedComparison(indentationSize + 2)
               + getMatchedNodesBlock(indentationSize + 2);
    }

    private String getFailedComparison(int indentation) {
        return getIndentation(indentation) + "- Failed comparison: " + failedComparison + System.lineSeparator();
    }

    @Override
    protected String getMatchedNodesBlock(int indentation) {
        if (matches.isEmpty()) {
            return getEmptyNodeMatchesLine(indentation);
        } else {
            return super.getMatchedNodesBlock(indentation);
        }
    }

    private String getEmptyNodeMatchesLine(int indentation) {
        return getIndentation(indentation) + "- No nodes matched!" + System.lineSeparator();
    }

    @Override
    protected String getMatchedPrefix() {
        return "Matched";
    }

}
