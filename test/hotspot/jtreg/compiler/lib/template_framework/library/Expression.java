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

/**
 * TODO: desc
 */
public class Expression {

    public CodeGenerationDataNameType returnType;
    public List<CodeGenerationDataNameType> argumentTypes;
    private List<String> strings;

    private Expression(CodeGenerationDataNameType returnType,
                      List<CodeGenerationDataNameType> argumentTypes,
                      List<String> strings) {
        if (argumentTypes.size() + 1 != strings.size()) {
            throw new RuntimeException("Must have one more string than argument.");
        }
        this.returnType = returnType;
        this.argumentTypes = argumentTypes;
        this.strings = strings;
    }


    /**
     * TODO: desc unary
     */
    public static Expression make(CodeGenerationDataNameType returnType,
                                  String s0,
                                  CodeGenerationDataNameType t0,
                                  String s1) {
        return new Expression(returnType, List.of(t0), List.of(s0, s1));
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
