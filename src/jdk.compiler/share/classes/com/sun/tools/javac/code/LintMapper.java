/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.code;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import javax.tools.JavaFileObject;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;

/**
 * Maps source code positions to the applicable {@link Lint} instance.
 *
 * <p>
 * Because {@code @SuppressWarnings} is a Java symbol, in general this mapping can't be
 * calculated until after attribution. As each top-level declaration (class, package, or module)
 * is attributed, this singleton is notified and the {@link Lint}s that apply to every source
 * position within that top-level declaration are calculated.
 *
 * <p>
 * The method {@link #lintAt} returns the {@link Lint} instance applicable to source position;
 * if it can't be determined yet, an empty {@link Optional} is returned.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 */
public class LintMapper {

    // The key for the context singleton
    private static final Context.Key<LintMapper> CONTEXT_KEY = new Context.Key<>();

    // Per-source file information. Note: during the parsing of a file, an entry exists but the FileInfo value is null
    private final Map<JavaFileObject, FileInfo> fileInfoMap = new HashMap<>();

    // Compiler context
    private final Context context;

    // These are initialized lazily; see initializeIfNeeded()
    private Lint rootLint;

    /**
     * Obtain the {@link LintMapper} context singleton.
     */
    public static LintMapper instance(Context context) {
        LintMapper instance = context.get(CONTEXT_KEY);
        if (instance == null)
            instance = new LintMapper(context);
        return instance;
    }

    /**
     * Constructor.
     */
    @SuppressWarnings("this-escape")
    protected LintMapper(Context context) {
        context.put(CONTEXT_KEY, this);
        this.context = context;
    }

    // Lazy initialization to avoid dependency loops
    private void initializeIfNeeded() {
        if (rootLint == null)
            rootLint = Lint.instance(context);
    }

// Lint Operations

    /**
     * Determine if the given file is known to this instance.
     *
     * @param sourceFile source file
     * @return true if file is recognized
     */
    public boolean isKnown(JavaFileObject sourceFile) {
        return fileInfoMap.containsKey(sourceFile);
    }

    /**
     * Obtain the {@link Lint} configuration that applies at the given position, if known.
     *
     * @param sourceFile source file
     * @param pos source position
     * @return the applicable {@link Lint}, if known, otherwise empty
     */
    public Optional<Lint> lintAt(JavaFileObject sourceFile, DiagnosticPosition pos) {
        initializeIfNeeded();
        FileInfo fileInfo = fileInfoMap.get(sourceFile);
        if (fileInfo != null)
            return fileInfo.lintAt(pos);
        return Optional.empty();
    }

    /**
     * Calculate {@lint Lint} configurations for all positions within the given top-level declaration.
     *
     * @param sourceFile source file
     * @param tree top-level declaration (class, package, or module)
     */
    public void calculateLints(JavaFileObject sourceFile, JCTree tree) {
        Assert.check(rootLint != null);
        fileInfoMap.get(sourceFile).afterAttr(tree);
    }

    /**
     * Reset this instance.
     */
    public void clear() {
        fileInfoMap.clear();
    }

// Parsing Notifications

    /**
     * Invoked when file parsing starts to create an entry for the new file (but with a null value).
     */
    public void startParsingFile(JavaFileObject sourceFile) {
        initializeIfNeeded();
        fileInfoMap.put(sourceFile, null);
    }

    /**
     * Invoked when file parsing completes to put in place a corresponding {@link FileInfo}.
     */
    public void finishParsingFile(JCCompilationUnit tree) {
        Assert.check(rootLint != null);
        fileInfoMap.put(tree.sourcefile, new FileInfo(rootLint, tree));
    }

// FileInfo

    /**
     * Holds {@link Lint} information for a fully parsed source file.
     *
     * <p>
     * Initially (immediately after parsing), "unmappedDecls" contains a {@link Span} corresponding to each
     * top-level declaration in the source file. As each top-level declaration is attributed, the corresponding
     * {@link Span} is removed and the corresponding {@link LintRange} subtree is populated under "rootRange".
     */
    private static class FileInfo {

        final LintRange rootRange;                              // the root LintRange (covering the entire source file)
        final List<Span> unmappedDecls = new LinkedList<>();    // unmapped top-level declarations awaiting attribution

        // After parsing: Add top-level declarations to our "unmappedDecls" list
        FileInfo(Lint rootLint, JCCompilationUnit tree) {
            rootRange = new LintRange(rootLint);
            for (JCTree decl : tree.defs) {
                if (isTopLevelDecl(decl))
                    unmappedDecls.add(new Span(decl));
            }
        }

        // After attribution: Discard the span from "unmappedDecls" and populate the declaration's subtree under "rootRange"
        void afterAttr(JCTree tree) {
            for (Iterator<Span> i = unmappedDecls.iterator(); i.hasNext(); ) {
                if (i.next().contains(tree.pos())) {
                    rootRange.populateSubtree(tree);
                    i.remove();
                    return;
                }
            }
            throw new AssertionError("top-level declaration not found");
        }

        // Find the most specific Lint configuration applying to the given position, unless the position has not been mapped yet
        Optional<Lint> lintAt(DiagnosticPosition pos) {
            for (Span span : unmappedDecls) {
                if (span.contains(pos))
                    return Optional.empty();
            }
            return Optional.of(rootRange.bestMatch(pos).lint);
        }

        boolean isTopLevelDecl(JCTree tree) {
            return tree.getTag() == Tag.MODULEDEF
                || tree.getTag() == Tag.PACKAGEDEF
                || tree.getTag() == Tag.CLASSDEF;
        }
    }

// Span

    /**
     * A lexical range.
     */
    private record Span(int startPos, int endPos) {

        static final Span MAXIMAL = new Span(Integer.MIN_VALUE, Integer.MAX_VALUE);

        Span(JCTree tree) {
            this(TreeInfo.getStartPos(tree), TreeInfo.getEndPos(tree));
        }

        boolean contains(DiagnosticPosition pos) {
            int offset = pos.getLintPosition();
            return offset == startPos || (offset > startPos && offset < endPos);
        }

        boolean contains(Span that) {
            return this.startPos <= that.startPos && this.endPos >= that.endPos;
        }
    }

// LintRange

    /**
     * A tree of nested lexical ranges and the {@link Lint} configurations that apply therein.
     */
    private record LintRange(
        Span span,                                      // declaration's lexical range
        Lint lint,                                      // the Lint configuration that applies at this declaration
        List<LintRange> children                        // the nested declarations one level below this node
    ) {

        // Create a node representing the entire file, using the root lint configuration
        LintRange(Lint rootLint) {
            this(Span.MAXIMAL, rootLint, new LinkedList<>());
        }

        // Create a node representing the given declaration and its corresponding Lint configuration
        LintRange(JCTree tree, Lint lint) {
            this(new Span(tree), lint, new LinkedList<>());
        }

        // Find the most specific node in this tree (including me) that contains the given position, if any
        LintRange bestMatch(DiagnosticPosition pos) {
            LintRange bestMatch = null;
            for (LintRange child : children) {
                if (!child.span.contains(pos))          // don't recurse unless necessary
                    continue;
                LintRange childBestMatch = child.bestMatch(pos);
                if (childBestMatch != null && (bestMatch == null || bestMatch.span.contains(childBestMatch.span)))
                    bestMatch = childBestMatch;
            }
            if (bestMatch == null)
                bestMatch = span.contains(pos) ? this : null;
            return bestMatch;
        }

        // Populate a sparse subtree corresponding to the given nested declaration.
        // Only when the Lint configuration differs from the parent is a node added.
        void populateSubtree(JCTree tree) {
            new TreeScanner() {

                private LintRange currentNode = LintRange.this;

                @Override
                public void visitModuleDef(JCModuleDecl tree) {
                    scanDecl(tree, tree.sym, super::visitModuleDef);
                }
                @Override
                public void visitPackageDef(JCPackageDecl tree) {
                    scanDecl(tree, tree.packge, super::visitPackageDef);
                }
                @Override
                public void visitClassDef(JCClassDecl tree) {
                    scanDecl(tree, tree.sym, super::visitClassDef);
                }
                @Override
                public void visitMethodDef(JCMethodDecl tree) {
                    scanDecl(tree, tree.sym, super::visitMethodDef);
                }
                @Override
                public void visitVarDef(JCVariableDecl tree) {
                    scanDecl(tree, tree.sym, super::visitVarDef);
                }

                private <T extends JCTree> void scanDecl(T tree, Symbol symbol, Consumer<? super T> recursor) {

                    // The "symbol" can be null if there were earlier errors; skip this declaration if so
                    if (symbol == null) {
                        recursor.accept(tree);
                        return;
                    }

                    // Update the Lint using the declaration; if there's no change, then we don't need a new node here
                    Lint newLint = currentNode.lint.augment(symbol);
                    if (newLint == currentNode.lint) {  // note: lint.augment() returns the same instance if there's no change
                        recursor.accept(tree);
                        return;
                    }

                    // Add a new node here and proceed
                    final LintRange previousNode = currentNode;
                    currentNode = new LintRange(tree, newLint);
                    previousNode.children.add(currentNode);
                    try {
                        recursor.accept(tree);
                    } finally {
                        currentNode = previousNode;
                    }
                }
            }.scan(tree);
        }
    }
}
