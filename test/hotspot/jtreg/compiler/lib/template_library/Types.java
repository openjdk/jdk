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

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import jdk.test.lib.Utils;

import compiler.lib.generators.Generators;
import compiler.lib.generators.Generator;
import compiler.lib.generators.RestrictableGenerator;

import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.TemplateBinding;
import compiler.lib.template_framework.TemplateBody;
import static compiler.lib.template_framework.Template.body;
import static compiler.lib.template_framework.Template.$;
import static compiler.lib.template_framework.Template.fuel;
import static compiler.lib.template_framework.Template.setFuelCost;
import static compiler.lib.template_framework.Template.defineName;
import static compiler.lib.template_framework.Template.countNames;
import static compiler.lib.template_framework.Template.sampleName;

/**
 *
 */
public abstract class Types {
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
                case ExpressionType.BOOLEAN -> RANDOM.nextInt() % 2 == 0;
            }
        ));

    public static final Template.TwoArgs<ExpressionType, String> DEFINE_STATIC_FIELD =
        Template.make("type", "name", (ExpressionType type, String name) -> body(
            "public static #type #name = ", CONSTANT_EXPRESSION.withArgs(type), ";\n"
        ));

    public static final Template.OneArgs<String> GENERATE_BOOLEAN_USING_BOXING_INLINE =
        Template.make("name", (String name) -> body(
            Library.CLASS_HOOK.insert(DEFINE_STATIC_FIELD.withArgs(ExpressionType.BOOLEAN, $("flag"))),
            """
            // #name is constant, but only known after Incremental Boxing Inline (after parsing).
            Integer $box;
            if ($flag) { $box = 1; } else { $box = 2; }
            boolean #name = ($box == 3);
            """
        ));

    public static final Template.OneArgs<String> GENERATE_BOOLEAN_USING_EMPTY_LOOP =
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

    public static final Template.OneArgs<String> GENERATE_BOOLEAN =
        Template.make("name", (String name) -> body(
            choice(List.of(
                // These are expected to constant fold at some point during the compilation:
                GENERATE_BOOLEAN_USING_BOXING_INLINE.withArgs(name),
                GENERATE_BOOLEAN_USING_EMPTY_LOOP.withArgs(name),
                // This will never constant fold, as it just loads a boolean:
                Library.CLASS_HOOK.insert(DEFINE_STATIC_FIELD.withArgs(ExpressionType.BOOLEAN, name))
            ))
        ));

    public static final Template.TwoArgs<ExpressionType, String> GENERATE_EARLILER_VALUE_FROM_BOOLEAN =
        Template.make("type", "name", (ExpressionType type, String name) -> body(
            GENERATE_BOOLEAN.withArgs($("delayed")),
            "#type #name = ($delayed) ? ",
            CONSTANT_EXPRESSION.withArgs(type),
            " : ",
            CONSTANT_EXPRESSION.withArgs(type),
            ";\n"
        ));

    public static final Template.TwoArgs<ExpressionType, String> GENERATE_EARLIER_VALUE =
        Template.make("type", "name", (ExpressionType type, String name) -> body(
            choice(List.of(
              Library.CLASS_HOOK.insert(DEFINE_STATIC_FIELD.withArgs(type, name)),
              Library.METHOD_HOOK.insert(GENERATE_EARLILER_VALUE_FROM_BOOLEAN.withArgs(type, name))
            ))
        ));

    public static final Template.OneArgs<ExpressionType> LOAD_EXPRESSION =
        Template.make("type", (ExpressionType type) -> {
            if (countNames(type, false) == 0 || RANDOM.nextInt(5) == 0) {
                return body(
                    // TODO defineName !!!
                    GENERATE_EARLIER_VALUE.withArgs(type, $("early")),
                    $("early")
                );
            } else {
                return body(
                    sampleName(type, false)
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

    private record UnaryOperator(String prefix, ExpressionType type, String postfix) implements Operator {
        @Override
        public TemplateBody instanciate() {
            return body(
                prefix,
                EXPRESSION.withArgs(type),
                postfix
            );
        }
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

    private record TernaryOperator(String prefix, ExpressionType t1, String mid1, ExpressionType t2, String mid2, ExpressionType t3, String postfix) implements Operator {
        @Override
        public TemplateBody instanciate() {
            return body(
                prefix,
                EXPRESSION.withArgs(t1),
                mid1,
                EXPRESSION.withArgs(t2),
                mid2,
                EXPRESSION.withArgs(t3),
                postfix
            );
        }
    }

    private static final List<Operator> INT_OPERATORS = List.of(
        new UnaryOperator("(-(", ExpressionType.INT, "))"),
        new UnaryOperator("(~", ExpressionType.INT, ")"),

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
        new BinaryOperator("(", ExpressionType.INT, " >>> ", ExpressionType.INT, ")"),

        new TernaryOperator("(", ExpressionType.BOOLEAN, " ? ", ExpressionType.INT, " : ", ExpressionType.INT, ")")
    );

    private static final List<Operator> LONG_OPERATORS = List.of(
        new UnaryOperator("(-(", ExpressionType.LONG, "))"),
        new UnaryOperator("(~", ExpressionType.LONG, ")"),

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
        new BinaryOperator("(", ExpressionType.LONG, " >>> ", ExpressionType.LONG, ")"),

        new TernaryOperator("(", ExpressionType.BOOLEAN, " ? ", ExpressionType.LONG, " : ", ExpressionType.LONG, ")")
    );

    private static final List<Operator> FLOAT_OPERATORS = List.of(
        new UnaryOperator("(-(", ExpressionType.FLOAT, "))"),

        new BinaryOperator("(", ExpressionType.FLOAT, " + ",   ExpressionType.FLOAT, ")"),
        new BinaryOperator("(", ExpressionType.FLOAT, " - ",   ExpressionType.FLOAT, ")"),
        new BinaryOperator("(", ExpressionType.FLOAT, " * ",   ExpressionType.FLOAT, ")"),
        new BinaryOperator("(", ExpressionType.FLOAT, " / ",   ExpressionType.FLOAT, ")"),
        new BinaryOperator("(", ExpressionType.FLOAT, " % ",   ExpressionType.FLOAT, ")"),

        new TernaryOperator("(", ExpressionType.BOOLEAN, " ? ", ExpressionType.FLOAT, " : ", ExpressionType.FLOAT, ")")
    );

    private static final List<Operator> DOUBLE_OPERATORS = List.of(
        new UnaryOperator("(-(", ExpressionType.DOUBLE, "))"),

        new BinaryOperator("(", ExpressionType.DOUBLE, " + ",   ExpressionType.DOUBLE, ")"),
        new BinaryOperator("(", ExpressionType.DOUBLE, " - ",   ExpressionType.DOUBLE, ")"),
        new BinaryOperator("(", ExpressionType.DOUBLE, " * ",   ExpressionType.DOUBLE, ")"),
        new BinaryOperator("(", ExpressionType.DOUBLE, " / ",   ExpressionType.DOUBLE, ")"),
        new BinaryOperator("(", ExpressionType.DOUBLE, " % ",   ExpressionType.DOUBLE, ")"),

        new TernaryOperator("(", ExpressionType.BOOLEAN, " ? ", ExpressionType.DOUBLE, " : ", ExpressionType.DOUBLE, ")")
    );

    private static final List<Operator> BOOLEAN_OPERATORS = List.of(
        new UnaryOperator("(!(", ExpressionType.BOOLEAN, "))"),

        new BinaryOperator("(", ExpressionType.BOOLEAN, " || ",   ExpressionType.BOOLEAN, ")"),
        new BinaryOperator("(", ExpressionType.BOOLEAN, " && ",   ExpressionType.BOOLEAN, ")"),
        new BinaryOperator("(", ExpressionType.BOOLEAN, " ^ ",    ExpressionType.BOOLEAN, ")"),

        new TernaryOperator("(", ExpressionType.BOOLEAN, " ? ", ExpressionType.BOOLEAN, " : ", ExpressionType.BOOLEAN, ")")
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
