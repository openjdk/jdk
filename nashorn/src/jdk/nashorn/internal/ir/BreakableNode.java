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

import java.util.List;
import jdk.nashorn.internal.codegen.Label;

/**
 * This class represents a node from which control flow can execute
 * a {@code break} statement
 */
public interface BreakableNode extends LexicalContextNode {
    /**
     * Ensure that any labels in this breakable node are unique so
     * that new jumps won't go to old parts of the tree. Used for
     * example for cloning finally blocks
     *
     * @param lc the lexical context
     * @return node after labels have been made unique
     */
    public abstract Node ensureUniqueLabels(final LexicalContext lc);

    /**
     * Check whether this can be broken out from without using a label,
     * e.g. everything but Blocks, basically
     * @return true if breakable without label
     */
    public boolean isBreakableWithoutLabel();

    /**
     * Return the break label, i.e. the location to go to on break.
     * @return the break label
     */
    public Label getBreakLabel();

    /**
     * Return the labels associated with this node. Breakable nodes that
     * aren't LoopNodes only have a break label - the location immediately
     * afterwards the node in code
     * @return list of labels representing locations around this node
     */
    public List<Label> getLabels();
}
