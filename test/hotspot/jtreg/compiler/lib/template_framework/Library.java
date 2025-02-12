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
import java.util.Random;
import jdk.test.lib.Utils;

import compiler.lib.generators.*;

import static compiler.lib.template_framework.Template.body;
import static compiler.lib.template_framework.Template.let;
import static compiler.lib.template_framework.Template.$;
import static compiler.lib.template_framework.Template.intoHook;
import static compiler.lib.template_framework.Template.fuel;
import static compiler.lib.template_framework.Template.setFuelCost;
import static compiler.lib.template_framework.Template.countNames;
import static compiler.lib.template_framework.Template.sampleName;
import static compiler.lib.template_framework.Template.ALL;
import static compiler.lib.template_framework.Template.MUTABLE;

/**
 * The Library provides a collection of helpful Templates and Hooks.
 *
 * TODO more operators
 * TODO more templates
 */
public abstract class Library {
    private static final Random RANDOM = Utils.getRandomInstance();
    private static final RestrictableGenerator<Integer> GEN_INT = Generators.G.ints();
    private static final RestrictableGenerator<Long> GEN_LONG = Generators.G.longs();
    private static final Generator<Float> GEN_FLOAT = Generators.G.floats();
    private static final Generator<Double> GEN_DOUBLE = Generators.G.doubles();

    private static <T> T choice(List<T> list) {
        if (list.isEmpty()) { return null; }
        int i = RANDOM.nextInt(list.size());
        return list.get(i);
    }

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
        ));

    public enum ExpressionType {
        INT("int"),
        LONG("long"),
        FLOAT("float"),
        DOUBLE("double"),
        BOOLEAN("boolean");

        private final String text;

        ExpressionType(final String text) { this.text = text; }

        @Override
        public String toString() { return text; }
    };

    public static final List<ExpressionType> ALL_EXPRESSION_TYPES = Arrays.asList(ExpressionType.class.getEnumConstants());

    public static final Template.OneArgs<ExpressionType> CONSTANT_EXPRESSION =
        Template.make("type", (ExpressionType type) -> body(
            switch (type) {
                case ExpressionType.INT -> GEN_INT.next();
                case ExpressionType.LONG -> GEN_LONG.next();
                case ExpressionType.FLOAT -> GEN_FLOAT.next();
                case ExpressionType.DOUBLE -> GEN_DOUBLE.next();
                case ExpressionType.BOOLEAN -> GEN_INT.next() % 2 == 0; // TODO better distribution?
            }
        ));

    public static final Template.TwoArgs<ExpressionType, String> DEFINE_STATIC_FIELD =
        Template.make("type", "name", (ExpressionType type, String name) -> body(
            "public static #type #name = ", CONSTANT_EXPRESSION.withArgs(type), ";\n"
        ));

    public static final Template.OneArgs<String> GENERATE_DELAYED_BOOLEAN_USING_BOXING_INLINE =
        Template.make("name", (String name) -> body(
            intoHook(CLASS_HOOK, DEFINE_STATIC_FIELD.withArgs(ExpressionType.BOOLEAN, $("flag"))),
            """
            // #name is constant, but only known after Incremental Boxing Inline (after parsing).
            Integer $box;
            if ($flag) { $box = 1; } else { $box = 2; }
            boolean #name = ($box == 3);
            """
        ));

    public static final Template.OneArgs<String> GENERATE_DELAYED_BOOLEAN_USING_EMPTY_LOOP =
        Template.make("name", (String name) -> body(
            """
            // #name is constant, but only known after first loop opts, when loop detected as empty.
            int $a = 77;
            int $b = 0;
            do {
                $a--;
                $b++;
            } while ($a > 0);
            boolean #name = ($b == 77);
            """
        ));

    public static final Template.OneArgs<String> GENERATE_DELAYED_BOOLEAN =
        Template.make("name", (String name) -> body(
            choice(List.of(
                GENERATE_DELAYED_BOOLEAN_USING_BOXING_INLINE.withArgs(name),
                GENERATE_DELAYED_BOOLEAN_USING_EMPTY_LOOP.withArgs(name),
                intoHook(CLASS_HOOK, DEFINE_STATIC_FIELD.withArgs(ExpressionType.BOOLEAN, name))
            ))
        ));

    public static final Template.TwoArgs<ExpressionType, String> GENERATE_EARLILER_VALUE_FROM_DELAYED_BOOLEAN =
        Template.make("type", "name", (ExpressionType type, String name) -> body(
            GENERATE_DELAYED_BOOLEAN.withArgs($("delayed")),
            "#type #name = ($delayed) ? ",
            CONSTANT_EXPRESSION.withArgs(type),
            " : ",
            CONSTANT_EXPRESSION.withArgs(type),
            ";\n"
        ));

    public static final Template.TwoArgs<ExpressionType, String> GENERATE_EARLIER_VALUE =
        Template.make("type", "name", (ExpressionType type, String name) -> body(
            // TODO alternatives
            choice(List.of(
              intoHook(CLASS_HOOK, DEFINE_STATIC_FIELD.withArgs(type, name)),
              intoHook(METHOD_HOOK, GENERATE_EARLILER_VALUE_FROM_DELAYED_BOOLEAN.withArgs(type, name))
            ))
        ));

    public static final Template.OneArgs<ExpressionType> LOAD_EXPRESSION =
        Template.make("type", (ExpressionType type) -> {
            if (countNames(type, ALL) == 0 || RANDOM.nextInt(5) == 0) {
                return body(
                    GENERATE_EARLIER_VALUE.withArgs(type, $("early")),
                    $("early")
                );
            } else {
                return body(
                    sampleName(type, ALL)
                );
            }
        });

    public static final Template.OneArgs<ExpressionType> TERMINAL_EXPRESSION =
        Template.make("type", (ExpressionType type) -> body(
            choice(List.of(
                CONSTANT_EXPRESSION.withArgs(type),
                LOAD_EXPRESSION.withArgs(type)
            ))
        ));

    // Use Binding to break recursive cycles.
    private static final TemplateBinding<Template.OneArgs<ExpressionType>> OPERATOR_EXPRESSION_BINDING = new TemplateBinding<Template.OneArgs<ExpressionType>>();

    public static final Template.OneArgs<ExpressionType> EXPRESSION =
        Template.make("type", (ExpressionType type) -> body(
            setFuelCost(0),
            (fuel() <= 0 || RANDOM.nextInt(3) == 0) ? TERMINAL_EXPRESSION.withArgs(type)
                                                    : OPERATOR_EXPRESSION_BINDING.get().withArgs(type)
        ));

    private interface Operator {
        TemplateBody instanciate();
    }

    private record BinaryOperator(String prefix, ExpressionType t1, String middle, ExpressionType t2, String postfix) implements Operator {
        @Override
        public TemplateBody instanciate() {
            return body(
                prefix,
                EXPRESSION.withArgs(t1),
                middle,
                EXPRESSION.withArgs(t2),
                postfix
            );
        }
    }

    private static final List<Operator> INT_OPERATORS = List.of(
        new BinaryOperator("(", ExpressionType.INT, " + ",   ExpressionType.INT, ")"),
        new BinaryOperator("(", ExpressionType.INT, " - ",   ExpressionType.INT, ")"),
        new BinaryOperator("(", ExpressionType.INT, " * ",   ExpressionType.INT, ")"),
        new BinaryOperator("(", ExpressionType.INT, " / ",   ExpressionType.INT, ")"),
        new BinaryOperator("(", ExpressionType.INT, " % ",   ExpressionType.INT, ")"),
        new BinaryOperator("(", ExpressionType.INT, " & ",   ExpressionType.INT, ")"),
        new BinaryOperator("(", ExpressionType.INT, " | ",   ExpressionType.INT, ")"),
        new BinaryOperator("(", ExpressionType.INT, " ^ ",   ExpressionType.INT, ")"),
        new BinaryOperator("(", ExpressionType.INT, " << ",  ExpressionType.INT, ")"),
        new BinaryOperator("(", ExpressionType.INT, " >> ",  ExpressionType.INT, ")"),
        new BinaryOperator("(", ExpressionType.INT, " >>> ", ExpressionType.INT, ")")
    );

    private static final List<Operator> LONG_OPERATORS = List.of(
        new BinaryOperator("(", ExpressionType.LONG, " + ",   ExpressionType.LONG, ")"),
        new BinaryOperator("(", ExpressionType.LONG, " - ",   ExpressionType.LONG, ")"),
        new BinaryOperator("(", ExpressionType.LONG, " * ",   ExpressionType.LONG, ")"),
        new BinaryOperator("(", ExpressionType.LONG, " / ",   ExpressionType.LONG, ")"),
        new BinaryOperator("(", ExpressionType.LONG, " % ",   ExpressionType.LONG, ")"),
        new BinaryOperator("(", ExpressionType.LONG, " & ",   ExpressionType.LONG, ")"),
        new BinaryOperator("(", ExpressionType.LONG, " | ",   ExpressionType.LONG, ")"),
        new BinaryOperator("(", ExpressionType.LONG, " ^ ",   ExpressionType.LONG, ")"),
        new BinaryOperator("(", ExpressionType.LONG, " << ",  ExpressionType.LONG, ")"),
        new BinaryOperator("(", ExpressionType.LONG, " >> ",  ExpressionType.LONG, ")"),
        new BinaryOperator("(", ExpressionType.LONG, " >>> ", ExpressionType.LONG, ")")
    );

    private static final List<Operator> FLOAT_OPERATORS = List.of(
        new BinaryOperator("(", ExpressionType.FLOAT, " + ",   ExpressionType.FLOAT, ")"),
        new BinaryOperator("(", ExpressionType.FLOAT, " - ",   ExpressionType.FLOAT, ")"),
        new BinaryOperator("(", ExpressionType.FLOAT, " * ",   ExpressionType.FLOAT, ")"),
        new BinaryOperator("(", ExpressionType.FLOAT, " / ",   ExpressionType.FLOAT, ")"),
        new BinaryOperator("(", ExpressionType.FLOAT, " % ",   ExpressionType.FLOAT, ")")
    );

    private static final List<Operator> DOUBLE_OPERATORS = List.of(
        new BinaryOperator("(", ExpressionType.DOUBLE, " + ",   ExpressionType.DOUBLE, ")"),
        new BinaryOperator("(", ExpressionType.DOUBLE, " - ",   ExpressionType.DOUBLE, ")"),
        new BinaryOperator("(", ExpressionType.DOUBLE, " * ",   ExpressionType.DOUBLE, ")"),
        new BinaryOperator("(", ExpressionType.DOUBLE, " / ",   ExpressionType.DOUBLE, ")"),
        new BinaryOperator("(", ExpressionType.DOUBLE, " % ",   ExpressionType.DOUBLE, ")")
    );

    private static final List<Operator> BOOLEAN_OPERATORS = List.of(
        new BinaryOperator("(", ExpressionType.BOOLEAN, " || ",   ExpressionType.BOOLEAN, ")"),
        new BinaryOperator("(", ExpressionType.BOOLEAN, " && ",   ExpressionType.BOOLEAN, ")"),
        new BinaryOperator("(", ExpressionType.BOOLEAN, " ^ ",    ExpressionType.BOOLEAN, ")")
    );

    private static List<Operator> operators(ExpressionType type) {
        return switch (type) {
            case ExpressionType.INT -> INT_OPERATORS;
            case ExpressionType.LONG -> LONG_OPERATORS;
            case ExpressionType.FLOAT -> FLOAT_OPERATORS;
            case ExpressionType.DOUBLE -> DOUBLE_OPERATORS;
            case ExpressionType.BOOLEAN -> BOOLEAN_OPERATORS;
        };
    }

    public static final Template.OneArgs<ExpressionType> OPERATOR_EXPRESSION =
        Template.make("type", (ExpressionType type) -> choice(operators(type)).instanciate());

    static { OPERATOR_EXPRESSION_BINDING.bind(OPERATOR_EXPRESSION); }
}
