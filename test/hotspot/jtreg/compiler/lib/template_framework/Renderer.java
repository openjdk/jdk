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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public abstract class Renderer {
    public static final class Nothing {
        public static final Nothing instance = new Nothing();

        private Nothing() {}
    }

    private static final Pattern DOLLAR_NAME_PATTERN = Pattern.compile("\\$([a-zA-Z_][a-zA-Z0-9_]*)");
    private static final Pattern HASHTAG_REPLACEMENT_PATTERN = Pattern.compile("#([a-zA-Z_][a-zA-Z0-9_]*)");
    private static final ArrayList<Frame> STACK = new ArrayList<Frame>();

    static int variableId = 0;

    public static String render(TemplateUse templateUse) {
        if (!STACK.isEmpty()) {
            throw new RendererException("Nested render not allowed");
        }
        return renderTemplateUse(templateUse);
    }

    public static String $(String name) {
        return currentStackFrame().variableName(name);
    }

    public static Nothing let(String key, Object value) {
        currentStackFrame().addContext(key, value.toString());
        return Nothing.instance;
    }

    public static <T, R> R let(String key, T value, Function<T, R> block) {
        currentStackFrame().addContext(key, value.toString());
        return block.apply(value);
    }

    public static int depth() {
        return STACK.size();
    }

    private static Frame currentStackFrame() {
        if (STACK.isEmpty()) {
            throw new RendererException("A method such as $ or let was called outside a template rendering. Make sure you are not calling templates yourself, but use use().");
        }
        return STACK.getLast();
    }

    private static String renderTemplateUse(TemplateUse templateUse) {
        Frame frame = new Frame();
        STACK.add(frame);
        templateUse.visitArguments((name, value) -> frame.addContext(name, value.toString()));
        InstantiatedTemplate it = templateUse.instantiate();
        for (Object e : it.elements()) {
            renderElement(frame, e);
        }
        STACK.removeLast();
        return frame.toString();
    }

    private static void renderElement(Frame frame, Object element) {
        switch (element) {
            case Nothing x -> {}
            case String s ->  frame.addString(templateString(s, frame));
            case Integer s -> frame.addString(s.toString());
            case Long s ->    frame.addString(s.toString());
            case Double s ->  frame.addString(s.toString());
            case Float s ->   frame.addString(s.toString());
            case Hook h ->    frame.addHook(h);
            case List l -> {
                for (Object e : l) {
                    renderElement(frame, e);
                }
            }
            case HookInsert h ->
                    frameForHook(h.hook()).insertIntoHook(h.hook(), render(h.templateUse()));
            case TemplateUse t -> frame.addString(renderTemplateUse(t));
            default -> throw new RendererException("body contained unexpected element: " + element);
        }
    }

    private static String templateString(String s, Frame frame) {
        var temp = DOLLAR_NAME_PATTERN.matcher(s).replaceAll((MatchResult result) -> $(result.group(1)));
        return HASHTAG_REPLACEMENT_PATTERN.matcher(temp).replaceAll((MatchResult result) -> frame.getContext(result.group(1)));
    }

    private static Frame frameForHook(Hook hook) {
        for (int i = STACK.size() - 1; i >= 0; i--) {
            Frame frame = STACK.get(i);
            if (frame.hasHook(hook)) {
                return frame;
            }
        }
        throw new RuntimeException("hook " + hook.name() + " was referenced but not found!");
    }
}
