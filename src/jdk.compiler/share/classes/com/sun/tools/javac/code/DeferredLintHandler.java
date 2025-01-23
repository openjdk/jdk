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

import java.util.HashMap;
import java.util.Map;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.Tag;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.ListBuffer;

/**
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

    /** The Lint to use when {@link #immediate(Lint)} is used,
     * instead of {@link #setDecl(JCTree)}. */
    private Lint immediateLint;

    @SuppressWarnings("this-escape")
    protected DeferredLintHandler(Context context) {
        context.put(deferredLintHandlerKey, this);
        immediateLint = Lint.instance(context);
    }

    /**An interface for deferred lint reporting - loggers passed to
     * {@link #report(LintLogger) } will be called when
     * {@link #flush(JCTree)} is invoked.
     */
    public interface LintLogger {
        void report(Lint lint);
    }

    private JCTree currentDecl;     // null means "immediate mode"
    private Map<JCTree, ListBuffer<LintLogger>> loggersQueue = new HashMap<>();

    /**Associate the given logger with the current declaration as set by {@link #setDecl(JCTree)}.
     * Will be invoked when {@link #flush(JCTree)} is invoked with the same declaration.
     * <br>
     * Will invoke the logger synchronously if {@link #immediate() } was called
     * instead of {@link #setDecl(JCTree)}.
     */
    public void report(LintLogger logger) {
        if (currentDecl == null) {
            logger.report(immediateLint);
        } else {
            loggersQueue.computeIfAbsent(currentDecl, d -> new ListBuffer<>()).append(logger);
        }
    }

    /**Invoke all {@link LintLogger}s that were associated with the provided declaration.
     */
    public void flush(JCTree decl, Lint lint) {
        ListBuffer<LintLogger> loggers = loggersQueue.remove(decl);
        if (loggers != null) {
            for (LintLogger lintLogger : loggers) {
                lintLogger.report(lint);
            }
        }
    }

    /**Sets the current declaration to the provided {@code decl}. {@link LintLogger}s
     * passed to subsequent invocations of {@link #report(LintLogger) } will be associated
     * with the given declaration.
     *
     * @param decl new current declaration, or null to restore immediate mode
     * @return previous current declaration, or null if previously in immediate mode
     */
    public JCTree setDecl(JCTree decl) {
        Assert.check(decl == null
                  || decl.getTag() == Tag.MODULEDEF
                  || decl.getTag() == Tag.PACKAGEDEF
                  || decl.getTag() == Tag.CLASSDEF
                  || decl.getTag() == Tag.METHODDEF
                  || decl.getTag() == Tag.VARDEF);
        JCTree prevDecl = this.currentDecl;
        this.currentDecl = decl;
        return prevDecl;
    }

    /**{@link LintLogger}s passed to subsequent invocations of
     * {@link #report(LintLogger) } will be invoked immediately.
     */
    public JCTree immediate(Lint lint) {
        immediateLint = lint;
        return setDecl(null);
    }
}
