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

package com.sun.tools.javac.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;

import com.sun.tools.javac.code.Lint;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.EndPosTable;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;

/**
 * Maps source code positions to the applicable {@link Lint} instance based on {@link -Xlint}
 * command line flags and {@code @SuppressWarnings} annotations on containing declarations.
 *
 * <p>
 * This mapping can't be cannot be calculated until after attribution. As each top-level
 * declaration (class, package, or module) is attributed, this singleton is notified by
 * Attr and the {@link Lint}s contained in that declaration are calculated.
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

    // This calculates the Lint instances that apply to various source code ranges
    private final LintSpanCalculator lintSpanCalculator = new LintSpanCalculator();

    // The root Lint instance, calculated on-demand to avoid init loops
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

    private Lint rootLint() {
        if (rootLint == null)
            rootLint = Lint.instance(context);
        return rootLint;
    }

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

        // If the file is completely unknown, we don't know
        FileInfo fileInfo = fileInfoMap.get(sourceFile);
        if (fileInfo == null)
            return Optional.empty();

        // If the file hasn't been fully parsed yet, we don't know what Lint applies yet
        if (!fileInfo.parsed)
            return Optional.empty();

        // Find the top-level declaration that contains pos; if there is none, then the root lint applies
        Span declSpan = fileInfo.findDeclSpan(pos);
        if (declSpan == null)
            return Optional.of(rootLint());

        // Have we attributed this top-level declaration? If not, we don't know what Lint applies yet
        List<LintSpan> lintSpans = fileInfo.lintSpanMap.get(declSpan);
        if (lintSpans == null)
            return Optional.empty();

        // Find the narrowest containing LintSpan; if there is none, then the root lint applies
        return FileInfo.bestMatch(lintSpans, pos)
          .map(lintSpan -> lintSpan.lint)
          .or(() -> Optional.of(rootLint()));
    }

    /**
     * Calculate {@lint Lint} configurations for all positions within the given top-level declaration.
     *
     * @param sourceFile source file
     * @param tree top-level declaration (class, package, or module)
     */
    public void calculateLints(JavaFileObject sourceFile, JCTree tree, EndPosTable endPositions) {

        // Get the info for this file
        FileInfo fileInfo = fileInfoMap.get(sourceFile);

        // Sanity checks
        Assert.check(isTopLevelDecl(tree));
        Assert.check(fileInfo != null && fileInfo.parsed);
        Span declSpan = new Span(tree, endPositions);
        Assert.check(fileInfo.lintSpanMap.containsKey(declSpan), "unknown declaration");
        Assert.check(fileInfo.lintSpanMap.get(declSpan) == null, "duplicate calculateLints()");

        // Build the list of lints for declarations within the top-level declaration
        fileInfo.lintSpanMap.put(declSpan, lintSpanCalculator.calculate(endPositions, tree));
    }

    /**
     * Reset this instance (except for listeners).
     */
    public void clear() {
        fileInfoMap.clear();
    }

    /**
     * Invoked when file parsing starts to create an entry for the new file.
     */
    public void startParsingFile(JavaFileObject sourceFile) {
        fileInfoMap.put(sourceFile, new FileInfo());
    }

    /**
     * Invoked when file parsing completes to identify the top-level declarations.
     */
    public void finishParsingFile(JCCompilationUnit tree) {

        // Get info for this file
        FileInfo fileInfo = fileInfoMap.get(tree.sourcefile);
        Assert.check(fileInfo != null, () -> "unknown source " + tree.sourcefile);
        Assert.check(!fileInfo.parsed, () -> "source already parsed: " + tree.sourcefile);
        Assert.check(fileInfo.lintSpanMap.isEmpty(), () -> "duplicate invocation for " + tree.sourcefile);

        // Mark file as parsed
        fileInfo.parsed = true;

        // Create an entry in lintSpanMap for each top-level declaration, with a null value for now
        tree.defs.stream()
          .filter(this::isTopLevelDecl)
          .map(decl -> new Span(decl, tree.endPositions))
          .forEach(span -> fileInfo.lintSpanMap.put(span, null));
    }

    private boolean isTopLevelDecl(JCTree tree) {
        return tree.getTag() == Tag.MODULEDEF
            || tree.getTag() == Tag.PACKAGEDEF
            || tree.getTag() == Tag.CLASSDEF;
    }

// FileInfo

    /**
     * Holds the calculated {@link Lint}s for top-level declarations in some source file.
     *
     * <p>
     * Instances evolve through these states:
     * <ul>
     *  <li>Before the file has been completely parsed, {@code #parsed} is false and {@link #lintSpanMap} is empty.
     *  <li>Immediately after the file has been parsed, {@code #parsed} is true and {@link #lintSpanMap} contains
     *      zero or more entries corresponding to the top-level declarations in the file, but whose values are null.
     *  <li>As each top-level declaration is attributed, the entries in {@link #lintSpanMap} are updated to non-null.
     * </ul>
     */
    private static class FileInfo {

        final Map<Span, List<LintSpan>> lintSpanMap = new HashMap<>();
        boolean parsed;

        // Find the top-level declaration containing the given position
        Span findDeclSpan(DiagnosticPosition pos) {
            return lintSpanMap.keySet().stream()
              .filter(span -> span.contains(pos))
              .findFirst()
              .orElse(null);
        }

        // Find the narrowest span in the given list that contains the given position
        static Optional<LintSpan> bestMatch(List<LintSpan> lintSpans, DiagnosticPosition pos) {
            int position = pos.getLintPosition();
            Assert.check(position != Position.NOPOS);
            LintSpan bestSpan = null;
            for (LintSpan lintSpan : lintSpans) {
                if (lintSpan.contains(position) && (bestSpan == null || bestSpan.contains(lintSpan))) {
                    bestSpan = lintSpan;
                }
            }
            return Optional.ofNullable(bestSpan);
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
        public int hashCode() {
            return Integer.hashCode(startPos) ^ Integer.hashCode(endPos);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this)
                return true;
            if (obj == null || obj.getClass() != this.getClass())
                return false;
            final Span that = (Span)obj;
            return this.startPos == that.startPos && this.endPos == that.endPos;
        }

        @Override
        public String toString() {
            return String.format("Span[%d-%d]", startPos, endPos);
        }
    }

// LintSpan

    /**
     * Represents a lexical range and the {@link Lint} configuration that applies therein.
     */
    private static class LintSpan extends Span {

        final Lint lint;

        LintSpan(int startPos, int endPos, Lint lint) {
            super(startPos, endPos);
            this.lint = lint;
        }

        LintSpan(JCTree tree, EndPosTable endPositions, Lint lint) {
            super(tree, endPositions);
            this.lint = lint;
        }

        // Note: no need for equals() or hashCode() here

        @Override
        public String toString() {
            return String.format("LintSpan[%d-%d, lint=%s]", startPos, endPos, lint);
        }
    }

// LintSpanCalculator

    private class LintSpanCalculator extends TreeScanner {

        private EndPosTable endPositions;
        private Lint currentLint;
        private List<LintSpan> lintSpans;

        List<LintSpan> calculate(EndPosTable endPositions, JCTree tree) {
            this.endPositions = endPositions;
            currentLint = rootLint();
            lintSpans = new ArrayList<>();
            try {
                scan(tree);
                return lintSpans;
            } finally {
                lintSpans = null;
            }
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
            Lint previousLint = currentLint;
            currentLint = Optional.ofNullable(symbol)   // symbol can be null if there were earlier errors
              .map(currentLint::augment)
              .orElse(currentLint);
            recursion.accept(tree);
            if (currentLint != previousLint) {          // Lint.augment() returns the same object if no change
                lintSpans.add(new LintSpan(tree, endPositions, currentLint));
                currentLint = previousLint;
            }
        }
    }
}
