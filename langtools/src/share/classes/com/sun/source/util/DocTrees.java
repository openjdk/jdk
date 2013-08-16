/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.source.util;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.JavaCompiler.CompilationTask;

import com.sun.source.doctree.DocCommentTree;
import javax.tools.Diagnostic;

/**
 * Provides access to syntax trees for doc comments.
 *
 * @since 1.8
 */
@jdk.Supported
public abstract class DocTrees extends Trees {
    /**
     * Gets a DocTrees object for a given CompilationTask.
     * @param task the compilation task for which to get the Trees object
     * @throws IllegalArgumentException if the task does not support the Trees API.
     */
    public static DocTrees instance(CompilationTask task) {
        return (DocTrees) Trees.instance(task);
    }

    /**
     * Gets a DocTrees object for a given ProcessingEnvironment.
     * @param env the processing environment for which to get the Trees object
     * @throws IllegalArgumentException if the env does not support the Trees API.
     */
    public static DocTrees instance(ProcessingEnvironment env) {
        if (!env.getClass().getName().equals("com.sun.tools.javac.processing.JavacProcessingEnvironment"))
            throw new IllegalArgumentException();
        return (DocTrees) getJavacTrees(ProcessingEnvironment.class, env);
    }

    /**
     * Gets the doc comment tree, if any, for the Tree node identified by a given TreePath.
     * Returns null if no doc comment was found.
     */
    public abstract DocCommentTree getDocCommentTree(TreePath path);

    /**
     * Gets the language model element referred to by the leaf node of the given
     * {@link DocTreePath}, or null if unknown.
     */
    public abstract Element getElement(DocTreePath path);

    public abstract DocSourcePositions getSourcePositions();

    /**
     * Prints a message of the specified kind at the location of the
     * tree within the provided compilation unit
     *
     * @param kind the kind of message
     * @param msg  the message, or an empty string if none
     * @param t    the tree to use as a position hint
     * @param root the compilation unit that contains tree
     */
    public abstract void printMessage(Diagnostic.Kind kind, CharSequence msg,
            com.sun.source.doctree.DocTree t,
            com.sun.source.doctree.DocCommentTree c,
            com.sun.source.tree.CompilationUnitTree root);
}
