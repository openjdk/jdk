/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.spi.Proxy;

@NodeInfo(allowedUsageTypes = {InputType.Guard}, nameTemplate = "Proxy({i#value})")
public final class GuardProxyNode extends ProxyNode implements GuardingNode, Proxy, LIRLowerable, Canonicalizable {

    public static final NodeClass<GuardProxyNode> TYPE = NodeClass.create(GuardProxyNode.class);
    @OptionalInput(InputType.Guard) GuardingNode value;

    public GuardProxyNode(GuardingNode value, LoopExitNode proxyPoint) {
        super(TYPE, StampFactory.forVoid(), proxyPoint);
        this.value = value;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
    }

    public void setValue(GuardingNode newValue) {
        this.updateUsages(value.asNode(), newValue.asNode());
        this.value = newValue;
    }

    @Override
    public ValueNode value() {
        return (value == null ? null : value.asNode());
    }

    @Override
    public Node getOriginalNode() {
        return (value == null ? null : value.asNode());
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (value == null) {
            return null;
        }
        return this;
    }
}
