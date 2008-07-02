/*
 * Copyright 1999-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.tools.javac.util;

import java.io.*;
import java.nio.CharBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;

import com.sun.tools.javac.code.Source;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticType;
import com.sun.tools.javac.util.JCDiagnostic.SimpleDiagnosticPosition;

import static com.sun.tools.javac.util.LayoutCharacters.*;

/** A class for error logs. Reports errors and warnings, and
 *  keeps track of error numbers and positions.
 *
 *  <p><b>This is NOT part of any API supported by Sun Microsystems.  If
 *  you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Log {
    /** The context key for the log. */
    public static final Context.Key<Log> logKey
        = new Context.Key<Log>();

    /** The context key for the output PrintWriter. */
    public static final Context.Key<PrintWriter> outKey =
        new Context.Key<PrintWriter>();

    //@Deprecated
    public final PrintWriter errWriter;

    //@Deprecated
    public final PrintWriter warnWriter;

    //@Deprecated
    public final PrintWriter noticeWriter;

    /** The maximum number of errors/warnings that are reported.
     */
    public final int MaxErrors;
    public final int MaxWarnings;

    /** Whether or not to display the line of source containing a diagnostic.
     */
    private final boolean showSourceLine;

    /** Switch: prompt user on each error.
     */
    public boolean promptOnError;

    /** Switch: emit warning messages.
     */
    public boolean emitWarnings;

    /** Enforce mandatory warnings.
     */
    private boolean enforceMandatoryWarnings;

    /** Print stack trace on errors?
     */
    public boolean dumpOnError;

    /** Print multiple errors for same source locations.
     */
    public boolean multipleErrors;

    /**
     * Diagnostic listener, if provided through programmatic
     * interface to javac (JSR 199).
     */
    protected DiagnosticListener<? super JavaFileObject> diagListener;
    /**
     * Formatter for diagnostics
     */
    private DiagnosticFormatter diagFormatter;

    /**
     * Factory for diagnostics
     */
    private JCDiagnostic.Factory diags;


    /** Construct a log with given I/O redirections.
     */
    @Deprecated
    protected Log(Context context, PrintWriter errWriter, PrintWriter warnWriter, PrintWriter noticeWriter) {
        context.put(logKey, this);
        this.errWriter = errWriter;
        this.warnWriter = warnWriter;
        this.noticeWriter = noticeWriter;

        this.diags = JCDiagnostic.Factory.instance(context);

        Options options = Options.instance(context);
        this.dumpOnError = options.get("-doe") != null;
        this.promptOnError = options.get("-prompt") != null;
        this.emitWarnings = options.get("-Xlint:none") == null;
        this.MaxErrors = getIntOption(options, "-Xmaxerrs", 100);
        this.MaxWarnings = getIntOption(options, "-Xmaxwarns", 100);
        this.showSourceLine = options.get("rawDiagnostics") == null;

        this.diagFormatter = DiagnosticFormatter.instance(context);
        @SuppressWarnings("unchecked") // FIXME
        DiagnosticListener<? super JavaFileObject> diagListener =
            context.get(DiagnosticListener.class);
        this.diagListener = diagListener;

        Source source = Source.instance(context);
        this.enforceMandatoryWarnings = source.enforceMandatoryWarnings();
    }
    // where
        private int getIntOption(Options options, String optionName, int defaultValue) {
            String s = options.get(optionName);
            try {
                if (s != null) return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                // silently ignore ill-formed numbers
            }
            return defaultValue;
        }

    /** The default writer for diagnostics
     */
    static final PrintWriter defaultWriter(Context context) {
        PrintWriter result = context.get(outKey);
        if (result == null)
            context.put(outKey, result = new PrintWriter(System.err));
        return result;
    }

    /** Construct a log with default settings.
     */
    protected Log(Context context) {
        this(context, defaultWriter(context));
    }

    /** Construct a log with all output redirected.
     */
    protected Log(Context context, PrintWriter defaultWriter) {
        this(context, defaultWriter, defaultWriter, defaultWriter);
    }

    /** Get the Log instance for this context. */
    public static Log instance(Context context) {
        Log instance = context.get(logKey);
        if (instance == null)
            instance = new Log(context);
        return instance;
    }

    /** The file that's currently translated.
     */
    protected JCDiagnostic.DiagnosticSource source;

    /** The number of errors encountered so far.
     */
    public int nerrors = 0;

    /** The number of warnings encountered so far.
     */
    public int nwarnings = 0;

    /** A set of all errors generated so far. This is used to avoid printing an
     *  error message more than once. For each error, a pair consisting of the
     *  source file name and source code position of the error is added to the set.
     */
    private Set<Pair<JavaFileObject, Integer>> recorded = new HashSet<Pair<JavaFileObject,Integer>>();

    private Map<JavaFileObject, Map<JCTree, Integer>> endPosTables;

    /** The buffer containing the file that's currently translated.
     */
    private char[] buf = null;

    /** The length of useful data in buf
     */
    private int bufLen = 0;

    /** The position in the buffer at which last error was reported
     */
    private int bp;

    /** number of the current source line; first line is 1
     */
    private int line;

    /**  buffer index of the first character of the current source line
     */
    private int lineStart;

    public boolean hasDiagnosticListener() {
        return diagListener != null;
    }

    public void setEndPosTable(JavaFileObject name, Map<JCTree, Integer> table) {
        if (endPosTables == null)
            endPosTables = new HashMap<JavaFileObject, Map<JCTree, Integer>>();
        endPosTables.put(name, table);
    }

    /** Re-assign source, returning previous setting.
     */
    public JavaFileObject useSource(final JavaFileObject name) {
        JavaFileObject prev = currentSource();
        if (name != prev) {
            source = new JCDiagnostic.DiagnosticSource() {
                    public JavaFileObject getFile() {
                        return name;
                    }
                    public CharSequence getName() {
                        return JavacFileManager.getJavacBaseFileName(getFile());
                    }
                    public int getLineNumber(int pos) {
                        return Log.this.getLineNumber(pos);
                    }
                    public int getColumnNumber(int pos) {
                        return Log.this.getColumnNumber(pos);
                    }
                    public Map<JCTree, Integer> getEndPosTable() {
                        return (endPosTables == null ? null : endPosTables.get(name));
                    }
                };
            buf = null;
        }
        return prev;
    }

    /** Re-assign source buffer for existing source name.
     */
    protected void setBuf(char[] newBuf) {
        buf = newBuf;
        bufLen = buf.length;
        bp = 0;
        lineStart = 0;
        line = 1;
    }

    protected char[] getBuf() {
        return buf;
    }

    /** Return current source name.
     */
    public JavaFileObject currentSource() {
        return source == null ? null : source.getFile();
    }

    /** Flush the logs
     */
    public void flush() {
        errWriter.flush();
        warnWriter.flush();
        noticeWriter.flush();
    }

    /** Returns true if an error needs to be reported for a given
     * source name and pos.
     */
    protected boolean shouldReport(JavaFileObject file, int pos) {
        if (multipleErrors || file == null)
            return true;

        Pair<JavaFileObject,Integer> coords = new Pair<JavaFileObject,Integer>(file, pos);
        boolean shouldReport = !recorded.contains(coords);
        if (shouldReport)
            recorded.add(coords);
        return shouldReport;
    }

    /** Prompt user after an error.
     */
    public void prompt() {
        if (promptOnError) {
            System.err.println(getLocalizedString("resume.abort"));
            char ch;
            try {
                while (true) {
                    switch (System.in.read()) {
                    case 'a': case 'A':
                        System.exit(-1);
                        return;
                    case 'r': case 'R':
                        return;
                    case 'x': case 'X':
                        throw new AssertionError("user abort");
                    default:
                    }
                }
            } catch (IOException e) {}
        }
    }

    /** Print the faulty source code line and point to the error.
     *  @param pos   Buffer index of the error position, must be on current line
     */
    private void printErrLine(int pos, PrintWriter writer) {
        if (!findLine(pos))
            return;

        int lineEnd = lineStart;
        while (lineEnd < bufLen && buf[lineEnd] != CR && buf[lineEnd] != LF)
            lineEnd++;
        if (lineEnd - lineStart == 0)
            return;
        printLines(writer, new String(buf, lineStart, lineEnd - lineStart));
        for (bp = lineStart; bp < pos; bp++) {
            writer.print((buf[bp] == '\t') ? "\t" : " ");
        }
        writer.println("^");
        writer.flush();
    }

    protected void initBuf(JavaFileObject fileObject) throws IOException {
        CharSequence cs = fileObject.getCharContent(true);
        if (cs instanceof CharBuffer) {
            CharBuffer cb = (CharBuffer) cs;
            buf = JavacFileManager.toArray(cb);
            bufLen = cb.limit();
        } else {
            buf = cs.toString().toCharArray();
            bufLen = buf.length;
        }
    }

    /** Find the line in the buffer that contains the current position
     * @param pos      Character offset into the buffer
     */
    private boolean findLine(int pos) {
        if (pos == Position.NOPOS || currentSource() == null)
            return false;
        try {
            if (buf == null) {
                initBuf(currentSource());
                lineStart = 0;
                line = 1;
            } else if (lineStart > pos) { // messages don't come in order
                lineStart = 0;
                line = 1;
            }
            bp = lineStart;
            while (bp < bufLen && bp < pos) {
                switch (buf[bp++]) {
                case CR:
                    if (bp < bufLen && buf[bp] == LF) bp++;
                    line++;
                    lineStart = bp;
                    break;
                case LF:
                    line++;
                    lineStart = bp;
                    break;
                }
            }
            return bp <= bufLen;
        } catch (IOException e) {
            //e.printStackTrace();
            // FIXME: include e.getLocalizedMessage() in error message
            printLines(errWriter, getLocalizedString("source.unavailable"));
            errWriter.flush();
            buf = new char[0];
        }
        return false;
    }

    /** Print the text of a message, translating newlines appropriately
     *  for the platform.
     */
    public static void printLines(PrintWriter writer, String msg) {
        int nl;
        while ((nl = msg.indexOf('\n')) != -1) {
            writer.println(msg.substring(0, nl));
            msg = msg.substring(nl+1);
        }
        if (msg.length() != 0) writer.println(msg);
    }

    /** Report an error, unless another error was already reported at same
     *  source position.
     *  @param key    The key for the localized error message.
     *  @param args   Fields of the error message.
     */
    public void error(String key, Object ... args) {
        report(diags.error(source, null, key, args));
    }

    /** Report an error, unless another error was already reported at same
     *  source position.
     *  @param pos    The source position at which to report the error.
     *  @param key    The key for the localized error message.
     *  @param args   Fields of the error message.
     */
    public void error(DiagnosticPosition pos, String key, Object ... args) {
        report(diags.error(source, pos, key, args));
    }

    /** Report an error, unless another error was already reported at same
     *  source position.
     *  @param pos    The source position at which to report the error.
     *  @param key    The key for the localized error message.
     *  @param args   Fields of the error message.
     */
    public void error(int pos, String key, Object ... args) {
        report(diags.error(source, wrap(pos), key, args));
    }

    /** Report a warning, unless suppressed by the  -nowarn option or the
     *  maximum number of warnings has been reached.
     *  @param pos    The source position at which to report the warning.
     *  @param key    The key for the localized warning message.
     *  @param args   Fields of the warning message.
     */
    public void warning(String key, Object ... args) {
        report(diags.warning(source, null, key, args));
    }

    /** Report a warning, unless suppressed by the  -nowarn option or the
     *  maximum number of warnings has been reached.
     *  @param pos    The source position at which to report the warning.
     *  @param key    The key for the localized warning message.
     *  @param args   Fields of the warning message.
     */
    public void warning(DiagnosticPosition pos, String key, Object ... args) {
        report(diags.warning(source, pos, key, args));
    }

    /** Report a warning, unless suppressed by the  -nowarn option or the
     *  maximum number of warnings has been reached.
     *  @param pos    The source position at which to report the warning.
     *  @param key    The key for the localized warning message.
     *  @param args   Fields of the warning message.
     */
    public void warning(int pos, String key, Object ... args) {
        report(diags.warning(source, wrap(pos), key, args));
    }

    /** Report a warning.
     *  @param pos    The source position at which to report the warning.
     *  @param key    The key for the localized warning message.
     *  @param args   Fields of the warning message.
     */
    public void mandatoryWarning(DiagnosticPosition pos, String key, Object ... args) {
        if (enforceMandatoryWarnings)
            report(diags.mandatoryWarning(source, pos, key, args));
        else
            report(diags.warning(source, pos, key, args));
    }

    /** Report a warning that cannot be suppressed.
     *  @param pos    The source position at which to report the warning.
     *  @param key    The key for the localized warning message.
     *  @param args   Fields of the warning message.
     */
    public void strictWarning(DiagnosticPosition pos, String key, Object ... args) {
        writeDiagnostic(diags.warning(source, pos, key, args));
        nwarnings++;
    }

    /** Provide a non-fatal notification, unless suppressed by the -nowarn option.
     *  @param key    The key for the localized notification message.
     *  @param args   Fields of the notification message.
     */
    public void note(String key, Object ... args) {
        report(diags.note(source, null, key, args));
    }

    /** Provide a non-fatal notification, unless suppressed by the -nowarn option.
     *  @param key    The key for the localized notification message.
     *  @param args   Fields of the notification message.
     */
    public void note(DiagnosticPosition pos, String key, Object ... args) {
        report(diags.note(source, pos, key, args));
    }

    /** Provide a non-fatal notification, unless suppressed by the -nowarn option.
     *  @param key    The key for the localized notification message.
     *  @param args   Fields of the notification message.
     */
    public void note(int pos, String key, Object ... args) {
        report(diags.note(source, wrap(pos), key, args));
    }

    /** Provide a non-fatal notification, unless suppressed by the -nowarn option.
     *  @param key    The key for the localized notification message.
     *  @param args   Fields of the notification message.
     */
    public void mandatoryNote(final JavaFileObject file, String key, Object ... args) {
        JCDiagnostic.DiagnosticSource wrapper = null;
        if (file != null) {
            wrapper = new JCDiagnostic.DiagnosticSource() {
                    public JavaFileObject getFile() {
                        return file;
                    }
                    public CharSequence getName() {
                        return JavacFileManager.getJavacBaseFileName(getFile());
                    }
                    public int getLineNumber(int pos) {
                        return Log.this.getLineNumber(pos);
                    }
                    public int getColumnNumber(int pos) {
                        return Log.this.getColumnNumber(pos);
                    }
                    public Map<JCTree, Integer> getEndPosTable() {
                        return (endPosTables == null ? null : endPosTables.get(file));
                    }
                };
        }
        if (enforceMandatoryWarnings)
            report(diags.mandatoryNote(wrapper, key, args));
        else
            report(diags.note(wrapper, null, key, args));
    }

    private DiagnosticPosition wrap(int pos) {
        return (pos == Position.NOPOS ? null : new SimpleDiagnosticPosition(pos));
    }

    /**
     * Common diagnostic handling.
     * The diagnostic is counted, and depending on the options and how many diagnostics have been
     * reported so far, the diagnostic may be handed off to writeDiagnostic.
     */
    public void report(JCDiagnostic diagnostic) {
        switch (diagnostic.getType()) {
        case FRAGMENT:
            throw new IllegalArgumentException();

        case NOTE:
            // Print out notes only when we are permitted to report warnings
            // Notes are only generated at the end of a compilation, so should be small
            // in number.
            if (emitWarnings || diagnostic.isMandatory()) {
                writeDiagnostic(diagnostic);
            }
            break;

        case WARNING:
            if (emitWarnings || diagnostic.isMandatory()) {
                if (nwarnings < MaxWarnings) {
                    writeDiagnostic(diagnostic);
                    nwarnings++;
                }
            }
            break;

        case ERROR:
            if (nerrors < MaxErrors
                && shouldReport(diagnostic.getSource(), diagnostic.getIntPosition())) {
                writeDiagnostic(diagnostic);
                nerrors++;
            }
            break;
        }
    }

    /**
     * Write out a diagnostic.
     */
    protected void writeDiagnostic(JCDiagnostic diag) {
        if (diagListener != null) {
            try {
                diagListener.report(diag);
                return;
            }
            catch (Throwable t) {
                throw new ClientCodeException(t);
            }
        }

        PrintWriter writer = getWriterForDiagnosticType(diag.getType());

        printLines(writer, diagFormatter.format(diag));
        if (showSourceLine) {
            int pos = diag.getIntPosition();
            if (pos != Position.NOPOS) {
                JavaFileObject prev = useSource(diag.getSource());
                printErrLine(pos, writer);
                useSource(prev);
            }
        }

        if (promptOnError) {
            switch (diag.getType()) {
            case ERROR:
            case WARNING:
                prompt();
            }
        }

        if (dumpOnError)
            new RuntimeException().printStackTrace(writer);

        writer.flush();
    }

    @Deprecated
    protected PrintWriter getWriterForDiagnosticType(DiagnosticType dt) {
        switch (dt) {
        case FRAGMENT:
            throw new IllegalArgumentException();

        case NOTE:
            return noticeWriter;

        case WARNING:
            return warnWriter;

        case ERROR:
            return errWriter;

        default:
            throw new Error();
        }
    }

    /** Find a localized string in the resource bundle.
     *  @param key    The key for the localized string.
     *  @param args   Fields to substitute into the string.
     */
    public static String getLocalizedString(String key, Object ... args) {
        return Messages.getDefaultLocalizedString("compiler.misc." + key, args);
    }

/***************************************************************************
 * raw error messages without internationalization; used for experimentation
 * and quick prototyping
 ***************************************************************************/

/** print an error or warning message:
 */
    private void printRawError(int pos, String msg) {
        if (!findLine(pos)) {
            printLines(errWriter, "error: " + msg);
        } else {
            JavaFileObject file = currentSource();
            if (file != null)
                printLines(errWriter,
                           JavacFileManager.getJavacFileName(file) + ":" +
                           line + ": " + msg);
            printErrLine(pos, errWriter);
        }
        errWriter.flush();
    }

/** report an error:
 */
    public void rawError(int pos, String msg) {
        if (nerrors < MaxErrors && shouldReport(currentSource(), pos)) {
            printRawError(pos, msg);
            prompt();
            nerrors++;
        }
        errWriter.flush();
    }

/** report a warning:
 */
    public void rawWarning(int pos, String msg) {
        if (nwarnings < MaxWarnings && emitWarnings) {
            printRawError(pos, "warning: " + msg);
        }
        prompt();
        nwarnings++;
        errWriter.flush();
    }

    /** Return the one-based line number associated with a given pos
     * for the current source file.  Zero is returned if no line exists
     * for the given position.
     */
    protected int getLineNumber(int pos) {
        if (findLine(pos))
            return line;
        return 0;
    }

    /** Return the one-based column number associated with a given pos
     * for the current source file.  Zero is returned if no column exists
     * for the given position.
     */
    protected int getColumnNumber(int pos) {
        if (findLine(pos)) {
            int column = 0;
            for (bp = lineStart; bp < pos; bp++) {
                if (bp >= bufLen)
                    return 0;
                if (buf[bp] == '\t')
                    column = (column / TabInc * TabInc) + TabInc;
                else
                    column++;
            }
            return column + 1; // positions are one-based
        }
        return 0;
    }

    public static String format(String fmt, Object... args) {
        return String.format((java.util.Locale)null, fmt, args);
    }

}
