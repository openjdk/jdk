/*
 * Copyright (c) 1998, 2007, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.hotspot.igv.rhino;

import com.sun.hotspot.igv.filter.ScriptEngineAbstraction;
import com.sun.hotspot.igv.graph.Diagram;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 *
 * @author Thomas Wuerthinger
 */
public class RhinoScriptEngine implements ScriptEngineAbstraction {

    private String jsHelperText;
    private Constructor importer;
    private Method scope_put;
    private Method cx_evaluateString;
    private Method context_enter;
    private Method context_exit;

    public boolean initialize(String s) {
        this.jsHelperText = s;
        Class importerTopLevel = null;
        try {
            ClassLoader cl = RhinoScriptEngine.class.getClassLoader();
            Class context = cl.loadClass("org.mozilla.javascript.Context");
            Class scriptable = cl.loadClass("org.mozilla.javascript.Scriptable");
            importerTopLevel = cl.loadClass("org.mozilla.javascript.ImporterTopLevel");
            importer = importerTopLevel.getDeclaredConstructor(context);
            scope_put = importerTopLevel.getMethod("put", new Class[]{String.class, scriptable, Object.class});
            cx_evaluateString = context.getDeclaredMethod("evaluateString", new Class[]{scriptable, String.class, String.class, Integer.TYPE, Object.class});
            context_enter = context.getDeclaredMethod("enter", new Class[0]);
            context_exit = context.getDeclaredMethod("exit", new Class[0]);
            return true;
        } catch (NoSuchMethodException nsme) {
            return false;
        } catch (ClassNotFoundException cnfe) {
            return false;
        }
    }

    public void execute(Diagram d, String code) {
        try {
            Object cx = context_enter.invoke(null, (Object[]) null);
            try {
                Object scope = importer.newInstance(cx);
                scope_put.invoke(scope, "IO", scope, System.out);
                scope_put.invoke(scope, "graph", scope, d);
                cx_evaluateString.invoke(cx, scope, jsHelperText, "jsHelper.js", 1, null);
                cx_evaluateString.invoke(cx, scope, code, "<cmd>", 1, null);
            } finally {
                // Exit from the context.
                context_exit.invoke(null, (Object[]) null);
            }
        } catch (InvocationTargetException iae) {
        } catch (IllegalAccessException iae) {
        } catch (InstantiationException iae) {
        }
    }
}
