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


package org.graalvm.compiler.nodes;

import org.graalvm.compiler.nodes.spi.NodeWithState;

/**
 * Interface implemented by nodes which may need {@linkplain FrameState deoptimization information}.
 * <p>
 * Sub-interfaces are used to specify exactly when the deoptimization can take place:
 * {@linkplain DeoptBefore before}, {@linkplain DeoptAfter after}, and/or {@linkplain DeoptDuring
 * during}. <br>
 * Note that these sub-interfaces are not mutually exclusive so that nodes that may deoptimize at
 * multiple times can be modeled.
 */
public interface DeoptimizingNode extends NodeWithState {

    /**
     * Determines if this node needs deoptimization information.
     */
    boolean canDeoptimize();

    /**
     * Interface for nodes that need a {@link FrameState} for deoptimizing to a point before their
     * execution.
     */
    public interface DeoptBefore extends DeoptimizingNode {

        /**
         * Sets the {@link FrameState} describing the program state before the execution of this
         * node.
         */
        void setStateBefore(FrameState state);

        FrameState stateBefore();

        default boolean canUseAsStateDuring() {
            return false;
        }
    }

    /**
     * Interface for nodes that need a {@link FrameState} for deoptimizing to a point after their
     * execution.
     */
    public interface DeoptAfter extends DeoptimizingNode, StateSplit {
    }

    /**
     * Interface for nodes that need a special {@link FrameState} for deoptimizing during their
     * execution (e.g. {@link Invoke}).
     */
    public interface DeoptDuring extends DeoptimizingNode, StateSplit {

        FrameState stateDuring();

        /**
         * Sets the {@link FrameState} describing the program state during the execution of this
         * node.
         */
        void setStateDuring(FrameState state);

        /**
         * Compute the {@link FrameState} describing the program state during the execution of this
         * node from an input {@link FrameState} describing the program state after finishing the
         * execution of this node.
         */
        void computeStateDuring(FrameState stateAfter);
    }
}
