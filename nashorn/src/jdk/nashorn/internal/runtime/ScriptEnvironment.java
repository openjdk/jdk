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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TimeZone;

import jdk.nashorn.internal.codegen.Namespace;
import jdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor;
import jdk.nashorn.internal.runtime.options.KeyValueOption;
import jdk.nashorn.internal.runtime.options.Option;
import jdk.nashorn.internal.runtime.options.Options;

/**
 * Script environment consists of command line options, arguments, script files
 * and output and error writers, top level Namespace etc.
 */
public final class ScriptEnvironment {
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

    /** Only compile script, do not run it or generate other ScriptObjects */
    public final boolean _compile_only;

    /** Accumulated callsite flags that will be used when bootstrapping script callsites */
    public final int     _callsite_flags;

    /** Generate line number table in class files */
    public final boolean _debug_lines;

    /** Package to which generated class files are added */
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

    /**
     * Behavior when encountering a function declaration in a lexical context where only statements are acceptable
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

    /** Print the AST before lowering */
    public final boolean _print_ast;

    /** Print the AST after lowering */
    public final boolean _print_lower_ast;

    /** Print resulting bytecode for script */
    public final boolean _print_code;

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

    /** range analysis for known types */
    public final boolean _range_analysis;

    /** is this environment in scripting mode? */
    public final boolean _scripting;

    /** is the JIT allowed to specializ calls based on callsite types? */
    public final Set<String> _specialize_calls;

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

    /**
     * Constructor
     *
     * @param options a Options object
     * @param out output print writer
     * @param err error print writer
     */
    public ScriptEnvironment(final Options options, final PrintWriter out, final PrintWriter err) {
        this.out = out;
        this.err = err;
        this.namespace = new Namespace();
        this.options = options;

        _class_cache_size     = options.getInteger("class.cache.size");
        _compile_only         = options.getBoolean("compile.only");
        _debug_lines          = options.getBoolean("debug.lines");
        _dest_dir             = options.getString("d");
        _dump_on_error        = options.getBoolean("doe");
        _early_lvalue_error   = options.getBoolean("early.lvalue.error");
        _empty_statements     = options.getBoolean("empty.statements");
        _fullversion          = options.getBoolean("fullversion");
        if(options.getBoolean("function.statement.error")) {
            _function_statement = FunctionStatementBehavior.ERROR;
        } else if(options.getBoolean("function.statement.warning")) {
            _function_statement = FunctionStatementBehavior.WARNING;
        } else {
            _function_statement = FunctionStatementBehavior.ACCEPT;
        }
        _fx                   = options.getBoolean("fx");
        _lazy_compilation     = options.getBoolean("lazy.compilation");
        _loader_per_compile   = options.getBoolean("loader.per.compile");
        _no_java              = options.getBoolean("no.java");
        _no_syntax_extensions = options.getBoolean("no.syntax.extensions");
        _no_typed_arrays      = options.getBoolean("no.typed.arrays");
        _parse_only           = options.getBoolean("parse.only");
        _print_ast            = options.getBoolean("print.ast");
        _print_lower_ast      = options.getBoolean("print.lower.ast");
        _print_code           = options.getBoolean("print.code");
        _print_mem_usage      = options.getBoolean("print.mem.usage");
        _print_no_newline     = options.getBoolean("print.no.newline");
        _print_parse          = options.getBoolean("print.parse");
        _print_lower_parse    = options.getBoolean("print.lower.parse");
        _print_symbols        = options.getBoolean("print.symbols");
        _range_analysis       = options.getBoolean("range.analysis");
        _scripting            = options.getBoolean("scripting");
        _strict               = options.getBoolean("strict");
        _version              = options.getBoolean("version");
        _verify_code          = options.getBoolean("verify.code");

        final String specialize = options.getString("specialize.calls");
        if (specialize == null) {
            _specialize_calls = null;
        } else {
            _specialize_calls = new HashSet<>();
            final StringTokenizer st = new StringTokenizer(specialize, ",");
            while (st.hasMoreElements()) {
                _specialize_calls.add(st.nextToken());
            }
        }

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
            if (kv.hasValue("scope")) {
                callSiteFlags |= NashornCallSiteDescriptor.CALLSITE_TRACE_SCOPE;
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
    }

    /**
     * Can we specialize a particular method name?
     * @param functionName method name
     * @return true if we are allowed to generate versions of this method
     */
    public boolean canSpecialize(final String functionName) {
        if (_specialize_calls == null) {
            return false;
        }
        return _specialize_calls.isEmpty() || _specialize_calls.contains(functionName);
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
}
