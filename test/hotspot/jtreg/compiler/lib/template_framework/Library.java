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
import java.util.List;

import compiler.lib.generators.*;

import static compiler.lib.template_framework.Template.body;
import static compiler.lib.template_framework.Template.let;

/**
 * The Library provides a collection of helpful Templates and Hooks.
 */
public abstract class Library {
    private static final RestrictableGenerator<Integer> GEN_INT = Generators.G.ints();
    private static final RestrictableGenerator<Long> GEN_LONG = Generators.G.longs();
    private static final Generator<Float> GEN_FLOAT = Generators.G.floats();
    private static final Generator<Double> GEN_DOUBLE = Generators.G.doubles();

    public static final Hook CLASS_HOOK = new Hook("Class");
    public static final Hook METHOD_HOOK = new Hook("Method");

    public record IRTestClassInfo(String classpath, String packageName, String className, List<String> imports) {};

    public static final Template.TwoArgs<IRTestClassInfo, List<TemplateWithArgs>> IR_TEST_CLASS =
        Template.make("info", "templates", (IRTestClassInfo info, List<TemplateWithArgs> templates) -> body(
            let("classpath", info.classpath),
            let("packageName", info.packageName),
            let("className", info.className),
            """
            package #packageName;

            // --- IMPORTS start ---
            import compiler.lib.ir_framework.*;
            """,
            info.imports.stream().map(i -> "import " + i + ";\n").toList(),
            """
            // --- IMPORTS end   ---

            public class #className {

            // --- CLASS_HOOK insertions start ---
            """,
            CLASS_HOOK.set(
            """
            // --- CLASS_HOOK insertions end   ---

                public static void main() {
                    TestFramework framework = new TestFramework(#className.class);
                    framework.addFlags("-classpath", "#classpath");
                    framework.start();
                }

            // --- LIST OF TEMPLATES start ---
            """,
            templates
            ),
            """
            // --- LIST OF TEMPLATES end   ---
            }
            """
        )
    );

    public static String format(int v) { return String.valueOf(v); }
    public static String format(long v) { return String.valueOf(v) + "L"; }

    public static String format(float v) { return String.valueOf(v) + "f"; }
    public static String format(double v) { return String.valueOf(v); }

    public enum ExpressionType {
        INT("int"),
        LONG("long"),
        FLOAT("float"),
        DOUBLE("double");

        private final String text;

        ExpressionType(final String text) { this.text = text; }

        @Override
        public String toString() { return text; }
    };

    public static final List<ExpressionType> ALL_EXPRESSION_TYPES = Arrays.asList(ExpressionType.class.getEnumConstants());

    public static final Template.OneArgs<ExpressionType> CONSTANT =
        Template.make("type", (ExpressionType type) -> body(
            switch (type) {
                case ExpressionType.INT -> format(GEN_INT.next());
                case ExpressionType.LONG -> format(GEN_LONG.next());
                case ExpressionType.FLOAT -> format(GEN_FLOAT.next());
                case ExpressionType.DOUBLE -> format(GEN_DOUBLE.next());
            }
        )
    );

    public static final Template.OneArgs<ExpressionType> EXPRESSION =
        Template.make("type", (ExpressionType type) -> body(
            CONSTANT.withArgs(type)
        )
    );
}
