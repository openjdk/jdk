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
 * Templates are {@link CodeGenerator}s, which can be conveniently specified as template Strings.
 * The template Strings can just be plain Strings, but they can also use additional
 * functionalities.
 * <p>
 * When using multiple templates, or the same template multiple times, the same variable
 * name may be used in different template instantiations, which can lead to variable name
 * collisions. For this, one can use the Template's variable renaming functionality, by
 * prepending the dollar sign to a variable name, e.g. {@code $name}. In the instantiation
 * the we append the unique {@link Parameters#instantiationID} to the variable name, which
 * ensures that the resulting variable names are distinct.
 * <p>
 * The String template may also contain template holes, either to be replaced with a
 * parameter value or with a recursive {@link CodeGenerator} instantiation. The syntax
 * for the parameter holes is {@code #{param}}, which is replaced with the String value
 * of the parameter {@code param}.
 * <p>
 * Recursive {@link CodeGenerator} instantiations can be specified with
 * {@code #{replacement_name:generator_name}}. The {@link CodeGenerator} with the name
 * {@code generator_name} is looked up in the {@link CodeGeneratorLibrary}, and is then
 * instantiated. The resulting string is inserted into the hole. One can then repeat
 * the same exact string with {@code #{replacement_name}}. Since repetition is not always
 * used, one can omit the {@code replacement_name}, and simply write
 * {@code #{:generator_name}}.
 * <p>
 * Some {@link CodeGenerator}s require parameters, which can be specified in this form:
 * {@code #{:generator_name(param1=value1,param2=value2)}}, where the String "param1"
 * is passed for parameter "param1" and the String "value2" is passed for parameter
 * "param2". One can also pass renamed variable names, or the replacement string of
 * an earlier recursive {@link CodeGenerator} instantiation, or a parameter value:
 * {@code #{:generator_name(param1=my_string,param2=$my_var,param3=#my_param,param4=#my_replacement)}},
 * where "my_string" is simply passed as this literal string, and for "$my_var" the
 * renamed variable name (e.g. "my_var_42") is passed, and for "#param" the parameter
 * value for the parameter "my_param" is passed, and for "#my_replacement" the
 * replacement string for the earlier instantiation with the name "my_replacement"
 * is passed.
 * <p>
 * Variables from the {@link Template} can be passed to the recursive instantiations,
 * by specifying them in a comma separated list:
 * {@code #{:generator_name:var1,var2,var3}}, where the renamed variables "var1",
 * "var2" and "var3" are passed to the recursive instantiation scope, where they
 * can then be sampled via {@link Scope#sampleVariable}, or with recursive calls
 * to {@link CodeGenerator}s that wrap this functionality, such as
 * {@code load(type=type_name)} or {@code store(type=type_name)}.
 * <p>
 * One can specify the beginning and end of class bodies and method bodies with
 * {@code #open(class)},
 * {@code #close(class)},
 * {@code #open(method)}, and
 * {@code #close(method)}.
 * This is useful for recursive {@link CodeGenerator}s which may want to add
 * additional class fields or method variables to the respecitive scopes.
 */
public final class Template extends CodeGenerator {

    /**
     * The default {@link fuelCost} for a {@link Template}. Fuel cost are used to guide
     * the choice of recursive {@link CodeGenerator} instantiation, and ensure eventual
     * termination at a recursive depth where the remaining fuel reaches zero.
     */
    public static final int DEFAULT_FUEL_COST = 10;

    /**
     * Match local variables:
     *   $name
     */
    private static final String VARIABLE_PATTERN = "(\\$\\w+)";

    /**
     * Match local variable with type declaration:
     *   ${name:type}
     */
    private static final String VARIABLE_WITH_TYPE_CHARS = "\\w:";
    private static final String VARIABLE_WITH_TYPE_PATTERN = "(\\$\\{[" + VARIABLE_WITH_TYPE_CHARS + "]+\\})";

    /**
     * Match replacements:
     *   #{name}
     *   #{name:generator}
     *   #{name:generator(arg1=v1,arg2=v2)}
     *   #{name:generator(arg1=$var,arg2=v2)}
     *   #{name:generator(arg1=#param,arg2=v2)}
     *   #{:generator}
     *   #{:generator:var1,var2,var3}
     */
    private static final String KEY_VALUE_PATTERN = "\\w+=[\\$#]?[\\w\\|]*";
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

    /**
     *Match newline + indentation:
     */
    private static final String NEWLINE_AND_INDENTATION_PATTERN = "(\\n *)";

    /**
     * Scopes:
     *   #open(class)
     *   #close(class)
     *   #open(method)
     *   #close(method)
     */
    private static final String SCOPE_PATTERN = "(#\\w+\\(\\w+\\))";

    /**
     * Match either variable or replacement or newline or scopes.
     */
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

    /**
     * Create a new {@link Template}, with custom {@link fuelCost}.
     *
     * @param templateName Name of the template, can be used for lookup in the
     *                     {@link CodeGeneratorLibrary} if the {@link Template}
     *                     is added to a library.
     * @param templateString The String specifying the content of the {@link Template}.
     * @param fuelCost The {@link fuelCost} for the {@link Template}.
     */
    public Template(String templateName, String templateString, int fuelCost) {
        super(templateName, fuelCost);
        this.templateString = templateString;
    }

    /**
     * Create a new {@link Template}, with default {@link fuelCost}.
     *
     * @param templateName Name of the template, can be used for lookup in the
     *                     {@link CodeGeneratorLibrary} if the {@link Template}
     *                     is added to a library.
     * @param templateString The String specifying the content of the {@link Template}.
     */
    public Template(String templateName, String templateString) {
        this(templateName, templateString, DEFAULT_FUEL_COST);
    }

    /**
     * Package-private helper class, used to remember replacement strings from
     * recursive {@link CodeGenerator}. In most cases, there is one such
     * {@link ReplacementState} per instantiation. However, one can also share
     * this state across the instantiation of multiple {@link Template}s, which
     * means that a replacement in an earlier instantiation can be remembered
     * in a later instantiation, see {@link TestClassInstantiator#Instantiator#generate}.
     */
    static final class ReplacementState {
        private HashMap<String,CodeStream> replacementsMap;

        public ReplacementState() {
            this.replacementsMap = new HashMap<String,CodeStream>();
        }

        public void checkHasNot(String name, Scope scope, String templated) {
            if (!name.equals("") && replacementsMap.containsKey(name)) {
                scope.print();
                throw new TemplateFrameworkException("Template generator call is not the first use of " + name +
                                                     ". Got " + templated);
            }
        }

        public void put(String name, CodeStream stream) {
            if (!name.equals("")) {
                replacementsMap.put(name, stream);
            }
        }

        public CodeStream get(String name, Scope scope, String templated) {
            if (name.equals("")) {
                scope.print();
                throw new TemplateFrameworkException("Template syntax error. Got: " + templated);
            }
            if (!replacementsMap.containsKey(name)) {
                scope.print();
                throw new TemplateFrameworkException("Template replacement error. Was neither parameter nor " +
                                                     "repeat of previous generator call: " + templated);
            }

            return replacementsMap.get(name);
        }
    }

    /**
     * Helper class for all states in an instantiation, including {@link Parameters},
     * {@link ReplacementState}, local variable names with their renamings, and the
     * recursive {@link Scope}s inside the {@link Template}.
     */
    private final class InstantiationState {
        public final Scope templateScope;
        public Scope currentScope;
        public final Parameters parameters;

        /**
         * Map local variable types, so we know them after their declaration.
         */
        record TypeAndMutability(String type, boolean mutable) {}
        private HashMap<String,TypeAndMutability> localVariables;

        public final ReplacementState replacementState;

        public InstantiationState(Scope scope, Parameters parameters, ReplacementState replacementState) {
            this.templateScope = scope;
            this.currentScope = scope;
            this.parameters = parameters;
            this.localVariables = new HashMap<String,TypeAndMutability>();
            this.replacementState = replacementState;
        }

        /**
         * Rename the variable names by appending the unique {@link Parameters#instantiationID},
         * to avoid variable name collisions.
         */
        public String wrapVariable(String name, String templated) {
            if (name.equals("")) {
                currentScope.print();
                throw new TemplateFrameworkException("Template local variable cannot be empty string. Got: " + templated);
            }
            int id = parameters.instantiationID;
            return name + "_" + id;
        }

        public void registerVariable(String name, String type, boolean mutable) {
            if (localVariables.containsKey(name)) {
                currentScope.print();
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
                                        Map<String,String> parameterMap,
                                        List<String> variableList,
                                        String templated) {
            replacementState.checkHasNot(name, currentScope, templated);

            CodeGenerator generator = templateScope.library().find(generatorName, ", got " + templated);

            // Create nested scope, and add the new variables to it.
            Scope nestedScope = new Scope(currentScope, currentScope.fuel - generator.fuelCost);
            for (String variable : variableList) {
                variable = wrapVariable(variable, templated);
                TypeAndMutability typeAndMutability = getVariable(variable);
                if (typeAndMutability == null) {
                    nestedScope.print();
                    throw new TemplateFrameworkException("Template generator call error. Variable type declaration not found" +
                                                         " for variable " + variable +
                                                         ". For generator call " + templated);
                }
                nestedScope.addVariable(variable, typeAndMutability.type, typeAndMutability.mutable);
            }

            Parameters parameters = new Parameters(parameterMap);
            generator.instantiate(nestedScope, parameters);
            nestedScope.close();

            // Map replacement for later repeats.
            replacementState.put(name, nestedScope.stream);

            // Add all generated code to the outer scope's stream.
            currentScope.stream.addCodeStream(nestedScope.stream);
        }

        /**
          * Fetch earlier stream generated with the generator, and push it again.
          */
        public void repeatReplacement(String name, String templated) {
            CodeStream stream = replacementState.get(name, currentScope, templated);
            currentScope.stream.addCodeStream(stream);
        }

        public void openClassScope() {
            ClassScope classScope = new ClassScope(currentScope, currentScope.fuel);
            classScope.setDebugContext("inside template", null);
            currentScope = classScope;
        }

        public void closeClassScope() {
            if (!(currentScope instanceof ClassScope)) {
                currentScope.print();
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
            methodScope.setDebugContext("inside template", null);
            currentScope = methodScope;
        }

        public void closeMethodScope() {
            if (!(currentScope instanceof MethodScope)) {
                currentScope.print();
                throw new TemplateFrameworkException("Template scope mismatch.");
            }
            Scope methodScope = currentScope;
            currentScope = methodScope.parent;
            methodScope.stream.setIndentation(0);
            methodScope.close();
            currentScope.stream.addCodeStream(methodScope.stream);
        }
    }

    /**
     * Instantiate the {@link Template}.
     *
     * @param scope Scope into which the code is generated.
     * @param parameters Provides the parameters for the instantiation, as well as a unique ID
     *                   for identifier name generation (e.g. variable of method names).
     */
    @Override
    public void instantiate(Scope scope, Parameters parameters) {
        ReplacementState replacementState = new ReplacementState();
        instantiate(scope, parameters, replacementState);
    }

    /**
     * Instantiate the {@link Template}, with a possibly shared {@link ReplacementState} to
     * share replacements from recursive {@link CodeGenerator} calls between the instantiation
     * of multiple {@link Template}s.
     *
     * @param scope Scope into which the code is generated.
     * @param parameters Provides the parameters for the instantiation, as well as a unique ID$
     *                   for identifier name generation (e.g. variable of method names).
     * @param replacementState Possibly shared replacement state.
     */
    public void instantiate(Scope scope, Parameters parameters, ReplacementState replacementState) {
        scope.setDebugContext(name, parameters);
        InstantiationState state = new InstantiationState(scope, parameters, replacementState);

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
                    state.currentScope.print();
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

    /**
     * Helper method used during template parsing for instantiation, which handles all the
     * template variable, parameter and replacement patterns.
     */
    private void handleTemplated(InstantiationState state, String templated) {
        if (templated.startsWith("${")) {
            // Local variable with type declaration: ${name} or ${name:type} or ${name:type:final}
            String[] parts = templated.substring(2, templated.length() - 1).split(":");
            if (parts.length > 3 || (parts.length == 3 && !parts[2].equals("final"))) {
                state.currentScope.print();
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
                state.currentScope.print();
                throw new TemplateFrameworkException("Template replacement syntax error. Should be " +
                                                     "#{name:generator:variables}, but got " + templated);
            }
            String name = parts[0];
            String generator = (parts.length > 1) ? parts[1] : "";
            String variables = (parts.length > 2) ? parts[2] : "";

            // Can only have variables if there is a generator.
            if (generator.equals("") && !variables.equals("")) {
                state.currentScope.print();
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
                        state.currentScope.print();
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
                    state.currentScope.print();
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
                    state.currentScope.print();
                    throw new TemplateFrameworkException("Template replacement syntax error (generator brackets). " +
                                                         "Generator name: " + generatorName + ". " +
                                                         "Generator arguments: " + generatorArguments + ". " +
                                                         "Found in: " + templated);
                }

                // Arguments:
                //   arg=text
                //   arg=$var
                //   arg=#param
                //   arg=#repeat_replacement
                Map<String,String> parameterMap = parseKeyValuePairs(generatorArguments, state);
                parameterMap = parameterMap.entrySet().stream().collect(Collectors.toMap(
                    e -> e.getKey(),
                    e -> {
                        String val = e.getValue();
                        if (val.startsWith("$")) {
                            return state.wrapVariable(val.substring(1), e.getKey() + "=$" + val + " in " + templated);
                        } else if (val.startsWith("#")) {
                            String n = val.substring(1);
                            // Try parameter
                            String parameterValue = state.parameters.getOrNull(n);
                            if (parameterValue != null) {
                                return parameterValue;
                            }
                            // Else replacement
                            CodeStream repeatReplacement = state.replacementState.get(n, state.currentScope, templated);
                            return repeatReplacement.toString();
                        }
                        return val;
                    }
                ));

                // Pattern: "$v1,$v2,$v3" -> v1 v2 v3
                String[] variableArray = variables.equals("") ? new String[0] : variables.split(",");
                List<String> variableList = Arrays.stream(variableArray).map(s -> s.substring(1)).toList();
                state.handleGeneratorCall(name, generatorName, parameterMap, variableList, templated);
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
                state.currentScope.print();
                throw new TemplateFrameworkException("Template scope syntax error. Got: " + templated);
            }
        } else {
            state.currentScope.print();
            throw new TemplateFrameworkException("Template pattern not handled: " + templated);
        }
    }

    /**
     * Helper method to parse a comma separated list of key-value pairs.
     */
    private static HashMap<String,String> parseKeyValuePairs(String pairs, InstantiationState state) {
        HashMap<String,String> map = new HashMap<String,String>();
        if (!pairs.equals("")) {
            for (String pair : pairs.split(",")) {
                String[] parts = pair.split("=", -1);
                if (parts.length != 2) {
                    state.currentScope.print();
                    throw new TemplateFrameworkException("Template syntax error in key value pairs. " +
                                                         "Got: " + pairs);
                }
                String key = parts[0];
                String val = parts[1];
                String oldVal = map.put(key, val);
                if (oldVal != null) {
                    state.currentScope.print();
                    throw new TemplateFrameworkException("Template syntax error in key value pairs. " +
                                                         "Duplicate of key " + key + ". " +
                                                         "Got: " + pairs);
                }
            }
        }
        return map;
    }
}
