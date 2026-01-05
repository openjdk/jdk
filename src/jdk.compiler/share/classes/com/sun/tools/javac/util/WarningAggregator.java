/*
 * Copyright (c) 2005, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.tools.JavaFileObject;

import com.sun.tools.javac.code.Lint;
import com.sun.tools.javac.code.Lint.LintCategory;
import com.sun.tools.javac.code.Source;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticFlag;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.JCDiagnostic.LintWarning;
import com.sun.tools.javac.util.JCDiagnostic.Note;
import com.sun.tools.javac.util.JCDiagnostic.Warning;


/**
 * An aggregator for warnings, setting up a deferred diagnostic
 * to be printed at the end of the compilation if some warnings get suppressed
 * because the lint category is not enabled or too many warnings have already
 * been generated.
 *
 * <p>
 * All warnings must be in the same {@link LintCategory} provided to the constructor.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
class WarningAggregator {

    /**
     * The kinds of different deferred diagnostics that might be generated
     * if a warning is suppressed because too many warnings have already been output.
     *
     * The parameter is a fragment used to build an I18N message key for Log.
     */
    private enum DeferredDiagnosticKind {
        /**
         * This kind is used when a single specific file is found to have warnings
         * and no similar warnings have already been given.
         * It generates a message like:
         *      FILE has ISSUES
         */
        IN_FILE(".filename"),
        /**
         * This kind is used when a single specific file is found to have warnings
         * and when similar warnings have already been reported for the file.
         * It generates a message like:
         *      FILE has additional ISSUES
         */
        ADDITIONAL_IN_FILE(".filename.additional"),
        /**
         * This kind is used when multiple files have been found to have warnings,
         * and none of them have had any similar warnings.
         * It generates a message like:
         *      Some files have ISSUES
         */
        IN_FILES(".plural"),
        /**
         * This kind is used when multiple files have been found to have warnings,
         * and some of them have had already had specific similar warnings.
         * It generates a message like:
         *      Some files have additional ISSUES
         */
        ADDITIONAL_IN_FILES(".plural.additional");

        DeferredDiagnosticKind(String v) { value = v; }
        String getKey(String prefix) { return prefix + value; }

        private final String value;
    }


    /**
     * Create an aggregator for warnings.
     *
     * @param log     The log on which to generate any diagnostics
     * @param source  Associated source file, or null for none
     * @param lc      The lint category for all warnings
     */
    public WarningAggregator(Log log, Source source, LintCategory lc) {
        this(log, source, lc, null);
    }

    /**
     * Create an aggregator for warnings.
     *
     * @param log     The log on which to generate any diagnostics
     * @param source  Associated source file, or null for none
     * @param lc      The lint category for all warnings
     * @param prefix  A common prefix for the set of message keys for the messages
     *                that may be generated, or null to infer from the lint category.
     */
    public WarningAggregator(Log log, Source source, LintCategory lc, String prefix) {
        this.log = log;
        this.source = source;
        this.prefix = prefix != null ? prefix : lc.option;
        this.lintCategory = lc;
    }

    /**
     * Aggregate a warning and determine whether to emit it.
     *
     * @param diagnostic the warning
     * @param verbose whether the warning's lint category is enabled
     * @return true if diagnostic should be emitted, otherwise false
     */
    public boolean aggregate(JCDiagnostic diagnostic, boolean verbose) {
        Assert.check(diagnostic.getLintCategory() == lintCategory);
        JavaFileObject currentSource = log.currentSourceFile();
        if (verbose) {
            if (sourcesWithReportedWarnings == null)
                sourcesWithReportedWarnings = new HashSet<>();
            if (log.nwarnings < log.MaxWarnings) {
                // generate message and remember the source file
                sourcesWithReportedWarnings.add(currentSource);
                anyWarningEmitted = true;
                return true;
            } else if (deferredDiagnosticKind == null) {
                // set up deferred message
                if (sourcesWithReportedWarnings.contains(currentSource)) {
                    // more errors in a file that already has reported warnings
                    deferredDiagnosticKind = DeferredDiagnosticKind.ADDITIONAL_IN_FILE;
                } else {
                    // warnings in a new source file
                    deferredDiagnosticKind = DeferredDiagnosticKind.IN_FILE;
                }
                deferredDiagnosticSource = currentSource;
                deferredDiagnosticArg = currentSource;
            } else if ((deferredDiagnosticKind == DeferredDiagnosticKind.IN_FILE
                        || deferredDiagnosticKind == DeferredDiagnosticKind.ADDITIONAL_IN_FILE)
                       && !Objects.equals(deferredDiagnosticSource, currentSource)) {
                // additional errors in more than one source file
                deferredDiagnosticKind = DeferredDiagnosticKind.ADDITIONAL_IN_FILES;
                deferredDiagnosticArg = null;
            }
        } else {
            if (deferredDiagnosticKind == null) {
                // warnings in a single source
                deferredDiagnosticKind = DeferredDiagnosticKind.IN_FILE;
                deferredDiagnosticSource = currentSource;
                deferredDiagnosticArg = currentSource;
            }  else if (deferredDiagnosticKind == DeferredDiagnosticKind.IN_FILE &&
                        !Objects.equals(deferredDiagnosticSource, currentSource)) {
                // warnings in multiple source files
                deferredDiagnosticKind = DeferredDiagnosticKind.IN_FILES;
                deferredDiagnosticArg = null;
            }
        }
        return false;
    }

    /**
     * Build and return any accumulated aggregation notes.
     */
    public List<JCDiagnostic> aggregationNotes() {
        List<JCDiagnostic> list = new ArrayList<>(2);
        if (deferredDiagnosticKind != null) {
            if (deferredDiagnosticArg == null) {
                if (source != null) {
                    addNote(list, deferredDiagnosticSource, deferredDiagnosticKind.getKey(prefix), source);
                } else {
                    addNote(list, deferredDiagnosticSource, deferredDiagnosticKind.getKey(prefix));
                }
            } else {
                if (source != null) {
                    addNote(list, deferredDiagnosticSource, deferredDiagnosticKind.getKey(prefix), deferredDiagnosticArg, source);
                } else {
                    addNote(list, deferredDiagnosticSource, deferredDiagnosticKind.getKey(prefix), deferredDiagnosticArg);
                }
            }
            if (!anyWarningEmitted)
                addNote(list, deferredDiagnosticSource, prefix + ".recompile");
        }
        return list;
    }

    private void addNote(List<JCDiagnostic> list, JavaFileObject file, String msg, Object... args) {
        list.add(log.diags.note(DiagnosticFlag.MANDATORY, log.getSource(file), null, new Note(null, "compiler", msg, args)));
    }

    /**
     * The log to which to report warnings.
     */
    private final Log log;
    private final Source source;

    /**
     * The common prefix for all I18N message keys generated by this handler.
     */
    private final String prefix;

    /**
     * A set containing the names of the source files for which specific
     * warnings have been generated -- i.e. in verbose mode.  If a source name
     * appears in this list, then deferred diagnostics will be phrased to
     * include "additionally"...
     */
    private Set<JavaFileObject> sourcesWithReportedWarnings;

    /**
     * A variable indicating the latest best guess at what the final
     * deferred diagnostic will be. Initially as specific and helpful
     * as possible, as more warnings are reported, the scope of the
     * diagnostic will be broadened.
     */
    private DeferredDiagnosticKind deferredDiagnosticKind;

    /**
     * If deferredDiagnosticKind is IN_FILE or ADDITIONAL_IN_FILE, this variable
     * gives the value of log.currentSource() for the file in question.
     */
    private JavaFileObject deferredDiagnosticSource;

    /**
     * An optional argument to be used when constructing the
     * deferred diagnostic message, based on deferredDiagnosticKind.
     * This variable should normally be set/updated whenever
     * deferredDiagnosticKind is updated.
     */
    private Object deferredDiagnosticArg;

    /**
     * Whether we have actually emitted a warning or just deferred everything.
     * In the latter case, the "recompile" notice is included in the summary.
     */
    private boolean anyWarningEmitted;

    /**
     * A LintCategory to be included in point-of-use diagnostics to indicate
     * how messages might be suppressed (i.e. with @SuppressWarnings).
     */
    private final LintCategory lintCategory;

    public void clear() {
        sourcesWithReportedWarnings = null;
        deferredDiagnosticKind = null;
        deferredDiagnosticSource = null;
        deferredDiagnosticArg = null;
    }
}
