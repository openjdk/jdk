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

import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.ConstraintFailure;

import java.util.List;

/**
 * This class creates a failure message for a failed constraint.
 */
public class ConstraintFailureMessageBuilder {
    private final String nodeRegex;
    private final int constraintId;
    private final List<String> matchedNodes;
    private final Indentation indentation;

    public ConstraintFailureMessageBuilder(ConstraintFailure constraintFailure, Indentation indentation) {
        this.nodeRegex = constraintFailure.nodeRegex();
        this.constraintId = constraintFailure.constraintId();
        this.matchedNodes = constraintFailure.matchedNodes();
        this.indentation = indentation;
    }

    public String buildConstraintHeader() {
        return indentation + "* Constraint "
               + constraintId + ": \"" + nodeRegex + "\""
               + System.lineSeparator();
    }

    public String buildMatchedNodesMessage(String matchedPrefix) {
        indentation.add();
        String header = buildMatchedNodesHeader(matchedPrefix);
        String body = buildMatchedNodesBody();
        indentation.sub();
        return header + body;
    }

    private String buildMatchedNodesHeader(String matchedPrefix) {
        int matchCount = matchedNodes.size();
        return indentation + "- " + matchedPrefix + " node" + (matchCount > 1 ? "s (" + matchCount + ")" : "") + ":"
               + System.lineSeparator();
    }

    private String buildMatchedNodesBody() {
        indentation.add();
        StringBuilder builder = new StringBuilder();
        List<String> matches = addWhiteSpacePrefixForEachLine(matchedNodes);
        matches.forEach(match -> builder.append(indentation)
                                        .append("* ").append(match).append(System.lineSeparator()));
        indentation.sub();
        return builder.toString();
    }

    private List<String> addWhiteSpacePrefixForEachLine(List<String> matches) {
        indentation.add();
        List<String> lines = indentation.prependForLines(matches);
        indentation.sub();
        return lines;
    }
}
