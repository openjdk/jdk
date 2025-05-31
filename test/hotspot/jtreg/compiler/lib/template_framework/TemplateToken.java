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
 * Represents a tokenized {@link Template} (after calling {@code asToken()}) ready for
 * instantiation either as a {@link Token} inside another {@link Template} or as
 * a {@link String} with {@link #render}.
 */
public sealed abstract class TemplateToken implements Token
                                           permits TemplateToken.ZeroArgs,
                                                   TemplateToken.OneArg,
                                                   TemplateToken.TwoArgs,
                                                   TemplateToken.ThreeArgs
{
    private TemplateToken() {}

    /**
     * Represents a tokenized zero-argument {@link Template} ready for instantiation
     * either as a {@link Token} inside another {@link Template} or as a {@link String}
     * with {@link #render}.
     */
    static final class ZeroArgs extends TemplateToken implements Token {
        private final Template.ZeroArgs zeroArgs;

        ZeroArgs(Template.ZeroArgs zeroArgs) {
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
     * Represents a tokenized one-argument {@link Template}, already filled with arguments, ready for
     * instantiation either as a {@link Token} inside another {@link Template} or as a {@link String}
     * with {@link #render}.
     *
     * @param <T1> The type of the (first) argument.
     */
    static final class OneArg<T1> extends TemplateToken implements Token {
        private final Template.OneArg<T1> oneArgs;
        private final T1 arg1;

        OneArg(Template.OneArg<T1> oneArgs, T1 arg1) {
            this.oneArgs = oneArgs;
            this.arg1 = arg1;
        }

        @Override
        public TemplateBody instantiate() {
            return oneArgs.instantiate(arg1);
        }

        @Override
        public void visitArguments(ArgumentVisitor visitor) {
            visitor.visit(oneArgs.arg1Name(), arg1);
        }
    }

    /**
     * Represents a tokenized two-argument {@link Template}, already filled with arguments, ready for
     * instantiation either as a {@link Token} inside another {@link Template} or as a {@link String}
     * with {@link #render}.
     *
     * @param <T1> The type of the first argument.
     * @param <T2> The type of the second argument.
     */
    static final class TwoArgs<T1, T2> extends TemplateToken implements Token {
        private final Template.TwoArgs<T1, T2> twoArgs;
        private final T1 arg1;
        private final T2 arg2;

        TwoArgs(Template.TwoArgs<T1, T2> twoArgs, T1 arg1, T2 arg2) {
            this.twoArgs = twoArgs;
            this.arg1 = arg1;
            this.arg2 = arg2;
        }

        @Override
        public TemplateBody instantiate() {
            return twoArgs.instantiate(arg1, arg2);
        }

        @Override
        public void visitArguments(ArgumentVisitor visitor) {
            visitor.visit(twoArgs.arg1Name(), arg1);
            visitor.visit(twoArgs.arg2Name(), arg2);
        }
    }

    /**
     * Represents a tokenized three-argument {@link TemplateToken}, already filled with arguments, ready for
     * instantiation either as a {@link Token} inside another {@link Template} or as a {@link String}
     * with {@link #render}.
     *
     * @param <T1> The type of the first argument.
     * @param <T2> The type of the second argument.
     * @param <T3> The type of the second argument.
     */
    static final class ThreeArgs<T1, T2, T3> extends TemplateToken implements Token {
        private final Template.ThreeArgs<T1, T2, T3> threeArgs;
        private final T1 arg1;
        private final T2 arg2;
        private final T3 arg3;

        ThreeArgs(Template.ThreeArgs<T1, T2, T3> threeArgs, T1 arg1, T2 arg2, T3 arg3) {
            this.threeArgs = threeArgs;
            this.arg1 = arg1;
            this.arg2 = arg2;
            this.arg3 = arg3;
        }

        @Override
        public TemplateBody instantiate() {
            return threeArgs.instantiate(arg1, arg2, arg3);
        }

        @Override
        public void visitArguments(ArgumentVisitor visitor) {
            visitor.visit(threeArgs.arg1Name(), arg1);
            visitor.visit(threeArgs.arg2Name(), arg2);
            visitor.visit(threeArgs.arg3Name(), arg3);
        }
    }

    abstract TemplateBody instantiate();

    @FunctionalInterface
    interface ArgumentVisitor {
        void visit(String name, Object value);
    }

    abstract void visitArguments(ArgumentVisitor visitor);

    final String render() {
        return Renderer.render(this);
    }

    final String render(float fuel) {
        return Renderer.render(this, fuel);
    }
}
