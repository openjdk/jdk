/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.tools.jnativescan;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.classfile.ClassModel;
import java.lang.constant.ClassDesc;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.module.ResolvedModule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

class JNativeScanTask {

    private final PrintWriter out;
    private final PrintWriter err;
    private final List<Path> classPaths;
    private final List<Path> modulePaths;
    private final List<String> cmdRootModules;
    private final Runtime.Version version;
    private final Action action;

    public JNativeScanTask(PrintWriter out, PrintWriter err, List<Path> classPaths, List<Path> modulePaths,
                           List<String> cmdRootModules, Runtime.Version version, Action action) {
        this.out = out;
        this.err = err;
        this.classPaths = classPaths;
        this.modulePaths = modulePaths;
        this.version = version;
        this.action = action;
        this.cmdRootModules = cmdRootModules;
    }

    public void run() throws JNativeScanFatalError {
        List<ClassFileSource> toScan = new ArrayList<>(findAllClassPathJars());

        ModuleFinder moduleFinder = ModuleFinder.of(modulePaths.toArray(Path[]::new));
        List<String> rootModules = cmdRootModules;
        if (rootModules.contains("ALL-MODULE-PATH")) {
            rootModules = allModuleNames(moduleFinder);
        }
        Configuration config = systemConfiguration().resolveAndBind(ModuleFinder.of(), moduleFinder, rootModules);
        for (ResolvedModule m : config.modules()) {
            toScan.add(new ClassFileSource.Module(m.reference()));
        }

        Set<String> errors = new LinkedHashSet<>();
        Diagnostics diagnostics = (context, error) ->
                errors.add("Error while processing method: " + context + ": " + error.getMessage());
        SortedMap<ClassFileSource, SortedMap<ClassDesc, List<RestrictedUse>>> allRestrictedMethods
                = new TreeMap<>(Comparator.comparing(ClassFileSource::path));
        try(SystemClassResolver systemClassResolver = SystemClassResolver.forRuntimeVersion(version)) {
            NativeMethodFinder finder = NativeMethodFinder.create(diagnostics, systemClassResolver);

            for (ClassFileSource source : toScan) {
                SortedMap<ClassDesc, List<RestrictedUse>> perClass
                        = new TreeMap<>(Comparator.comparing(JNativeScanTask::qualName));
                try (Stream<ClassModel> stream = source.classModels()) {
                    stream.forEach(classModel -> {
                        List<RestrictedUse> restrictedUses = finder.find(classModel);
                        if (!restrictedUses.isEmpty()) {
                            perClass.put(classModel.thisClass().asSymbol(), restrictedUses);
                        }
                    });
                }
                if (!perClass.isEmpty()) {
                    allRestrictedMethods.put(source, perClass);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        switch (action) {
            case PRINT -> printNativeAccess(allRestrictedMethods);
            case DUMP_ALL -> dumpAll(allRestrictedMethods, errors);
        }
    }

    private List<ClassFileSource> findAllClassPathJars() throws JNativeScanFatalError {
        List<ClassFileSource> result = new ArrayList<>();
        for (Path path : classPaths) {
            if (isJarFile(path)) {
                Deque<Path> jarsToScan  = new ArrayDeque<>();
                jarsToScan.offer(path);

                // recursively look for all class path jars, starting at the root jars
                // in this.classPaths, and recursively following all Class-Path manifest
                // attributes
                while (!jarsToScan.isEmpty()) {
                    Path jar = jarsToScan.poll();
                    String[] classPathAttribute = classPathAttribute(jar);
                    Path parentDir = jar.getParent();
                    for (String classPathEntry : classPathAttribute) {
                        Path otherJar = parentDir != null
                                ? parentDir.resolve(classPathEntry)
                                : Path.of(classPathEntry);
                        if (Files.exists(otherJar)) {
                            // Class-Path attribute specifies that jars that
                            // are not found are simply ignored. Do the same here
                            jarsToScan.offer(otherJar);
                        }
                    }
                    result.add(new ClassFileSource.ClassPathJar(jar, version));
                }
            } else if (Files.isDirectory(path)) {
                result.add(new ClassFileSource.ClassPathDirectory(path));
            } else {
                throw new JNativeScanFatalError(
                    "Path does not appear to be a jar file, or directory containing classes: " + path);
            }
        }
        return result;
    }

    private String[] classPathAttribute(Path jar) {
        try (JarFile jf = new JarFile(jar.toFile(), false, ZipFile.OPEN_READ, version)) {
           Manifest manifest = jf.getManifest();
           if (manifest != null) {
               String attrib = manifest.getMainAttributes().getValue("Class-Path");
               if (attrib != null) {
                   return attrib.split("\\s+");
               }
           }
           return new String[0];
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Configuration systemConfiguration() {
        ModuleFinder systemFinder = ModuleFinder.ofSystem();
        Configuration system = Configuration.resolve(systemFinder, List.of(Configuration.empty()), ModuleFinder.of(),
                allModuleNames(systemFinder)); // resolve all of them
        return system;
    }

    private List<String> allModuleNames(ModuleFinder finder) {
        return finder.findAll().stream().map(mr -> mr.descriptor().name()).toList();
    }

    private void printNativeAccess(SortedMap<ClassFileSource, SortedMap<ClassDesc, List<RestrictedUse>>> allRestrictedMethods) {
        String nativeAccess = allRestrictedMethods.keySet().stream()
                .map(ClassFileSource::moduleName)
                .distinct()
                .collect(Collectors.joining(","));
        out.println(nativeAccess);
    }

    private void dumpAll(SortedMap<ClassFileSource, SortedMap<ClassDesc, List<RestrictedUse>>> allRestrictedMethods, Set<String> errors) {
        if (allRestrictedMethods.isEmpty()) {
            out.println("  <no restricted methods>");
        } else {
            allRestrictedMethods.forEach((module, perClass) -> {
                out.println(module.path() + " (" + module.moduleName() + "):");
                perClass.forEach((classDesc, restrictedUses) -> {
                    out.println("  " + qualName(classDesc) + ":");
                    restrictedUses.forEach(use -> {
                        switch (use) {
                            case RestrictedUse.NativeMethodDecl(MethodRef nmd) ->
                                out.println("    " + nmd + " is a native method declaration");
                            case RestrictedUse.RestrictedMethodRefs(MethodRef referent, Set<MethodRef> referees) -> {
                                out.println("    " + referent + " references restricted methods:");
                                referees.forEach(referee -> out.println("      " + referee));
                            }
                        }
                    });
                });
            });
        }
        if (!errors.isEmpty()) {
            err.println("Error(s) while processing classes:");
            errors.forEach(error -> err.println("  " + error));
        }
    }

    private static boolean isJarFile(Path path) throws JNativeScanFatalError {
        return Files.exists(path) && Files.isRegularFile(path) && path.toString().endsWith(".jar");
    }

    public enum Action {
        DUMP_ALL,
        PRINT
    }

    public static String qualName(ClassDesc desc) {
        String packagePrefix = desc.packageName().isEmpty() ? "" : desc.packageName() + ".";
        return packagePrefix + desc.displayName();
    }

    interface Diagnostics {
        void error(MethodRef context, JNativeScanFatalError error);
    }
}
