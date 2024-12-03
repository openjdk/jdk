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

import java.util.HashMap;
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

    private final String templateString;

    public Template(String templateString) {
        this.templateString = templateString;
    }

    private class InstantiationState {
        public final Scope scope;
        public final Parameters parameters;
        private HashMap<String,String> localVariableToType;
        private HashMap<String,String> replacementsMap;

        public InstantiationState(Scope scope, Parameters parameters) {
            this.scope = scope;
            this.parameters = parameters;
            this.localVariableToType = new HashMap<String,String>();
        }

        public void registerVariable(String name, String type) {
            if (localVariableToType.containsKey(name)) {
                throw new TemplateFrameworkException("Template local variable with type declaration " +
                                                     "${" + name + ":" + type + "} was not the first use of the variable.");
            }
            localVariableToType.put(name, type);
        }

        public void registerVariable(String name) {
            if (!localVariableToType.containsKey(name)) {
                localVariableToType.put(name, null);
            }
        }

        public void addCodeForVariable(String name) {
            int id = parameters.instantiationID;
            scope.addCodeToLine(name);
            scope.addCodeToLine("_");
            scope.addCodeToLine(Integer.toString(id));
        }

    }

    public void instantiate(Scope scope, Parameters parameters) {
        InstantiationState state = new InstantiationState(scope, parameters);

        // Parse the templateString, detect templated and nonTemplated segments.
        Matcher matcher = PATTERNS.matcher(templateString);
        int pos = 0;
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();

            // We have segments:  [pos...start] [start...end]
            //                    nonTemplated  templated
            String nonTemplated = templateString.substring(pos, start);
            String templated = templateString.substring(start, end);
            pos = end;

            // The nonTemplated code segment can simply be added.
            scope.addCode(nonTemplated);

            // The templated code needs to be analyzed and transformed or recursively generated.
            handleTemplated(state, templated);
        }

        // Cleanup: part after the last templated segments.
        String nonTemplated = templateString.substring(pos);
        scope.addCode(nonTemplated);
        scope.addNewline();
    }

    private void handleTemplated(InstantiationState state, String templated) {
        System.out.println("Found: " + templated);
        if (templated.startsWith("${")) {
            // Local variable with type declaration: ${name:type}
            int pos = templated.indexOf(':');
            String name = templated.substring(2, pos);
            String type = templated.substring(pos+1, templated.length() - 1);
            if (type.contains(":")) {
                throw new TemplateFrameworkException("Template local variable with type declaration should have format " +
                                                     "${name:type}, but got " + templated);
            }
            state.registerVariable(name, type);
            state.addCodeForVariable(name);
        } else if (templated.startsWith("$")) {
            // Local variable: $name
            String name = templated.substring(1);
            state.registerVariable(name);
            state.addCodeForVariable(name);
        } else if (templated.startsWith("#{")) {
            // Replacement: #{name:generator:variables}
            String replacement = templated.substring(2, templated.length() - 1);
            String[] parts = replacement.split(":");
            if (parts.length > 3) {
                throw new TemplateFrameworkException("Template replacement syntax error. Should be " +
                                                     "#{name:generator:variables}, but got " + templated);
            }
            String name = parts[0];
            String generator = (parts.length > 1) ? parts[1] : "";
            String variables = (parts.length > 2) ? parts[2] : "";

            // Can only have variables if there is a generator.
            if (generator.equals("") && !variables.equals("")) {
                throw new TemplateFrameworkException("Template replacement syntax error. Cannot have variables " +
                                                     "without generator. Usage: ${name:generator:variables}. Got " +
                                                     templated);
            }

            // Check if it is a parameter.
            if (!name.equals("")) {
                String parameterValue = state.parameters.get(name);
                if (parameterValue != null) {
                    // It is a parameter value.
                    if (!generator.equals("") || !variables.equals("")) {
                        throw new TemplateFrameworkException("Template replacement error: " + name + "is given as " +
                                                             "parameter with value " + parameterValue + ", so we " +
                                                             "cannot also define a generator. Got " + templated);
                    }
                    state.scope.addCode(parameterValue);
                    return;
                }
            }

            System.out.println("Replacement: #{" + name + ":" + generator + ":" + variables + "}");
            // TODO

            // Recursive generator call.
            if (!generator.equals("")) {
                // Parse generator string.
                int openPos = generator.indexOf('(');
                int closePos = generator.indexOf(')');
                if ((openPos == -1) != (closePos == -1) || (closePos != -1 && closePos != generator.length() - 1)) {
                    throw new TemplateFrameworkException("Template replacement syntax error (generator brackets). " +
                                                         "Got: " + templated);
                }
                String generatorName = null;
                String generatorParameters = null;
                if (openPos == -1) {
                    generatorName = generator;
                    generatorParameters = "";
                } else {
                    generatorName = generator.substring(0, openPos);
                    generatorParameters = generator.substring(openPos + 1, generator.length() - 1);
                }
                if (generatorName.contains("(") ||
                    generatorName.contains(")") ||
                    generatorParameters.contains("(") ||
                    generatorParameters.contains(")")) {
                    throw new TemplateFrameworkException("Template replacement syntax error (generator brackets). " +
                                                         "Generator name: " + generatorName + ". " +
                                                         "Generator parameters: " + generatorParameters + ". " +
                                                         "Found in: " + templated);
                }
                System.out.println("Generator: " + generatorName + " " + generatorParameters);
            }

            state.scope.addCode(templated);
        } else {
            throw new TemplateFrameworkException("Template pattern not handled: " + templated);
        }
    }
}
