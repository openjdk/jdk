/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package compiler.lib.template_framework;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

class Frame {
    public final Frame parent;
    private final List<Code> codeList = new ArrayList<Code>();
    private final Map<Hook, Code.CodeList> hookCodeLists = new HashMap<>();

    private final Map<String, String> variableNames = new HashMap<>();
    private final Map<String, String> context = new HashMap<>();

    Frame(Frame parent) {
        this.parent = parent;
    }

    public int depth() {
        if (parent == null) { return 0; }
        return parent.depth() + 1;
    }

    void addString(String s) {
        codeList.add(new Code.Token(s));
    }

    void addCode(Code code) {
        codeList.add(code);
    }

    void addHook(Hook hook) {
        if (hasHook(hook)) {
            throw new RendererException("Duplicate Hook in Template: " + hook.name());
        }
        hookCodeLists.put(hook, new Code.CodeList(new ArrayList<Code>()));
    }

    boolean hasHook(Hook hook) {
        return hookCodeLists.containsKey(hook);
    }

    public void addContext(String key, String value) {
        context.put(key, value);
    }

    public String getContext(String key) {
        if (context.containsKey(key)) {
            return context.get(key);
        }
        throw new RendererException("Tried to interpolate field " + key + " which does not exist.");
    }

    String variableName(String name) {
        return variableNames.computeIfAbsent(name, s -> name + Renderer.variableId++);
    }

    // TODO ensure only use once!
    Code getCode() {
        return new Code.CodeList(codeList);
    }
}
