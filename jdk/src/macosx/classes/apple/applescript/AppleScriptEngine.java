/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

package apple.applescript;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;

import javax.script.*;

/**
 * AppleScriptEngine implements JSR 223 for AppleScript on Mac OS X
 */
public class AppleScriptEngine implements ScriptEngine {
    private static native void initNative();

    private static native long createContextFrom(final Object object);
    private static native Object createObjectFrom(final long context);
    private static native void disposeContext(final long context);

    private static native long evalScript(final String script, long contextptr);
    private static native long evalScriptFromURL(final String filename, long contextptr);

    static {
        System.loadLibrary("AppleScriptEngine");
        initNative();
        TRACE("<static-init>");
    }

    static void checkSecurity() {
        final SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) securityManager.checkExec("/usr/bin/osascript");
    }

    static void TRACE(final String str) {
//        System.out.println(AppleScriptEngine.class.getName() + "." + str);
    }

    /**
     * Accessor for the ScriptEngine's long name variable
     * @return the long name of the ScriptEngine
     */
    protected static String getEngine() {
        TRACE("getEngine()");
        return AppleScriptEngineFactory.ENGINE_NAME;
    }

    /**
     * Accessor for the ScriptEngine's version
     * @return the version of the ScriptEngine
     */
    protected static String getEngineVersion() {
        TRACE("getEngineVersion()");
        return AppleScriptEngineFactory.ENGINE_VERSION;
    }

    /**
     * Accessor for the ScriptEngine's short name
     * @return the short name of the ScriptEngine
     */
    protected static String getName() {
        TRACE("getName()");
        return AppleScriptEngineFactory.ENGINE_SHORT_NAME;
    }

    /**
     * Accessor for the ScriptEngine's supported language name
     * @return the language the ScriptEngine supports
     */
    protected static String getLanguage() {
        TRACE("getLanguage()");
        return AppleScriptEngineFactory.LANGUAGE;
    }

    /**
     * The no argument constructor sets up the object with default members,
     * a factory for the engine and a fresh context.
     * @see com.apple.applescript.AppleScriptEngine#init()
     */
    public AppleScriptEngine() {
        TRACE("<ctor>()");
        // set our parent factory to be a new factory
        factory = AppleScriptEngineFactory.getFactory();

        // set up our noarg bindings
        setContext(new SimpleScriptContext());
        put(ARGV, "");

        init();
    }

    /**
     * All AppleScriptEngines share the same ScriptEngineFactory
     */
    private final ScriptEngineFactory factory;

    /**
     * The local context for the AppleScriptEngine
     */
    private ScriptContext context;

    /**
     * The constructor taking a factory as an argument sets the parent factory for
     * this engine to be the passed factory, and sets up the engine with a fresh context
     * @param factory
     * @see com.apple.applescript.AppleScriptEngine#init()
     */
    public AppleScriptEngine(final ScriptEngineFactory factory) {
        // inherit the factory passed to us
        this.factory = factory;

        // set up our noarg bindings
        setContext(new SimpleScriptContext());
        put(ARGV, "");

        init();
    }

    /**
     * The initializer populates the local context with some useful predefined variables:
     * <ul><li><code>javax_script_language_version</code> - the version of AppleScript that the AppleScriptEngine supports.</li>
     * <li><code>javax_script_language</code> - "AppleScript" -- the language supported by the AppleScriptEngine.</li>
     * <li><code>javax_script_engine</code> - "AppleScriptEngine" -- the name of the ScriptEngine.</li>
     * <li><code>javax_script_engine_version</code> - the version of the AppleScriptEngine</li>
     * <li><code>javax_script_argv</code> - "" -- AppleScript does not take arguments from the command line</li>
     * <li><code>javax_script_filename</code> - "" -- the currently executing filename</li>
     * <li><code>javax_script_name</code> - "AppleScriptEngine" -- the short name of the AppleScriptEngine</li>
     * <li><code>THREADING</code> - null -- the AppleScriptEngine does not support concurrency, you will have to implement thread-safeness yourself.</li></ul>
     */
    private void init() {
        TRACE("init()");
        // set up our context
/* TODO -- name of current executable?  bad java documentation at:
 * http://java.sun.com/javase/6/docs/api/javax/script/ScriptEngine.html#FILENAME */
        put(ScriptEngine.FILENAME, "");
        put(ScriptEngine.ENGINE, getEngine());
        put(ScriptEngine.ENGINE_VERSION, getEngineVersion());
        put(ScriptEngine.NAME, getName());
        put(ScriptEngine.LANGUAGE, getLanguage());
        put(ScriptEngine.LANGUAGE_VERSION, getLanguageVersion());

        // TODO -- for now, err on the side of caution and say that we are NOT thread-safe
        put("THREADING", null);
    }

    /**
     * Uses the AppleScriptEngine to get the local AppleScript version
     * @return the version of AppleScript running on the system
     */
    protected String getLanguageVersion() {
        TRACE("AppleScriptEngine.getLanguageVersion()");
        try {
            final Object result = eval("get the version of AppleScript");
            if (result instanceof String) return (String)result;
        } catch (final ScriptException e) { e.printStackTrace(); }
        return "unknown";
    }

    /**
     * Implementation required by ScriptEngine parent<br />
     * Returns the factory parent of this AppleScriptEngine
     */
    public ScriptEngineFactory getFactory() {
        return factory;
    }

    /**
     * Implementation required by ScriptEngine parent<br />
     * Return the engine's context
     * @return this ScriptEngine's context
     */
    public ScriptContext getContext() {
        return context;
    }

    /**
     * Implementation required by ScriptEngine parent<br />
     * Set a new context for the engine
     * @param context the new context to install in the engine
     */
    public void setContext(final ScriptContext context) {
        this.context = context;
    }

    /**
     * Implementation required by ScriptEngine parent<br />
     * Create and return a new set of simple bindings.
     * @return a new and empty set of bindings
     */
    public Bindings createBindings() {
        return new SimpleBindings();
    }

    /**
     * Implementation required by ScriptEngine parent<br />
     * Return the engines bindings for the context indicated by the argument.
     * @param scope contextual scope to return.
     * @return the bindings in the engine for the scope indicated by the parameter
     */
    public Bindings getBindings(final int scope) {
        return context.getBindings(scope);
    }

    /**
     * Implementation required by ScriptEngine parent<br />
     * Sets the bindings for the indicated scope
     * @param bindings a set of bindings to assign to the engine
     * @param scope the scope that the passed bindings should be assigned to
     */
    public void setBindings(final Bindings bindings, final int scope) {
        context.setBindings(bindings, scope);
    }

    /**
     * Implementation required by ScriptEngine parent<br />
     * Insert a key and value into the engine's bindings (scope: engine)
     * @param key the key of the pair
     * @param value the value of the pair
     */
    public void put(final String key, final Object value) {
        getBindings(ScriptContext.ENGINE_SCOPE).put(key, value);
    }

    /**
     * Implementation required by ScriptEngine parent<br />
     * Get a value from the engine's bindings using a key (scope: engine)
     * @param key the key of the pair
     * @return the value of the pair
     */
    public Object get(final String key) {
        return getBindings(ScriptContext.ENGINE_SCOPE).get(key);
    }

    /**
     * Implementation required by ScriptEngine parent<br />
     * Passes the Reader argument, as well as the engine's context to a lower evaluation function.<br />
     * Prefers FileReader or BufferedReader wrapping FileReader as argument.
     * @param reader a Reader to AppleScript source or compiled AppleScript
     * @return an Object corresponding to the return value of the script
     * @see com.apple.applescript.AppleScriptEngine#eval(Reader, ScriptContext)
     */
    public Object eval(final Reader reader) throws ScriptException {
        return eval(reader, getContext());
    }

    /**
     * Implementation required by ScriptEngine parent<br />
     * Uses the passed bindings as the context for executing the passed script.
     * @param reader a stream to AppleScript source or compiled AppleScript
     * @param bindings a Bindings object representing the contexts to execute inside
     * @return the return value of the script
     * @see com.apple.applescript.AppleScriptEngine#eval(Reader, ScriptContext)
     */
    public Object eval(final Reader reader, final Bindings bindings) throws ScriptException {
        final Bindings tmp = getContext().getBindings(ScriptContext.ENGINE_SCOPE);
        getContext().setBindings(bindings, ScriptContext.ENGINE_SCOPE);
        final Object retval = eval(reader);
        getContext().setBindings(tmp, ScriptContext.ENGINE_SCOPE);
        return retval;
    }

    /**
     * Implementation required by ScriptEngine parent<br />
     * This function can execute either AppleScript source or compiled AppleScript and functions by writing the
     * contents of the Reader to a temporary file and then executing it with the engine's context.
     * @param reader
     * @param scriptContext
     * @return an Object corresponding to the return value of the script
     */
    public Object eval(final Reader reader, final ScriptContext context) throws ScriptException {
        checkSecurity();

        // write our passed reader to a temporary file
        File tmpfile;
        FileWriter tmpwrite;
        try {
            tmpfile = File.createTempFile("AppleScriptEngine.", ".scpt");
            tmpwrite = new FileWriter(tmpfile);

            // read in our input and write directly to tmpfile
            /* TODO -- this may or may not be avoidable for certain Readers,
             * if a filename can be grabbed, it would be faster to get that and
             * use the underlying file than writing a temp file.
             */
            int data;
            while ((data = reader.read()) != -1) {
                tmpwrite.write(data);
            }
            tmpwrite.close();

            // set up our context business
            final long contextptr = scriptContextToNSDictionary(context);
            try {
                final long retCtx = evalScriptFromURL("file://" + tmpfile.getCanonicalPath(), contextptr);
                Object retVal = (retCtx == 0) ? null : createObjectFrom(retCtx);
                disposeContext(retCtx);
                return retVal;
            } finally {
                disposeContext(contextptr);
                tmpfile.delete();
            }
        } catch (final IOException e) {
            throw new ScriptException(e);
        }
    }

    /**
     * Implementation required by ScriptEngine parent<br />
     * Evaluate an AppleScript script passed as a source string. Using the engine's built in context.
     * @param script the string to execute.
     * @return an Object representing the return value of the script
     * @see com.apple.applescript.AppleScriptEngine#eval(String, ScriptContext)
     */
    public Object eval(final String script) throws ScriptException {
        return eval(script, getContext());
    }

    /**
     * Implementation required by ScriptEngine parent<br />
     * Evaluate an AppleScript script passed as a source string with a custom ScriptContext.
     * @param script the AppleScript source to compile and execute.
     * @param scriptContext the context to execute the script under
     * @see com.apple.applescript.AppleScriptEngine#eval(String, ScriptContext)
     */
    public Object eval(final String script, final Bindings bindings) throws ScriptException {
        final Bindings tmp = getContext().getBindings(ScriptContext.ENGINE_SCOPE);
        getContext().setBindings(bindings, ScriptContext.ENGINE_SCOPE);

        final Object retval = eval(script);
        getContext().setBindings(tmp, ScriptContext.ENGINE_SCOPE);

        return retval;
    }

    /**
     * Implementation required by ScriptEngine parent
     * @param script
     * @param scriptContext
     */
    public Object eval(final String script, final ScriptContext context) throws ScriptException {
        checkSecurity();
        final long ctxPtr = scriptContextToNSDictionary(context);
        try {
            final long retCtx = evalScript(script, ctxPtr);
            Object retVal = (retCtx == 0) ? null : createObjectFrom(retCtx);
            disposeContext(retCtx);
            return retVal;
        } finally {
            disposeContext(ctxPtr);
        }
    }

    /**
     * Converts a ScriptContext into an NSDictionary
     * @param context ScriptContext for the engine
     * @return a pointer to an NSDictionary
     */
    private long scriptContextToNSDictionary(final ScriptContext context) throws ScriptException {
        final Map<String, Object> contextAsMap = new HashMap<String, Object>();
        for (final Entry<String, Object> e : context.getBindings(ScriptContext.ENGINE_SCOPE).entrySet()) {
            contextAsMap.put(e.getKey().replaceAll("\\.", "_"), e.getValue());
        }
        return createContextFrom(contextAsMap);
    }
}
