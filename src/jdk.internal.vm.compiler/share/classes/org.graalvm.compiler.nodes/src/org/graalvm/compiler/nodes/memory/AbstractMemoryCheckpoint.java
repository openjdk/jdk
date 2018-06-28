/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.nodes.memory;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.AbstractStateSplit;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.StateSplit;

/**
 * Provides an implementation of {@link StateSplit}.
 */
@NodeInfo
public abstract class AbstractMemoryCheckpoint extends AbstractStateSplit implements MemoryCheckpoint {

    public static final NodeClass<AbstractMemoryCheckpoint> TYPE = NodeClass.create(AbstractMemoryCheckpoint.class);

    protected AbstractMemoryCheckpoint(NodeClass<? extends AbstractMemoryCheckpoint> c, Stamp stamp) {
        this(c, stamp, null);
    }

    protected AbstractMemoryCheckpoint(NodeClass<? extends AbstractMemoryCheckpoint> c, Stamp stamp, FrameState stateAfter) {
        super(c, stamp, stateAfter);
    }
}
