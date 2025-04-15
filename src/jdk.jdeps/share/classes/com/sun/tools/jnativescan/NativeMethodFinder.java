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

import com.sun.tools.jnativescan.JNativeScanTask.Diagnostics;
import com.sun.tools.jnativescan.RestrictedUse.NativeMethodDecl;
import com.sun.tools.jnativescan.RestrictedUse.RestrictedMethodRefs;

import java.io.IOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.*;

class NativeMethodFinder {

    // ct.sym uses this fake name for the restricted annotation instead
    // see make/langtools/src/classes/build/tools/symbolgenerator/CreateSymbols.java
    private static final String RESTRICTED_NAME = "Ljdk/internal/javac/Restricted+Annotation;";

    private final Map<MethodRef, Boolean> cache = new HashMap<>();
    private final Diagnostics diagnostics;
    private final SystemClassResolver systemClassResolver;

    private NativeMethodFinder(Diagnostics diagnostics, SystemClassResolver systemClassResolver) {
        this.diagnostics = diagnostics;
        this.systemClassResolver = systemClassResolver;
    }

    public static NativeMethodFinder create(Diagnostics diagnostics, SystemClassResolver systemClassResolver) throws JNativeScanFatalError, IOException {
        return new NativeMethodFinder(diagnostics, systemClassResolver);
    }

    public List<RestrictedUse> find(ClassModel model) throws JNativeScanFatalError {
        return model.methods().stream().<RestrictedUse>mapMulti((methodModel, sink) -> {
                if (methodModel.flags().has(AccessFlag.NATIVE)) {
                    sink.accept(new NativeMethodDecl(MethodRef.ofModel(methodModel)));
                } else {
                    SortedSet<MethodRef> perMethod = findRestrictedMethodInvocations(methodModel);
                    if (!perMethod.isEmpty()) {
                        sink.accept(new RestrictedMethodRefs(MethodRef.ofModel(methodModel), perMethod));
                    }
                }
            })
            .toList();
    }

    private SortedSet<MethodRef> findRestrictedMethodInvocations(MethodModel methodModel) {
        SortedSet<MethodRef> perMethod = new TreeSet<>(Comparator.comparing(MethodRef::toString));
        methodModel.code().ifPresent(code -> code.forEach(e -> {
            if (e instanceof InvokeInstruction invoke) {
                MethodRef ref = MethodRef.ofInvokeInstruction(invoke);
                try {
                    if (isRestrictedMethod(ref)) {
                        perMethod.add(ref);
                    }
                } catch (JNativeScanFatalError ex) {
                    diagnostics.error(MethodRef.ofModel(methodModel), ex);
                }
            }
        }));
        return perMethod;
    }

    private boolean isRestrictedMethod(MethodRef ref) throws JNativeScanFatalError {
        return cache.computeIfAbsent(ref, methodRef -> {
            if (methodRef.owner().isArray()) {
                // no restricted methods in arrays atm, and we can't look them up since they have no class file
                return false;
            }
            Optional<ClassModel> modelOpt = systemClassResolver.lookup(methodRef.owner());
            if (!modelOpt.isPresent()) {
                // The target class is not in a system module. Since only system modules
                // contain classes with restricted methods, we can safely assume that
                // the target method is not restricted.
                return false;
            }
            ClassModel classModel = modelOpt.get();
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
