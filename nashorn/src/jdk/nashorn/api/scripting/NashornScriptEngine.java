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

package jdk.nashorn.api.scripting;

import static jdk.nashorn.internal.runtime.ECMAErrors.referenceError;
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.Permissions;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.ProtectionDomain;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;
import javax.script.AbstractScriptEngine;
import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.ErrorManager;
import jdk.nashorn.internal.runtime.GlobalObject;
import jdk.nashorn.internal.runtime.Property;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.Source;
import jdk.nashorn.internal.runtime.linker.JavaAdapterFactory;
import jdk.nashorn.internal.runtime.options.Options;

/**
 * JSR-223 compliant script engine for Nashorn. Instances are not created directly, but rather returned through
 * {@link NashornScriptEngineFactory#getScriptEngine()}. Note that this engine implements the {@link Compilable} and
 * {@link Invocable} interfaces, allowing for efficient precompilation and repeated execution of scripts.
 * @see NashornScriptEngineFactory
 */

public final class NashornScriptEngine extends AbstractScriptEngine implements Compilable, Invocable {
    /**
     * Key used to associate Nashorn global object mirror with arbitrary Bindings instance.
     */
    public static final String NASHORN_GLOBAL = "nashorn.global";

    // commonly used access control context objects
    private static AccessControlContext createPermAccCtxt(final String permName) {
        final Permissions perms = new Permissions();
        perms.add(new RuntimePermission(permName));
        return new AccessControlContext(new ProtectionDomain[] { new ProtectionDomain(null, perms) });
    }

    private static final AccessControlContext CREATE_CONTEXT_ACC_CTXT = createPermAccCtxt(Context.NASHORN_CREATE_CONTEXT);
    private static final AccessControlContext CREATE_GLOBAL_ACC_CTXT  = createPermAccCtxt(Context.NASHORN_CREATE_GLOBAL);

    // the factory that created this engine
    private final ScriptEngineFactory factory;
    // underlying nashorn Context - 1:1 with engine instance
    private final Context             nashornContext;
    // do we want to share single Nashorn global instance across ENGINE_SCOPEs?
    private final boolean             _global_per_engine;
    // This is the initial default Nashorn global object.
    // This is used as "shared" global if above option is true.
    private final ScriptObject        global;
    // initialized bit late to be made 'final'.
    // Property object for "context" property of global object.
    private volatile Property         contextProperty;

    // default options passed to Nashorn Options object
    private static final String[] DEFAULT_OPTIONS = new String[] { "-scripting", "-doe" };

    // Nashorn script engine error message management
    private static final String MESSAGES_RESOURCE = "jdk.nashorn.api.scripting.resources.Messages";

    private static final ResourceBundle MESSAGES_BUNDLE;
    static {
        MESSAGES_BUNDLE = ResourceBundle.getBundle(MESSAGES_RESOURCE, Locale.getDefault());
    }

    // helper to get Nashorn script engine error message
    private static String getMessage(final String msgId, final String... args) {
        try {
            return new MessageFormat(MESSAGES_BUNDLE.getString(msgId)).format(args);
        } catch (final java.util.MissingResourceException e) {
            throw new RuntimeException("no message resource found for message id: "+ msgId);
        }
    }

    // load engine.js and return content as a char[]
    @SuppressWarnings("resource")
    private static char[] loadEngineJSSource() {
        final String script = "resources/engine.js";
        try {
            final InputStream is = AccessController.doPrivileged(
                    new PrivilegedExceptionAction<InputStream>() {
                        @Override
                        public InputStream run() throws Exception {
                            final URL url = NashornScriptEngine.class.getResource(script);
                            return url.openStream();
                        }
                    });
            return Source.readFully(new InputStreamReader(is));
        } catch (final PrivilegedActionException | IOException e) {
            if (Context.DEBUG) {
                e.printStackTrace();
            }
            throw new RuntimeException(e);
        }
    }

    // Source object for engine.js
    private static final Source ENGINE_SCRIPT_SRC = new Source(NashornException.ENGINE_SCRIPT_SOURCE_NAME, loadEngineJSSource());

    NashornScriptEngine(final NashornScriptEngineFactory factory, final ClassLoader appLoader) {
        this(factory, DEFAULT_OPTIONS, appLoader);
    }

    NashornScriptEngine(final NashornScriptEngineFactory factory, final String[] args, final ClassLoader appLoader) {
        this.factory = factory;
        final Options options = new Options("nashorn");
        options.process(args);

        // throw ParseException on first error from script
        final ErrorManager errMgr = new Context.ThrowErrorManager();
        // create new Nashorn Context
        this.nashornContext = AccessController.doPrivileged(new PrivilegedAction<Context>() {
            @Override
            public Context run() {
                try {
                    return new Context(options, errMgr, appLoader);
                } catch (final RuntimeException e) {
                    if (Context.DEBUG) {
                        e.printStackTrace();
                    }
                    throw e;
                }
            }
        }, CREATE_CONTEXT_ACC_CTXT);

        // cache this option that is used often
        this._global_per_engine = nashornContext.getEnv()._global_per_engine;

        // create new global object
        this.global = createNashornGlobal(context);
        // set the default ENGINE_SCOPE object for the default context
        context.setBindings(new ScriptObjectMirror(global, global), ScriptContext.ENGINE_SCOPE);
    }

    @Override
    public Object eval(final Reader reader, final ScriptContext ctxt) throws ScriptException {
        try {
            if (reader instanceof URLReader) {
                final URL url = ((URLReader)reader).getURL();
                final Charset cs = ((URLReader)reader).getCharset();
                return evalImpl(compileImpl(new Source(url.toString(), url, cs), ctxt), ctxt);
            }
            return evalImpl(Source.readFully(reader), ctxt);
        } catch (final IOException e) {
            throw new ScriptException(e);
        }
    }

    @Override
    public Object eval(final String script, final ScriptContext ctxt) throws ScriptException {
        return evalImpl(script.toCharArray(), ctxt);
    }

    @Override
    public ScriptEngineFactory getFactory() {
        return factory;
    }

    @Override
    public Bindings createBindings() {
        if (_global_per_engine) {
            // just create normal SimpleBindings.
            // We use same 'global' for all Bindings.
            return new SimpleBindings();
        }
        return createGlobalMirror(null);
    }

    // Compilable methods

    @Override
    public CompiledScript compile(final Reader reader) throws ScriptException {
        try {
            return asCompiledScript(compileImpl(Source.readFully(reader), context));
        } catch (final IOException e) {
            throw new ScriptException(e);
        }
    }

    @Override
    public CompiledScript compile(final String str) throws ScriptException {
        return asCompiledScript(compileImpl(str.toCharArray(), context));
    }

    // Invocable methods

    @Override
    public Object invokeFunction(final String name, final Object... args)
            throws ScriptException, NoSuchMethodException {
        return invokeImpl(null, name, args);
    }

    @Override
    public Object invokeMethod(final Object thiz, final String name, final Object... args)
            throws ScriptException, NoSuchMethodException {
        if (thiz == null) {
            throw new IllegalArgumentException(getMessage("thiz.cannot.be.null"));
        }
        return invokeImpl(thiz, name, args);
    }

    @Override
    public <T> T getInterface(final Class<T> clazz) {
        return getInterfaceInner(null, clazz);
    }

    @Override
    public <T> T getInterface(final Object thiz, final Class<T> clazz) {
        if (thiz == null) {
            throw new IllegalArgumentException(getMessage("thiz.cannot.be.null"));
        }
        return getInterfaceInner(thiz, clazz);
    }

    // These are called from the "engine.js" script

    /**
     * This hook is used to search js global variables exposed from Java code.
     *
     * @param self 'this' passed from the script
     * @param ctxt current ScriptContext in which name is searched
     * @param name name of the variable searched
     * @return the value of the named variable
     */
    public Object __noSuchProperty__(final Object self, final ScriptContext ctxt, final String name) {
        if (ctxt != null) {
            final int scope = ctxt.getAttributesScope(name);
            final ScriptObject ctxtGlobal = getNashornGlobalFrom(ctxt);
            if (scope != -1) {
                return ScriptObjectMirror.unwrap(ctxt.getAttribute(name, scope), ctxtGlobal);
            }

            if (self == UNDEFINED) {
                // scope access and so throw ReferenceError
                throw referenceError(ctxtGlobal, "not.defined", name);
            }
        }

        return UNDEFINED;
    }

    // Implementation only below this point

    private <T> T getInterfaceInner(final Object thiz, final Class<T> clazz) {
        if (clazz == null || !clazz.isInterface()) {
            throw new IllegalArgumentException(getMessage("interface.class.expected"));
        }

        // perform security access check as early as possible
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            if (! Modifier.isPublic(clazz.getModifiers())) {
                throw new SecurityException(getMessage("implementing.non.public.interface", clazz.getName()));
            }
            Context.checkPackageAccess(clazz.getName());
        }

        ScriptObject realSelf = null;
        ScriptObject realGlobal = null;
        if(thiz == null) {
            // making interface out of global functions
            realSelf = realGlobal = getNashornGlobalFrom(context);
        } else if (thiz instanceof ScriptObjectMirror) {
            final ScriptObjectMirror mirror = (ScriptObjectMirror)thiz;
            realSelf = mirror.getScriptObject();
            realGlobal = mirror.getHomeGlobal();
            if (! isOfContext(realGlobal, nashornContext)) {
                throw new IllegalArgumentException(getMessage("script.object.from.another.engine"));
            }
        } else if (thiz instanceof ScriptObject) {
            // called from script code.
            realSelf = (ScriptObject)thiz;
            realGlobal = Context.getGlobal();
            if (realGlobal == null) {
                throw new IllegalArgumentException(getMessage("no.current.nashorn.global"));
            }

            if (! isOfContext(realGlobal, nashornContext)) {
                throw new IllegalArgumentException(getMessage("script.object.from.another.engine"));
            }
        }

        if (realSelf == null) {
            throw new IllegalArgumentException(getMessage("interface.on.non.script.object"));
        }

        try {
            final ScriptObject oldGlobal = Context.getGlobal();
            final boolean globalChanged = (oldGlobal != realGlobal);
            try {
                if (globalChanged) {
                    Context.setGlobal(realGlobal);
                }

                if (! isInterfaceImplemented(clazz, realSelf)) {
                    return null;
                }
                return clazz.cast(JavaAdapterFactory.getConstructor(realSelf.getClass(), clazz).invoke(realSelf));
            } finally {
                if (globalChanged) {
                    Context.setGlobal(oldGlobal);
                }
            }
        } catch(final RuntimeException|Error e) {
            throw e;
        } catch(final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    // Retrieve nashorn Global object for a given ScriptContext object
    private ScriptObject getNashornGlobalFrom(final ScriptContext ctxt) {
        if (_global_per_engine) {
            // shared single global object for all ENGINE_SCOPE Bindings
            return global;
        }

        final Bindings bindings = ctxt.getBindings(ScriptContext.ENGINE_SCOPE);
        // is this Nashorn's own Bindings implementation?
        if (bindings instanceof ScriptObjectMirror) {
            final ScriptObject sobj = globalFromMirror((ScriptObjectMirror)bindings);
            if (sobj != null) {
                return sobj;
            }
        }

        // Arbitrary user Bindings implementation. Look for NASHORN_GLOBAL in it!
        Object scope = bindings.get(NASHORN_GLOBAL);
        if (scope instanceof ScriptObjectMirror) {
            final ScriptObject sobj = globalFromMirror((ScriptObjectMirror)scope);
            if (sobj != null) {
                return sobj;
            }
        }

        // We didn't find associated nashorn global mirror in the Bindings given!
        // Create new global instance mirror and associate with the Bindings.
        final ScriptObjectMirror mirror = createGlobalMirror(ctxt);
        bindings.put(NASHORN_GLOBAL, mirror);
        return mirror.getScriptObject();
    }

    // Retrieve nashorn Global object from a given ScriptObjectMirror
    private ScriptObject globalFromMirror(final ScriptObjectMirror mirror) {
        ScriptObject sobj = mirror.getScriptObject();
        if (sobj instanceof GlobalObject && isOfContext(sobj, nashornContext)) {
            return sobj;
        }

        return null;
    }

    // Create a new ScriptObjectMirror wrapping a newly created Nashorn Global object
    private ScriptObjectMirror createGlobalMirror(final ScriptContext ctxt) {
        final ScriptObject newGlobal = createNashornGlobal(ctxt);
        return new ScriptObjectMirror(newGlobal, newGlobal);
    }

    // Create a new Nashorn Global object
    private ScriptObject createNashornGlobal(final ScriptContext ctxt) {
        final ScriptObject newGlobal = AccessController.doPrivileged(new PrivilegedAction<ScriptObject>() {
            @Override
            public ScriptObject run() {
                try {
                    return nashornContext.newGlobal();
                } catch (final RuntimeException e) {
                    if (Context.DEBUG) {
                        e.printStackTrace();
                    }
                    throw e;
                }
            }
        }, CREATE_GLOBAL_ACC_CTXT);

        nashornContext.initGlobal(newGlobal);

        final int NON_ENUMERABLE_CONSTANT = Property.NOT_ENUMERABLE | Property.NOT_CONFIGURABLE | Property.NOT_WRITABLE;
        // current ScriptContext exposed as "context"
        // "context" is non-writable from script - but script engine still
        // needs to set it and so save the context Property object
        contextProperty = newGlobal.addOwnProperty("context", NON_ENUMERABLE_CONSTANT, null);
        // current ScriptEngine instance exposed as "engine". We added @SuppressWarnings("LeakingThisInConstructor") as
        // NetBeans identifies this assignment as such a leak - this is a false positive as we're setting this property
        // in the Global of a Context we just created - both the Context and the Global were just created and can not be
        // seen from another thread outside of this constructor.
        newGlobal.addOwnProperty("engine", NON_ENUMERABLE_CONSTANT, this);
        // global script arguments with undefined value
        newGlobal.addOwnProperty("arguments", Property.NOT_ENUMERABLE, UNDEFINED);
        // file name default is null
        newGlobal.addOwnProperty(ScriptEngine.FILENAME, Property.NOT_ENUMERABLE, null);
        // evaluate engine.js initialization script this new global object
        try {
            evalImpl(compileImpl(ENGINE_SCRIPT_SRC, newGlobal), ctxt, newGlobal);
        } catch (final ScriptException exp) {
            throw new RuntimeException(exp);
        }
        return newGlobal;
    }

    // scripts should see "context" and "engine" as variables in the given global object
    private void setContextVariables(final ScriptObject ctxtGlobal, final ScriptContext ctxt) {
        // set "context" global variable via contextProperty - because this
        // property is non-writable
        contextProperty.setObjectValue(ctxtGlobal, ctxtGlobal, ctxt, false);
        Object args = ScriptObjectMirror.unwrap(ctxt.getAttribute("arguments"), ctxtGlobal);
        if (args == null || args == UNDEFINED) {
            args = ScriptRuntime.EMPTY_ARRAY;
        }
        // if no arguments passed, expose it
        if (! (args instanceof ScriptObject)) {
            args = ((GlobalObject)ctxtGlobal).wrapAsObject(args);
            ctxtGlobal.set("arguments", args, false);
        }
    }

    private Object invokeImpl(final Object selfObject, final String name, final Object... args) throws ScriptException, NoSuchMethodException {
        name.getClass(); // null check

        ScriptObjectMirror selfMirror = null;
        if (selfObject instanceof ScriptObjectMirror) {
            selfMirror = (ScriptObjectMirror)selfObject;
            if (! isOfContext(selfMirror.getHomeGlobal(), nashornContext)) {
                throw new IllegalArgumentException(getMessage("script.object.from.another.engine"));
            }
        } else if (selfObject instanceof ScriptObject) {
            // invokeMethod called from script code - in which case we may get 'naked' ScriptObject
            // Wrap it with oldGlobal to make a ScriptObjectMirror for the same.
            final ScriptObject oldGlobal = Context.getGlobal();
            if (oldGlobal == null) {
                throw new IllegalArgumentException(getMessage("no.current.nashorn.global"));
            }

            if (! isOfContext(oldGlobal, nashornContext)) {
                throw new IllegalArgumentException(getMessage("script.object.from.another.engine"));
            }

            selfMirror = (ScriptObjectMirror)ScriptObjectMirror.wrap(selfObject, oldGlobal);
        } else if (selfObject == null) {
            // selfObject is null => global function call
            final ScriptObject ctxtGlobal = getNashornGlobalFrom(context);
            selfMirror = (ScriptObjectMirror)ScriptObjectMirror.wrap(ctxtGlobal, ctxtGlobal);
        }

        if (selfMirror != null) {
            try {
                return ScriptObjectMirror.translateUndefined(selfMirror.call(name, args));
            } catch (final Exception e) {
                final Throwable cause = e.getCause();
                if (cause instanceof NoSuchMethodException) {
                    throw (NoSuchMethodException)cause;
                }
                throwAsScriptException(e);
                throw new AssertionError("should not reach here");
            }
        }

        // Non-script object passed as selfObject
        throw new IllegalArgumentException(getMessage("interface.on.non.script.object"));
    }

    private Object evalImpl(final char[] buf, final ScriptContext ctxt) throws ScriptException {
        return evalImpl(compileImpl(buf, ctxt), ctxt);
    }

    private Object evalImpl(final ScriptFunction script, final ScriptContext ctxt) throws ScriptException {
        return evalImpl(script, ctxt, getNashornGlobalFrom(ctxt));
    }

    private Object evalImpl(final ScriptFunction script, final ScriptContext ctxt, final ScriptObject ctxtGlobal) throws ScriptException {
        if (script == null) {
            return null;
        }
        final ScriptObject oldGlobal = Context.getGlobal();
        final boolean globalChanged = (oldGlobal != ctxtGlobal);
        try {
            if (globalChanged) {
                Context.setGlobal(ctxtGlobal);
            }

            // set ScriptContext variables if ctxt is non-null
            if (ctxt != null) {
                setContextVariables(ctxtGlobal, ctxt);
            }
            return ScriptObjectMirror.translateUndefined(ScriptObjectMirror.wrap(ScriptRuntime.apply(script, ctxtGlobal), ctxtGlobal));
        } catch (final Exception e) {
            throwAsScriptException(e);
            throw new AssertionError("should not reach here");
        } finally {
            if (globalChanged) {
                Context.setGlobal(oldGlobal);
            }
        }
    }

    private static void throwAsScriptException(final Exception e) throws ScriptException {
        if (e instanceof ScriptException) {
            throw (ScriptException)e;
        } else if (e instanceof NashornException) {
            final NashornException ne = (NashornException)e;
            final ScriptException se = new ScriptException(
                ne.getMessage(), ne.getFileName(),
                ne.getLineNumber(), ne.getColumnNumber());
            se.initCause(e);
            throw se;
        } else if (e instanceof RuntimeException) {
            throw (RuntimeException)e;
        } else {
            // wrap any other exception as ScriptException
            throw new ScriptException(e);
        }
    }

    private CompiledScript asCompiledScript(final ScriptFunction script) {
        return new CompiledScript() {
            @Override
            public Object eval(final ScriptContext ctxt) throws ScriptException {
                return evalImpl(script, ctxt);
            }
            @Override
            public ScriptEngine getEngine() {
                return NashornScriptEngine.this;
            }
        };
    }

    private ScriptFunction compileImpl(final char[] buf, final ScriptContext ctxt) throws ScriptException {
        final Object val = ctxt.getAttribute(ScriptEngine.FILENAME);
        final String fileName = (val != null) ? val.toString() : "<eval>";
        return compileImpl(new Source(fileName, buf), ctxt);
    }

    private ScriptFunction compileImpl(final Source source, final ScriptContext ctxt) throws ScriptException {
        return compileImpl(source, getNashornGlobalFrom(ctxt));
    }

    private ScriptFunction compileImpl(final Source source, final ScriptObject newGlobal) throws ScriptException {
        final ScriptObject oldGlobal = Context.getGlobal();
        final boolean globalChanged = (oldGlobal != newGlobal);
        try {
            if (globalChanged) {
                Context.setGlobal(newGlobal);
            }

            return nashornContext.compileScript(source, newGlobal);
        } catch (final Exception e) {
            throwAsScriptException(e);
            throw new AssertionError("should not reach here");
        } finally {
            if (globalChanged) {
                Context.setGlobal(oldGlobal);
            }
        }
    }

    private static boolean isInterfaceImplemented(final Class<?> iface, final ScriptObject sobj) {
        for (final Method method : iface.getMethods()) {
            // ignore methods of java.lang.Object class
            if (method.getDeclaringClass() == Object.class) {
                continue;
            }

            Object obj = sobj.get(method.getName());
            if (! (obj instanceof ScriptFunction)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isOfContext(final ScriptObject global, final Context context) {
        assert global instanceof GlobalObject: "Not a Global object";
        return ((GlobalObject)global).isOfContext(context);
    }
}
