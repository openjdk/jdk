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

package compiler.lib.template_library;

import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import jdk.test.lib.Utils;

import compiler.lib.template_framework.Hook;
import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.Name;
import compiler.lib.template_framework.TemplateWithArgs;
import static compiler.lib.template_framework.Template.body;
import static compiler.lib.template_framework.Template.let;
import static compiler.lib.template_framework.Template.addName;

/**
 * TODO
 * The Library provides a collection of helpful Templates and Hooks.
 *
 * TODO more operators
 * TODO more templates
 * TODO configure choice and other randomization?
 */
public abstract class Library {
    private static final Random RANDOM = Utils.getRandomInstance();

    public static final Hook CLASS_HOOK = new Hook("Class");
    public static final Hook METHOD_HOOK = new Hook("Method");

    public static <T> T choice(List<T> list) {
        if (list.isEmpty()) { return null; }
        int i = RANDOM.nextInt(list.size());
        return list.get(i);
    }

    static final <T> List<T> concat(List<? extends T> ... lists) {
        List<T> list = new ArrayList<T>();
        for (var l : lists) {
            list.addAll(l);
        }

        // Ensure the list is immutable.
        return List.copyOf(list);
    }

    public static TemplateWithArgs defineField(Name name, boolean isStatic, Object valueToken) {
        var define = Template.make(() -> body(
            let("static", isStatic ? "static" : ""),
            let("type", name.type()),
            let("name", name.name()),
            "public #static #type #name = ", valueToken, ";\n",
            addName(name)
        ));
        var template = Template.make(() -> body(
            CLASS_HOOK.insert(define.withArgs())
        ));
        return template.withArgs();
    }

    public static TemplateWithArgs defineVariableWithComputation(String name, Type type) {
        Name n = new Name(name, type, false, 1);
        // TODO: more patterns
        var define = Template.make(() -> body(
            let("type", type),
            let("name", name),
            "#type #name = ", type.con(), ";\n",
            addName(n)
        ));
        var template = Template.make(() -> body(
            METHOD_HOOK.insert(define.withArgs())
        ));
        return template.withArgs();
    }

    public static TemplateWithArgs arrayFillMethods() {
        var template = Template.make(() -> body(
            """
            private static final RestrictableGenerator<Integer> GEN_BYTE = Generators.G.safeRestrict(Generators.G.ints(), Byte.MIN_VALUE, Byte.MAX_VALUE);
            private static final RestrictableGenerator<Integer> GEN_CHAR = Generators.G.safeRestrict(Generators.G.ints(), Character.MIN_VALUE, Character.MAX_VALUE);
            private static final RestrictableGenerator<Integer> GEN_SHORT = Generators.G.safeRestrict(Generators.G.ints(), Short.MIN_VALUE, Short.MAX_VALUE);
            private static final RestrictableGenerator<Integer> GEN_INT = Generators.G.ints();
            private static final RestrictableGenerator<Long> GEN_LONG = Generators.G.longs();
            private static final Generator<Float> GEN_FLOAT = Generators.G.floats();
            private static final Generator<Double> GEN_DOUBLE = Generators.G.doubles();
            private static final RestrictableGenerator<Integer> GEN_BOOLEAN = Generators.G.safeRestrict(Generators.G.ints(), 0, 1);

            public static byte[] fill(byte[] a) {
                for (int i = 0; i < a.length; i++) {
                    a[i] = GEN_BYTE.next().byteValue();
                }
                return a;
            }

            public static char[] fill(char[] a) {
                for (int i = 0; i < a.length; i++) {
                    a[i] = (char)GEN_CHAR.next().intValue();
                }
                return a;
            }

            public static short[] fill(short[] a) {
                for (int i = 0; i < a.length; i++) {
                    a[i] = GEN_SHORT.next().shortValue();
                }
                return a;
            }

            public static int[] fill(int[] a) {
                Generators.G.fill(GEN_INT, a);
                return a;
            }

            public static long[] fill(long[] a) {
                Generators.G.fill(GEN_LONG, a);
                return a;
            }

            public static float[] fill(float[] a) {
                Generators.G.fill(GEN_FLOAT, a);
                return a;
            }

            public static double[] fill(double[] a) {
                Generators.G.fill(GEN_DOUBLE, a);
                return a;
            }

            public static boolean[] fill(boolean[] a) {
                for (int i = 0; i < a.length; i++) {
                    a[i] = GEN_BOOLEAN.next() == 1;
                }
                return a;
            }
            """
        ));
        return template.withArgs();
    }
} 
