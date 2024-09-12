/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.launcher;

import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.code.Preview;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.resources.LauncherProperties.Errors;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Context.Factory;

import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An object to encapsulate the set of in-memory classes, such that
 * they can be written by a file manager and subsequently used by
 * a class loader.
 *
 * <p><strong>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</strong></p>
 */
final class MemoryContext {
    private final PrintWriter out;
    private final ProgramDescriptor descriptor;

    private final RelevantJavacOptions options;

    private final JavacTool compiler;
    private final JavacFileManager standardFileManager;
    private final JavaFileManager memoryFileManager;

    private final Map<String, byte[]> inMemoryClasses = new HashMap<>();

    MemoryContext(PrintWriter out, ProgramDescriptor descriptor, RelevantJavacOptions options) throws Fault {
        this.out = out;
        this.descriptor = descriptor;
        this.options = options;

        this.compiler = JavacTool.create();
        this.standardFileManager = compiler.getStandardFileManager(null, null, null);
        try {
            List<File> searchPath = descriptor.fileObject().isFirstLineIgnored() ? List.of() : List.of(descriptor.sourceRootPath().toFile());
            standardFileManager.setLocation(StandardLocation.SOURCE_PATH, searchPath);
        } catch (IOException e) {
            throw new Error("unexpected exception from file manager", e);
        }
        this.memoryFileManager = new MemoryFileManager(inMemoryClasses, standardFileManager);
    }

    ProgramDescriptor getProgramDescriptor() {
        return descriptor;
    }

    String getSourceFileAsString() {
        return descriptor.fileObject().getFile().toAbsolutePath().toString();
    }

    Set<String> getNamesOfCompiledClasses() {
        return Set.copyOf(inMemoryClasses.keySet());
    }

    /**
     * Compiles a source file, placing the class files in a map in memory.
     * Any messages generated during compilation will be written to the stream
     * provided when this object was created.
     *
     * @throws Fault if any compilation errors occur, or if no class was found
     */
    void compileProgram() throws Fault {
        var units = new ArrayList<JavaFileObject>();
        units.add(descriptor.fileObject());
        if (descriptor.isModular()) {
            var root = descriptor.sourceRootPath();
            units.add(standardFileManager.getJavaFileObject(root.resolve("module-info.java")));
        }
        var opts = options.forProgramCompilation();
        var context = new Context();
        MemoryPreview.registerInstance(context);
        var task = compiler.getTask(out, memoryFileManager, null, opts, null, units, context);
        var ok = task.call();
        if (!ok) {
            throw new Fault(Errors.CompilationFailed);
        }
    }

    /**
     * Determines a source file from the given class name and compiles it.
     * Any messages generated during compilation will be written to the stream
     * provided when this object was created.
     * <p>
     * This method is passed a reference to an instance of {@link MemoryClassLoader},
     * that uses it to compile a source file on demand.
     *
     * @param name the name of the class to be compiled.
     * @return the byte code of the compiled class or {@code null}
     *         if no source file was found for the given name
     */
    byte[] compileJavaFileByName(String name) {
        // Initially, determine existing directory from class name.
        // [pack$age . ] na$me [ $ enclo$ed [$ dee$per] ]
        var lastDot = name.lastIndexOf(".");
        var packageName = lastDot == -1 ? "" : name.substring(0, lastDot);
        var packagePath = descriptor.sourceRootPath().resolve(packageName.replace('.', '/'));
        // Trivial case: no matching directory exists
        if (!Files.isDirectory(packagePath)) return null;

        // Determine source file from class name.
        var candidate = name.substring(lastDot + 1, name.length()); // "na$me$enclo$ed$dee$per"
        // For each `$` in the name try to find the first matching compilation unit.
        while (candidate.contains("$")) {
            if (Files.exists(packagePath.resolve(candidate + ".java"))) break;
            candidate = candidate.substring(0, candidate.lastIndexOf("$"));
        }
        var file = packagePath.resolve(candidate + ".java");

        // Trivial case: no matching source file exists
        if (!Files.exists(file)) return null;

        // Compile source file (unit) with similar options as the program.
        var opts = options.forSubsequentCompilations();
        var unit = standardFileManager.getJavaFileObject(file);
        var task = compiler.getTask(out, memoryFileManager, null, opts, null, List.of(unit));

        var ok = task.call();
        if (!ok) {
            var fault = new Fault(Errors.CompilationFailed);
            // Don't throw fault - fail fast!
            out.println(fault.getMessage());
            System.exit(2);
        }

        // The memory file manager stored bytes in the context map, indexed by the class names.
        return inMemoryClasses.get(name);
    }

    /**
     * Create a new class load for the main entry-point class.
     *
     * @param parent the class loader to be used as the parent loader
     * @param mainClassName the fully-qualified name of the application class to load
     * @return class loader object able to find and load the desired class
     * @throws Fault if a modular application class is in the unnamed package
     */
    ClassLoader newClassLoaderFor(ClassLoader parent, String mainClassName) throws Fault {
        var moduleInfoBytes = inMemoryClasses.get("module-info");
        if (moduleInfoBytes == null) {
            // Trivial case: no compiled module descriptor available, no extra module layer required
            return new MemoryClassLoader(inMemoryClasses, parent, null, descriptor, this::compileJavaFileByName);
        }

        // Ensure main class resides in a named package.
        var lastDotInMainClassName = mainClassName.lastIndexOf('.');
        if (lastDotInMainClassName == -1) {
            throw new Fault(Errors.UnnamedPkgNotAllowedNamedModules);
        }

        var bootLayer = ModuleLayer.boot();
        var parentLayer = bootLayer;
        var parentLoader = parent;

        // Optionally create module layer for all modules on the module path.
        var modulePathFinder = createModuleFinderFromModulePath();
        var modulePathModules = modulePathFinder.findAll().stream().map(ModuleReference::descriptor).map(ModuleDescriptor::name).toList();
        if (!modulePathModules.isEmpty()) {
            var modulePathConfiguration = bootLayer.configuration().resolveAndBind(modulePathFinder, ModuleFinder.of(), Set.copyOf(modulePathModules));
            var modulePathLayer = ModuleLayer.defineModulesWithOneLoader(modulePathConfiguration, List.of(bootLayer), parent).layer();
            parentLayer = modulePathLayer;
            parentLoader = modulePathLayer.findLoader(modulePathModules.getFirst());
        }

        // Create in-memory module layer for the modular application.
        var applicationModule = ModuleDescriptor.read(ByteBuffer.wrap(moduleInfoBytes), descriptor::computePackageNames);
        var memoryFinder = new MemoryModuleFinder(inMemoryClasses, applicationModule, descriptor);
        var memoryConfig = parentLayer.configuration().resolveAndBind(memoryFinder, ModuleFinder.of(), Set.of(applicationModule.name()));
        var memoryClassLoader = new MemoryClassLoader(inMemoryClasses, parentLoader, applicationModule, descriptor, this::compileJavaFileByName);
        var memoryController = ModuleLayer.defineModules(memoryConfig, List.of(parentLayer), __ -> memoryClassLoader);
        var memoryLayer = memoryController.layer();

        // Make application class accessible from the calling (unnamed) module, that loaded this class.
        var module = memoryLayer.findModule(applicationModule.name()).orElseThrow();
        var mainClassNamePackageName = mainClassName.substring(0, lastDotInMainClassName);
        memoryController.addOpens(module, mainClassNamePackageName, getClass().getModule());

        return memoryLayer.findLoader(applicationModule.name());
    }

    private static ModuleFinder createModuleFinderFromModulePath() {
        var elements = System.getProperty("jdk.module.path");
        if (elements == null) {
            return ModuleFinder.of();
        }
        var paths = Arrays.stream(elements.split(File.pathSeparator)).map(Path::of);
        return ModuleFinder.of(paths.toArray(Path[]::new));
    }

    static class MemoryPreview extends Preview {
        static void registerInstance(Context context) {
            context.put(previewKey, (Factory<Preview>)MemoryPreview::new);
        }

        MemoryPreview(Context context) {
            super(context);
        }

        @Override
        public void reportDeferredDiagnostics() {
            // suppress diagnostics like "Note: Recompile with -Xlint:preview for details."
        }
    }
}
