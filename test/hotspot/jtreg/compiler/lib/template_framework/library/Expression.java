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

package compiler.lib.template_framework.library;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.Random;
import jdk.test.lib.Utils;
import java.util.stream.Stream;
import java.util.stream.Collectors;

import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.TemplateToken;
import static compiler.lib.template_framework.Template.scope;

/**
 * {@link Expression}s model Java expressions, that have a list of arguments with specified
 * argument types, and a result with a specified result type. Once can {@link #make} a new
 * {@link Expression} or use existing ones from {@link Operations}.
 *
 * <p>
 * The {@link Expression}s are composable, they can be explicitly {@link #nest}ed, or randomly
 * combined using {@link #nestRandomly}.
 *
 * <p>
 * Finally, they can be used in a {@link Template} as a {@link TemplateToken} by calling
 * {@link #asToken} with the required arguments.
 */
public class Expression {
    private static final Random RANDOM = Utils.getRandomInstance();

    /**
     * Specifies the return type of the {@link Expression}.
     */
    public final CodeGenerationDataNameType returnType;


    /**
     * Specifies the types of the arguments.
     */
    public final List<CodeGenerationDataNameType> argumentTypes;

    final List<String> strings;

    /**
     * Provides additional information about the {@link Expression}.
     */
    public final Info info;

    private Expression(CodeGenerationDataNameType returnType,
                      List<CodeGenerationDataNameType> argumentTypes,
                      List<String> strings,
                      Info info) {
        if (argumentTypes.size() + 1 != strings.size()) {
            throw new RuntimeException("Must have one more string than argument.");
        }
        this.returnType = returnType;
        this.argumentTypes = List.copyOf(argumentTypes);
        this.strings = List.copyOf(strings);
        this.info = info;
    }


    /**
     * Specifies additional information for an {@link Expression}.
     */
    public static class Info {
        /**
         * Set of exceptions the {@link Exception} could throw when executed.
         * By default, we assume that an {@link Expression} throws no exceptions.
         */
        public final Set<String> exceptions;

        /**
         * Specifies if the result of the {@link Expression} is guaranteed to
         * be deterministic. This allows exact result verification, for example
         * by comparing compiler and interpreter results. However, there are some
         * operations that do not always return the same exact result, which can
         * for example happen with {@code Float.floatToRawIntBits} in combination
         * with more than one {@code NaN} bit representations.
         * By default, we assume that an {@link Expression} is deterministic.
         */
        public final boolean isResultDeterministic;

        /**
         * Create a default {@link Info}.
         */
        public Info() {
            this.exceptions = Set.of();
            this.isResultDeterministic = true;
        }

        private Info(Set<String> exceptions, boolean isResultDeterministic) {
            this.exceptions = Set.copyOf(exceptions);
            this.isResultDeterministic = isResultDeterministic;
        }

        /**
         * Creates a new {@link Info} with additional exceptions that the {@link Expression} could throw.
         *
         * @param exceptions the exceptions to be added.
         * @return a new {@link Info} instance with the added exceptions.
         */
        public Info withExceptions(Set<String> exceptions) {
            exceptions = Stream.concat(this.exceptions.stream(), exceptions.stream())
                               .collect(Collectors.toSet());
            return new Info(exceptions, this.isResultDeterministic);
        }

        /**
         * Creates a new {@link Info} that specifies that the {@link Exception} may return
         * indeterministic results, which prevents exact result verification.
         *
         * @return a new {@link Info} instance that specifies indeterministic results.
         */
        public Info withNondeterministicResult() {
            return new Info(this.exceptions, false);
        }

        Info combineWith(Info other) {
            Info info = this.withExceptions(other.exceptions);
            if (!other.isResultDeterministic) {
                info = info.withNondeterministicResult();
            }
            return info;
        }
    }

    /**
     * Creates a new Expression with 1 arguments.
     *
     * @param returnType The return type of the {@link Expression}.
     * @param s0 The first string, to be placed before {@code t0}.
     * @param t0 The type of the first argument.
     * @param s1 The last string, finishing the {@link Expression}.
     * @return the new {@link Expression}.
     */
    public static Expression make(CodeGenerationDataNameType returnType,
                                  String s0,
                                  CodeGenerationDataNameType t0,
                                  String s1) {
        return make(returnType, s0, t0, s1, new Info());
    }

    /**
     * Creates a new Expression with 1 argument.
     *
     * @param returnType The return type of the {@link Expression}.
     * @param s0 The first string, to be placed before {@code t0}.
     * @param t0 The type of the first argument.
     * @param s1 The last string, finishing the {@link Expression}.
     * @param info Additional information about the {@link Expression}.
     * @return the new {@link Expression}.
     */
    public static Expression make(CodeGenerationDataNameType returnType,
                                  String s0,
                                  CodeGenerationDataNameType t0,
                                  String s1,
                                  Info info) {
        return new Expression(returnType, List.of(t0), List.of(s0, s1), info);
    }

    /**
     * Creates a new Expression with 2 arguments.
     *
     * @param returnType The return type of the {@link Expression}.
     * @param s0 The first string, to be placed before {@code t0}.
     * @param t0 The type of the first argument.
     * @param s1 The second string, to be placed before {@code t1}.
     * @param t1 The type of the second argument.
     * @param s2 The last string, finishing the {@link Expression}.
     * @return the new {@link Expression}.
     */
    public static Expression make(CodeGenerationDataNameType returnType,
                                  String s0,
                                  CodeGenerationDataNameType t0,
                                  String s1,
                                  CodeGenerationDataNameType t1,
                                  String s2) {
        return make(returnType, s0, t0, s1, t1, s2, new Info());
    }

    /**
     * Creates a new Expression with 2 arguments.
     *
     * @param returnType The return type of the {@link Expression}.
     * @param s0 The first string, to be placed before {@code t0}.
     * @param t0 The type of the first argument.
     * @param s1 The second string, to be placed before {@code t1}.
     * @param t1 The type of the second argument.
     * @param s2 The last string, finishing the {@link Expression}.
     * @param info Additional information about the {@link Expression}.
     * @return the new {@link Expression}.
     */
    public static Expression make(CodeGenerationDataNameType returnType,
                                  String s0,
                                  CodeGenerationDataNameType t0,
                                  String s1,
                                  CodeGenerationDataNameType t1,
                                  String s2,
                                  Info info) {
        return new Expression(returnType, List.of(t0, t1), List.of(s0, s1, s2), info);
    }

    /**
     * Creates a new Expression with 3 arguments.
     *
     * @param returnType The return type of the {@link Expression}.
     * @param s0 The first string, to be placed before {@code t0}.
     * @param t0 The type of the first argument.
     * @param s1 The second string, to be placed before {@code t1}.
     * @param t1 The type of the second argument.
     * @param s2 The third string, to be placed before {@code t2}.
     * @param t2 The type of the third argument.
     * @param s3 The last string, finishing the {@link Expression}.
     * @return the new {@link Expression}.
     */
    public static Expression make(CodeGenerationDataNameType returnType,
                                  String s0,
                                  CodeGenerationDataNameType t0,
                                  String s1,
                                  CodeGenerationDataNameType t1,
                                  String s2,
                                  CodeGenerationDataNameType t2,
                                  String s3) {
        return make(returnType, s0, t0, s1, t1, s2, t2, s3, new Info());
    }

    /**
     * Creates a new Expression with 3 arguments.
     *
     * @param returnType The return type of the {@link Expression}.
     * @param s0 The first string, to be placed before {@code t0}.
     * @param t0 The type of the first argument.
     * @param s1 The second string, to be placed before {@code t1}.
     * @param t1 The type of the second argument.
     * @param s2 The third string, to be placed before {@code t2}.
     * @param t2 The type of the third argument.
     * @param s3 The last string, finishing the {@link Expression}.
     * @param info Additional information about the {@link Expression}.
     * @return the new {@link Expression}.
     */
    public static Expression make(CodeGenerationDataNameType returnType,
                                  String s0,
                                  CodeGenerationDataNameType t0,
                                  String s1,
                                  CodeGenerationDataNameType t1,
                                  String s2,
                                  CodeGenerationDataNameType t2,
                                  String s3,
                                  Info info) {
        return new Expression(returnType, List.of(t0, t1, t2), List.of(s0, s1, s2, s3), info);
    }

    /**
     * Creates a new Expression with 4 arguments.
     *
     * @param returnType The return type of the {@link Expression}.
     * @param s0 The first string, to be placed before {@code t0}.
     * @param t0 The type of the first argument.
     * @param s1 The second string, to be placed before {@code t1}.
     * @param t1 The type of the second argument.
     * @param s2 The third string, to be placed before {@code t2}.
     * @param t2 The type of the third argument.
     * @param s3 The fourth string, to be placed before {@code t3}.
     * @param t3 The type of the fourth argument.
     * @param s4 The last string, finishing the {@link Expression}.
     * @return the new {@link Expression}.
     */
    public static Expression make(CodeGenerationDataNameType returnType,
                                  String s0,
                                  CodeGenerationDataNameType t0,
                                  String s1,
                                  CodeGenerationDataNameType t1,
                                  String s2,
                                  CodeGenerationDataNameType t2,
                                  String s3,
                                  CodeGenerationDataNameType t3,
                                  String s4) {
        return make(returnType, s0, t0, s1, t1, s2, t2, s3, t3, s4, new Info());
    }

    /**
     * Creates a new Expression with 4 arguments.
     *
     * @param returnType The return type of the {@link Expression}.
     * @param s0 The first string, to be placed before {@code t0}.
     * @param t0 The type of the first argument.
     * @param s1 The second string, to be placed before {@code t1}.
     * @param t1 The type of the second argument.
     * @param s2 The third string, to be placed before {@code t2}.
     * @param t2 The type of the third argument.
     * @param s3 The fourth string, to be placed before {@code t3}.
     * @param t3 The type of the fourth argument.
     * @param s4 The last string, finishing the {@link Expression}.
     * @param info Additional information about the {@link Expression}.
     * @return the new {@link Expression}.
     */
    public static Expression make(CodeGenerationDataNameType returnType,
                                  String s0,
                                  CodeGenerationDataNameType t0,
                                  String s1,
                                  CodeGenerationDataNameType t1,
                                  String s2,
                                  CodeGenerationDataNameType t2,
                                  String s3,
                                  CodeGenerationDataNameType t3,
                                  String s4,
                                  Info info) {
        return new Expression(returnType, List.of(t0, t1, t2, t3), List.of(s0, s1, s2, s3, s4), info);
    }

    /**
     * Creates a {@link TemplateToken} for the use in a {@link Template} by applying the
     * {@code arguments} to the {@link Expression}. It is the users responsibility to
     * ensure that the argument tokens match the required {@link #argumentTypes}.
     *
     * @param arguments the tokens to be passed as arguments into the {@link Expression}.
     * @return a {@link TemplateToken} representing the {@link Expression} with applied arguments,
     *         for the use in a {@link Template}.
     */
    public TemplateToken asToken(List<Object> arguments) {
        if (arguments.size() != argumentTypes.size()) {
            throw new IllegalArgumentException("Wrong number of arguments:" +
                                               " expected: " + argumentTypes.size() +
                                               " but got: " + arguments.size() +
                                               " for " + this);
        }

        // List of tokens: interleave strings and arguments.
        List<Object> tokens = new ArrayList<>();
        for (int i = 0; i < argumentTypes.size(); i++) {
            tokens.add(strings.get(i));
            tokens.add(arguments.get(i));
        }
        tokens.add(strings.getLast());

        var template = Template.make(() -> scope(
            tokens
        ));
        return template.asToken();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("Expression[");

        for (int i = 0; i < this.argumentTypes.size(); i++) {
            sb.append("\"");
            sb.append(this.strings.get(i));
            sb.append("\", ");
            sb.append(this.argumentTypes.get(i).toString());
            sb.append(", ");
        }
        sb.append("\"");
        sb.append(this.strings.getLast());
        sb.append("\"]");
        return sb.toString();
    }

    /**
     * Create a nested {@link Expression} with a specified {@code returnType} from a
     * set of {@code expressions}.
     *
     * @param returnType the type of the return value.
     * @param expressions the list of {@link Expression}s from which we sample to create
     *                    the nested {@link Expression}.
     * @param maxNumberOfUsedExpressions the maximal number of {@link Expression}s from the
     *                                   {@code expressions} are nested.
     * @return a new randomly nested {@link Expression}.
     */
    public static Expression nestRandomly(CodeGenerationDataNameType returnType,
                                          List<Expression> expressions,
                                          int maxNumberOfUsedExpressions) {
        List<Expression> filtered = expressions.stream().filter(e -> e.returnType.isSubtypeOf(returnType)).toList();

        if (filtered.isEmpty()) {
            throw new IllegalArgumentException("Found no exception with the specified returnType.");
        }

        int r = RANDOM.nextInt(filtered.size());
        Expression expression = filtered.get(r);

        for (int i = 1; i < maxNumberOfUsedExpressions; i++) {
            expression = expression.nestRandomly(expressions);
        }
        return expression;
    }

    /**
     * Nests a random {@link Expression} from {@code nestingExpressions} into a random argument of
     * {@code this} {@link Expression}, ensuring compatibility of argument and return type.
     *
     * @param nestingExpressions list of expressions we sample from for the inner {@link Expression}.
     * @return a new nested {@link Expression}.
     */
    public Expression nestRandomly(List<Expression> nestingExpressions) {
        int argumentIndex = RANDOM.nextInt(this.argumentTypes.size());
        CodeGenerationDataNameType argumentType = this.argumentTypes.get(argumentIndex);
        List<Expression> filtered = nestingExpressions.stream().filter(e -> e.returnType.isSubtypeOf(argumentType)).toList();

        if (filtered.isEmpty()) {
            // Found no expression that has a matching returnType.
            return this;
        }

        int r = RANDOM.nextInt(filtered.size());
        Expression expression = filtered.get(r);

        return this.nest(argumentIndex, expression);
    }

    /**
     * Nests the {@code nestingExpression} into the specified {@code argumentIndex} of
     * {@code this} {@link Expression}.
     *
     * @param argumentIndex the index specifying at which argument of {@code this}
     *                      {@link Expression} we inser the {@code nestingExpression}.
     * @param nestingExpression the inner {@link Expression}.
     * @return a new nested {@link Expression}.
     */
    public Expression nest(int argumentIndex, Expression nestingExpression) {
        if (!nestingExpression.returnType.isSubtypeOf(this.argumentTypes.get(argumentIndex))) {
            throw new IllegalArgumentException("Cannot nest expressions because of mismatched types.");
        }

        List<CodeGenerationDataNameType> newArgumentTypes = new ArrayList<>();
        List<String> newStrings = new ArrayList<>();
        // s0 t0 s1 [S0 T0 S1 T1 S2] s2 t2 s3
        for (int i = 0; i < argumentIndex; i++) {
            newStrings.add(this.strings.get(i));
            newArgumentTypes.add(this.argumentTypes.get(i));
        }
        newStrings.add(this.strings.get(argumentIndex) +
                       nestingExpression.strings.getFirst()); // concat s1 and S0
        newArgumentTypes.add(nestingExpression.argumentTypes.getFirst());
        for (int i = 1; i < nestingExpression.argumentTypes.size(); i++) {
            newStrings.add(nestingExpression.strings.get(i));
            newArgumentTypes.add(nestingExpression.argumentTypes.get(i));
        }
        newStrings.add(nestingExpression.strings.getLast() +
                       this.strings.get(argumentIndex + 1)); // concat S2 and s2
        for (int i = argumentIndex+1; i < this.argumentTypes.size(); i++) {
            newArgumentTypes.add(this.argumentTypes.get(i));
            newStrings.add(this.strings.get(i+1));
        }

        return new Expression(this.returnType, newArgumentTypes, newStrings, this.info.combineWith(nestingExpression.info));
    }
}
