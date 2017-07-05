/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package build.tools.symbolgenerator;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.lang.model.element.ModuleElement.RequiresDirective;
import javax.lang.model.util.Elements;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.code.Symbol.ModuleSymbol;

/**
 * Print reflexive transitive closure of the given modules along their requires transitive edges.
 */
public class TransitiveDependencies {

    private static void help() {
        System.err.println("java TransitiveDependencies <module-source-path> <root-modules>");
    }

    public static void main(String... args) throws IOException {
        if (args.length < 1) {
            help();
            return ;
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        List<String> options = Arrays.asList("-source", "9",
                                             "-target", "9",
                                             "--system", "none",
                                             "--module-source-path", args[0],
                                             "--add-modules", Arrays.stream(args)
                                                                    .skip(1)
                                                                    .collect(Collectors.joining(",")));
        List<String> jlObjectList = Arrays.asList("java.lang.Object");
        JavacTaskImpl task = (JavacTaskImpl) compiler.getTask(null, null, d -> {}, options, jlObjectList, null);
        task.enter();
        Elements elements = task.getElements();
        List<String> todo = new LinkedList<>();
        Arrays.stream(args).skip(1).forEach(todo::add);
        Set<String> allModules = new HashSet<>();

        while (!todo.isEmpty()) {
            String current = todo.remove(0);

            if (!allModules.add(current))
                continue;

            ModuleSymbol mod = (ModuleSymbol) elements.getModuleElement(current);

            if (mod == null) {
                throw new IllegalStateException("Missing: " + current);
            }

             //use the internal structure to avoid unnecesarily completing the symbol using the UsesProvidesVisitor:
            for (RequiresDirective rd : mod.requires) {
                if (rd.isTransitive()) {
                    todo.add(rd.getDependency().getQualifiedName().toString());
                }
            }
        }

        allModules.add("java.base");
        allModules.add("jdk.unsupported");

        allModules.stream()
                  .sorted()
                  .forEach(System.out::println);
    }

}
