/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.debug.CounterKey;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Position;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.Verbosity;

/**
 * A simple recursive pattern matcher for a DAG of nodes.
 */

public class MatchPattern {

    enum MatchResultCode {
        OK,
        WRONG_CLASS,
        NAMED_VALUE_MISMATCH,
        TOO_MANY_USERS,
        NOT_IN_BLOCK,
        NOT_SAFE,
        ALREADY_USED,
    }

    /**
     * A descriptive result for match failures. This can be helpful for debugging why a match
     * doesn't work as expected.
     */
    static class Result {
        final MatchResultCode code;

        final Node node;

        final MatchPattern matcher;

        Result(MatchResultCode result, Node node, MatchPattern matcher) {
            this.code = result;
            this.node = node;
            this.matcher = matcher;
        }

        private static final CounterKey MatchResult_WRONG_CLASS = DebugContext.counter("MatchResult_WRONG_CLASS");
        private static final CounterKey MatchResult_NAMED_VALUE_MISMATCH = DebugContext.counter("MatchResult_NAMED_VALUE_MISMATCH");
        private static final CounterKey MatchResult_TOO_MANY_USERS = DebugContext.counter("MatchResult_TOO_MANY_USERS");
        private static final CounterKey MatchResult_NOT_IN_BLOCK = DebugContext.counter("MatchResult_NOT_IN_BLOCK");
        private static final CounterKey MatchResult_NOT_SAFE = DebugContext.counter("MatchResult_NOT_SAFE");
        private static final CounterKey MatchResult_ALREADY_USED = DebugContext.counter("MatchResult_ALREADY_USED");

        static final Result OK = new Result(MatchResultCode.OK, null, null);
        private static final Result CACHED_WRONG_CLASS = new Result(MatchResultCode.WRONG_CLASS, null, null);
        private static final Result CACHED_NAMED_VALUE_MISMATCH = new Result(MatchResultCode.NAMED_VALUE_MISMATCH, null, null);
        private static final Result CACHED_TOO_MANY_USERS = new Result(MatchResultCode.TOO_MANY_USERS, null, null);
        private static final Result CACHED_NOT_IN_BLOCK = new Result(MatchResultCode.NOT_IN_BLOCK, null, null);
        private static final Result CACHED_NOT_SAFE = new Result(MatchResultCode.NOT_SAFE, null, null);
        private static final Result CACHED_ALREADY_USED = new Result(MatchResultCode.ALREADY_USED, null, null);

        static Result wrongClass(Node node, MatchPattern matcher) {
            MatchResult_WRONG_CLASS.increment(node.getDebug());
            return node.getDebug().isLogEnabled() ? new Result(MatchResultCode.WRONG_CLASS, node, matcher) : CACHED_WRONG_CLASS;
        }

        static Result namedValueMismatch(Node node, MatchPattern matcher) {
            MatchResult_NAMED_VALUE_MISMATCH.increment(node.getDebug());
            return node.getDebug().isLogEnabled() ? new Result(MatchResultCode.NAMED_VALUE_MISMATCH, node, matcher) : CACHED_NAMED_VALUE_MISMATCH;
        }

        static Result tooManyUsers(Node node, MatchPattern matcher) {
            MatchResult_TOO_MANY_USERS.increment(node.getDebug());
            return node.getDebug().isLogEnabled() ? new Result(MatchResultCode.TOO_MANY_USERS, node, matcher) : CACHED_TOO_MANY_USERS;
        }

        static Result notInBlock(Node node, MatchPattern matcher) {
            MatchResult_NOT_IN_BLOCK.increment(node.getDebug());
            return node.getDebug().isLogEnabled() ? new Result(MatchResultCode.NOT_IN_BLOCK, node, matcher) : CACHED_NOT_IN_BLOCK;
        }

        static Result notSafe(Node node, MatchPattern matcher) {
            MatchResult_NOT_SAFE.increment(node.getDebug());
            return node.getDebug().isLogEnabled() ? new Result(MatchResultCode.NOT_SAFE, node, matcher) : CACHED_NOT_SAFE;
        }

        static Result alreadyUsed(Node node, MatchPattern matcher) {
            MatchResult_ALREADY_USED.increment(node.getDebug());
            return node.getDebug().isLogEnabled() ? new Result(MatchResultCode.ALREADY_USED, node, matcher) : CACHED_ALREADY_USED;
        }

        @Override
        public String toString() {
            if (code == MatchResultCode.OK) {
                return "OK";
            }
            if (node == null) {
                return code.toString();
            } else {
                return code + " " + node.toString(Verbosity.Id) + "|" + node.getClass().getSimpleName() + " " + matcher;
            }
        }
    }

    /**
     * The expected type of the node. It must match exactly.
     */
    private final Class<? extends Node> nodeClass;

    /**
     * An optional name for this node. A name can occur multiple times in a match and that name must
     * always refer to the same node of the match will fail.
     */
    private final String name;

    /**
     * Patterns to match the inputs.
     */
    private final MatchPattern[] patterns;

    /**
     * The inputs to match the patterns against.
     */
    private final Position[] inputs;

    /**
     * Can there only be one user of the node. Constant nodes can be matched even if there are other
     * users.
     */
    private final boolean singleUser;

    private static final MatchPattern[] EMPTY_PATTERNS = new MatchPattern[0];

    public MatchPattern(String name, boolean singleUser) {
        this(null, name, singleUser);
    }

    public MatchPattern(Class<? extends Node> nodeClass, String name, boolean singleUser) {
        this.nodeClass = nodeClass;
        this.name = name;
        this.singleUser = singleUser;
        this.patterns = EMPTY_PATTERNS;
        this.inputs = null;
    }

    private MatchPattern(Class<? extends Node> nodeClass, String name, boolean singleUser, MatchPattern[] patterns, Position[] inputs) {
        assert inputs == null || inputs.length == patterns.length;
        this.nodeClass = nodeClass;
        this.name = name;
        this.singleUser = singleUser;
        this.patterns = patterns;
        this.inputs = inputs;
    }

    public MatchPattern(Class<? extends Node> nodeClass, String name, MatchPattern first, Position[] inputs, boolean singleUser) {
        this(nodeClass, name, singleUser, new MatchPattern[]{first}, inputs);
    }

    public MatchPattern(Class<? extends Node> nodeClass, String name, MatchPattern first, MatchPattern second, Position[] inputs, boolean singleUser) {
        this(nodeClass, name, singleUser, new MatchPattern[]{first, second}, inputs);
    }

    public MatchPattern(Class<? extends Node> nodeClass, String name, MatchPattern first, MatchPattern second, MatchPattern third, Position[] inputs, boolean singleUser) {
        this(nodeClass, name, singleUser, new MatchPattern[]{first, second, third}, inputs);
    }

    Class<? extends Node> nodeClass() {
        return nodeClass;
    }

    private Result matchType(Node node) {
        if (nodeClass != null && node.getClass() != nodeClass) {
            return Result.wrongClass(node, this);
        }
        return Result.OK;
    }

    /**
     * Match any named nodes and ensure that the consumed nodes can be safely merged.
     *
     * @param node
     * @param context
     * @return Result.OK is the pattern can be safely matched.
     */
    Result matchUsage(Node node, MatchContext context) {
        Result result = matchUsage(node, context, true);
        if (result == Result.OK) {
            result = context.validate();
        }
        return result;
    }

    private Result matchUsage(Node node, MatchContext context, boolean atRoot) {
        Result result = matchType(node);
        if (result != Result.OK) {
            return result;
        }
        if (singleUser && !atRoot) {
            result = context.consume(node);
            if (result != Result.OK) {
                return result;
            }
        }

        if (name != null) {
            result = context.captureNamedValue(name, nodeClass, node);
        }

        for (int input = 0; input < patterns.length; input++) {
            result = patterns[input].matchUsage(getInput(input, node), context, false);
            if (result != Result.OK) {
                return result;
            }
        }

        return result;
    }

    /**
     * Recursively match the shape of the tree without worry about named values. Most matches fail
     * at this point so it's performed first.
     *
     * @param node
     * @param statement
     * @return Result.OK if the shape of the pattern matches.
     */
    public Result matchShape(Node node, MatchStatement statement) {
        return matchShape(node, statement, true);
    }

    private Result matchShape(Node node, MatchStatement statement, boolean atRoot) {
        Result result = matchType(node);
        if (result != Result.OK) {
            return result;
        }

        if (singleUser && !atRoot) {
            if (!isSingleValueUser(node)) {
                return Result.tooManyUsers(node, statement.getPattern());
            }
        }

        for (int input = 0; input < patterns.length; input++) {
            result = patterns[input].matchShape(getInput(input, node), statement, false);
            if (result != Result.OK) {
                return result;
            }
        }

        return result;
    }

    public static boolean isSingleValueUser(Node node) {
        int valueUsage = node.getUsageCount();
        if (valueUsage == 1) {
            return true;
        }
        if (node.isAllowedUsageType(InputType.Guard)) {
            // See if the other usages are non-Value usages.
            valueUsage = 0;
            for (Node usage : node.usages()) {
                for (Position input : usage.inputPositions()) {
                    if (input.getInputType() == InputType.Value && input.get(usage) == node) {
                        valueUsage++;
                        if (valueUsage > 1) {
                            // Too many value users
                            return false;
                        }
                    }
                }
            }
            assert valueUsage == 1;
            return true;
        }
        return false;
    }

    /**
     * For a node starting at root, produce a String showing the inputs that matched against this
     * rule. It's assumed that a match has already succeeded against this rule, otherwise the
     * printing may produce exceptions.
     */
    public String formatMatch(Node root) {
        String result = String.format("%s", root);
        if (patterns.length == 0) {
            return result;
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("(");
            sb.append(result);
            for (int input = 0; input < patterns.length; input++) {
                sb.append(" ");
                sb.append(patterns[input].formatMatch(getInput(input, root)));
            }
            sb.append(")");
            return sb.toString();
        }
    }

    private Node getInput(int index, Node node) {
        return inputs[index].get(node);
    }

    @Override
    public String toString() {
        if (nodeClass == null) {
            return name;
        } else {
            String nodeName = nodeClass.getSimpleName();
            if (nodeName.endsWith("Node")) {
                nodeName = nodeName.substring(0, nodeName.length() - 4);
            }
            if (patterns.length == 0) {
                return nodeName + (name != null ? "=" + name : "");
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append("(");
                sb.append(nodeName);
                for (int index = 0; index < patterns.length; index++) {
                    sb.append(" ");
                    sb.append(patterns[index].toString());
                }
                sb.append(")");
                return sb.toString();
            }
        }
    }
}
