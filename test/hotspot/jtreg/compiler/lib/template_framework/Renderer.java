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
    private static final Pattern DOLLAR_NAME_PATTERN = Pattern.compile("\\$([a-zA-Z_][a-zA-Z0-9_]*)");
    private static final Pattern HASHTAG_REPLACEMENT_PATTERN = Pattern.compile("#([a-zA-Z_][a-zA-Z0-9_]*)");

    // TODO describe
    private static CodeFrame baseCodeFrame = null;
    private static CodeFrame currentCodeFrame = null;

    private static int nextTemplateId = 0; // TODO refactor

    static TemplateFrame baseTemplateFrame = null;
    static TemplateFrame currentTemplateFrame = null;

    public static String render(TemplateWithArgs templateWithArgs) {
        // Check nobody else is using the Renderer.
        if (baseCodeFrame != null) {
            throw new RendererException("Nested render not allowed.");
        }

        // Setup the Renderer.
        baseCodeFrame = new CodeFrame(null);
        currentCodeFrame = baseCodeFrame;
        nextTemplateId = 0;
        baseTemplateFrame = new TemplateFrame(null, nextTemplateId++);
        currentTemplateFrame = baseTemplateFrame;

        renderTemplateWithArgs(templateWithArgs);

        // Ensure CodeFrame consistency.
        if (baseCodeFrame != currentCodeFrame) {
            throw new RendererException("Renderer did not end up at base CodeFrame.");
        }
        // Ensure TemplateFrame consistency.
        if (baseTemplateFrame != currentTemplateFrame) {
            throw new RendererException("Renderer did not end up at base TemplateFrame.");
        }

        // Collect Code to String.
        StringBuilder builder = new StringBuilder();
        baseCodeFrame.getCode().renderTo(builder);
        String code = builder.toString();

        // Release the Renderer.
        baseCodeFrame = null;
        currentCodeFrame = null;
        baseTemplateFrame = null;
        currentTemplateFrame = null;

        return code;
    }

    static String $(String name) {
        return getCurrentTemplateFrame().$(name);
    }

    // TODO
    //public static <T, R> R let(String key, T value, Function<T, R> block) {
    //    getCurrentFrame().addContext(key, value.toString());
    //    return block.apply(value);
    //}

    // TODO fuel - based on codeFrame or templateFrame?
    public static int depth() {
        return getCurrentCodeFrame().depth();
    }

    private static CodeFrame getCurrentCodeFrame() {
        if (currentCodeFrame == null) {
            // TODO update text - which methods are involved?
            throw new RendererException("A method such as $ or let was called outside a template rendering. Make sure you are not calling templates yourself, but use use().");
        }
        return currentCodeFrame;
    }

    private static TemplateFrame getCurrentTemplateFrame() {
        if (currentTemplateFrame == null) {
            // TODO update text - which methods are involved?
            throw new RendererException("A method such as $ or let was called outside a template rendering. Make sure you are not calling templates yourself, but use use().");
        }
        return currentTemplateFrame;
    }

    private static void renderTemplateWithArgs(TemplateWithArgs templateWithArgs) {
        TemplateFrame templateFrame = new TemplateFrame(getCurrentTemplateFrame(), nextTemplateId++);
        currentTemplateFrame = templateFrame;

        templateWithArgs.visitArguments((name, value) -> templateFrame.addHashtagReplacement(name, value.toString()));
        InstantiatedTemplate it = templateWithArgs.instantiate();
        renderTokenList(it.tokens());

        if (currentTemplateFrame != templateFrame) {
            throw new RendererException("TemplateFrame mismatch!");
        }
        currentTemplateFrame = currentTemplateFrame.parent;
    }

    private static void renderToken(Token token) {
        CodeFrame codeFrame = getCurrentCodeFrame();
        switch (token) {
            case StringToken(String s) ->  codeFrame.addString(templateString(s));
            case LetToken(String key, String value) -> {
                getCurrentTemplateFrame().addHashtagReplacement(key, value.toString());
            }
            case HookSetToken(Hook hook, List<Token> tokens) -> {
                // TODO describe and maybe rename to HookSetUse
                CodeFrame outerCodeFrame = getCurrentCodeFrame();

                // We need a CodeFrame to which the hook can insert code. That way, name
                // definitions at the hook cannot excape the hookCodeFrame.
                CodeFrame hookCodeFrame = new CodeFrame(outerCodeFrame);
                hookCodeFrame.addHook(hook);

                // We need a CodeFrame where the tokens can be rendered. That way, name
                // definitions from the tokens cannot escape the innerCodeFrame to the
                // hookCodeFrame.
                CodeFrame innerCodeFrame = new CodeFrame(hookCodeFrame);
                currentCodeFrame = innerCodeFrame;

                renderTokenList(tokens);

                // Close the hookCodeFrame and innerCodeFrame. hookCodeFrame code comes before the
                // innerCodeFrame code from the tokens.
                currentCodeFrame = outerCodeFrame;
                currentCodeFrame.addCode(hookCodeFrame.getCode());
                currentCodeFrame.addCode(innerCodeFrame.getCode());
            }
            case HookIntoToken(Hook hook, TemplateWithArgs t) -> {
                // TODO describe and maybe rename to IntoHookUse
                // Switch to hook CodeFrame.
                CodeFrame hookCodeFrame = codeFrameForHook(hook);

                // Use a transparent nested CodeFrame. We need a CodeFrame so that the code generated
                // by the TemplateWithArgs can be collected, and hook insertions from it can still
                // be made to the hookCodeFrame before the code from the TemplateWithArgs is added to
                // the hookCodeFrame.
                // But the CodeFrame must be transparent, so that its name definitions go out to
                // the hookCodeFrame, and are not limited to the CodeFrame for the TemplateWithArgs.
                currentCodeFrame = new CodeFrame(hookCodeFrame);
                // TODO make transparent for names

                renderTemplateWithArgs(t);

                hookCodeFrame.addCode(currentCodeFrame.getCode());

                // Switch back from hook CodeFrame to caller CodeFrame.
                currentCodeFrame = codeFrame;
            }
            case TemplateWithArgs t -> {
                // Use a nested CodeFrame.
                currentCodeFrame = new CodeFrame(codeFrame);

                renderTemplateWithArgs(t);

                codeFrame.addCode(currentCodeFrame.getCode());
                currentCodeFrame = codeFrame;
            }
        }
    }

    private static void renderTokenList(List<Token> tokens) {
        CodeFrame codeFrame = getCurrentCodeFrame();
        for (Token t : tokens) {
            renderToken(t);
        }
        if (codeFrame != getCurrentCodeFrame()) {
            throw new RendererException("CodeFrame mismatch.");
        }
    }

    private static String templateString(String s) {
        var temp = DOLLAR_NAME_PATTERN.matcher(s).replaceAll(
            (MatchResult result) -> getCurrentTemplateFrame().$(result.group(1))
	);
        return HASHTAG_REPLACEMENT_PATTERN.matcher(temp).replaceAll(
            // We must escape "$", because it has a special meaning in replaceAll.
            (MatchResult result) -> getCurrentTemplateFrame().getHashtagReplacement(result.group(1)).replace("$", "\\$")
        );
    }

    private static CodeFrame codeFrameForHook(Hook hook) {
        CodeFrame codeFrame = getCurrentCodeFrame();
        while (codeFrame != null) {
            if (codeFrame.hasHook(hook)) {
                return codeFrame;
            }
            codeFrame = codeFrame.parent;
        }
        throw new RendererException("hook " + hook.name() + " was referenced but not found!");
    }
}
