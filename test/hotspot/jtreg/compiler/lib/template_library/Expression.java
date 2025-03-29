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
    private final List<Type> argTypes;
    private final HashSet<String> exceptions;

    Expression(final Template.OneArgs<List<Object>> template, final List<Type> argTypes, HashSet<String> exceptions) {
        this.template = template;
        this.argTypes = argTypes;
        this.exceptions = exceptions;
    }

    public final List<Type> argTypes() { return this.argTypes; }
    public final HashSet<String> exceptions() { return this.exceptions; }

    public final TemplateWithArgs withArgs(List<Object> args) {
        if (args.size() != argTypes.size()) { throw new RuntimeException("'args' must have the same size as 'argTypes'"); }
        return template.withArgs(args);
    }

    public final List<Value> randomArgValues() {
        return argTypes.stream().map(Value::makeRandom).toList();
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

    public static final <T extends Type> Expression make(Type resultType, List<T> allowedTypes, int maxDepth) {
        HashSet<Type> allowedTypesSet = new HashSet<Type>(allowedTypes);
        List<Operation> ops = Operations.ALL_BUILTIN_OPERATIONS.stream().filter(o -> o.matchesTypes(allowedTypesSet)).toList();

        List<Type> argTypes = new ArrayList<Type>();
        HashSet<String> exceptions = new HashSet<String>();
        ExpressionGenerator generator = expressionGenerator(resultType, ops, maxDepth, argTypes, exceptions);

        var template = Template.make("args", (List<Object> args) -> body(
            generator.tokens(args)
        ));
        return new Expression(template, argTypes, exceptions);
    }

    private static final ExpressionGenerator expressionGenerator(Type resultType, List<Operation> ops, int maxDepth, List<Type> argTypes, HashSet<String> exceptions) {
        ExpressionGeneratorStep step = expressionGeneratorStep(resultType, ops, maxDepth, argTypes, exceptions);
        return (List<Object> args) -> {
            List<Object> tokens = new ArrayList<Object>();
            step.addTokens(tokens, args);
            return tokens;
        };
    }

    private static final ExpressionGeneratorStep expressionGeneratorStep(Type resultType, List<Operation> ops, int maxDepth, List<Type> argTypes, HashSet<String> exceptions) {
        List<Operation> resultTypeOps = ops.stream().filter(o -> o.matchesReturnType(resultType)).toList();
        if (maxDepth <= 0 || resultTypeOps.isEmpty() || RANDOM.nextInt(2 * maxDepth) == 0) {
            if (RANDOM.nextInt(3) == 0) {
                // Fill with random constant value.
                Object c = resultType.con();
                return (List<Object> tokens, List<Object> args) -> {
                    tokens.add(c);
                };
            } else {
                // Remember which type we need to fill the ith argument with.
                int i = argTypes.size();
                argTypes.add(resultType);
                return (List<Object> tokens, List<Object> args) -> {
                    // Extract the ith argument.
                    tokens.add(args.get(i));
                };
            }
        }
        switch (Library.choice(resultTypeOps)) {
            case Operation.Unary(Type r, String s0, Type t0, String s1, List<String> es) -> {
                if (es != null) { exceptions.addAll(es); }
                ExpressionGeneratorStep step0 = expressionGeneratorStep(t0, ops, maxDepth-1, argTypes, exceptions);
                return (List<Object> tokens, List<Object> args) -> {
                    tokens.add(s0);
                    step0.addTokens(tokens, args);
                    tokens.add(s1);
                };
            }
            case Operation.Binary(Type r, String s0, Type t0, String s1, Type t1, String s2, List<String> es) -> {
                if (es != null) { exceptions.addAll(es); }
                ExpressionGeneratorStep step0 = expressionGeneratorStep(t0, ops, maxDepth-1, argTypes, exceptions);
                ExpressionGeneratorStep step1 = expressionGeneratorStep(t1, ops, maxDepth-1, argTypes, exceptions);
                return (List<Object> tokens, List<Object> args) -> {
                    tokens.add(s0);
                    step0.addTokens(tokens, args);
                    tokens.add(s1);
                    step1.addTokens(tokens, args);
                    tokens.add(s2);
                };
            }
            case Operation.Ternary(Type r, String s0, Type t0, String s1, Type t1, String s2, Type t2, String s3, List<String> es) -> {
                if (es != null) { exceptions.addAll(es); }
                ExpressionGeneratorStep step0 = expressionGeneratorStep(t0, ops, maxDepth-1, argTypes, exceptions);
                ExpressionGeneratorStep step1 = expressionGeneratorStep(t1, ops, maxDepth-1, argTypes, exceptions);
                ExpressionGeneratorStep step2 = expressionGeneratorStep(t2, ops, maxDepth-1, argTypes, exceptions);
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
            case Operation.Quaternary(Type r, String s0, Type t0, String s1, Type t1, String s2, Type t2, String s3, Type t3, String s4, List<String> es) -> {
                if (es != null) { exceptions.addAll(es); }
                ExpressionGeneratorStep step0 = expressionGeneratorStep(t0, ops, maxDepth-1, argTypes, exceptions);
                ExpressionGeneratorStep step1 = expressionGeneratorStep(t1, ops, maxDepth-1, argTypes, exceptions);
                ExpressionGeneratorStep step2 = expressionGeneratorStep(t2, ops, maxDepth-1, argTypes, exceptions);
                ExpressionGeneratorStep step3 = expressionGeneratorStep(t3, ops, maxDepth-1, argTypes, exceptions);
                return (List<Object> tokens, List<Object> args) -> {
                    tokens.add(s0);
                    step0.addTokens(tokens, args);
                    tokens.add(s1);
                    step1.addTokens(tokens, args);
                    tokens.add(s2);
                    step2.addTokens(tokens, args);
                    tokens.add(s3);
                    step3.addTokens(tokens, args);
                    tokens.add(s4);
                };
            }
        }
    }
} 
