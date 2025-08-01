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

/**
 * The {@link CodeFrame} represents a frame (i.e. scope) of code, appending {@link Code} to the {@code 'codeList'}
 * as {@link Token}s are rendered, and adding names to the {@link NameSet}s with {@link Template#addStructuralName}/
 * {@link Template#addDataName}. {@link Hook}s can be added to a frame, which allows code to be inserted at that
 * location later. When a {@link Hook} is {@link Hook#anchor}ed, it separates the Template into an outer and inner
 * {@link CodeFrame}, ensuring that names that are added inside the inner frame are only available inside that frame.
 *
 * <p>
 * On the other hand, each {@link TemplateFrame} represents the frame (or scope) of exactly one use of a
 * Template.
 *
 * <p>
 * For simple Template nesting, the {@link CodeFrame}s and {@link TemplateFrame}s overlap exactly.
 * However, when using {@link Hook#insert}, we simply nest {@link TemplateFrame}s, going further "in",
 * but we jump to an outer {@link CodeFrame}, ensuring that we insert {@link Code} at the outer frame,
 * and operating on the names of the outer frame. Once the {@link Hook#insert}ion is complete, we jump
 * back to the caller {@link TemplateFrame} and {@link CodeFrame}.
 */
class CodeFrame {
    public final CodeFrame parent;
    private final List<Code> codeList = new ArrayList<>();
    private final Map<Hook, Code.CodeList> hookCodeLists = new HashMap<>();

    /**
     * The {@link NameSet} is used for variable and fields etc.
     */
    private final NameSet names;

    private CodeFrame(CodeFrame parent, boolean isTransparentForNames) {
        this.parent = parent;
        if (parent == null) {
            // NameSet without any parent.
            this.names = new NameSet(null);
        } else if (isTransparentForNames) {
            // We use the same NameSet as the parent - makes it transparent.
            this.names = parent.names;
        } else {
            // New NameSet, to make sure we have a nested scope for the names.
            this.names = new NameSet(parent.names);
        }
    }

    /**
     * Creates a base frame, which has no {@link #parent}.
     */
    public static CodeFrame makeBase() {
        return new CodeFrame(null, false);
    }

    /**
     * Creates a normal frame, which has a {@link #parent} and which defines an inner
     * {@link NameSet}, for the names that are generated inside this frame. Once this
     * frame is exited, the name from inside this frame are not available anymore.
     */
    public static CodeFrame make(CodeFrame parent) {
        return new CodeFrame(parent, false);
    }

    /**
     * Creates a special frame, which has a {@link #parent} but uses the {@link NameSet}
     * from the parent frame, allowing {@link Template#addDataName}/
     * {@link Template#addStructuralName} to persist in the outer frame when the current frame
     * is exited. This is necessary for {@link Hook#insert},  where we would possibly want to
     * make field or variable definitions during the insertion that are not just local to the
     * insertion but affect the {@link CodeFrame} that we {@link Hook#anchor} earlier and are
     * now {@link Hook#insert}ing into.
     */
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
            // This should never happen, as we add a dedicated CodeFrame for each hook.
            throw new RuntimeException("Internal error: Duplicate Hook in CodeFrame: " + hook.name());
        }
        hookCodeLists.put(hook, new Code.CodeList(new ArrayList<>()));
    }

    private boolean hasHook(Hook hook) {
        return hookCodeLists.containsKey(hook);
    }

    CodeFrame codeFrameForHook(Hook hook) {
        CodeFrame current = this;
        while (current != null) {
            if (current.hasHook(hook)) {
                return current;
            }
            current = current.parent;
        }
        return null;
    }

    void addName(Name name) {
        names.add(name);
    }

    Name sampleName(NameSet.Predicate predicate) {
        return names.sample(predicate);
    }

    int countNames(NameSet.Predicate predicate) {
        return names.count(predicate);
    }

    boolean hasAnyNames(NameSet.Predicate predicate) {
        return names.hasAny(predicate);
    }

    List<Name> listNames(NameSet.Predicate predicate) {
        return names.toList(predicate);
    }

    Code getCode() {
        return new Code.CodeList(codeList);
    }
}
