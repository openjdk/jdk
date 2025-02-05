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
    private static Frame baseFrame = null;
    private static Frame currentFrame = null;

    static int variableId = 0;

    public static String render(TemplateUse templateUse) {
        // Check nobody else is using the Renderer.
        if (baseFrame != null) {
            throw new RendererException("Nested render not allowed.");
        }
        baseFrame = new Frame(null);
        currentFrame = baseFrame;

        renderTemplateUse(templateUse);

        // Ensure Frame consistency.
        if (baseFrame != currentFrame) {
            throw new RendererException("Renderer did not end up at base frame.");
        }

        // Collect Code to String.
        StringBuilder builder = new StringBuilder();
        baseFrame.getCode().renderTo(builder);
        String code = builder.toString();

        // Release the Renderer.
        baseFrame = null;

        return code;
    }

    static String variableName(String name) {
        return getCurrentFrame().variableName(name);
    }

    public static Nothing let(String key, Object value) {
        getCurrentFrame().addContext(key, value.toString());
        return Nothing.instance;
    }

    public static <T, R> R let(String key, T value, Function<T, R> block) {
        getCurrentFrame().addContext(key, value.toString());
        return block.apply(value);
    }

    // TODO fuel
    public static int depth() {
        return getCurrentFrame().depth();
    }

    private static Frame getCurrentFrame() {
        if (currentFrame == null) {
            // TODO update text
            throw new RendererException("A method such as $ or let was called outside a template rendering. Make sure you are not calling templates yourself, but use use().");
        }
        return currentFrame;
    }

    private static void renderTemplateUse(TemplateUse templateUse) {
        Frame frame = getCurrentFrame();

        templateUse.visitArguments((name, value) -> frame.addContext(name, value.toString()));
        InstantiatedTemplate it = templateUse.instantiate();
        for (Object token : it.tokens()) {
            renderToken(token);
        }

        if (frame != getCurrentFrame()) {
            throw new RendererException("Frame mismatch.");
        }
    }

    private static void renderToken(Object token) {
        Frame frame = getCurrentFrame();
        switch (token) {
            case Nothing x -> {}
            case String s ->  frame.addString(templateString(s, frame));
            case Integer s -> frame.addString(s.toString());
            case Long s ->    frame.addString(s.toString());
            case Double s ->  frame.addString(s.toString());
            case Float s ->   frame.addString(s.toString());
            case List l -> {
                for (Object t : l) {
                    renderToken(t);
                }
            }
            case Hook h -> {
                throw new RendererException("Do not use Hook directly, use Hook.set: " + h);
            }
            case HookUse(Hook hook, List<Object> tokens) -> {
                Frame outerFrame = getCurrentFrame();

                // We need a frame to which the hook can insert code. That way, name
                // definitions at the hook cannot excape the hookFrame.
                Frame hookFrame = new Frame(outerFrame);
                hookFrame.addHook(hook);

                // We need a frame where the tokens can be rendered. That way, name
                // definitions from the tokens cannot escape the innerFrame to the
                // hookFrame.
                Frame innerFrame = new Frame(hookFrame);
                currentFrame = innerFrame;
                // TODO render list, and verify frames are consistent.
                for (Object t : tokens) {
                    renderToken(t);
                }
                // Close the hookFrame and innerFrame. hookFrame code comes before the
                // innerFrame code from the tokens.
                currentFrame = outerFrame;
                currentFrame.addCode(hookFrame.getCode());
                currentFrame.addCode(innerFrame.getCode());
            }
            case HookInsert(Hook hook, TemplateUse t) -> {
                Frame hookFrame = frameForHook(hook);
                currentFrame = hookFrame; // switch to hook frame.
                renderTemplateUse(t);
                currentFrame = frame;     // switch back.
            }
            case TemplateUse t -> {
                // Use a nested Frame.
                currentFrame = new Frame(frame);
                renderTemplateUse(t);
                frame.addCode(currentFrame.getCode());
                currentFrame = frame;
            }
            default -> throw new RendererException("body contained unexpected token: " + token);
        }
    }

    private static String templateString(String s, Frame frame) {
        var temp = DOLLAR_NAME_PATTERN.matcher(s).replaceAll(
            (MatchResult result) -> variableName(result.group(1))
	);
        return HASHTAG_REPLACEMENT_PATTERN.matcher(temp).replaceAll(
            // We must escape "$", because it has a special meaning in replaceAll.
            (MatchResult result) -> frame.getContext(result.group(1)).replace("$", "\\$")
        );
    }

    private static Frame frameForHook(Hook hook) {
        Frame frame = getCurrentFrame();
        while (frame != null) {
            if (frame.hasHook(hook)) {
                return frame;
            }
            frame = frame.parent;
        }
        throw new RendererException("hook " + hook.name() + " was referenced but not found!");
    }
}
