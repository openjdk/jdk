/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.phases.graph;

import java.util.List;

import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.ControlSplitNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopEndNode;

public abstract class MergeableState<T> {

    @Override
    public abstract T clone();

    /**
     * This method is called on merge on the state of the first branch. The {@code withStates} list
     * contains the states of the of the other branches in the order of the merge's end nodes.
     *
     * @param merge the merge node
     * @param withStates the state at the merge's end node except the first one.
     */
    public abstract boolean merge(AbstractMergeNode merge, List<T> withStates);

    /**
     * This method is called before a loop is entered (before the {@link LoopBeginNode} is visited).
     *
     * @param loopBegin the begin node of the loop
     */
    public void loopBegin(LoopBeginNode loopBegin) {
        // empty default implementation
    }

    /**
     * This method is called after all {@link LoopEndNode}s belonging to a loop have been visited.
     *
     * @param loopBegin the begin node of the loop
     * @param loopEndStates the states at the loop ends, sorted according to
     *            {@link LoopBeginNode#orderedLoopEnds()}
     */
    public void loopEnds(LoopBeginNode loopBegin, List<T> loopEndStates) {
        // empty default implementation
    }

    /**
     * This method is called before the successors of a {@link ControlSplitNode} are visited.
     *
     * @param node the successor of the control split that is about to be visited
     */
    public void afterSplit(AbstractBeginNode node) {
        // empty default implementation
    }
}
