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

import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public interface Template {
    static ZeroArgs make(Supplier<InstantiatedTemplate> t) {
        return new ZeroArgs(t);
    }

    static <A> OneArg<A> make(String arg0Name, Function<A, InstantiatedTemplate> t) {
        return new OneArg<>(arg0Name, t);
    }

    static <A, B> TwoArgs<A, B> make(String arg0Name, String arg1Name, BiFunction<A, B, InstantiatedTemplate> t) {
        return new TwoArgs<>(arg0Name, arg1Name, t);
    }

    record ZeroArgs(Supplier<InstantiatedTemplate> function) implements Template {
        InstantiatedTemplate instantiate() {
            return function.get();
        }

        public TemplateWithArgs.ZeroArgsUse withArgs() {
            return new TemplateWithArgs.ZeroArgsUse(this);
        }
    }

    record OneArg<A>(String arg0Name, Function<A, InstantiatedTemplate> function) implements Template {
        InstantiatedTemplate instantiate(A a) {
            return function.apply(a);
        }

        public TemplateWithArgs.OneArgUse<A> withArgs(A a) {
            return new TemplateWithArgs.OneArgUse<>(this, a);
        }
    }

    record TwoArgs<A, B>(String arg0Name, String arg1Name,
                         BiFunction<A, B, InstantiatedTemplate> function) implements Template {
        InstantiatedTemplate instantiate(A a, B b) {
            return function.apply(a, b);
        }

        public TemplateWithArgs.TwoArgsUse<A, B> withArgs(A a, B b) {
            return new TemplateWithArgs.TwoArgsUse<>(this, a, b);
        }
    }

    static InstantiatedTemplate body(Object... tokens) {
        return new InstantiatedTemplate(Token.parse(Arrays.asList(tokens)));
    }

    static HookIntoToken intoHook(Hook hook, TemplateWithArgs t) {
        return new HookIntoToken(hook, t);
    }

    static String $(String name) {
        return Renderer.$(name);
    }

    static LetToken let(String key, Object value) {
        return new LetToken(key, value.toString());
    }
}
