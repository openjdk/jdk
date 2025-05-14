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

/**
 * The {@link Renderer} class is used to keep track of the states during a nested
 * Template rendering. There can only be a single {@link Renderer} active
 * at any point, since there are static methods that reference {@link Renderer#getCurrent}.
 *
 * The {@link Renderer} instance keeps track of the current frames,
 * see {@link TemplateFrame} and {@link CodeFrame}.
 */
class Renderer {
    private static final Pattern DOLLAR_NAME_PATTERN = Pattern.compile("\\$([a-zA-Z_][a-zA-Z0-9_]*)");
    private static final Pattern HASHTAG_REPLACEMENT_PATTERN = Pattern.compile("#([a-zA-Z_][a-zA-Z0-9_]*)");

    /**
     * There can be at most one Renderer instance at any time. This is to avoid that users accidentally
     * render templates to strings, rather than letting them all render together.
     */
    private static Renderer renderer = null;

    private int nextTemplateFrameId;
    private TemplateFrame baseTemplateFrame;
    private TemplateFrame currentTemplateFrame;
    private CodeFrame baseCodeFrame;
    private CodeFrame currentCodeFrame;

    // We do not want any other instances, so we keep it private.
    private Renderer(float fuel) {
        nextTemplateFrameId = 0;
        baseTemplateFrame = TemplateFrame.makeBase(nextTemplateFrameId++, fuel);
        currentTemplateFrame = baseTemplateFrame;
        baseCodeFrame = CodeFrame.makeBase();
        currentCodeFrame = baseCodeFrame;
    }

    static Renderer getCurrent() {
        if (renderer == null) {
            throw new RendererException("A Template method such as '$' or 'let' was called outside a template rendering.");
        }
        return renderer;
    }

    static String render(FilledTemplate filledTemplate) {
        return render(filledTemplate, Template.DEFAULT_FUEL);
    }

    static String render(FilledTemplate filledTemplate, float fuel) {
        // Check nobody else is using the Renderer.
        if (renderer != null) {
            throw new RendererException("Nested render not allowed. Please only use 'fillWith' inside Templates, and call 'render' only once at the end.");
        }
        try {
            renderer = new Renderer(fuel);
            renderer.renderFilledTemplate(filledTemplate);
            renderer.checkFrameConsistencyAfterRendering();
            return renderer.collectCode();
        } finally {
            // Release the Renderer.
            renderer = null;
        }
    }

    private void checkFrameConsistencyAfterRendering() {
        // Ensure CodeFrame consistency.
        if (baseCodeFrame != currentCodeFrame) {
            throw new RuntimeException("Internal error: Renderer did not end up at base CodeFrame.");
        }
        // Ensure TemplateFrame consistency.
        if (baseTemplateFrame != currentTemplateFrame) {
            throw new RuntimeException("Internal error: Renderer did not end up at base TemplateFrame.");
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

    void addHashtagReplacement(String key, Object value) {
        currentTemplateFrame.addHashtagReplacement(key, format(value));
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

    long weighNames(Name.Type type, boolean onlyMutable) {
        return currentCodeFrame.weighNames(type, onlyMutable);
    }

    Name sampleName(Name.Type type, boolean onlyMutable) {
        return currentCodeFrame.sampleName(type, onlyMutable);
    }

    /**
     * Formats values to {@link String} with the goal of using them in Java code.
     * By default we use the overrides of {@link Object#toString}.
     * But for some boxed primitives we need to create a special formatting.
     */
    static String format(Object value) {
        return switch (value) {
            case String s -> s;
            case Integer i -> i.toString();
            // We need to append the "L" so that the values are not interpreted as ints,
            // and then javac might complain that the values are too large for an int.
            case Long l -> l.toString() + "L";
            // Some Float and Double values like Infinity and NaN need a special representation.
            case Float f -> formatFloat(f);
            case Double d -> formatDouble(d);
            default -> value.toString();
        };
    }

    private static String formatFloat(Float f) {
        if (Float.isFinite(f)) {
            return f.toString() + "f";
        } else if (f.isNaN()) {
            return "Float.intBitsToFloat(" + Float.floatToRawIntBits(f) + " /* NaN */)";
        } else if (f.isInfinite()) {
            if (f > 0) {
                return "Float.POSITIVE_INFINITY";
            } else {
                return "Float.NEGATIVE_INFINITY";
            }
        } else {
            throw new RuntimeException("Not handled: " + f);
        }
    }

    private static String formatDouble(Double d) {
        if (Double.isFinite(d)) {
            return d.toString();
        } else if (d.isNaN()) {
            return "Double.longBitsToDouble(" + Double.doubleToRawLongBits(d) + "L /* NaN */)";
        } else if (d.isInfinite()) {
            if (d > 0) {
                return "Double.POSITIVE_INFINITY";
            } else {
                return "Double.NEGATIVE_INFINITY";
            }
        } else {
            throw new RuntimeException("Not handled: " + d);
        }
    }

    private void renderFilledTemplate(FilledTemplate filledTemplate) {
        TemplateFrame templateFrame = TemplateFrame.make(currentTemplateFrame, nextTemplateFrameId++);
        currentTemplateFrame = templateFrame;

        filledTemplate.visitArguments((name, value) -> addHashtagReplacement(name, format(value)));
        TemplateBody body = filledTemplate.instantiate();
        renderTokenList(body.tokens());

        if (currentTemplateFrame != templateFrame) {
            throw new RuntimeException("Internal error: TemplateFrame mismatch!");
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
                // definitions at the hook cannot escape the hookCodeFrame.
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
            case HookInsertToken(Hook hook, FilledTemplate t) -> {
                // Switch to hook CodeFrame.
                CodeFrame callerCodeFrame = currentCodeFrame;
                CodeFrame hookCodeFrame = codeFrameForHook(hook);

                // Use a transparent nested CodeFrame. We need a CodeFrame so that the code generated
                // by the FilledTemplate can be collected, and hook insertions from it can still
                // be made to the hookCodeFrame before the code from the FilledTemplate is added to
                // the hookCodeFrame.
                // But the CodeFrame must be transparent, so that its name definitions go out to
                // the hookCodeFrame, and are not limited to the CodeFrame for the FilledTemplate.
                currentCodeFrame = CodeFrame.makeTransparentForNames(hookCodeFrame);

                renderFilledTemplate(t);

                hookCodeFrame.addCode(currentCodeFrame.getCode());

                // Switch back from hook CodeFrame to caller CodeFrame.
                currentCodeFrame = callerCodeFrame;
            }
            case FilledTemplate t -> {
                // Use a nested CodeFrame.
                CodeFrame callerCodeFrame = currentCodeFrame;
                currentCodeFrame = CodeFrame.make(currentCodeFrame);

                renderFilledTemplate(t);

                callerCodeFrame.addCode(currentCodeFrame.getCode());
                currentCodeFrame = callerCodeFrame;
            }
            case AddNameToken(Name name) -> {
                currentCodeFrame.addName(name);
            }
        }
    }

    private void renderTokenList(List<Token> tokens) {
        CodeFrame codeFrame = currentCodeFrame;
        for (Token t : tokens) {
            renderToken(t);
        }
        if (codeFrame != currentCodeFrame) {
            throw new RuntimeException("Internal error: CodeFrame mismatch.");
        }
    }

    private String templateString(String s) {
        var temp = DOLLAR_NAME_PATTERN.matcher(s).replaceAll(
            (MatchResult result) -> $(result.group(1))
        );
        return HASHTAG_REPLACEMENT_PATTERN.matcher(temp).replaceAll(
            // We must escape "$", because it has a special meaning in replaceAll.
            (MatchResult result) -> getHashtagReplacement(result.group(1)).replace("\\", "\\\\").replace("$", "\\$")
        );
    }

    boolean isSet(Hook hook) {
        return currentCodeFrame.codeFrameForHook(hook) != null;
    }

    private CodeFrame codeFrameForHook(Hook hook) {
        CodeFrame codeFrame = currentCodeFrame.codeFrameForHook(hook);
        if (codeFrame == null) {
            throw new RendererException("Hook '" + hook.name() + "' was referenced but not found!");
        }
        return codeFrame;
    }
}
