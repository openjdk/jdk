/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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



package jdk.tools.jaotc;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jdk.tools.jaotc.collect.ClassSearch;
import jdk.tools.jaotc.collect.FileSupport;
import jdk.tools.jaotc.collect.classname.ClassNameSourceProvider;
import jdk.tools.jaotc.collect.directory.DirectorySourceProvider;
import jdk.tools.jaotc.collect.jar.JarSourceProvider;
import jdk.tools.jaotc.collect.module.ModuleSourceProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

final class Collector {

    private final Main main;

    Collector(Main main) {
        this.main = main;
    }

    Set<Class<?>> collectClassesToCompile() {
        Set<Class<?>> classesToCompile = new HashSet<>();
        FileSupport fileSupport = new FileSupport();
        ClassSearch lookup = new ClassSearch();
        lookup.addProvider(new ModuleSourceProvider());
        lookup.addProvider(new ClassNameSourceProvider(fileSupport));
        lookup.addProvider(new JarSourceProvider());
        lookup.addProvider(new DirectorySourceProvider(fileSupport));

        List<LoadedClass> foundClasses = null;
        try {
            foundClasses = lookup.search(main.options.files, main.options.searchPath, this::handleLoadingError);
        } catch (InternalError e) {
            main.printer.reportError(e);
            return null;
        }

        for (LoadedClass loadedClass : foundClasses) {
            classesToCompile.add(loadedClass.getLoadedClass());
        }
        return classesToCompile;
    }

    private void addMethods(AOTCompiledClass aotClass, ResolvedJavaMethod[] methods, CompilationSpec compilationRestrictions) {
        for (ResolvedJavaMethod m : methods) {
            addMethod(aotClass, m, compilationRestrictions);
        }
    }

    private void addMethod(AOTCompiledClass aotClass, ResolvedJavaMethod method, CompilationSpec compilationRestrictions) {
        // Don't compile native or abstract methods.
        if (!method.hasBytecodes()) {
            return;
        }
        if (!compilationRestrictions.shouldCompileMethod(method)) {
            return;
        }
        if (!main.filters.shouldCompileMethod(method)) {
            return;
        }

        aotClass.addMethod(method);
        main.printer.printlnVerbose("  added " + method.getName() + method.getSignature().toMethodDescriptor());
    }

    /**
     * Collect all method we should compile.
     *
     * @return array list of AOT classes which have compiled methods.
     */
    List<AOTCompiledClass> collectMethodsToCompile(Set<Class<?>> classesToCompile, MetaAccessProvider metaAccess) {
        int total = 0;
        int count = 0;
        List<AOTCompiledClass> classes = new ArrayList<>();
        CompilationSpec compilationRestrictions = collectSpecifiedMethods();

        for (Class<?> c : classesToCompile) {
            ResolvedJavaType resolvedJavaType = metaAccess.lookupJavaType(c);
            if (main.filters.shouldCompileAnyMethodInClass(resolvedJavaType)) {
                AOTCompiledClass aotClass = new AOTCompiledClass(resolvedJavaType);
                main.printer.printlnVerbose(" Scanning " + c.getName());

                // Constructors
                try {
                    ResolvedJavaMethod[] ctors = resolvedJavaType.getDeclaredConstructors();
                    addMethods(aotClass, ctors, compilationRestrictions);
                    total += ctors.length;
                } catch (Throwable e) {
                    handleLoadingError(c.getName(), e);
                }

                // Methods
                try {
                    ResolvedJavaMethod[] methods = resolvedJavaType.getDeclaredMethods();
                    addMethods(aotClass, methods, compilationRestrictions);
                    total += methods.length;
                } catch (Throwable e) {
                    handleLoadingError(c.getName(), e);
                }

                // Class initializer
                try {
                    ResolvedJavaMethod clinit = resolvedJavaType.getClassInitializer();
                    if (clinit != null) {
                        addMethod(aotClass, clinit, compilationRestrictions);
                        total++;
                    }
                } catch (Throwable e) {
                    handleLoadingError(c.getName(), e);
                }

                // Found any methods to compile? Add the class.
                if (aotClass.hasMethods()) {
                    classes.add(aotClass);
                    count += aotClass.getMethodCount();
                }
            }
        }
        main.printer.printInfo(total + " methods total, " + count + " methods to compile");
        return classes;
    }

    /**
     * If a file with compilation limitations is specified using flag --compile-commands, read the
     * file's contents and collect the restrictions.
     */
    private CompilationSpec collectSpecifiedMethods() {
        CompilationSpec compilationRestrictions = new CompilationSpec();
        String methodListFileName = main.options.methodList;

        if (methodListFileName != null && !methodListFileName.equals("")) {
            try {
                FileReader methListFile = new FileReader(methodListFileName);
                BufferedReader readBuf = new BufferedReader(methListFile);
                String line = null;
                while ((line = readBuf.readLine()) != null) {
                    String trimmedLine = line.trim();
                    if (!trimmedLine.startsWith("#")) {
                        String[] components = trimmedLine.split(" ");
                        if (components.length == 2) {
                            String directive = components[0];
                            String pattern = components[1];
                            switch (directive) {
                                case "compileOnly":
                                    compilationRestrictions.addCompileOnlyPattern(pattern);
                                    break;
                                case "exclude":
                                    compilationRestrictions.addExcludePattern(pattern);
                                    break;
                                default:
                                    System.out.println("Unrecognized command " + directive + ". Ignoring\n\t" + line + "\n encountered in " + methodListFileName);
                            }
                        } else {
                            if (!trimmedLine.equals("")) {
                                System.out.println("Ignoring malformed line:\n\t " + line + "\n");
                            }
                        }
                    }
                }
                readBuf.close();
            } catch (FileNotFoundException e) {
                throw new InternalError("Unable to open method list file: " + methodListFileName, e);
            } catch (IOException e) {
                throw new InternalError("Unable to read method list file: " + methodListFileName, e);
            }
        }

        return compilationRestrictions;
    }

    private void handleLoadingError(String name, Throwable t) {
        if (main.options.ignoreClassLoadingErrors) {
            main.printer.printError(name + ": " + t);
        } else {
            throw new InternalError(t);
        }
    }
}
