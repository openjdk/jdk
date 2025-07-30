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
     * Initially (immediately after parsing), "unmappedDecls" will contain a {@link JCTree} corresponding
     * to each top-level declaration in the source file. As those top-level declarations are attributed,
     * the {@link JCTree} is removed and a new {@link MappedDecl} subtree is added to the "mappedDecls" tree.
     */
    private static class FileInfo {

        EndPosTable endPositions;                           // end position table for this source file (only during attribution)
        final MappedDecl mappedDecls;                       // root node with subtree for each mapped top-level declaration
        final List<JCTree> unmappedDecls;                   // unmapped (i.e., awaiting attribution) top-level declarations

        // After parsing: Add top-level declarations to our "unmappedDecls" list
        FileInfo(Lint rootLint, JCCompilationUnit tree) {
            this.endPositions = tree.endPositions;
            this.mappedDecls = new MappedDecl(rootLint);
            this.unmappedDecls = tree.defs.stream()
              .filter(this::isTopLevelDecl)
              .collect(Collectors.toCollection(ArrayList::new));
        }

        // After attribution: Discard the tree from "unmappedDecls" and add a corresponding MappedDecl to "mappedDecls"
        void afterAttr(JCTree tree) {
            MappedDeclBuilder builder = null;
            for (Iterator<JCTree> i = unmappedDecls.iterator(); i.hasNext(); ) {
                if (contains(i.next(), tree.pos())) {
                    builder = new MappedDeclBuilder(mappedDecls, endPositions);
                    i.remove();
                    break;
                }
            }
            Assert.check(builder != null, "top-level declaration not found");
            builder.scan(tree);
            if (unmappedDecls.isEmpty())
                endPositions = null;                        // gc friendly
        }

        // Find the (narrowest) Lint that applies to the given position, unless the position has not been mapped yet
        Optional<Lint> lintAt(DiagnosticPosition pos) {
            boolean mapped = unmappedDecls.stream().noneMatch(tree -> contains(tree, pos));
            return mapped ? Optional.of(mappedDecls.bestMatch(pos).lint) : Optional.empty();
        }

        boolean contains(JCTree tree, DiagnosticPosition pos) {
            return FileInfo.contains(TreeInfo.getStartPos(tree), TreeInfo.getEndPos(tree, endPositions), pos);
        }

        boolean isTopLevelDecl(JCTree tree) {
            return tree.getTag() == Tag.MODULEDEF
                || tree.getTag() == Tag.PACKAGEDEF
                || tree.getTag() == Tag.CLASSDEF;
        }

        static boolean contains(int startPos, int endPos, DiagnosticPosition pos) {
            int offset = pos.getLintPosition();
            return offset == startPos || (offset > startPos && offset < endPos);
        }
    }

// MappedDecl

    /**
     * A module, package, class, method, or variable declaration within which all {@link Lint} configurations are known.
     * There is also a root instance that represents the entire file.
     */
    private static class MappedDecl {

        final int startPos;                                     // declaration's lexical starting position
        final int endPos;                                       // declaration's lexical ending position
        final Lint lint;                                        // the Lint configuration that applies at this declaration
        final Symbol symbol;                                    // declaration symbol (for debug purposes only; null for root)
        final MappedDecl parent;                                // the parent node of this node
        final List<MappedDecl> children;                        // the nested declarations one level below this node

        // Create a node representing the entire file, using the root lint configuration
        MappedDecl(Lint rootLint) {
            this(Integer.MIN_VALUE, Integer.MAX_VALUE, rootLint, null, null);
        }

        // Create a node representing the given declaration and its corresponding Lint configuration
        MappedDecl(JCTree tree, EndPosTable endPositions, Lint lint, Symbol symbol, MappedDecl parent) {
            this(TreeInfo.getStartPos(tree), TreeInfo.getEndPos(tree, endPositions), lint, symbol, parent);
            parent.children.add(this);
        }

        MappedDecl(int startPos, int endPos, Lint lint, Symbol symbol, MappedDecl parent) {
            this.startPos = startPos;
            this.endPos = endPos;
            this.lint = lint;
            this.symbol = symbol;
            this.parent = parent;
            this.children = new ArrayList<>();
        }

        // Find the narrowest node in this tree (including me) that contains the given position, if any
        MappedDecl bestMatch(DiagnosticPosition pos) {
            return children.stream()
              .map(child -> child.bestMatch(pos))
              .filter(Objects::nonNull)
              .reduce((a, b) -> a.contains(b) ? b : a)
              .orElseGet(() -> contains(pos) ? this : null);
        }

        boolean contains(DiagnosticPosition pos) {
            return FileInfo.contains(startPos, endPos, pos);
        }

        boolean contains(MappedDecl that) {
            return this.startPos <= that.startPos && this.endPos >= that.endPos;
        }

        @Override
        public String toString() {
            String label = symbol != null ? "sym=" + symbol : "ROOT";
            return String.format("MappedDecl[%d-%d,%s,lint=%s]", startPos, endPos, label, lint);
        }
    }

// MappedDeclBuilder

    /**
     * Builds a tree of {@link MappedDecl}s starting from a top-level declaration.
     * The tree is sparse: only declarations that differ from their parent are included.
     */
    private static class MappedDeclBuilder extends TreeScanner {

        private final EndPosTable endPositions;

        private MappedDecl parent;
        private Lint lint;

        MappedDeclBuilder(MappedDecl rootNode, EndPosTable endPositions) {
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

        private <T extends JCTree> void scanDecl(T tree, Symbol symbol, Consumer<? super T> recursor) {

            // The "symbol" can be null if there were earlier errors; skip this declaration if so
            if (symbol == null) {
                recursor.accept(tree);
                return;
            }

            // Update the current Lint in effect
            Lint previousLint = lint;
            lint = lint.augment(symbol);            // note: lint.augment() returns the same instance if there's no change

            // Add a MappedDecl node here, but only if this declaration's Lint configuration is different from its parent
            if (lint != previousLint) {
                MappedDecl node = new MappedDecl(tree, endPositions, lint, symbol, parent);
                parent = node;
                try {
                    recursor.accept(tree);
                } finally {
                    parent = node.parent;
                    lint = previousLint;
                }
            } else {
                recursor.accept(tree);
            }
        }
    }
}
