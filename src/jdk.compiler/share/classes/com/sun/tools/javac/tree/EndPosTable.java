/*
 * Copyright (c) 2011, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.tree;

import com.sun.tools.javac.util.Position;

/**
 * Specifies the methods to access a mappings of syntax trees to end positions.
 *
 * <p>
 * Implementations <b>must</b> store end positions for at least these node types:
 * <ul>
 *  <li>{@link JCTree.JCModuleDecl}
 *  <li>{@link JCTree.JCPackageDecl}
 *  <li>{@link JCTree.JCClassDecl}
 *  <li>{@link JCTree.JCMethodDecl}
 *  <li>{@link JCTree.JCVariableDecl}
 * </ul>
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 */
public interface EndPosTable {

    /**
     * This method will return the end position of a given tree, otherwise a
     * Positions.NOPOS will be returned.
     * @param tree JCTree
     * @return position of the source tree or Positions.NOPOS for non-existent mapping
     */
    int getEndPos(JCTree tree);

    /**
     * Store ending position for a tree, the value of which is the greater of
     * last error position and the given ending position.
     * @param tree The tree.
     * @param endpos The ending position to associate with the tree.
     * @return the {@code tree}
     */
    <T extends JCTree> T storeEnd(T tree, int endpos);

    /**
     * Set the error position during the parsing phases, the value of which
     * will be set only if it is greater than the last stored error position.
     * @param errPos The error position
     */
    void setErrorEndPos(int errPos);

    /**
     * Give an old tree and a new tree, the old tree will be replaced with
     * the new tree, the position of the new tree will be that of the old
     * tree.
     * @param oldtree a JCTree to be replaced
     * @param newtree a JCTree to be replaced with, or null to just remove {@code oldtree}
     * @return position of the old tree or Positions.NOPOS for non-existent mapping
     */
    int replaceTree(JCTree oldtree, JCTree newtree);
}
