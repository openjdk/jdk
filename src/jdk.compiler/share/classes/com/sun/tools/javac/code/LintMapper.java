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
import java.util.Iterator;
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
 * <p>
 * This class also tracks which {@code @SuppressWarnings} suppressions actually suppress something.
 * Those that don't are unnecessary and trigger warnings in the {@code "suppression"} lint category.
 * For this to work, this class must be notified any time a warning that is currently suppressed would
 * have been reported; this is termed the "validation" of the suppression. That notification happens
 * via {@link #validateSuppression}.
 *
 * <p>
 * Validation events "bubble up" the source tree until they are "caught" by a {@code @SuppressWarnings}
 * annotation or they escape the file entirely. Being "caught" validates that suppression.
 * A suppression that is never validated is unnecessary.
 *
 * <p>
 * Additional observations and corner cases:
 * <ul>
 *  <li>Lint warnings can be suppressed at a module, package, class, method, or variable declaration
 *      (via {@code @SuppressWarnings}), or globally (via {@code -Xlint:-key}).
 *  <li>Consequently, an unnecessary suppression warning can only be emitted at one of those declarations.
 *  <li>Some categories (e.g., {@code classfile}) don't support suppression via {@code @SuppressWarnings}.
 *      These can only generate warnings at the global level (and therefore any {@code @SuppressWarnings}
 *      annotation is always unnecessary).
 *  <li>Some categories are never tracked for suppression, e.g., {@code options}, {@code path}, and the
 *      suppression category {@code "suppression"} itself.
 *  <li>{@code @SuppressWarnings("suppression")} is perfectly valid: it means unnecessary suppression
 *      warnings will never be reported for any lint category suppressed by that annotation or by any
 *      {@code @SuppressWarnings} annotation nested within the scope of its declaration.
 * </ul>
 *
 * <p><b>This is NOT part of any supported API. If you write code that depends on this, you do so at your
 * own risk. This code and its internal interfaces are subject to change or deletion without notice.</b>
 */
public class LintMapper {

    // The key for the context singleton
    private static final Context.Key<LintMapper> CONTEXT_KEY = new Context.Key<>();

    // Per-source file information. Note: during the parsing of a file, an entry exists but the FileInfo value is null
    private final Map<JavaFileObject, FileInfo> fileInfoMap = new HashMap<>();

    // Compiler context
    private final Context context;

    // These are initialized lazily; see initializeIfNeeded()
    private Log log;
    private Lint rootLint;
    private Symtab syms;

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
        fileInfoMap.get(sourceFile).afterAttr(syms, tree, endPositions);
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
        if (symbol != null) {
            fileInfoMap.get(log.currentSourceFile()).validationsFor(symbol).add(category);
        }
    }

    /**
     * Warn about unnecessary {@code @SuppressWarnings} suppressions within the given top-level declaration.
     *
     * <p>
     * This step must be done after the given source file has been warned about.
     *
     * @param sourceFile source file
     * @param tree top level declaration
     */
    public void reportUnnecessarySuppressionAnnotations(JavaFileObject sourceFile, JCTree tree) {

        // Anything to do here?
        initializeIfNeeded();
        if (!rootLint.isEnabled(SUPPRESSION, false)) {
            return;
        }

        // Find the LintRange corresponding to "tree"
        FileInfo fileInfo = fileInfoMap.get(sourceFile);
        LintRange lintRange = fileInfo.rootRange.findChild(tree.pos());

        // Propagate validations within the top-level declaration to determine which suppressions never got validated
        fileInfo.propagateValidations(lintRange);

        // Report unvalidated suppresions
        lintRange.stream()
          .filter(node -> node.lint.isEnabled(SUPPRESSION, false))
          .forEach(node -> {
            String unvalidatedNames = node.unvalidated.stream()
              .filter(lc -> lc.suppressionTracking)
              .map(category -> category.option)
              .map(name -> "\"" + name + "\"")
              .collect(Collectors.joining(", "));
            if (!unvalidatedNames.isEmpty())
                log.warning(node.annotation.pos(), LintWarnings.UnnecessaryWarningSuppression(unvalidatedNames));
        });
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
        final List<Span> unmappedDecls = new ArrayList<>();     // unmapped top-level declarations awaiting attribution
        final Map<Symbol, EnumSet<LintCategory>> validationsMap // maps declaration symbol to validations therein
          = new HashMap<>();

        // After parsing: Add top-level declarations to our "unmappedDecls" list
        FileInfo(Lint rootLint, JCCompilationUnit tree) {
            rootRange = new LintRange(rootLint);
            tree.defs.stream()
              .filter(this::isTopLevelDecl)
              .map(decl -> new Span(decl, tree.endPositions))
              .forEach(unmappedDecls::add);
        }

        // After attribution: Discard the span from "unmappedDecls" and populate the declaration's subtree under "rootRange"
        void afterAttr(Symtab syms, JCTree tree, EndPosTable endPositions) {
            for (Iterator<Span> i = unmappedDecls.iterator(); i.hasNext(); ) {
                if (i.next().contains(tree.pos())) {
                    rootRange.populateSubtree(this, syms, tree, endPositions);
                    i.remove();
                    return;
                }
            }
            throw new AssertionError("top-level declaration not found");
        }

        // Find the most specific Lint configuration applying to the given position, unless the position has not been mapped yet
        Optional<Lint> lintAt(DiagnosticPosition pos) {
            boolean mapped = unmappedDecls.stream().noneMatch(span -> span.contains(pos));
            return mapped ? Optional.of(rootRange.bestMatch(pos).lint) : Optional.empty();
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
        EnumSet<LintCategory> propagateValidations(LintRange lintRange) {
            return lintRange.propagateValidations(validationsMap);
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

        Span(JCTree tree, EndPosTable endPositions) {
            this(TreeInfo.getStartPos(tree), TreeInfo.getEndPos(tree, endPositions));
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
        Symbol symbol,                                  // declaration symbol (null for root range)
        List<LintRange> children,                       // the nested declarations one level below this node
        JCAnnotation annotation,                        // the @SuppressWarnings on this declaration, if any
        EnumSet<LintCategory> suppressions,             // categories suppressed by @SuppressWarnings, if any
        EnumSet<LintCategory> unvalidated               // categories in "suppressions" that were never validated
    ) {

        // Create a node representing the entire file, using the root lint configuration
        LintRange(Lint rootLint) {
            this(Span.MAXIMAL, rootLint, null, new ArrayList<>(), null, LintCategory.newEmptySet(), LintCategory.newEmptySet());
        }

        // Create a node representing the given declaration and its corresponding Lint configuration
        LintRange(JCTree tree, EndPosTable endPositions, Lint lint, Symbol symbol,
          JCAnnotation annotation, EnumSet<LintCategory> suppressions) {
            this(new Span(tree, endPositions), lint, symbol, new ArrayList<>(),
              annotation, suppressions, EnumSet.copyOf(suppressions));
        }

        // Find the most specific node in this tree (including me) that contains the given position, if any
        LintRange bestMatch(DiagnosticPosition pos) {
            return children.stream()
              .map(child -> child.bestMatch(pos))
              .filter(Objects::nonNull)
              .reduce((a, b) -> a.span.contains(b.span) ? b : a)
              .orElseGet(() -> span.contains(pos) ? this : null);
        }

        // Find the child containing the given position
        LintRange findChild(DiagnosticPosition pos) {
            return children.stream()
              .filter(node -> node.span.contains(pos))
              .findFirst()
              .orElseThrow(() -> new AssertionError("child not found"));
        }

        // Stream this node and all descendents via pre-order recursive descent
        Stream<LintRange> stream() {
            return Stream.concat(Stream.of(this), children.stream().flatMap(LintRange::stream));
        }

        // Calculate the unvalidated suppressions in the subtree rooted at this node. We do this by recursively
        // propagating validations upward until they are "caught" by some matching suppression; this validates
        // the suppression. Validations that are never caught and "escape" are returned to the caller.
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

        // Populate a sparse subtree corresponding to the given nested declaration.
        // Only "interesting" declarations are included:
        //  - Declarations that have a different Lint configuration than their parent
        //  - Declarations with a @SuppressWarnings annotation
        void populateSubtree(FileInfo fileInfo, Symtab syms, JCTree tree, EndPosTable endPositions) {
            new TreeScanner() {

                // Variables declared together (separated by commas) share their @SuppressWarnings annotation, so they must also
                // share the set of validated suppressions: the suppression of a category is valid if *any* of the variables
                // validates it. We detect that situation using this map and, when found, invoke FileInfo.mergeValidations().
                private final Map<JCAnnotation, VarSymbol> annotationRepresentativeSymbolMap = new HashMap<>();

                private LintRange currentNode = LintRange.this;

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

                private <T extends JCTree> void scanDecl(T tree,
                  Symbol symbol, JCAnnotation annotation, Consumer<? super T> recursor) {

                    // The "symbol" can be null if there were earlier errors; skip this declaration if so
                    if (symbol == null) {
                        recursor.accept(tree);
                        return;
                    }

                    // Update the Lint using the declaration
                    Lint newLint = currentNode.lint.augment(symbol);

                    // Get the lint categories explicitly suppressed at this symbol's declaration by @SuppressedWarnings
                    EnumSet<LintCategory> suppressed = Optional.ofNullable(annotation)
                      .map(anno -> lint.suppressionsFrom(anno))
                      .orElseGet(LintCategory::newEmptySet);

                    // Merge validation sets for variables that share the same declaration (and therefore @SuppressedWarnings)
                    if (annotation != null && symbol instanceof VarSymbol varSym) {
                        annotationRepresentativeSymbolMap.merge(annotation, varSym, (oldSymbol, newSymbol) -> {
                            fileInfo.mergeValidations(oldSymbol, newSymbol);
                            return oldSymbol;
                        });
                    }

                    // If this declaration is not "interesting", then we don't need a new node here
                    if (newLint == currentNode.lint && currentNode.symbol != null && suppressed.isEmpty()) {
                        recursor.accept(tree);
                        return;
                    }

                    // Add a new node here and proceed
                    final LintRange previousNode = currentNode;
                    currentNode = new LintRange(tree, endPositions, newLint, symbol, annotation, suppressed);
                    previousNode.children.add(currentNode);
                    try {
                        recursor.accept(tree);
                    } finally {
                        currentNode = previousNode;
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
            }.scan(tree);
        }
    }
}
