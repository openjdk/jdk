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

package com.sun.tools.javac.parser;

import java.util.ArrayDeque;
import java.util.LinkedList;
import java.util.ListIterator;

import com.sun.tools.javac.code.DeferredLintHandler;
import com.sun.tools.javac.code.DeferredLintHandler.LintLogger;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.JCDiagnostic.LintWarning;
import com.sun.tools.javac.util.JCDiagnostic.SimpleDiagnosticPosition;

/**
 * Stashes lint warnings suppressible via {@code @SuppressWarnings} and their source code
 * positions while we await the creation of the innermost containing declaration.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class LexicalLintHandler {

    private final LinkedList<Report> reports = new LinkedList<>();
    private final ArrayDeque<DeclNode> declNodes = new ArrayDeque<>();

    private int lastFlushedStartPos;
    private int lastFlushedEndPos;
    private boolean flushed;

// Public API

    /**
     * Report a lexical warning subject to possible suppression by {@code @SuppressWarnings}.
     *
     * @param pos the lexical position at which the warning occurs
     * @param logger the warning callback
     */
    public void report(DiagnosticPosition pos, LintLogger logger) {
        addReport(new Report(pos.getStartPosition(), logger));
    }

    /**
     * Report a lexical warning subject to possible suppression by {@code @SuppressWarnings}.
     *
     * @param pos the lexical position at which the warning occurs
     * @param key the warning to report
     */
    public void report(DiagnosticPosition pos, LintWarning key) {
        report(pos, lint -> lint.logIfEnabled(pos, key));
    }

    /**
     * Report the creation of a declaration that supports {@code @SuppressWarnings}.
     *
     * @param decl the newly parsed declaration
     * @param endPos the ending position of {@code decl} (exclusive)
     * @return the given {@code decl} (for fluent chaining)
     */
    public <T extends JCTree> T endDecl(T decl, int endPos) {
        Assert.check(!flushed);
        declNodes.addLast(new DeclNode(decl, endPos));
        return decl;
    }

    /**
     * Flush all reported warnings to the given {@link DeferredLintHandler} in association
     * their innermost enclosing declaration, if any.
     *
     * <p>
     * This must be invoked at the end, after parsing a file. Once this method is invoked,
     * no further invocations of {@link #report} or {@link #endDecl} are allowed.
     */
    public void flushTo(DeferredLintHandler deferredLintHandler) {
        Assert.check(!flushed);
        flushed = true;

        // Flush reports contained within any of the declaration nodes we have gathered
        declNodes.forEach(declNode -> flushDeclReports(deferredLintHandler, declNode));
        declNodes.clear();

        // Flush the remaining reports, which must be "top level" (i.e., not contained within any declaration)
        reports.forEach(report -> flushReport(deferredLintHandler, report));
        reports.clear();
    }

// Internal Methods

    // Add a new report to our list, which we keep sorted by position, in the appropriate spot.
    // Reports are (usually? always?) generated in source position order, so this should be quick.
    private void addReport(Report report) {
        Assert.check(!flushed);
        ListIterator<Report> i = reports.listIterator(reports.size());
        while (i.hasPrevious()) {
            if (i.previous().pos() <= report.pos()) {
                i.next();
                break;
            }
        }
        i.add(report);
    }

    // Flush all reports contained within the given declaration
    private void flushDeclReports(DeferredLintHandler deferredLintHandler, DeclNode declNode) {

        // Get declaration's starting position so we know its lexical range
        int startPos = TreeInfo.getStartPos(declNode.decl());
        int endPos = declNode.endPos();

        // Sanity check our assumptions about declarations:
        //  1. If two declarations overlap, then one of them must nest within the other
        //  2. endDecl() is always invoked in order of increasing declaration ending position
        Assert.check(endPos >= lastFlushedEndPos);
        Assert.check(startPos >= lastFlushedEndPos || startPos <= lastFlushedStartPos);

        // Find all reports contained by the declaration; they should all be at or near the end of the list
        ListIterator<Report> i = reports.listIterator(reports.size());
        int count = 0;
        while (i.hasPrevious()) {
            switch (i.previous().compareToRange(startPos, endPos)) {
            case AFTER:     // unusual; e.g., report is contained in the next token after declaration
                continue;
            case WITHIN:    // report is contained in the declaration, so we will flush it
                count++;
                continue;
            case BEFORE:    // we've gone too far, backup one step and start here
                i.next();
                break;
            }
            break;
        }

        // Flush the reports contained by the declaration (in order). Note, we know that it is the innermost
        // containing declaration because any more deeply nested declarations must have already been flushed
        // by now; this follows from the above assumptions.
        deferredLintHandler.push(declNode.decl());
        try {
            while (count-- > 0) {
                flushReport(deferredLintHandler, i.next());
                i.remove();
            }
        } finally {
            deferredLintHandler.pop();
        }

        // Update markers
        lastFlushedStartPos = startPos;
        lastFlushedEndPos = endPos;
    }

    private void flushReport(DeferredLintHandler deferredLintHandler, Report report) {
        deferredLintHandler.report(report.logger());
    }

// DeclNode

    // A declaration that has been created and whose starting and ending positions are known
    private record DeclNode(JCTree decl, int endPos) { }

// Report

    // A warning report that has not yet been flushed to the DeferredLintHandler
    private record Report(int pos, LintLogger logger) {

        // Compare our position to the given range
        Direction compareToRange(int minPos, int maxPos) {
            if (pos() < minPos)
                return Direction.BEFORE;
            if (pos() == minPos || (pos() > minPos && pos() < maxPos)) {
                return Direction.WITHIN;
            }
            return Direction.AFTER;
        }
    }

// Direction

    // Describes where a source code position lies relative to some range of positions
    private enum Direction {
        BEFORE,
        WITHIN,
        AFTER;
    }
}
