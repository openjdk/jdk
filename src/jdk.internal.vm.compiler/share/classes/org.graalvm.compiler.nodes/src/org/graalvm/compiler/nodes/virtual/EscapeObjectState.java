/*
 * Copyright (c) 2009, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.nodes.virtual;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_0;

import org.graalvm.compiler.graph.Node.ValueNumberable;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.VirtualState;

@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
public abstract class EscapeObjectState extends VirtualState implements ValueNumberable {
    public static final NodeClass<EscapeObjectState> TYPE = NodeClass.create(EscapeObjectState.class);

    @Input protected VirtualObjectNode object;

    public VirtualObjectNode object() {
        return object;
    }

    public EscapeObjectState(NodeClass<? extends EscapeObjectState> c, VirtualObjectNode object) {
        super(c);
        this.object = object;
    }

    @Override
    public abstract EscapeObjectState duplicateWithVirtualState();

    @Override
    public boolean isPartOfThisState(VirtualState state) {
        return this == state;
    }

    @Override
    public void applyToVirtual(VirtualClosure closure) {
        closure.apply(this);
    }
}
