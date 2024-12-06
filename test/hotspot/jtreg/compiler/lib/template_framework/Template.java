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

import java.util.Arrays;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * TODO
 *
 * - Extend library
 * - Way to add variable to method/class scope
 *
 * - Convenience Classes:
 *   - Repeat test, maybe with set of values for parameters
 *   - Integrate with IR Framework
 *   - Wrap whole class in Template
 * - Easy generation of programmatic CodeGenerator
 *   - improve API for recursive calls, parameter checks/load, etc
 *
 * Tests:
 * - List of ops, test with any inputs
 * - Example test / library that generates random classes, generates objects, loads / stores fields
 *   - Good for: Valhalla, escape analysis, maybe type system, maybe method inlining etc.
 *
 */
public final class Template implements CodeGenerator {
    public static final int DEFAULT_FUEL_COST = 10;
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
    //   #{name:generator(arg1=v1,arg2=v2)}
    //   #{name:generator(arg1=$var,arg2=v2)}
    //   #{name:generator(arg1=#param,arg2=v2)}
    //   #{:generator}
    private static final String KEY_VALUE_PATTERN = "\\w+=[\\$#]?\\w*";
    private static final String KEY_VALUE_LIST_PATTERN = "(?:" + // open non-capturing group 1
                                                             KEY_VALUE_PATTERN +
                                                             "(?:" + // open non-capturing group 1
                                                                 "," + KEY_VALUE_PATTERN +
                                                             ")*" +  // Do 0.. times
                                                         ")?";   // Do 0 or 1 times.
    private static final String GENERATOR_PATTERN = "(?:" + // open non-capturing group 1
                                                        "\\w+" + // generator name
                                                        "(?:" + // open non-capturing group 2: "(args)"
                                                            "\\(" +
                                                                KEY_VALUE_LIST_PATTERN +
                                                            "\\)" +
                                                        ")?" +
                                                    ")?";   // Do 0 or 1 times.
    private static final String VARIABLE_LIST_PATTERN = "(?:" + // open non-capturing group 1
                                                            "\\$\\w+" +
                                                            "(?:" + // open non-capturing group 1
                                                                ",\\$\\w+" +
                                                            ")*" +  // Do 0.. times
                                                        ")?";   // Do 0 or 1 times.
    private static final String REPLACEMENT_PATTERN = "(" + // capturing group
                                                          "#\\{" +
                                                              "\\w*" +
                                                              "(?:" +
                                                                  ":" +
                                                                  GENERATOR_PATTERN +
                                                                  "(?:" +
                                                                      ":" +
                                                                      VARIABLE_LIST_PATTERN +
                                                                  ")?" +
                                                              ")?" +
                                                          "\\}" +
                                                      ")";

    // Match newline + indentation:
    private static final String NEWLINE_AND_INDENTATION_PATTERN = "(\\n *)";

    // Scopes:
    //   #open(class)
    //   #close(class)
    //   #open(method)
    //   #close(method)
    private static final String SCOPE_PATTERN = "(#\\w+\\(\\w+\\))";

    // Match either variable or replacement or newline or scopes.
    private static final String ALL_PATTERNS = "" +
                                               VARIABLE_PATTERN +
                                               "|" +
                                               VARIABLE_WITH_TYPE_PATTERN +
                                               "|" +
                                               REPLACEMENT_PATTERN +
                                               "|" +
                                               NEWLINE_AND_INDENTATION_PATTERN +
                                               "|" +
                                               SCOPE_PATTERN +
                                               "";
    private static final Pattern PATTERNS = Pattern.compile(ALL_PATTERNS);

    private final String templateString;
    private final int templateFuelCost;


    public Template(String templateString, int fuelCost) {
        // Trim to remove the newline at the end of mutli-line strings.
        this.templateString = templateString.trim();
        this.templateFuelCost = fuelCost;
    }

    public Template(String templateString) {
        this(templateString, DEFAULT_FUEL_COST);
    }

    public int fuelCost() {
        return templateFuelCost;
    }

    private class InstantiationState {
        public final Scope templateScope;
        public Scope currentScope;
        public final Parameters parameters;

        record TypeAndMutability(String type, boolean mutable) {}

        // Map local variable types, so we know them after their declaration.
        private HashMap<String,TypeAndMutability> localVariables;

        // Map replacements / code generated by generator call, so that we know
        // them if/when we need to repeat a replacement (i.e. same name).
        private HashMap<String,CodeStream> replacementsMap;

        public InstantiationState(Scope scope, Parameters parameters) {
            this.templateScope = scope;
            this.currentScope = scope;
            this.parameters = parameters;
            this.localVariables = new HashMap<String,TypeAndMutability>();
            this.replacementsMap = new HashMap<String,CodeStream>();
        }

        public String wrapVariable(String name, String templated) {
            if (name.equals("")) {
                throw new TemplateFrameworkException("Template local variable cannot be empty string. Got: " + templated);
            }
            int id = parameters.instantiationID;
            return name + "_" + id;
        }

        public void registerVariable(String name, String type, boolean mutable) {
            System.out.println("register " + name + " " + type + " " + mutable);
            if (localVariables.containsKey(name)) {
                throw new TemplateFrameworkException("Template local variable with type declaration " +
                                                     "${" + name + ":" + type + "} was not the first use of the variable.");
            }
            localVariables.put(name, new TypeAndMutability(type, mutable));
        }

        public void registerVariable(String name) {
            if (!localVariables.containsKey(name)) {
                localVariables.put(name, null);
            }
        }

        public TypeAndMutability getVariable(String name) {
            return localVariables.get(name);
        }

        public void handleGeneratorCall(String name,
                                        String generatorName,
                                        Map<String,String> argumentsMap,
                                        List<String> variableList,
                                        String templated) {
            if (!name.equals("") && replacementsMap.containsKey(name)) {
                throw new TemplateFrameworkException("Template generator call is not the first use of " + name +
                                                     ". Got " + templated);
            }

            CodeGenerator generator = templateScope.library().find(generatorName, ", got " + templated);

            // Create nested scope, and add the new variables to it.
            Scope nestedScope = new Scope(currentScope, currentScope.fuel - generator.fuelCost());
            for (String variable : variableList) {
                variable = wrapVariable(variable, templated);
                TypeAndMutability typeAndMutability = getVariable(variable);
                if (typeAndMutability == null) {
                    throw new TemplateFrameworkException("Template generator call error. Variable type declaration not found" +
                                                         " for variable " + variable +
                                                         ". For generator call " + templated);
                }
                nestedScope.addVariable(variable, typeAndMutability.type, typeAndMutability.mutable);
            }

            Parameters parameters = new Parameters(argumentsMap);
            generator.instantiate(nestedScope, parameters);
            nestedScope.close();

            // Map replacement for later repeats.
            if (!name.equals("")) {
                replacementsMap.put(name, nestedScope.stream);
            }

            // Add all generated code to the outer scope's stream.
            currentScope.stream.addCodeStream(nestedScope.stream);
        }

        public void repeatReplacement(String name, String templated) {
            if (name.equals("")) {
                throw new TemplateFrameworkException("Template syntax error. Got: " + templated);
            }
            if (!replacementsMap.containsKey(name)) {
                parameters.print();
                throw new TemplateFrameworkException("Template replacement error. Was neither parameter nor " +
                                                     "repeat of previous generator call: " + templated);
            }

            // Fetch earlier stream generated with the generator, and push it again.
            currentScope.stream.addCodeStream(replacementsMap.get(name));
        }

        public void openClassScope() {
            ClassScope classScope = new ClassScope(currentScope, currentScope.fuel);
            currentScope = classScope;
        }

        public void closeClassScope() {
            if (!(currentScope instanceof ClassScope)) {
                throw new TemplateFrameworkException("Template scope mismatch.");
            }
            Scope classScope = currentScope;
            currentScope = classScope.parent;
            classScope.stream.setIndentation(0);
            classScope.close();
            currentScope.stream.addCodeStream(classScope.stream);
        }

        public void openMethodScope() {
            MethodScope methodScope = new MethodScope(currentScope, currentScope.fuel);
            currentScope = methodScope;
        }

        public void closeMethodScope() {
            if (!(currentScope instanceof MethodScope)) {
                throw new TemplateFrameworkException("Template scope mismatch.");
            }
            Scope methodScope = currentScope;
            currentScope = methodScope.parent;
            methodScope.stream.setIndentation(0);
            methodScope.close();
            currentScope.stream.addCodeStream(methodScope.stream);
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
            state.currentScope.stream.addCodeToLine(nonTemplated);

            if (templated.startsWith("\n")) {
                // Newline with indentation
                int spaces = templated.length() - 1;
                if (spaces % 4 != 0) {
                    throw new TemplateFrameworkException("Template non factor-of-4 indentation: " + spaces);
                }
                // Compute indentation relative to templateScope from spaces.
                int indentation = spaces / 4 - state.currentScope.indentationFrom(state.templateScope);
                // Empty lines can have zero spaces -> would lead to negative local indentation.
                indentation = Math.max(0, indentation);
                state.currentScope.stream.setIndentation(indentation);
                state.currentScope.stream.addNewline();
            } else {
                // The templated code needs to be analyzed and transformed or recursively generated.
                handleTemplated(state, templated);
            }
        }

        // Cleanup: part after the last templated segments.
        String nonTemplated = templateString.substring(pos);
        state.currentScope.stream.addCodeToLine(nonTemplated);

        // Cleanup: revert any indentation
        state.currentScope.stream.setIndentation(0);
    }

    private void handleTemplated(InstantiationState state, String templated) {
        if (templated.startsWith("${")) {
            // Local variable with type declaration: ${name} or ${name:type} or ${name:type:final}
            String[] parts = templated.substring(2, templated.length() - 1).split(":");
            if (parts.length > 3 || (parts.length == 3 && !parts[2].equals("final"))) {
                throw new TemplateFrameworkException("Template local variable with type declaration should have format " +
                                                     "$name or ${name} or ${name:type} or ${name:type:final}, but got " + templated);
            }
            String name = state.wrapVariable(parts[0], templated);
            if (parts.length == 1) {
                state.registerVariable(name);
                state.currentScope.stream.addCodeToLine(name);
                return;
            }
            String type = parts[1];
            boolean mutable = parts.length == 2; // third position is "final" qualifier.
            state.registerVariable(name, type, mutable);
            state.currentScope.stream.addCodeToLine(name);
        } else if (templated.startsWith("$")) {
            // Local variable: $name
            String name = state.wrapVariable(templated.substring(1), templated);
            state.registerVariable(name);
            state.currentScope.stream.addCodeToLine(name);
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
                String parameterValue = state.parameters.getOrNull(name);
                if (parameterValue != null) {
                    // It is a parameter value.
                    if (!generator.equals("") || !variables.equals("")) {
                        throw new TemplateFrameworkException("Template replacement error: " + name + "is given as " +
                                                             "parameter with value " + parameterValue + ", so we " +
                                                             "cannot also define a generator. Got " + templated);
                    }
                    state.currentScope.stream.addCodeToLine(parameterValue);
                    return;
                }
            }

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
                String generatorArguments = null;
                if (openPos == -1) {
                    generatorName = generator;
                    generatorArguments = "";
                } else {
                    generatorName = generator.substring(0, openPos);
                    generatorArguments = generator.substring(openPos + 1, generator.length() - 1);
                }
                if (generatorName.contains("(") ||
                    generatorName.contains(")") ||
                    generatorArguments.contains("(") ||
                    generatorArguments.contains(")")) {
                    throw new TemplateFrameworkException("Template replacement syntax error (generator brackets). " +
                                                         "Generator name: " + generatorName + ". " +
                                                         "Generator arguments: " + generatorArguments + ". " +
                                                         "Found in: " + templated);
                }

                // Arguments:
                //   arg=text
                //   arg=$var
                //   arg=#param
                Map<String,String> argumentsMap = parseKeyValuePairs(generatorArguments);
                argumentsMap = argumentsMap.entrySet().stream().collect(Collectors.toMap(
                    e -> e.getKey(),
                    e -> {
                        String val = e.getValue();
                        if (val.startsWith("$")) {
                            return state.wrapVariable(val.substring(1), e.getKey() + "=" + val + " in " + templated);
                        } else if (val.startsWith("#")) {
                            return state.parameters.get(name, e.getKey() + "=" + " in " + templated);
                        }
                        return val;
                    }
                ));

                // Pattern: "$v1,$v2,$v3" -> v1 v2 v3
                String[] variableArray = variables.equals("") ? new String[0] : variables.split(",");
                List<String> variableList = Arrays.stream(variableArray).map(s -> s.substring(1)).toList();
                state.handleGeneratorCall(name, generatorName, argumentsMap, variableList, templated);
                return;
            }

            // Default case: Repeat an earlier replacement.
            state.repeatReplacement(name, templated);
        } else if (templated.startsWith("#")) {
            // Scope: #open(method)
            String[] parts = templated.substring(1,templated.length()-1).split("\\(");
            String scopeAction = parts[0];
            String scopeKind = parts[1];

            if (scopeAction.equals("open") && scopeKind.equals("class")) {
                state.openClassScope();
            } else if (scopeAction.equals("close") && scopeKind.equals("class")) {
                state.closeClassScope();
            } else if (scopeAction.equals("open") && scopeKind.equals("method")) {
                state.openMethodScope();
            } else if (scopeAction.equals("close") && scopeKind.equals("method")) {
                state.closeMethodScope();
            } else {
                throw new TemplateFrameworkException("Template scope syntax error. Got: " + templated);
            }
        } else {
            throw new TemplateFrameworkException("Template pattern not handled: " + templated);
        }
    }

    private static HashMap<String,String> parseKeyValuePairs(String pairs) {
        HashMap<String,String> map = new HashMap<String,String>();
        if (!pairs.equals("")) {
            for (String pair : pairs.split(",")) {
                String[] parts = pair.split("=");
                if (parts.length != 2) {
                    throw new TemplateFrameworkException("Template syntax error in key value pairs. " +
                                                         "Got: " + pairs);
                }
                String key = parts[0];
                String val = parts[1];
                String oldVal = map.put(key, val);
                if (oldVal != null) {
                    throw new TemplateFrameworkException("Template syntax error in key value pairs. " +
                                                         "Duplicate of key " + key + ". " +
                                                         "Got: " + pairs);
                }
            }
        }
        return map;
    }
}
