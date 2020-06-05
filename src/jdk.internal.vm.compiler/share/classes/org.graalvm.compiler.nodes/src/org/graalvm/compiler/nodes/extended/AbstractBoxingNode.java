/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.nodes.extended;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_2;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValueNodeUtil;
import org.graalvm.compiler.nodes.memory.MemoryAccess;
import org.graalvm.compiler.nodes.memory.MemoryKill;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.Virtualizable;
import jdk.internal.vm.compiler.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

@NodeInfo(cycles = CYCLES_2, size = SIZE_2)
public abstract class AbstractBoxingNode extends FixedWithNextNode implements Virtualizable, Lowerable, Canonicalizable.Unary<ValueNode>, MemoryAccess {
    public static final NodeClass<AbstractBoxingNode> TYPE = NodeClass.create(AbstractBoxingNode.class);

    @Input protected ValueNode value;
    protected final JavaKind boxingKind;
    protected final LocationIdentity accessedLocation;

    public AbstractBoxingNode(NodeClass<? extends AbstractBoxingNode> c, ValueNode value, JavaKind boxingKind, Stamp s, LocationIdentity accessedLocation) {
        super(c, s);
        this.value = value;
        this.boxingKind = boxingKind;
        this.accessedLocation = accessedLocation;
    }

    public static ResolvedJavaField getValueField(ResolvedJavaType resultType) {
        for (ResolvedJavaField f : resultType.getInstanceFields(false)) {
            if (f.getName().equals("value")) {
                return f;
            }
        }
        throw GraalError.shouldNotReachHere();
    }

    @Override
    public ValueNode getValue() {
        return value;
    }

    public JavaKind getBoxingKind() {
        return boxingKind;
    }

    @Override
    public MemoryKill getLastLocationAccess() {
        return lastLocationAccess;
    }

    @OptionalInput(InputType.Memory) MemoryKill lastLocationAccess;

    @Override
    public void setLastLocationAccess(MemoryKill newlla) {
        updateUsages(ValueNodeUtil.asNode(lastLocationAccess), ValueNodeUtil.asNode(newlla));
        lastLocationAccess = newlla;
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return accessedLocation;
    }
}
