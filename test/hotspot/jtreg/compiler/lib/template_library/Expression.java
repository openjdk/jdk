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

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Random;
import jdk.test.lib.Utils;

import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.TemplateWithArgs;
import static compiler.lib.template_framework.Template.body;
import static compiler.lib.template_framework.Template.setFuelCost;

/**
 * TODO: description
 * Idea: generates a template that has a list of {@link Type} holes.
 */
public final class Expression {
    private static final Random RANDOM = Utils.getRandomInstance();

    private final Template.OneArgs<List<Object>> template;
    private final List<Type> types;

    Expression(final Template.OneArgs<List<Object>> template, final List<Type> types) {
        this.template = template;
        this.types = types;
    }

    public final List<Type> types() { return this.types; }

    public final TemplateWithArgs withArgs(List<Object> args) {
        if (args.size() != types.size()) { throw new RuntimeException("'args' must have the same size as 'types'"); }
        return template.withArgs(args);
    }

    public final List<Value> randomArgValues() {
        return types.stream().map(Value::makeRandom).toList();
    }

    // We would like to use identical args multiple times, but possible field / variable defs have to happen only once.
    // So we need to separate possible generation for the arguments with the loads.
    // So we have
    // - def-tokens: define fields/vars, or do nothing if we reference something that already exists / con.
    // - use-tokens: reference defined fields/vars, or just constant.
    public final TemplateWithArgs withRandomArgs() {
        List<Value> argValues = randomArgValues();
        List<Object> def = argValues.stream().map(v -> v.defTokens()).toList();
        List<Object> use = argValues.stream().map(v -> v.useTokens()).toList();
        var template = Template.make(() -> body(
            setFuelCost(0),
            def,
            withArgs(use)
        ));
        return template.withArgs();
    }

    private interface ExpressionGenerator {
        List<Object> tokens(List<Object> args);
    }

    private interface ExpressionGeneratorStep {
        void addTokens(List<Object> tokens, List<Object> args);
    }

    public static final Expression make(Type resultType, List<Type> allowedTypes, int maxDepth) {
        HashSet<Type> allowedTypesSet = new HashSet(allowedTypes);

        List<Type> types = new ArrayList<Type>();
        ExpressionGenerator generator = expressionGenerator(resultType, allowedTypesSet, maxDepth, types);

        var template = Template.make("args", (List<Object> args) -> body(
            generator.tokens(args)
        ));
        return new Expression(template, types);
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
        List<Operation> ops = Operations.PRIMITIVE_OPERATIONS.stream().filter(o -> o.matchesTypes(resultType, allowedTypes)).toList();
        if (maxDepth <= 0 || ops.isEmpty() || RANDOM.nextInt(2 * maxDepth) == 0) {
            // Remember which type we need to fill the ith argument with.
            int i = types.size();
            types.add(resultType);
            return (List<Object> tokens, List<Object> args) -> {
                // Extract the ith argument.
                tokens.add(args.get(i));
            };
        }
        switch (Library.choice(ops)) {
            case Operation.Unary(Type r, String s0, Type t0, String s1) -> {
                ExpressionGeneratorStep step0 = expressionGeneratorStep(t0, allowedTypes, maxDepth-1, types);
                return (List<Object> tokens, List<Object> args) -> {
                    tokens.add(s0);
                    step0.addTokens(tokens, args);
                    tokens.add(s1);
                };
            }
            case Operation.Binary(Type r, String s0, Type t0, String s1, Type t1, String s2) -> {
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
            case Operation.Ternary(Type r, String s0, Type t0, String s1, Type t1, String s2, Type t2, String s3) -> {
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
} 
