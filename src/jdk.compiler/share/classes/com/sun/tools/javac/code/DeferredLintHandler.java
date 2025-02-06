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
import java.util.HashMap;
import java.util.Optional;
import java.util.function.Consumer;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.Tag;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.Context;

/**
 * Holds pending {@link Lint} warnings until the {@lint Lint} instance associated with the containing
 * module, package, class, method, or variable declaration is known so that {@link @SupressWarnings}
 * suppressions may be applied.
 *
 * <p>
 * Warnings are regsistered at any time prior to attribution via {@link #report}. The warning will be
 * associated with the declaration placed in context by the most recent invocation of {@link #push push()}
 * not yet {@link #pop}'d. Warnings are actually emitted later, during attribution, via {@link #flush}.
 *
 * <p>
 * There is also an "immediate" mode, where warnings are emitted synchronously; see {@link #pushImmediate}.
 *
 * <p>
 * Deferred warnings are grouped by the innermost containing module, package, class, method, or variable
 * declaration (represented by {@link JCTree} nodes), so that the corresponding {@link Lint} configuration
 * can be applied when the warning is eventually generated.
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
     * Registered {@link LintLogger}s grouped by the innermost containing module, package, class,
     * method, or variable declaration.
     */
    private final HashMap<JCTree, ArrayList<LintLogger>> deferralMap = new HashMap<>();

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
        Lint rootLint = Lint.instance(context);
        pushImmediate(rootLint);            // default to "immediate" mode
    }

// LintLogger

    /**An interface for deferred lint reporting - loggers passed to
     * {@link #report(LintLogger) } will be called when
     * {@link #flush(DiagnosticPosition) } is invoked.
     */
    public interface LintLogger {

        /**
         * Generate a warning if appropriate.
         *
         * @param lint the applicable lint configuration
         */
        void report(Lint lint);
    }

// Reporter Stack

    /**
     * Defer {@link #report}ed warnings until the given declaration is flushed.
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
                                        .add(logger));
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
        Optional.of(decl)
          .map(deferralMap::remove)
          .stream()
          .flatMap(ArrayList::stream)
          .forEach(logger -> logger.report(lint));
    }
}
