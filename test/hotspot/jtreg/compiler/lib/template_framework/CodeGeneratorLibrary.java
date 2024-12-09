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
import java.util.Random;
import java.util.function.Consumer;

import jdk.test.lib.Utils;

/**
 * TODO
 */
public class CodeGeneratorLibrary {
    private static final Random RANDOM = Utils.getRandomInstance();

    private CodeGeneratorLibrary parent;
    private HashMap<String,CodeGenerator> library;

    CodeGeneratorLibrary(CodeGeneratorLibrary parent, HashMap<String,CodeGenerator> library) {
        this.parent = parent;
        if (parent != null) {
            for (String name : library.keySet()) {
                if (parent.findOrNull(name) != null) {
                    throw new TemplateFrameworkException("Code library already has a generator for name " + name);
                }
            }
        }
        this.library = new HashMap<String,CodeGenerator>(library);
    }

    /**
     * Recursively find CodeGenerator with given name in this library or parent library.
     */
    public CodeGenerator find(String name, String errorMessage) {
        CodeGenerator codeGenerator = findOrNull(name);
        if (codeGenerator == null) {
            throw new TemplateFrameworkException("Template generator '" + name + "' not found " + errorMessage);
        }
        return codeGenerator;
    }

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

    public static CodeGenerator factoryLoadStore(boolean mutable) {
        String generatorName = mutable ? "store" : "load";
        return new ProgrammaticCodeGenerator(generatorName, (Scope scope, Parameters parameters) -> {
            String type = parameters.get("type", " for generator call to load/store");
            String name = scope.sampleVariable(type, mutable);
            if (name == null) {
                scope.print();
                throw new TemplateFrameworkException("Generator call to load/store cannot find variable of type: " + type);
            }
            scope.stream.addCodeToLine(String.valueOf(name));
        }, 0);
    }

    public static CodeGenerator factoryDispatch() {
        return new ProgrammaticCodeGenerator("dispatch", (Scope scope, Parameters parameters) -> {
            String scopeKind = parameters.get("scope", " for generator call to 'dispatch'");
            String generatorName = parameters.get("call", " for generator call to 'dispatch'");
            CodeGenerator generator = scope.library().find(generatorName, " for dispatch in " + scopeKind + " scope");

            System.out.println("Dispatch " + generatorName + " to " + scopeKind);

            // Copy arguments, and remove the 2 args we just used. Forward the other args to the dispatch.
            HashMap<String,String> argumentsMap = new HashMap<String,String>(parameters.getArguments());
            argumentsMap.remove("scope");
            argumentsMap.remove("call");

            switch(scopeKind) {
                case "class" -> {
                    ClassScope classScope = scope.classScope(" in dispatch for " + generatorName);
                    classScope.dispatch(scope, generator, argumentsMap);
                }
                case "method" -> {
                    MethodScope methodScope = scope.methodScope(" in dispatch for " + generatorName);
                    methodScope.dispatch(scope, generator, argumentsMap);
                }
                default -> {
                    scope.print();
                    throw new TemplateFrameworkException("Generator dispatch got: scope=" + scopeKind +
                                                         "but should be scope=class or scope=method");
                }
            }
        }, 0);
    }

    public static CodeGenerator factoryAddVariable() {
        return new ProgrammaticCodeGenerator("add_variable", (Scope scope, Parameters parameters) -> {
            String scopeKind = parameters.get("scope", " for generator call to 'add_variable'");
            String name = parameters.get("name", " for generator call to 'add_variable'");
            String type = parameters.get("type", " for generator call to 'add_variable'");
            String isFinal = parameters.getOrNull("final");

            if (isFinal != null && !isFinal.equals("true") && !isFinal.equals("false")) {
                scope.print();
                throw new TemplateFrameworkException("Generator 'add_variable' got: final=" + isFinal +
                                                     "but should be final=true or final=false");
            }
            boolean mutable = isFinal.equals("false");

            switch(scopeKind) {
                case "class" -> {
                    ClassScope classScope = scope.classScope(" in 'add_variable' for " + name);
                    classScope.addVariable(name, type, mutable);
                }
                case "method" -> {
                    MethodScope methodScope = scope.methodScope(" in 'add_variable' for " + name);
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



    public static CodeGeneratorLibrary standard() {
        HashMap<String,CodeGenerator> codeGenerators = new HashMap<String,CodeGenerator>();

        // Random Constants.
        codeGenerators.put("int_con", new ProgrammaticCodeGenerator("int_con",
            (Scope scope, Parameters parameters) -> {
                String lo = parameters.getOrNull("lo");
                String hi = parameters.getOrNull("hi");

                if (lo == null && hi == null) {
                    // Full int range
                    int v = RANDOM.nextInt();
                    scope.stream.addCodeToLine(String.valueOf(v));
                } else if (lo == null) {
                    // Bounded: [min_int, hi)
                    int hiVal = parameters.getInt("hi", " In int_con.", scope);
                    if (hiVal == Integer.MIN_VALUE) {
                        scope.print();
                        throw new TemplateFrameworkException("Generator int_con must have min_int < hi");
                    }
                    int v = RANDOM.nextInt(Integer.MIN_VALUE, hiVal);
                    scope.stream.addCodeToLine(String.valueOf(v));
                } else if (hi == null) {
                    // Bounded: [lo, max_int]
                    int loVal = parameters.getInt("lo", " In int_con.", scope);
                    if (loVal == Integer.MIN_VALUE) {
                        // Full int range
                        int v = RANDOM.nextInt();
                        scope.stream.addCodeToLine(String.valueOf(v));
                    } else {
                        // We have to shift things to make sure max_int can be generated.
                        int v = RANDOM.nextInt(loVal-1, Integer.MAX_VALUE) + 1;
                        scope.stream.addCodeToLine(String.valueOf(v));
                    }
                } else {
                    // Bounded: [lo, hi)
                    int loVal = parameters.getInt("lo", " In int_con.", scope);
                    int hiVal = parameters.getInt("hi", " In int_con.", scope);
                    if (loVal >= hiVal) {
                        scope.print();
                        throw new TemplateFrameworkException("Generator int_con must have lo < hi.");
                    }
                    int v = RANDOM.nextInt(loVal, hiVal);
                    scope.stream.addCodeToLine(String.valueOf(v));
                }
           }, 0));

        // Variable load/store.
        codeGenerators.put("load",  factoryLoadStore(false));
        codeGenerators.put("store", factoryLoadStore(true));

        // Dispatch generator call to a ClassScope or MethodScope
        codeGenerators.put("dispatch", factoryDispatch());

        // Add variable to ClassScope or MethodScope.
        codeGenerators.put("add_variable", factoryAddVariable());

        // ClassScope generators.
        codeGenerators.put("new_field_in_class", new Template("new_field_in_class",
            """
            // start $new_field_in_class
            public static int #{name} = #{:int_con};
            #{:add_variable(scope=class,type=int,name=#name,final=#final):}
            // end   $new_field_in_class
            """
        ));

        // MethodScope generators.
        codeGenerators.put("new_var_in_method", new Template("new_var_in_method",
            """
            // start $new_var_in_method
            int #{name} = #{:int_con};
            #{:add_variable(scope=method,type=int,name=#name,final=#final):}
            // end   $new_var_in_method
            """
        ));

        // Code blocks.
        codeGenerators.put("empty", new Template("empty",
            """
            // $empty
            """
        ));
        codeGenerators.put("split", new Template("split",
            """
            // start $split
                #{:code}
            // mid   $split
                #{:code}
            // end   $split
            """
        ));
        codeGenerators.put("prefix", new Template("prefix",
            """
            // start $prefix
            // ... prefix code ...
                #{:code}
            // end   $prefix
            """
        ));
        codeGenerators.put("foo", new Template("foo",
            """
            // start $foo
            {
                #{v1:store(type=int)} = #{v11:load(type=int)};
                #{v2:store(type=int)} = #{v12:load(type=int)};
                #{v3:store(type=int)} = #{v13:load(type=int)};
                #{v4:store(type=int)} = #{v14:load(type=int)};
                #{v5:store(type=int)} = #{v15:load(type=int)};
            }
            // end   $foo
            """
        ));
        codeGenerators.put("bar", new Template("bar",
            """
            // start $bar
            {
                ${fieldI} += 42;
                #{:dispatch(scope=class,call=new_field_in_class,name=$fieldI,final=false)}
                ${varI} += 42;
                #{:dispatch(scope=method,call=new_var_in_method,name=$varI,final=false)}
            }
            // end   $bar
            """
        ));

        // Selector for code blocks.
        SelectorCodeGenerator selectorForCode = new SelectorCodeGenerator("code_selector", "empty");
        selectorForCode.add("split",  100);
        selectorForCode.add("prefix", 100);
        selectorForCode.add("foo", 100);
        selectorForCode.add("bar", 100);
        codeGenerators.put("code", selectorForCode);

        return new CodeGeneratorLibrary(null, codeGenerators);
    }
}
