/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.nodes;

import static org.graalvm.compiler.nodeinfo.InputType.Condition;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_0;

import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;

import jdk.vm.ci.meta.TriState;

/**
 * Logic node that negates its argument.
 */
@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
public final class LogicNegationNode extends LogicNode implements Canonicalizable.Unary<LogicNode> {

    public static final NodeClass<LogicNegationNode> TYPE = NodeClass.create(LogicNegationNode.class);
    @Input(Condition) LogicNode value;

    public LogicNegationNode(LogicNode value) {
        super(TYPE);
        this.value = value;
    }

    public static LogicNode create(LogicNode value) {
        LogicNode synonym = findSynonym(value);
        if (synonym != null) {
            return synonym;
        }
        return new LogicNegationNode(value);
    }

    private static LogicNode findSynonym(LogicNode value) {
        if (value instanceof LogicConstantNode) {
            LogicConstantNode logicConstantNode = (LogicConstantNode) value;
            return LogicConstantNode.forBoolean(!logicConstantNode.getValue());
        } else if (value instanceof LogicNegationNode) {
            return ((LogicNegationNode) value).getValue();
        }
        return null;
    }

    @Override
    public LogicNode getValue() {
        return value;
    }

    @Override
    public LogicNode canonical(CanonicalizerTool tool, LogicNode forValue) {
        LogicNode synonym = findSynonym(forValue);
        if (synonym != null) {
            return synonym;
        }
        return this;
    }

    @Override
    public TriState implies(boolean thisNegated, LogicNode other) {
        if (other == getValue()) {
            return TriState.get(thisNegated);
        }
        return getValue().implies(!thisNegated, other);
    }
}
