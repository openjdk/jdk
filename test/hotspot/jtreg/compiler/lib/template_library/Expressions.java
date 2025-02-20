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
import java.util.HashSet;
import java.util.Random;
import jdk.test.lib.Utils;

import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.TemplateWithArgs;
import static compiler.lib.template_framework.Template.body;
import static compiler.lib.template_framework.Template.$;
import static compiler.lib.template_framework.Template.fuel;
import static compiler.lib.template_framework.Template.setFuelCost;
import static compiler.lib.template_framework.Template.defineName;
import static compiler.lib.template_framework.Template.countNames;
import static compiler.lib.template_framework.Template.sampleName;

import compiler.lib.template_library.types.Type;
import compiler.lib.template_library.types.Operation;

public abstract class Expressions {

    private static final Random RANDOM = Utils.getRandomInstance();

    private static <T> T choice(List<T> list) {
        if (list.isEmpty()) { return null; }
        int i = RANDOM.nextInt(list.size());
        return list.get(i);
    }

    public static final Expression constant(Type type) {
        var template = Template.make("args", (List<Object> args) -> body(
            type.con()
        ));
        return new Expression(template, List.of());
    }

    private interface ExpressionGenerator {
        List<Object> tokens(List<Object> args);
    }

    private interface ExpressionGeneratorStep {
        void addTokens(List<Object> tokens, List<Object> args);
    }

    public static final Expression expression(Type resultType, List<Type> allowedTypes, int maxDepth) {
        HashSet<Type> allowedTypesSet = new HashSet(allowedTypes);

        List<Type> types = new ArrayList<Type>();
        ExpressionGenerator generator = expressionGenerator(resultType, allowedTypesSet, maxDepth, types);
        // TODO:
        // Output: lambda(args) -> list of tokens, using args
        // Step: lambda(args, tokens) -> update tokens

        var template = Template.make("args", (List<Object> args) -> body(
            generator.tokens(args)
        ));
        return new Expression(template, List.of());
    }

    private static final ExpressionGenerator expressionGenerator(Type resultType, HashSet<Type> allowedTypes, int maxDepth, List<Type> types) {
        ExpressionGeneratorStep step = expressionGeneratorStep(resultType, allowedTypes, maxDepth, types);
        return (List<Object> args) -> {
            List<Object> tokens = new ArrayList<Object>();
            step.addTokens(tokens, args);
            return tokens;
        };
    }

    private static final ExpressionGeneratorStep expressionGeneratorStep(Type resultType, HashSet<Type> allowedTypes, int maxDepth, List<Type> types) {
        List<Operation> ops = resultType.operations().stream().filter(o -> o.hasOnlyTypes(allowedTypes)).toList();
        if (maxDepth <= 0 || ops.isEmpty()) {
            // TODO: add to types in some cases!
            return expressionGeneratorStepCon(resultType);
        }
        switch (choice(ops)) {
            case Operation.Unary(String s0, Type t0, String s1) -> {
                ExpressionGeneratorStep step0 = expressionGeneratorStep(t0, allowedTypes, maxDepth-1, types);
                return (List<Object> tokens, List<Object> args) -> {
                    tokens.add(s0);
                    step0.addTokens(tokens, args);
                    tokens.add(s1);
                };
            }
            case Operation.Binary(String s0, Type t0, String s1, Type t1, String s2) -> {
                ExpressionGeneratorStep step0 = expressionGeneratorStep(t0, allowedTypes, maxDepth-1, types);
                ExpressionGeneratorStep step1 = expressionGeneratorStep(t1, allowedTypes, maxDepth-1, types);
                return (List<Object> tokens, List<Object> args) -> {
                    tokens.add(s0);
                    step0.addTokens(tokens, args);
                    tokens.add(s1);
                    step1.addTokens(tokens, args);
                    tokens.add(s2);
                };
            }
            case Operation.Ternary(String s0, Type t0, String s1, Type t1, String s2, Type t2, String s3) -> {
                ExpressionGeneratorStep step0 = expressionGeneratorStep(t0, allowedTypes, maxDepth-1, types);
                ExpressionGeneratorStep step1 = expressionGeneratorStep(t1, allowedTypes, maxDepth-1, types);
                ExpressionGeneratorStep step2 = expressionGeneratorStep(t1, allowedTypes, maxDepth-1, types);
                return (List<Object> tokens, List<Object> args) -> {
                    tokens.add(s0);
                    step0.addTokens(tokens, args);
                    tokens.add(s1);
                    step1.addTokens(tokens, args);
                    tokens.add(s2);
                    step2.addTokens(tokens, args);
                    tokens.add(s3);
                };
            }
        }
    }

    private static final ExpressionGeneratorStep expressionGeneratorStepCon(Type type) {
        Object c = type.con();
        return (List<Object> tokens, List<Object> args) -> {
            tokens.add(c);
        };
    }
}
