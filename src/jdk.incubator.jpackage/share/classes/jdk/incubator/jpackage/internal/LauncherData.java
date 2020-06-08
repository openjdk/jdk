/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.incubator.jpackage.internal;

import java.io.File;
import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.*;
import java.util.function.Supplier;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Extracts data needed to run application from parameters.
 */
final class LauncherData {
    boolean isModular() {
        return moduleDescriptor != null;
    }

    String qualifiedClassName() {
        return qualifiedClassName;
    }

    String packageName() {
        int sepIdx = qualifiedClassName.lastIndexOf('.');
        if (sepIdx < 0) {
            return "";
        }
        return qualifiedClassName.substring(sepIdx + 1);
    }

    String moduleName() {
        verifyIsModular(true);
        return moduleDescriptor.name();
    }

    List<Path> modulePath() {
        verifyIsModular(true);
        return modulePath;
    }

    Path mainJarName() {
        verifyIsModular(false);
        return mainJarName;
    }

    List<Path> classPath() {
        return classPath;
    }

    String getAppVersion() {
        if (isModular()) {
            ModuleDescriptor.Version ver = moduleDescriptor.version().orElse(null);
            if (ver != null) {
                return ver.toString();
            }
            return moduleDescriptor.rawVersion().orElse(null);
        }

        return null;
    }

    private LauncherData() {
    }

    private void verifyIsModular(boolean isModular) {
        if ((moduleDescriptor != null) != isModular) {
            throw new IllegalStateException();
        }
    }

    static LauncherData create(Map<String, ? super Object> params) throws
            ConfigException, IOException {

        final String mainModule = getMainModule(params);
        if (mainModule == null) {
            return createNonModular(params);
        }

        LauncherData launcherData = new LauncherData();

        final int sepIdx = mainModule.indexOf("/");
        final String moduleName;
        if (sepIdx > 0) {
            launcherData.qualifiedClassName = mainModule.substring(sepIdx + 1);
            moduleName = mainModule.substring(0, sepIdx);
        } else {
            moduleName = mainModule;
        }
        launcherData.modulePath = getModulePath(params);

        launcherData.moduleDescriptor = JLinkBundlerHelper.createModuleFinder(
                launcherData.modulePath).find(moduleName).orElseThrow(
                () -> new ConfigException(MessageFormat.format(I18N.getString(
                        "error.no-module-in-path"), moduleName), null)).descriptor();

        if (launcherData.qualifiedClassName == null) {
            launcherData.qualifiedClassName = launcherData.moduleDescriptor.mainClass().orElseThrow(
                    () -> new ConfigException(I18N.getString("ERR_NoMainClass"),
                            null));
        }

        launcherData.initClasspath(params);
        return launcherData;
    }

    private static LauncherData createNonModular(
            Map<String, ? super Object> params) throws ConfigException, IOException {
        LauncherData launcherData = new LauncherData();

        launcherData.qualifiedClassName = getMainClass(params);

        launcherData.mainJarName = getMainJarName(params);
        if (launcherData.mainJarName == null && launcherData.qualifiedClassName
                == null) {
            throw new ConfigException(I18N.getString("error.no-main-jar-parameter"),
                    null);
        }

        Path mainJarDir = StandardBundlerParam.SOURCE_DIR.fetchFrom(params);
        if (mainJarDir == null && launcherData.qualifiedClassName == null) {
            throw new ConfigException(I18N.getString("error.no-input-parameter"),
                    null);
        }

        final Path mainJarPath;
        if (launcherData.mainJarName != null && mainJarDir != null) {
            mainJarPath = mainJarDir.resolve(launcherData.mainJarName);
            if (!Files.exists(mainJarPath)) {
                throw new ConfigException(MessageFormat.format(I18N.getString(
                        "error.main-jar-does-not-exist"),
                        launcherData.mainJarName), I18N.getString(
                        "error.main-jar-does-not-exist.advice"));
            }
        } else {
            mainJarPath = null;
        }

        if (launcherData.qualifiedClassName == null) {
            if (mainJarPath == null) {
                throw new ConfigException(I18N.getString("error.no-main-class"),
                        I18N.getString("error.no-main-class.advice"));
            }

            try (JarFile jf = new JarFile(mainJarPath.toFile())) {
                Manifest m = jf.getManifest();
                Attributes attrs = (m != null) ? m.getMainAttributes() : null;
                if (attrs != null) {
                    launcherData.qualifiedClassName = attrs.getValue(
                            Attributes.Name.MAIN_CLASS);
                }
            }
        }

        if (launcherData.qualifiedClassName == null) {
            throw new ConfigException(MessageFormat.format(I18N.getString(
                    "error.no-main-class-with-main-jar"),
                    launcherData.mainJarName), MessageFormat.format(
                            I18N.getString(
                                    "error.no-main-class-with-main-jar.advice"),
                            launcherData.mainJarName));
        }

        launcherData.initClasspath(params);
        return launcherData;
    }

    private void initClasspath(Map<String, ? super Object> params)
            throws IOException {
        Path inputDir = StandardBundlerParam.SOURCE_DIR.fetchFrom(params);
        if (inputDir == null) {
            classPath = Collections.emptyList();
        } else {
            try (Stream<Path> walk = Files.walk(inputDir, 1)) {
                Set<Path> jars = walk.filter(Files::isRegularFile)
                        .filter(file -> file.toString().endsWith(".jar"))
                        .map(Path::getFileName)
                        .collect(Collectors.toSet());
                jars.remove(mainJarName);
                classPath = jars.stream().sorted().collect(Collectors.toList());
            }
        }
    }

    private static String getMainClass(Map<String, ? super Object> params) {
       return getStringParam(params, Arguments.CLIOptions.APPCLASS.getId());
    }

    private static Path getMainJarName(Map<String, ? super Object> params)
            throws ConfigException {
       return getPathParam(params, Arguments.CLIOptions.MAIN_JAR.getId());
    }

    private static String getMainModule(Map<String, ? super Object> params) {
       return getStringParam(params, Arguments.CLIOptions.MODULE.getId());
    }

    private static String getStringParam(Map<String, ? super Object> params,
            String paramName) {
        Optional<Object> value = Optional.ofNullable(params.get(paramName));
        if (value.isPresent()) {
            return value.get().toString();
        }
        return null;
    }

    private static <T> T getPathParam(Map<String, ? super Object> params,
            String paramName, Supplier<T> func) throws ConfigException {
        try {
            return func.get();
        } catch (InvalidPathException ex) {
            throw new ConfigException(MessageFormat.format(I18N.getString(
                    "error.not-path-parameter"), paramName,
                    ex.getLocalizedMessage()), null, ex);
        }
    }

    private static Path getPathParam(Map<String, ? super Object> params,
            String paramName) throws ConfigException {
        return getPathParam(params, paramName, () -> {
            String value = getStringParam(params, paramName);
            Path result = null;
            if (value != null) {
                result = Path.of(value);
            }
            return result;
        });
    }

    private static List<Path> getModulePath(Map<String, ? super Object> params)
            throws ConfigException {
        return getPathListParameter(Arguments.CLIOptions.MODULE_PATH.getId(),
                params);
    }

    private static List<Path> getPathListParameter(String paramName,
            Map<String, ? super Object> params) throws ConfigException {
        return getPathParam(params, paramName, () -> {
            String value = params.getOrDefault(paramName, "").toString();
        return List.of(value.split(File.pathSeparator)).stream()
                .map(Path::of)
                .collect(Collectors.toUnmodifiableList());
        });
    }

    private String qualifiedClassName;
    private Path mainJarName;
    private List<Path> classPath;
    private List<Path> modulePath;
    private ModuleDescriptor moduleDescriptor;
}
