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

import static jdk.nashorn.internal.codegen.CompilerConstants.RUN_SCRIPT;
import static jdk.nashorn.internal.codegen.CompilerConstants.STRICT_MODE;
import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;
import static jdk.nashorn.internal.runtime.linker.Lookup.MH;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.AccessController;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.PrivilegedAction;
import java.util.Locale;
import java.util.TimeZone;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.util.CheckClassAdapter;
import jdk.nashorn.internal.codegen.ClassEmitter;
import jdk.nashorn.internal.codegen.Compiler;
import jdk.nashorn.internal.codegen.Namespace;
import jdk.nashorn.internal.codegen.objects.ObjectClassGenerator;
import jdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor;
import jdk.nashorn.internal.runtime.options.KeyValueOption;
import jdk.nashorn.internal.runtime.options.Option;
import jdk.nashorn.internal.runtime.options.Options;

/**
 * This class manages the global state of execution. Context is immutable.
 */
public final class Context {

    /** Is Context global debug mode enabled ? */
    public static final boolean DEBUG = Options.getBooleanProperty("nashorn.debug");

    private static final ThreadLocal<ScriptObject> currentGlobal =
        new ThreadLocal<ScriptObject>() {
            @Override
            protected ScriptObject initialValue() {
                 return null;
            }
        };

    /**
     * Get the error stream if applicable and initialized, otherwise stderr
     * Usually this is the error stream given the context, but for testing and
     * certain bootstrapping situations we need a default stream
     */

    /**
     * Return the current global scope
     * @return current global scope
     */
    public static ScriptObject getGlobal() {
        return currentGlobal.get();
    }

    /**
     * Set the current global scope
     * @param global the global scope
     */
    public static void setGlobal(final ScriptObject global) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new RuntimePermission("setNashornGlobal"));
        }

        if (global != null && !(global instanceof GlobalObject)) {
            throw new IllegalArgumentException("global does not implement GlobalObject!");
        }

        currentGlobal.set(global);
    }

    /**
     * Get context of the current global
     * @return current global scope's context.
     */
    public static Context getContext() {
        return Context.getGlobal().getContext();
    }

    /**
     * Output text to this Context's error stream
     * @param str text to write
     */
    public static void err(final String str) {
        err(str, true);
    }

    /**
     * Output text to this Context's error stream, optionally with
     * a newline afterwards
     *
     * @param str  text to write
     * @param crlf write a carriage return/new line after text
     */
    @SuppressWarnings("resource")
    public static void err(final String str, final boolean crlf) {
        final PrintWriter err = Context.getContext().getErr();
        if (err != null) {
            if (crlf) {
                err.println(str);
            } else {
                err.print(str);
            }
        }
    }

    /** Class loader to load classes from -classpath option, if set. */
    private final ClassLoader  classPathLoader;

    /** Class loader to load classes compiled from scripts. */
    private final ScriptLoader scriptLoader;

    /** Top level namespace. */
    private final Namespace namespace;

    /** Current options. */
    private final Options options;

    /** Current error manager. */
    private final ErrorManager errors;

    /** Output writer for this context */
    private final PrintWriter out;

    /** Error writer for this context */
    private final PrintWriter err;

    /** Local for error messages */
    private final Locale locale;

    /** Empty map used for seed map for JO$ objects */
    final PropertyMap emptyMap = PropertyMap.newEmptyMap(this);

    // cache fields for "well known" options.
    // see jdk.nashorn.internal.runtime.Resources

    /** Always allow functions as statements */
    public final boolean _anon_functions;

    /** Size of the per-global Class cache size */
    public final int     _class_cache_size;

    /** Only compile script, do not run it or generate other ScriptObjects */
    public final boolean _compile_only;

    /** Accumulated callsite flags that will be used when boostrapping script callsites */
    public final int     _callsite_flags;

    /** Genereate line number table in class files */
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

    /** Create a new class loaded for each compilation */
    public final boolean _loader_per_compile;

    /** Package to which generated class files are added */
    public final String  _package;

    /** Only parse the source code, do not compile */
    public final boolean _parse_only;

    /** Print the AST before lowering */
    public final boolean _print_ast;

    /** Print the AST after lowering */
    public final boolean _print_lower_ast;

    /** Print resulting bytecode for script */
    public final boolean _print_code;

    /** Print function will no print newline characters */
    public final boolean _print_no_newline;

    /** Print AST in more human readable form */
    public final boolean _print_parse;

    /** Print AST in more human readable form after Lowering */
    public final boolean _print_lower_parse;

    /** print symbols and their contents for the script */
    public final boolean _print_symbols;

    /** is this context in scripting mode? */
    public final boolean _scripting;

    /** is this context in strict mode? */
    public final boolean _strict;

    /** print version info of Nashorn */
    public final boolean _version;

    /** should code verification be done of generated bytecode */
    public final boolean _verify_code;

    /** time zone for this context */
    public final TimeZone _timezone;

    private static final StructureLoader sharedLoader;

    static {
        sharedLoader = AccessController.doPrivileged(new PrivilegedAction<StructureLoader>() {
            @Override
            public StructureLoader run() {
                return new StructureLoader(Context.class.getClassLoader(), null);
            }
        });
    }

    /**
     * ThrowErrorManager that throws ParserException upon error conditions.
     */
    public static class ThrowErrorManager extends ErrorManager {
        @Override
        public void error(final String message) {
            throw new ParserException(message);
        }

        @Override
        public void error(final ParserException e) {
            throw e;
        }
    }

    /**
     * Constructor
     *
     * @param options options from command line or Context creator
     * @param errors  error manger
     */
    public Context(final Options options, final ErrorManager errors) {
        this(options, errors, new PrintWriter(System.out, true), new PrintWriter(System.err, true));
    }

    /**
     * Constructor
     *
     * @param options options from command line or Context creator
     * @param errors  error manger
     * @param out     output writer for this Context
     * @param err     error writer for this Context
     */
    public Context(final Options options, final ErrorManager errors, final PrintWriter out, final PrintWriter err) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new RuntimePermission("createNashornContext"));
        }

        this.scriptLoader = (ScriptLoader)AccessController.doPrivileged(
             new PrivilegedAction<ClassLoader>() {
                @Override
                public ClassLoader run() {
                    final ClassLoader structureLoader = new StructureLoader(sharedLoader, Context.this);
                    return new ScriptLoader(structureLoader, Context.this);
                }
             });

        this.namespace = new Namespace();
        this.options   = options;
        this.errors    = errors;
        this.locale    = Locale.getDefault();
        this.out       = out;
        this.err       = err;

        _anon_functions     = options.getBoolean("anon.functions");
        _class_cache_size   = options.getInteger("class.cache.size");
        _compile_only       = options.getBoolean("compile.only");
        _debug_lines        = options.getBoolean("debug.lines");
        _dest_dir           = options.getString("d");
        _dump_on_error      = options.getBoolean("doe");
        _early_lvalue_error = options.getBoolean("early.lvalue.error");
        _empty_statements   = options.getBoolean("empty.statements");
        _fullversion        = options.getBoolean("fullversion");
        _loader_per_compile = options.getBoolean("loader.per.compile");
        _package            = options.getString("package");
        _parse_only         = options.getBoolean("parse.only");
        _print_ast          = options.getBoolean("print.ast");
        _print_lower_ast    = options.getBoolean("print.lower.ast");
        _print_code         = options.getBoolean("print.code");
        _print_no_newline   = options.getBoolean("print.no.newline");
        _print_parse        = options.getBoolean("print.parse");
        _print_lower_parse  = options.getBoolean("print.lower.parse");
        _print_symbols      = options.getBoolean("print.symbols");
        _scripting          = options.getBoolean("scripting");
        _strict             = options.getBoolean("strict");
        _version            = options.getBoolean("version");
        _verify_code        = options.getBoolean("verify.code");

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

        final Option<?> option = options.get("timezone");
        if (option != null) {
            this._timezone = (TimeZone)option.getValue();
        } else {
            this._timezone  = TimeZone.getDefault();
        }

        // if user passed -classpath option, make a class loader with that and set it as
        // thread context class loader so that script can access classes from that path.
        final String classPath = options.getString("classpath");
        if (! _compile_only && classPath != null && !classPath.isEmpty()) {
            // make sure that caller can create a class loader.
            if (sm != null) {
                sm.checkPermission(new RuntimePermission("createClassLoader"));
            }
            this.classPathLoader = NashornLoader.createClassLoader(classPath);
        } else {
            this.classPathLoader = null;
        }

        // print version info if asked.
        if (_version) {
            getErr().println("nashorn " + Version.version());
        }

        if (_fullversion) {
            getErr().println("nashorn full version " + Version.fullVersion());
        }
    }

    /**
     * Get the error manager for this context
     * @return error manger
     */
    public ErrorManager getErrors() {
        return errors;
    }

    /**
     * Get the output stream for this context
     * @return output print writer
     */
    public PrintWriter getOut() {
        return out;
    }

    /**
     * Get the error stream for this context
     * @return error print writer
     */
    public PrintWriter getErr() {
        return err;
    }

    /**
     * Get the namespace for this context
     * @return namespace
     */
    public Namespace getNamespace() {
        return namespace;
    }

    /**
     * Get the options given to this context
     * @return options
     */
    public Options getOptions() {
        return options;
    }

    /**
     * Get the locale for this context
     * @return locale
     */
    public Locale getLocale() {
        return locale;
    }

    /**
     * Get the time zone for this context
     * @return time zone
     */
    public TimeZone getTimeZone() {
        return _timezone;
    }

    /**
     * Compile a top level script.
     *
     * @param source the source
     * @param scope  the scope
     * @param strict are we in strict mode
     *
     * @return top level function for script
     */
    public ScriptFunction compileScript(final Source source, final ScriptObject scope, final boolean strict) {
        return compileScript(source, scope, this.errors, strict);
    }

    /**
     * Compile a top level script - no Source given, but an URL to
     * load it from
     *
     * @param name    name of script/source
     * @param url     URL to source
     * @param scope   the scope
     * @param strict  are we in strict mode
     *
     * @return top level function for the script
     *
     * @throws IOException if URL cannot be resolved
     */
    public ScriptFunction compileScript(final String name, final URL url, final ScriptObject scope, final boolean strict) throws IOException {
        return compileScript(name, url, scope, this.errors, strict);
    }

    /**
     * Entry point for {@code eval}
     *
     * @param initialScope The scope of this eval call
     * @param string       Evaluated code as a String
     * @param callThis     "this" to be passed to the evaluated code
     * @param location     location of the eval call
     * @param strict       is this {@code eval} call from a strict mode code?
     *
     * @return the return value of the {@code eval}
     */
    public Object eval(final ScriptObject initialScope, final String string, final Object callThis, final Object location, final boolean strict) {
        final String  file       = (location == UNDEFINED || location == null) ? "<eval>" : location.toString();
        final Source  source     = new Source(file, string);
        final boolean directEval = location != UNDEFINED; // is this direct 'eval' call or indirectly invoked eval?
        final ScriptObject global = Context.getGlobal();

        ScriptObject scope = initialScope;

        // ECMA section 10.1.1 point 2 says eval code is strict if it begins
        // with "use strict" directive or eval direct call itself is made
        // from from strict mode code. We are passed with caller's strict mode.
        boolean strictFlag = directEval && strict;

        Class<?> clazz = null;
        try {
            clazz = compile(source, new ThrowErrorManager(), strictFlag);
        } catch (final ParserException e) {
            e.throwAsEcmaException(global);
            return null;
        }

        if (!strictFlag) {
            // We need to get strict mode flag from compiled class. This is
            // because eval code may start with "use strict" directive.
            try {
                strictFlag = clazz.getField(STRICT_MODE.tag()).getBoolean(null);
            } catch (final NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
                //ignored
                strictFlag = false;
            }
        }

        // In strict mode, eval does not instantiate variables and functions
        // in the caller's environment. A new environment is created!
        if (strictFlag) {
            // Create a new scope object
            final ScriptObject strictEvalScope = ((GlobalObject)global).newObject();

            // bless it as a "scope"
            strictEvalScope.setIsScope();

            // set given scope to be it's proto so that eval can still
            // access caller environment vars in the new environment.
            strictEvalScope.setProto(scope);
            scope = strictEvalScope;
        }

        ScriptFunction func = getRunScriptFunction(clazz, scope);
        Object evalThis;
        if (directEval) {
            evalThis = (callThis instanceof ScriptObject || strictFlag) ? callThis : global;
        } else {
            evalThis = global;
        }

        return ScriptRuntime.apply(func, evalThis);
    }

    /**
     * Implementation of {@code load} Nashorn extension. Load a script file from a source
     * expression
     *
     * @param scope  the scope
     * @param source source expression for script
     *
     * @return return value for load call (undefined)
     *
     * @throws IOException if source cannot be found or loaded
     */
    public Object load(final ScriptObject scope, final Object source) throws IOException {
        Object src = source;
        URL url = null;
        String srcName = null;

        if (src instanceof ConsString) {
            src = src.toString();
        }
        if (src instanceof String) {
            srcName = (String)src;
            final File file = new File((String)src);
            if (file.isFile()) {
                url = file.toURI().toURL();
            } else if (srcName.indexOf(':') != -1) {
                try {
                    url = new URL((String)src);
                } catch (final MalformedURLException e) {
                    // fallback URL - nashorn:foo.js - check under jdk/nashorn/internal/runtime/resources
                    String str = (String)src;
                    if (str.startsWith("nashorn:")) {
                        str = "resources/" + str.substring("nashorn:".length());
                        url = Context.class.getResource(str);
                        if (url == null) {
                            throw e;
                        }
                    } else {
                        throw e;
                    }
                }
            }
            src = url;
        }

        if (src instanceof File && ((File)src).isFile()) {
            final File file = (File)src;
            url = file.toURI().toURL();
            if (srcName == null) {
                srcName = file.getCanonicalPath();
            }
        } else if (src instanceof URL) {
            url = (URL)src;
            if (srcName == null) {
                srcName = url.toString();
            }
        }

        if (url != null) {
            assert srcName != null : "srcName null here!";
            return evaluateSource(srcName, url, scope, scope);
        } else if (src instanceof ScriptObject) {
            final ScriptObject sobj = (ScriptObject)src;
            if (sobj.has("script") && sobj.has("name")) {
                final String script = JSType.toString(sobj.get("script"));
                final String name   = JSType.toString(sobj.get("name"));
                return evaluateSource(new Source(name, script), scope, scope);
            }
        }

        typeError(Context.getGlobal(), "cant.load.script", ScriptRuntime.safeToString(source));

        return UNDEFINED;
    }

    /**
     * Load or get a structure class. Structure class names are based on the number of parameter fields
     * and {@link AccessorProperty} fields in them. Structure classes are used to represent ScriptObjects
     *
     * @see ObjectClassGenerator
     * @see AccessorProperty
     * @see ScriptObject
     *
     * @param fullName  full name of class, e.g. jdk.nashorn.internal.objects.JO$2P1 contains 2 fields and 1 parameter.
     *
     * @return the Class<?> for this structure
     *
     * @throws ClassNotFoundException if structure class cannot be resolved
     */
    public static Class<?> forStructureClass(final String fullName) throws ClassNotFoundException {
        return Class.forName(fullName, true, sharedLoader);
    }

    /**
     * Lookup a Java class. This is used for JSR-223 stuff linking in from
     * {@link jdk.nashorn.internal.objects.NativeJava} and {@link jdk.nashorn.internal.runtime.NativeJavaPackage}
     *
     * @param fullName full name of class to load
     *
     * @return the Class<?> for the name
     *
     * @throws ClassNotFoundException if class cannot be resolved
     */
    public Class<?> findClass(final String fullName) throws ClassNotFoundException {
        // check package access as soon as possible!
        final int index = fullName.lastIndexOf('.');
        if (index != -1) {
            final SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                sm.checkPackageAccess(fullName.substring(0, index));
            }
        }

        // try script loader first
        try {
            return Class.forName(fullName, true, scriptLoader);
        } catch (final ClassNotFoundException e) {
            // ignored, continue search
        }

        // try script -classpath loader, if set
        if (classPathLoader != null) {
            try {
                return Class.forName(fullName, true, classPathLoader);
            } catch (final ClassNotFoundException e) {
                // ignore, continue search
            }
        }

        // This helps in finding using "app" loader - which is typically set as thread context loader
        try {
            return Class.forName(fullName, true, Thread.currentThread().getContextClassLoader());
        } catch (final ClassNotFoundException e) {
            throw e;
        }
    }

    /**
     * Hook to print stack trace for a {@link Throwable} that occurred during
     * execution
     *
     * @param t throwable for which to dump stack
     */
    public static void printStackTrace(final Throwable t) {
        if (Context.DEBUG) {
            t.printStackTrace(Context.getContext().getErr());
        }
    }

    /**
     * Verify generated bytecode before emission. This is called back from the
     * {@link ClassEmitter} or the {@link Compiler}. If the "--verify-code" parameter
     * hasn't been given, this is a nop
     *
     * Note that verification may load classes -- we don't want to do that unless
     * user specified verify option. We check it here even though caller
     * may have already checked that flag
     *
     * @param bytecode bytecode to verify
     */
    public void verify(final byte[] bytecode) {
        if (_verify_code) {
            // No verification when security manager is around as verifier
            // may load further classes - which should be avoided.
            if (System.getSecurityManager() == null) {
                CheckClassAdapter.verify(new ClassReader(bytecode), scriptLoader, false, new PrintWriter(System.err, true));
            }
        }
    }

    /**
     * Create global script object
     * @return the global script object
     */
    public ScriptObject createGlobal() {
        final ScriptObject global = newGlobal();

        // Need only minimal global object, if we are just compiling.
        if (!_compile_only) {
            // initialize global scope with builtin global objects
            ((GlobalObject)global).initBuiltinObjects();
        }

        return global;
    }

    /**
     * Try to infer Context instance from the Class. If we cannot,
     * then get it from the thread local variable.
     *
     * @param clazz the class
     * @return context
     */
    static Context fromClass(final Class<?> clazz) {
        final ClassLoader loader = clazz.getClassLoader();

        Context context = null;
        if (loader instanceof NashornLoader) {
            context = ((NashornLoader)loader).getContext();
        }

        return (context != null) ? context : Context.getContext();
    }

    private Object evaluateSource(final String name, final URL url, final ScriptObject scope, final ScriptObject thiz) throws IOException {
        ScriptFunction script = null;

        try {
            script = compileScript(name, url, scope, new Context.ThrowErrorManager(), _strict);
        } catch (final ParserException e) {
            e.throwAsEcmaException(Context.getGlobal());
        }

        return ScriptRuntime.apply(script, thiz);
    }

    private Object evaluateSource(final Source source, final ScriptObject scope, final ScriptObject thiz) {
        ScriptFunction script = null;

        try {
            script = compileScript(source, scope, new Context.ThrowErrorManager(), _strict);
        } catch (final ParserException e) {
            e.throwAsEcmaException(Context.getGlobal());
        }

        return ScriptRuntime.apply(script, thiz);
    }

    private static ScriptFunction getRunScriptFunction(final Class<?> script, final ScriptObject scope) {
        if (script == null) {
            return null;
        }

        // Get run method - the entry point to the script
        final MethodHandle runMethodHandle =
                MH.findStatic(
                    MethodHandles.lookup(),
                    script,
                    RUN_SCRIPT.tag(),
                    MH.type(
                        Object.class,
                        Object.class,
                        ScriptFunction.class));

        boolean strict;

        try {
            strict = script.getField(STRICT_MODE.tag()).getBoolean(null);
        } catch (final NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
            strict = false;
        }

        // Package as a JavaScript function and pass function back to shell.
        return ((GlobalObject)Context.getGlobal()).newScriptFunction(RUN_SCRIPT.tag(), runMethodHandle, scope, strict);
    }

    private ScriptFunction compileScript(final String name, final URL url, final ScriptObject scope, final ErrorManager errMan, final boolean strict) throws IOException {
        return getRunScriptFunction(compile(new Source(name, url), url, errMan, strict), scope);
    }

    private ScriptFunction compileScript(final Source source, final ScriptObject scope, final ErrorManager errMan, final boolean strict) {
        return getRunScriptFunction(compile(source, null, errMan, strict), scope);
    }

    private Class<?> compile(final Source source, final ErrorManager errMan, final boolean strict) {
        return compile(source, null, errMan, strict);
    }

   private synchronized Class<?> compile(final Source source, final URL url, final ErrorManager errMan, final boolean strict) {
        // start with no errors, no warnings.
        errMan.reset();

        GlobalObject global = null;
        Class<?> script;

        if (_class_cache_size > 0) {
            global = (GlobalObject)Context.getGlobal();
            script = global.findCachedClass(source);
            if (script != null) {
                return script;
            }
        }

        final Compiler compiler = Compiler.compiler(source, this, errMan, strict);

        if (!compiler.compile()) {
            return null;
        }

        final ScriptLoader loader = _loader_per_compile ? createNewLoader() : scriptLoader;
        final CodeSource   cs     = url == null ? null : new CodeSource(url, (CodeSigner[])null);

        script = compiler.install(new CodeInstaller() {
            @Override
            public Class<?> install(final String className, final byte[] bytecode) {
                return loader.installClass(className, bytecode, cs);
            }
        });

        if (global != null) {
            global.cacheClass(source, script);
        }

        return script;
    }

    private ScriptLoader createNewLoader() {
        return AccessController.doPrivileged(
             new PrivilegedAction<ScriptLoader>() {
                @Override
                public ScriptLoader run() {
                    // Generated code won't refer to any class generated by context
                    // script loader and so parent loader can be the structure
                    // loader -- which is parent of the context script loader.
                    return new ScriptLoader(scriptLoader.getParent(), Context.this);
                }
             });
    }

    private ScriptObject newGlobal() {
        try {
            final Class<?> clazz = Class.forName("jdk.nashorn.internal.objects.Global", true, scriptLoader);
            return (ScriptObject) clazz.newInstance();
        } catch (final ClassNotFoundException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException e) {
            printStackTrace(e);
            throw new RuntimeException(e);
        }
    }
}
