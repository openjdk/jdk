/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TODO
 *
 * Brainstorming
 * -------------
 *
 * Scope
 * - Nesting
 * - available variables
 * - API for adding code?
 *
 * Template
 * - Manages local variables
 * - Replacements: nested CodeGenerator
 * - Can have free variables - to be set by Instantiator?
 *
 * CodeGenerator
 * - Can be Template or Programmatic
 * - Can have free variables - to be set by Instantiator?
 * - On instantiation, gets Scope and Instantiator/Args for free variables
 *   - Must generate code, variables, etc, push it to Scope.
 *   - Call nested CodeGenerator recursively - how to do Instantiator ... maybe via args?
 *
 * Parameters (Instantiator / Args for free variables)
 * - Must be passed on CodeGenerator initialization
 * - For Templates: fills free variable replacements
 * - For CodeGenerator: can be queried and used freely. This allows passing int values etc. as parameters.
 * - The args could either be a list or dict... I think dict with named args is better because
 *   that goes better with the Templates where an order is not really given for the free variables.
 *   Ok, so the args are strings. Basically w characters only, because Templates cannot pass anything else.
 *
 */
public final class Template implements CodeGenerator {
    // Match local variables:
    //   $name
    private static final String VARIABLE_PATTERN = "(\\$\\w+)";

    // Match local variable with type declaration:
    //   ${name:type}
    private static final String VARIABLE_WITH_TYPE_CHARS = "\\w:";
    private static final String VARIABLE_WITH_TYPE_PATTERN = "(\\$\\{[" + VARIABLE_WITH_TYPE_CHARS + "]+\\})";

    // Match replacements:
    //   #{name}
    //   #{name:generator}
    //   #{name:generator(arg1,arg2)}
    //   #{:generator}
    private static final String REPLACEMENT_CHARS = "\\w:\\(\\),";
    private static final String REPLACEMENT_PATTERN = "(#\\{[" + REPLACEMENT_CHARS + "]+\\})";

    // Match either variable or replacement.
    private static final String ALL_PATTERNS = "" +
                                               VARIABLE_PATTERN +
                                               "|" +
                                               VARIABLE_WITH_TYPE_PATTERN +
                                               "|" +
                                               REPLACEMENT_PATTERN +
                                               "";
    private static final Pattern PATTERNS = Pattern.compile(ALL_PATTERNS);

    private String templateString;

    public Template(String templateString) {
        this.templateString = templateString;
    }

    public String instantiate(Scope scope, Parameters parameters) {
        Matcher matcher = PATTERNS.matcher(templateString);

        while (matcher.find()) {
            System.out.println("group: " + matcher.group());
            int start = matcher.start();
            int end = matcher.end();
            String extract = templateString.substring(start, end);
            System.out.println("Found: " + extract);
        }

        return templateString;
    }
}
