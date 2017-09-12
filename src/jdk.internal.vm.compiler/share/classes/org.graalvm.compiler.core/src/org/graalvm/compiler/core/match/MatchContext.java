/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.match;

import static org.graalvm.compiler.debug.DebugOptions.LogVerbose;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.graalvm.compiler.core.gen.NodeLIRBuilder;
import org.graalvm.compiler.core.match.MatchPattern.Result;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.util.EconomicMap;
import org.graalvm.util.Equivalence;

/**
 * Container for state captured during a match.
 */
public class MatchContext {

    private final Node root;

    private final List<Node> nodes;

    private final MatchStatement rule;

    private EconomicMap<String, NamedNode> namedNodes;

    private ArrayList<Node> consumed;

    private int startIndex;

    private int endIndex;

    private final NodeLIRBuilder builder;

    private static class NamedNode {
        final Class<? extends Node> type;
        final Node value;

        NamedNode(Class<? extends Node> type, Node value) {
            this.type = type;
            this.value = value;
        }
    }

    public MatchContext(NodeLIRBuilder builder, MatchStatement rule, int index, Node node, List<Node> nodes) {
        this.builder = builder;
        this.rule = rule;
        this.root = node;
        this.nodes = nodes;
        assert index == nodes.indexOf(node);
        // The root should be the last index since all the inputs must be scheduled before it.
        startIndex = endIndex = index;
    }

    public Node getRoot() {
        return root;
    }

    public Result captureNamedValue(String name, Class<? extends Node> type, Node value) {
        if (namedNodes == null) {
            namedNodes = EconomicMap.create(Equivalence.DEFAULT);
        }
        NamedNode current = namedNodes.get(name);
        if (current == null) {
            current = new NamedNode(type, value);
            namedNodes.put(name, current);
            return Result.OK;
        } else {
            if (current.value != value || current.type != type) {
                return Result.namedValueMismatch(value, rule.getPattern());
            }
            return Result.OK;
        }
    }

    public Result validate() {
        // Ensure that there's no unsafe work in between these operations.
        for (int i = startIndex; i <= endIndex; i++) {
            Node node = nodes.get(i);
            if (node instanceof VirtualObjectNode || node instanceof FloatingNode) {
                // The order of evaluation of these nodes controlled by data dependence so they
                // don't interfere with this match.
                continue;
            } else if ((consumed == null || !consumed.contains(node)) && node != root) {
                if (LogVerbose.getValue(root.getOptions())) {
                    DebugContext debug = root.getDebug();
                    debug.log("unexpected node %s", node);
                    for (int j = startIndex; j <= endIndex; j++) {
                        Node theNode = nodes.get(j);
                        debug.log("%s(%s) %1s", (consumed != null && consumed.contains(theNode) || theNode == root) ? "*" : " ", theNode.getUsageCount(), theNode);
                    }
                }
                return Result.notSafe(node, rule.getPattern());
            }
        }
        return Result.OK;
    }

    /**
     * Mark the interior nodes with INTERIOR_MATCH and set the Value of the root to be the result.
     * During final LIR generation it will be evaluated to produce the actual LIR value.
     *
     * @param result
     */
    public void setResult(ComplexMatchResult result) {
        ComplexMatchValue value = new ComplexMatchValue(result);
        DebugContext debug = root.getDebug();
        if (debug.isLogEnabled()) {
            debug.log("matched %s %s", rule.getName(), rule.getPattern());
            debug.log("with nodes %s", rule.formatMatch(root));
        }
        if (consumed != null) {
            for (Node node : consumed) {
                // All the interior nodes should be skipped during the normal doRoot calls in
                // NodeLIRBuilder so mark them as interior matches. The root of the match will get a
                // closure which will be evaluated to produce the final LIR.
                builder.setMatchResult(node, ComplexMatchValue.INTERIOR_MATCH);
            }
        }
        builder.setMatchResult(root, value);
    }

    /**
     * Mark a node as consumed by the match. Consumed nodes will never be evaluated.
     *
     * @return Result.OK if the node can be safely consumed.
     */
    public Result consume(Node node) {
        assert MatchPattern.isSingleValueUser(node) : "should have already been checked";

        // Check NOT_IN_BLOCK first since that usually implies ALREADY_USED
        int index = nodes.indexOf(node);
        if (index == -1) {
            return Result.notInBlock(node, rule.getPattern());
        }

        if (builder.hasOperand(node)) {
            return Result.alreadyUsed(node, rule.getPattern());
        }

        startIndex = Math.min(startIndex, index);
        if (consumed == null) {
            consumed = new ArrayList<>(2);
        }
        consumed.add(node);
        return Result.OK;
    }

    /**
     * Return the named node. It's an error if the
     *
     * @param name the name of a node in the match rule
     * @return the matched node
     * @throws GraalError is the named node doesn't exist.
     */
    public Node namedNode(String name) {
        if (namedNodes != null) {
            NamedNode value = namedNodes.get(name);
            if (value != null) {
                return value.value;
            }
        }
        throw new GraalError("missing node %s", name);
    }

    @Override
    public String toString() {
        return String.format("%s %s (%d, %d) consumed %s", rule, root, startIndex, endIndex, consumed != null ? Arrays.toString(consumed.toArray()) : "");
    }
}
