/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.nashorn.internal.ir;

import jdk.nashorn.internal.ir.annotations.Ignore;
import jdk.nashorn.internal.runtime.Source;

/**
 * IR base class for break and continue.
 *
 */
public abstract class LabeledNode extends Node {
    /** Optional label. */
    @Ignore
    protected final LabelNode labelNode;

    /** Target control node. */
    @Ignore
    protected final Node targetNode;

    /** Try chain. */
    @Ignore
    protected final TryNode tryChain;

    /** scope nesting level */
    protected int scopeNestingLevel;

    /**
     * Constructor
     *
     * @param source     the source
     * @param token      token
     * @param finish     finish
     * @param labelNode  the label node
     * @param targetNode the place to break to
     * @param tryChain   the try chain
     */
    public LabeledNode(final Source source, final long token, final int finish, final LabelNode labelNode, final Node targetNode, final TryNode tryChain) {
        super(source, token, finish);

        this.labelNode  = labelNode;
        this.targetNode = targetNode;
        this.tryChain   = tryChain;
    }

    /**
     * Copy constructor
     *
     * @param labeledNode source node
     * @param cs          copy state
     */
    protected LabeledNode(final LabeledNode labeledNode, final CopyState cs) {
        super(labeledNode);

        this.labelNode         = (LabelNode)cs.existingOrCopy(labeledNode.labelNode);
        this.targetNode        = cs.existingOrSame(labeledNode.targetNode);
        this.tryChain          = (TryNode)cs.existingOrSame(labeledNode.tryChain);
        this.scopeNestingLevel = labeledNode.scopeNestingLevel;
    }

    /**
     * Get the label
     * @return the label
     */
    public LabelNode getLabel() {
        return labelNode;
    }

    /**
     * Get the target node
     * @return the target node
     */
    public Node getTargetNode() {
        return targetNode;
    }

    /**
     * Get the surrounding try chain
     * @return the try chain
     */
    public TryNode getTryChain() {
        return tryChain;
    }

    /**
     * Get the scope nesting level
     * @return nesting level
     */
    public int getScopeNestingLevel() {
        return scopeNestingLevel;
    }

    /**
     * Set scope nesting level
     * @param level the new level
     */
    public void setScopeNestingLevel(final int level) {
        scopeNestingLevel = level;
    }
}
