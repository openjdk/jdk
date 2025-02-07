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

// TODO rename OneArgUse to OneArgsUse for consistency?
public sealed interface TemplateWithArgs extends Token
                                         permits TemplateWithArgs.ZeroArgsUse,
                                                 TemplateWithArgs.OneArgUse,
                                                 TemplateWithArgs.TwoArgsUse
{
    record ZeroArgsUse(Template.ZeroArgs zeroArgs) implements TemplateWithArgs, Token {
        @Override
        public TemplateBody instantiate() {
            return zeroArgs.instantiate();
        }

        @Override
        public void visitArguments(ArgumentVisitor visitor) {}
    }

    record OneArgUse<A>(Template.OneArg<A> oneArg, A a) implements TemplateWithArgs, Token {
        @Override
        public TemplateBody instantiate() {
            return oneArg.instantiate(a);
        }

        @Override
        public void visitArguments(ArgumentVisitor visitor) {
            visitor.visit(oneArg.arg0Name(), a);
        }
    }

    record TwoArgsUse<A, B>(Template.TwoArgs<A, B> twoArgs, A a, B b) implements TemplateWithArgs, Token {
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

    TemplateBody instantiate();

    @FunctionalInterface
    interface ArgumentVisitor {
        void visit(String name, Object value);
    }

    void visitArguments(ArgumentVisitor visitor);

    // TODO ensure that there can be no nested rendering!
    //      Otherwise people might fold recursive to String uses too soon... true?
    //      Yes, the issue is that outer scope may create vars, the inner then does
    //      not have access!
    default String render() {
        return Renderer.render(this);
    }
}
