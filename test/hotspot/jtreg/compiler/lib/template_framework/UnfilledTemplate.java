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

/**
 * Represents a Template with arguments that are not yet filled, but can be filled
 * by calling {@code fillWith} (e.g. {@link OneArgs#fillWith(Object)} to obtain
 * a {@link FilledTemplate}.
 */
public interface UnfilledTemplate {
    /**
     * A {@link UnfilledTemplate} with no arguments.
     *
     * @param function The {@link Supplier} that creates the {@link TemplateBody}.
     */
    public record ZeroArgs(Supplier<TemplateBody> function) implements UnfilledTemplate {
        TemplateBody instantiate() {
            return function.get();
        }

        /**
         * Creates a {@link FilledTemplate} which can be used as a {@link Token} inside
         * a {@link UnfilledTemplate} for nested code generation, and it can also be used with
         * {@link FilledTemplate#render} to render the template to a {@link String}
         * directly.
         *
         * @return The template all (zero) arguments applied.
         */
        FilledTemplate.ZeroArgs fillWithNothing() {
            return new FilledTemplate.ZeroArgs(this);
        }
    }

    /**
     * A {@link UnfilledTemplate} with one argument.
     *
     * @param arg0Name The name of the (first) argument, used for hashtag replacements in the {@link UnfilledTemplate}.
     * @param <A> The type of the (first) argument.
     * @param function The {@link Function} that creates the {@link TemplateBody} given the template argument.
     */
    public record OneArgs<A>(String arg0Name, Function<A, TemplateBody> function)
            implements UnfilledTemplate, TemplateBinding.Bindable {
        TemplateBody instantiate(A a) {
            return function.apply(a);
        }

        /**
         * Creates a {@link FilledTemplate} which can be used as a {@link Token} inside
         * a {@link UnfilledTemplate} for nested code generation, and it can also be used with
         * {@link FilledTemplate#render} to render the template to a {@link String}
         * directly.
         *
         * @param a The value for the (first) argument.
         * @return The template its argument applied.
         */
        public FilledTemplate fillWith(A a) {
            return new FilledTemplate.OneArgs<>(this, a);
        }
    }

    /**
     * A {@link UnfilledTemplate} with two arguments.
     *
     * @param arg0Name The name of the first argument, used for hashtag replacements in the {@link UnfilledTemplate}.
     * @param arg1Name The name of the second argument, used for hashtag replacements in the {@link UnfilledTemplate}.
     * @param <A> The type of the first argument.
     * @param <B> The type of the second argument.
     * @param function The {@link BiFunction} that creates the {@link TemplateBody} given the template arguments.
     */
    public record TwoArgs<A, B>(String arg0Name, String arg1Name, BiFunction<A, B, TemplateBody> function)
            implements UnfilledTemplate, TemplateBinding.Bindable {
        TemplateBody instantiate(A a, B b) {
            return function.apply(a, b);
        }

        /**
         * Creates a {@link FilledTemplate} which can be used as a {@link Token} inside
         * a {@link UnfilledTemplate} for nested code generation, and it can also be used with
         * {@link FilledTemplate#render} to render the template to a {@link String}
         * directly.
         *
         * @param a The value for the first argument.
         * @param b The value for the second argument.
         * @return The template all (two) arguments applied.
         */
        public FilledTemplate fillWith(A a, B b) {
            return new FilledTemplate.TwoArgs<>(this, a, b);
        }
    }

    /**
     * Interface for function with three arguments.
     *
     * @param <T> Type of the first argument.
     * @param <U> Type of the second argument.
     * @param <V> Type of the third argument.
     * @param <R> Type of the return value.
     */
    @FunctionalInterface
    public interface TriFunction<T, U, V, R> {

        /**
         * Function definition for the three argument functions.
         *
         * @param t The first argument.
         * @param u The second argument.
         * @param v The third argument.
         * @return Return value of the three argument function.
         */
        R apply(T t, U u, V v);
    }

    /**
     * A {@link UnfilledTemplate} with three arguments.
     *
     * @param arg0Name The name of the first argument, used for hashtag replacements in the {@link UnfilledTemplate}.
     * @param arg1Name The name of the second argument, used for hashtag replacements in the {@link UnfilledTemplate}.
     * @param arg2Name The name of the third argument, used for hashtag replacements in the {@link UnfilledTemplate}.
     * @param <A> The type of the first argument.
     * @param <B> The type of the second argument.
     * @param <C> The type of the third argument.
     * @param function The function with three arguments that creates the {@link TemplateBody} given the template arguments.
     */
    public record ThreeArgs<A, B, C>(String arg0Name, String arg1Name, String arg2Name, TriFunction<A, B, C, TemplateBody> function)
            implements UnfilledTemplate, TemplateBinding.Bindable {
        TemplateBody instantiate(A a, B b, C c) {
            return function.apply(a, b, c);
        }

        /**
         * Creates a {@link FilledTemplate} which can be used as a {@link Token} inside
         * a {@link UnfilledTemplate} for nested code generation, and it can also be used with
         * {@link FilledTemplate#render} to render the template to a {@link String}
         * directly.
         *
         * @param a The value for the first argument.
         * @param b The value for the second argument.
         * @param c The value for the third argument.
         * @return The template all (three) arguments applied.
         */
        public FilledTemplate fillWith(A a, B b, C c) {
            return new FilledTemplate.ThreeArgs<>(this, a, b, c);
        }
    }
}
