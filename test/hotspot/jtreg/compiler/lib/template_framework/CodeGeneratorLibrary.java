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
        return new ProgrammaticCodeGenerator((Scope scope, Parameters parameters) -> {
            String type = parameters.get("type", " for generator call to load/store");
            String name = scope.sampleVariable(type, mutable);
            if (name == null) {
                throw new TemplateFrameworkException("Generator call to load/store cannot find variable of type: " + type);
            }
            scope.stream.addCodeToLine(String.valueOf(name));
        }, 0);
    }

    public static CodeGenerator factoryDispatch() {
        return new ProgrammaticCodeGenerator((Scope scope, Parameters parameters) -> {
            String scopeKind = parameters.get("scope", " for generator call to 'dispatch'");
            String generatorName = parameters.get("call", " for generator call to 'dispatch'");
            CodeGenerator generator = scope.library().find(generatorName, " for dispatch in " + scopeKind + " scope");

            System.out.println("Dispatch " + generatorName + " to " + scopeKind);

            switch(scopeKind) {
                case "class" -> {
                    ClassScope classScope = scope.classScope(" in dispatch for " + generatorName);
                    classScope.dispatch(scope, generator);
                    // TODO parameters from dispatch?
                }
                case "method" -> {
                    MethodScope methodScope = scope.methodScope(" in dispatch for " + generatorName);
                    methodScope.dispatch(scope, generator);
                    // TODO parameters from dispatch?
                }
                default -> {
                    throw new TemplateFrameworkException("Generator dispatch got: scope=" + scopeKind +
                                                         "but should be scope=class or scope=method");
                }
            }
        }, 0);
    }

    public static CodeGeneratorLibrary standard() {
        HashMap<String,CodeGenerator> codeGenerators = new HashMap<String,CodeGenerator>();

        // Random Constants.
        codeGenerators.put("int_con", new ProgrammaticCodeGenerator(
            (Scope scope, Parameters parameters) -> {
                int v = RANDOM.nextInt();
                scope.stream.addCodeToLine(String.valueOf(v));
            }, 0));

        // Variable load/store.
        codeGenerators.put("load",  factoryLoadStore(false));
        codeGenerators.put("store", factoryLoadStore(true));

        // Dispatch generator call to a ClassScope or MethodScope
        codeGenerators.put("dispatch", factoryDispatch());

        // ClassScope generators.
        codeGenerators.put("new_field_in_class", new Template(
            """
            // start $new_field_in_class
            public int ${fieldI:int} = #{:int_con};
            // end   $new_field_in_class
            """
        ));

        // MethodScope generators.
        codeGenerators.put("new_var_in_method", new Template(
            """
            // start $new_var_in_method
            int ${varI:int} = #{:int_con};
            // end   $new_var_in_method
            """
        ));

        // Code blocks.
        codeGenerators.put("empty", new Template(
            """
            // $empty
            """
        ));
        codeGenerators.put("split", new Template(
            """
            // start $split
                #{:code}
            // mid   $split
                #{:code}
            // end   $split
            """
        ));
        codeGenerators.put("prefix", new Template(
            """
            // start $prefix
            // ... prefix code ...
                #{:code}
            // end   $prefix
            """
        ));
        codeGenerators.put("foo", new Template(
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
        codeGenerators.put("bar", new Template(
            """
            // start $bar
            {
                #{:dispatch(scope=class,call=new_field_in_class)}
                #{:dispatch(scope=method,call=new_var_in_method)}
            }
            // end   $bar
            """
        ));

        // Selector for code blocks.
        SelectorCodeGenerator selectorForCode = new SelectorCodeGenerator("empty");
        selectorForCode.add("split",  100);
        selectorForCode.add("prefix", 100);
        selectorForCode.add("foo", 100);
        selectorForCode.add("bar", 100);
        codeGenerators.put("code", selectorForCode);

        return new CodeGeneratorLibrary(null, codeGenerators);
    }
}
