/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.internal.ws.util;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.sun.xml.internal.ws.util.localization.Localizable;

/**
 * A helper class to invoke javac.
 *
 * @author WS Development Team
 */
public class JavaCompilerHelper extends ToolBase {

    public JavaCompilerHelper(OutputStream out) {
        super(out, " ");
        this.out = out;
    }

    public boolean compile(String[] args) {
        return internalCompile(args);
    }

    protected String getResourceBundleName() {
        return "com.sun.tools.internal.ws.resources.javacompiler";
    }

    protected boolean internalCompile(String[] args) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Class comSunToolsJavacMainClass = null;
        try {
            /* try to use the new compiler */
            comSunToolsJavacMainClass =
                    cl.loadClass("com.sun.tools.javac.Main");
            try {
                Method compileMethod =
                        comSunToolsJavacMainClass.getMethod(
                                "compile",
                                compile141MethodSignature);
                try {
                    Object result =
                            compileMethod.invoke(
                                    null,
                                    new Object[] { args, new PrintWriter(out)});
                    if (!(result instanceof Integer)) {
                        return false;
                    }
                    return ((Integer) result).intValue() == 0;
                } catch (IllegalAccessException e3) {
                    return false;
                } catch (IllegalArgumentException e3) {
                    return false;
                } catch (InvocationTargetException e3) {
                    return false;
                }
            } catch (NoSuchMethodException e2) {
                //tryout 1.3.1 signature
                return internalCompilePre141(args);
                //onError(getMessage("javacompiler.nosuchmethod.error", "getMethod(\"compile\", compile141MethodSignature)"));
                //return false;
            }
        } catch (ClassNotFoundException e) {
            onError(
                    getMessage(
                            "javacompiler.classpath.error",
                            "com.sun.tools.javac.Main"));
            return false;
        } catch (SecurityException e) {
            return false;
        }
    }

    protected boolean internalCompilePre141(String[] args) {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            try {
                    Class sunToolsJavacMainClass = cl.loadClass("sun.tools.javac.Main");
                    try {
                            Constructor constructor =
                                    sunToolsJavacMainClass.getConstructor(constructorSignature);
                            try {
                                    Object javacMain =
                                            constructor.newInstance(new Object[] { out, "javac" });
                                    Method compileMethod =
                                            sunToolsJavacMainClass.getMethod(
                                                    "compile",
                                                    compileMethodSignature);
                                    Object result =
                                            compileMethod.invoke(javacMain, new Object[] { args });
                                    if (!(result instanceof Boolean)) {
                                            return false;
                                    }
                                    return ((Boolean) result).booleanValue();
                            } catch (InstantiationException e4) {
                                    return false;
                            } catch (IllegalAccessException e4) {
                                    return false;
                            } catch (IllegalArgumentException e4) {
                                    return false;
                            } catch (InvocationTargetException e4) {
                                    return false;
                            }

                    } catch (NoSuchMethodException e3) {
                            onError(
                                    getMessage(
                                            "javacompiler.nosuchmethod.error",
                                            "getMethod(\"compile\", compileMethodSignature)"));
                            return false;
                    }
            } catch (ClassNotFoundException e2) {
                    return false;
            }
    }

    protected String getGenericErrorMessage() {
            return "javacompiler.error";
    }

    protected void run() {
    }

    protected boolean parseArguments(String[] args) {
            return false;
    }

    public void onError(Localizable msg) {
            report(getMessage("javacompiler.error", localizer.localize(msg)));
    }

    protected OutputStream out;

    protected static final Class[] compile141MethodSignature;
    protected static final Class[] constructorSignature;
    protected static final Class[] compileMethodSignature;

    static {
            compile141MethodSignature = new Class[2];
            compile141MethodSignature[0] = (new String[0]).getClass();
            compile141MethodSignature[1] = PrintWriter.class;
            //jdk version < 1.4.1 signature
            constructorSignature = new Class[2];
            constructorSignature[0] = OutputStream.class;
            constructorSignature[1] = String.class;
            compileMethodSignature = new Class[1];
            compileMethodSignature[0] = compile141MethodSignature[0]; // String[]

    }
}
