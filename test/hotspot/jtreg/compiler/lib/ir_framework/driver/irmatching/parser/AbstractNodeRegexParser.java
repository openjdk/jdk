/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework.driver.irmatching.parser;

import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.DefaultRegexes;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.driver.irmatching.IRMatcher;
import compiler.lib.ir_framework.driver.irmatching.irrule.AbstractParsedNodeList;
import compiler.lib.ir_framework.shared.TestFormatException;

import java.util.List;

/**
 * This class provides default regex strings that can be used in {@link IR @IR} annotations to specify IR constraints.
 * <p>
 * There are two types of default regexes:
 * <ul>
 *     <li><p>Standalone regexes: Use them directly.</li>
 *     <li><p>Composite regexes: Their names contain "{@code _OF}" and expect another string in a list in
 *            {@link IR#failOn()} and {@link IR#counts()}. They cannot be use as standalone regex and will result in a
 *            {@link TestFormatException} when doing so.</li>
 * </ul>
 *
 * @see IR
 */
abstract public class AbstractNodeRegexParser {
    /**
     * Called by {@link IRMatcher} to merge special composite nodes together with additional user-defined input.
     */

    abstract protected void addNonDefaultNode(AbstractParsedNodeList parsedIRNodes, ParsedNode parsedNode);

    protected void parseNodeRegexes(AbstractParsedNodeList parsedNodesList, List<NodeRegex> nodeRegexes, CompilePhase compilePhase) {
        for (NodeRegex nodeRegex : nodeRegexes) {
            String nodeString = nodeRegex.getRawNodeString();
            if (IRNode.isDefaultIRNode(nodeString)) {
                ParsedNode parsedNode = getParsedDefaultNode(nodeRegex, compilePhase);
                addDefaultNode(parsedNodesList, parsedNode);
            } else {
                addNonDefaultNode(parsedNodesList, createParsedNode(nodeString, nodeRegex));
            }
        }
    }

    protected ParsedNode getParsedDefaultNode(NodeRegex nodeRegex, CompilePhase compilePhase) {
        String defaultNodeString = DefaultRegexes.getRegexForIRNode(nodeRegex.getRawNodeString(), compilePhase);
        if (nodeRegex.isCompositeNode()) {
            defaultNodeString = defaultNodeString.replaceAll(DefaultRegexes.IS_REPLACED, nodeRegex.getUserPostfixString());
        }
        return createParsedNode(defaultNodeString, nodeRegex);
    }

    private void addDefaultNode(AbstractParsedNodeList parsedIRNodes, ParsedNode parsedNode) {
        parsedIRNodes.addNode(parsedNode);
    }


    private ParsedNode createParsedNode(String nodeString, NodeRegex nodeRegex) {
        if (nodeRegex.isCountConstraint()) {
            return new ParsedNode(nodeString, nodeRegex.getCountConstraint());
        } else {
            return new ParsedNode(nodeString);
        }
    }

}
