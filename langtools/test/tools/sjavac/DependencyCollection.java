/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8056258 8048609
 * @summary Ensures that the DependencyCollector covers various cases.
 * @library /tools/lib
 * @build Wrapper ToolBox
 * @run main Wrapper DependencyCollection
 */

import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.code.Symbol.PackageSymbol;
import com.sun.tools.sjavac.comp.SmartFileManager;
import com.sun.tools.sjavac.comp.dependencies.DependencyCollector;

public class DependencyCollection {

    public static void main(String[] args) {
        Path src = Paths.get(ToolBox.testSrc, "test-input", "src");

        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fileManager = javac.getStandardFileManager(null, null, null)) {
            SmartFileManager smartFileManager = new SmartFileManager(fileManager);
            smartFileManager.setSymbolFileEnabled(false);
            Iterable<? extends JavaFileObject> fileObjects =
                    fileManager.getJavaFileObjectsFromFiles(Arrays.asList(src.resolve("pkg/Test.java").toFile()));
            JavacTaskImpl task = (JavacTaskImpl) javac.getTask(new PrintWriter(System.out),
                                                               smartFileManager,
                                                               null,
                                                               Arrays.asList("-d", "classes",
                                                                             "-sourcepath", src.toAbsolutePath().toString()),
                                                               null,
                                                               fileObjects);
            DependencyCollector depsCollector = new DependencyCollector();
            task.addTaskListener(depsCollector);
            task.doCall();

            // Find pkg symbol
            PackageSymbol pkg = findPkgSymbolWithName(depsCollector.getSourcePackages(), "pkg");
            Set<PackageSymbol> foundDependencies = depsCollector.getDependenciesForPkg(pkg);

            // Print dependencies
            System.out.println("Found dependencies:");
            foundDependencies.stream()
                             .sorted(Comparator.comparing(DependencyCollection::extractNumber))
                             .forEach(p -> System.out.println("    " + p));

            // Check result
            Set<Integer> found = foundDependencies.stream()
                                                  .map(DependencyCollection::extractNumber)
                                                  .collect(Collectors.toSet());
            found.remove(-1); // Dependencies with no number (java.lang etc)
            Set<Integer> expected = new HashSet<>();
            for (int i = 2; i <= 30; i++) {
                if (i == 15) continue;  // Case 15 correspond to the type of a throw-away return value.
                expected.add(i);
            }

            Set<Integer> missing = new HashSet<>(expected);
            missing.removeAll(found);
            if (missing.size() > 0) {
                System.out.println("Missing dependencies:");
                missing.forEach(i -> System.out.println("    Dependency " + i));
            }

            Set<Integer> unexpected = new HashSet<>(found);
            unexpected.removeAll(expected);
            if (unexpected.size() > 0) {
                System.out.println("Unexpected dependencies found:");
                unexpected.forEach(i -> System.out.println("    Dependency " + i));
            }

            if (missing.size() > 0 || unexpected.size() > 0)
                throw new AssertionError("Missing and/or unexpected dependencies found.");
        }
    }

    private static PackageSymbol findPkgSymbolWithName(Set<PackageSymbol> syms, String name) {
        for (PackageSymbol ps : syms)
            if (ps.fullname.toString().equals("pkg"))
                return ps;
        throw new AssertionError("Could not find package named \"pkg\".");
    }

    public static int extractNumber(PackageSymbol p) {
        Matcher m = Pattern.compile("\\d+").matcher(p.fullname.toString());
        if (!m.find())
            return -1;
        return Integer.parseInt(m.group());
    }
}
