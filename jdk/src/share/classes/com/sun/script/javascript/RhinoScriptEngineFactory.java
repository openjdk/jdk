/*
 * Copyright (c) 2005, 2006, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.script.javascript;
import javax.script.*;
import java.util.*;
import sun.org.mozilla.javascript.internal.*;
import com.sun.script.util.*;

/**
 * Factory to create RhinoScriptEngine
 *
 * @author Mike Grogan
 * @since 1.6
 */
public class RhinoScriptEngineFactory extends ScriptEngineFactoryBase {

    public RhinoScriptEngineFactory() {
    }

    public List<String> getExtensions() {
        return extensions;
    }

    public List<String> getMimeTypes() {
        return mimeTypes;
    }

    public List<String> getNames() {
        return names;
    }

    public Object getParameter(String key) {
        if (key.equals(ScriptEngine.NAME)) {
            return "javascript";
        } else if (key.equals(ScriptEngine.ENGINE)) {
            return "Mozilla Rhino";
        } else if (key.equals(ScriptEngine.ENGINE_VERSION)) {
            return "1.7 release 3 PRERELEASE";
        } else if (key.equals(ScriptEngine.LANGUAGE)) {
            return "ECMAScript";
        } else if (key.equals(ScriptEngine.LANGUAGE_VERSION)) {
            return "1.8";
        } else if (key.equals("THREADING")) {
            return "MULTITHREADED";
        } else {
            throw new IllegalArgumentException("Invalid key");
        }
    }

    public ScriptEngine getScriptEngine() {
        RhinoScriptEngine ret = new RhinoScriptEngine();
        ret.setEngineFactory(this);
        return ret;
    }

    public String getMethodCallSyntax(String obj, String method, String... args) {

        String ret = obj + "." + method + "(";
        int len = args.length;
        if (len == 0) {
            ret += ")";
            return ret;
        }

        for (int i = 0; i < len; i++) {
            ret += args[i];
            if (i != len - 1) {
                ret += ",";
            } else {
                ret += ")";
            }
        }
        return ret;
    }

    public String getOutputStatement(String toDisplay) {
        StringBuffer buf = new StringBuffer();
        int len = toDisplay.length();
        buf.append("print(\"");
        for (int i = 0; i < len; i++) {
            char ch = toDisplay.charAt(i);
            switch (ch) {
            case '"':
                buf.append("\\\"");
                break;
            case '\\':
                buf.append("\\\\");
                break;
            default:
                buf.append(ch);
                break;
            }
        }
        buf.append("\")");
        return buf.toString();
    }

    public String getProgram(String... statements) {
        int len = statements.length;
        String ret = "";
        for (int i = 0; i < len; i++) {
            ret += statements[i] + ";";
        }

        return ret;
    }

    /*
    public static void main(String[] args) {
        RhinoScriptEngineFactory fact = new RhinoScriptEngineFactory();
        System.out.println(fact.getParameter(ScriptEngine.ENGINE_VERSION));
    }
    */

    private static List<String> names;
    private static List<String> mimeTypes;
    private static List<String> extensions;

    static {
        names = new ArrayList<String>(6);
        names.add("js");
        names.add("rhino");
        names.add("JavaScript");
        names.add("javascript");
        names.add("ECMAScript");
        names.add("ecmascript");
        names = Collections.unmodifiableList(names);

        mimeTypes = new ArrayList<String>(4);
        mimeTypes.add("application/javascript");
        mimeTypes.add("application/ecmascript");
        mimeTypes.add("text/javascript");
        mimeTypes.add("text/ecmascript");
        mimeTypes = Collections.unmodifiableList(mimeTypes);

        extensions = new ArrayList<String>(1);
        extensions.add("js");
        extensions = Collections.unmodifiableList(extensions);
    }
}
