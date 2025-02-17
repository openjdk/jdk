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

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.List;

/**
 * TODO
 * TODO talk about hashtag replacement and arguments.
 */
public interface Template {

    /**
     * Creates a {@link Template} with no arguments.
     *
     * @param body The {@link TemplateBody} created by {@link Template#body}.
     * @return A {@link Template} with zero arguments.
     */
    static ZeroArgs make(Supplier<TemplateBody> body) {
        return new ZeroArgs(body);
    }

    /**
     * Creates a {@link Template} with one argument.
     *
     * @param body The {@link TemplateBody} created by {@link Template#body}.
     * @param <A> Type of the zeroth argument.
     * @param arg0Name The name of the zeroth argument for hashtag replacement.
     * @return A {@link Template} with one argument.
     */
    static <A> OneArgs<A> make(String arg0Name, Function<A, TemplateBody> body) {
        return new OneArgs<>(arg0Name, body);
    }

    /**
     * Creates a {@link Template} with two arguments.
     *
     * @param body The {@link TemplateBody} created by {@link Template#body}.
     * @param <A> Type of the zeroth argument.
     * @param arg0Name The name of the zeroth argument for hashtag replacement.
     * @param <B> Type of the first argument.
     * @param arg1Name The name of the first argument for hashtag replacement.
     * @return A {@link Template} with two arguments.
     */
    static <A, B> TwoArgs<A, B> make(String arg0Name, String arg1Name, BiFunction<A, B, TemplateBody> body) {
        return new TwoArgs<>(arg0Name, arg1Name, body);
    }

    /**
     * A {@link Template} with no arguments.
     */
    record ZeroArgs(Supplier<TemplateBody> function) implements Template {
        TemplateBody instantiate() {
            return function.get();
        }

        /**
         * Creates a {@link TemplateWithArgs} which can be used as a {@link Token} inside
         * a {@link Template} for nested code generation, and it can also be used with
         * {@link TemplateWithArgs#render} to render the template to a {@link String}
         * directly.
         */
        public TemplateWithArgs.ZeroArgsUse withArgs() {
            return new TemplateWithArgs.ZeroArgsUse(this);
        }
    }


    /**
     * A {@link Template} with one argument.
     */
    record OneArgs<A>(String arg0Name, Function<A, TemplateBody> function) implements Template {
        TemplateBody instantiate(A a) {
            return function.apply(a);
        }

        public TemplateWithArgs.OneArgsUse<A> withArgs(A a) {
            return new TemplateWithArgs.OneArgsUse<>(this, a);
        }
    }

    /**
     * A {@link Template} with two arguments.
     */
    record TwoArgs<A, B>(String arg0Name, String arg1Name,
                         BiFunction<A, B, TemplateBody> function) implements Template {
        TemplateBody instantiate(A a, B b) {
            return function.apply(a, b);
        }

        public TemplateWithArgs.TwoArgsUse<A, B> withArgs(A a, B b) {
            return new TemplateWithArgs.TwoArgsUse<>(this, a, b);
        }
    }

    /**
     * Creates a {@link TemplateBody} from a list of tokens, which can be {@link String}s,
     * boxed primitive types (e.g. {@link Integer}), any {@link Token}, or {@link List}s
     * of any of these.
     *
     * @param tokens A list of tokens, which can be {@link String}s,boxed primitive types
     *               (e.g. {@link Integer}), any {@link Token}, or {@link List}s
     *               of any of these.
     * @return The {@link TemplateBody} which captures the list of validated tokens.
     * @throws IllegalArgumentException if the list of tokens contains an unexpected object.
     */
    static TemplateBody body(Object... tokens) {
        return new TemplateBody(Token.parse(tokens));
    }

    /**
     * Let a {@link TemplateWithArgs} generate code at the innermost location where the
     * {@link Hook} was set with {@link Hook#set}.
     *
     * @param hook The {@link Hook} the code is to be generated at.
     * @param templateWithArgs The {@link Template} with applied arguments to be generated at the {@link Hook}.
     * @return The {@link HookIntoToken} which when used inside a {@link Template#body} performs the code generation into the {@link Hook}.
     * @throws RendererException if there is no active {@link Hook#set}.
     */
    static HookIntoToken intoHook(Hook hook, TemplateWithArgs templateWithArgs) {
        return new HookIntoToken(hook, templateWithArgs);
    }

    /**
     * Retrieves the renaming (dollar-replacement) of the {@code 'name'} for the
     * current {@link Template} that is being instanciated. It returns the same
     * dollar-replacement as the string use {@code "$name"}.
     *
     * Here an example where a {@link Template} creates a local variable {@code 'var'},
     * with an implicit dollar-replacement, and then captures that dollar-replacement
     * using {@link $} for the use inside a nested template.
     * {@snippet lang=java :
     * var template = Template.make(() -> body(
     *     """
     *     int $var = 42;
     *     """,
     *     otherTemplate.withArgs($("var"))
     * ));
     * }
     *
     * @param name The {@link String} name of the name.
     * @return The dollar-replacement for the {@code 'name'}.
     */
    static String $(String name) {
        return Renderer.getCurrent().$(name);
    }

    /**
     * Define a hashtag replacement for {@code "#key"}, with a specific value.
     *
     * {@snippet lang=java :
     * var template = Template.make("a", (Integer a) -> body(
     *     let("b", a * 5),
     *     """
     *     System.out.prinln("Use a and b with hashtag replacement: #a and #b");
     *     """
     * ));
     * }
     *
     * @param key Name for the hashtag replacement.
     * @param value The value that the hashtag is replaced with.
     * @return A token that does nothing, so that the {@link let} cal can easily be put in a list of tokens
     *         inside a {@link Template#body}.
     * @throws RendererException if there is a duplicate hashtag {@code key}.
     */
    static NothingToken let(String key, Object value) {
        Renderer.getCurrent().addHashtagReplacement(key, value);
        return new NothingToken();
    }

    /**
     * Define a hashtag replacement for {@code "#key"}, with a specific value, which is also captured
     * by the provided {@code 'function'} with type {@code <T>}.
     *
     * {@snippet lang=java :
     * var template = Template.make("a", (Integer a) -> let("b", a * 2, (Integer b) -> body(
     *     """
     *     System.out.prinln("Use a and b with hashtag replacement: #a and #b");
     *     """,
     *     "System.out.println(\"Use a and b as capture variables:\" + a + " and " + b + ");\n"
     * )));
     * }
     *
     * @param key Name for the hashtag replacement.
     * @param value The value that the hashtag is replaced with.
     * @param function The function that is applied with the provided {@code 'value'}.
     * @return A token that does nothing, so that the {@link let} cal can easily be put in a list of tokens
     *         inside a {@link Template#body}.
     * @throws RendererException if there is a duplicate hashtag {@code key}.
     */
    static <T> TemplateBody let(String key, T value, Function<T, TemplateBody> function) {
        Renderer.getCurrent().addHashtagReplacement(key, value);
        return function.apply(value);
    }

    static float fuel() {
        return Renderer.getCurrent().fuel();
    }

    static NothingToken setFuelCost(float fuelCost) {
        Renderer.getCurrent().setFuelCost(fuelCost);
        return new NothingToken();
    }

    static NothingToken defineName(String name, Object type, NameSelection nameSelection) {
        Renderer.getCurrent().defineName(name, type, nameSelection);
        return new NothingToken();
    }

    static int countNames(Object type, NameSelection nameSelection) {
        return Renderer.getCurrent().countNames(type, nameSelection);
    }

    static String sampleName(Object type, NameSelection nameSelection) {
        return Renderer.getCurrent().sampleName(type, nameSelection);
    }
}
