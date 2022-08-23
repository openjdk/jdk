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

import compiler.lib.ir_framework.driver.irmatching.FailureMessage;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Base class representing a failure when applying a constraint (i.e. regex matching) on a compile phase output.
 *
 * @see Constraint
 * @see CheckAttribute
 * @see CheckAttributeMatchResult
 */
abstract class ConstraintFailure implements FailureMessage {
    private final String nodeRegex;
    private final int constraintIndex;
    protected final List<String> matchedNodes;

    public ConstraintFailure(Constraint constraint, List<String> matchedNodes) {
        this.nodeRegex = constraint.getRegex();
        this.constraintIndex = constraint.getIndex();
        this.matchedNodes = matchedNodes;
    }

    private List<String> addWhiteSpacePrefixForEachLine(List<String> matches, String indentation) {
        return matches
                .stream()
                .map(s -> s.replaceAll(System.lineSeparator(), System.lineSeparator() + indentation))
                .collect(Collectors.toList());
    }

    public int getMatchedNodesCount() {
        return matchedNodes.size();
    }

    protected String buildConstraintHeader(int indentation) {
        return getIndentation(indentation) + "* Constraint " + constraintIndex + ": \"" + nodeRegex + "\"" + System.lineSeparator();
    }

    protected String buildMatchedNodesMessage(int indentation) {
        return buildMatchedNodesHeader(indentation) + buildMatchedNodesBody(indentation + 2);
    }

    private String buildMatchedNodesHeader(int indentation) {
        int matchCount = matchedNodes.size();
        return getIndentation(indentation) + "- " + getMatchedPrefix() + " node"
               + (matchCount > 1 ? "s (" + matchCount + ")" : "") + ":" + System.lineSeparator();
    }

    abstract protected String getMatchedPrefix();

    private String buildMatchedNodesBody(int indentation) {
        StringBuilder builder = new StringBuilder();
        String indentationString = getIndentation(indentation);
        List<String> matches = addWhiteSpacePrefixForEachLine(this.matchedNodes, indentationString + "  ");
        matches.forEach(match -> builder.append(indentationString).append("* ").append(match).append(System.lineSeparator()));
        return builder.toString();
    }
}
