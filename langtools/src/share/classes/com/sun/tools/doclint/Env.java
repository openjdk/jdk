/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.doclint;


import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.util.DocTrees;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.model.JavacTypes;
import com.sun.tools.javac.tree.JCTree;

/**
 * Utility container for current execution environment,
 * providing the current declaration and its doc comment.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 */
public class Env {
    /**
     * Access kinds for declarations.
     */
    public enum AccessKind {
        PRIVATE,
        PACKAGE,
        PROTECTED,
        PUBLIC;

        static boolean accepts(String opt) {
            for (AccessKind g: values())
                if (opt.equals(g.name().toLowerCase())) return true;
            return false;
        }

        static AccessKind of(Set<Modifier> mods) {
            if (mods.contains(Modifier.PUBLIC))
                return AccessKind.PUBLIC;
            else if (mods.contains(Modifier.PROTECTED))
                return AccessKind.PROTECTED;
            else if (mods.contains(Modifier.PRIVATE))
                return AccessKind.PRIVATE;
            else
                return AccessKind.PACKAGE;
        }
    };

    /** Message handler. */
    final Messages messages;

    int implicitHeaderLevel = 0;

    // Utility classes
    DocTrees trees;
    Elements elements;
    Types types;

    // Types used when analysing doc comments.
    TypeMirror java_lang_Error;
    TypeMirror java_lang_RuntimeException;
    TypeMirror java_lang_Throwable;
    TypeMirror java_lang_Void;

    /** The path for the declaration containing the comment currently being analyzed. */
    TreePath currPath;
    /** The element for the declaration containing the comment currently being analyzed. */
    Element currElement;
    /** The comment current being analyzed. */
    DocCommentTree currDocComment;
    /**
     * The access kind of the declaration containing the comment currently being analyzed.
     * This is the minimum (most restrictive) access kind of the declaration itself
     * and that of its containers. For example, a public method in a private class is
     * noted as private.
     */
    AccessKind currAccess;
    /** The set of methods, if any, that the current declaration overrides. */
    Set<? extends ExecutableElement> currOverriddenMethods;

    Env() {
        messages = new Messages(this);
    }

    void init(JavacTask task) {
        init(DocTrees.instance(task), task.getElements(), task.getTypes());
    }

    void init(DocTrees trees, Elements elements, Types types) {
        this.trees = trees;
        this.elements = elements;
        this.types = types;
        java_lang_Error = elements.getTypeElement("java.lang.Error").asType();
        java_lang_RuntimeException = elements.getTypeElement("java.lang.RuntimeException").asType();
        java_lang_Throwable = elements.getTypeElement("java.lang.Throwable").asType();
        java_lang_Void = elements.getTypeElement("java.lang.Void").asType();
    }

    void setImplicitHeaders(int n) {
        implicitHeaderLevel = n;
    }

    /** Set the current declaration and its doc comment. */
    void setCurrent(TreePath path, DocCommentTree comment) {
        currPath = path;
        currDocComment = comment;
        currElement = trees.getElement(currPath);
        currOverriddenMethods = ((JavacTypes) types).getOverriddenMethods(currElement);

        AccessKind ak = AccessKind.PUBLIC;
        for (TreePath p = path; p != null; p = p.getParentPath()) {
            Element e = trees.getElement(p);
            if (e != null && e.getKind() != ElementKind.PACKAGE) {
                ak = min(ak, AccessKind.of(e.getModifiers()));
            }
        }
        currAccess = ak;
    }

    AccessKind getAccessKind() {
        return currAccess;
    }

    long getPos(TreePath p) {
        return ((JCTree) p.getLeaf()).pos;
    }

    long getStartPos(TreePath p) {
        SourcePositions sp = trees.getSourcePositions();
        return sp.getStartPosition(p.getCompilationUnit(), p.getLeaf());
    }

    private <T extends Comparable<T>> T min(T item1, T item2) {
        return (item1 == null) ? item2
                : (item2 == null) ? item1
                : item1.compareTo(item2) <= 0 ? item1 : item2;
    }
}
