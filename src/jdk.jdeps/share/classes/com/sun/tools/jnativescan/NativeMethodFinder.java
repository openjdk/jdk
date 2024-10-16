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

import com.sun.tools.jnativescan.RestrictedUse.NativeMethodDecl;
import com.sun.tools.jnativescan.RestrictedUse.RestrictedMethodRefs;

import java.io.IOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.*;

class NativeMethodFinder {

    // ct.sym uses this fake name for the restricted annotation instead
    // see make/langtools/src/classes/build/tools/symbolgenerator/CreateSymbols.java
    private static final String RESTRICTED_NAME = "Ljdk/internal/javac/Restricted+Annotation;";

    private final Map<MethodRef, Boolean> cache = new HashMap<>();
    private final ClassResolver classesToScan;
    private final ClassResolver systemClassResolver;

    private NativeMethodFinder(ClassResolver classesToScan, ClassResolver systemClassResolver) {
        this.classesToScan = classesToScan;
        this.systemClassResolver = systemClassResolver;
    }

    public static NativeMethodFinder create(ClassResolver classesToScan, ClassResolver systemClassResolver) throws JNativeScanFatalError, IOException {
        return new NativeMethodFinder(classesToScan, systemClassResolver);
    }

    public SortedMap<ClassFileSource, SortedMap<ClassDesc, List<RestrictedUse>>> findAll() throws JNativeScanFatalError {
        SortedMap<ClassFileSource, SortedMap<ClassDesc, List<RestrictedUse>>> restrictedMethods
                = new TreeMap<>(Comparator.comparing(ClassFileSource::path));
        classesToScan.forEach((_, info) -> {
            ClassModel classModel = info.model();
            List<RestrictedUse> perClass = new ArrayList<>();
            classModel.methods().forEach(methodModel -> {
                if (methodModel.flags().has(AccessFlag.NATIVE)) {
                    perClass.add(new NativeMethodDecl(MethodRef.ofModel(methodModel)));
                } else {
                    SortedSet<MethodRef> perMethod = new TreeSet<>(Comparator.comparing(MethodRef::toString));
                    methodModel.code().ifPresent(code -> {
                         try {
                             code.forEach(e -> {
                                 switch (e) {
                                     case InvokeInstruction invoke -> {
                                         MethodRef ref = MethodRef.ofInvokeInstruction(invoke);
                                         if (isRestrictedMethod(ref)) {
                                             perMethod.add(ref);
                                         }
                                     }
                                     default -> {
                                     }
                                 }
                             });
                         } catch (JNativeScanFatalError e) {
                             throw new JNativeScanFatalError("Error while processing method: " +
                                     MethodRef.ofModel(methodModel), e);
                         }
                    });
                    if (!perMethod.isEmpty()) {
                        perClass.add(new RestrictedMethodRefs(MethodRef.ofModel(methodModel), perMethod));
                    }
                }
            });
            if (!perClass.isEmpty()) {
                restrictedMethods.computeIfAbsent(info.source(),
                                _ -> new TreeMap<>(Comparator.comparing(JNativeScanTask::qualName)))
                        .put(classModel.thisClass().asSymbol(), perClass);
            }
        });
        return restrictedMethods;
    }

    private boolean isRestrictedMethod(MethodRef ref) throws JNativeScanFatalError {
        return cache.computeIfAbsent(ref, methodRef -> {
            if (methodRef.owner().isArray()) {
                // no restricted methods in arrays atm, and we can't look them up since they have no class file
                return false;
            }
            Optional<ClassResolver.Info> info = systemClassResolver.lookup(methodRef.owner());
            if (!info.isPresent()) {
                return false;
            }
            ClassModel classModel = info.get().model();
            Optional<MethodModel> methodModel = findMethod(classModel, methodRef.name(), methodRef.type());
            if (!methodModel.isPresent()) {
                // If we are here, the method was referenced through a subclass of the class containing the actual
                // method declaration. We could implement a method resolver (that needs to be version aware
                // as well) to find the method model of the declaration, but it's not really worth it.
                // None of the restricted methods (atm) are exposed through more than 1 public type, so it's not
                // possible for user code to reference them through a subclass.
                return false;
            }

            return hasRestrictedAnnotation(methodModel.get());
        });
    }

    private static boolean hasRestrictedAnnotation(MethodModel method) {
        return method.findAttribute(Attributes.runtimeVisibleAnnotations())
                .map(rva -> rva.annotations().stream().anyMatch(ann ->
                        ann.className().stringValue().equals(RESTRICTED_NAME)))
                .orElse(false);
    }

    private static Optional<MethodModel> findMethod(ClassModel classModel, String name, MethodTypeDesc type) {
        return classModel.methods().stream()
                .filter(m -> m.methodName().stringValue().equals(name)
                        && m.methodType().stringValue().equals(type.descriptorString()))
                .findFirst();
    }
}
