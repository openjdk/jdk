/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.nashorn.internal.codegen;

import static jdk.nashorn.internal.runtime.UnwarrantedOptimismException.FIRST_PROGRAM_POINT;
import static jdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor.MAX_PROGRAM_POINT_VALUE;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import jdk.nashorn.internal.ir.AccessNode;
import jdk.nashorn.internal.ir.BinaryNode;
import jdk.nashorn.internal.ir.CallNode;
import jdk.nashorn.internal.ir.Expression;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.IdentNode;
import jdk.nashorn.internal.ir.IndexNode;
import jdk.nashorn.internal.ir.LexicalContext;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.Optimistic;
import jdk.nashorn.internal.ir.UnaryNode;
import jdk.nashorn.internal.ir.VarNode;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;

/**
 * Find program points in the code that are needed for optimistic assumptions
 */
class ProgramPoints extends NodeVisitor<LexicalContext> {

    private final Deque<int[]> nextProgramPoint = new ArrayDeque<>();
    private final Set<Node> noProgramPoint = new HashSet<>();

    ProgramPoints() {
        super(new LexicalContext());
    }

    private int next() {
        final int next = nextProgramPoint.peek()[0]++;
        if(next > MAX_PROGRAM_POINT_VALUE) {
            throw new AssertionError("Function has more than " + MAX_PROGRAM_POINT_VALUE + " program points");
        }
        return next;
    }

    @Override
    public boolean enterFunctionNode(final FunctionNode functionNode) {
        nextProgramPoint.push(new int[] { FIRST_PROGRAM_POINT });
        return true;
    }

    @Override
    public Node leaveFunctionNode(final FunctionNode functionNode) {
        nextProgramPoint.pop();
        return functionNode;
    }

    private Optimistic setProgramPoint(final Optimistic optimistic, final int programPoint) {
        if (noProgramPoint.contains(optimistic)) {
            return optimistic;
        }
        return (Optimistic)(Expression)optimistic.setProgramPoint(programPoint);
    }

    @Override
    public boolean enterVarNode(final VarNode varNode) {
        noProgramPoint.add(varNode.getAssignmentDest());
        return true;
    }

    @Override
    public boolean enterIdentNode(final IdentNode identNode) {
        if (identNode.isInternal()) {
            noProgramPoint.add(identNode);
        }
        return true;
    }

    @Override
    public Node leaveIdentNode(final IdentNode identNode) {
        return (Node)setProgramPoint(identNode, next());
    }

    @Override
    public Node leaveCallNode(final CallNode callNode) {
        return (Node)setProgramPoint(callNode, next());
    }

    @Override
    public Node leaveAccessNode(final AccessNode accessNode) {
        return (Node)setProgramPoint(accessNode, next());
    }

    @Override
    public Node leaveIndexNode(final IndexNode indexNode) {
        return (Node)setProgramPoint(indexNode, next());
    }

    @Override
    public Node leaveBinaryNode(final BinaryNode binaryNode) {
        return (Node)setProgramPoint(binaryNode, next());
    }

    @Override
    public Node leaveUnaryNode(final UnaryNode unaryNode) {
        return (Node)setProgramPoint(unaryNode, next());
    }
}
