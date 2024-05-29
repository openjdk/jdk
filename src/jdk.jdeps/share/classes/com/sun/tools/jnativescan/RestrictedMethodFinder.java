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
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.constantpool.InterfaceMethodRefEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.MethodRefEntry;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

class RestrictedMethodFinder {

    // ct.sym uses this fake name for the restricted annotation instead
    // see make/langtools/src/classes/build/tools/symbolgenerator/CreateSymbols.java
    private static final String RESTRICTED_NAME = "Ljdk/internal/javac/Restricted+Annotation;";
    private static final List<String> RESTRICTED_MODULES = List.of("java.base");

    private final Map<MethodRef, Boolean> CACHE = new HashMap<>();
    private final Runtime.Version version;
    private final JavaFileManager platformFileManager;

    private RestrictedMethodFinder(Runtime.Version version, JavaFileManager platformFileManager) {
        this.version = version;
        this.platformFileManager = platformFileManager;
    }

    public static RestrictedMethodFinder create(Runtime.Version version) throws JNativeScanFatalError {
        String platformName = String.valueOf(version.feature());
        PlatformProvider platformProvider = ServiceLoader.load(PlatformProvider.class).findFirst().orElseThrow();
        PlatformDescription platform;
        try {
            platform = platformProvider.getPlatform(platformName, null);
        } catch (PlatformProvider.PlatformNotSupported e) {
            throw new JNativeScanFatalError("Release: " + platformName + " not supported", e);
        }
        JavaFileManager fm = platform.getFileManager();
        return new RestrictedMethodFinder(version, fm);
    }

    public Map<ClassDesc, List<RestrictedUse>> findRestrictedMethodReferences(Path jar) {
        Map<ClassDesc, List<RestrictedUse>> restrictedMethods = new HashMap<>();
        forEachClassFile(jar, model -> {
            List<RestrictedUse> perClass = new ArrayList<>();
            model.methods().forEach(method -> {
                if (method.flags().has(AccessFlag.NATIVE)) {
                    perClass.add(new RestrictedUse.NativeMethodDecl(MethodRef.ofModel(method)));
                } else {
                    Set<MethodRef> perMethod = new HashSet<>();
                    method.code()
                        .ifPresent(code -> {
                            code.forEach(e -> {
                                switch (e) {
                                    case InvokeInstruction invoke -> {
                                        if (isRestrictedMethod(invoke.method())) {
                                            perMethod.add(MethodRef.ofMethodRefEntry(invoke.method()));
                                        }
                                    }
                                    default -> {
                                    }
                                }
                            });
                        });
                    if (!perMethod.isEmpty()) {
                        perClass.add(new RestrictedUse.RestrictedMethodRefs(MethodRef.ofModel(method), Set.copyOf(perMethod)));
                    }
                }
            });
            if (!perClass.isEmpty()) {
                restrictedMethods.put(model.thisClass().asSymbol(), perClass);
            }
        });
        return restrictedMethods;
    }

    private boolean isRestrictedMethod(MemberRefEntry method) {
        return switch (method) {
            case MethodRefEntry mre ->
                    isRestrictedMethod(mre.owner().asSymbol(), mre.name().stringValue(), mre.typeSymbol());
            case InterfaceMethodRefEntry mre ->
                    isRestrictedMethod(mre.owner().asSymbol(), mre.name().stringValue(), mre.typeSymbol());
            default -> throw new IllegalStateException("Unexpected type: " + method);
        };
    }

    private JavaFileObject findFileForSystemClass(String qualName) {
        for (String moduleName : RESTRICTED_MODULES) {
            try {
                JavaFileManager.Location loc = platformFileManager.getLocationForModule(StandardLocation.SYSTEM_MODULES, moduleName);
                JavaFileObject jfo = platformFileManager.getJavaFileForInput(loc, qualName, JavaFileObject.Kind.CLASS);
                if (jfo != null) {
                    return jfo;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return null; // not found
    }

    private boolean isRestrictedMethod(ClassDesc owner, String name, MethodTypeDesc type) {
        return CACHE.computeIfAbsent(new MethodRef(owner, name, type), methodRef -> {
            String qualName = methodRef.owner().packageName() + '.' + methodRef.owner().displayName();
            JavaFileObject jfo = findFileForSystemClass(qualName);
            if (jfo == null) {
                return false;
            }

            ClassModel classModel;
            try {
                classModel = ClassFile.of().parse(jfo.openInputStream().readAllBytes());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            MethodModel method = findMethod(classModel, methodRef.name(), methodRef.type());
            return hasRestrictedAnnotation(method);
        });
    }

    private static boolean hasRestrictedAnnotation(MethodModel method) {
        return method.findAttribute(Attributes.runtimeVisibleAnnotations())
                .map(rva -> rva.annotations().stream().anyMatch(ann ->
                        ann.className().stringValue().equals(RESTRICTED_NAME)))
                .orElse(false);
    }

    private static MethodModel findMethod(ClassModel classModel, String name, MethodTypeDesc type) {
        return classModel.methods().stream()
                .filter(m -> m.methodName().stringValue().equals(name)
                        && m.methodType().stringValue().equals(type.descriptorString()))
                .findFirst()
                .orElseThrow();
    }

    private void forEachClassFile(Path jarFile, Consumer<ClassModel> action) {
        try (JarFile jf = new JarFile(jarFile.toFile(), false, ZipFile.OPEN_READ, version)) {
            jf.versionedStream().forEach(je -> {
                if (je.getName().endsWith(".class")) {
                    try {
                        ClassModel model = ClassFile.of().parse(jf.getInputStream(je).readAllBytes());
                        action.accept(model);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
