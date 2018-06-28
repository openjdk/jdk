/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.nodes.profiling;

import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;

import jdk.vm.ci.meta.ResolvedJavaMethod;

@NodeInfo
public class ProfileBranchNode extends ProfileWithNotificationNode {
    public static final NodeClass<ProfileBranchNode> TYPE = NodeClass.create(ProfileBranchNode.class);

    @OptionalInput ValueNode branchCondition;
    protected int bci;
    protected int targetBci;

    public ProfileBranchNode(ResolvedJavaMethod method, int freqLog, int probabilityLog, ConditionalNode branchCondition, int bci, int targetBci) {
        super(TYPE, method, freqLog, probabilityLog);
        assert targetBci <= bci;
        this.branchCondition = branchCondition;
        this.bci = bci;
        this.targetBci = targetBci;
    }

    public ProfileBranchNode(ResolvedJavaMethod method, int freqLog, int probabilityLog, int bci, int targetBci) {
        super(TYPE, method, freqLog, probabilityLog);
        assert targetBci <= bci;
        this.branchCondition = null;
        this.bci = bci;
        this.targetBci = targetBci;
    }

    public int bci() {
        return bci;
    }

    public int targetBci() {
        return targetBci;
    }

    public ValueNode branchCondition() {
        return branchCondition;
    }

    public boolean hasCondition() {
        return branchCondition != null;
    }

    @Override
    protected boolean canBeMergedWith(ProfileNode p) {
        if (p instanceof ProfileBranchNode) {
            ProfileBranchNode that = (ProfileBranchNode) p;
            return this.method.equals(that.method) && this.bci == that.bci;
        }
        return false;
    }

    /**
     * Gathers all the {@link ProfileBranchNode}s that are inputs to the
     * {@linkplain StructuredGraph#getNodes() live nodes} in a given graph.
     */
    public static NodeIterable<ProfileBranchNode> getProfileBranchNodes(StructuredGraph graph) {
        return graph.getNodes().filter(ProfileBranchNode.class);
    }
}
