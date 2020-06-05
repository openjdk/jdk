/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.nodes;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ValueNode;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * This node class can be used to create {@link MacroNode}s for simple pure functions like
 * {@link System#identityHashCode(Object)}.
 */
@NodeInfo
public abstract class PureFunctionMacroNode extends MacroStateSplitNode implements Canonicalizable {

    public static final NodeClass<PureFunctionMacroNode> TYPE = NodeClass.create(PureFunctionMacroNode.class);

    public PureFunctionMacroNode(NodeClass<? extends MacroNode> c, MacroParams p) {
        super(c, p);
    }

    /**
     * This method should return either a constant that represents the result of the function, or
     * null if no such result could be determined.
     */
    protected abstract JavaConstant evaluate(JavaConstant param, MetaAccessProvider metaAccess);

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (tool.allUsagesAvailable() && hasNoUsages()) {
            return null;
        } else {
            ValueNode param = arguments.get(0);
            if (param.isConstant()) {
                JavaConstant constant = evaluate(param.asJavaConstant(), tool.getMetaAccess());
                if (constant != null) {
                    return ConstantNode.forConstant(constant, tool.getMetaAccess());
                }
            }
        }
        return this;
    }
}
