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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;

import com.sun.tools.javac.tree.EndPosTable;
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
        return Optional.of(sourceFile)
          .map(fileInfoMap::get)
          .flatMap(fileInfo -> fileInfo.lintAt(pos));
    }

    /**
     * Calculate {@lint Lint} configurations for all positions within the given top-level declaration.
     *
     * @param sourceFile source file
     * @param tree top-level declaration (class, package, or module)
     */
    public void calculateLints(JavaFileObject sourceFile, JCTree tree, EndPosTable endPositions) {
        Assert.check(rootLint != null);
        fileInfoMap.get(sourceFile).afterAttr(tree, endPositions);
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
        fileInfoMap.put(tree.sourcefile, new FileInfo(tree));
    }

// FileInfo

    /**
     * Holds {@link Lint} information for a fully parsed source file.
     *
     * <p>
     * Initially (immediately after parsing), "rootNode" will have an (unmapped) {@link DeclNode} child corresponding
     * to each top-level declaration in the source file. As those top-level declarations are attributed, the corresponding
     * {@link DeclNode} child is replaced with a {@link MappedDeclNode}, created via {@link MappedDeclNodeBuilder}.
     */
    private class FileInfo {

        final MappedDeclNode rootNode = new MappedDeclNode(rootLint);   // tree of this file's "interesting" declaration nodes

        // After parsing: Create the root node and its immediate (unmapped) children
        FileInfo(JCCompilationUnit tree) {
            tree.defs.stream()
              .filter(this::isTopLevelDecl)
              .forEach(decl -> new DeclNode(rootNode, decl, tree.endPositions));
        }

        // After attribution: Replace top-level DeclNode child with a MappedDeclNode subtree
        void afterAttr(JCTree tree, EndPosTable endPositions) {
            Assert.check(rootNode != null, "source not parsed");
            DeclNode node = findTopNode(tree.pos(), true);
            Assert.check(node != null, "unknown declaration");
            Assert.check(!(node instanceof MappedDeclNode), "duplicate call");
            new MappedDeclNodeBuilder(rootNode, endPositions).scan(tree);
        }

        // Find the Lint configuration that applies to the given position, if known
        Optional<Lint> lintAt(DiagnosticPosition pos) {
            return switch (findTopNode(pos, false)) {       // find the top-level declaration containing "pos", if any
            case MappedDeclNode node                        // if the declaration has been attributed...
              -> Optional.of(node.find(pos).lint);          //  -> return the most specific applicable Lint configuration
            case DeclNode node                              // if the declaration has not been attributed...
              -> Optional.empty();                          //  -> we don't know yet
            case null                                       // if "pos" is outside of any declaration...
              -> Optional.of(rootLint);                     //  -> use the root lint
            };
        }

        // Find (and optionally remove) the top-level declaration containing "pos", if any
        DeclNode findTopNode(DiagnosticPosition pos, boolean remove) {
            for (Iterator<DeclNode> i = rootNode.children.iterator(); i.hasNext(); ) {
                DeclNode node = i.next();
                if (node.contains(pos)) {
                    if (remove)
                        i.remove();
                    return node;
                }
            }
            return null;
        }

        boolean isTopLevelDecl(JCTree tree) {
            return tree.getTag() == Tag.MODULEDEF
                || tree.getTag() == Tag.PACKAGEDEF
                || tree.getTag() == Tag.CLASSDEF;
        }
    }

// DeclNode

    /**
     * Represents the lexical range corresponding to a module, package, class, method, or variable declaration,
     * or a "root" representing an entire file.
     */
    private static class DeclNode {

        final int startPos;                                     // declaration's starting position
        final int endPos;                                       // declaration's ending position
        final MappedDeclNode parent;                            // the immediately containing declaration (null for root)

        DeclNode(int startPos, int endPos, MappedDeclNode parent) {
            this.startPos = startPos;
            this.endPos = endPos;
            this.parent = parent;
            if (parent != null)
                parent.children.add(this);
        }

        // Create a node representing the given declaration
        DeclNode(MappedDeclNode parent, JCTree tree, EndPosTable endPositions) {
            this(TreeInfo.getStartPos(tree), TreeInfo.getEndPos(tree, endPositions), parent);
        }

        boolean contains(DiagnosticPosition pos) {
            int offset = pos.getLintPosition();
            return offset == startPos || (offset > startPos && offset < endPos);
        }

        boolean contains(DeclNode that) {
            return this.startPos <= that.startPos && this.endPos >= that.endPos;
        }

        @Override
        public String toString() {
            return String.format("DeclNode[%d-%d]", startPos, endPos);
        }
    }

    /**
     * A {@link DeclNode} for which the corresponding {@link Lint} configuration is known.
     */
    private static class MappedDeclNode extends DeclNode {

        final Symbol symbol;                                    // declaration symbol (null for root; for debug purposes only)
        final Lint lint;                                        // the Lint configuration that applies at this declaration
        final List<DeclNode> children = new ArrayList<>();      // the nested declarations one level below this node

        // Create a node representing the entire file, using the root lint configuration
        MappedDeclNode(Lint rootLint) {
            super(Integer.MIN_VALUE, Integer.MAX_VALUE, null);
            this.symbol = null;
            this.lint = rootLint;
        }

        // Create a node representing the given declaration and its corresponding Lint configuration
        MappedDeclNode(Symbol symbol, MappedDeclNode parent, JCTree tree, EndPosTable endPositions, Lint lint) {
            super(parent, tree, endPositions);
            this.symbol = symbol;
            this.lint = lint;
        }

        // Find the narrowest node in this tree (including me) that contains the given position, if any
        MappedDeclNode find(DiagnosticPosition pos) {
            return children.stream()
              .map(MappedDeclNode.class::cast)      // this cast is ok because this method is never invoked on the root instance
              .map(child -> child.find(pos))
              .filter(Objects::nonNull)
              .reduce((a, b) -> a.contains(b) ? b : a)
              .orElseGet(() -> contains(pos) ? this : null);
        }

        @Override
        public String toString() {
            String label = symbol != null ? "sym=" + symbol : "ROOT";
            return String.format("MappedDeclNode[%d-%d,%s,lint=%s]", startPos, endPos, label, lint);
        }
    }

// MappedDeclNodeBuilder

    /**
     * Builds a tree of {@link MappedDeclNode}s starting from a top-level declaration.
     */
    private class MappedDeclNodeBuilder extends TreeScanner {

        private final EndPosTable endPositions;

        private MappedDeclNode parent;
        private Lint lint;

        MappedDeclNodeBuilder(MappedDeclNode rootNode, EndPosTable endPositions) {
            this.endPositions = endPositions;
            this.parent = rootNode;
            this.lint = rootNode.lint;              // i.e, rootLint
        }

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

        private <T extends JCTree> void scanDecl(T tree, Symbol symbol, Consumer<? super T> recursion) {

            // "symbol" can be null if there were earlier errors; skip this declaration if so
            if (symbol == null) {
                recursion.accept(tree);
                return;
            }

            // Update the current Lint in effect; note lint.augment() returns the same instance if there's no change
            Lint previousLint = lint;
            lint = lint.augment(symbol);

            // If this declaration is not "interesting", we don't need to create a DeclNode for it
            if (lint == previousLint && parent.parent != null) {
                recursion.accept(tree);
                return;
            }

            // Add a MappedDeclNode here
            MappedDeclNode node = new MappedDeclNode(symbol, parent, tree, endPositions, lint);
            parent = node;
            try {
                recursion.accept(tree);
            } finally {
                parent = node.parent;
                lint = previousLint;
            }
        }
    }
}
