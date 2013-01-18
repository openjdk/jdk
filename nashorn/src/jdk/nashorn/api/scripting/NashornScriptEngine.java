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
import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;
import static jdk.nashorn.internal.runtime.linker.Lookup.MH;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
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

    private final ScriptEngineFactory factory;
    private final Context             nashornContext;
    private final ScriptObject        global;

    // default options passed to Nashorn Options object
    private static final String[] DEFAULT_OPTIONS = new String[] { "-scripting", "-af", "-doe" };

    NashornScriptEngine(final NashornScriptEngineFactory factory) {
        this(factory, DEFAULT_OPTIONS);
    }

    @SuppressWarnings("LeakingThisInConstructor")
    NashornScriptEngine(final NashornScriptEngineFactory factory, final String[] args) {
        this.factory = factory;
        final Options options = new Options("nashorn");
        options.process(args);

        // throw ParseException on first error from script
        final ErrorManager errors = new Context.ThrowErrorManager();
        // create new Nashorn Context
        this.nashornContext = AccessController.doPrivileged(new PrivilegedAction<Context>() {
            @Override
            public Context run() {
                try {
                    return new Context(options, errors);
                } catch (final RuntimeException e) {
                    if (Context.DEBUG) {
                        e.printStackTrace();
                    }
                    throw e;
                }
            }
        });

        // create new global object
        this.global =  AccessController.doPrivileged(new PrivilegedAction<ScriptObject>() {
            @Override
            public ScriptObject run() {
                try {
                    return nashornContext.createGlobal();
                } catch (final RuntimeException e) {
                    if (Context.DEBUG) {
                        e.printStackTrace();
                    }
                    throw e;
                }
            }
        });

        // current ScriptContext exposed as "context"
        global.addOwnProperty("context", Property.NOT_ENUMERABLE, context);
        // current ScriptEngine instance exposed as "engine". We added @SuppressWarnings("LeakingThisInConstructor") as
        // NetBeans identifies this assignment as such a leak - this is a false positive as we're setting this property
        // in the Global of a Context we just created - both the Context and the Global were just created and can not be
        // seen from another thread outside of this constructor.
        global.addOwnProperty("engine", Property.NOT_ENUMERABLE, this);
        // global script arguments
        global.addOwnProperty("arguments", Property.NOT_ENUMERABLE, UNDEFINED);

        // evaluate engine initial script
        try {
            evalEngineScript();
        } catch (final ScriptException e) {
            if (Context.DEBUG) {
                e.printStackTrace();
            }
            throw new RuntimeException(e);
        }
    }

    @Override
    public Object eval(final Reader reader, final ScriptContext ctxt) throws ScriptException {
        try {
            return evalImpl(Source.readFully(reader), ctxt);
        } catch (final IOException e) {
            throw new ScriptException(e);
        }
    }

    @Override
    public Object eval(final String script, final ScriptContext ctxt) throws ScriptException {
        return evalImpl(script.toCharArray(), ctxt);
    }

    /*
     * FIXME: This is not exactly right. Script execution should actually
     * put the variables in ENGINE_SCOPE bindings. But, it is difficult
     * (not possible?) with the way ScriptObject is implemented. So, for now
     * giving access to script variables by accessing it from "globals". This
     * way at least engine.get("foo") will work whenever "foo" is a global var
     * defined by eval'ed scripts.
     */
    @Override
    public Object get(final String key) {
        Object value = super.get(key);
        if (value == null) {
            value = ScriptObjectMirror.wrap(global.get(key), global);
        }
        return (value == UNDEFINED) ? null : value;
    }

    @Override
    public ScriptEngineFactory getFactory() {
        return factory;
    }

    @Override
    public Bindings createBindings() {
        return new SimpleBindings();
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
    public Object invokeMethod(final Object self, final String name, final Object... args)
            throws ScriptException, NoSuchMethodException {
        if (self == null) {
            throw new IllegalArgumentException("script object can not be null");
        }
        return invokeImpl(self, name, args);
    }

    private <T> T getInterfaceInner(final Object self, final Class<T> clazz) {
        final Object realSelf;
        if(self == null) {
            realSelf = global;
        } else if (!(self instanceof ScriptObject)) {
            realSelf = ScriptObjectMirror.unwrap(self, global);
        } else {
            realSelf = self;
        }
        try {
            final ScriptObject oldGlobal = Context.getGlobal();
            try {
                if(oldGlobal != global) {
                    setNashornGlobal(global);
                }
                return clazz.cast(JavaAdapterFactory.getConstructor(realSelf.getClass(), clazz).invoke(realSelf));
            } finally {
                if(oldGlobal != global) {
                    setNashornGlobal(oldGlobal);
                }
            }
        } catch(final RuntimeException|Error e) {
            throw e;
        } catch(final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @Override
    public <T> T getInterface(final Class<T> clazz) {
        return getInterfaceInner(null, clazz);
    }

    @Override
    public <T> T getInterface(final Object self, final Class<T> clazz) {
        if (self == null) {
            throw new IllegalArgumentException("script object can not be null");
        }
        return getInterfaceInner(self, clazz);
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
        final int scope = ctxt.getAttributesScope(name);
        if (scope != -1) {
            return ScriptObjectMirror.unwrap(ctxt.getAttribute(name, scope), global);
        }

        if (self == UNDEFINED) {
            // scope access and so throw ReferenceError
            referenceError(global, "not.defined", name);
        }

        return UNDEFINED;
    }

    /**
     * This hook is used to call js global functions exposed from Java code.
     *
     * @param self 'this' passed from the script
     * @param ctxt current ScriptContext in which method is searched
     * @param name name of the method
     * @param args arguments to be passed to the method
     * @return return value of the called method
     */
    public Object __noSuchMethod__(final Object self, final ScriptContext ctxt, final String name, final Object args) {
        final int scope = ctxt.getAttributesScope(name);
        Object value;

        if (scope != -1) {
            value = ctxt.getAttribute(name, scope);
        } else {
            if (self == UNDEFINED) {
                referenceError(global, "not.defined", name);
            } else {
                typeError(global, "no.such.function", name, ScriptRuntime.safeToString(global));
            }
            return UNDEFINED;
        }

        value = ScriptObjectMirror.unwrap(value, global);

        if (value instanceof Method) {
            final Method method = (Method) value;
            final int    mods   = method.getModifiers();
            if (Modifier.isStatic(mods) && Modifier.isPublic(mods)) {
               value = MH.find((Method)value);
            }
        }

        if (value instanceof MethodHandle) {
            value = ((GlobalObject)global).newScriptFunction(name, (MethodHandle)value, null, false);
        }

        if (!(value instanceof ScriptFunction)) {
            typeError(global, "not.a.function", name);
        }

        if (value instanceof ScriptFunction) {
            return ScriptObjectMirror.unwrap(ScriptRuntime.apply((ScriptFunction)value, global, args), global);
        }

        typeError(global, "not.a.function", ScriptRuntime.safeToString(name));

        return UNDEFINED;
    }

    private void evalEngineScript() throws ScriptException {
        evalSupportScript("resources/engine.js");
    }

    private void evalSupportScript(final String script) throws ScriptException {
        try {
            final InputStream is = AccessController.doPrivileged(
                    new PrivilegedExceptionAction<InputStream>() {
                        public InputStream run() throws Exception {
                            final URL url = NashornScriptEngine.class.getResource(script);
                            return url.openStream();
                        }
                    });
            put(ScriptEngine.FILENAME, "<engine>:" + script);
            try (final InputStreamReader isr = new InputStreamReader(is)) {
                eval(isr);
            }
        } catch (final PrivilegedActionException | IOException e) {
            throw new ScriptException(e);
        } finally {
            put(ScriptEngine.FILENAME, null);
        }
    }

    // scripts should see "context" and "engine" as variables
    private void setContextVariables(final ScriptContext ctxt) {
        ctxt.setAttribute("context", ctxt, ScriptContext.ENGINE_SCOPE);
        // current ScriptContext exposed as "context"
        global.set("context", ctxt, false);
        Object args = ctxt.getAttribute("arguments");
        // if no arguments passed, make it empty array
        if (args == null) {
            args = ScriptRuntime.EMPTY_ARRAY;
        }
        args = ((GlobalObject)global).wrapAsObject(args);
        global.set("arguments", args, false);
    }

    private Object invokeImpl(final Object selfObject, final String name, final Object... args) throws ScriptException, NoSuchMethodException {
        final ScriptObject oldGlobal     = Context.getGlobal();
        final boolean globalChanged = (oldGlobal != global);

        Object self = selfObject;

        try {
            if (globalChanged) {
                setNashornGlobal(global);
            }

            ScriptObject sobj;
            Object       value;

            self = ScriptObjectMirror.unwrap(self, global);

            // FIXME: should convert when self is not ScriptObject
            if (self instanceof ScriptObject) {
                sobj = (ScriptObject)self;
                value = sobj.get(name);
            } else if (self == null) {
                self  = global;
                sobj  = global;
                value = sobj.get(name);
            } else {
                // Find the java method and make a ScriptFunction of it.
                final Method[] methods = self.getClass().getMethods();
                Method target = null;

                for (final Method mth : methods) {
                    // choose the right overload by number of arguments -- don't
                    // care overload resolution for now..
                    if (mth.getName().equals(name) &&
                        mth.getParameterTypes().length == args.length) {
                        target = mth;
                        break;
                    }
                }
                if (target == null) {
                    throw new NoSuchMethodException(name);
                }
                final GlobalObject gobj = (GlobalObject) global;
                value = gobj.newScriptFunction(name, MH.find(target), null, false);
            }

            if (value instanceof ScriptFunction) {
                final Object res;
                try {
                    res = ScriptRuntime.apply((ScriptFunction)value, self, ScriptObjectMirror.unwrapArray(args, global));
                } catch (final Exception e) {
                    throwAsScriptException(e);
                    throw new AssertionError("should not reach here");
                }
                return ScriptObjectMirror.wrap(res, global);
            }

            throw new NoSuchMethodException(name);
        } finally {
            if (globalChanged) {
                setNashornGlobal(oldGlobal);
            }
        }
    }

    private Object evalImpl(final char[] buf, final ScriptContext ctxt) throws ScriptException {
        return evalImpl(compileImpl(buf, ctxt), ctxt);
    }

    private Object evalImpl(final ScriptFunction script, final ScriptContext ctxt) throws ScriptException {
        if (script == null) {
            return null;
        }
        final ScriptObject oldGlobal = Context.getGlobal();
        final boolean globalChanged = (oldGlobal != global);
        try {
            if (globalChanged) {
                setNashornGlobal(global);
            }

            setContextVariables(ctxt);
            final Object val = ctxt.getAttribute(ScriptEngine.FILENAME);
            final String fileName = (val != null) ? val.toString() : "<eval>";

            // NOTE: FIXME: If this is jrunscript's init.js, we want to run the replacement.
            // This should go away once we fix jrunscript's copy of init.js.
            if ("<system-init>".equals(fileName)) {
                evalSupportScript("resources/init.js");
                return null;
            }

            Object res = ScriptRuntime.apply(script, global);
            res = ScriptObjectMirror.wrap(res, global);
            return (res == UNDEFINED) ? null : res;
        } catch (final Exception e) {
            throwAsScriptException(e);
            throw new AssertionError("should not reach here");
        } finally {
            if (globalChanged) {
                setNashornGlobal(oldGlobal);
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
        final ScriptObject oldGlobal = Context.getGlobal();
        final boolean globalChanged = (oldGlobal != global);
        try {
            final Object val = ctxt.getAttribute(ScriptEngine.FILENAME);
            final String fileName = (val != null) ? val.toString() : "<eval>";

            final Source source = new Source(fileName, buf);
            if (globalChanged) {
                setNashornGlobal(global);
            }

            setContextVariables(ctxt);
            return nashornContext.compileScript(source, global, nashornContext._strict);
        } catch (final Exception e) {
            throwAsScriptException(e);
            throw new AssertionError("should not reach here");
        } finally {
            if (globalChanged) {
                setNashornGlobal(oldGlobal);
            }
        }
    }

    // don't make this public!!
    static void setNashornGlobal(final ScriptObject global) {
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            @Override
            public Void run() {
               Context.setGlobal(global);
               return null;
            }
        });
    }
}
