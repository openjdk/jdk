/*
 * Copyright (c) 1997, 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.tool;


import java.io.PrintWriter;
import java.util.Locale;
import java.util.ResourceBundle;

import javax.lang.model.element.Element;
import javax.tools.Diagnostic.Kind;

import com.sun.tools.javac.util.Context.Factory;
import jdk.javadoc.doclet.Reporter;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.DocSourcePositions;
import com.sun.source.util.DocTreePath;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticType;
import com.sun.tools.javac.util.JavacMessages;
import com.sun.tools.javac.util.Log;

/**
 * Utility for integrating with javadoc tools and for localization.
 * Handle resources, access to error and warning counts and
 * message formatting.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @see java.util.ResourceBundle
 * @see java.text.MessageFormat
 */
public class Messager extends Log implements Reporter {
    final Context context;

    /** Get the current messager, which is also the compiler log. */
    public static Messager instance0(Context context) {
        Log instance = context.get(logKey);
        if (!(instance instanceof Messager m))
            throw new InternalError("no messager instance!");
        return m;
    }

    public static void preRegister(Context context,
                                   final String programName) {
        context.put(logKey, (Factory<Log>)c -> new Messager(c, programName));
    }

    public static void preRegister(Context context, final String programName,
            final PrintWriter outWriter, final PrintWriter errWriter) {
        context.put(logKey, (Factory<Log>)c -> new Messager(c, programName, outWriter, errWriter));
    }

    @Override
    public void print(Kind kind, String msg) {
        switch (kind) {
            case ERROR:
                printError(msg);
                return;
            case WARNING:
            case MANDATORY_WARNING:
                printWarning(msg);
                return;
            default:
                printNotice(msg);
                return;
        }
    }

    @Override
    public void print(Kind kind, DocTreePath path, String msg) {
        switch (kind) {
            case ERROR:
                printError(path, msg);
                return;
            case WARNING:
            case MANDATORY_WARNING:
                printWarning(path, msg);
                return;
            default:
                printWarning(path, msg);
                return;
        }
    }

    @Override
    public void print(Kind kind, Element e, String msg) {
                switch (kind) {
            case ERROR:
                printError(e, msg);
                return;
            case WARNING:
            case MANDATORY_WARNING:
                printWarning(e, msg);
                return;
            case NOTE:
                printNotice(e, msg);
                return;
            default:
                throw new IllegalArgumentException(String.format("unexpected option %s", kind));
        }
    }

    final String programName;

    private Locale locale;
    private final JavacMessages messages;
    private final JCDiagnostic.Factory javadocDiags;

    /** The default writer for diagnostics
     */
    static final PrintWriter defaultOutWriter = new PrintWriter(System.out);
    static final PrintWriter defaultErrWriter = new PrintWriter(System.err);

    /**
     * Constructor
     * @param programName  Name of the program (for error messages).
     */
    public Messager(Context context, String programName) {
        this(context, programName, defaultOutWriter, defaultErrWriter);
    }

    /**
     * Constructor
     * @param programName  Name of the program (for error messages).
     * @param outWriter    Stream for notices etc.
     * @param errWriter    Stream for errors and warnings
     */
    @SuppressWarnings("deprecation")
    public Messager(Context context, String programName, PrintWriter outWriter, PrintWriter errWriter) {
        super(context, errWriter, errWriter, outWriter);
        messages = JavacMessages.instance(context);
        messages.add(locale -> ResourceBundle.getBundle("jdk.javadoc.internal.tool.resources.javadoc",
                                                         locale));
        javadocDiags = new JCDiagnostic.Factory(messages, "javadoc");
        this.programName = programName;
        this.context = context;
        locale = Locale.getDefault();
    }

    public void setLocale(Locale locale) {
        this.locale = locale;
    }

    /**
     * get and format message string from resource
     *
     * @param key selects message from resource
     * @param args arguments for the message
     */
    String getText(String key, Object... args) {
        return messages.getLocalizedString(locale, key, args);
    }

    private String getDiagSource(DocTreePath path) {
        if (path == null || path.getTreePath() == null) {
            return programName;
        }
        JavacTrees trees = JavacTrees.instance(context);
        DocSourcePositions sourcePositions = trees.getSourcePositions();
        CompilationUnitTree cu = path.getTreePath().getCompilationUnit();
        long spos = sourcePositions.getStartPosition(cu, path.getDocComment(), path.getLeaf());
        long lineNumber = cu.getLineMap().getLineNumber(spos);
        String fname = cu.getSourceFile().getName();
        String posString = fname + ":" + lineNumber;
        return posString;
    }

    private String getDiagSource(Element e) {
        if (e == null) {
            return programName;
        }
        JavacTrees trees = JavacTrees.instance(context);
        TreePath path = trees.getPath(e);
        if (path == null) {
            return programName;
        }
        DocSourcePositions sourcePositions = trees.getSourcePositions();
        JCTree tree = trees.getTree(e);
        CompilationUnitTree cu = path.getCompilationUnit();
        long spos = sourcePositions.getStartPosition(cu, tree);
        long lineNumber = cu.getLineMap().getLineNumber(spos);
        String fname = cu.getSourceFile().getName();
        String posString = fname + ":" + lineNumber;
        return posString;
    }

    /**
     * Print error message, increment error count.
     * Part of DocErrorReporter.
     *
     * @param msg message to print
     */
    public void printError(String msg) {
        printError((DocTreePath)null, msg);
    }

    public void printError(DocTreePath path, String msg) {
        String prefix = getDiagSource(path);
        if (diagListener != null) {
            report(DiagnosticType.ERROR, prefix, msg);
            return;
        }
        printError(prefix, msg);
    }

    public void printError(Element e, String msg) {
        String prefix = getDiagSource(e);
        if (diagListener != null) {
            report(DiagnosticType.ERROR, prefix, msg);
            return;
        }
        printError(prefix, msg);
    }

    public void printErrorUsingKey(String key, Object... args) {
        printError((Element)null, getText(key, args));
    }

    // print the error and increment count
    private void printError(String prefix, String msg) {
        if (nerrors < MaxErrors) {
            PrintWriter errWriter = getWriter(WriterKind.ERROR);
            printRawLines(errWriter, prefix + ": " + getText("javadoc.error") + " - " + msg);
            errWriter.flush();
            prompt();
            nerrors++;
        }
    }

    /**
     * Print warning message, increment warning count.
     * Part of DocErrorReporter.
     *
     * @param msg message to print
     */
    public void printWarning(String msg) {
        printWarning((DocTreePath)null, msg);
    }

    public void printWarningUsingKey(String key, Object... args) {
        printWarning((Element)null, getText(key, args));
    }

    public void printWarning(Element e, String key, Object... args) {
        printWarning(getText(key, args));
    }

    public void printWarning(DocTreePath path, String msg) {
        String prefix = getDiagSource(path);
        if (diagListener != null) {
            report(DiagnosticType.WARNING, prefix, msg);
            return;
        }
        printWarning(prefix, msg);
    }

    public void printWarning(Element e, String msg) {
        String prefix = getDiagSource(e);
        if (diagListener != null) {
            report(DiagnosticType.WARNING, prefix, msg);
            return;
        }
        printWarning(prefix, msg);
    }

    // print the warning and increment count
    private void printWarning(String prefix, String msg) {
        if (nwarnings < MaxWarnings) {
            PrintWriter warnWriter = getWriter(WriterKind.WARNING);
            printRawLines(warnWriter, prefix + ": " + getText("javadoc.warning") + " - " + msg);
            warnWriter.flush();
            nwarnings++;
        }
    }

    /**
     * Print a message.
     * Part of DocErrorReporter.
     *
     * @param msg message to print
     */
    public void printNotice(String msg) {
        printNotice((DocTreePath)null, msg);
    }

    public void printNotice(DocTreePath path, String msg) {
        String prefix = getDiagSource(path);
        if (diagListener != null) {
            report(DiagnosticType.NOTE, null, prefix + ": " + msg);
            return;
        }

        PrintWriter noticeWriter = getWriter(WriterKind.NOTICE);
        if (path == null) {
            printRawLines(noticeWriter, msg);
        } else {
            printRawLines(noticeWriter, prefix + ": " + msg);
        }
        noticeWriter.flush();
    }

    public void printNotice(Element e, String msg) {
        String pos = getDiagSource(e);
        if (diagListener != null) {
            report(DiagnosticType.NOTE, pos, msg);
            return;
        }

        PrintWriter noticeWriter = getWriter(WriterKind.NOTICE);
        if (e == null) {
            printRawLines(noticeWriter, msg);
        } else {
            printRawLines(noticeWriter, pos + ": " + msg);
        }
        noticeWriter.flush();
    }

    /**
     * Print a message.
     *
     * @param key selects message from resource
     */
    public void notice(String key, Object... args) {
        printNotice(getText(key, args));
    }

    /**
     * Returns true if errors have been recorded.
     */
    public boolean hasErrors() {
        return nerrors != 0;
    }

    /**
     * Returns true if warnings have been recorded.
     */
    public boolean hasWarnings() {
        return nwarnings != 0;
    }

    /**
     * Print exit message.
     */
    public void printErrorWarningCounts() {
        if (nerrors > 0) {
            notice((nerrors > 1) ? "main.errors" : "main.error",
                   "" + nerrors);
        }
        if (nwarnings > 0) {
            notice((nwarnings > 1) ?  "main.warnings" : "main.warning",
                   "" + nwarnings);
        }
    }

    private void report(DiagnosticType type, String pos, String msg) {
        switch (type) {
            case ERROR:
            case WARNING:
                Object prefix = (pos == null) ? programName : pos;
                report(javadocDiags.create(type, null, null, "msg", prefix, msg));
                break;

            case NOTE:
                String key = (pos == null) ? "msg" : "pos.msg";
                report(javadocDiags.create(type, null, null, key, pos, msg));
                break;

            default:
                throw new IllegalArgumentException(type.toString());
        }
    }
}
