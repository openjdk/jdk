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


package org.graalvm.compiler.replacements.arraycopy;

import static jdk.internal.vm.compiler.word.LocationIdentity.any;

import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.replacements.nodes.BasicArrayCopyNode;
import jdk.internal.vm.compiler.word.LocationIdentity;

@NodeInfo
public final class ArrayCopyNode extends BasicArrayCopyNode implements Lowerable {

    public static final NodeClass<ArrayCopyNode> TYPE = NodeClass.create(ArrayCopyNode.class);

    protected final boolean forceAnyLocation;

    public ArrayCopyNode(int bci, ValueNode src, ValueNode srcPos, ValueNode dst, ValueNode dstPos, ValueNode length) {
        this(bci, src, srcPos, dst, dstPos, length, false);
    }

    public ArrayCopyNode(int bci, ValueNode src, ValueNode srcPos, ValueNode dst, ValueNode dstPos, ValueNode length, boolean forceAnyLocation) {
        super(TYPE, src, srcPos, dst, dstPos, length, null, bci);
        this.forceAnyLocation = forceAnyLocation;
        if (!forceAnyLocation) {
            elementKind = ArrayCopy.selectComponentKind(this);
        } else {
            assert elementKind == null;
        }
    }

    public ArrayCopyNode(ArrayCopy arraycopy) {
        this(arraycopy.getBci(), arraycopy.getSource(), arraycopy.getSourcePosition(), arraycopy.getDestination(), arraycopy.getDestinationPosition(), arraycopy.getLength());
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        if (!forceAnyLocation && elementKind == null) {
            elementKind = ArrayCopy.selectComponentKind(this);
        }
        if (elementKind != null) {
            return NamedLocationIdentity.getArrayLocation(elementKind);
        }
        return any();
    }

    public boolean killsAnyLocation() {
        return forceAnyLocation;
    }
}
