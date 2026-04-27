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
 * The {@link CodeFrame} represents a frame (i.e. scope) of generated code by appending {@link Code} to the {@link #codeList}
 * as {@link Token}s are rendered, and adding names to the {@link NameSet}s with {@link Template#addStructuralName}/
 * {@link Template#addDataName}. {@link Hook}s can be added to a code frame, which allows code to be inserted at that
 * location later.
 *
 * <p>
 * The {@link CodeFrame} thus implements the {@link Name} non-transparency aspect of {@link ScopeToken}.
 *
 * <p>
 * The {@link CodeFrame}s are nested relative to the order of the final rendered code. This can
 * diverge from the nesting order of the {@link Template} when using {@link Hook#insert}, where
 * the execution jumps from the current (caller) {@link CodeFrame} scope to the scope of the
 * {@link Hook#anchor}. This ensures that the {@link Name}s of the anchor scope are accessed,
 * and not the ones from the caller scope. Once the {@link Hook#insert}ion is complete, we
 * jump back to the caller {@link CodeFrame}.
 *
 * <p>
 * Note, that {@link CodeFrame}s and {@link TemplateFrame}s often go together. But they do diverge when
 * we call {@link Hook#insert}. On the {@link CodeFrame} side, the inserted scope is nested in the anchoring
 * scope, so that the inserted scope has access to the Names of the anchoring scope, and not the caller
 * scope. But the {@link TemplateFrame} of the inserted scope is nested in the caller scope, so
 * that the inserted scope has access to hashtag replacements of the caller scope, and not the
 * anchoring scope.
 */

/*
 * Below, we look at an example, and show the use of CodeFrames (c) and TemplateFrames (t).
 *
 * Explanations:
 *  - Generally, every scope has a CodeFrame and a TemplateFrame. There can be multiple
 *    scopes inside a Template, and so there can be multiple CodeFrames and TemplateFrames.
 *    In the drawing below, we draw the frames vertically, and give each a unique id.
 *  - When we nest scopes inside scopes, we create a new CodeFrame and a new TemplateFrame,
 *    and so they grow the same nested structure. Example: t3 is nested inside t2 and
 *    c3 is nested inside c2b.
 *  - The exception to this:
 *    - At a hook.anchor, there are two CodeFrames. The first one (e.g. c2a) we call the
 *      hook CodeFrame, it is kept empty until we insert code to the hook. The second
 *      (e.g. c2b) we call the inner CodeFrame of the anchoring, into which we keep
 *      generating the code that is inside the scope of the hook.anchor.
 *    - At a hook.insert, the TemplateFrame (e.g. t4) is nested into the caller (e.g. t3),
 *      while the CodeFrame (e.g. c4) is nested into the anchoring CodeFrame (e.g. c2a).
 *
 * Template(
 *   t1 c1
 *   t1 c1
 *   t1 c1  Anchoring Scope
 *   t1 c1  hook.anchor(scope(
 *   t1 c1  t2 c2a
 *   t1 c1  t2 c2a <------ CodeFrame nesting--------+
 *   t1 c1  t2 c2a         with generated code      |
 *   t1 c1  t2             and Names                |
 *   t1 c1  t2  ^                                   |
 *   t1 c1  t2  +- Two CodeFramees                  |
 *   t1 c1  t2  v                                   |
 *   t1 c1  t2                                      |
 *   t1 c1  t2 c2b                                  |
 *   t1 c1  t2 c2b                                  |
 *   t1 c1  t2 c2b     Caller Scope                 |
 *   t1 c1  t2 c2b ... scope(                       |
 *   t1 c1  t2 c2b ... t3 c3                        |     Insertion Scope
 *   t1 c1  t2 c2b ... t3 c3                        |     hook.insert(transparentScope(
 *   t1 c1  t2 c2b ... t3 c3                        |     t4     c4
 *   t1 c1  t2 c2b ... t3 c3                        +---- t4 ----c4
 *   t1 c1  t2 c2b ... t3 c3                              t4     c4
 *   t1 c1  t2 c2b ... t3 c3 <-- TemplateFrame nesting ---t4     c4
 *   t1 c1  t2 c2b ... t3 c3     with hashtag             t4     c4                            // t: Concerns Template Frame
 *   t1 c1  t2 c2b ... t3 c3     and setFuelCost          t4     c4                            // c: Concerns Code Frame
 *   t1 c1  t2 c2b ... t3 c3                              t4     c4 "use hashtag #x"           -> t: hashtag queried in Insertion (t4) and Caller Scope (t3)
 *   t1 c1  t2 c2b ... t3 c3                              t4     c4                               c: code added to Anchoring Scope (c2a)
 *   t1 c1  t2 c2b ... t3 c3                              t4     c4
 *   t1 c1  t2 c2b ... t3 c3                              t4     c4 let("x", 42)               -> t: hashtag definition escapes to Caller Scope (t3) because
 *   t1 c1  t2 c2b ... t3 c3                              t4     c4                                  Insertion Scope is transparent
 *   t1 c1  t2 c2b ... t3 c3                              t4     c4
 *   t1 c1  t2 c2b ... t3 c3                              t4     c4 dataNames(...)...sample()  -> c: sample from Insertion (c4) and Anchoring Scope (c2a)
 *   t1 c1  t2 c2b ... t3 c3                              t4     c4                                  (CodeFrame nesting: c2a -> c4)
 *   t1 c1  t2 c2b ... t3 c3                              t4     c4 addDataName(...)           -> c: names escape to the Caller Scope (c3) because
 *   t1 c1  t2 c2b ... t3 c3                              t4     c4                                  Insertion Scope is transparent
 *   t1 c1  t2 c2b ... t3 c3                              t4     c4
 *   t1 c1  t2 c2b ... t3 c3                              ))
 *   t1 c1  t2 c2b ... t3 c3
 *   t1 c1  t2 c2b ... t3 c3
 *   t1 c1  t2 c2b ... )
 *   t1 c1  t2 c2b
 *   t1 c1  t2 c2b
 *   t1 c1  ))
 *   t1 c1
 *   t1 c1
 * )
 *
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
     * Creates a normal frame, which has a {@link #parent}. It can either be
     * transparent for names, meaning that names are added and accessed to and
     * from an outer frame. Names that are added in a transparent frame are
     * still available in the outer frames, as far out as the next non-transparent
     * frame. If a frame is non-transparent, this frame defines an inner
     * {@link NameSet}, for the names that are generated inside this frame. Once
     * this frame is exited, the names from inside this frame are not available.
     */
    public static CodeFrame make(CodeFrame parent, boolean isTransparentForNames) {
        return new CodeFrame(parent, isTransparentForNames);
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
