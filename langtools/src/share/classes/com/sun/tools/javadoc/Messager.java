/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javadoc;

import java.io.PrintWriter;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

import com.sun.javadoc.*;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticType;
import com.sun.tools.javac.util.JavacMessages;
import com.sun.tools.javac.util.Log;

/**
 * Utility for integrating with javadoc tools and for localization.
 * Handle Resources. Access to error and warning counts.
 * Message formatting.
 * <br>
 * Also provides implementation for DocErrorReporter.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @see java.util.ResourceBundle
 * @see java.text.MessageFormat
 * @author Neal Gafter (rewrite)
 */
public class Messager extends Log implements DocErrorReporter {
    public static final SourcePosition NOPOS = null;

    /** Get the current messager, which is also the compiler log. */
    public static Messager instance0(Context context) {
        Log instance = context.get(logKey);
        if (instance == null || !(instance instanceof Messager))
            throw new InternalError("no messager instance!");
        return (Messager)instance;
    }

    public static void preRegister(Context context,
                                   final String programName) {
        context.put(logKey, new Context.Factory<Log>() {
            public Log make(Context c) {
                return new Messager(c,
                                    programName);
            }
        });
    }
    public static void preRegister(Context context,
                                   final String programName,
                                   final PrintWriter errWriter,
                                   final PrintWriter warnWriter,
                                   final PrintWriter noticeWriter) {
        context.put(logKey, new Context.Factory<Log>() {
            public Log make(Context c) {
                return new Messager(c,
                                    programName,
                                    errWriter,
                                    warnWriter,
                                    noticeWriter);
            }
        });
    }

    public class ExitJavadoc extends Error {
        private static final long serialVersionUID = 0;
    }

    final String programName;

    private Locale locale;
    private final JavacMessages messages;
    private final JCDiagnostic.Factory javadocDiags;

    /** The default writer for diagnostics
     */
    static final PrintWriter defaultErrWriter = new PrintWriter(System.err);
    static final PrintWriter defaultWarnWriter = new PrintWriter(System.err);
    static final PrintWriter defaultNoticeWriter = new PrintWriter(System.out);

    /**
     * Constructor
     * @param programName  Name of the program (for error messages).
     */
    protected Messager(Context context, String programName) {
        this(context, programName, defaultErrWriter, defaultWarnWriter, defaultNoticeWriter);
    }

    /**
     * Constructor
     * @param programName  Name of the program (for error messages).
     * @param errWriter    Stream for error messages
     * @param warnWriter   Stream for warnings
     * @param noticeWriter Stream for other messages
     */
    @SuppressWarnings("deprecation")
    protected Messager(Context context,
                       String programName,
                       PrintWriter errWriter,
                       PrintWriter warnWriter,
                       PrintWriter noticeWriter) {
        super(context, errWriter, warnWriter, noticeWriter);
        messages = JavacMessages.instance(context);
        messages.add("com.sun.tools.javadoc.resources.javadoc");
        javadocDiags = new JCDiagnostic.Factory(messages, "javadoc");
        this.programName = programName;
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

    /**
     * Print error message, increment error count.
     * Part of DocErrorReporter.
     *
     * @param msg message to print
     */
    public void printError(String msg) {
        printError(null, msg);
    }

    /**
     * Print error message, increment error count.
     * Part of DocErrorReporter.
     *
     * @param pos the position where the error occurs
     * @param msg message to print
     */
    public void printError(SourcePosition pos, String msg) {
        if (diagListener != null) {
            report(DiagnosticType.ERROR, pos, msg);
            return;
        }

        if (nerrors < MaxErrors) {
            String prefix = (pos == null) ? programName : pos.toString();
            errWriter.println(prefix + ": " + getText("javadoc.error") + " - " + msg);
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
        printWarning(null, msg);
    }

    /**
     * Print warning message, increment warning count.
     * Part of DocErrorReporter.
     *
     * @param pos the position where the error occurs
     * @param msg message to print
     */
    public void printWarning(SourcePosition pos, String msg) {
        if (diagListener != null) {
            report(DiagnosticType.WARNING, pos, msg);
            return;
        }

        if (nwarnings < MaxWarnings) {
            String prefix = (pos == null) ? programName : pos.toString();
            warnWriter.println(prefix +  ": " + getText("javadoc.warning") +" - " + msg);
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
        printNotice(null, msg);
    }

    /**
     * Print a message.
     * Part of DocErrorReporter.
     *
     * @param pos the position where the error occurs
     * @param msg message to print
     */
    public void printNotice(SourcePosition pos, String msg) {
        if (diagListener != null) {
            report(DiagnosticType.NOTE, pos, msg);
            return;
        }

        if (pos == null)
            noticeWriter.println(msg);
        else
            noticeWriter.println(pos + ": " + msg);
        noticeWriter.flush();
    }

    /**
     * Print error message, increment error count.
     *
     * @param key selects message from resource
     */
    public void error(SourcePosition pos, String key, Object... args) {
        printError(pos, getText(key, args));
    }

    /**
     * Print warning message, increment warning count.
     *
     * @param key selects message from resource
     */
    public void warning(SourcePosition pos, String key, Object... args) {
        printWarning(pos, getText(key, args));
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
     * Return total number of errors, including those recorded
     * in the compilation log.
     */
    public int nerrors() { return nerrors; }

    /**
     * Return total number of warnings, including those recorded
     * in the compilation log.
     */
    public int nwarnings() { return nwarnings; }

    /**
     * Print exit message.
     */
    public void exitNotice() {
        if (nerrors > 0) {
            notice((nerrors > 1) ? "main.errors" : "main.error",
                   "" + nerrors);
        }
        if (nwarnings > 0) {
            notice((nwarnings > 1) ?  "main.warnings" : "main.warning",
                   "" + nwarnings);
        }
    }

    /**
     * Force program exit, e.g., from a fatal error.
     * <p>
     * TODO: This method does not really belong here.
     */
    public void exit() {
        throw new ExitJavadoc();
    }

    private void report(DiagnosticType type, SourcePosition pos, String msg) {
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
