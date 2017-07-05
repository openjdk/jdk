/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
 * A state split is a node that may have a frame state associated with it.
 */
public interface StateSplit extends NodeWithState {

    /**
     * Gets the {@link FrameState} corresponding to the state of the JVM after execution of this
     * node.
     */
    FrameState stateAfter();

    /**
     * Sets the {@link FrameState} corresponding to the state of the JVM after execution of this
     * node.
     */
    void setStateAfter(FrameState x);

    /**
     * Determines if this node has a side-effect. Such nodes cannot be safely re-executed because
     * they modify state which is visible to other threads or modify state beyond what is captured
     * in {@link FrameState} nodes.
     */
    boolean hasSideEffect();
}
