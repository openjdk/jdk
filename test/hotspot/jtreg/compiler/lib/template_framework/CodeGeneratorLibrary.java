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
                if (parent.find(name) != null) {
                    throw new TemplateFrameworkException("Code library already has a generator for name " + name);
                }
            }
        }
        this.library = new HashMap<String,CodeGenerator>(library);
    }

    /**
     * Recursively find CodeGenerator with given name in this library or parent library.
     */
    public CodeGenerator find(String name) {
        CodeGenerator codeGenerator = library.get(name);
        if (codeGenerator != null) {
            return codeGenerator;
        } else if (parent != null){
            return parent.find(name);
        } else {
            return null;
        }
    }

    public static CodeGeneratorLibrary standard() {
        HashMap<String,CodeGenerator> codeGenerators = new HashMap<String,CodeGenerator>();

        // Random Constants.
        codeGenerators.put("int_con", new ProgrammaticCodeGenerator(
            (Scope scope, Parameters parameters) -> {
                int v = RANDOM.nextInt();
                scope.stream.addCodeToLine(String.valueOf(v));
            }, 0));

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

        // Selector for code blocks.
        SelectorCodeGenerator selectorForCode = new SelectorCodeGenerator("empty");
        selectorForCode.add("split",  100);
        selectorForCode.add("prefix", 100);
        codeGenerators.put("code", selectorForCode);

        return new CodeGeneratorLibrary(null, codeGenerators);
    }
}
