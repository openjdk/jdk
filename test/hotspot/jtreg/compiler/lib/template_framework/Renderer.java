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
import java.util.Random;

import jdk.test.lib.Utils;

public class Renderer {
    private static final Pattern DOLLAR_NAME_PATTERN = Pattern.compile("\\$([a-zA-Z_][a-zA-Z0-9_]*)");
    private static final Pattern HASHTAG_REPLACEMENT_PATTERN = Pattern.compile("#([a-zA-Z_][a-zA-Z0-9_]*)");

    static final Random RANDOM = Utils.getRandomInstance();

    /**
     * There can be at most one Renderer instance at any time. This is to avoid that users accidentally
     * render templates to strings, rather than letting them all render together.
     */
    private static Renderer renderer = null;

    // TODO describe
    private int nextTemplateFrameId;
    private TemplateFrame baseTemplateFrame;
    private TemplateFrame currentTemplateFrame;
    private CodeFrame baseCodeFrame;
    private CodeFrame currentCodeFrame;

    // We do not want any other instances.
    private Renderer(float fuel) {
        nextTemplateFrameId = 0;
        baseTemplateFrame = TemplateFrame.makeBase(nextTemplateFrameId++, fuel);
        currentTemplateFrame = baseTemplateFrame;
        baseCodeFrame = CodeFrame.makeBase();
        currentCodeFrame = baseCodeFrame;
    }

    static Renderer getCurrent() {
        if (renderer == null) {
            // TODO update text - which methods are involved?
            throw new RendererException("A method such as $ or let was called outside a template rendering. Make sure you are not calling templates yourself, but use use().");
        }
        return renderer;
    }

    static String render(TemplateWithArgs templateWithArgs) {
        return render(templateWithArgs, TemplateFrame.DEFAULT_FUEL);
    }

    static String render(TemplateWithArgs templateWithArgs, float fuel) {
        // Check nobody else is using the Renderer.
        if (renderer != null) {
            throw new RendererException("Nested render not allowed. Please only use 'withArgs' inside Templates, and call 'render' only once at the end.");
        }

        renderer = new Renderer(fuel);
        renderer.renderTemplateWithArgs(templateWithArgs);
        renderer.checkFrameConsistencyAfterRendering();
        String code = renderer.collectCode();

        // Release the Renderer.
        renderer = null;

        return code;
    }

    private void checkFrameConsistencyAfterRendering() {
        // Ensure CodeFrame consistency.
        if (baseCodeFrame != currentCodeFrame) {
            throw new RendererException("Renderer did not end up at base CodeFrame.");
        }
        // Ensure TemplateFrame consistency.
        if (baseTemplateFrame != currentTemplateFrame) {
            throw new RendererException("Renderer did not end up at base TemplateFrame.");
        }
    }

    private String collectCode() {
        StringBuilder builder = new StringBuilder();
        baseCodeFrame.getCode().renderTo(builder);
        return builder.toString();
    }

    String $(String name) {
        return currentTemplateFrame.$(name);
    }

    void addHashtagReplacement(String key, String value) {
        currentTemplateFrame.addHashtagReplacement(key, value);
    }

    private String getHashtagReplacement(String key) {
        return currentTemplateFrame.getHashtagReplacement(key);
    }

    float fuel() {
        return currentTemplateFrame.fuel;
    }

    void setFuelCost(float fuelCost) {
        currentTemplateFrame.setFuelCost(fuelCost);
    }

    void defineName(String name, Object type, NameSelection nameSelection) {
        currentCodeFrame.defineName(name, type, nameSelection);
    }

    int countNames(Object type, NameSelection nameSelection) {
        return currentCodeFrame.countNames(type, nameSelection);
    }

    String sampleName(Object type, NameSelection nameSelection) {
        return currentCodeFrame.sampleName(type, nameSelection);
    }

    private void renderTemplateWithArgs(TemplateWithArgs templateWithArgs) {
        TemplateFrame templateFrame = TemplateFrame.make(currentTemplateFrame, nextTemplateFrameId++);
        currentTemplateFrame = templateFrame;

        templateWithArgs.visitArguments((name, value) -> addHashtagReplacement(name, value.toString()));
        TemplateBody it = templateWithArgs.instantiate();
        renderTokenList(it.tokens());

        if (currentTemplateFrame != templateFrame) {
            throw new RendererException("TemplateFrame mismatch!");
        }
        currentTemplateFrame = currentTemplateFrame.parent;
    }

    private void renderToken(Token token) {
        switch (token) {
            case StringToken(String s) -> {
                currentCodeFrame.addString(templateString(s));
            }
            case NothingToken() -> {
                // Nothing.
            }
            case HookSetToken(Hook hook, List<Token> tokens) -> {
                CodeFrame outerCodeFrame = currentCodeFrame;

                // We need a CodeFrame to which the hook can insert code. That way, name
                // definitions at the hook cannot excape the hookCodeFrame.
                CodeFrame hookCodeFrame = CodeFrame.make(outerCodeFrame);
                hookCodeFrame.addHook(hook);

                // We need a CodeFrame where the tokens can be rendered. That way, name
                // definitions from the tokens cannot escape the innerCodeFrame to the
                // hookCodeFrame.
                CodeFrame innerCodeFrame = CodeFrame.make(hookCodeFrame);
                currentCodeFrame = innerCodeFrame;

                renderTokenList(tokens);

                // Close the hookCodeFrame and innerCodeFrame. hookCodeFrame code comes before the
                // innerCodeFrame code from the tokens.
                currentCodeFrame = outerCodeFrame;
                currentCodeFrame.addCode(hookCodeFrame.getCode());
                currentCodeFrame.addCode(innerCodeFrame.getCode());
            }
            case HookIntoToken(Hook hook, TemplateWithArgs t) -> {
                // Switch to hook CodeFrame.
                CodeFrame callerCodeFrame = currentCodeFrame;
                CodeFrame hookCodeFrame = codeFrameForHook(hook);

                // Use a transparent nested CodeFrame. We need a CodeFrame so that the code generated
                // by the TemplateWithArgs can be collected, and hook insertions from it can still
                // be made to the hookCodeFrame before the code from the TemplateWithArgs is added to
                // the hookCodeFrame.
                // But the CodeFrame must be transparent, so that its name definitions go out to
                // the hookCodeFrame, and are not limited to the CodeFrame for the TemplateWithArgs.
                currentCodeFrame = CodeFrame.makeTransparentForNames(hookCodeFrame);

                renderTemplateWithArgs(t);

                hookCodeFrame.addCode(currentCodeFrame.getCode());

                // Switch back from hook CodeFrame to caller CodeFrame.
                currentCodeFrame = callerCodeFrame;
            }
            case TemplateWithArgs t -> {
                // Use a nested CodeFrame.
                CodeFrame callerCodeFrame = currentCodeFrame;
                currentCodeFrame = CodeFrame.make(currentCodeFrame);

                renderTemplateWithArgs(t);

                callerCodeFrame.addCode(currentCodeFrame.getCode());
                currentCodeFrame = callerCodeFrame;
            }
        }
    }

    private void renderTokenList(List<Token> tokens) {
        CodeFrame codeFrame = currentCodeFrame;
        for (Token t : tokens) {
            renderToken(t);
        }
        if (codeFrame != currentCodeFrame) {
            throw new RendererException("CodeFrame mismatch.");
        }
    }

    private String templateString(String s) {
        var temp = DOLLAR_NAME_PATTERN.matcher(s).replaceAll(
            (MatchResult result) -> $(result.group(1))
	);
        return HASHTAG_REPLACEMENT_PATTERN.matcher(temp).replaceAll(
            // We must escape "$", because it has a special meaning in replaceAll.
            (MatchResult result) -> getHashtagReplacement(result.group(1)).replace("$", "\\$")
        );
    }

    private CodeFrame codeFrameForHook(Hook hook) {
        CodeFrame codeFrame = currentCodeFrame;
        while (codeFrame != null) {
            if (codeFrame.hasHook(hook)) {
                return codeFrame;
            }
            codeFrame = codeFrame.parent;
        }
        throw new RendererException("hook " + hook.name() + " was referenced but not found!");
    }
}
