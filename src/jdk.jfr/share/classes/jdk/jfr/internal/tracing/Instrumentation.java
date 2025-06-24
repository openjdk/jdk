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
package jdk.jfr.internal.tracing;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassElement;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassFile.ClassHierarchyResolverOption;
import java.lang.classfile.ClassFile.Option;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodTransform;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jdk.jfr.internal.LogLevel;
import jdk.jfr.internal.LogTag;
import jdk.jfr.internal.Logger;

/**
 * Class that adds bytecode instrumentation for a class.
 */
final class Instrumentation {
    private final Map<String, Method> modificationMap = new LinkedHashMap<>();
    private final String className;
    private final ClassLoader classLoader;
    private final byte[] bytecode;

    public Instrumentation(ClassLoader classLoader, String internalClassName, byte[] bytecode) {
        this.className = internalClassName.replace("/", ".");
        this.classLoader = classLoader;
        this.bytecode = bytecode;
    }

    public void addMethod(long methodId, String name, String signature, int modification) {
        modificationMap.put(name + signature, new Method(methodId, Modification.valueOf(modification), className + "::" + name));
    }

    public List<Method> getMethods() {
        return new ArrayList<>(modificationMap.values());
    }

    public byte[] generateBytecode() {
        boolean[] modified = new boolean[1];
        ClassFile classFile = ClassFile.of(resolverOption());
        ClassModel classModel = classFile.parse(bytecode);
        byte[] generated = classFile.build(classModel.thisClass().asSymbol(), classBuilder -> {
            for (var ce : classModel) {
                if (modifyClassElement(classBuilder, ce)) {
                    modified[0] = true;
                } else {
                    classBuilder.with(ce);
                }
            }
        });
        return modified[0] ? generated : null;
    }

    private Option resolverOption() {
        return ClassHierarchyResolverOption.of(resolver());
    }

    private ClassHierarchyResolver resolver() {
        if (classLoader == null) {
            return ClassHierarchyResolver.ofResourceParsing(ClassLoader.getSystemClassLoader());
        } else {
            return ClassHierarchyResolver.ofResourceParsing(classLoader);
        }
    }

    private boolean modifyClassElement(ClassBuilder classBuilder, ClassElement ce) {
        if (ce instanceof MethodModel mm) {
            String method = mm.methodName().stringValue();
            String signature = mm.methodType().stringValue();
            String full = method + signature;
            Method tm = modificationMap.get(full);
            if (tm != null) {
                Modification m = tm.modification();
                if (m.tracing() || m.timing()) {
                    return modifyMethod(classBuilder, mm, tm);
                }
            }
        }
        return false;
    }

    private boolean modifyMethod(ClassBuilder classBuilder, MethodModel m, Method method) {
        var code = m.code();
        if (code.isPresent()) {
            if (classLoader == null && ExcludeList.containsMethod(method.name())) {
                String msg = "Risk of recursion, skipping bytecode generation of " + method.name();
                Logger.log(LogTag.JFR_METHODTRACE, LogLevel.DEBUG, msg);
                return false;
            }
            MethodTransform s = MethodTransform.ofStateful(
                () -> MethodTransform.transformingCode(new Transform(method))
            );
            classBuilder.transformMethod(m, s);
            return true;
        }
        return false;
    }
}
