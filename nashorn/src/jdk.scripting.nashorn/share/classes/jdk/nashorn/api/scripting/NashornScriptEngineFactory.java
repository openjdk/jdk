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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.Version;

/**
 * JSR-223 compliant script engine factory for Nashorn. The engine answers for:
 * <ul>
 * <li>names {@code "nashorn"}, {@code "Nashorn"}, {@code "js"}, {@code "JS"}, {@code "JavaScript"},
 * {@code "javascript"}, {@code "ECMAScript"}, and {@code "ecmascript"};</li>
 * <li>MIME types {@code "application/javascript"}, {@code "application/ecmascript"}, {@code "text/javascript"}, and
 * {@code "text/ecmascript"};</li>
 * <li>as well as for the extension {@code "js"}.</li>
 * </ul>
 * Programs executing in engines created using {@link #getScriptEngine(String[])} will have the passed arguments
 * accessible as a global variable named {@code "arguments"}.
 */
public final class NashornScriptEngineFactory implements ScriptEngineFactory {
    @Override
    public String getEngineName() {
        return (String) getParameter(ScriptEngine.ENGINE);
    }

    @Override
    public String getEngineVersion() {
        return (String) getParameter(ScriptEngine.ENGINE_VERSION);
    }

    @Override
    public List<String> getExtensions() {
        return Collections.unmodifiableList(extensions);
    }

    @Override
    public String getLanguageName() {
        return (String) getParameter(ScriptEngine.LANGUAGE);
    }

    @Override
    public String getLanguageVersion() {
        return (String) getParameter(ScriptEngine.LANGUAGE_VERSION);
    }

    @Override
    public String getMethodCallSyntax(final String obj, final String method, final String... args) {
        final StringBuilder sb = new StringBuilder().append(obj).append('.').append(method).append('(');
        final int len = args.length;

        if (len > 0) {
            sb.append(args[0]);
        }
        for (int i = 1; i < len; i++) {
            sb.append(',').append(args[i]);
        }
        sb.append(')');

        return sb.toString();
    }

    @Override
    public List<String> getMimeTypes() {
        return Collections.unmodifiableList(mimeTypes);
    }

    @Override
    public List<String> getNames() {
        return Collections.unmodifiableList(names);
    }

    @Override
    public String getOutputStatement(final String toDisplay) {
        return "print(" + toDisplay + ")";
    }

    @Override
    public Object getParameter(final String key) {
        switch (key) {
        case ScriptEngine.NAME:
            return "javascript";
        case ScriptEngine.ENGINE:
            return "Oracle Nashorn";
        case ScriptEngine.ENGINE_VERSION:
            return Version.version();
        case ScriptEngine.LANGUAGE:
            return "ECMAScript";
        case ScriptEngine.LANGUAGE_VERSION:
            return "ECMA - 262 Edition 5.1";
        case "THREADING":
            // The engine implementation is not thread-safe. Can't be
            // used to execute scripts concurrently on multiple threads.
            return null;
        default:
            throw new IllegalArgumentException("Invalid key");
        }
    }

    @Override
    public String getProgram(final String... statements) {
        final StringBuilder sb = new StringBuilder();

        for (final String statement : statements) {
            sb.append(statement).append(';');
        }

        return sb.toString();
    }

    // default options passed to Nashorn script engine
    private static final String[] DEFAULT_OPTIONS = new String[] { "-doe" };

    @Override
    public ScriptEngine getScriptEngine() {
        try {
            return new NashornScriptEngine(this, DEFAULT_OPTIONS, getAppClassLoader(), null);
        } catch (final RuntimeException e) {
            if (Context.DEBUG) {
                e.printStackTrace();
            }
            throw e;
        }
    }

    /**
     * Create a new Script engine initialized by given class loader.
     *
     * @param appLoader class loader to be used as script "app" class loader.
     * @return newly created script engine.
     * @throws SecurityException
     *         if the security manager's {@code checkPermission}
     *         denies {@code RuntimePermission("nashorn.setConfig")}
     */
    public ScriptEngine getScriptEngine(final ClassLoader appLoader) {
        return newEngine(DEFAULT_OPTIONS, appLoader, null);
    }

    /**
     * Create a new Script engine initialized by given class filter.
     *
     * @param classFilter class filter to use.
     * @return newly created script engine.
     * @throws NullPointerException if {@code classFilter} is {@code null}
     * @throws SecurityException
     *         if the security manager's {@code checkPermission}
     *         denies {@code RuntimePermission("nashorn.setConfig")}
     */
    public ScriptEngine getScriptEngine(final ClassFilter classFilter) {
        classFilter.getClass(); // null check
        return newEngine(DEFAULT_OPTIONS, getAppClassLoader(), classFilter);
    }

    /**
     * Create a new Script engine initialized by given arguments.
     *
     * @param args arguments array passed to script engine.
     * @return newly created script engine.
     * @throws NullPointerException if {@code args} is {@code null}
     * @throws SecurityException
     *         if the security manager's {@code checkPermission}
     *         denies {@code RuntimePermission("nashorn.setConfig")}
     */
    public ScriptEngine getScriptEngine(final String... args) {
        args.getClass(); // null check
        return newEngine(args, getAppClassLoader(), null);
    }

    /**
     * Create a new Script engine initialized by given arguments.
     *
     * @param args arguments array passed to script engine.
     * @param appLoader class loader to be used as script "app" class loader.
     * @return newly created script engine.
     * @throws NullPointerException if {@code args} is {@code null}
     * @throws SecurityException
     *         if the security manager's {@code checkPermission}
     *         denies {@code RuntimePermission("nashorn.setConfig")}
     */
    public ScriptEngine getScriptEngine(final String[] args, final ClassLoader appLoader) {
        args.getClass(); // null check
        return newEngine(args, appLoader, null);
    }

    /**
     * Create a new Script engine initialized by given arguments.
     *
     * @param args arguments array passed to script engine.
     * @param appLoader class loader to be used as script "app" class loader.
     * @param classFilter class filter to use.
     * @return newly created script engine.
     * @throws NullPointerException if {@code args} or {@code classFilter} is {@code null}
     * @throws SecurityException
     *         if the security manager's {@code checkPermission}
     *         denies {@code RuntimePermission("nashorn.setConfig")}
     */
    public ScriptEngine getScriptEngine(final String[] args, final ClassLoader appLoader, final ClassFilter classFilter) {
        args.getClass(); // null check
        classFilter.getClass(); // null check
        return newEngine(args, appLoader, classFilter);
    }

    private ScriptEngine newEngine(final String[] args, final ClassLoader appLoader, final ClassFilter classFilter) {
        checkConfigPermission();
        try {
            return new NashornScriptEngine(this, args, appLoader, classFilter);
        } catch (final RuntimeException e) {
            if (Context.DEBUG) {
                e.printStackTrace();
            }
            throw e;
        }
    }

    // -- Internals only below this point

    private static void checkConfigPermission() {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new RuntimePermission(Context.NASHORN_SET_CONFIG));
        }
    }

    private static final List<String> names;
    private static final List<String> mimeTypes;
    private static final List<String> extensions;

    static {
        names = immutableList(
                    "nashorn", "Nashorn",
                    "js", "JS",
                    "JavaScript", "javascript",
                    "ECMAScript", "ecmascript"
                );

        mimeTypes = immutableList(
                        "application/javascript",
                        "application/ecmascript",
                        "text/javascript",
                        "text/ecmascript"
                    );

        extensions = immutableList("js");
    }

    private static List<String> immutableList(final String... elements) {
        return Collections.unmodifiableList(Arrays.asList(elements));
    }

    private static ClassLoader getAppClassLoader() {
        // Revisit: script engine implementation needs the capability to
        // find the class loader of the context in which the script engine
        // is running so that classes will be found and loaded properly
        final ClassLoader ccl = Thread.currentThread().getContextClassLoader();
        return (ccl == null)? NashornScriptEngineFactory.class.getClassLoader() : ccl;
    }
}
