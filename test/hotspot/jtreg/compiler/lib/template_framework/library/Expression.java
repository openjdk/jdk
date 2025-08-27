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
import static compiler.lib.template_framework.Template.body;

/**
 * TODO: desc
 */
public class Expression {
    private static final Random RANDOM = Utils.getRandomInstance();

    public CodeGenerationDataNameType returnType;
    public List<CodeGenerationDataNameType> argumentTypes;
    List<String> strings;
    public Info info;

    private Expression(CodeGenerationDataNameType returnType,
                      List<CodeGenerationDataNameType> argumentTypes,
                      List<String> strings,
                      Info info) {
        if (argumentTypes.size() + 1 != strings.size()) {
            throw new RuntimeException("Must have one more string than argument.");
        }
        this.returnType = returnType;
        this.argumentTypes = argumentTypes;
        this.strings = strings;
        this.info = info;
    }


    /**
     * TODO: desc: used for all sorts of optional info.
     */
    public static class Info {
        public Set<String> exceptions = Set.of();
        public boolean isResultDeterministic = true;

        public Info() {}

        private Info(Info info) {
            this.exceptions = Set.copyOf(info.exceptions);
            this.isResultDeterministic = info.isResultDeterministic;
        }

        /**
         * TODO: desc union of exceptions
         */
        public Info withExceptions(Set<String> exceptions) {
            Info info = new Info(this);
            info.exceptions = Stream.concat(this.exceptions.stream(), exceptions.stream())
                                    .collect(Collectors.toSet());
            return info;
        }

        public Info withNondeterministicResult() {
            Info info = new Info(this);
            info.isResultDeterministic = false;
            return info;
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
     * Creates a new Espression with 1 arguments.
     *
     * @param returnType The return type of the expression.
     * @param s0 The first string, to be placed before {@code t0}.
     * @param t0 The type of the first argument.
     * @param s1 The last string, finishing the expression.
     */
    public static Expression make(CodeGenerationDataNameType returnType,
                                  String s0,
                                  CodeGenerationDataNameType t0,
                                  String s1) {
        return new Expression(returnType, List.of(t0), List.of(s0, s1), new Info());
    }

    /**
     * Creates a new Espression with 1 argument.
     *
     * @param returnType The return type of the expression.
     * @param s0 The first string, to be placed before {@code t0}.
     * @param t0 The type of the first argument.
     * @param s1 The last string, finishing the expression.
     * @param info Additional information about the Expression.
     */
    public static Expression make(CodeGenerationDataNameType returnType,
                                  String s0,
                                  CodeGenerationDataNameType t0,
                                  String s1,
                                  Info info) {
        return new Expression(returnType, List.of(t0), List.of(s0, s1), info);
    }

    /**
     * Creates a new Espression with 2 arguments.
     *
     * @param returnType The return type of the expression.
     * @param s0 The first string, to be placed before {@code t0}.
     * @param t0 The type of the first argument.
     * @param s1 The second string, to be placed before {@code t1}.
     * @param t1 The type of the second argument.
     * @param s2 The last string, finishing the expression.
     */
    public static Expression make(CodeGenerationDataNameType returnType,
                                  String s0,
                                  CodeGenerationDataNameType t0,
                                  String s1,
                                  CodeGenerationDataNameType t1,
                                  String s2) {
        return new Expression(returnType, List.of(t0, t1), List.of(s0, s1, s2), new Info());
    }

    /**
     * Creates a new Espression with 2 arguments.
     *
     * @param returnType The return type of the expression.
     * @param s0 The first string, to be placed before {@code t0}.
     * @param t0 The type of the first argument.
     * @param s1 The second string, to be placed before {@code t1}.
     * @param t1 The type of the second argument.
     * @param s2 The last string, finishing the expression.
     * @param info Additional information about the Expression.
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
     * Creates a new Espression with 3 arguments.
     *
     * @param returnType The return type of the expression.
     * @param s0 The first string, to be placed before {@code t0}.
     * @param t0 The type of the first argument.
     * @param s1 The second string, to be placed before {@code t1}.
     * @param t1 The type of the second argument.
     * @param s2 The third string, to be placed before {@code t2}.
     * @param t2 The type of the third argument.
     * @param s3 The last string, finishing the expression.
     */
    public static Expression make(CodeGenerationDataNameType returnType,
                                  String s0,
                                  CodeGenerationDataNameType t0,
                                  String s1,
                                  CodeGenerationDataNameType t1,
                                  String s2,
                                  CodeGenerationDataNameType t2,
                                  String s3) {
        return new Expression(returnType, List.of(t0, t1, t2), List.of(s0, s1, s2, s3), new Info());
    }

    /**
     * Creates a new Espression with 3 arguments.
     *
     * @param returnType The return type of the expression.
     * @param s0 The first string, to be placed before {@code t0}.
     * @param t0 The type of the first argument.
     * @param s1 The second string, to be placed before {@code t1}.
     * @param t1 The type of the second argument.
     * @param s2 The third string, to be placed before {@code t2}.
     * @param t2 The type of the third argument.
     * @param s3 The last string, finishing the expression.
     * @param info Additional information about the Expression.
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
     * Creates a new Espression with 4 arguments.
     *
     * @param returnType The return type of the expression.
     * @param s0 The first string, to be placed before {@code t0}.
     * @param t0 The type of the first argument.
     * @param s1 The second string, to be placed before {@code t1}.
     * @param t1 The type of the second argument.
     * @param s2 The third string, to be placed before {@code t2}.
     * @param t2 The type of the third argument.
     * @param s3 The fourth string, to be placed before {@code t3}.
     * @param t3 The type of the fourth argument.
     * @param s4 The last string, finishing the expression.
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
        return new Expression(returnType, List.of(t0, t1, t2, t3), List.of(s0, s1, s2, s3, s4), new Info());
    }

    /**
     * Creates a new Espression with 4 arguments.
     *
     * @param returnType The return type of the expression.
     * @param s0 The first string, to be placed before {@code t0}.
     * @param t0 The type of the first argument.
     * @param s1 The second string, to be placed before {@code t1}.
     * @param t1 The type of the second argument.
     * @param s2 The third string, to be placed before {@code t2}.
     * @param t2 The type of the third argument.
     * @param s3 The fourth string, to be placed before {@code t3}.
     * @param t3 The type of the fourth argument.
     * @param s4 The last string, finishing the expression.
     * @param info Additional information about the Expression.
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
     * TODO: desc
     */
    public TemplateToken asToken(List<Object> arguments) {
        if (arguments.size() != argumentTypes.size()) {
            throw new IllegalArgumentException("Wrong number of arguments:" +
                                               " expected: " + argumentTypes.size() +
                                               " but got: " + arguments.size());
        }

        // List of tokens: interleave strings and arguments.
        List<Object> tokens = new ArrayList<>();
        for (int i = 0; i < argumentTypes.size(); i++) {
            tokens.add(strings.get(i));
            tokens.add(arguments.get(i));
        }
        tokens.add(strings.get(strings.size()-1));

        var template = Template.make(() -> body(
            tokens
        ));
        return template.asToken();
    }

    /**
     * Nests a random expression from {@code nestingExpressions} into a random argument of
     * {@code this} expression, ensuring compatibility of argument and return type.
     */
    public Expression nestRandomly(List<Expression> nestingExpressions) {
        int slot = RANDOM.nextInt(this.argumentTypes.size());
        CodeGenerationDataNameType slotType = this.argumentTypes.get(slot);
        List<Expression> filtered = nestingExpressions.stream().filter(e -> e.returnType.isSubtypeOf(slotType)).toList();
        int r = RANDOM.nextInt(filtered.size());
        Expression expression = filtered.get(r);

        return this.nest(slot, expression);
    }

    /**
     * Nests the {@code nestingExpression} into the specified {@code slot} of
     * {@code this} expression.
     */
    public Expression nest(int slot, Expression nestingExpression) {
        if (!nestingExpression.returnType.isSubtypeOf(this.argumentTypes.get(slot))) {
            throw new IllegalArgumentException("Cannot nest expressions because of mismatched types.");
        }

        List<CodeGenerationDataNameType> newArgumentTypes = new ArrayList<>();
        List<String> newStrings = new ArrayList<>();
        // s0 t0 s1 [S0 T0 S1 T1 S2] s2 t2 s3
        for (int i = 0; i < slot; i++) {
            newStrings.add(this.strings.get(i));
            newArgumentTypes.add(this.argumentTypes.get(i));
        }
        newStrings.add(this.strings.get(slot) +
                       nestingExpression.strings.get(0)); // concat s1 and S0
        newArgumentTypes.add(nestingExpression.argumentTypes.get(0));
        for (int i = 1; i < nestingExpression.argumentTypes.size(); i++) {
            newStrings.add(nestingExpression.strings.get(i));
            newArgumentTypes.add(nestingExpression.argumentTypes.get(i));
        }
        newStrings.add(nestingExpression.strings.get(nestingExpression.strings.size() - 1) +
                       this.strings.get(slot + 1)); // concat S2 and s2
        for (int i = slot; i < this.argumentTypes.size(); i++) {
            newArgumentTypes.add(this.argumentTypes.get(i));
            newStrings.add(this.strings.get(i+1));
        }

        return new Expression(this.returnType, newArgumentTypes, newStrings, this.info.combineWith(nestingExpression.info));
    }

    //public static Expression nestRandomly(CodeGenerationDataNameType returnType,
    //                                      List<Expression> expressions,
    //                                      int numberOfUsedExpression) {
    //}
}
