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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
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
 * and non-parsing mode. Warnings are actually emitted when the correpsonding declaration is {@link #flush}ed.
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
    private boolean parsing;

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

// Mode Switching

    /**
     * Enter parsing mode.
     */
    public void enterParsingMode() {
        Assert.check(!parsing);
        Assert.check(parsingDeferrals.isEmpty());
        parsing = true;
    }

    /**
     * Exit parsing mode and resolve each of the {@link Deferral}s accumulated during parsing to the
     * innermost containing declaration in the given tree.
     *
     * <p>
     * Any {@link Deferral}s that are not encompassed by a declaration are emitted using the root
     * {@link Lint} instance.
     *
     * @param tree top level node, or null if parsing failed
     */
    public void exitParsingMode(JCCompilationUnit tree) {
        Assert.check(parsing || tree == null);
        parsing = false;
        parsingDeferrals.sort(Comparator.comparingInt(Deferral::pos));          // sort deferrals by position
        if (tree != null) {
            new LexicalDeferralMapper(tree).map();                              // map them into deferralMap
            parsingDeferrals.forEach(deferral -> deferral.report(rootLint));    // report any leftovers
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
        Assert.check(parsing);
        reporterStack.push(logger -> parsingDeferrals.add(new Deferral(logger, pos)));
    }

    /**
     * Defer {@link #report}ed warnings until the given declaration is flushed.
     *
     * <p>
     * This is normally only invoked when in non-parsing mode, but it can also be invoked in
     * parsing mode if the declaration is known (e.g., see handling for "dangling-doc-comments").
     *
     * @param decl module, package, class, method, or variable declaration
     * @see #pop
     */
    public void push(JCTree decl) {
        //Assert.check(!parsing);
        Assert.check(decl.getTag() == Tag.MODULEDEF
                  || decl.getTag() == Tag.PACKAGEDEF
                  || decl.getTag() == Tag.CLASSDEF
                  || decl.getTag() == Tag.METHODDEF
                  || decl.getTag() == Tag.VARDEF);
        reporterStack.push(logger ->
            deferralMap.computeIfAbsent(decl, s -> new ArrayList<>())
              .add(new Deferral(logger, decl.getPreferredPosition())));
    }

    /**
     * Enter "immediate" mode so that reported warnings are emitted synchonously.
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
        Assert.check(!parsing);
        ArrayList<Deferral> deferrals = deferralMap.remove(decl);
        if (deferrals != null) {
            for (Deferral deferral : deferrals) {
                deferral.report(lint);
            }
        }
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

// Deferral

    /**
     * Represents a deferred warning.
     */
    private static class Deferral {

        private final LintLogger logger;
        private final int pos;

        Deferral(LintLogger logger, int pos) {
            this.logger = logger;
            this.pos = pos;
        }

        int pos() {
            return pos;
        }

        void report(Lint lint) {
            logger.report(lint);
        }

        // Does the position fit into the given range?
        boolean matches(int minPos, int maxPos) {
            return pos == minPos || (pos > minPos && pos < maxPos);
        }

        // Create a binary search key
        static Deferral key(int pos) {
            return new Deferral(null, pos);
        }
    }

// LexicalDeferralMapper

    // This scans a source file and identifies, for each lexical Deferral, the innermost
    // declaration that contains it and moves it to the corresponding entry in deferralMap.
    private class LexicalDeferralMapper extends TreeScanner {

        private final JCCompilationUnit tree;

        LexicalDeferralMapper(JCCompilationUnit tree) {
            this.tree = tree;
        }

        void map() {
            scan(tree);
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

            // We recurse *first* so the innermost matching declaration wins
            recursion.accept(decl);

            // Get the lexical extent of this declaration
            int minPos = decl.getPreferredPosition();
            int maxPos = decl.getEndPosition(tree.endPositions);

            // Find matching lexical Deferral's, if any, and move them into deferralMap
            int index = Collections.binarySearch(parsingDeferrals,
              Deferral.key(minPos), Comparator.comparingInt(Deferral::pos));
            if (index < 0)
                index = ~index;
            while (index < parsingDeferrals.size()) {
                Deferral deferral = parsingDeferrals.get(index);
                if (!deferral.matches(minPos, maxPos))
                    break;
                parsingDeferrals.remove(index);
                deferralMap.computeIfAbsent(decl, s -> new ArrayList<>()).add(deferral);
            }
        }
    }
}
