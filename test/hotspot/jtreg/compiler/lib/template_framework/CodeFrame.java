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

class CodeFrame {
    public final CodeFrame parent;
    private final List<Code> codeList = new ArrayList<Code>();
    private final Map<Hook, Code.CodeList> hookCodeLists = new HashMap<>();

    final NameSet mutableNames;
    final NameSet allNames;

    private CodeFrame(CodeFrame parent, boolean isTransparentForNames) {
        this.parent = parent;
        if (parent == null) {
            // NameSets without any parent.
            this.mutableNames = new NameSet(null);
            this.allNames     = new NameSet(null);
        } else if (isTransparentForNames) {
            // We use the same NameSets as the parent - makes it transparent.
            this.mutableNames = parent.mutableNames;
            this.allNames     = parent.allNames;
        } else {
            // New NameSets, to make sure we have a nested scope for the names.
            this.mutableNames = new NameSet(parent.mutableNames);
            this.allNames     = new NameSet(parent.allNames);
        }
    }

    public static CodeFrame makeBase() {
        return new CodeFrame(null, false);
    }

    public static CodeFrame make(CodeFrame parent) {
        return new CodeFrame(parent, false);
    }

    public static CodeFrame makeTransparentForNames(CodeFrame parent) {
        return new CodeFrame(parent, true);
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

    private NameSet nameSet(NameSelection nameSelection) {
        if (nameSelection == NameSelection.MUTABLE) {
            return mutableNames;
        } else {
            return allNames;
        }
    }

    void defineName(String name, Object type, NameSelection nameSelection) {
        nameSet(nameSelection).add(name, type);
    }

    int countNames(Object type, NameSelection nameSelection) {
        return nameSet(nameSelection).count(type);
    }

    String sampleName(Object type, NameSelection nameSelection) {
        return nameSet(nameSelection).sample(type);
    }

    // TODO ensure only use once!
    Code getCode() {
        return new Code.CodeList(codeList);
    }
}
