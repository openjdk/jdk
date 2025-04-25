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
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;

import com.sun.tools.javac.code.Lint.LintCategory;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.main.Option;
import com.sun.tools.javac.resources.CompilerProperties.LintWarnings;
import com.sun.tools.javac.tree.EndPosTable;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.util.Options;

import static com.sun.tools.javac.code.Lint.LintCategory.DEPRECATION;
import static com.sun.tools.javac.code.Lint.LintCategory.OPTIONS;
import static com.sun.tools.javac.code.Lint.LintCategory.SUPPRESSION;
import static com.sun.tools.javac.code.Lint.LintCategory.SUPPRESSION_OPTION;

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

    // Validations of "-Xlint:-foo" suppressions
    private final EnumSet<LintCategory> optionFlagValidations = LintCategory.newEmptySet();

    // Compiler context
    private final Context context;

    // These are initialized lazily; see initializeIfNeeded()
    private Log log;
    private Lint rootLint;
    private Symtab syms;
    private Names names;
    private Options options;

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
        if (rootLint == null) {
            log = Log.instance(context);
            rootLint = Lint.instance(context);
            syms = Symtab.instance(context);
            names = Names.instance(context);
            options = Options.instance(context);
        }
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
        optionFlagValidations.clear();
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

// Suppression Tracking

    /**
     * Validate the given lint category within the scope of the given symbol's declaration (or globally if symbol is null).
     *
     * <p>
     * This is to indicate that, if the category is being suppressed, a warning would have otherwise been generated.
     *
     * @param symbol innermost {@code @SuppressWarnings}-annotated symbol in scope, or null for global scope
     * @param category lint category to validate
     */
    public void validateSuppression(Symbol symbol, LintCategory category) {
        EnumSet<LintCategory> validations = symbol != null ?
          fileInfoMap.get(log.currentSourceFile()).validationsFor(symbol) : optionFlagValidations;
        validations.add(category);
    }

    /**
     * Warn about unnecessary {@code @SuppressWarnings} suppressions within the given tree.
     *
     * <p>
     * This step must be done after the given source file has been warned about.
     *
     * @param sourceFile source file
     * @param tree top level declaration
     */
    public void reportUnnecessarySuppressionAnnotations(JavaFileObject sourceFile, JCTree tree) {
        initializeIfNeeded();
        FileInfo fileInfo = fileInfoMap.get(sourceFile);
        DeclNode topNode = fileInfo.findTopNode(tree.pos());

        // Propagate validations in this top-level declaration to determine which suppressions never got validated
        propagateValidations(fileInfo, topNode);

        // Report them if needed
        if (rootLint.isEnabled(SUPPRESSION, false)) {
            topNode.stream()
              .filter(node -> node.lint.isEnabled(SUPPRESSION, false))
              .forEach(node -> report(node.unvalidated, name -> "\"" + name + "\"",
                names -> log.warning(node.annotation.pos(), LintWarnings.UnnecessaryWarningSuppression(names))));
        }
    }

    /**
     * Warn about unnecessary {@code -Xlint:-key} flags.
     *
     * <p>
     * This step must be done after all source files have been warned about; it invokes {@link #clear} as a side effect.
     */
    public void reportUnnecessarySuppressionOptions() {
        initializeIfNeeded();
        if (rootLint.isEnabled(SUPPRESSION_OPTION, false) &&
            rootLint.isEnabled(OPTIONS, false) &&
            !options.isSet(Option.XLINT) &&                     // if "-Xlint:all" appears, all "-foo" suppressions are valid
            !options.isSet(Option.XLINT_CUSTOM, "all")) {

            // If a file has errors, we may never get a call to reportUnnecessaryAnnotations(), which means validations
            // in that file may never propagate to the global level, which means possible bogus "suppression-option" warnings.
            // To avoid that, promote any leftover validations to the global level here.
            fileInfoMap.values().stream()
              .forEach(fileInfo -> fileInfo.rootNode.children.stream()
                .forEach(topNode -> propagateValidations(fileInfo, topNode)));

            // Calculate the "Xlint:-foo" suppressions that did not get validated
            EnumSet<LintCategory> unvalidated = rootLint.getOptionFlagSuppressions();
            unvalidated.removeAll(optionFlagValidations);

            // Report them
            report(unvalidated, name -> "-" + name, names -> log.warning(LintWarnings.UnnecessaryLintWarningSuppression(names)));
        }

        // We are completely done
        clear();
    }

    private void report(EnumSet<LintCategory> unvalidated, Function<String, String> formatter, Consumer<String> logger) {
        String names = unvalidated.stream()
          .filter(lc -> lc.suppressionTracking)
          .map(category -> category.option)
          .map(formatter)
          .collect(Collectors.joining(", "));
        if (!names.isEmpty())
            logger.accept(names);
    }

    // Propagate validations in the given top-level declaration; any that escape validate the corresponding "Xlint" suppression
    private void propagateValidations(FileInfo fileInfo, DeclNode topNode) {
        optionFlagValidations.addAll(fileInfo.propagateValidations(topNode));
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

        List<Span> topSpans;                                        // the spans of all top level declarations
        final DeclNode rootNode = new DeclNode(rootLint);           // tree of file's "interesting" declaration nodes
        final Map<Symbol, EnumSet<LintCategory>> validationsMap     // maps declaration symbol to validations therein
          = new HashMap<>();

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

        // Obtain the validation state for the given symbol
        EnumSet<LintCategory> validationsFor(Symbol symbol) {
            return validationsMap.computeIfAbsent(symbol, s -> LintCategory.newEmptySet());
        }

        // Combine the validation sets for two variable symbols that are declared together
        void mergeValidations(VarSymbol symbol1, VarSymbol symbol2) {
            EnumSet<LintCategory> validations1 = validationsFor(symbol1);
            EnumSet<LintCategory> validations2 = validationsFor(symbol2);
            Assert.check(validations1.equals(validations2));
            validationsMap.put(symbol2, validations1);          // now the two symbols share the same validation set
        }

        // Propagate validations in the given top-level node
        EnumSet<LintCategory> propagateValidations(DeclNode topNode) {
            Assert.check(rootNode.children.contains(topNode));
            return topNode.propagateValidations(validationsMap);
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
            new DeclNodeTreeBuilder(this, rootNode, endPositions).scan(tree);
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
        final JCAnnotation annotation;                          // the @SuppressWarnings on this declaration, if any
        final EnumSet<LintCategory> suppressions;               // categories suppressed by @SuppressWarnings, if any
        final EnumSet<LintCategory> unvalidated;                // categories in "suppressions" that were never validated

        // Create a root node representing the entire file
        DeclNode(Lint rootLint) {
            super(Integer.MIN_VALUE, Integer.MAX_VALUE);
            this.symbol = null;
            this.parent = null;
            this.lint = rootLint;
            this.annotation = null;
            this.suppressions = LintCategory.newEmptySet();     // you can't put @SuppressWarnings on a file
            this.unvalidated = EnumSet.copyOf(suppressions);
        }

        // Create a normal declaration node
        DeclNode(Symbol symbol, DeclNode parent, JCTree tree, EndPosTable endPositions,
          Lint lint, JCAnnotation annotation, EnumSet<LintCategory> suppressions) {
            super(tree, endPositions);
            this.symbol = symbol;
            this.parent = parent;
            this.lint = lint;
            this.annotation = annotation;
            this.suppressions = suppressions;
            this.unvalidated = EnumSet.copyOf(suppressions);
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

        // Calculate the unvalidated suppressions in the subtree rooted at this node. We do this by recursively
        // propagating validations upward until they are "caught" by some matching suppression; this validates
        // the suppression. Validations that are not caught are returned to the caller.
        public EnumSet<LintCategory> propagateValidations(Map<Symbol, EnumSet<LintCategory>> validationsMap) {

            // Recurse on subtrees first and gather their uncaught validations
            EnumSet<LintCategory> validations = LintCategory.newEmptySet();
            children.stream()
              .map(child -> child.propagateValidations(validationsMap))
              .forEach(validations::addAll);

            // Add in the validations that occurred at this node, if any
            Optional.of(symbol)
              .map(validationsMap::get)
              .ifPresent(validations::addAll);

            // Apply (and then discard) validations that match any of this node's suppressions
            validations.removeIf(category -> {
                if (suppressions.contains(category)) {
                    unvalidated.remove(category);
                    return true;
                }
                return false;
            });

            // Propagate the remaining validations that weren't caught upward
            return validations;
        }

        @Override
        public String toString() {
            String label = symbol != null ? "sym=" + symbol : "ROOT";
            return String.format("DeclNode[%s,lint=%s,suppressions=%s]", label, lint, suppressions);
        }
    }

// DeclNodeTreeBuilder

    /**
     * Builds a tree of {@link DeclNode}s starting from a top-level declaration.
     */
    private class DeclNodeTreeBuilder extends TreeScanner {

        // Variables declared together (separated by commas) share their @SuppressWarnings annotation, so they must also share
        // the set of validated suppressions: the suppression of a category is valid if *any* of the variables validates it.
        // We detect that situation using this map and, when found, invoke FileInfo.mergeValidations().
        private final Map<JCAnnotation, VarSymbol> annotationRepresentativeSymbolMap = new HashMap<>();

        private final FileInfo fileInfo;
        private final EndPosTable endPositions;

        private DeclNode parent;
        private Lint lint;

        DeclNodeTreeBuilder(FileInfo fileInfo, DeclNode rootNode, EndPosTable endPositions) {
            this.fileInfo = fileInfo;
            this.endPositions = endPositions;
            this.parent = rootNode;
            this.lint = rootNode.lint;              // i.e, rootLint
        }

        @Override
        public void visitModuleDef(JCModuleDecl tree) {
            scanDecl(tree, tree.sym, findAnnotation(tree.mods), super::visitModuleDef);
        }

        @Override
        public void visitPackageDef(JCPackageDecl tree) {
            scanDecl(tree, tree.packge, findAnnotation(tree.annotations), super::visitPackageDef);
        }

        @Override
        public void visitClassDef(JCClassDecl tree) {
            scanDecl(tree, tree.sym, findAnnotation(tree.mods), super::visitClassDef);
        }

        @Override
        public void visitMethodDef(JCMethodDecl tree) {
            scanDecl(tree, tree.sym, findAnnotation(tree.mods), super::visitMethodDef);
        }

        @Override
        public void visitVarDef(JCVariableDecl tree) {
            scanDecl(tree, tree.sym, findAnnotation(tree.mods), super::visitVarDef);
        }

        private <T extends JCTree> void scanDecl(T tree, Symbol symbol, JCAnnotation annotation, Consumer<? super T> recursion) {

            // "symbol" can be null if there were earlier errors; skip this declaration if so
            if (symbol == null) {
                recursion.accept(tree);
                return;
            }

            // Update the current Lint in effect; note lint.augment() returns the same instance if there's no change
            Lint previousLint = lint;
            lint = lint.augment(symbol);

            // Get the lint categories explicitly suppressed at this symbol's declaration by @SuppressedWarnings
            EnumSet<LintCategory> suppressed = Optional.ofNullable(annotation)
              .map(anno -> rootLint.suppressionsFrom(anno, true))
              .orElseGet(LintCategory::newEmptySet);

            // Merge validation sets for variables that share the same declaration (and therefore the same @SuppressedWarnings)
            if (annotation != null && symbol instanceof VarSymbol varSym) {
                annotationRepresentativeSymbolMap.merge(annotation, varSym, (oldSymbol, newSymbol) -> {
                    fileInfo.mergeValidations(oldSymbol, newSymbol);
                    return oldSymbol;
                });
            }

            // If this declaration is not "interesting", we don't need to create a DeclNode for it
            if (lint == previousLint && parent.parent != null && suppressed.isEmpty()) {
                recursion.accept(tree);
                return;
            }

            // Add a DeclNode here
            DeclNode node = new DeclNode(symbol, parent, tree, endPositions, lint, annotation, suppressed);
            parent = node;
            try {
                recursion.accept(tree);
            } finally {
                parent = node.parent;
                lint = previousLint;
            }
        }

        // Retrieve the @SuppressWarnings annotation, if any, from the given modifiers
        private JCAnnotation findAnnotation(JCModifiers mods) {
            return Optional.ofNullable(mods)
              .map(m -> m.annotations)
              .map(this::findAnnotation)
              .orElse(null);
        }

        // Retrieve the @SuppressWarnings annotation, if any, from the given list of annotations
        private JCAnnotation findAnnotation(Collection<JCAnnotation> annotations) {
            return Optional.ofNullable(annotations)
              .stream()
              .flatMap(Collection::stream)
              .filter(a -> a.attribute.type.tsym == syms.suppressWarningsType.tsym)
              .findFirst()
              .orElse(null);
        }
    }
}
