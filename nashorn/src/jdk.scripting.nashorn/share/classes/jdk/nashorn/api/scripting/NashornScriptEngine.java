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

import static jdk.nashorn.internal.runtime.Source.sourceFor;

import java.io.IOException;
import java.io.Reader;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.Permissions;
import java.security.PrivilegedAction;
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
import jdk.nashorn.internal.objects.Global;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.ErrorManager;
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
    private final Global              global;

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

    NashornScriptEngine(final NashornScriptEngineFactory factory, final String[] args, final ClassLoader appLoader, final ClassFilter classFilter) {
        assert args != null : "null argument array";
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
                    return new Context(options, errMgr, appLoader, classFilter);
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
        return evalImpl(makeSource(reader, ctxt), ctxt);
    }

    @Override
    public Object eval(final String script, final ScriptContext ctxt) throws ScriptException {
        return evalImpl(makeSource(script, ctxt), ctxt);
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
        return asCompiledScript(makeSource(reader, context));
    }

    @Override
    public CompiledScript compile(final String str) throws ScriptException {
        return asCompiledScript(makeSource(str, context));
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

    // Implementation only below this point

    private static Source makeSource(final Reader reader, final ScriptContext ctxt) throws ScriptException {
        try {
            return sourceFor(getScriptName(ctxt), reader);
        } catch (final IOException e) {
            throw new ScriptException(e);
        }
    }

    private static Source makeSource(final String src, final ScriptContext ctxt) {
        return sourceFor(getScriptName(ctxt), src);
    }

    private static String getScriptName(final ScriptContext ctxt) {
        final Object val = ctxt.getAttribute(ScriptEngine.FILENAME);
        return (val != null) ? val.toString() : "<eval>";
    }

    private <T> T getInterfaceInner(final Object thiz, final Class<T> clazz) {
        assert !(thiz instanceof ScriptObject) : "raw ScriptObject not expected here";

        if (clazz == null || !clazz.isInterface()) {
            throw new IllegalArgumentException(getMessage("interface.class.expected"));
        }

        // perform security access check as early as possible
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            if (! Modifier.isPublic(clazz.getModifiers())) {
                throw new SecurityException(getMessage("implementing.non.public.interface", clazz.getName()));
            }
            Context.checkPackageAccess(clazz);
        }

        ScriptObject realSelf = null;
        Global realGlobal = null;
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
        }

        if (realSelf == null) {
            throw new IllegalArgumentException(getMessage("interface.on.non.script.object"));
        }

        try {
            final Global oldGlobal = Context.getGlobal();
            final boolean globalChanged = (oldGlobal != realGlobal);
            try {
                if (globalChanged) {
                    Context.setGlobal(realGlobal);
                }

                if (! isInterfaceImplemented(clazz, realSelf)) {
                    return null;
                }
                return clazz.cast(JavaAdapterFactory.getConstructor(realSelf.getClass(), clazz,
                        MethodHandles.publicLookup()).invoke(realSelf));
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
    private Global getNashornGlobalFrom(final ScriptContext ctxt) {
        if (_global_per_engine) {
            // shared single global object for all ENGINE_SCOPE Bindings
            return global;
        }

        final Bindings bindings = ctxt.getBindings(ScriptContext.ENGINE_SCOPE);
        // is this Nashorn's own Bindings implementation?
        if (bindings instanceof ScriptObjectMirror) {
            final Global glob = globalFromMirror((ScriptObjectMirror)bindings);
            if (glob != null) {
                return glob;
            }
        }

        // Arbitrary user Bindings implementation. Look for NASHORN_GLOBAL in it!
        final Object scope = bindings.get(NASHORN_GLOBAL);
        if (scope instanceof ScriptObjectMirror) {
            final Global glob = globalFromMirror((ScriptObjectMirror)scope);
            if (glob != null) {
                return glob;
            }
        }

        // We didn't find associated nashorn global mirror in the Bindings given!
        // Create new global instance mirror and associate with the Bindings.
        final ScriptObjectMirror mirror = createGlobalMirror(ctxt);
        bindings.put(NASHORN_GLOBAL, mirror);
        return mirror.getHomeGlobal();
    }

    // Retrieve nashorn Global object from a given ScriptObjectMirror
    private Global globalFromMirror(final ScriptObjectMirror mirror) {
        final ScriptObject sobj = mirror.getScriptObject();
        if (sobj instanceof Global && isOfContext((Global)sobj, nashornContext)) {
            return (Global)sobj;
        }

        return null;
    }

    // Create a new ScriptObjectMirror wrapping a newly created Nashorn Global object
    private ScriptObjectMirror createGlobalMirror(final ScriptContext ctxt) {
        final Global newGlobal = createNashornGlobal(ctxt);
        return new ScriptObjectMirror(newGlobal, newGlobal);
    }

    // Create a new Nashorn Global object
    private Global createNashornGlobal(final ScriptContext ctxt) {
        final Global newGlobal = AccessController.doPrivileged(new PrivilegedAction<Global>() {
            @Override
            public Global run() {
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

        nashornContext.initGlobal(newGlobal, this);
        newGlobal.setScriptContext(ctxt);

        return newGlobal;
    }

    private Object invokeImpl(final Object selfObject, final String name, final Object... args) throws ScriptException, NoSuchMethodException {
        name.getClass(); // null check
        assert !(selfObject instanceof ScriptObject) : "raw ScriptObject not expected here";

        Global invokeGlobal = null;
        ScriptObjectMirror selfMirror = null;
        if (selfObject instanceof ScriptObjectMirror) {
            selfMirror = (ScriptObjectMirror)selfObject;
            if (! isOfContext(selfMirror.getHomeGlobal(), nashornContext)) {
                throw new IllegalArgumentException(getMessage("script.object.from.another.engine"));
            }
            invokeGlobal = selfMirror.getHomeGlobal();
        } else if (selfObject == null) {
            // selfObject is null => global function call
            final Global ctxtGlobal = getNashornGlobalFrom(context);
            invokeGlobal = ctxtGlobal;
            selfMirror = (ScriptObjectMirror)ScriptObjectMirror.wrap(ctxtGlobal, ctxtGlobal);
        }

        if (selfMirror != null) {
            try {
                return ScriptObjectMirror.translateUndefined(selfMirror.callMember(name, args));
            } catch (final Exception e) {
                final Throwable cause = e.getCause();
                if (cause instanceof NoSuchMethodException) {
                    throw (NoSuchMethodException)cause;
                }
                throwAsScriptException(e, invokeGlobal);
                throw new AssertionError("should not reach here");
            }
        }

        // Non-script object passed as selfObject
        throw new IllegalArgumentException(getMessage("interface.on.non.script.object"));
    }

    private Object evalImpl(final Source src, final ScriptContext ctxt) throws ScriptException {
        return evalImpl(compileImpl(src, ctxt), ctxt);
    }

    private Object evalImpl(final ScriptFunction script, final ScriptContext ctxt) throws ScriptException {
        return evalImpl(script, ctxt, getNashornGlobalFrom(ctxt));
    }

    private static Object evalImpl(final Context.MultiGlobalCompiledScript mgcs, final ScriptContext ctxt, final Global ctxtGlobal) throws ScriptException {
        final Global oldGlobal = Context.getGlobal();
        final boolean globalChanged = (oldGlobal != ctxtGlobal);
        try {
            if (globalChanged) {
                Context.setGlobal(ctxtGlobal);
            }

            final ScriptFunction script = mgcs.getFunction(ctxtGlobal);
            ctxtGlobal.setScriptContext(ctxt);
            return ScriptObjectMirror.translateUndefined(ScriptObjectMirror.wrap(ScriptRuntime.apply(script, ctxtGlobal), ctxtGlobal));
        } catch (final Exception e) {
            throwAsScriptException(e, ctxtGlobal);
            throw new AssertionError("should not reach here");
        } finally {
            if (globalChanged) {
                Context.setGlobal(oldGlobal);
            }
        }
    }

    private static Object evalImpl(final ScriptFunction script, final ScriptContext ctxt, final Global ctxtGlobal) throws ScriptException {
        if (script == null) {
            return null;
        }
        final Global oldGlobal = Context.getGlobal();
        final boolean globalChanged = (oldGlobal != ctxtGlobal);
        try {
            if (globalChanged) {
                Context.setGlobal(ctxtGlobal);
            }

            ctxtGlobal.setScriptContext(ctxt);
            return ScriptObjectMirror.translateUndefined(ScriptObjectMirror.wrap(ScriptRuntime.apply(script, ctxtGlobal), ctxtGlobal));
        } catch (final Exception e) {
            throwAsScriptException(e, ctxtGlobal);
            throw new AssertionError("should not reach here");
        } finally {
            if (globalChanged) {
                Context.setGlobal(oldGlobal);
            }
        }
    }

    private static void throwAsScriptException(final Exception e, final Global global) throws ScriptException {
        if (e instanceof ScriptException) {
            throw (ScriptException)e;
        } else if (e instanceof NashornException) {
            final NashornException ne = (NashornException)e;
            final ScriptException se = new ScriptException(
                ne.getMessage(), ne.getFileName(),
                ne.getLineNumber(), ne.getColumnNumber());
            ne.initEcmaError(global);
            se.initCause(e);
            throw se;
        } else if (e instanceof RuntimeException) {
            throw (RuntimeException)e;
        } else {
            // wrap any other exception as ScriptException
            throw new ScriptException(e);
        }
    }

    private CompiledScript asCompiledScript(final Source source) throws ScriptException {
        final Context.MultiGlobalCompiledScript mgcs;
        final ScriptFunction func;
        final Global oldGlobal = Context.getGlobal();
        final Global newGlobal = getNashornGlobalFrom(context);
        final boolean globalChanged = (oldGlobal != newGlobal);
        try {
            if (globalChanged) {
                Context.setGlobal(newGlobal);
            }

            mgcs = nashornContext.compileScript(source);
            func = mgcs.getFunction(newGlobal);
        } catch (final Exception e) {
            throwAsScriptException(e, newGlobal);
            throw new AssertionError("should not reach here");
        } finally {
            if (globalChanged) {
                Context.setGlobal(oldGlobal);
            }
        }

        return new CompiledScript() {
            @Override
            public Object eval(final ScriptContext ctxt) throws ScriptException {
                final Global globalObject = getNashornGlobalFrom(ctxt);
                // Are we running the script in the same global in which it was compiled?
                if (func.getScope() == globalObject) {
                    return evalImpl(func, ctxt, globalObject);
                }

                // different global
                return evalImpl(mgcs, ctxt, globalObject);
            }
            @Override
            public ScriptEngine getEngine() {
                return NashornScriptEngine.this;
            }
        };
    }

    private ScriptFunction compileImpl(final Source source, final ScriptContext ctxt) throws ScriptException {
        return compileImpl(source, getNashornGlobalFrom(ctxt));
    }

    private ScriptFunction compileImpl(final Source source, final Global newGlobal) throws ScriptException {
        final Global oldGlobal = Context.getGlobal();
        final boolean globalChanged = (oldGlobal != newGlobal);
        try {
            if (globalChanged) {
                Context.setGlobal(newGlobal);
            }

            return nashornContext.compileScript(source, newGlobal);
        } catch (final Exception e) {
            throwAsScriptException(e, newGlobal);
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

            // skip check for default methods - non-abstract, interface methods
            if (! Modifier.isAbstract(method.getModifiers())) {
                continue;
            }

            final Object obj = sobj.get(method.getName());
            if (! (obj instanceof ScriptFunction)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isOfContext(final Global global, final Context context) {
        return global.isOfContext(context);
    }
}
