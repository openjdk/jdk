/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8072480 8203814
 * @summary Check the platform classpath contains the correct elements.
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.code
 *          jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.platform
 *          jdk.compiler/com.sun.tools.javac.processing
 *          jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.ToolBox PrintCTSymContent
 * @run main PrintCTSymContent
 */

import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import javax.lang.model.element.Element;
import javax.lang.model.element.ModuleElement;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.processing.PrintingProcessor.PrintingElementVisitor;
import java.util.Comparator;
import javax.lang.model.element.ModuleElement.ExportsDirective;
import javax.lang.model.element.QualifiedNameable;
import javax.lang.model.util.ElementFilter;

import toolbox.ToolBox;


/**
 * Dump content of ct.sym for specified releases to a specified directory.
 *
 * Usage: adjust the `@run main PrintCTSymContent` tag to include the starting and
 * ending versions, and a target directory where the API snapshot should be written:
 *
 *  @run main PrintCTSymContent <start> <end> <directory>
 *
 * and run the test.
 */
public class PrintCTSymContent {

    public static void main(String... args) throws Exception {
        if (args.length == 0) {
            return ; //pass vacuously
        }
        new PrintCTSymContent().generateCtSymOutput(args);
    }

    void generateCtSymOutput(String... args) throws Exception {
        int startRelease = Integer.parseInt(args[0]);
        int endRelease = Integer.parseInt(args[1]);
        String directory = args[2];

        for (int i = startRelease; i <= endRelease; i++) {
            try (Writer content = Files.newBufferedWriter(Paths.get(directory, "ct-sym-content-" + i + ".txt"))) {
                run(content, String.valueOf(i));
            }
        }
    }

    void run(Writer output, String version) throws Exception {
        List<String> options = Arrays.asList("--release", version, "-classpath", "", "--add-modules", "ALL-SYSTEM");
        List<ToolBox.JavaSource> files = List.of(new ToolBox.JavaSource("Test", ""));
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        JavacTaskImpl task = (JavacTaskImpl) compiler.getTask(null, null, null, options, null, files);

        task.analyze();

        PrintingElementVisitor target = new PrintingElementVisitor(output, task.getElements());
        Comparator<QualifiedNameable> sortByQualifiedName = (me1, me2) -> me1.getQualifiedName().toString().compareTo(me2.getQualifiedName().toString());
        List<? extends ModuleElement> allModules = task.getElements().getAllModuleElements().stream().sorted(sortByQualifiedName).toList();

        for (ModuleElement module : allModules) {
            if (module.isUnnamed()) {
                continue;
            }
            target.visit(module);
            for (ExportsDirective ed : ElementFilter.exportsIn(module.getDirectives())) {
                if (ed.getTargetModules() == null) {
                    for (Element c : ElementFilter.typesIn(ed.getPackage().getEnclosedElements()).stream().sorted(sortByQualifiedName).toList()) {
                        target.visit(c);
                    }
                }
            }
        }
    }

}
