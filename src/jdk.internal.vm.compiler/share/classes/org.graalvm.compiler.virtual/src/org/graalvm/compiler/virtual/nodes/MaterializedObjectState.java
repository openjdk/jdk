/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.virtual.nodes;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.virtual.EscapeObjectState;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;

/**
 * This class encapsulated the materialized state of an escape analyzed object.
 */
@NodeInfo
public final class MaterializedObjectState extends EscapeObjectState implements Node.ValueNumberable {

    public static final NodeClass<MaterializedObjectState> TYPE = NodeClass.create(MaterializedObjectState.class);
    @Input ValueNode materializedValue;

    public ValueNode materializedValue() {
        return materializedValue;
    }

    public MaterializedObjectState(VirtualObjectNode object, ValueNode materializedValue) {
        super(TYPE, object);
        this.materializedValue = materializedValue;
    }

    @Override
    public MaterializedObjectState duplicateWithVirtualState() {
        return graph().addWithoutUnique(new MaterializedObjectState(object(), materializedValue));
    }

    @Override
    public void applyToNonVirtual(NodeClosure<? super ValueNode> closure) {
        closure.apply(this, materializedValue);
    }
}
