/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.runtime;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.logging.Level;
import jdk.nashorn.internal.codegen.Namespace;
import jdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor;
import jdk.nashorn.internal.runtime.options.KeyValueOption;
import jdk.nashorn.internal.runtime.options.LoggingOption;
import jdk.nashorn.internal.runtime.options.LoggingOption.LoggerInfo;
import jdk.nashorn.internal.runtime.options.Option;
import jdk.nashorn.internal.runtime.options.Options;

/**
 * Script environment consists of command line options, arguments, script files
 * and output and error writers, top level Namespace etc.
 */
public final class ScriptEnvironment {
    // Primarily intended to be used in test environments so that eager compilation tests work without an
    // error when tested with optimistic compilation.
    private static final boolean ALLOW_EAGER_COMPILATION_SILENT_OVERRIDE = Options.getBooleanProperty(
            "nashorn.options.allowEagerCompilationSilentOverride", false);

    /** Output writer for this environment */
    private final PrintWriter out;

    /** Error writer for this environment */
    private final PrintWriter err;

    /** Top level namespace. */
    private final Namespace namespace;

    /** Current Options object. */
    private final Options options;

    /** Size of the per-global Class cache size */
    public final int     _class_cache_size;

    /** -classpath value. */
    public final String  _classpath;

    /** Only compile script, do not run it or generate other ScriptObjects */
    public final boolean _compile_only;

    /** Accept "const" keyword and treat it as variable. Interim feature */
    public final boolean _const_as_var;

    /** Accumulated callsite flags that will be used when bootstrapping script callsites */
    public final int     _callsite_flags;

    /** Generate line number table in class files */
    public final boolean _debug_lines;

    /** Directory in which source files and generated class files are dumped */
    public final String  _dest_dir;

    /** Display stack trace upon error, default is false */
    public final boolean _dump_on_error;

    /** Invalid lvalue expressions should be reported as early errors */
    public final boolean _early_lvalue_error;

    /** Empty statements should be preserved in the AST */
    public final boolean _empty_statements;

    /** Show full Nashorn version */
    public final boolean _fullversion;

    /** Launch using as fx application */
    public final boolean _fx;

    /** Use single Global instance per jsr223 engine instance. */
    public final boolean _global_per_engine;

    /** Enable experimental ECMAScript 6 features. */
    public final boolean _es6;


    /** Number of times a dynamic call site has to be relinked before it is
     * considered unstable (and thus should be linked as if it were megamorphic).
     */
    public final int _unstable_relink_threshold;

    /** Argument passed to compile only if optimistic compilation should take place */
    public static final String COMPILE_ONLY_OPTIMISTIC_ARG = "optimistic";

    /**
     *  Behavior when encountering a function declaration in a lexical context where only statements are acceptable
     * (function declarations are source elements, but not statements).
     */
    public enum FunctionStatementBehavior {
        /**
         * Accept the function declaration silently and treat it as if it were a function expression assigned to a local
         * variable.
         */
        ACCEPT,
        /**
         * Log a parser warning, but accept the function declaration and treat it as if it were a function expression
         * assigned to a local variable.
         */
        WARNING,
        /**
         * Raise a {@code SyntaxError}.
         */
        ERROR
    }

    /**
     * Behavior when encountering a function declaration in a lexical context where only statements are acceptable
     * (function declarations are source elements, but not statements).
     */
    public final FunctionStatementBehavior _function_statement;

    /** Should lazy compilation take place */
    public final boolean _lazy_compilation;

    /** Should optimistic types be used */
    public final boolean _optimistic_types;

    /** Create a new class loaded for each compilation */
    public final boolean _loader_per_compile;

    /** Do not support Java support extensions. */
    public final boolean _no_java;

    /** Do not support non-standard syntax extensions. */
    public final boolean _no_syntax_extensions;

    /** Do not support typed arrays. */
    public final boolean _no_typed_arrays;

    /** Only parse the source code, do not compile */
    public final boolean _parse_only;

    /** Enable disk cache for compiled scripts */
    public final boolean _persistent_cache;

    /** Print the AST before lowering */
    public final boolean _print_ast;

    /** Print the AST after lowering */
    public final boolean _print_lower_ast;

    /** Print resulting bytecode for script */
    public final boolean _print_code;

    /** Directory (optional) to print files to */
    public final String _print_code_dir;

    /** List of functions to write to the print code dir, optional */
    public final String _print_code_func;

    /** Print memory usage for IR after each phase */
    public final boolean _print_mem_usage;

    /** Print function will no print newline characters */
    public final boolean _print_no_newline;

    /** Print AST in more human readable form */
    public final boolean _print_parse;

    /** Print AST in more human readable form after Lowering */
    public final boolean _print_lower_parse;

    /** print symbols and their contents for the script */
    public final boolean _print_symbols;

    /** is this environment in scripting mode? */
    public final boolean _scripting;

    /** is this environment in strict mode? */
    public final boolean _strict;

    /** print version info of Nashorn */
    public final boolean _version;

    /** should code verification be done of generated bytecode */
    public final boolean _verify_code;

    /** time zone for this environment */
    public final TimeZone _timezone;

    /** Local for error messages */
    public final Locale _locale;

    /** Logging */
    public final Map<String, LoggerInfo> _loggers;

    /** Timing */
    public final Timing _timing;

    /** Whether to use anonymous classes. See {@link #useAnonymousClasses(boolean)}. */
    private final AnonymousClasses _anonymousClasses;
    private enum AnonymousClasses {
        AUTO,
        OFF,
        ON
    }

    /**
     * Constructor
     *
     * @param options a Options object
     * @param out output print writer
     * @param err error print writer
     */
    @SuppressWarnings("unused")
    public ScriptEnvironment(final Options options, final PrintWriter out, final PrintWriter err) {
        this.out = out;
        this.err = err;
        this.namespace = new Namespace();
        this.options = options;

        _class_cache_size     = options.getInteger("class.cache.size");
        _classpath            = options.getString("classpath");
        _compile_only         = options.getBoolean("compile.only");
        _const_as_var         = options.getBoolean("const.as.var");
        _debug_lines          = options.getBoolean("debug.lines");
        _dest_dir             = options.getString("d");
        _dump_on_error        = options.getBoolean("doe");
        _early_lvalue_error   = options.getBoolean("early.lvalue.error");
        _empty_statements     = options.getBoolean("empty.statements");
        _fullversion          = options.getBoolean("fullversion");
        if (options.getBoolean("function.statement.error")) {
            _function_statement = FunctionStatementBehavior.ERROR;
        } else if (options.getBoolean("function.statement.warning")) {
            _function_statement = FunctionStatementBehavior.WARNING;
        } else {
            _function_statement = FunctionStatementBehavior.ACCEPT;
        }
        _fx                   = options.getBoolean("fx");
        _global_per_engine    = options.getBoolean("global.per.engine");
        _optimistic_types     = options.getBoolean("optimistic.types");
        final boolean lazy_compilation = options.getBoolean("lazy.compilation");
        if (!lazy_compilation && _optimistic_types) {
            if (!ALLOW_EAGER_COMPILATION_SILENT_OVERRIDE) {
                throw new IllegalStateException(
                        ECMAErrors.getMessage(
                                "config.error.eagerCompilationConflictsWithOptimisticTypes",
                                options.getOptionTemplateByKey("lazy.compilation").getName(),
                                options.getOptionTemplateByKey("optimistic.types").getName()));
            }
            _lazy_compilation = true;
        } else {
            _lazy_compilation = lazy_compilation;
        }
        _loader_per_compile   = options.getBoolean("loader.per.compile");
        _no_java              = options.getBoolean("no.java");
        _no_syntax_extensions = options.getBoolean("no.syntax.extensions");
        _no_typed_arrays      = options.getBoolean("no.typed.arrays");
        _parse_only           = options.getBoolean("parse.only");
        _persistent_cache     = options.getBoolean("persistent.code.cache");
        _print_ast            = options.getBoolean("print.ast");
        _print_lower_ast      = options.getBoolean("print.lower.ast");
        _print_code           = options.getString("print.code") != null;
        _print_mem_usage      = options.getBoolean("print.mem.usage");
        _print_no_newline     = options.getBoolean("print.no.newline");
        _print_parse          = options.getBoolean("print.parse");
        _print_lower_parse    = options.getBoolean("print.lower.parse");
        _print_symbols        = options.getBoolean("print.symbols");
        _scripting            = options.getBoolean("scripting");
        _strict               = options.getBoolean("strict");
        _version              = options.getBoolean("version");
        _verify_code          = options.getBoolean("verify.code");

        final int configuredUrt = options.getInteger("unstable.relink.threshold");
        // The default for this property is -1, so we can easily detect when
        // it is not specified on command line.
        if (configuredUrt < 0) {
            // In this case, use a default of 8, or 16 for optimistic types.
            // Optimistic types come with dual fields, and in order to get
            // performance on benchmarks with a lot of object instantiation and
            // then field reassignment, it can take slightly more relinks to
            // become stable with type changes swapping out an entire property
            // map and making a map guard fail. Also, honor the "nashorn.*"
            // system property for now. It was documented in DEVELOPER_README
            // so we should recognize it for the time being.
            _unstable_relink_threshold = Options.getIntProperty(
                    "nashorn.unstable.relink.threshold",
                    _optimistic_types ? 16 : 8);
        } else {
            _unstable_relink_threshold = configuredUrt;
        }

        final String anonClasses = options.getString("anonymous.classes");
        if (anonClasses == null || anonClasses.equals("auto")) {
            _anonymousClasses = AnonymousClasses.AUTO;
        } else if (anonClasses.equals("true")) {
            _anonymousClasses = AnonymousClasses.ON;
        } else if (anonClasses.equals("false")) {
            _anonymousClasses = AnonymousClasses.OFF;
        } else {
            throw new RuntimeException("Unsupported value for anonymous classes: " + anonClasses);
        }


        final String language = options.getString("language");
        if (language == null || language.equals("es5")) {
            _es6 = false;
        } else if (language.equals("es6")) {
            _es6 = true;
        } else {
            throw new RuntimeException("Unsupported language: " + language);
        }

        String dir = null;
        String func = null;
        final String pc = options.getString("print.code");
        if (pc != null) {
            final StringTokenizer st = new StringTokenizer(pc, ",");
            while (st.hasMoreTokens()) {
                final StringTokenizer st2 = new StringTokenizer(st.nextToken(), ":");
                while (st2.hasMoreTokens()) {
                    final String cmd = st2.nextToken();
                    if ("dir".equals(cmd)) {
                        dir = st2.nextToken();
                    } else if ("function".equals(cmd)) {
                        func = st2.nextToken();
                    }
                }
            }
        }
        _print_code_dir = dir;
        _print_code_func = func;

        int callSiteFlags = 0;
        if (options.getBoolean("profile.callsites")) {
            callSiteFlags |= NashornCallSiteDescriptor.CALLSITE_PROFILE;
        }

        if (options.get("trace.callsites") instanceof KeyValueOption) {
            callSiteFlags |= NashornCallSiteDescriptor.CALLSITE_TRACE;
            final KeyValueOption kv = (KeyValueOption)options.get("trace.callsites");
            if (kv.hasValue("miss")) {
                callSiteFlags |= NashornCallSiteDescriptor.CALLSITE_TRACE_MISSES;
            }
            if (kv.hasValue("enterexit") || (callSiteFlags & NashornCallSiteDescriptor.CALLSITE_TRACE_MISSES) == 0) {
                callSiteFlags |= NashornCallSiteDescriptor.CALLSITE_TRACE_ENTEREXIT;
            }
            if (kv.hasValue("objects")) {
                callSiteFlags |= NashornCallSiteDescriptor.CALLSITE_TRACE_VALUES;
            }
        }
        this._callsite_flags = callSiteFlags;

        final Option<?> timezoneOption = options.get("timezone");
        if (timezoneOption != null) {
            this._timezone = (TimeZone)timezoneOption.getValue();
        } else {
            this._timezone  = TimeZone.getDefault();
        }

        final Option<?> localeOption = options.get("locale");
        if (localeOption != null) {
            this._locale = (Locale)localeOption.getValue();
        } else {
            this._locale = Locale.getDefault();
        }

        final LoggingOption loggingOption = (LoggingOption)options.get("log");
        this._loggers = loggingOption == null ? new HashMap<String, LoggerInfo>() : loggingOption.getLoggers();

        final LoggerInfo timeLoggerInfo = _loggers.get(Timing.getLoggerName());
        this._timing = new Timing(timeLoggerInfo != null && timeLoggerInfo.getLevel() != Level.OFF);
    }

    /**
     * Get the output stream for this environment
     * @return output print writer
     */
    public PrintWriter getOut() {
        return out;
    }

    /**
     * Get the error stream for this environment
     * @return error print writer
     */
    public PrintWriter getErr() {
        return err;
    }

    /**
     * Get the namespace for this environment
     * @return namespace
     */
    public Namespace getNamespace() {
        return namespace;
    }

    /**
     * Return the JavaScript files passed to the program
     *
     * @return a list of files
     */
    public List<String> getFiles() {
        return options.getFiles();
    }

    /**
     * Return the user arguments to the program, i.e. those trailing "--" after
     * the filename
     *
     * @return a list of user arguments
     */
    public List<String> getArguments() {
        return options.getArguments();
    }

    /**
     * Check if there is a logger registered for a particular name: typically
     * the "name" attribute of a Loggable annotation on a class
     *
     * @param name logger name
     * @return true, if a logger exists for that name, false otherwise
     */
    public boolean hasLogger(final String name) {
        return _loggers.get(name) != null;
    }

    /**
     * Check if compilation/runtime timings are enabled
     * @return true if enabled
     */
    public boolean isTimingEnabled() {
        return _timing != null ? _timing.isEnabled() : false;
    }

    /**
     * Returns true if compilation should use anonymous classes.
     * @param isEval true if compilation is an eval call.
     * @return true if anonymous classes should be used
     */
    public boolean useAnonymousClasses(final boolean isEval) {
        return _anonymousClasses == AnonymousClasses.ON || (_anonymousClasses == AnonymousClasses.AUTO && isEval);
    }

}
