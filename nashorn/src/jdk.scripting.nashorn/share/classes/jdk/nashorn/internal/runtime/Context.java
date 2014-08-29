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

import static jdk.nashorn.internal.codegen.CompilerConstants.CONSTANTS;
import static jdk.nashorn.internal.codegen.CompilerConstants.CREATE_PROGRAM_FUNCTION;
import static jdk.nashorn.internal.codegen.CompilerConstants.SOURCE;
import static jdk.nashorn.internal.codegen.CompilerConstants.STRICT_MODE;
import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;
import static jdk.nashorn.internal.runtime.Source.sourceFor;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.Permissions;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.ProtectionDomain;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import javax.script.ScriptEngine;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.util.CheckClassAdapter;
import jdk.nashorn.api.scripting.ClassFilter;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import jdk.nashorn.internal.codegen.Compiler;
import jdk.nashorn.internal.codegen.Compiler.CompilationPhases;
import jdk.nashorn.internal.codegen.ObjectClassGenerator;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.debug.ASTWriter;
import jdk.nashorn.internal.ir.debug.PrintVisitor;
import jdk.nashorn.internal.lookup.MethodHandleFactory;
import jdk.nashorn.internal.objects.Global;
import jdk.nashorn.internal.parser.Parser;
import jdk.nashorn.internal.runtime.events.RuntimeEvent;
import jdk.nashorn.internal.runtime.logging.DebugLogger;
import jdk.nashorn.internal.runtime.logging.Loggable;
import jdk.nashorn.internal.runtime.logging.Logger;
import jdk.nashorn.internal.runtime.options.LoggingOption.LoggerInfo;
import jdk.nashorn.internal.runtime.options.Options;

/**
 * This class manages the global state of execution. Context is immutable.
 */
public final class Context {
    // nashorn specific security runtime access permission names
    /**
     * Permission needed to pass arbitrary nashorn command line options when creating Context.
     */
    public static final String NASHORN_SET_CONFIG      = "nashorn.setConfig";

    /**
     * Permission needed to create Nashorn Context instance.
     */
    public static final String NASHORN_CREATE_CONTEXT  = "nashorn.createContext";

    /**
     * Permission needed to create Nashorn Global instance.
     */
    public static final String NASHORN_CREATE_GLOBAL   = "nashorn.createGlobal";

    /**
     * Permission to get current Nashorn Context from thread local storage.
     */
    public static final String NASHORN_GET_CONTEXT     = "nashorn.getContext";

    /**
     * Permission to use Java reflection/jsr292 from script code.
     */
    public static final String NASHORN_JAVA_REFLECTION = "nashorn.JavaReflection";

    /**
     * Permission to enable nashorn debug mode.
     */
    public static final String NASHORN_DEBUG_MODE = "nashorn.debugMode";

    // nashorn load psuedo URL prefixes
    private static final String LOAD_CLASSPATH = "classpath:";
    private static final String LOAD_FX = "fx:";
    private static final String LOAD_NASHORN = "nashorn:";

    private static MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static MethodType CREATE_PROGRAM_FUNCTION_TYPE = MethodType.methodType(ScriptFunction.class, ScriptObject.class);

    /* Force DebuggerSupport to be loaded. */
    static {
        DebuggerSupport.FORCELOAD = true;
    }

    /**
     * ContextCodeInstaller that has the privilege of installing classes in the Context.
     * Can only be instantiated from inside the context and is opaque to other classes
     */
    public static class ContextCodeInstaller implements CodeInstaller<ScriptEnvironment> {
        private final Context      context;
        private final ScriptLoader loader;
        private final CodeSource   codeSource;

        private ContextCodeInstaller(final Context context, final ScriptLoader loader, final CodeSource codeSource) {
            this.context    = context;
            this.loader     = loader;
            this.codeSource = codeSource;
        }

        /**
         * Return the context for this installer
         * @return ScriptEnvironment
         */
        @Override
        public ScriptEnvironment getOwner() {
            return context.env;
        }

        @Override
        public Class<?> install(final String className, final byte[] bytecode) {
            final String   binaryName = Compiler.binaryName(className);
            return loader.installClass(binaryName, bytecode, codeSource);
        }

        @Override
        public void initialize(final Collection<Class<?>> classes, final Source source, final Object[] constants) {
            // do these in parallel, this significantly reduces class installation overhead
            // however - it still means that every thread needs a separate doPrivileged
            final Global global = currentGlobal.get();
            classes.parallelStream().forEach(
                new Consumer<Class<?>>() {
                    @Override
                    public void accept(final Class<?> clazz) {
                        // Global threadlocal may be needed by StructureLoader during in field lookup.
                        currentGlobal.set(global);
                        try {
                            AccessController.doPrivileged(new PrivilegedExceptionAction<Void>() {
                                @Override
                                public Void run() {
                                    try {
                                        //use reflection to write source and constants table to installed classes
                                        final Field sourceField = clazz.getDeclaredField(SOURCE.symbolName());
                                        sourceField.setAccessible(true);
                                        sourceField.set(null, source);

                                        final Field constantsField = clazz.getDeclaredField(CONSTANTS.symbolName());
                                        constantsField.setAccessible(true);
                                        constantsField.set(null, constants);
                                    } catch (final IllegalAccessException | NoSuchFieldException e) {
                                        throw new RuntimeException(e);
                                    }
                                    return null;
                                }
                            });
                        } catch (final PrivilegedActionException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
        }

        @Override
        public void verify(final byte[] code) {
            context.verify(code);
        }

        @Override
        public long getUniqueScriptId() {
            return context.getUniqueScriptId();
        }

        @Override
        public long getUniqueEvalId() {
            return context.getUniqueEvalId();
        }

        @Override
        public void storeScript(final String classInfoFile, final Source source, final String mainClassName,
                                final Map<String,byte[]> classBytes, final Map<Integer, FunctionInitializer> initializers,
                                final Object[] constants, final int compilationId) {
            if (context.codeStore != null) {
                context.codeStore.storeScript(classInfoFile, source, mainClassName, classBytes, initializers, constants, compilationId);
            }
        }

        @Override
        public StoredScript loadScript(final Source source, final String functionKey) {
            if (context.codeStore != null) {
                return context.codeStore.loadScript(source, functionKey);
            }
            return null;
        }
    }

    /** Is Context global debug mode enabled ? */
    public static final boolean DEBUG = Options.getBooleanProperty("nashorn.debug");

    private static final ThreadLocal<Global> currentGlobal = new ThreadLocal<>();

    // in-memory cache for loaded classes
    private ClassCache classCache;

    // persistent code store
    private CodeStore codeStore;

    /**
     * Get the current global scope
     * @return the current global scope
     */
    public static Global getGlobal() {
        // This class in a package.access protected package.
        // Trusted code only can call this method.
        return currentGlobal.get();
    }

    /**
     * Set the current global scope
     * @param global the global scope
     */
    public static void setGlobal(final ScriptObject global) {
        if (global != null && !(global instanceof Global)) {
            throw new IllegalArgumentException("not a global!");
        }
        setGlobal((Global)global);
    }

    /**
     * Set the current global scope
     * @param global the global scope
     */
    public static void setGlobal(final Global global) {
        // This class in a package.access protected package.
        // Trusted code only can call this method.
        assert getGlobal() != global;
        //same code can be cached between globals, then we need to invalidate method handle constants
        if (global != null) {
            Global.getConstants().invalidateAll();
        }
        currentGlobal.set(global);
    }

    /**
     * Get context of the current global
     * @return current global scope's context.
     */
    public static Context getContext() {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new RuntimePermission(NASHORN_GET_CONTEXT));
        }
        return getContextTrusted();
    }

    /**
     * Get current context's error writer
     *
     * @return error writer of the current context
     */
    public static PrintWriter getCurrentErr() {
        final ScriptObject global = getGlobal();
        return (global != null)? global.getContext().getErr() : new PrintWriter(System.err);
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
    public static void err(final String str, final boolean crlf) {
        final PrintWriter err = Context.getCurrentErr();
        if (err != null) {
            if (crlf) {
                err.println(str);
            } else {
                err.print(str);
            }
        }
    }

    /** Current environment. */
    private final ScriptEnvironment env;

    /** is this context in strict mode? Cached from env. as this is used heavily. */
    final boolean _strict;

    /** class loader to resolve classes from script. */
    private final ClassLoader  appLoader;

    /** Class loader to load classes from -classpath option, if set. */
    private final ClassLoader  classPathLoader;

    /** Class loader to load classes compiled from scripts. */
    private final ScriptLoader scriptLoader;

    /** Current error manager. */
    private final ErrorManager errors;

    /** Unique id for script. Used only when --loader-per-compile=false */
    private final AtomicLong uniqueScriptId;

    /** Unique id for 'eval' */
    private final AtomicLong uniqueEvalId;

    /** Optional class filter to use for Java classes. Can be null. */
    private final ClassFilter classFilter;

    private static final ClassLoader myLoader = Context.class.getClassLoader();
    private static final StructureLoader sharedLoader;

    /*package-private*/ @SuppressWarnings("static-method")
    ClassLoader getSharedLoader() {
        return sharedLoader;
    }

    private static AccessControlContext createNoPermAccCtxt() {
        return new AccessControlContext(new ProtectionDomain[] { new ProtectionDomain(null, new Permissions()) });
    }

    private static AccessControlContext createPermAccCtxt(final String permName) {
        final Permissions perms = new Permissions();
        perms.add(new RuntimePermission(permName));
        return new AccessControlContext(new ProtectionDomain[] { new ProtectionDomain(null, perms) });
    }

    private static final AccessControlContext NO_PERMISSIONS_ACC_CTXT = createNoPermAccCtxt();
    private static final AccessControlContext CREATE_LOADER_ACC_CTXT  = createPermAccCtxt("createClassLoader");
    private static final AccessControlContext CREATE_GLOBAL_ACC_CTXT  = createPermAccCtxt(NASHORN_CREATE_GLOBAL);

    static {
        sharedLoader = AccessController.doPrivileged(new PrivilegedAction<StructureLoader>() {
            @Override
            public StructureLoader run() {
                return new StructureLoader(myLoader);
            }
        }, CREATE_LOADER_ACC_CTXT);
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
     * @param appLoader application class loader
     */
    public Context(final Options options, final ErrorManager errors, final ClassLoader appLoader) {
        this(options, errors, appLoader, (ClassFilter)null);
    }

    /**
     * Constructor
     *
     * @param options options from command line or Context creator
     * @param errors  error manger
     * @param appLoader application class loader
     * @param classFilter class filter to use
     */
    public Context(final Options options, final ErrorManager errors, final ClassLoader appLoader, final ClassFilter classFilter) {
        this(options, errors, new PrintWriter(System.out, true), new PrintWriter(System.err, true), appLoader, classFilter);
    }

    /**
     * Constructor
     *
     * @param options options from command line or Context creator
     * @param errors  error manger
     * @param out     output writer for this Context
     * @param err     error writer for this Context
     * @param appLoader application class loader
     */
    public Context(final Options options, final ErrorManager errors, final PrintWriter out, final PrintWriter err, final ClassLoader appLoader) {
        this(options, errors, out, err, appLoader, (ClassFilter)null);
    }

    /**
     * Constructor
     *
     * @param options options from command line or Context creator
     * @param errors  error manger
     * @param out     output writer for this Context
     * @param err     error writer for this Context
     * @param appLoader application class loader
     * @param classFilter class filter to use
     */
    public Context(final Options options, final ErrorManager errors, final PrintWriter out, final PrintWriter err, final ClassLoader appLoader, final ClassFilter classFilter) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new RuntimePermission(NASHORN_CREATE_CONTEXT));
        }

        this.classFilter = classFilter;
        this.env       = new ScriptEnvironment(options, out, err);
        this._strict   = env._strict;
        this.appLoader = appLoader;
        if (env._loader_per_compile) {
            this.scriptLoader = null;
            this.uniqueScriptId = null;
        } else {
            this.scriptLoader = createNewLoader();
            this.uniqueScriptId = new AtomicLong();
        }
        this.errors    = errors;
        this.uniqueEvalId = new AtomicLong();

        // if user passed -classpath option, make a class loader with that and set it as
        // thread context class loader so that script can access classes from that path.
        final String classPath = options.getString("classpath");
        if (!env._compile_only && classPath != null && !classPath.isEmpty()) {
            // make sure that caller can create a class loader.
            if (sm != null) {
                sm.checkPermission(new RuntimePermission("createClassLoader"));
            }
            this.classPathLoader = NashornLoader.createClassLoader(classPath);
        } else {
            this.classPathLoader = null;
        }

        final int cacheSize = env._class_cache_size;
        if (cacheSize > 0) {
            classCache = new ClassCache(cacheSize);
        }

        if (env._persistent_cache) {
            try {
                final String cacheDir = Options.getStringProperty("nashorn.persistent.code.cache", "nashorn_code_cache");
                codeStore = new CodeStore(this, cacheDir);
            } catch (final IOException e) {
                throw new RuntimeException("Error initializing code cache", e);
            }
        }

        // print version info if asked.
        if (env._version) {
            getErr().println("nashorn " + Version.version());
        }

        if (env._fullversion) {
            getErr().println("nashorn full version " + Version.fullVersion());
        }

        initLoggers();
    }


    /**
     * Get the class filter for this context
     * @return class filter
     */
    public ClassFilter getClassFilter() {
        return classFilter;
    }

    /**
     * Get the error manager for this context
     * @return error manger
     */
    public ErrorManager getErrorManager() {
        return errors;
    }

    /**
     * Get the script environment for this context
     * @return script environment
     */
    public ScriptEnvironment getEnv() {
        return env;
    }

    /**
     * Get the output stream for this context
     * @return output print writer
     */
    public PrintWriter getOut() {
        return env.getOut();
    }

    /**
     * Get the error stream for this context
     * @return error print writer
     */
    public PrintWriter getErr() {
        return env.getErr();
    }

    /**
     * Get the PropertyMap of the current global scope
     * @return the property map of the current global scope
     */
    public static PropertyMap getGlobalMap() {
        return Context.getGlobal().getMap();
    }

    /**
     * Compile a top level script.
     *
     * @param source the source
     * @param scope  the scope
     *
     * @return top level function for script
     */
    public ScriptFunction compileScript(final Source source, final ScriptObject scope) {
        return compileScript(source, scope, this.errors);
    }

    /**
     * Interface to represent compiled code that can be re-used across many
     * global scope instances
     */
    public static interface MultiGlobalCompiledScript {
        /**
         * Obtain script function object for a specific global scope object.
         *
         * @param newGlobal global scope for which function object is obtained
         * @return script function for script level expressions
         */
        public ScriptFunction getFunction(final Global newGlobal);
    }

    /**
     * Compile a top level script.
     *
     * @param source the script source
     * @return reusable compiled script across many global scopes.
     */
    public MultiGlobalCompiledScript compileScript(final Source source) {
        final Class<?> clazz = compile(source, this.errors, this._strict);
        final MethodHandle createProgramFunctionHandle = getCreateProgramFunctionHandle(clazz);

        return new MultiGlobalCompiledScript() {
            @Override
            public ScriptFunction getFunction(final Global newGlobal) {
                return invokeCreateProgramFunctionHandle(createProgramFunctionHandle, newGlobal);
            }
        };
    }

    /**
     * Entry point for {@code eval}
     *
     * @param initialScope The scope of this eval call
     * @param string       Evaluated code as a String
     * @param callThis     "this" to be passed to the evaluated code
     * @param location     location of the eval call
     * @param strict       is this {@code eval} call from a strict mode code?
     * @return the return value of the {@code eval}
     */
    public Object eval(final ScriptObject initialScope, final String string,
            final Object callThis, final Object location, final boolean strict) {
        return eval(initialScope, string, callThis, location, strict, false);
    }

    /**
     * Entry point for {@code eval}
     *
     * @param initialScope The scope of this eval call
     * @param string       Evaluated code as a String
     * @param callThis     "this" to be passed to the evaluated code
     * @param location     location of the eval call
     * @param strict       is this {@code eval} call from a strict mode code?
     * @param evalCall     is this called from "eval" builtin?
     *
     * @return the return value of the {@code eval}
     */
    public Object eval(final ScriptObject initialScope, final String string,
            final Object callThis, final Object location, final boolean strict, final boolean evalCall) {
        final String  file       = location == UNDEFINED || location == null ? "<eval>" : location.toString();
        final Source  source     = sourceFor(file, string, evalCall);
        final boolean directEval = location != UNDEFINED; // is this direct 'eval' call or indirectly invoked eval?
        final Global  global = Context.getGlobal();
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
                strictFlag = clazz.getField(STRICT_MODE.symbolName()).getBoolean(null);
            } catch (final NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
                //ignored
                strictFlag = false;
            }
        }

        // In strict mode, eval does not instantiate variables and functions
        // in the caller's environment. A new environment is created!
        if (strictFlag) {
            // Create a new scope object
            final ScriptObject strictEvalScope = global.newObject();

            // bless it as a "scope"
            strictEvalScope.setIsScope();

            // set given scope to be it's proto so that eval can still
            // access caller environment vars in the new environment.
            strictEvalScope.setProto(scope);
            scope = strictEvalScope;
        }

        final ScriptFunction func = getProgramFunction(clazz, scope);
        Object evalThis;
        if (directEval) {
            evalThis = callThis instanceof ScriptObject || strictFlag ? callThis : global;
        } else {
            evalThis = global;
        }

        return ScriptRuntime.apply(func, evalThis);
    }

    private static Source loadInternal(final String srcStr, final String prefix, final String resourcePath) {
        if (srcStr.startsWith(prefix)) {
            final String resource = resourcePath + srcStr.substring(prefix.length());
            // NOTE: even sandbox scripts should be able to load scripts in nashorn: scheme
            // These scripts are always available and are loaded from nashorn.jar's resources.
            return AccessController.doPrivileged(
                    new PrivilegedAction<Source>() {
                        @Override
                        public Source run() {
                            try {
                                final URL resURL = Context.class.getResource(resource);
                                return resURL != null ? sourceFor(srcStr, resURL) : null;
                            } catch (final IOException exp) {
                                return null;
                            }
                        }
                    });
        }

        return null;
    }

    /**
     * Implementation of {@code load} Nashorn extension. Load a script file from a source
     * expression
     *
     * @param scope  the scope
     * @param from   source expression for script
     *
     * @return return value for load call (undefined)
     *
     * @throws IOException if source cannot be found or loaded
     */
    public Object load(final ScriptObject scope, final Object from) throws IOException {
        final Object src = from instanceof ConsString ? from.toString() : from;
        Source source = null;

        // load accepts a String (which could be a URL or a file name), a File, a URL
        // or a ScriptObject that has "name" and "source" (string valued) properties.
        if (src instanceof String) {
            final String srcStr = (String)src;
            if (srcStr.startsWith(LOAD_CLASSPATH)) {
                final URL url = getResourceURL(srcStr.substring(LOAD_CLASSPATH.length()));
                source = url != null ? sourceFor(url.toString(), url) : null;
            } else {
                final File file = new File(srcStr);
                if (srcStr.indexOf(':') != -1) {
                    if ((source = loadInternal(srcStr, LOAD_NASHORN, "resources/")) == null &&
                        (source = loadInternal(srcStr, LOAD_FX, "resources/fx/")) == null) {
                        URL url;
                        try {
                            //check for malformed url. if malformed, it may still be a valid file
                            url = new URL(srcStr);
                        } catch (final MalformedURLException e) {
                            url = file.toURI().toURL();
                        }
                        source = sourceFor(url.toString(), url);
                    }
                } else if (file.isFile()) {
                    source = sourceFor(srcStr, file);
                }
            }
        } else if (src instanceof File && ((File)src).isFile()) {
            final File file = (File)src;
            source = sourceFor(file.getName(), file);
        } else if (src instanceof URL) {
            final URL url = (URL)src;
            source = sourceFor(url.toString(), url);
        } else if (src instanceof ScriptObject) {
            final ScriptObject sobj = (ScriptObject)src;
            if (sobj.has("script") && sobj.has("name")) {
                final String script = JSType.toString(sobj.get("script"));
                final String name   = JSType.toString(sobj.get("name"));
                source = sourceFor(name, script);
            }
        } else if (src instanceof Map) {
            final Map<?,?> map = (Map<?,?>)src;
            if (map.containsKey("script") && map.containsKey("name")) {
                final String script = JSType.toString(map.get("script"));
                final String name   = JSType.toString(map.get("name"));
                source = sourceFor(name, script);
            }
        }

        if (source != null) {
            return evaluateSource(source, scope, scope);
        }

        throw typeError("cant.load.script", ScriptRuntime.safeToString(from));
    }

    /**
     * Implementation of {@code loadWithNewGlobal} Nashorn extension. Load a script file from a source
     * expression, after creating a new global scope.
     *
     * @param from source expression for script
     * @param args (optional) arguments to be passed to the loaded script
     *
     * @return return value for load call (undefined)
     *
     * @throws IOException if source cannot be found or loaded
     */
    public Object loadWithNewGlobal(final Object from, final Object...args) throws IOException {
        final Global oldGlobal = getGlobal();
        final Global newGlobal = AccessController.doPrivileged(new PrivilegedAction<Global>() {
           @Override
           public Global run() {
               try {
                   return newGlobal();
               } catch (final RuntimeException e) {
                   if (Context.DEBUG) {
                       e.printStackTrace();
                   }
                   throw e;
               }
           }
        }, CREATE_GLOBAL_ACC_CTXT);
        // initialize newly created Global instance
        initGlobal(newGlobal);
        setGlobal(newGlobal);

        final Object[] wrapped = args == null? ScriptRuntime.EMPTY_ARRAY :  ScriptObjectMirror.wrapArray(args, oldGlobal);
        newGlobal.put("arguments", newGlobal.wrapAsObject(wrapped), env._strict);

        try {
            // wrap objects from newGlobal's world as mirrors - but if result
            // is from oldGlobal's world, unwrap it!
            return ScriptObjectMirror.unwrap(ScriptObjectMirror.wrap(load(newGlobal, from), newGlobal), oldGlobal);
        } finally {
            setGlobal(oldGlobal);
        }
    }

    /**
     * Load or get a structure class. Structure class names are based on the number of parameter fields
     * and {@link AccessorProperty} fields in them. Structure classes are used to represent ScriptObjects
     *
     * @see ObjectClassGenerator
     * @see AccessorProperty
     * @see ScriptObject
     *
     * @param fullName  full name of class, e.g. jdk.nashorn.internal.objects.JO2P1 contains 2 fields and 1 parameter.
     *
     * @return the {@code Class<?>} for this structure
     *
     * @throws ClassNotFoundException if structure class cannot be resolved
     */
    @SuppressWarnings("unchecked")
    public static Class<? extends ScriptObject> forStructureClass(final String fullName) throws ClassNotFoundException {
        if (System.getSecurityManager() != null && !StructureLoader.isStructureClass(fullName)) {
            throw new ClassNotFoundException(fullName);
        }
        return (Class<? extends ScriptObject>)Class.forName(fullName, true, sharedLoader);
    }

    /**
     * Checks that the given Class can be accessed from no permissions context.
     *
     * @param clazz Class object
     * @throws SecurityException if not accessible
     */
    public static void checkPackageAccess(final Class<?> clazz) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            Class<?> bottomClazz = clazz;
            while (bottomClazz.isArray()) {
                bottomClazz = bottomClazz.getComponentType();
            }
            checkPackageAccess(sm, bottomClazz.getName());
        }
    }

    /**
     * Checks that the given package name can be accessed from no permissions context.
     *
     * @param pkgName package name
     * @throws SecurityException if not accessible
     */
    public static void checkPackageAccess(final String pkgName) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            checkPackageAccess(sm, pkgName.endsWith(".") ? pkgName : pkgName + ".");
        }
    }

    /**
     * Checks that the given package can be accessed from no permissions context.
     *
     * @param sm current security manager instance
     * @param fullName fully qualified package name
     * @throw SecurityException if not accessible
     */
    private static void checkPackageAccess(final SecurityManager sm, final String fullName) {
        sm.getClass(); // null check
        final int index = fullName.lastIndexOf('.');
        if (index != -1) {
            final String pkgName = fullName.substring(0, index);
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                @Override
                public Void run() {
                    sm.checkPackageAccess(pkgName);
                    return null;
                }
            }, NO_PERMISSIONS_ACC_CTXT);
        }
    }

    /**
     * Checks that the given Class can be accessed from no permissions context.
     *
     * @param clazz Class object
     * @return true if package is accessible, false otherwise
     */
    private static boolean isAccessiblePackage(final Class<?> clazz) {
        try {
            checkPackageAccess(clazz);
            return true;
        } catch (final SecurityException se) {
            return false;
        }
    }

    /**
     * Checks that the given Class is public and it can be accessed from no permissions context.
     *
     * @param clazz Class object to check
     * @return true if Class is accessible, false otherwise
     */
    public static boolean isAccessibleClass(final Class<?> clazz) {
        return Modifier.isPublic(clazz.getModifiers()) && Context.isAccessiblePackage(clazz);
    }

    /**
     * Lookup a Java class. This is used for JSR-223 stuff linking in from
     * {@code jdk.nashorn.internal.objects.NativeJava} and {@code jdk.nashorn.internal.runtime.NativeJavaPackage}
     *
     * @param fullName full name of class to load
     *
     * @return the {@code Class<?>} for the name
     *
     * @throws ClassNotFoundException if class cannot be resolved
     */
    public Class<?> findClass(final String fullName) throws ClassNotFoundException {
        if (fullName.indexOf('[') != -1 || fullName.indexOf('/') != -1) {
            // don't allow array class names or internal names.
            throw new ClassNotFoundException(fullName);
        }

        // give chance to ClassFilter to filter out, if present
        if (classFilter != null && !classFilter.exposeToScripts(fullName)) {
            throw new ClassNotFoundException(fullName);
        }

        // check package access as soon as possible!
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            checkPackageAccess(sm, fullName);
        }

        // try the script -classpath loader, if that is set
        if (classPathLoader != null) {
            try {
                return Class.forName(fullName, true, classPathLoader);
            } catch (final ClassNotFoundException ignored) {
                // ignore, continue search
            }
        }

        // Try finding using the "app" loader.
        return Class.forName(fullName, true, appLoader);
    }

    /**
     * Hook to print stack trace for a {@link Throwable} that occurred during
     * execution
     *
     * @param t throwable for which to dump stack
     */
    public static void printStackTrace(final Throwable t) {
        if (Context.DEBUG) {
            t.printStackTrace(Context.getCurrentErr());
        }
    }

    /**
     * Verify generated bytecode before emission. This is called back from the
     * {@link ObjectClassGenerator} or the {@link Compiler}. If the "--verify-code" parameter
     * hasn't been given, this is a nop
     *
     * Note that verification may load classes -- we don't want to do that unless
     * user specified verify option. We check it here even though caller
     * may have already checked that flag
     *
     * @param bytecode bytecode to verify
     */
    public void verify(final byte[] bytecode) {
        if (env._verify_code) {
            // No verification when security manager is around as verifier
            // may load further classes - which should be avoided.
            if (System.getSecurityManager() == null) {
                CheckClassAdapter.verify(new ClassReader(bytecode), sharedLoader, false, new PrintWriter(System.err, true));
            }
        }
    }

    /**
     * Create and initialize a new global scope object.
     *
     * @return the initialized global scope object.
     */
    public Global createGlobal() {
        return initGlobal(newGlobal());
    }

    /**
     * Create a new uninitialized global scope object
     * @return the global script object
     */
    public Global newGlobal() {
        return new Global(this);
    }

    /**
     * Initialize given global scope object.
     *
     * @param global the global
     * @param engine the associated ScriptEngine instance, can be null
     * @return the initialized global scope object.
     */
    public Global initGlobal(final Global global, final ScriptEngine engine) {
        // Need only minimal global object, if we are just compiling.
        if (!env._compile_only) {
            final Global oldGlobal = Context.getGlobal();
            try {
                Context.setGlobal(global);
                // initialize global scope with builtin global objects
                global.initBuiltinObjects(engine);
            } finally {
                Context.setGlobal(oldGlobal);
            }
        }

        return global;
    }

    /**
     * Initialize given global scope object.
     *
     * @param global the global
     * @return the initialized global scope object.
     */
    public Global initGlobal(final Global global) {
        return initGlobal(global, null);
    }

    /**
     * Return the current global's context
     * @return current global's context
     */
    static Context getContextTrusted() {
        return ((ScriptObject)Context.getGlobal()).getContext();
    }

    static Context getContextTrustedOrNull() {
        final Global global = Context.getGlobal();
        return global == null ? null : ((ScriptObject)global).getContext();
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

        if (loader instanceof ScriptLoader) {
            return ((ScriptLoader)loader).getContext();
        }

        return Context.getContextTrusted();
    }

    private URL getResourceURL(final String resName) {
        // try the classPathLoader if we have and then
        // try the appLoader if non-null.
        if (classPathLoader != null) {
            return classPathLoader.getResource(resName);
        } else if (appLoader != null) {
            return appLoader.getResource(resName);
        }

        return null;
    }

    private Object evaluateSource(final Source source, final ScriptObject scope, final ScriptObject thiz) {
        ScriptFunction script = null;

        try {
            script = compileScript(source, scope, new Context.ThrowErrorManager());
        } catch (final ParserException e) {
            e.throwAsEcmaException();
        }

        return ScriptRuntime.apply(script, thiz);
    }

    private static ScriptFunction getProgramFunction(final Class<?> script, final ScriptObject scope) {
        if (script == null) {
            return null;
        }
        return invokeCreateProgramFunctionHandle(getCreateProgramFunctionHandle(script), scope);
    }

    private static MethodHandle getCreateProgramFunctionHandle(final Class<?> script) {
        try {
            return LOOKUP.findStatic(script, CREATE_PROGRAM_FUNCTION.symbolName(), CREATE_PROGRAM_FUNCTION_TYPE);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new AssertionError("Failed to retrieve a handle for the program function for " + script.getName(), e);
        }
    }

    private static ScriptFunction invokeCreateProgramFunctionHandle(final MethodHandle createProgramFunctionHandle, final ScriptObject scope) {
        try {
            return (ScriptFunction)createProgramFunctionHandle.invokeExact(scope);
        } catch (final RuntimeException|Error e) {
            throw e;
        } catch (final Throwable t) {
            throw new AssertionError("Failed to create a program function", t);
        }
    }

    private ScriptFunction compileScript(final Source source, final ScriptObject scope, final ErrorManager errMan) {
        return getProgramFunction(compile(source, errMan, this._strict), scope);
    }

    private synchronized Class<?> compile(final Source source, final ErrorManager errMan, final boolean strict) {
        // start with no errors, no warnings.
        errMan.reset();

        Class<?> script = findCachedClass(source);
        if (script != null) {
            final DebugLogger log = getLogger(Compiler.class);
            if (log.isEnabled()) {
                log.fine(new RuntimeEvent<>(Level.INFO, source), "Code cache hit for ", source, " avoiding recompile.");
            }
            return script;
        }

        StoredScript storedScript = null;
        FunctionNode functionNode = null;
        final boolean useCodeStore = env._persistent_cache && !env._parse_only && !env._optimistic_types;
        final String cacheKey = useCodeStore ? CodeStore.getCacheKey(0, null) : null;

        if (useCodeStore) {
            storedScript = codeStore.loadScript(source, cacheKey);
        }

        if (storedScript == null) {
            functionNode = new Parser(env, source, errMan, strict, getLogger(Parser.class)).parse();

            if (errors.hasErrors()) {
                return null;
            }

            if (env._print_ast || functionNode.getFlag(FunctionNode.IS_PRINT_AST)) {
                getErr().println(new ASTWriter(functionNode));
            }

            if (env._print_parse || functionNode.getFlag(FunctionNode.IS_PRINT_PARSE)) {
                getErr().println(new PrintVisitor(functionNode, true, false));
            }
        }

        if (env._parse_only) {
            return null;
        }

        final URL          url    = source.getURL();
        final ScriptLoader loader = env._loader_per_compile ? createNewLoader() : scriptLoader;
        final CodeSource   cs     = new CodeSource(url, (CodeSigner[])null);
        final CodeInstaller<ScriptEnvironment> installer = new ContextCodeInstaller(this, loader, cs);

        if (storedScript == null) {
            final CompilationPhases phases = Compiler.CompilationPhases.COMPILE_ALL;

            final Compiler compiler = new Compiler(
                    this,
                    env,
                    installer,
                    source,
                    strict | functionNode.isStrict());

            final FunctionNode compiledFunction = compiler.compile(functionNode, phases);
            script = compiledFunction.getRootClass();
            compiler.persistClassInfo(cacheKey, compiledFunction);
        } else {
            Compiler.updateCompilationId(storedScript.getCompilationId());
            script = install(storedScript, source, installer);
        }

        cacheClass(source, script);
        return script;
    }

    private ScriptLoader createNewLoader() {
        return AccessController.doPrivileged(
             new PrivilegedAction<ScriptLoader>() {
                @Override
                public ScriptLoader run() {
                    return new ScriptLoader(appLoader, Context.this);
                }
             }, CREATE_LOADER_ACC_CTXT);
    }

    private long getUniqueEvalId() {
        return uniqueEvalId.getAndIncrement();
    }

    private long getUniqueScriptId() {
        return uniqueScriptId.getAndIncrement();
    }

    /**
     * Install a previously compiled class from the code cache.
     *
     * @param storedScript cached script containing class bytes and constants
     * @return main script class
     */
    private static Class<?> install(final StoredScript storedScript, final Source source, final CodeInstaller<ScriptEnvironment> installer) {

        final Map<String, Class<?>> installedClasses = new HashMap<>();
        final Object[] constants       = storedScript.getConstants();
        final String   mainClassName   = storedScript.getMainClassName();
        final byte[]   mainClassBytes  = storedScript.getClassBytes().get(mainClassName);
        final Class<?> mainClass       = installer.install(mainClassName, mainClassBytes);
        final Map<Integer, FunctionInitializer> initialzers = storedScript.getInitializers();

        installedClasses.put(mainClassName, mainClass);

        for (final Map.Entry<String, byte[]> entry : storedScript.getClassBytes().entrySet()) {
            final String className = entry.getKey();
            if (className.equals(mainClassName)) {
                continue;
            }
            final byte[] code = entry.getValue();

            installedClasses.put(className, installer.install(className, code));
        }

        installer.initialize(installedClasses.values(), source, constants);

        for (final Object constant : constants) {
            if (constant instanceof RecompilableScriptFunctionData) {
                final RecompilableScriptFunctionData data = (RecompilableScriptFunctionData) constant;
                data.initTransients(source, installer);
                if (initialzers != null) {
                    final FunctionInitializer initializer = initialzers.get(data.getFunctionNodeId());
                    initializer.setCode(installedClasses.get(initializer.getClassName()));
                    data.initializeCode(initializer);
                }
            }
        }

        return mainClass;
    }

    /**
     * Cache for compiled script classes.
     */
    @SuppressWarnings("serial")
    private static class ClassCache extends LinkedHashMap<Source, ClassReference> {
        private final int size;
        private final ReferenceQueue<Class<?>> queue;

        ClassCache(final int size) {
            super(size, 0.75f, true);
            this.size = size;
            this.queue = new ReferenceQueue<>();
        }

        void cache(final Source source, final Class<?> clazz) {
            put(source, new ClassReference(clazz, queue, source));
        }

        @Override
        protected boolean removeEldestEntry(final Map.Entry<Source, ClassReference> eldest) {
            return size() > size;
        }

        @Override
        public ClassReference get(final Object key) {
            for (ClassReference ref; (ref = (ClassReference)queue.poll()) != null; ) {
                remove(ref.source);
            }
            return super.get(key);
        }

    }

    private static class ClassReference extends SoftReference<Class<?>> {
        private final Source source;

        ClassReference(final Class<?> clazz, final ReferenceQueue<Class<?>> queue, final Source source) {
            super(clazz, queue);
            this.source = source;
        }
    }

    // Class cache management
    private Class<?> findCachedClass(final Source source) {
        final ClassReference ref = classCache == null ? null : classCache.get(source);
        return ref != null ? ref.get() : null;
    }

    private void cacheClass(final Source source, final Class<?> clazz) {
        if (classCache != null) {
            classCache.cache(source, clazz);
        }
    }

    // logging
    private final Map<String, DebugLogger> loggers = new HashMap<>();

    private void initLoggers() {
        ((Loggable)MethodHandleFactory.getFunctionality()).initLogger(this);
    }

    /**
     * Get a logger, given a loggable class
     * @param clazz a Loggable class
     * @return debuglogger associated with that class
     */
    public DebugLogger getLogger(final Class<? extends Loggable> clazz) {
        return getLogger(clazz, null);
    }

    /**
     * Get a logger, given a loggable class
     * @param clazz a Loggable class
     * @param initHook an init hook - if this is the first time the logger is created in the context, run the init hook
     * @return debuglogger associated with that class
     */
    public DebugLogger getLogger(final Class<? extends Loggable> clazz, final Consumer<DebugLogger> initHook) {
        final String name = getLoggerName(clazz);
        DebugLogger logger = loggers.get(name);
        if (logger == null) {
            if (!env.hasLogger(name)) {
                return DebugLogger.DISABLED_LOGGER;
            }
            final LoggerInfo info = env._loggers.get(name);
            logger = new DebugLogger(name, info.getLevel(), info.isQuiet());
            if (initHook != null) {
                initHook.accept(logger);
            }
            loggers.put(name, logger);
        }
        return logger;
    }

    /**
     * Given a Loggable class, weave debug info info a method handle for that logger.
     * Level.INFO is used
     *
     * @param clazz loggable
     * @param mh    method handle
     * @param text  debug printout to add
     *
     * @return instrumented method handle, or null if logger not enabled
     */
    public MethodHandle addLoggingToHandle(final Class<? extends Loggable> clazz, final MethodHandle mh, final Supplier<String> text) {
        return addLoggingToHandle(clazz, Level.INFO, mh, Integer.MAX_VALUE, false, text);
    }

    /**
     * Given a Loggable class, weave debug info info a method handle for that logger.
     *
     * @param clazz            loggable
     * @param level            log level
     * @param mh               method handle
     * @param paramStart       first parameter to print
     * @param printReturnValue should we print the return vaulue?
     * @param text             debug printout to add
     *
     * @return instrumented method handle, or null if logger not enabled
     */
    public MethodHandle addLoggingToHandle(final Class<? extends Loggable> clazz, final Level level, final MethodHandle mh, final int paramStart, final boolean printReturnValue, final Supplier<String> text) {
        final DebugLogger log = getLogger(clazz);
        if (log.isEnabled()) {
            return MethodHandleFactory.addDebugPrintout(log, level, mh, paramStart, printReturnValue, text.get());
        }
        return mh;
    }

    private static String getLoggerName(final Class<?> clazz) {
        Class<?> current = clazz;
        while (current != null) {
            final Logger log = current.getAnnotation(Logger.class);
            if (log != null) {
                assert !"".equals(log.name());
                return log.name();
            }
            current = current.getSuperclass();
        }
        assert false;
        return null;
    }

}
