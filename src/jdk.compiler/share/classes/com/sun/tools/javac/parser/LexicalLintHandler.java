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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.tools.javac.code.DeferredLintHandler;
import com.sun.tools.javac.code.DeferredLintHandler.LintLogger;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.Tag;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.JCDiagnostic.LintWarning;

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
    private final ArrayList<DeclNode> declNodes = new ArrayList<>();

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

        // Basic sanity checks
        Assert.check(!flushed);
        Assert.check(decl.getTag() == Tag.MODULEDEF
                  || decl.getTag() == Tag.PACKAGEDEF
                  || decl.getTag() == Tag.CLASSDEF
                  || decl.getTag() == Tag.METHODDEF
                  || decl.getTag() == Tag.VARDEF);

        // Create new declaration node
        DeclNode declNode = new DeclNode(decl, endPos);

        // Verify our assumptions about declarations:
        //  1. If two declarations overlap, then one of them must nest within the other
        //  2. endDecl() is invoked in order of increasing declaration ending position
        if (!declNodes.isEmpty()) {
            DeclNode prevNode = declNodes.get(declNodes.size() - 1);
            Assert.check(declNode.endPos() >= prevNode.endPos());
            Assert.check(declNode.startPos() >= prevNode.endPos() || declNode.startPos() <= prevNode.startPos());
        }

        // Add node to the list
        declNodes.add(declNode);
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

        // Assign the innermost containing declaration, if any, to each report
        ListIterator<Report> reportIterator = reports.listIterator(0);
      declLoop:
        for (DeclNode declNode : declNodes) {
            while (true) {
                if (!reportIterator.hasNext())
                    break declLoop;
                Report report = reportIterator.next();
                switch (report.relativeTo(declNode)) {
                case BEFORE:        // report is contained by some outer declaration, or is "top level"
                    continue;
                case WITHIN:        // assign to this declaration, unless contained by an inner declaration
                    report.decl().compareAndSet(null, declNode.decl());
                    continue;
                case AFTER:         // we've gone too far, backup one step and go to the next declaration
                    reportIterator.previous();
                    continue declLoop;
                }
            }
        }

        // Now flush all the reports
        reports.forEach(report -> report.flushTo(deferredLintHandler));

        // Clean up
        reports.clear();
        declNodes.clear();
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

// DeclNode

    // A declaration that has been created and whose starting and ending positions are known
    private record DeclNode(JCTree decl, int startPos, int endPos) {

        DeclNode(JCTree decl, int endPos) {
            this(decl, TreeInfo.getStartPos(decl), endPos);
        }
    }

// Report

    // A warning report that has not yet been flushed to the DeferredLintHandler
    private record Report(int pos, LintLogger logger, AtomicReference<JCTree> decl) {

        Report(int pos, LintLogger logger) {
            this(pos, logger, new AtomicReference<>());
        }

        // Flush this report to the DeferredLintHandler using our assigned declaration (if any)
        void flushTo(DeferredLintHandler deferredLintHandler) {
            JCTree decl = decl().get();
            if (decl != null)
                deferredLintHandler.push(decl);
            try {
                deferredLintHandler.report(logger());
            } finally {
                if (decl != null)
                    deferredLintHandler.pop();
            }
        }

        // Determine our position relative to the range spanned by the given declaration
        Direction relativeTo(DeclNode declNode) {
            int startPos = declNode.startPos();
            int endPos = declNode.endPos();
            if (pos() < startPos)
                return Direction.BEFORE;
            if (pos() == startPos || (pos() > startPos && pos() < endPos)) {
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
