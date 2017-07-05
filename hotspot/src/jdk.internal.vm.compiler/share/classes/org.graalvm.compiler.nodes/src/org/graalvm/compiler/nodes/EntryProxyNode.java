/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.nodeinfo.InputType.Association;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_0;

import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.ValueProxy;

/**
 * Proxy node that is used in OSR. This node drops the stamp information from the value, since the
 * types we see during OSR may be too precise (if a branch was not parsed for example).
 */
@NodeInfo(nameTemplate = "EntryProxy({i#value})", cycles = CYCLES_0, size = SIZE_0)
public final class EntryProxyNode extends FloatingNode implements ValueProxy {

    public static final NodeClass<EntryProxyNode> TYPE = NodeClass.create(EntryProxyNode.class);
    @Input(Association) EntryMarkerNode proxyPoint;
    @Input ValueNode value;

    public EntryProxyNode(ValueNode value, EntryMarkerNode proxyPoint) {
        super(TYPE, value.stamp().unrestricted());
        this.value = value;
        this.proxyPoint = proxyPoint;
    }

    public ValueNode value() {
        return value;
    }

    @Override
    public ValueNode getOriginalNode() {
        return value();
    }
}
