/*
 * Copyright 1998-2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.tools.javadoc;

import com.sun.javadoc.*;

import static com.sun.javadoc.LanguageVersion.*;

import com.sun.tools.javac.util.List;

import java.net.*;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.InvocationTargetException;

import java.io.File;
import java.io.IOException;
import java.util.StringTokenizer;

/**
 * Class creates, controls and invokes doclets.
 * @author Neal Gafter (rewrite)
 */
public class DocletInvoker {

    private final Class<?> docletClass;

    private final String docletClassName;

    private final ClassLoader appClassLoader;

    private final Messager messager;

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

    public DocletInvoker(Messager messager,
                         String docletClassName, String docletPath,
                         ClassLoader docletParentClassLoader) {
        this.messager = messager;
        this.docletClassName = docletClassName;

        // construct class loader
        String cpString = null;   // make sure env.class.path defaults to dot

        // do prepends to get correct ordering
        cpString = appendPath(System.getProperty("env.class.path"), cpString);
        cpString = appendPath(System.getProperty("java.class.path"), cpString);
        cpString = appendPath(docletPath, cpString);
        URL[] urls = com.sun.tools.javac.file.Paths.pathToURLs(cpString);
        if (docletParentClassLoader == null)
            appClassLoader = new URLClassLoader(urls, getDelegationClassLoader(docletClassName));
        else
            appClassLoader = new URLClassLoader(urls, docletParentClassLoader);

        // attempt to find doclet
        Class<?> dc = null;
        try {
            dc = appClassLoader.loadClass(docletClassName);
        } catch (ClassNotFoundException exc) {
            messager.error(null, "main.doclet_class_not_found", docletClassName);
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
        Class<?>[] paramTypes = new Class<?>[1];
        Object[] params = new Object[1];
        paramTypes[0] = RootDoc.class;
        params[0] = root;
        try {
            retVal = invoke(methodName, null, paramTypes, params);
        } catch (DocletInvokeException exc) {
            return false;
        }
        if (retVal instanceof Boolean) {
            return ((Boolean)retVal).booleanValue();
        } else {
            messager.error(null, "main.must_return_boolean",
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
        Class<?>[] paramTypes = new Class<?>[1];
        Object[] params = new Object[1];
        paramTypes[0] = option.getClass();
        params[0] = option;
        try {
            retVal = invoke(methodName, new Integer(0), paramTypes, params);
        } catch (DocletInvokeException exc) {
            return -1;
        }
        if (retVal instanceof Integer) {
            return ((Integer)retVal).intValue();
        } else {
            messager.error(null, "main.must_return_int",
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
        Class<?>[] paramTypes = new Class<?>[2];
        Object[] params = new Object[2];
        paramTypes[0] = options.getClass();
        paramTypes[1] = DocErrorReporter.class;
        params[0] = options;
        params[1] = reporter;
        try {
            retVal = invoke(methodName, Boolean.TRUE, paramTypes, params);
        } catch (DocletInvokeException exc) {
            return false;
        }
        if (retVal instanceof Boolean) {
            return ((Boolean)retVal).booleanValue();
        } else {
            messager.error(null, "main.must_return_boolean",
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
                retVal = invoke(methodName, JAVA_1_1, paramTypes, params);
            } catch (DocletInvokeException exc) {
                return JAVA_1_1;
            }
            if (retVal instanceof LanguageVersion) {
                return (LanguageVersion)retVal;
            } else {
                messager.error(null, "main.must_return_languageversion",
                               docletClassName, methodName);
                return JAVA_1_1;
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
                    messager.error(null, "main.doclet_method_not_found",
                                   docletClassName, methodName);
                    throw new DocletInvokeException();
                } else {
                    return returnValueIfNonExistent;
                }
            } catch (SecurityException exc) {
                messager.error(null, "main.doclet_method_not_accessible",
                               docletClassName, methodName);
                throw new DocletInvokeException();
            }
            if (!Modifier.isStatic(meth.getModifiers())) {
                messager.error(null, "main.doclet_method_must_be_static",
                               docletClassName, methodName);
                throw new DocletInvokeException();
            }
            ClassLoader savedCCL =
                Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(appClassLoader);
                return meth.invoke(null , params);
            } catch (IllegalArgumentException exc) {
                messager.error(null, "main.internal_error_exception_thrown",
                               docletClassName, methodName, exc.toString());
                throw new DocletInvokeException();
            } catch (IllegalAccessException exc) {
                messager.error(null, "main.doclet_method_not_accessible",
                               docletClassName, methodName);
                throw new DocletInvokeException();
            } catch (NullPointerException exc) {
                messager.error(null, "main.internal_error_exception_thrown",
                               docletClassName, methodName, exc.toString());
                throw new DocletInvokeException();
            } catch (InvocationTargetException exc) {
                Throwable err = exc.getTargetException();
                if (err instanceof java.lang.OutOfMemoryError) {
                    messager.error(null, "main.out.of.memory");
                } else {
                messager.error(null, "main.exception_thrown",
                               docletClassName, methodName, exc.toString());
                    exc.getTargetException().printStackTrace();
                }
                throw new DocletInvokeException();
            } finally {
                Thread.currentThread().setContextClassLoader(savedCCL);
            }
    }
}
