/*
 * Copyright (c) 1998, 2016, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javadoc.main;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.regex.Pattern;

import javax.tools.DocumentationTool;
import javax.tools.JavaFileManager;

import com.sun.javadoc.*;
import com.sun.tools.javac.util.ClientCodeException;
import com.sun.tools.javac.util.List;

/**
 * Class creates, controls and invokes doclets.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Neal Gafter (rewrite)
 */
@Deprecated
public class DocletInvoker {

    private final Class<?> docletClass;

    private final String docletClassName;

    private final ClassLoader appClassLoader;

    private final Messager messager;

    /**
     * In API mode, exceptions thrown while calling the doclet are
     * propagated using ClientCodeException.
     */
    private final boolean apiMode;

    /**
     * Whether javadoc internal API should be exported to doclets
     * and (indirectly) to taglets
     */
    private final boolean exportInternalAPI;

    private static class DocletInvokeException extends Exception {
        private static final long serialVersionUID = 0;
    }

    private String appendPath(String path1, String path2) {
        if (path1 == null || path1.length() == 0) {
            return path2 == null ? "." : path2;
        } else if (path2 == null || path2.length() == 0) {
            return path1;
        } else {
            return path1  + File.pathSeparator + path2;
        }
    }

    public DocletInvoker(Messager messager, Class<?> docletClass, boolean apiMode, boolean exportInternalAPI) {
        this.messager = messager;
        this.docletClass = docletClass;
        docletClassName = docletClass.getName();
        appClassLoader = null;
        this.apiMode = apiMode;
        this.exportInternalAPI = exportInternalAPI; // for backdoor use by standard doclet for taglets

        // this may not be soon enough if the class has already been loaded
        if (exportInternalAPI) {
            exportInternalAPI(docletClass.getClassLoader());
        }
    }

    public DocletInvoker(Messager messager, JavaFileManager fileManager,
                         String docletClassName, String docletPath,
                         ClassLoader docletParentClassLoader,
                         boolean apiMode,
                         boolean exportInternalAPI) {
        this.messager = messager;
        this.docletClassName = docletClassName;
        this.apiMode = apiMode;
        this.exportInternalAPI = exportInternalAPI; // for backdoor use by standard doclet for taglets

        if (fileManager != null && fileManager.hasLocation(DocumentationTool.Location.DOCLET_PATH)) {
            appClassLoader = fileManager.getClassLoader(DocumentationTool.Location.DOCLET_PATH);
        } else {
            // construct class loader
            String cpString = null;   // make sure env.class.path defaults to dot

            // do prepends to get correct ordering
            cpString = appendPath(System.getProperty("env.class.path"), cpString);
            cpString = appendPath(System.getProperty("java.class.path"), cpString);
            cpString = appendPath(docletPath, cpString);
            URL[] urls = pathToURLs(cpString);
            if (docletParentClassLoader == null)
                appClassLoader = new URLClassLoader(urls, getDelegationClassLoader(docletClassName));
            else
                appClassLoader = new URLClassLoader(urls, docletParentClassLoader);
        }

        if (exportInternalAPI) {
            exportInternalAPI(appClassLoader);
        }

        // attempt to find doclet
        Class<?> dc = null;
        try {
            dc = appClassLoader.loadClass(docletClassName);
        } catch (ClassNotFoundException exc) {
            messager.error(Messager.NOPOS, "main.doclet_class_not_found", docletClassName);
            messager.exit();
        }
        docletClass = dc;
    }

    /*
     * Returns the delegation class loader to use when creating
     * appClassLoader (used to load the doclet).  The context class
     * loader is the best choice, but legacy behavior was to use the
     * default delegation class loader (aka system class loader).
     *
     * Here we favor using the context class loader.  To ensure
     * compatibility with existing apps, we revert to legacy
     * behavior if either or both of the following conditions hold:
     *
     * 1) the doclet is loadable from the system class loader but not
     *    from the context class loader,
     *
     * 2) this.getClass() is loadable from the system class loader but not
     *    from the context class loader.
     */
    private ClassLoader getDelegationClassLoader(String docletClassName) {
        ClassLoader ctxCL = Thread.currentThread().getContextClassLoader();
        ClassLoader sysCL = ClassLoader.getSystemClassLoader();
        if (sysCL == null)
            return ctxCL;
        if (ctxCL == null)
            return sysCL;

        // Condition 1.
        try {
            sysCL.loadClass(docletClassName);
            try {
                ctxCL.loadClass(docletClassName);
            } catch (ClassNotFoundException e) {
                return sysCL;
            }
        } catch (ClassNotFoundException e) {
        }

        // Condition 2.
        try {
            if (getClass() == sysCL.loadClass(getClass().getName())) {
                try {
                    if (getClass() != ctxCL.loadClass(getClass().getName()))
                        return sysCL;
                } catch (ClassNotFoundException e) {
                    return sysCL;
                }
            }
        } catch (ClassNotFoundException e) {
        }

        return ctxCL;
    }

    /**
     * Generate documentation here.  Return true on success.
     */
    public boolean start(RootDoc root) {
        Object retVal;
        String methodName = "start";
        Class<?>[] paramTypes = { RootDoc.class };
        Object[] params = { root };
        try {
            retVal = invoke(methodName, null, paramTypes, params);
        } catch (DocletInvokeException exc) {
            return false;
        }
        if (retVal instanceof Boolean) {
            return ((Boolean)retVal);
        } else {
            messager.error(Messager.NOPOS, "main.must_return_boolean",
                           docletClassName, methodName);
            return false;
        }
    }

    /**
     * Check for doclet added options here. Zero return means
     * option not known.  Positive value indicates number of
     * arguments to option.  Negative value means error occurred.
     */
    public int optionLength(String option) {
        Object retVal;
        String methodName = "optionLength";
        Class<?>[] paramTypes = { String.class };
        Object[] params = { option };
        try {
            retVal = invoke(methodName, 0, paramTypes, params);
        } catch (DocletInvokeException exc) {
            return -1;
        }
        if (retVal instanceof Integer) {
            return ((Integer)retVal);
        } else {
            messager.error(Messager.NOPOS, "main.must_return_int",
                           docletClassName, methodName);
            return -1;
        }
    }

    /**
     * Let doclet check that all options are OK. Returning true means
     * options are OK.  If method does not exist, assume true.
     */
    public boolean validOptions(List<String[]> optlist) {
        Object retVal;
        String options[][] = optlist.toArray(new String[optlist.length()][]);
        String methodName = "validOptions";
        DocErrorReporter reporter = messager;
        Class<?>[] paramTypes = { String[][].class, DocErrorReporter.class };
        Object[] params = { options, reporter };
        try {
            retVal = invoke(methodName, Boolean.TRUE, paramTypes, params);
        } catch (DocletInvokeException exc) {
            return false;
        }
        if (retVal instanceof Boolean) {
            return ((Boolean)retVal);
        } else {
            messager.error(Messager.NOPOS, "main.must_return_boolean",
                           docletClassName, methodName);
            return false;
        }
    }

    /**
     * Return the language version supported by this doclet.
     * If the method does not exist in the doclet, assume version 1.1.
     */
    public LanguageVersion languageVersion() {
        try {
            Object retVal;
            String methodName = "languageVersion";
            Class<?>[] paramTypes = new Class<?>[0];
            Object[] params = new Object[0];
            try {
                retVal = invoke(methodName, LanguageVersion.JAVA_1_1, paramTypes, params);
            } catch (DocletInvokeException exc) {
                return LanguageVersion.JAVA_1_1;
            }
            if (retVal instanceof LanguageVersion) {
                return (LanguageVersion)retVal;
            } else {
                messager.error(Messager.NOPOS, "main.must_return_languageversion",
                               docletClassName, methodName);
                return LanguageVersion.JAVA_1_1;
            }
        } catch (NoClassDefFoundError ex) { // for boostrapping, no Enum class.
            return null;
        }
    }

    /**
     * Utility method for calling doclet functionality
     */
    private Object invoke(String methodName, Object returnValueIfNonExistent,
                          Class<?>[] paramTypes, Object[] params)
        throws DocletInvokeException {
            Method meth;
            try {
                meth = docletClass.getMethod(methodName, paramTypes);
            } catch (NoSuchMethodException exc) {
                if (returnValueIfNonExistent == null) {
                    messager.error(Messager.NOPOS, "main.doclet_method_not_found",
                                   docletClassName, methodName);
                    throw new DocletInvokeException();
                } else {
                    return returnValueIfNonExistent;
                }
            } catch (SecurityException exc) {
                messager.error(Messager.NOPOS, "main.doclet_method_not_accessible",
                               docletClassName, methodName);
                throw new DocletInvokeException();
            }
            if (!Modifier.isStatic(meth.getModifiers())) {
                messager.error(Messager.NOPOS, "main.doclet_method_must_be_static",
                               docletClassName, methodName);
                throw new DocletInvokeException();
            }
            ClassLoader savedCCL =
                Thread.currentThread().getContextClassLoader();
            try {
                if (appClassLoader != null) // will be null if doclet class provided via API
                    Thread.currentThread().setContextClassLoader(appClassLoader);
                return meth.invoke(null , params);
            } catch (IllegalArgumentException | NullPointerException exc) {
                messager.error(Messager.NOPOS, "main.internal_error_exception_thrown",
                               docletClassName, methodName, exc.toString());
                throw new DocletInvokeException();
            } catch (IllegalAccessException exc) {
                messager.error(Messager.NOPOS, "main.doclet_method_not_accessible",
                               docletClassName, methodName);
                throw new DocletInvokeException();
            }
            catch (InvocationTargetException exc) {
                Throwable err = exc.getTargetException();
                if (apiMode)
                    throw new ClientCodeException(err);
                if (err instanceof java.lang.OutOfMemoryError) {
                    messager.error(Messager.NOPOS, "main.out.of.memory");
                } else {
                    messager.error(Messager.NOPOS, "main.exception_thrown",
                               docletClassName, methodName, exc.toString());
                    exc.getTargetException().printStackTrace(System.err);
                }
                throw new DocletInvokeException();
            } finally {
                Thread.currentThread().setContextClassLoader(savedCCL);
            }
    }

    /**
     * Export javadoc internal API to the unnamed module for a classloader.
     * This is to support continued use of existing non-standard doclets that
     * use the internal toolkit API and related classes.
     * @param cl the classloader
     */
    private void exportInternalAPI(ClassLoader cl) {
        String[] packages = {
            "com.sun.tools.doclets",
            "com.sun.tools.doclets.standard",
            "com.sun.tools.doclets.internal.toolkit",
            "com.sun.tools.doclets.internal.toolkit.taglets",
            "com.sun.tools.doclets.internal.toolkit.builders",
            "com.sun.tools.doclets.internal.toolkit.util",
            "com.sun.tools.doclets.internal.toolkit.util.links",
            "com.sun.tools.doclets.formats.html",
            "com.sun.tools.doclets.formats.html.markup"
        };

        try {
            Method getModuleMethod = Class.class.getDeclaredMethod("getModule");
            Object thisModule = getModuleMethod.invoke(getClass());

            Class<?> moduleClass = Class.forName("java.lang.Module");
            Method addExportsMethod = moduleClass.getDeclaredMethod("addExports", String.class, moduleClass);

            Method getUnnamedModuleMethod = ClassLoader.class.getDeclaredMethod("getUnnamedModule");
            Object target = getUnnamedModuleMethod.invoke(cl);

            for (String pack : packages) {
                addExportsMethod.invoke(thisModule, pack, target);
            }
        } catch (Exception e) {
            // do nothing
        }
    }

    /**
     * Utility method for converting a search path string to an array of directory and JAR file
     * URLs.
     *
     * Note that this method is called by the DocletInvoker.
     *
     * @param path the search path string
     * @return the resulting array of directory and JAR file URLs
     */
    private static URL[] pathToURLs(String path) {
        java.util.List<URL> urls = new ArrayList<>();
        for (String s: path.split(Pattern.quote(File.pathSeparator))) {
            if (!s.isEmpty()) {
                URL url = fileToURL(Paths.get(s));
                if (url != null) {
                    urls.add(url);
                }
            }
        }
        return urls.toArray(new URL[urls.size()]);
    }

    /**
     * Returns the directory or JAR file URL corresponding to the specified local file name.
     *
     * @param file the Path object
     * @return the resulting directory or JAR file URL, or null if unknown
     */
    private static URL fileToURL(Path file) {
        Path p;
        try {
            p = file.toRealPath();
        } catch (IOException e) {
            p = file.toAbsolutePath();
        }
        try {
            return p.normalize().toUri().toURL();
        } catch (MalformedURLException e) {
            return null;
        }
    }
}
