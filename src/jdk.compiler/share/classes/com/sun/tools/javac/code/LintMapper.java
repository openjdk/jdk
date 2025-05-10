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
 * Because {@code @SuppressWarnings} is a Java symbol, in general this mapping can't be be
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

    // Per-source file lint information
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
     * @return the applicable {@link Lint}, if known
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
     * Invoked when file parsing starts to create an entry for the new file.
     */
    public void startParsingFile(JavaFileObject sourceFile) {
        initializeIfNeeded();
        fileInfoMap.put(sourceFile, new FileInfo());
    }

    /**
     * Invoked when file parsing completes to identify the top-level declarations.
     */
    public void finishParsingFile(JCCompilationUnit tree) {
        Assert.check(rootLint != null);
        fileInfoMap.get(tree.sourcefile).afterParse(tree);
    }

// FileInfo

    /**
     * Holds {@link Lint} information for one source file.
     *
     * <p>
     * Instances evolve through three states:
     * <ul>
     *  <li>Before the file has been completely parsed, {@link #topSpans} is null.
     *  <li>Immediately after the file has been parsed, {@link #topSpans} contains zero or more {@link Span}s
     *      corresponding to the top-level declarations in the file, and {@code rootNode} has no children.
     *  <li>When a top-level declaration is attributed, a corresponding {@link DeclNode} child matching one
     *      of the {@link Span}s in {@link #topSpans} is created and added to {@link #rootNode}.
     * </ul>
     */
    private class FileInfo {

        List<Span> topSpans;                                // the spans of all top level declarations
        final DeclNode rootNode = new DeclNode(rootLint);   // tree of file's "interesting" declaration nodes

        // Find the Lint that applies to the given position, if known
        Optional<Lint> lintAt(DiagnosticPosition pos) {
            if (topSpans == null)                           // has the file been parsed yet?
                return Optional.empty();                    // -> no, we don't know yet
            if (!findTopSpan(pos).isPresent())              // is the position within some top-level declaration?
                return Optional.of(rootLint);               // -> no, use the root lint
            DeclNode topNode = findTopNode(pos);
            if (topNode == null)                            // has that declaration been attributed yet?
                return Optional.empty();                    // -> no, we don't know yet
            DeclNode node = topNode.find(pos);              // find the best matching node (it must exist)
            return Optional.of(node.lint);                  // use its Lint
        }

        void afterParse(JCCompilationUnit tree) {
            Assert.check(topSpans == null, "source already parsed");
            topSpans = tree.defs.stream()
              .filter(this::isTopLevelDecl)
              .map(decl -> new Span(decl, tree.endPositions))
              .collect(Collectors.toList());
        }

        void afterAttr(JCTree tree, EndPosTable endPositions) {
            Assert.check(topSpans != null, "source not parsed");
            Assert.check(findTopNode(tree.pos()) == null, "duplicate call");
            new DeclNodeTreeBuilder(rootNode, endPositions).scan(tree);
        }

        Optional<Span> findTopSpan(DiagnosticPosition pos) {
            return topSpans.stream()
              .filter(span -> span.contains(pos))
              .findFirst();
        }

        DeclNode findTopNode(DiagnosticPosition pos) {
            return rootNode.children.stream()
              .filter(node -> node.contains(pos))
              .findFirst()
              .orElse(null);
        }

        boolean isTopLevelDecl(JCTree tree) {
            return tree.getTag() == Tag.MODULEDEF
                || tree.getTag() == Tag.PACKAGEDEF
                || tree.getTag() == Tag.CLASSDEF;
        }
    }

// Span

    /**
     * Represents a lexical range in a file.
     */
    private static class Span {

        final int startPos;
        final int endPos;

        Span(int startPos, int endPos) {
            this.startPos = startPos;
            this.endPos = endPos;
        }

        Span(JCTree tree, EndPosTable endPositions) {
            this(TreeInfo.getStartPos(tree), TreeInfo.endPos(endPositions, tree));
        }

        boolean contains(int pos) {
            return pos == startPos || (pos > startPos && pos < endPos);
        }

        boolean contains(DiagnosticPosition pos) {
            return contains(pos.getLintPosition());
        }

        boolean contains(Span that) {
            return this.startPos <= that.startPos && this.endPos >= that.endPos;
        }

        @Override
        public String toString() {
            return String.format("Span[%d-%d]", startPos, endPos);
        }
    }

// DeclNode

    /**
     * Represents a declaration and the {@link Lint} configuration that applies within its lexical range.
     *
     * <p>
     * For each file, there is a root node represents the entire source file. At the next level down are
     * nodes representing the top-level declarations in the file, and so on.
     */
    private static class DeclNode extends Span {

        final Symbol symbol;                                    // the symbol declared by this declaration (null for root)
        final DeclNode parent;                                  // the immediately containing declaration (null for root)
        final List<DeclNode> children = new ArrayList<>();      // the immediately next level down declarations under this node
        final Lint lint;                                        // the Lint configuration that applies at this declaration

        // Create a root node representing the entire file
        DeclNode(Lint rootLint) {
            super(Integer.MIN_VALUE, Integer.MAX_VALUE);
            this.symbol = null;
            this.parent = null;
            this.lint = rootLint;
        }

        // Create a normal declaration node
        DeclNode(Symbol symbol, DeclNode parent, JCTree tree, EndPosTable endPositions, Lint lint) {
            super(tree, endPositions);
            this.symbol = symbol;
            this.parent = parent;
            this.lint = lint;
            parent.children.add(this);
        }

        // Find the narrowest node in this tree that contains the given position, if any
        DeclNode find(DiagnosticPosition pos) {
            return children.stream()
              .map(child -> child.find(pos))
              .filter(Objects::nonNull)
              .reduce((a, b) -> a.contains(b) ? b : a)
              .orElseGet(() -> contains(pos) ? this : null);
        }

        // Stream this node and all descendents via pre-order recursive descent
        Stream<DeclNode> stream() {
            return Stream.concat(Stream.of(this), children.stream().flatMap(DeclNode::stream));
        }

        @Override
        public String toString() {
            String label = symbol != null ? "sym=" + symbol : "ROOT";
            return String.format("DeclNode[%s,lint=%s]", label, lint);
        }
    }

// DeclNodeTreeBuilder

    /**
     * Builds a tree of {@link DeclNode}s starting from a top-level declaration.
     */
    private class DeclNodeTreeBuilder extends TreeScanner {

        private final EndPosTable endPositions;

        private DeclNode parent;
        private Lint lint;

        DeclNodeTreeBuilder(DeclNode rootNode, EndPosTable endPositions) {
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

            // Add a DeclNode here
            DeclNode node = new DeclNode(symbol, parent, tree, endPositions, lint);
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
