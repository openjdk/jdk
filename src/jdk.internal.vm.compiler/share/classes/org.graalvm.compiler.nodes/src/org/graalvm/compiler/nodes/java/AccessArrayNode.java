/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.nodes.java;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;

/**
 * This the base class of all array operations.
 */
@NodeInfo
public abstract class AccessArrayNode extends FixedWithNextNode {

    public static final NodeClass<AccessArrayNode> TYPE = NodeClass.create(AccessArrayNode.class);
    @Input protected ValueNode array;

    public ValueNode array() {
        return array;
    }

    /**
     * Creates a new AccessArrayNode.
     *
     * @param array the instruction that produces the array object value
     */
    public AccessArrayNode(NodeClass<? extends AccessArrayNode> c, Stamp stamp, ValueNode array) {
        super(c, stamp);
        this.array = array;
    }

    public void setArray(ValueNode array) {
        updateUsages(this.array, array);
        this.array = array;
    }
}
