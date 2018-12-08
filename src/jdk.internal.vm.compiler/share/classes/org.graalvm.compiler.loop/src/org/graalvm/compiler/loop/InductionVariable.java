/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.loop;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;

/**
 * This class describes a value node that is an induction variable in a counted loop.
 */
public abstract class InductionVariable {

    public enum Direction {
        Up,
        Down;

        public Direction opposite() {
            switch (this) {
                case Up:
                    return Down;
                case Down:
                    return Up;
                default:
                    throw GraalError.shouldNotReachHere();
            }
        }
    }

    public abstract StructuredGraph graph();

    protected final LoopEx loop;

    public InductionVariable(LoopEx loop) {
        this.loop = loop;
    }

    public LoopEx getLoop() {
        return loop;
    }

    public abstract Direction direction();

    /**
     * Returns the value node that is described by this induction variable.
     */
    public abstract ValueNode valueNode();

    /**
     * Returns the node that gives the initial value of this induction variable.
     */
    public abstract ValueNode initNode();

    /**
     * Returns the stride of the induction variable. The stride is the value that is added to the
     * induction variable at each iteration.
     */
    public abstract ValueNode strideNode();

    public abstract boolean isConstantInit();

    public abstract boolean isConstantStride();

    public abstract long constantInit();

    public abstract long constantStride();

    /**
     * Returns the extremum value of the induction variable. The extremum value is the value of the
     * induction variable in the loop body of the last iteration, only taking into account the main
     * loop limit test. It's possible for the loop to exit before this value if
     * {@link CountedLoopInfo#isExactTripCount()} returns false for the containing loop.
     */
    public ValueNode extremumNode() {
        return extremumNode(false, valueNode().stamp(NodeView.DEFAULT));
    }

    public abstract ValueNode extremumNode(boolean assumePositiveTripCount, Stamp stamp);

    public abstract boolean isConstantExtremum();

    public abstract long constantExtremum();

    /**
     * Returns the exit value of the induction variable. The exit value is the value of the
     * induction variable at the loop exit.
     */
    public abstract ValueNode exitValueNode();

    /**
     * Deletes any nodes created within the scope of this object that have no usages.
     */
    public abstract void deleteUnusedNodes();
}
