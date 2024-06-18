/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.javac.platform.PlatformDescription;
import com.sun.tools.javac.platform.PlatformProvider;

import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.constant.ClassDesc;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

abstract class ClassResolver implements AutoCloseable {

    static ClassResolver forScannedModules(List<ScannedModule> modules, Runtime.Version version) throws IOException {
        List<JarFile> loaded = new ArrayList<>();
        Map<ClassDesc, Info> classMap = new HashMap<>();
        for (ScannedModule m : modules) {
            JarFile jf = new JarFile(m.path().toFile(), false, ZipFile.OPEN_READ, version);
            loaded.add(jf);
            jf.versionedStream().filter(je -> je.getName().endsWith(".class")).forEach(je -> {
                try {
                    ClassModel model = ClassFile.of().parse(jf.getInputStream(je).readAllBytes());
                    ClassDesc desc =  model.thisClass().asSymbol();
                    classMap.put(desc, new Info(m.moduleName(), m.path(), model));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        return new ScannedModuleClassResolver(loaded, classMap);
    }

    static ClassResolver forSystemModules(Runtime.Version version) {
        String platformName = String.valueOf(version.feature());
        PlatformProvider platformProvider = ServiceLoader.load(PlatformProvider.class).findFirst().orElseThrow();
        PlatformDescription platform;
        try {
            platform = platformProvider.getPlatform(platformName, null);
        } catch (PlatformProvider.PlatformNotSupported e) {
            throw new JNativeScanFatalError("Release: " + platformName + " not supported", e);
        }
        JavaFileManager fm = platform.getFileManager();
        return new SystemModuleClassResolver(fm);
    }

    record Info(String moduleName, Path jarPath, ClassModel model) {}

    public abstract void forEach(BiConsumer<ClassDesc, ClassResolver.Info> action);
    public abstract Optional<ClassResolver.Info> lookup(ClassDesc desc);

    @Override
    public abstract void close() throws IOException;

    private static class ScannedModuleClassResolver extends ClassResolver {

        private final List<JarFile> jars;
        private final Map<ClassDesc, ClassResolver.Info> classMap;

        public ScannedModuleClassResolver(List<JarFile> jars, Map<ClassDesc, Info> classMap) {
            this.jars = jars;
            this.classMap = classMap;
        }

        public void forEach(BiConsumer<ClassDesc, ClassResolver.Info> action) {
            classMap.forEach(action);
        }

        public Optional<ClassResolver.Info> lookup(ClassDesc desc) {
            return Optional.ofNullable(classMap.get(desc));
        }

        @Override
        public void close() throws IOException {
            for (JarFile jarFile : jars) {
                jarFile.close();
            }
        }
    }

    private static class SystemModuleClassResolver extends ClassResolver {

        private final JavaFileManager platformFileManager;
        private final Map<String, String> packageToSystemModule;
        private final Map<ClassDesc, Info> cache = new HashMap<>();

        public SystemModuleClassResolver(JavaFileManager platformFileManager) {
            this.platformFileManager = platformFileManager;
            this.packageToSystemModule = packageToSystemModule();
        }

        private static Map<String, String> packageToSystemModule() {
            List<ModuleDescriptor> descriptors = ModuleFinder.ofSystem()
                    .findAll()
                    .stream()
                    .map(ModuleReference::descriptor)
                    .toList();
            Map<String, String> result = new HashMap<>();
            for (ModuleDescriptor descriptor : descriptors) {
                for (ModuleDescriptor.Exports export : descriptor.exports()) {
                    if (!export.isQualified()) {
                        result.put(export.source(), descriptor.name());
                    }
                }
            }
            return result;
        }

        @Override
        public void forEach(BiConsumer<ClassDesc, Info> action) {
            throw new UnsupportedOperationException("NYI");
        }

        @Override
        public Optional<Info> lookup(ClassDesc desc) {
            return Optional.ofNullable(cache.computeIfAbsent(desc, _ -> {
                String qualName = JNativeScanTask.qualName(desc);
                String moduleName = packageToSystemModule.get(desc.packageName());
                if (moduleName != null) {
                    try {
                        JavaFileManager.Location loc = platformFileManager.getLocationForModule(StandardLocation.SYSTEM_MODULES, moduleName);
                        JavaFileObject jfo = platformFileManager.getJavaFileForInput(loc, qualName, JavaFileObject.Kind.CLASS);
                        ClassModel model = ClassFile.of().parse(jfo.openInputStream().readAllBytes());
                        return new Info(moduleName, null, model);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                return null;
            }));
        }

        @Override
        public void close() throws IOException {
            platformFileManager.close();
        }
    }
}
