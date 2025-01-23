/*
 * Copyright (c) 2011, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Optional;
import java.util.function.Consumer;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.Context;

/**
 * Holds pending {@link Lint} warnings until the {@lint Lint} instance associated with the containing
 * module, package, class, method, or variable declaration is known so that {@link @SupressWarnings}
 * suppressions may be applied.
 *
 * <p>
 * Warnings are regsistered at any time prior to attribution via {@link #report}. The warning will be
 * associated with the file position (if parsing) or declaration (after parsing) placed in context by
 * the most recent invocation of {@link #push push()} not yet {@link #pop}'d. Warnings are actually
 * emitted later, during attribution, via {@link #flush}.
 *
 * <p>
 * There is also an "immediate" mode, where warnings are emitted synchronously; see {@link #pushImmediate}.
 *
 * <p>
 * Deferred warnings are grouped by the innermost containing module, package, class, method, or variable
 * declaration (represented by {@link JCTree} nodes), so that the corresponding {@link Lint} configuration
 * can be applied when the warning is eventually generated. During parsing, no {@link JCTree} nodes exist
 * yet, so warnings are stored by file character offset. Once parsing completes, these offsets are resolved
 * to the innermost containing declaration. This class therefore operates in two distinct modes: parsing mode
 * and non-parsing mode. Warnings are emitted when the correpsonding declaration is {@link #flush}ed.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 */
public class DeferredLintHandler {

    protected static final Context.Key<DeferredLintHandler> deferredLintHandlerKey = new Context.Key<>();

    public static DeferredLintHandler instance(Context context) {
        DeferredLintHandler instance = context.get(deferredLintHandlerKey);
        if (instance == null)
            instance = new DeferredLintHandler(context);
        return instance;
    }

    /**
     * The root lint instance.
     */
    private final Lint rootLint;

    /**
     * Are we in parsing mode or non-parsing mode?
     */
    private boolean parsingMode;

    /**
     * Registered lexical {@link Deferral}s for the source file currently being parsed.
     *
     * <p>
     * This list is only used when parsing a source file. Once parsing ends, these deferrals
     * are resolved to their corresponding declarations and moved to {@link #deferralMap}.
     */
    private ArrayList<Deferral> parsingDeferrals = new ArrayList<>();

    /**
     * Registered {@link Deferral}s grouped by the innermost containing module, package, class,
     * method, or variable declaration.
     */
    private final HashMap<JCTree, ArrayList<Deferral>> deferralMap = new HashMap<>();

    /**
     * The current "reporter" stack, reflecting calls to {@link #push} and {@link #pop}.
     *
     * <p>
     * The top of the stack determines how calls to {@link #report} are handled.
     */
    private final ArrayDeque<Consumer<LintLogger>> reporterStack = new ArrayDeque<>();

    @SuppressWarnings("this-escape")
    protected DeferredLintHandler(Context context) {
        context.put(deferredLintHandlerKey, this);
        rootLint = Lint.instance(context);
        pushImmediate(rootLint);            // default to "immediate" mode
    }

// LintLogger

    /**
     * Callback interface for deferred lint reporting.
     */
    public interface LintLogger {

        /**
         * Generate a warning if appropriate.
         *
         * @param lint the applicable lint configuration
         */
        void report(Lint lint);
    }

// Mode Switching

    /**
     * Enter parsing mode.
     */
    public void enterParsingMode() {
        Assert.check(!parsingMode);
        Assert.check(parsingDeferrals.isEmpty());
        parsingMode = true;
    }

    /**
     * Exit parsing mode and resolve each of the lexical {@link Deferral}s accumulated during parsing
     * to the innermost containing declaration in the given tree.
     *
     * <p>
     * Any lexical {@link Deferral}s that are not encompassed by a declaration are emitted using
     * the root {@link Lint} instance.
     *
     * @param tree top level node, or null to clean up after parsing failed
     */
    public void exitParsingMode(JCCompilationUnit tree) {
        Assert.check(parsingMode || tree == null);
        parsingMode = false;
        if (tree != null && !parsingDeferrals.isEmpty()) {

            // Map lexical Deferral's to corresponding declarations
            new LexicalDeferralMapper(tree).mapLexicalDeferrals();

            // Report any remainders immediately (must be outside the top level declaration)
            Optional.ofNullable(deferralMap.remove(null))
              .stream()
              .flatMap(ArrayList::stream)
              .forEach(deferral -> deferral.logger().report(rootLint));
        }
        parsingDeferrals.clear();
    }

// Reporter Stack

    /**
     * Defer {@link #report}ed warnings until the declaration encompassing the given
     * source file position is flushed.
     *
     * <p>
     * This should only be invoked when in parsing mode.
     *
     * @param pos character offset
     * @see #pop
     */
    public void push(int pos) {
        Assert.check(parsingMode);
        reporterStack.push(logger -> parsingDeferrals.add(new Deferral(logger, pos)));
    }

    /**
     * Defer {@link #report}ed warnings until the given declaration is flushed.
     *
     * <p>
     * This is normally only invoked when in non-parsing mode, but it can also be invoked in
     * parsing mode if the declaration is known (e.g., see "dangling-doc-comments" handling).
     *
     * @param decl module, package, class, method, or variable declaration
     * @see #pop
     */
    public void push(JCTree decl) {
        Assert.check(decl.getTag() == Tag.MODULEDEF
                  || decl.getTag() == Tag.PACKAGEDEF
                  || decl.getTag() == Tag.CLASSDEF
                  || decl.getTag() == Tag.METHODDEF
                  || decl.getTag() == Tag.VARDEF);
        reporterStack.push(logger -> deferralMap
                                        .computeIfAbsent(decl, s -> new ArrayList<>())
                                        .add(new Deferral(logger)));
    }

    /**
     * Enter "immediate" mode so that {@link #report}ed warnings are emitted synchonously.
     *
     * @param lint lint configuration to use for reported warnings
     */
    public void pushImmediate(Lint lint) {
        reporterStack.push(logger -> logger.report(lint));
    }

    /**
     * Revert to the previous configuration in effect prior to the most recent invocation
     * of {@link #push} or {@link #pushImmediate}.
     *
     * @see #pop
     */
    public void pop() {
        Assert.check(reporterStack.size() > 1);     // the bottom stack entry should never be popped
        reporterStack.pop();
    }

    /**
     * Report a warning.
     *
     * <p>
     * In immediate mode, the warning is emitted synchronously. Otherwise, the warning is emitted later
     * when the current declaration is flushed.
     */
    public void report(LintLogger logger) {
        Assert.check(!reporterStack.isEmpty());
        reporterStack.peek().accept(logger);
    }

// Warning Flush

    /**
     * Emit deferred warnings encompassed by the given declaration.
     *
     * @param decl module, package, class, method, or variable declaration
     * @param lint lint configuration corresponding to {@code decl}
     */
    public void flush(JCTree decl, Lint lint) {
        Assert.check(!parsingMode);
        Optional.of(decl)
          .map(deferralMap::remove)
          .stream()
          .flatMap(ArrayList::stream)
          .map(Deferral::logger)
          .forEach(logger -> logger.report(lint));
    }

// Deferral

    /**
     * Represents a deferred warning.
     *
     * @param logger the logger that will report the warning
     * @param pos character offset in the source file (parsing mode only)
     */
    private record Deferral(LintLogger logger, int pos) {

        // Create an instance in non-parsing mode
        Deferral(LintLogger logger) {
            this(logger, -1);
        }

        // Compare our position to the given declaration range. Only used for lexical deferrals.
        int compareToRange(int minPos, int maxPos) {
            if (pos() < minPos)
                return -1;
            if (pos() == minPos || (pos() > minPos && pos() < maxPos))
                return 0;
            return 1;
        }
    }

// LexicalDeferralMapper

    // This scans a source file and identifies, for each lexical Deferral, the innermost
    // declaration that contains it and moves it to the corresponding entry in deferralMap.
    private class LexicalDeferralMapper extends TreeScanner {

        private final JCCompilationUnit tree;

        private JCTree[] declMap;
        private int currentDeferral;

        LexicalDeferralMapper(JCCompilationUnit tree) {
            this.tree = tree;
        }

        void mapLexicalDeferrals() {

            // Sort lexical deferrals by position so our "online" algorithm works.
            // We also depend on TreeScanner visiting declarations in lexical order.
            parsingDeferrals.sort(Comparator.comparingInt(Deferral::pos));

            // Initialize our mapping table
            declMap = new JCTree[parsingDeferrals.size()];
            currentDeferral = 0;

            // Scan declarations and map lexical deferrals to them
            try {
                scan(tree);
            } catch (ShortCircuitException e) {
                // got done early
            }

            // Move lexical deferrals to their corresponding declarations (or null for remainders)
            for (int i = 0; i < declMap.length; i++) {
                deferralMap.computeIfAbsent(declMap[i], s -> new ArrayList<>()).add(parsingDeferrals.get(i));
            }
        }

    // TreeScanner methods

        @Override
        public void visitModuleDef(JCModuleDecl tree) {
            scanDecl(tree, super::visitModuleDef);
        }

        @Override
        public void visitPackageDef(JCPackageDecl tree) {
            scanDecl(tree, super::visitPackageDef);
        }

        @Override
        public void visitClassDef(JCClassDecl tree) {
            scanDecl(tree, super::visitClassDef);
        }

        @Override
        public void visitMethodDef(JCMethodDecl tree) {
            scanDecl(tree, super::visitMethodDef);
        }

        @Override
        public void visitVarDef(JCVariableDecl tree) {
            scanDecl(tree, super::visitVarDef);
        }

        private <T extends JCTree> void scanDecl(T decl, Consumer<? super T> recursion) {

            // Get the lexical extent of this declaration
            int minPos = decl.getPreferredPosition();
            int maxPos = decl.getEndPosition(tree.endPositions);

            // Skip forward through lexical deferrals until we hit this declaration
            while (true) {

                // We can stop scanning once we pass the last lexical deferral
                if (currentDeferral >= parsingDeferrals.size()) {
                    throw new ShortCircuitException();
                }

                // Get the deferral currently under consideration
                Deferral deferral = parsingDeferrals.get(currentDeferral);

                // Is its position prior to this declaration?
                int relativePosition = deferral.compareToRange(minPos, maxPos);
                if (relativePosition < 0) {
                    currentDeferral++;      // already past it
                    continue;
                }

                // Is its position after this declaration?
                if (relativePosition > 0) {
                    break;                  // stop for now; a subsequent declaration might match
                }

                // Deferral's position is within this declaration - we should map it.
                // Note this declaration may not be the innermost containing declaration,
                // but if not, that's OK: the narrower declaration will follow and overwrite.
                declMap[currentDeferral] = decl;
                break;
            }

            // Recurse
            recursion.accept(decl);
        }
    }

// ShortCircuitException

    @SuppressWarnings("serial")
    private static class ShortCircuitException extends RuntimeException {
    }
}
