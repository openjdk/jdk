/*
 * Copyright (c) 1998, 2022, Oracle and/or its affiliates. All rights reserved.
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
import javax.script.ScriptException;
import org.openide.cookies.OpenCookie;
import org.openide.util.Exceptions;

/**
 *
 * @author Thomas Wuerthinger
 */
public class CustomFilter extends AbstractFilter {

    private String code;
    private String name;
    private final ScriptEngine engine;

    public CustomFilter(String name, String code, ScriptEngine engine) {
        this.name = name;
        this.code = code;
        this.engine = engine;
        getProperties().setProperty("name", name);
    }

    @Override
    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public void setName(String s) {
        name = s;
    }

    public void setCode(String s) {
        code = s;
    }

    @Override
    public OpenCookie getEditor() {
        return this::openInEditor;
    }

    public boolean openInEditor() {
        EditFilterDialog dialog = new EditFilterDialog(CustomFilter.this);
        dialog.setVisible(true);
        boolean accepted = dialog.wasAccepted();
        if (accepted) {
            getChangedEvent().fire();
        }
        return accepted;
    }

    @Override
    public String toString() {
        return getName();
    }


    @Override
    public void apply(Diagram d) {
        try {
            Bindings b = engine.getContext().getBindings(ScriptContext.ENGINE_SCOPE);
            b.put("graph", d);
            engine.eval(code, b);
        } catch (ScriptException ex) {
            Exceptions.printStackTrace(ex);
        }
    }
}
