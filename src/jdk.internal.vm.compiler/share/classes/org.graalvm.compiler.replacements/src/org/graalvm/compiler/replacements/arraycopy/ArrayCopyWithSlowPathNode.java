/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.nodes.BasicArrayCopyNode;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.JavaKind;

@NodeInfo(allowedUsageTypes = InputType.Memory)
public final class ArrayCopyWithSlowPathNode extends BasicArrayCopyNode {

    public static final NodeClass<ArrayCopyWithSlowPathNode> TYPE = NodeClass.create(ArrayCopyWithSlowPathNode.class);

    private final SnippetTemplate.SnippetInfo snippet;

    public ArrayCopyWithSlowPathNode(ValueNode src, ValueNode srcPos, ValueNode dest, ValueNode destPos, ValueNode length, SnippetTemplate.SnippetInfo snippet, JavaKind elementKind) {
        super(TYPE, src, srcPos, dest, destPos, length, elementKind, BytecodeFrame.INVALID_FRAMESTATE_BCI);
        assert StampTool.isPointerNonNull(src) && StampTool.isPointerNonNull(dest) : "must have been null checked";
        this.snippet = snippet;
    }

    @NodeIntrinsic
    public static native void arraycopy(Object nonNullSrc, int srcPos, Object nonNullDest, int destPos, int length, @ConstantNodeParameter SnippetTemplate.SnippetInfo snippet,
                    @ConstantNodeParameter JavaKind elementKind);

    public SnippetTemplate.SnippetInfo getSnippet() {
        return snippet;
    }

    public void setBci(int bci) {
        this.bci = bci;
    }
}
