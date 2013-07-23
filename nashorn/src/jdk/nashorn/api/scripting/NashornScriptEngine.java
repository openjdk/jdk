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
    // initialized bit late to be made 'final'. Property object for "context"
    // property of global object
    private Property                  contextProperty;

    // default options passed to Nashorn Options object
    private static final String[] DEFAULT_OPTIONS = new String[] { "-scripting", "-doe" };

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
        });

        // create new global object
        this.global = createNashornGlobal();
        // set the default engine scope for the default context
        context.setBindings(new ScriptObjectMirror(global, global), ScriptContext.ENGINE_SCOPE);

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
        final ScriptObject newGlobal = createNashornGlobal();
        return new ScriptObjectMirror(newGlobal, newGlobal);
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
        if (clazz == null || !clazz.isInterface()) {
            throw new IllegalArgumentException("interface Class expected");
        }

        // perform security access check as early as possible
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            if (! Modifier.isPublic(clazz.getModifiers())) {
                throw new SecurityException("attempt to implement non-public interfce: " + clazz);
            }
            Context.checkPackageAccess(clazz.getName());
        }

        final ScriptObject realSelf;
        final ScriptObject ctxtGlobal = getNashornGlobalFrom(context);
        if(self == null) {
            realSelf = ctxtGlobal;
        } else if (!(self instanceof ScriptObject)) {
            realSelf = (ScriptObject)ScriptObjectMirror.unwrap(self, ctxtGlobal);
        } else {
            realSelf = (ScriptObject)self;
        }

        try {
            final ScriptObject oldGlobal = Context.getGlobal();
            try {
                if(oldGlobal != ctxtGlobal) {
                    Context.setGlobal(ctxtGlobal);
                }

                if (! isInterfaceImplemented(clazz, realSelf)) {
                    return null;
                }
                return clazz.cast(JavaAdapterFactory.getConstructor(realSelf.getClass(), clazz).invoke(realSelf));
            } finally {
                if(oldGlobal != ctxtGlobal) {
                    Context.setGlobal(oldGlobal);
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
        final ScriptObject ctxtGlobal = getNashornGlobalFrom(ctxt);
        if (scope != -1) {
            return ScriptObjectMirror.unwrap(ctxt.getAttribute(name, scope), ctxtGlobal);
        }

        if (self == UNDEFINED) {
            // scope access and so throw ReferenceError
            throw referenceError(ctxtGlobal, "not.defined", name);
        }

        return UNDEFINED;
    }

    private ScriptObject getNashornGlobalFrom(final ScriptContext ctxt) {
        final Bindings bindings = ctxt.getBindings(ScriptContext.ENGINE_SCOPE);
        if (bindings instanceof ScriptObjectMirror) {
             ScriptObject sobj = ((ScriptObjectMirror)bindings).getScriptObject();
             if (sobj instanceof GlobalObject) {
                 return sobj;
             }
        }

        // didn't find global object from context given - return the engine-wide global
        return global;
    }

    private ScriptObject createNashornGlobal() {
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
        });

        nashornContext.initGlobal(newGlobal);

        final int NON_ENUMERABLE_CONSTANT = Property.NOT_ENUMERABLE | Property.NOT_CONFIGURABLE | Property.NOT_WRITABLE;
        // current ScriptContext exposed as "context"
        // "context" is non-writable from script - but script engine still
        // needs to set it and so save the context Property object
        contextProperty = newGlobal.addOwnProperty("context", NON_ENUMERABLE_CONSTANT, UNDEFINED);
        // current ScriptEngine instance exposed as "engine". We added @SuppressWarnings("LeakingThisInConstructor") as
        // NetBeans identifies this assignment as such a leak - this is a false positive as we're setting this property
        // in the Global of a Context we just created - both the Context and the Global were just created and can not be
        // seen from another thread outside of this constructor.
        newGlobal.addOwnProperty("engine", NON_ENUMERABLE_CONSTANT, this);
        // global script arguments with undefined value
        newGlobal.addOwnProperty("arguments", Property.NOT_ENUMERABLE, UNDEFINED);
        // file name default is null
        newGlobal.addOwnProperty(ScriptEngine.FILENAME, Property.NOT_ENUMERABLE, null);
        return newGlobal;
    }

    private void evalEngineScript() throws ScriptException {
        evalSupportScript("resources/engine.js", NashornException.ENGINE_SCRIPT_SOURCE_NAME);
    }

    private void evalSupportScript(final String script, final String name) throws ScriptException {
        try {
            final InputStream is = AccessController.doPrivileged(
                    new PrivilegedExceptionAction<InputStream>() {
                        @Override
                        public InputStream run() throws Exception {
                            final URL url = NashornScriptEngine.class.getResource(script);
                            return url.openStream();
                        }
                    });
            put(ScriptEngine.FILENAME, name);
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
        final ScriptObject ctxtGlobal = getNashornGlobalFrom(ctxt);
        // set "context" global variable via contextProperty - because this
        // property is non-writable
        contextProperty.setObjectValue(ctxtGlobal, ctxtGlobal, ctxt, false);
        Object args = ScriptObjectMirror.unwrap(ctxt.getAttribute("arguments"), ctxtGlobal);
        if (args == null || args == UNDEFINED) {
            args = ScriptRuntime.EMPTY_ARRAY;
        }
        // if no arguments passed, expose it
        args = ((GlobalObject)ctxtGlobal).wrapAsObject(args);
        ctxtGlobal.set("arguments", args, false);
    }

    private Object invokeImpl(final Object selfObject, final String name, final Object... args) throws ScriptException, NoSuchMethodException {
        final ScriptObject oldGlobal     = Context.getGlobal();
        final ScriptObject ctxtGlobal    = getNashornGlobalFrom(context);
        final boolean globalChanged = (oldGlobal != ctxtGlobal);

        Object self = globalChanged? ScriptObjectMirror.wrap(selfObject, oldGlobal) : selfObject;

        try {
            if (globalChanged) {
                Context.setGlobal(ctxtGlobal);
            }

            ScriptObject sobj;
            Object       value = null;

            self = ScriptObjectMirror.unwrap(self, ctxtGlobal);

            // FIXME: should convert when self is not ScriptObject
            if (self instanceof ScriptObject) {
                sobj = (ScriptObject)self;
                value = sobj.get(name);
            } else if (self == null) {
                self  = ctxtGlobal;
                sobj  = ctxtGlobal;
                value = sobj.get(name);
            }

            if (value instanceof ScriptFunction) {
                final Object res;
                try {
                    final Object[] modArgs = globalChanged? ScriptObjectMirror.wrapArray(args, oldGlobal) : args;
                    res = ScriptRuntime.checkAndApply((ScriptFunction)value, self, ScriptObjectMirror.unwrapArray(modArgs, ctxtGlobal));
                } catch (final Exception e) {
                    throwAsScriptException(e);
                    throw new AssertionError("should not reach here");
                }
                return ScriptObjectMirror.translateUndefined(ScriptObjectMirror.wrap(res, ctxtGlobal));
            }

            throw new NoSuchMethodException(name);
        } finally {
            if (globalChanged) {
                Context.setGlobal(oldGlobal);
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
        final ScriptObject ctxtGlobal = getNashornGlobalFrom(ctxt);
        final boolean globalChanged = (oldGlobal != ctxtGlobal);
        try {
            if (globalChanged) {
                Context.setGlobal(ctxtGlobal);
            }

            setContextVariables(ctxt);
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
        final ScriptObject oldGlobal = Context.getGlobal();
        final ScriptObject ctxtGlobal = getNashornGlobalFrom(ctxt);
        final boolean globalChanged = (oldGlobal != ctxtGlobal);
        try {
            if (globalChanged) {
                Context.setGlobal(ctxtGlobal);
            }

            return nashornContext.compileScript(source, ctxtGlobal);
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
}
