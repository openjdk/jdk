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
     * Initially (immediately after parsing), "unmappedDecls" will contain a {@link Decl} corresponding
     * to each top-level declaration in the source file. As those top-level declarations are attributed,
     * the {@link Decl} is removed and a {@link MappedDecl} is added to "mappedDecls".
     */
    private class FileInfo {

        final List<Decl> unmappedDecls = new ArrayList<>();         // unmapped (i.e., awaiting attribution) top-level declarations
        final MappedDecl mappedDecls = new MappedDecl(rootLint);    // root node with subtree for each mapped top-level declaration

        // After parsing: Create a Decl corresponding to each top-level declaration and add to "unmappedDecls"
        FileInfo(JCCompilationUnit tree) {
            tree.defs.stream()
              .filter(this::isTopLevelDecl)
              .map(decl -> new Decl(decl, tree.endPositions))
              .forEach(unmappedDecls::add);
        }

        // After attribution: Discard the Decl from "unmappedDecls" and add a corresponding MappedDecl to "mappedDecls"
        void afterAttr(JCTree tree, EndPosTable endPositions) {
            for (Iterator<Decl> i = unmappedDecls.iterator(); i.hasNext(); ) {
                if (i.next().contains(tree.pos())) {
                    new MappedDeclBuilder(mappedDecls, endPositions).scan(tree);
                    i.remove();
                    return;
                }
            }
            throw new AssertionError("top-level declaration not found");
        }

        // Find the Lint configuration that applies to the given position, if known
        Optional<Lint> lintAt(DiagnosticPosition pos) {
            if (unmappedDecls.stream().anyMatch(decl -> decl.contains(pos)))    // the top level declaration is not mapped yet
                return Optional.empty();
            return Optional.of(mappedDecls.bestMatch(pos).lint);                // return the narrowest matching declaration
        }

        boolean isTopLevelDecl(JCTree tree) {
            return tree.getTag() == Tag.MODULEDEF
                || tree.getTag() == Tag.PACKAGEDEF
                || tree.getTag() == Tag.CLASSDEF;
        }
    }

// Decl

    /**
     * Represents a lexical range corresponding to a module, package, class, method, or variable declaration.
     */
    private static class Decl {

        final int startPos;
        final int endPos;

        Decl(int startPos, int endPos) {
            this.startPos = startPos;
            this.endPos = endPos;
        }

        Decl(JCTree tree, EndPosTable endPositions) {
            this(TreeInfo.getStartPos(tree), TreeInfo.getEndPos(tree, endPositions));
        }

        boolean contains(DiagnosticPosition pos) {
            int offset = pos.getLintPosition();
            return offset == startPos || (offset > startPos && offset < endPos);
        }

        boolean contains(Decl that) {
            return this.startPos <= that.startPos && this.endPos >= that.endPos;
        }

        @Override
        public String toString() {
            return String.format("Decl[%d-%d]", startPos, endPos);
        }
    }

// MappedDecl

    /**
     * A declaration for which the corresponding {@link Lint} configuration is known.
     */
    private static class MappedDecl extends Decl {

        final Lint lint;                                        // the Lint configuration that applies at this declaration
        final Symbol symbol;                                    // declaration symbol (for debug purposes only; null for root)
        final MappedDecl parent;                                // the parent node of this node
        final List<MappedDecl> children = new ArrayList<>();    // the nested declarations one level below this node

        // Create a node representing the entire file, using the root lint configuration
        MappedDecl(Lint rootLint) {
            super(Integer.MIN_VALUE, Integer.MAX_VALUE);
            this.lint = rootLint;
            this.symbol = null;
            this.parent = null;
        }

        // Create a node representing the given declaration and its corresponding Lint configuration
        MappedDecl(Symbol symbol, MappedDecl parent, JCTree tree, EndPosTable endPositions, Lint lint) {
            super(TreeInfo.getStartPos(tree), TreeInfo.getEndPos(tree, endPositions));
            this.lint = lint;
            this.symbol = symbol;
            this.parent = parent;
            parent.children.add(this);
        }

        // Find the narrowest node in this tree (including me) that contains the given position, if any
        MappedDecl bestMatch(DiagnosticPosition pos) {
            return children.stream()
              .map(child -> child.bestMatch(pos))
              .filter(Objects::nonNull)
              .reduce((a, b) -> a.contains(b) ? b : a)
              .orElseGet(() -> contains(pos) ? this : null);
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
     * The tree is sparse: only "interesting" declarations are included.
     */
    private class MappedDeclBuilder extends TreeScanner {

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

        private <T extends JCTree> void scanDecl(T tree, Symbol symbol, Consumer<? super T> recursion) {

            // "symbol" can be null if there were earlier errors; skip this declaration if so
            if (symbol == null) {
                recursion.accept(tree);
                return;
            }

            // Update the current Lint in effect; note lint.augment() returns the same instance if there's no change
            Lint previousLint = lint;
            lint = lint.augment(symbol);

            // If this declaration is not "interesting", we don't need to create a MappedDecl for it
            if (lint == previousLint && parent.parent != null) {
                recursion.accept(tree);
                return;
            }

            // Add a MappedDecl here
            MappedDecl node = new MappedDecl(symbol, parent, tree, endPositions, lint);
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
