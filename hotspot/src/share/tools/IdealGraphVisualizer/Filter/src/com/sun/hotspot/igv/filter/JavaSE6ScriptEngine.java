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
package com.sun.hotspot.igv.filter;

import com.sun.hotspot.igv.graph.Diagram;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import org.openide.util.Exceptions;

/**
 *
 * @author Thomas Wuerthinger
 */
public class JavaSE6ScriptEngine implements ScriptEngineAbstraction {

    private ScriptEngine engine;
    private Bindings bindings;

    public boolean initialize(String jsHelperText) {
        try {
            ScriptEngineManager sem = new ScriptEngineManager();
            ScriptEngine e = sem.getEngineByName("ECMAScript");
            engine = e;
            e.eval(jsHelperText);
            Bindings b = e.getContext().getBindings(ScriptContext.ENGINE_SCOPE);
            b.put("IO", System.out);
            bindings = b;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void execute(Diagram d, String code) {
        try {
            Bindings b = bindings;
            b.put("graph", d);
            engine.eval(code, b);
        } catch (ScriptException ex) {
            Exceptions.printStackTrace(ex);
        }
    }
}
