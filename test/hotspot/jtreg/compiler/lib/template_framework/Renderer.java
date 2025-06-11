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

import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The {@link Renderer} class renders a tokenized {@link Template} in the form of a {@link TemplateToken}.
 * It also keeps track of the states during a nested Template rendering. There can only be a single
 * {@link Renderer} active at any point, since there are static methods that reference
 * {@link Renderer#getCurrent}.
 *
 * <p>
 * The {@link Renderer} instance keeps track of the current frames.
 *
 * @see TemplateFrame
 * @see CodeFrame
 */
final class Renderer {
    private static final String NAME_CHARACTERS = "[a-zA-Z_][a-zA-Z0-9_]*";
    private static final Pattern NAME_PATTERN = Pattern.compile(
        // We are parsing patterns:
        //   #name
        //   #{name}
        //   $name
        //   ${name}
        // But the "#" or "$" have already been removed, and the String
        // starts at the character after that.
        // The pattern must be at the beginning of the String part.
        "^" +
        // We either have "name" or "{name}"
        "(?:" + // non-capturing group for the OR
            // capturing group for "name"
            "(" + NAME_CHARACTERS + ")" +
        "|" + // OR
            // We want to trim off the brackets, so have
            // another non-capturing group.
            "(?:\\{" +
                // capturing group for "name" inside "{name}"
                "(" + NAME_CHARACTERS + ")" +
            "\\})" +
        ")");
    private static final Pattern NAME_CHARACTERS_PATTERN = Pattern.compile("^" + NAME_CHARACTERS + "$");

    static boolean isValidHashtagOrDollarName(String name) {
        return NAME_CHARACTERS_PATTERN.matcher(name).find();
    }

    /**
     * There can be at most one Renderer instance at any time.
     *
     * <p>
     * When using nested templates, the user of the Template Framework may be tempted to first render
     * the nested template to a {@link String}, and then use this {@link String} as a token in an outer
     * {@link Template#body}. This would be a bad pattern: the outer and nested {@link Template} would
     * be rendered separately, and could not interact. For example, the nested {@link Template} would
     * not have access to the scopes of the outer {@link Template}. The inner {@link Template} could
     * not access {@link Name}s and {@link Hook}s from the outer {@link Template}. The user might assume
     * that the inner {@link Template} has access to the outer {@link Template}, but they would actually
     * be separated. This could lead to unexpected behavior or even bugs.
     *
     * <p>
     * Instead, the user should create a {@link TemplateToken} from the inner {@link Template}, and
     * use that {@link TemplateToken} in the {@link Template#body} of the outer {@link Template}.
     * This way, the inner and outer {@link Template}s get rendered together, and the inner {@link Template}
     * has access to the {@link Name}s and {@link Hook}s of the outer {@link Template}.
     *
     * <p>
     * The {@link Renderer} instance exists during the whole rendering process. Should the user ever
     * attempt to render a nested {@link Template} to a {@link String}, we would detect that there is
     * already a {@link Renderer} instance for the outer {@link Template}, and throw a {@link RendererException}.
     */
    private static Renderer renderer = null;

    private int nextTemplateFrameId;
    private final TemplateFrame baseTemplateFrame;
    private TemplateFrame currentTemplateFrame;
    private final CodeFrame baseCodeFrame;
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
            throw new RendererException("A Template method such as '$', 'let', 'sample', 'count' etc. was called outside a template rendering.");
        }
        return renderer;
    }

    static String render(TemplateToken templateToken) {
        return render(templateToken, Template.DEFAULT_FUEL);
    }

    static String render(TemplateToken templateToken, float fuel) {
        // Check nobody else is using the Renderer.
        if (renderer != null) {
            throw new RendererException("Nested render not allowed. Please only use 'asToken' inside Templates, and call 'render' only once at the end.");
        }
        try {
            renderer = new Renderer(fuel);
            renderer.renderTemplateToken(templateToken);
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

    Name sampleName(NameSet.Predicate predicate) {
        return currentCodeFrame.sampleName(predicate);
    }

    int countNames(NameSet.Predicate predicate) {
        return currentCodeFrame.countNames(predicate);
    }

    boolean hasAnyNames(NameSet.Predicate predicate) {
        return currentCodeFrame.hasAnyNames(predicate);
    }

    List<Name> listNames(NameSet.Predicate predicate) {
        return currentCodeFrame.listNames(predicate);
    }

    /**
     * Formats values to {@link String} with the goal of using them in Java code.
     * By default, we use the overrides of {@link Object#toString}.
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

    private void renderTemplateToken(TemplateToken templateToken) {
        TemplateFrame templateFrame = TemplateFrame.make(currentTemplateFrame, nextTemplateFrameId++);
        currentTemplateFrame = templateFrame;

        templateToken.visitArguments((name, value) -> addHashtagReplacement(name, format(value)));
        TemplateBody body = templateToken.instantiate();
        renderTokenList(body.tokens());

        if (currentTemplateFrame != templateFrame) {
            throw new RuntimeException("Internal error: TemplateFrame mismatch!");
        }
        currentTemplateFrame = currentTemplateFrame.parent;
    }

    private void renderToken(Token token) {
        switch (token) {
            case StringToken(String s) -> {
                renderStringWithDollarAndHashtagReplacements(s);
            }
            case NothingToken() -> {
                // Nothing.
            }
            case HookAnchorToken(Hook hook, List<Token> tokens) -> {
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
            case HookInsertToken(Hook hook, TemplateToken templateToken) -> {
                // Switch to hook CodeFrame.
                CodeFrame callerCodeFrame = currentCodeFrame;
                CodeFrame hookCodeFrame = codeFrameForHook(hook);

                // Use a transparent nested CodeFrame. We need a CodeFrame so that the code generated
                // by the TemplateToken can be collected, and hook insertions from it can still
                // be made to the hookCodeFrame before the code from the TemplateToken is added to
                // the hookCodeFrame.
                // But the CodeFrame must be transparent, so that its name definitions go out to
                // the hookCodeFrame, and are not limited to the CodeFrame for the TemplateToken.
                currentCodeFrame = CodeFrame.makeTransparentForNames(hookCodeFrame);

                renderTemplateToken(templateToken);

                hookCodeFrame.addCode(currentCodeFrame.getCode());

                // Switch back from hook CodeFrame to caller CodeFrame.
                currentCodeFrame = callerCodeFrame;
            }
            case TemplateToken templateToken -> {
                // Use a nested CodeFrame.
                CodeFrame callerCodeFrame = currentCodeFrame;
                currentCodeFrame = CodeFrame.make(currentCodeFrame);

                renderTemplateToken(templateToken);

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

    /**
     * We split a {@link String} by "#" and "$", and then look at each part.
     * Example:
     *
     *  s:    "abcdefghijklmnop #name abcdefgh${var_name} 12345#{name2}_con $field_name something"
     *  parts: --------0-------- ------1------ --------2------- ------3----- ----------4---------
     *  start: ^                 ^             ^                ^            ^
     *  next:                   ^             ^                ^            ^                    ^
     *         none             hashtag       dollar           hashtag      dollar               done
     */
    private void renderStringWithDollarAndHashtagReplacements(final String s) {
        int count = 0; // First part needs special handling
        int start = 0;
        boolean startIsAfterDollar = false;
        do {
            // Find the next "$" or "#", after start.
            int dollar  = s.indexOf("$", start);
            int hashtag = s.indexOf("#", start);
            // If the character was not found, we want to have the rest of the
            // String s, so instead of "-1" take the end/length of the String.
            dollar  = (dollar == -1)  ? s.length() : dollar;
            hashtag = (hashtag == -1) ? s.length() : hashtag;
            // Take the first one.
            int next = Math.min(dollar, hashtag);
            String part = s.substring(start, next);

            if (count == 0) {
                // First part has no "#" or "$" before it.
                currentCodeFrame.addString(part);
            } else {
                // All others must do the replacement.
                renderStringWithDollarAndHashtagReplacementsPart(s, part, startIsAfterDollar);
            }

            if (next == s.length()) {
                // No new "#" or "$" was found, we just processed the rest of the String,
                // terminate now.
                return;
            }
            start = next + 1; // skip over the "#" or "$"
            startIsAfterDollar = next == dollar; // remember which character we just split with
            count++;
        } while (true);
    }

    /**
     * We are parsing a part now. Before the part, there was either a "#" or "$":
     * isDollar = false:
     *   "#part"
     *   "#name abcdefgh"
     *     ----
     *   "#{name2}_con "
     *     -------
     *
     * isDollar = true:
     *   "$part"
     *   "${var_name} 12345"
     *     ----------
     *   "$field_name something"
     *     ----------
     *
     * We now want to find the name pattern at the beginning of the part, and replace
     * it according to the hashtag or dollar replacement strategy.
     */
    private void renderStringWithDollarAndHashtagReplacementsPart(final String s, final String part, final boolean isDollar) {
        Matcher matcher = NAME_PATTERN.matcher(part);
        // If the string has a "#" or "$" that is not followed by a correct name
        // pattern, then the matcher will not match. These can be cases like:
        //   "##name" -> the first hashtag leads to an empty part, and an empty name.
        //   "#1name" -> the name pattern does not allow a digit as the first character.
        //   "anything#" -> a hashtag at the end of the string leads to an empty name.
        if (!matcher.find()) {
            String replacement = isDollar ? "$" : "#";
            throw new RendererException("Is not a valid '" + replacement + "' replacement pattern: '" +
                                        replacement + part + "' in '" + s + "'.");
        }
        // We know that there is a correct pattern, and now we replace it.
        currentCodeFrame.addString(matcher.replaceFirst(
            (MatchResult result) -> {
                // There are two groups: (1) for "name" and (2) for "{name}"
                String name = result.group(1) != null ? result.group(1) : result.group(2);
                if (isDollar) {
                    return $(name);
                } else {
                    // replaceFirst needs some special escaping of backslashes and ollar signs.
                    return getHashtagReplacement(name).replace("\\", "\\\\").replace("$", "\\$");
                }
            }
        ));
    }

    boolean isAnchored(Hook hook) {
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
