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
import java.util.Map;
import java.util.HashSet;
import java.util.Random;
import java.util.function.Consumer;

import jdk.test.lib.Utils;

import compiler.lib.generators.*;

/**
 * The {@link CodeGeneratorLibrary} provides a way to map {@link CodeGenerator} names to {@link CodeGenerator},
 * and provides the lookup facility required for recursive instantiation calls.
 */
public final class CodeGeneratorLibrary {
    private static final Random RANDOM = Utils.getRandomInstance();
    private static final IntGenerator intGenerator = Generators.ints();

    private CodeGeneratorLibrary parent;
    private HashMap<String,CodeGenerator> library;

    /**
     * Create a new {@link CodeGeneratorLibrary}.
     *
     * @param parent The parent library, or null. If a parent library is provided, that library is extended with
     *               the content of this library.
     * @param generators The set of generators for this library.
     */
    public CodeGeneratorLibrary(CodeGeneratorLibrary parent, HashSet<CodeGenerator> generators) {
        this.parent = parent;
        this.library = new HashMap<String,CodeGenerator>();
        for (CodeGenerator generator : generators) {
            if (findOrNull(generator.name) != null) {
                throw new TemplateFrameworkException("Code library already has a generator for name " + generator.name);
            }
            this.library.put(generator.name, generator);
        }
    }

    /**
     * Recursively find CodeGenerator with given name in this library or a parent library.
     *
     * @param name Name of the generator to find.
     * @param errorMessage Error message added in the exception if no generator is found for the name.
     * @return The generator from the library with the specified name.
     * @throws TemplateFrameworkException If no generator is found for the name.
     */
    public CodeGenerator find(String name, String errorMessage) {
        CodeGenerator codeGenerator = findOrNull(name);
        if (codeGenerator == null) {
            print();
            throw new TemplateFrameworkException("Code generator '" + name + "' not found" + errorMessage);
        }
        return codeGenerator;
    }

    /**
     * Recursively find CodeGenerator with given name in this library or a parent library.
     *
     * @param name Name of the generator to find.
     * @return The generator from the library with the specified name, or null if not found.
     */
    public CodeGenerator findOrNull(String name) {
        CodeGenerator codeGenerator = library.get(name);
        if (codeGenerator != null) {
            return codeGenerator;
        } else if (parent != null){
            return parent.findOrNull(name);
        } else {
            return null;
        }
    }

    /**
     * Print all generator names in the library.
     */
    public void print() {
        System.out.println("Library");
        for (Map.Entry<String,CodeGenerator> e : library.entrySet()) {
            System.out.println("  " + e.getKey() + ":   fuelCost=" + e.getValue().fuelCost);
        }
        if (parent != null) {
            parent.print();
        }
    }

    /**
     * {@code mutable_var} samples a random mutable variable.
     * {@code var} samples a random variable, including mutable and immutable.
     *
     * @param type Name of the type of the variable.
     * @return Name of the variable.
     */
    private static CodeGenerator factorySampleVariable(boolean mutable) {
        String generatorName = mutable ? "mutable_var" : "var";
        return new ProgrammaticCodeGenerator(generatorName, (Scope scope, Parameters parameters) -> {
            parameters.checkOnlyHas(scope, "type");
            String type = parameters.get("type", scope);
            String name = scope.sampleVariable(type, mutable);
            if (name == null) {
                scope.print();
                throw new TemplateFrameworkException("Generator call to load/store cannot find variable of type: " + type);
            }
            scope.stream.addCodeToLine(String.valueOf(name));
        }, 0);
    }

    private static CodeGenerator factoryDispatch() {
        return new ProgrammaticCodeGenerator("dispatch", (Scope scope, Parameters parameters) -> {
            String scopeKind = parameters.get("scope", scope);
            String generatorName = parameters.get("call", scope);
            CodeGenerator generator = scope.library().find(generatorName, " for dispatch in " + scopeKind + " scope");

            // Copy arguments, and remove the 2 args we just used. Forward the other args to the dispatch.
            HashMap<String,String> parameterMap = new HashMap<String,String>(parameters.getParameterMap());
            parameterMap.remove("scope");
            parameterMap.remove("call");

            switch(scopeKind) {
                case "class" -> {
                    ClassScope classScope = scope.classScope(" in dispatch for " + generatorName);
                    classScope.dispatch(generator, parameterMap);
                }
                case "method" -> {
                    MethodScope methodScope = scope.methodScope(" in dispatch for " + generatorName);
                    methodScope.dispatch(generator, parameterMap);
                }
                default -> {
                    scope.print();
                    throw new TemplateFrameworkException("Generator dispatch got: scope=" + scopeKind +
                                                         "but should be scope=class or scope=method");
                }
            }
        }, 0);
    }

    /**
     * {@code add_var} adds a variable.
     *
     * @param scope Either {@code class} or {@code method}, to add to class or method scope.
     * @param name Name of the variable.
     * @param type Name of the type of the variable.
     * @param mutable Optional (default true), if set to true: the variable is mutable, and
     *                can be chosen both via {@code var} and {@code mutable_var} sampling,
     *                if false: it can only be sampled via {@code var}.
     */
    private static CodeGenerator factoryAddVariable() {
        return new ProgrammaticCodeGenerator("add_var", (Scope scope, Parameters parameters) -> {
            parameters.checkOnlyHas(scope, "scope", "name", "type", "mutable");
            String scopeKind = parameters.get("scope", scope);
            String name = parameters.get("name", scope);
            String type = parameters.get("type", scope);
            String isMutable = parameters.getOrNull("mutable");

            if (isMutable != null && !isMutable.equals("true") && !isMutable.equals("false")) {
                scope.print();
                throw new TemplateFrameworkException("Generator 'add_var' got: mutable=" + isMutable +
                                                     "but should be mutable=true or mutable=false " +
                                                     "or unset with default false.");
            }
            boolean mutable = (isMutable == null) || isMutable.equals("true");

            switch(scopeKind) {
                case "class" -> {
                    ClassScope classScope = scope.classScope(" in 'add_var' for " + name);
                    classScope.addVariable(name, type, mutable);
                }
                case "method" -> {
                    MethodScope methodScope = scope.methodScope(" in 'add_var' for " + name);
                    methodScope.addVariable(name, type, mutable);
                }
                default -> {
                    scope.print();
                    throw new TemplateFrameworkException("Generator dispatch got: scope=" + scopeKind +
                                                         "but should be scope=class or scope=method");
                }
            }
        }, 0);
    }

    private static CodeGenerator factoryRepeat() {
        return new ProgrammaticCodeGenerator("repeat", (Scope scope, Parameters parameters) -> {
            String generatorName = parameters.get("call", scope);
            int repeat = parameters.getInt("repeat", scope);

            if (repeat > 1000) {
                scope.print();
                throw new TemplateFrameworkException("Generator repeat should have repeat <= 1000, got: " + repeat);
            }

            CodeGenerator generator = scope.library().find(generatorName, " for repeat");

            // Copy arguments, and remove the 2 args we just used. Forward the other args to the repeat.
            HashMap<String,String> parameterMap = new HashMap<String,String>(parameters.getParameterMap());
            parameterMap.remove("call");
            parameterMap.remove("repeat");

            for (int i = 0; i < repeat; i++) {
                generator.where(parameterMap).instantiate(scope);
            }
        }, 0);
    }

    /**
     * The standard library populated with a large set of {@link CodeGenerator}s.
     *
     * @return The standard library.
     */
    public static CodeGeneratorLibrary standard() {
        HashSet<CodeGenerator> codeGenerators = new HashSet<CodeGenerator>();

        addConstants(codeGenerators);

        codeGenerators.add(factoryDispatch());
        codeGenerators.add(factoryRepeat());

        addBasicOperators(codeGenerators);
        addVariableCodeGenerators(codeGenerators);
        addRandomCode(codeGenerators);
        return new CodeGeneratorLibrary(null, codeGenerators);
    }

    private static void addConstants(HashSet<CodeGenerator> codeGenerators) {
        /**
         * {@code int_con} returns a random int.
         *
         * @param lo Optional: lower inclusive bound of the range, default min_int.
         * @param hi Optional: upper inclusive bound of the range, default max_int.
         * @return Value in the range [lo,hi].
         */
        codeGenerators.add(new ProgrammaticCodeGenerator("int_con",
            (Scope scope, Parameters parameters) -> {
                parameters.checkOnlyHas(scope, "lo", "hi");
                int lo = parameters.getIntOrDefault("lo", Integer.MIN_VALUE, scope);
                int hi = parameters.getIntOrDefault("hi", Integer.MAX_VALUE, scope);

                int v = intGenerator.nextInt(lo, hi);
                scope.stream.addCodeToLine(String.valueOf(v));
        }, 0));
    }

    private static void addBasicOperators(HashSet<CodeGenerator> codeGenerators) {
        /**
         * {@code choose} picks a random choice from a list like "aaa|bbb|ccc", with separator "|".
         *
         * @param from List of strings, separated by "|".
         * @return One element from the list, picked uniformly at random.
         */
        codeGenerators.add(new ProgrammaticCodeGenerator("choose",
            (Scope scope, Parameters parameters) -> {
                parameters.checkOnlyHas(scope, "from");
                String list = parameters.get("from", scope);
                String[] elements = list.split("\\|");
                int r = RANDOM.nextInt(elements.length);
                scope.stream.addCodeToLine(elements[r]);
        }, 0));
    }

    private static void addVariableCodeGenerators(HashSet<CodeGenerator> codeGenerators) {
        // Add variable to ClassScope or MethodScope.
        codeGenerators.add(factoryAddVariable());

        // Sample random variable, mutable or immutable.
        codeGenerators.add(factorySampleVariable(false));
        codeGenerators.add(factorySampleVariable(true));

        // Internal: Define variable
        codeGenerators.add(new Template("_internal_def_var",
            "#{prefix} #{name} = #{value};#{:add_var(scope=method,name=#name,type=#type,mutable=#mutable)}"
        ));

        // Internal: Define field
        codeGenerators.add(new Template("_internal_def_field",
            "#{prefix} #{name} = #{value};#{:add_var(scope=class,name=#name,type=#type,mutable=#mutable)}"
        ));

        /**
         * {@code def_var} defines a variable in the current method scope.
         *
         * @param name Name of the variable.
         * @param prefix Access qualifier and type.
         * @param value Value assigned to the variable on definition.
         * @param type Type for which the variable can be sampled with {@code var} or {@code mutable_var}.
         */
        codeGenerators.add(new Template("def_var",
            "#{:dispatch(scope=method,call=_internal_def_var,name=#name,prefix=#prefix,value=#value,type=#type,mutable=true)}"
        ));

        /**
         * {@code def_final_var} defines a variable in the current method scope.
         *
         * @param name Name of the variable.
         * @param prefix Access qualifier and type.
         * @param value Value assigned to the variable on definition.
         * @param type Type for which the variable can be sampled with {@code var}.
         */
        codeGenerators.add(new Template("def_final_var",
            "#{:dispatch(scope=method,call=_internal_def_var,name=#name,prefix=#prefix,value=#value,type=#type,mutable=false)}"
        ));

        /**
         * {@code def_field} defines a field in the current class scope.
         *
         * @param name Name of the field.
         * @param prefix Access qualifier and type.
         * @param value Value assigned to the field on definition.
         * @param type Type for which the field can be sampled with {@code var} or {@code mutable_var}.
         */
        codeGenerators.add(new Template("def_field",
            "#{:dispatch(scope=class,call=_internal_def_field,name=#name,prefix=#prefix,value=#value,type=#type,mutable=true)}"
        ));

        /**
         * {@code def_final_field} defines a final field in the current class scope.
         *
         * @param name Name of the field.
         * @param prefix Access qualifier and type.
         * @param value Value assigned to the field on definition.
         * @param type Type for which the field can be sampled with {@code var}.
         */
        codeGenerators.add(new Template("def_final_field",
            "#{:dispatch(scope=class,call=_internal_def_field,name=#name,prefix=#prefix,value=#value,type=#type,mutable=false)}"
        ));
    }

    private static void addRandomCode(HashSet<CodeGenerator> codeGenerators) {
        codeGenerators.add(new Template("empty","/* empty */", 0));

        codeGenerators.add(new Template("method_code_split",
            """
            #{:method_code}
            #{:method_code}
            """
        ));

        // TODO some random if, loops, while, try/catch, random variables, etc

        /**
         * {@code method_code} recursively generates random code, to be instantiated inside a method body.
         */
        SelectorCodeGenerator selectorForCode = new SelectorCodeGenerator("method_code", "empty");
        selectorForCode.add("method_code_split",  100);
        // TODO add more
        codeGenerators.add(selectorForCode);
    }
}
