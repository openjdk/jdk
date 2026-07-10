/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import jdk.jpackage.internal.model.JPackageException;
import jdk.jpackage.internal.model.LauncherJarStartupInfo;
import jdk.jpackage.internal.model.LauncherJarStartupInfoMixin;
import jdk.jpackage.internal.model.LauncherModularStartupInfo;
import jdk.jpackage.internal.model.LauncherModularStartupInfoMixin;
import jdk.jpackage.internal.model.LauncherStartupInfo;

final class LauncherStartupInfoBuilder {

    LauncherStartupInfo create() {
        if (moduleName != null) {
            return createModular();
        } else if (mainJar != null) {
            return createNonModular();
        } else {
            throw new JPackageException(I18N.format("ERR_NoEntryPoint"));
        }
    }

    LauncherStartupInfoBuilder inputDir(Path v) {
        inputDir = v;
        return this;
    }

    LauncherStartupInfoBuilder javaOptions(List<String> v) {
        if (v != null) {
            v.forEach(Objects::requireNonNull);
        }
        javaOptions = v;
        return this;
    }

    LauncherStartupInfoBuilder defaultParameters(List<String> v) {
        if (v != null) {
            v.forEach(Objects::requireNonNull);
        }
        defaultParameters = v;
        return this;
    }

    LauncherStartupInfoBuilder mainJar(Path v) {
        mainJar = v;
        return this;
    }

    LauncherStartupInfoBuilder mainClassName(String v) {
        mainClassName = v;
        return this;
    }

    LauncherStartupInfoBuilder predefinedRuntimeImage(Path v) {
        cookedRuntimePath = v;
        return this;
    }

    LauncherStartupInfoBuilder moduleName(String v) {
        if (v == null) {
            moduleName = null;
        } else {
            var slashIdx = v.indexOf('/');
            if (slashIdx < 0) {
                moduleName = v;
            } else {
                moduleName = v.substring(0, slashIdx);
                if (slashIdx < v.length() - 1) {
                    mainClassName(v.substring(slashIdx + 1));
                }
            }
        }
        return this;
    }

    LauncherStartupInfoBuilder modulePath(List<Path> v) {
        modulePath = v;
        return this;
    }

    private Optional<Path> inputDir() {
        return Optional.ofNullable(inputDir);
    }

    private Optional<String> mainClassName() {
        return Optional.ofNullable(mainClassName);
    }

    private Optional<Path> cookedRuntimePath() {
        return Optional.ofNullable(cookedRuntimePath);
    }

    private LauncherStartupInfo createLauncherStartupInfo(String mainClassName, List<Path> classpath) {
        Objects.requireNonNull(mainClassName);
        classpath.forEach(Objects::requireNonNull);
        return new LauncherStartupInfo.Stub(mainClassName,
                Optional.ofNullable(javaOptions).orElseGet(List::of),
                Optional.ofNullable(defaultParameters).orElseGet(List::of),
                classpath);
    }

    private static List<Path> createClasspath(Path inputDir, Set<Path> excludes) {
        excludes.forEach(Objects::requireNonNull);
        try (final var walk = Files.walk(inputDir)) {
            return walk.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".jar"))
                    .map(inputDir::relativize)
                    .filter(Predicate.not(excludes::contains))
                    .distinct()
                    .toList();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private LauncherModularStartupInfo createModular() {
        final var fullModulePath = getFullModulePath();

        // Try to find the module in the specified module path list.
        final var moduleInfo = JLinkRuntimeBuilder.createModuleFinder(fullModulePath).find(moduleName)
                .map(ModuleInfo::fromModuleReference).or(() -> {
                    // Failed to find the module in the specified module path list.
                    return cookedRuntimePath().flatMap(cookedRuntime -> {
                        // Lookup the module in the external runtime.
                        return ModuleInfo.fromCookedRuntime(moduleName, cookedRuntime);
                    });
                }).orElseThrow(() -> {
                    return I18N.buildConfigException("error.no-module-in-path", moduleName).create();
                });

        final var effectiveMainClassName = mainClassName().or(moduleInfo::mainClass).orElseThrow(() -> {
            return I18N.buildConfigException("ERR_NoMainClass").create();
        });

        // If module is located in the file system, exclude it from the classpath.
        final var classpath = inputDir().map(theInputDir -> {
            var classpathExcludes = moduleInfo.fileLocation().filter(moduleFile -> {
                return moduleFile.startsWith(theInputDir);
            }).map(theInputDir::relativize).map(Set::of).orElseGet(Set::of);
            return createClasspath(theInputDir, classpathExcludes);
        }).orElseGet(List::of);

        return LauncherModularStartupInfo.create(
                createLauncherStartupInfo(effectiveMainClassName, classpath),
                new LauncherModularStartupInfoMixin.Stub(moduleInfo.name(), moduleInfo.version()));
    }

    private List<Path> getFullModulePath() {
        return cookedRuntimePath().map(runtimeImage -> {
            return Stream.of(modulePath(), List.of(runtimeImage.resolve("lib"))).flatMap(List::stream).toList();
        }).orElse(modulePath());
    }

    private List<Path> modulePath() {
        return Optional.ofNullable(modulePath).orElseGet(List::of);
    }

    private LauncherJarStartupInfo createNonModular() {
        final var theInputDir = inputDir().orElseThrow();

        final var mainJarPath = theInputDir.resolve(mainJar);

        if (!Files.exists(mainJarPath)) {
            throw I18N.buildConfigException()
                    .message("error.main-jar-does-not-exist", mainJar)
                    .advice("error.main-jar-does-not-exist.advice")
                    .create();
        }

        final var effectiveMainClassName = mainClassName().or(() -> {
            try (final var jf = new JarFile(mainJarPath.toFile())) {
                return Optional.ofNullable(jf.getManifest()).map(Manifest::getMainAttributes).map(attrs -> {
                    return attrs.getValue(Attributes.Name.MAIN_CLASS);
                });
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }).orElseThrow(() -> {
            return I18N.buildConfigException()
                    .message("error.no-main-class-with-main-jar", mainJar)
                    .advice("error.no-main-class-with-main-jar.advice", mainJar)
                    .create();
        });

        return LauncherJarStartupInfo.create(
                createLauncherStartupInfo(effectiveMainClassName, createClasspath(theInputDir, Set.of(mainJar))),
                new LauncherJarStartupInfoMixin.Stub(mainJar, mainClassName().isEmpty()));
    }

    // Modular options
    private String moduleName;
    private List<Path> modulePath;

    // Non-modular options
    private Path mainJar;

    // Common options
    private Path inputDir;
    private String mainClassName;
    private List<String> javaOptions;
    private List<String> defaultParameters;
    private Path cookedRuntimePath;
}
