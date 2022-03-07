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

import java.util.List;
import java.util.stream.Collectors;

/**
 * Base class representing an IR matching failure of a regex of a check attribute of an IR rule.
 *
 * @see CheckAttributeMatchResult
 * @see CheckAttribute
 * @see IRRule
 */
abstract class RegexFailure {
    protected final String nodeRegex;
    protected final int nodeId;
    protected final List<String> matches;

    public RegexFailure(String nodeRegex, int nodeId, List<String> matches) {
        this.nodeRegex = nodeRegex;
        this.nodeId = nodeId;
        this.matches = addWhiteSpacePrefixForEachLine(matches);
    }

    private List<String> addWhiteSpacePrefixForEachLine(List<String> matches) {
        return matches
                .stream()
                .map(s -> s.replaceAll(System.lineSeparator(), System.lineSeparator()
                                                               + getMatchedNodesItemWhiteSpace() + "  "))
                .collect(Collectors.toList());
    }

    abstract public String buildFailureMessage();

    public int getMatchesCount() {
        return matches.size();
    }

    protected String getRegexLine() {
        return "       * Regex " + nodeId + ": " + nodeRegex + System.lineSeparator();
    }

    protected String getMatchedNodesBlock() {
        return getMatchedNodesHeader() + getMatchesNodeLines();
    }

    protected String getMatchedNodesHeader() {
        int matchCount = matches.size();
        return "" + getMatchedNodesWhiteSpace() + "- " + getMatchedPrefix() + " node"
               + (matchCount != 1 ? "s (" + matchCount + ")" : "") + ":" + System.lineSeparator();
    }

    protected String getMatchedNodesWhiteSpace() {
        return "         ";
    }

    abstract protected String getMatchedPrefix();

    protected String getMatchesNodeLines() {
        StringBuilder builder = new StringBuilder();
        matches.forEach(match -> builder.append(getMatchedNodesItemWhiteSpace()).append("* ").append(match).append(System.lineSeparator()));
        return builder.toString();
    }

    private String getMatchedNodesItemWhiteSpace() {
        return "           ";
    }
}
