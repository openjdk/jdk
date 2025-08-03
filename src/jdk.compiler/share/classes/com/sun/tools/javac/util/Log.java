/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;

import com.sun.tools.javac.api.DiagnosticFormatter;
import com.sun.tools.javac.code.Lint;
import com.sun.tools.javac.code.Lint.LintCategory;
import com.sun.tools.javac.code.Source;
import com.sun.tools.javac.main.Main;
import com.sun.tools.javac.main.Option;
import com.sun.tools.javac.tree.EndPosTable;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticFlag;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticInfo;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticType;

import static com.sun.tools.javac.main.Option.*;
import static com.sun.tools.javac.util.JCDiagnostic.DiagnosticFlag.*;

/** A class for error logs. Reports errors and warnings, and
 *  keeps track of error numbers and positions.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Log extends AbstractLog {
    /** The context key for the log. */
    public static final Context.Key<Log> logKey = new Context.Key<>();

    /** The context key for the standard output PrintWriter. */
    public static final Context.Key<PrintWriter> outKey = new Context.Key<>();

    /** The context key for the diagnostic PrintWriter. */
    public static final Context.Key<PrintWriter> errKey = new Context.Key<>();

    /* TODO: Should unify this with prefix handling in JCDiagnostic.Factory. */
    public enum PrefixKind {
        JAVAC("javac."),
        COMPILER_MISC("compiler.misc.");
        PrefixKind(String v) {
            value = v;
        }
        public String key(String k) {
            return value + k;
        }
        final String value;
    }

    /**
     * DiagnosticHandler's provide the initial handling for diagnostics.
     * When a diagnostic handler is created and has been initialized, it
     * will install itself as the current diagnostic handler. When a
     * client has finished using a handler, the client should call
     * {@code log.removeDiagnosticHandler();}
     *
     * Note that javax.tools.DiagnosticListener (if set) is called later in the
     * diagnostic pipeline.
     */
    public abstract class DiagnosticHandler {
        /**
         * The previously installed diagnostic handler.
         */
        protected final DiagnosticHandler prev;

        /**
         * Install this diagnostic handler as the current one,
         * recording the previous one.
         */
        protected DiagnosticHandler() {
            prev = diagnosticHandler;
            diagnosticHandler = this;
        }

        /**
         * Handle a diagnostic.
         */
        public abstract void report(JCDiagnostic diag);
    }

    /**
     * A DiagnosticHandler that discards all diagnostics.
     */
    public class DiscardDiagnosticHandler extends DiagnosticHandler {

        @Override
        public void report(JCDiagnostic diag) { }
    }

    /**
     * A DiagnosticHandler that can defer some or all diagnostics,
     * by buffering them for later examination and/or reporting.
     * If a diagnostic is not deferred, or is subsequently reported
     * with reportAllDiagnostics(), it will be reported to the previously
     * active diagnostic handler.
     */
    public class DeferredDiagnosticHandler extends DiagnosticHandler {
        private List<JCDiagnostic> deferred = new ArrayList<>();
        private final Predicate<JCDiagnostic> filter;
        private final boolean passOnNonDeferrable;

        public DeferredDiagnosticHandler() {
            this(null);
        }

        public DeferredDiagnosticHandler(Predicate<JCDiagnostic> filter) {
            this(filter, true);
        }

        public DeferredDiagnosticHandler(Predicate<JCDiagnostic> filter, boolean passOnNonDeferrable) {
            this.filter = Optional.ofNullable(filter).orElse(d -> true);
            this.passOnNonDeferrable = passOnNonDeferrable;
        }

        private boolean deferrable(JCDiagnostic diag) {
            return !(diag.isFlagSet(NON_DEFERRABLE) && passOnNonDeferrable) && filter.test(diag);
        }

        @Override
        public void report(JCDiagnostic diag) {
            if (deferrable(diag)) {
                deferred.add(diag);
            } else {
                prev.report(diag);
            }
        }

        public List<JCDiagnostic> getDiagnostics() {
            return deferred;
        }

        /** Report all deferred diagnostics. */
        public void reportDeferredDiagnostics() {
            reportDeferredDiagnostics(d -> true);
        }

        /** Report selected deferred diagnostics. */
        public void reportDeferredDiagnostics(Predicate<JCDiagnostic> accepter) {

            // Flush matching reports to the previous handler
            deferred.stream()
              .filter(accepter)
              .forEach(prev::report);
            deferred = null; // prevent accidental ongoing use
        }

        /** Report all deferred diagnostics in the specified order. */
        public void reportDeferredDiagnostics(Comparator<JCDiagnostic> order) {
            deferred.sort(order);   // ok to sort in place: reportDeferredDiagnostics() is going to discard it
            reportDeferredDiagnostics();
        }
    }

    public enum WriterKind { NOTICE, WARNING, ERROR, STDOUT, STDERR }

    private final Map<WriterKind, PrintWriter> writers;

    /** The maximum number of errors/warnings that are reported.
     */
    protected int MaxErrors;
    protected int MaxWarnings;

    /** Switch: prompt user on each error.
     */
    public boolean promptOnError;

    /** Switch: emit warning messages.
     */
    public boolean emitWarnings;

    /** Switch: suppress note messages.
     */
    public boolean suppressNotes;

    /** Print stack trace on errors?
     */
    public boolean dumpOnError;

    /**
     * Diagnostic listener, if provided through programmatic
     * interface to javac (JSR 199).
     */
    protected DiagnosticListener<? super JavaFileObject> diagListener;

    /**
     * Formatter for diagnostics.
     */
    private DiagnosticFormatter<JCDiagnostic> diagFormatter;

    /**
     * Keys for expected diagnostics.
     */
    public Set<String> expectDiagKeys;

    /**
     * Set to true if a compressed diagnostic is reported
     */
    public boolean compressedOutput;

    /**
     * JavacMessages object used for localization.
     */
    private JavacMessages messages;

    /**
     * The compilation context.
     */
    private final Context context;

    /**
     * The root {@link Lint} singleton.
     */
    private Lint rootLint;

    /**
     * Handler for initial dispatch of diagnostics.
     */
    private DiagnosticHandler diagnosticHandler;

    /** Get the Log instance for this context. */
    public static Log instance(Context context) {
        Log instance = context.get(logKey);
        if (instance == null)
            instance = new Log(context);
        return instance;
    }

    /**
     * Register a Context.Factory to create a Log.
     */
    public static void preRegister(Context context, PrintWriter w) {
        context.put(Log.class, (Context.Factory<Log>) (c -> new Log(c, w)));
    }

    /**
     * Construct a log with default settings.
     * If no streams are set in the context, the log will be initialized to use
     * System.out for normal output, and System.err for all diagnostic output.
     * If one stream is set in the context, with either Log.outKey or Log.errKey,
     * it will be used for all output.
     * Otherwise, the log will be initialized to use both streams found in the context.
     */
    @SuppressWarnings("this-escape")
    protected Log(Context context) {
        this(context, initWriters(context));
    }

    /**
     * Initialize a map of writers based on values found in the context
     * @param context the context in which to find writers to use
     * @return a map of writers
     */
    private static Map<WriterKind, PrintWriter> initWriters(Context context) {
        PrintWriter out = context.get(outKey);
        PrintWriter err = context.get(errKey);
        if (out == null && err == null) {
            out = new PrintWriter(System.out, true);
            err = new PrintWriter(System.err, true);
            return initWriters(out, err);
        } else if (out == null || err == null) {
            PrintWriter pw = (out != null) ? out : err;
            return initWriters(pw, pw);
        } else {
            return initWriters(out, err);
        }
    }

    /**
     * Construct a log with all output sent to a single output stream.
     */
    @SuppressWarnings("this-escape")
    protected Log(Context context, PrintWriter writer) {
        this(context, initWriters(writer, writer));
    }

    /**
     * Construct a log.
     * The log will be initialized to use stdOut for normal output, and stdErr
     * for all diagnostic output.
     */
    @SuppressWarnings("this-escape")
    protected Log(Context context, PrintWriter out, PrintWriter err) {
        this(context, initWriters(out, err));
    }

    /**
     * Initialize a writer map for a stream for normal output, and a stream for diagnostics.
     * @param out a stream to be used for normal output
     * @param err a stream to be used for diagnostic messages, such as errors, warnings, etc
     * @return a map of writers
     */
    private static Map<WriterKind, PrintWriter> initWriters(PrintWriter out, PrintWriter err) {
        Map<WriterKind, PrintWriter> writers = new EnumMap<>(WriterKind.class);
        writers.put(WriterKind.ERROR, err);
        writers.put(WriterKind.WARNING, err);
        writers.put(WriterKind.NOTICE, err);

        writers.put(WriterKind.STDOUT, out);
        writers.put(WriterKind.STDERR, err);

        return writers;
    }

    /**
     * Creates a log.
     * @param context the context in which the log should be registered
     * @param writers a map of writers that can be accessed by the kind of writer required
     */
    private Log(Context context, Map<WriterKind, PrintWriter> writers) {
        super(JCDiagnostic.Factory.instance(context));
        context.put(logKey, this);
        this.context = context;
        this.writers = writers;

        @SuppressWarnings("unchecked") // FIXME
        DiagnosticListener<? super JavaFileObject> dl =
            context.get(DiagnosticListener.class);
        this.diagListener = dl;

        diagnosticHandler = new DefaultDiagnosticHandler();

        messages = JavacMessages.instance(context);
        messages.add(Main.javacBundleName);

        // Initialize fields configured by Options that we may need before it is ready
        this.emitWarnings = true;
        this.MaxErrors = getDefaultMaxErrors();
        this.MaxWarnings = getDefaultMaxWarnings();
        this.diagFormatter = new BasicDiagnosticFormatter(messages);

        // Once Options is ready, complete the initialization
        final Options options = Options.instance(context);
        options.whenReady(this::initOptions);
    }
    // where
        private void initOptions(Options options) {
            this.dumpOnError = options.isSet(DOE);
            this.promptOnError = options.isSet(PROMPT);
            this.emitWarnings = options.isUnset(NOWARN);
            this.suppressNotes = options.isSet("suppressNotes");
            this.MaxErrors = getIntOption(options, XMAXERRS, getDefaultMaxErrors());
            this.MaxWarnings = getIntOption(options, XMAXWARNS, getDefaultMaxWarnings());

            boolean rawDiagnostics = options.isSet("rawDiagnostics");
            this.diagFormatter = rawDiagnostics ? new RawDiagnosticFormatter(options) :
                                                  new BasicDiagnosticFormatter(options, messages);

            String ek = options.get("expectKeys");
            if (ek != null)
                expectDiagKeys = new HashSet<>(Arrays.asList(ek.split(", *")));
        }

        private int getIntOption(Options options, Option option, int defaultValue) {
            String s = options.get(option);
            try {
                if (s != null) {
                    int n = Integer.parseInt(s);
                    return (n <= 0 ? Integer.MAX_VALUE : n);
                }
            } catch (NumberFormatException e) {
                // silently ignore ill-formed numbers
            }
            return defaultValue;
        }

        /** Default value for -Xmaxerrs.
         */
        protected int getDefaultMaxErrors() {
            return 100;
        }

        /** Default value for -Xmaxwarns.
         */
        protected int getDefaultMaxWarnings() {
            return 100;
        }

    /** The number of errors encountered so far.
     */
    public int nerrors = 0;

    /** The number of warnings encountered so far.
     */
    public int nwarnings = 0;

    /** The number of errors encountered after MaxErrors was reached.
     */
    public int nsuppressederrors = 0;

    /** The number of warnings encountered after MaxWarnings was reached.
     */
    public int nsuppressedwarns = 0;

    /** A set of all errors generated so far. This is used to avoid printing an
     *  error message more than once. For each error, a pair consisting of the
     *  source file name and source code position of the error is added to the set.
     */
    protected Set<Pair<JavaFileObject, Integer>> recorded = new HashSet<>();

    /** A set of "not-supported-in-source-X" errors produced so far. This is used to only generate
     *  one such error per file.
     */
    protected Set<Pair<JavaFileObject, List<String>>>  recordedSourceLevelErrors = new HashSet<>();

    public boolean hasDiagnosticListener() {
        return diagListener != null;
    }

    public void setEndPosTable(JavaFileObject name, EndPosTable endPosTable) {
        Assert.checkNonNull(name);
        getSource(name).setEndPosTable(endPosTable);
    }

    /** Return current sourcefile.
     */
    public JavaFileObject currentSourceFile() {
        return source == null ? null : source.getFile();
    }

    /** Get the current diagnostic formatter.
     */
    public DiagnosticFormatter<JCDiagnostic> getDiagnosticFormatter() {
        return diagFormatter;
    }

    /** Set the current diagnostic formatter.
     */
    public void setDiagnosticFormatter(DiagnosticFormatter<JCDiagnostic> diagFormatter) {
        this.diagFormatter = diagFormatter;
    }

    public PrintWriter getWriter(WriterKind kind) {
        return writers.get(kind);
    }

    public void setWriter(WriterKind kind, PrintWriter pw) {
        Assert.checkNonNull(pw);
        writers.put(kind, pw);
    }

    public void setWriters(PrintWriter pw) {
        Assert.checkNonNull(pw);
        for (WriterKind k: WriterKind.values())
            writers.put(k, pw);
    }

    /**
     * Replace the specified diagnostic handler with the
     * handler that was current at the time this handler was created.
     * The given handler must be the currently installed handler;
     * it must be specified explicitly for clarity and consistency checking.
     */
    public void popDiagnosticHandler(DiagnosticHandler h) {
        Assert.check(diagnosticHandler == h);
        Assert.check(h.prev != null);
        diagnosticHandler = h.prev;
    }

    /** Flush the logs
     */
    public void flush() {
        for (PrintWriter pw: writers.values()) {
            pw.flush();
        }
    }

    public void flush(WriterKind kind) {
        getWriter(kind).flush();
    }

    /** Returns true if an error needs to be reported for a given
     * source name and pos.
     */
    protected boolean shouldReport(JavaFileObject file, int pos) {
        if (file == null)
            return true;

        Pair<JavaFileObject,Integer> coords = new Pair<>(file, pos);
        boolean shouldReport = !recorded.contains(coords);
        if (shouldReport)
            recorded.add(coords);
        return shouldReport;
    }

    /** Returns true if a diagnostics needs to be reported.
     */
    private boolean shouldReport(JCDiagnostic d) {
        JavaFileObject file = d.getSource();

        if (file == null)
            return true;

        if (!shouldReport(file, d.getIntPosition()))
            return false;

        if (!d.isFlagSet(SOURCE_LEVEL))
            return true;

        Pair<JavaFileObject, List<String>> coords = new Pair<>(file, getCode(d));
        boolean shouldReport = !recordedSourceLevelErrors.contains(coords);
        if (shouldReport)
            recordedSourceLevelErrors.add(coords);
        return shouldReport;
    }

    //where
        private List<String> getCode(JCDiagnostic d) {
            ListBuffer<String> buf = new ListBuffer<>();
            getCodeRecursive(buf, d);
            return buf.toList();
        }

        private void getCodeRecursive(ListBuffer<String> buf, JCDiagnostic d) {
            buf.add(d.getCode());
            for (Object o : d.getArgs()) {
                if (o instanceof JCDiagnostic diagnostic) {
                    getCodeRecursive(buf, diagnostic);
                }
            }
        }

    /**Is an error reported at the given pos (inside the current source)?*/
    public boolean hasErrorOn(DiagnosticPosition pos) {
        JavaFileObject file = source != null ? source.fileObject : null;

        return file != null && recorded.contains(new Pair<>(file, pos.getPreferredPosition()));
    }

    /** Prompt user after an error.
     */
    public void prompt() {
        if (promptOnError) {
            System.err.println(localize("resume.abort"));
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
        String line = (source == null ? null : source.getLine(pos));
        if (line == null)
            return;
        int col = source.getColumnNumber(pos, false);

        printRawLines(writer, line);
        for (int i = 0; i < col - 1; i++) {
            writer.print((line.charAt(i) == '\t') ? "\t" : " ");
        }
        writer.println("^");
        writer.flush();
    }

    public void printNewline() {
        PrintWriter noticeWriter = writers.get(WriterKind.NOTICE);
        noticeWriter.println();
    }

    public void printNewline(WriterKind wk) {
        getWriter(wk).println();
    }

    public void printLines(String key, Object... args) {
        PrintWriter noticeWriter = writers.get(WriterKind.NOTICE);
        printRawLines(noticeWriter, localize(key, args));
    }

    public void printLines(DiagnosticInfo diag) {
        PrintWriter noticeWriter = writers.get(WriterKind.NOTICE);
        printRawLines(noticeWriter, localize(diag));
    }

    public void printLines(PrefixKind pk, String key, Object... args) {
        PrintWriter noticeWriter = writers.get(WriterKind.NOTICE);
        printRawLines(noticeWriter, localize(pk, key, args));
    }

    public void printLines(WriterKind wk, String key, Object... args) {
        printRawLines(getWriter(wk), localize(key, args));
    }

    public void printLines(WriterKind wk, PrefixKind pk, String key, Object... args) {
        printRawLines(getWriter(wk), localize(pk, key, args));
    }

    /** Print the text of a message, translating newlines appropriately
     *  for the platform.
     */
    public void printRawLines(String msg) {
        PrintWriter noticeWriter = writers.get(WriterKind.NOTICE);
        printRawLines(noticeWriter, msg);
    }

    /** Print the text of a message, translating newlines appropriately
     *  for the platform.
     */
    public void printRawLines(WriterKind kind, String msg) {
        printRawLines(getWriter(kind), msg);
    }

    /** Print the text of a message, translating newlines appropriately
     *  for the platform.
     */
    public static void printRawLines(PrintWriter writer, String msg) {
        int nl;
        while ((nl = msg.indexOf('\n')) != -1) {
            writer.println(msg.substring(0, nl));
            msg = msg.substring(nl+1);
        }
        if (msg.length() != 0) writer.println(msg);
    }

    /**
     * Print the localized text of a "verbose" message to the
     * noticeWriter stream.
     */
    public void printVerbose(String key, Object... args) {
        PrintWriter noticeWriter = writers.get(WriterKind.NOTICE);
        printRawLines(noticeWriter, localize("verbose." + key, args));
    }

    @Override
    protected void directError(String key, Object... args) {
        PrintWriter errWriter = writers.get(WriterKind.ERROR);
        printRawLines(errWriter, localize(key, args));
        errWriter.flush();
    }

    /**
     * Primary method to report a diagnostic.
     * @param diagnostic
     */
    @Override
    public void report(JCDiagnostic diagnostic) {
        diagnosticHandler.report(diagnostic);
    }

    // Obtain root Lint singleton lazily to avoid init loops
    private Lint rootLint() {
        if (rootLint == null)
            rootLint = Lint.instance(context);
        return rootLint;
    }

// Mandatory Warnings

    private final EnumMap<LintCategory, WarningAggregator> aggregators = new EnumMap<>(LintCategory.class);

    private final EnumSet<LintCategory> suppressedDeferredMandatory = EnumSet.noneOf(LintCategory.class);

    /**
     * Suppress aggregated mandatory warning notes for the specified category.
     */
    public void suppressAggregatedWarningNotes(LintCategory category) {
        suppressedDeferredMandatory.add(category);
    }

    /**
     * Report any remaining unreported aggregated mandatory warning notes.
     */
    public void reportOutstandingNotes() {
        aggregators.entrySet().stream()
          .filter(entry -> !suppressedDeferredMandatory.contains(entry.getKey()))
          .map(Map.Entry::getValue)
          .map(WarningAggregator::aggregationNotes)
          .flatMap(List::stream)
          .forEach(this::report);
        aggregators.clear();
    }

    private WarningAggregator aggregatorFor(LintCategory lc) {
        return switch (lc) {
        case PREVIEW -> aggregators.computeIfAbsent(lc, c -> new WarningAggregator(this, Source.instance(context), c));
        case DEPRECATION -> aggregators.computeIfAbsent(lc, c -> new WarningAggregator(this, null, c, "deprecated"));
        default -> aggregators.computeIfAbsent(lc, c -> new WarningAggregator(this, null, c));
        };
    }

    /**
     * Reset the state of this instance.
     */
    public void clear() {
        recorded.clear();
        sourceMap.clear();
        nerrors = 0;
        nwarnings = 0;
        nsuppressederrors = 0;
        nsuppressedwarns = 0;
        while (diagnosticHandler.prev != null)
            popDiagnosticHandler(diagnosticHandler);
        aggregators.clear();
        suppressedDeferredMandatory.clear();
    }

// DefaultDiagnosticHandler

    /**
     * Common diagnostic handling.
     * The diagnostic is counted, and depending on the options and how many diagnostics have been
     * reported so far, the diagnostic may be handed off to writeDiagnostic.
     */
    private class DefaultDiagnosticHandler extends DiagnosticHandler {

        @Override
        public void report(JCDiagnostic diagnostic) {
            if (expectDiagKeys != null)
                expectDiagKeys.remove(diagnostic.getCode());

            if (diagnostic.hasRewriter()) {
                JCDiagnostic rewrittenDiag = diagnostic.rewrite();
                diagnostic = rewrittenDiag != null ? rewrittenDiag : diagnostic;
            }

            switch (diagnostic.getType()) {
            case FRAGMENT:
                throw new IllegalArgumentException();

            case NOTE:
                // Print out notes only when we are permitted to report warnings
                // Notes are only generated at the end of a compilation, so should be small
                // in number.
                if ((emitWarnings || diagnostic.isMandatory()) && !suppressNotes) {
                    writeDiagnostic(diagnostic);
                }
                break;

            case WARNING:

                // Apply the appropriate mandatory warning aggregator, if needed
                if (diagnostic.isFlagSet(AGGREGATE)) {
                    LintCategory category = diagnostic.getLintCategory();
                    boolean verbose = rootLint().isEnabled(category);
                    if (!aggregatorFor(category).aggregate(diagnostic, verbose))
                        return;
                }

                // Strict warnings are always emitted
                if (diagnostic.isFlagSet(DiagnosticFlag.STRICT)) {
                    writeDiagnostic(diagnostic);
                    nwarnings++;
                    return;
                }

                // Emit other warning unless not mandatory and warnings are disabled
                if (emitWarnings || diagnostic.isMandatory()) {
                    if (nwarnings < MaxWarnings) {
                        writeDiagnostic(diagnostic);
                        nwarnings++;
                    } else {
                        nsuppressedwarns++;
                    }
                }
                break;

            case ERROR:
                if (diagnostic.isFlagSet(API) || shouldReport(diagnostic)) {
                    if (nerrors < MaxErrors) {
                        writeDiagnostic(diagnostic);
                        nerrors++;
                    } else {
                        nsuppressederrors++;
                    }
                }
                break;
            }
            if (diagnostic.isFlagSet(COMPRESSED)) {
                compressedOutput = true;
            }
        }
    }

    /**
     * Write out a diagnostic.
     */
    protected void writeDiagnostic(JCDiagnostic diag) {
        if (diagListener != null) {
            diagListener.report(diag);
            return;
        }

        PrintWriter writer = getWriterForDiagnosticType(diag.getType());

        printRawLines(writer, diagFormatter.format(diag, messages.getCurrentLocale()));

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

    protected PrintWriter getWriterForDiagnosticType(DiagnosticType dt) {
        switch (dt) {
        case FRAGMENT:
            throw new IllegalArgumentException();

        case NOTE:
            return writers.get(WriterKind.NOTICE);

        case WARNING:
            return writers.get(WriterKind.WARNING);

        case ERROR:
            return writers.get(WriterKind.ERROR);

        default:
            throw new Error();
        }
    }

    /** Find a localized string in the resource bundle.
     *  Because this method is static, it ignores the locale.
     *  Use localize(key, args) when possible.
     *  @param key    The key for the localized string.
     *  @param args   Fields to substitute into the string.
     */
    public static String getLocalizedString(String key, Object ... args) {
        return JavacMessages.getDefaultLocalizedString(PrefixKind.COMPILER_MISC.key(key), args);
    }

    /** Find a localized string in the resource bundle.
     *  @param key    The key for the localized string.
     *  @param args   Fields to substitute into the string.
     */
    public String localize(String key, Object... args) {
        return localize(PrefixKind.COMPILER_MISC, key, args);
    }

    public String localize(JCDiagnostic.DiagnosticInfo diagInfo) {
        if (useRawMessages) {
            return diagInfo.key();
        } else {
            return messages.getLocalizedString(diagInfo);
        }
    }

    /** Find a localized string in the resource bundle.
     *  @param key    The key for the localized string.
     *  @param args   Fields to substitute into the string.
     */
    public String localize(PrefixKind pk, String key, Object... args) {
        if (useRawMessages)
            return pk.key(key);
        else
            return messages.getLocalizedString(pk.key(key), args);
    }
    // where
        // backdoor hook for testing, should transition to use -XDrawDiagnostics
        private static boolean useRawMessages = false;

/* *************************************************************************
 * raw error messages without internationalization; used for experimentation
 * and quick prototyping
 ***************************************************************************/

    /** print an error or warning message:
     */
    private void printRawDiag(PrintWriter pw, String prefix, int pos, String msg) {
        if (source == null || pos == Position.NOPOS) {
            printRawLines(pw, prefix + msg);
        } else {
            int line = source.getLineNumber(pos);
            JavaFileObject file = source.getFile();
            if (file != null)
                printRawLines(pw,
                           file.getName() + ":" +
                           line + ": " + msg);
            printErrLine(pos, pw);
        }
        pw.flush();
    }

    /** report an error:
     */
    public void rawError(int pos, String msg) {
        PrintWriter errWriter = writers.get(WriterKind.ERROR);
        if (nerrors < MaxErrors && shouldReport(currentSourceFile(), pos)) {
            printRawDiag(errWriter, "error: ", pos, msg);
            prompt();
            nerrors++;
        } else {
            nsuppressederrors++;
        }
        errWriter.flush();
    }

    /** report a warning:
     */
    public void rawWarning(int pos, String msg) {
        PrintWriter warnWriter = writers.get(WriterKind.ERROR);
        if (emitWarnings) {
            if (nwarnings < MaxWarnings) {
                printRawDiag(warnWriter, "warning: ", pos, msg);
            } else {
                nsuppressedwarns++;
            }
        }
        prompt();
        nwarnings++;
        warnWriter.flush();
    }

    public static String format(String fmt, Object... args) {
        return String.format((java.util.Locale)null, fmt, args);
    }

}
