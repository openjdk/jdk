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

import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.TemplateToken;
import static compiler.lib.template_framework.Template.body;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;

/**
 * TODO: desc
 */
public class Expression {

    public CodeGenerationDataNameType returnType;
    public List<CodeGenerationDataNameType> argumentTypes;
    private List<String> strings;
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

        public Info withExceptions(Set<String> exceptions) {
            Info info = new Info(this);
            info.exceptions = Set.copyOf(exceptions);
            return info;
        }

        public Info withNondeterministicResult() {
            Info info = new Info(this);
            info.isResultDeterministic = false;
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
}
