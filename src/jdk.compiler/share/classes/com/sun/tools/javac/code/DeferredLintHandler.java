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

import com.sun.tools.javac.tree.EndPosTable;
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
 * There is also an "immediate" mode, during which warnings are emitted synchronously.
 *
 * <p>
 * Deferred warnings are grouped by the innermost containing module, package, class, method, or variable
 * declaration (represented by {@link JCTree} nodes), so that the corresponding {@link Lint} configuration
 * can be applied when the warning is eventually generated. During parsing, no {@link JCTree} nodes exist
 * yet, so warnings are stored only by file character offset. Once parsing completes, these offsets are
 * resolved into {@link JCTree} nodes by {@link #resolvePositionDeferrals}.
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
     * The current {@link Reporter} stack.
     */
    private final ArrayDeque<Reporter> reporterStack = new ArrayDeque<>();

    /**
     * Registered lexical {@link Deferral}s for the source file currently being parsed.
     *
     * <p>
     * These are resolved and moved to {@link #deferralMap} once the parse is complete.
     * See {@link #resolvePositionDeferrals}.
     */
    private final ArrayList<PositionDeferral> positionDeferrals = new ArrayList<>();

    /**
     * Registered {@link Deferral}s grouped by declaration.
     */
    private final HashMap<JCTree, ArrayList<Deferral>> deferralMap = new HashMap<>();

    /**
     * Mapping from source file position to innermost enclosing declaration.
     */
    private final ArrayList<DeclPosition> declPositions = new ArrayList<>();

    @SuppressWarnings("this-escape")
    protected DeferredLintHandler(Context context) {
        context.put(deferredLintHandlerKey, this);
        rootLint = Lint.instance(context);
        pushImmediate(rootLint);            // default to "immediate" mode
    }

    /**
     * Defer reported warnings until the declaration encompassing the given location,
     * or the given source file if none, is flushed.
     *
     * <p>
     * This is invoked during file parsing only to configure handling for lexical warnings.
     *
     * @param pos character offset
     * @see #pop
     */
    public void push(int pos) {
        reporterStack.push(new PositionReporter(pos));
    }

    /**
     * Defer reported warnings until the given declaration is flushed.
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
        reporterStack.push(new DeclarationReporter(decl));
    }

    /**
     * Enter "immediate" mode so that reported warnings are emitted synchonously.
     *
     * @param lint lint configuration to use for reported warnings
     */
    public void pushImmediate(Lint lint) {
        reporterStack.push(new ImmediateReporter(lint));
    }

    /**
     * Revert to the current declaration or immediate mode that was in effect prior to the
     * most recent invocation of {@link #push} or {@link #pushImmediate}.
     *
     * @see #pop
     */
    public void pop() {
        Assert.check(reporterStack.size() > 1);     // the bottom stack entry should never be popped
        reporterStack.pop();
    }

    /**
     * Register a warning at the current location or declaration.
     *
     * <p>
     * In immediate mode, the warning is emitted synchronously. Otherwise, the warning is emitted later
     * when the current declaration is flushed.
     */
    public void report(LintLogger logger) {
        Assert.check(!reporterStack.isEmpty());
        reporterStack.peek().report(this, logger);
    }

    /**
     * Associate {@link PositionDeferral}s accumulated during parsing with the innermost
     * containing declarations in the given tree.
     *
     * <p>
     * Any {@link PositionDeferral}s that are not encompassed by a declaration are emitted at this point,
     * using the root {@link Lint} instance.
     *
     * @param tree top level node
     * @param endPos ending position table
     */
    public void resolvePositionDeferrals(JCCompilationUnit tree, EndPosTable endPosTable) {
        positionDeferrals.sort(Comparator.comparingInt(PositionDeferral::pos)); // ensure deferrals are sorted by position
        new PositionDeferralConverter(endPosTable).scan(tree);
        positionDeferrals.forEach(deferral -> deferral.report(rootLint));
        positionDeferrals.clear();
    }

    /**
     * Discard any {@link PositionDeferral}s accumulated during parsing.
     *
     * <p>
     * This should be invoked after parsing if an error occurred and {@link #resolvePositionDeferrals}
     * won't be invoked.
     */
    public void resetPositionDeferrals() {
        positionDeferrals.clear();
    }

    /**
     * Emit deferred warnings encompassed by the given declaration.
     *
     * @param decl module, package, class, method, or variable declaration
     * @param lint lint configuration corresponding to {@code decl}
     */
    public void flush(JCTree decl, Lint lint) {
        Assert.check(positionDeferrals.isEmpty());      // should have been resolved already
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

// Reporter

    /**
     * Handler for {@code report()} requests.
     */
    private abstract static class Reporter {

        abstract void report(DeferredLintHandler handler, LintLogger logger);
    }

    // Handles report() in immediate mode
    private static class ImmediateReporter extends Reporter {

        private final Lint lint;

        ImmediateReporter(Lint lint) {
            this.lint = lint;
        }

        @Override
        void report(DeferredLintHandler handler, LintLogger logger) {
            logger.report(lint);
        }
    }

    // Handles report() when there is a current declaration
    private static class DeclarationReporter extends Reporter {

        private final JCTree decl;

        DeclarationReporter(JCTree decl) {
            this.decl = decl;
        }

        @Override
        void report(DeferredLintHandler handler, LintLogger logger) {
            handler.deferralMap.computeIfAbsent(decl, s -> new ArrayList<>())
              .add(new Deferral(logger));
        }
    }

    // Handles report() when there is a current position
    private static class PositionReporter extends Reporter {

        private final int pos;

        PositionReporter(int pos) {
            this.pos = pos;
        }

        @Override
        void report(DeferredLintHandler handler, LintLogger logger) {
            handler.positionDeferrals.add(new PositionDeferral(logger, pos));
        }
    }

// Deferral

    /**
     * Represents a deferred warning.
     */
    private static class Deferral {

        private final LintLogger logger;

        Deferral(LintLogger logger) {
            this.logger = logger;
        }

        void report(Lint lint) {
            logger.report(lint);
        }
    }

    /**
     * A {@link Deferral} generated during parsing corresponding to a lexical position in a source file.
     */
    private static class PositionDeferral extends Deferral {

        private final int pos;

        PositionDeferral(LintLogger logger, int pos) {
            super(logger);
            this.pos = pos;
        }

        int pos() {
            return pos;
        }

        boolean matches(int minPos, int maxPos) {
            return pos == minPos || (pos > minPos && pos < maxPos);
        }

        static PositionDeferral key(int pos) {
            return new PositionDeferral(null, pos);
        }
    }

// DeclPosition

    private record DeclPosition(JCTree decl, int minPos, int maxPos) {

        boolean matches(int pos) {
            return pos == minPos() || (pos > minPos() && pos < maxPos());
        }
    }

    // This scans a source file and identifies, for each PositionDeferral, the innermost module,
    // package, class, method, of variable declaration that contains it, if any.
    private class PositionDeferralConverter extends TreeScanner {

        private final EndPosTable endPosTable;

        PositionDeferralConverter(EndPosTable endPosTable) {
            this.endPosTable = endPosTable;
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
            int maxPos = decl.getEndPosition(endPosTable);

            // Find matching PositionDeferral's, if any, and move them into deferralMap
            int index = Collections.binarySearch(positionDeferrals,
              PositionDeferral.key(minPos), Comparator.comparingInt(PositionDeferral::pos));
            if (index < 0)
                index = ~index;
            while (index < positionDeferrals.size()) {
                PositionDeferral deferral = positionDeferrals.get(index);
                if (!deferral.matches(minPos, maxPos))
                    break;
                positionDeferrals.remove(index);
                deferralMap.computeIfAbsent(decl, s -> new ArrayList<>()).add(deferral);
            }
        }
    }
}
