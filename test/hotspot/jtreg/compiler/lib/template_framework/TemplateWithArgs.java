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

/**
 * Represents a {@link Template} with applied arguments, ready for instantiation, either
 * as a {@link Token} inside another {@link Template} or with {@link #render}.
 */
public sealed abstract class TemplateWithArgs implements Token
                                              permits TemplateWithArgs.ZeroArgsUse,
                                                      TemplateWithArgs.OneArgsUse,
                                                      TemplateWithArgs.TwoArgsUse,
                                                      TemplateWithArgs.ThreeArgsUse
{
    private TemplateWithArgs() {}

    /**
     * Represents a zero-argument {@link Template} with applied arguments, ready for instantiation
     * either as a {@link Token} inside another {@link Template} or with {@link #render}.
     */
    public static final class ZeroArgsUse extends TemplateWithArgs implements Token {
        private final Template.ZeroArgs zeroArgs;

        ZeroArgsUse(Template.ZeroArgs zeroArgs) {
            this.zeroArgs = zeroArgs;
        }

        @Override
        public TemplateBody instantiate() {
            return zeroArgs.instantiate();
        }

        @Override
        public void visitArguments(ArgumentVisitor visitor) {}
    }

    /**
     * Represents a one-argument {@link Template} with applied arguments, ready for instantiation
     * either as a {@link Token} inside another {@link Template} or with {@link #render}.
     *
     * @param <A> The type of the (first) argument.
     */
    public static final class OneArgsUse<A> extends TemplateWithArgs implements Token {
        private final Template.OneArgs<A> oneArgs;
        private final A a;

        OneArgsUse(Template.OneArgs<A> oneArgs, A a) {
            this.oneArgs = oneArgs;
            this.a = a;
        }

        @Override
        public TemplateBody instantiate() {
            return oneArgs.instantiate(a);
        }

        @Override
        public void visitArguments(ArgumentVisitor visitor) {
            visitor.visit(oneArgs.arg0Name(), a);
        }
    }

    /**
     * Represents a two-argument {@link Template} with applied arguments, ready for instantiation
     * either as a {@link Token} inside another {@link Template} or with {@link #render}.
     *
     * @param <A> The type of the first argument.
     * @param <B> The type of the second argument.
     */
    public static final class TwoArgsUse<A, B> extends TemplateWithArgs implements Token {
        private final Template.TwoArgs<A, B> twoArgs;
        private final A a;
        private final B b;

        TwoArgsUse(Template.TwoArgs<A, B> twoArgs, A a, B b) {
            this.twoArgs = twoArgs;
            this.a = a;
            this.b = b;
        }

        @Override
        public TemplateBody instantiate() {
            return twoArgs.instantiate(a, b);
        }

        @Override
        public void visitArguments(ArgumentVisitor visitor) {
            visitor.visit(twoArgs.arg0Name(), a);
            visitor.visit(twoArgs.arg1Name(), b);
        }
    }

    /**
     * Represents a three-argument {@link Template} with applied arguments, ready for instantiation
     * either as a {@link Token} inside another {@link Template} or with {@link #render}.
     *
     * @param <A> The type of the first argument.
     * @param <B> The type of the second argument.
     * @param <C> The type of the second argument.
     */
    public static final class ThreeArgsUse<A, B, C> extends TemplateWithArgs implements Token {
        private final Template.ThreeArgs<A, B, C> threeArgs;
        private final A a;
        private final B b;
        private final C c;

        ThreeArgsUse(Template.ThreeArgs<A, B, C> threeArgs, A a, B b, C c) {
            this.threeArgs = threeArgs;
            this.a = a;
            this.b = b;
            this.c = c;
        }

        @Override
        public TemplateBody instantiate() {
            return threeArgs.instantiate(a, b, c);
        }

        @Override
        public void visitArguments(ArgumentVisitor visitor) {
            visitor.visit(threeArgs.arg0Name(), a);
            visitor.visit(threeArgs.arg1Name(), b);
            visitor.visit(threeArgs.arg2Name(), c);
        }
    }

    abstract TemplateBody instantiate();

    @FunctionalInterface
    interface ArgumentVisitor {
        void visit(String name, Object value);
    }

    abstract void visitArguments(ArgumentVisitor visitor);

    /**
     * Renders the {@link Template} with applied arguments to a {@link String}.
     *
     * @return The {@link Template} rendered to a {@link String}.
     */
    public final String render() {
        return Renderer.render(this);
    }

    /**
     * Renders the {@link Template} with applied arguments to a {@link String}.
     *
     * @param fuel The amount of fuel provided for recursive {@link Template} instantiations.
     * @return The {@link Template} rendered to a {@link String}.
     */
    public final String render(float fuel) {
        return Renderer.render(this, fuel);
    }
}
