/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

package compiler.jvmci.common;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.hotspot.CompilerToVMHelper;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;

import java.io.IOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class CTVMUtilities {
    /*
     * A method to return HotSpotResolvedJavaMethod object using class object
     * and method as input
     */
    public static HotSpotResolvedJavaMethod getResolvedMethod(Class<?> cls,
            Executable method) {
        if (!(method instanceof Method || method instanceof Constructor)) {
            throw new Error("wrong executable type " + method.getClass());
        }
        return CompilerToVMHelper.asResolvedJavaMethod(method);
    }

    public static HotSpotResolvedJavaMethod getResolvedMethod(
            Executable method) {
        return getResolvedMethod(method.getDeclaringClass(), method);
    }

    public static InstalledCode getInstalledCode(ResolvedJavaMethod method, String name, long address, long entryPoint) {
        return CompilerToVMHelper.getInstalledCode(method, name, address, entryPoint);
    }

    public static Map<Integer, Integer> getBciToLineNumber(Executable method) {
        ClassModel classModel = findClassBytes(method.getDeclaringClass());
        MethodModel methodModel = findMethod(classModel, method);
        if (methodModel == null)
            return Map.of();

        var foundLineNumberTable = methodModel.code().flatMap(code ->
                code.findAttribute(Attributes.lineNumberTable()));
        if (foundLineNumberTable.isEmpty()) {
            boolean isEmptyMethod = Modifier.isAbstract(method.getModifiers())
                    || Modifier.isNative(method.getModifiers());
            if (!isEmptyMethod) {
                throw new Error(method + " doesn't contains the line numbers table "
                        + "(the method marked neither abstract nor native)");
            }
            return Map.of();
        }

        Map<Integer, Integer> lineNumbers = new TreeMap<>();
        foundLineNumberTable.get().lineNumbers().forEach(ln ->
                lineNumbers.put(ln.startPc(), ln.lineNumber()));
        return lineNumbers;
    }

    // Finds the ClassFile API model of a given class, or fail with an Error.
    public static ClassModel findClassBytes(Class<?> clazz) {
        String binaryName = clazz.getName();
        byte[] fileBytes;
        try (var inputStream = clazz.getModule().getResourceAsStream(
                binaryName.replace('.', '/') + ".class")) {
            fileBytes = inputStream.readAllBytes();
        } catch (IOException e) {
            throw new Error("TEST BUG: cannot read " + binaryName, e);
        }
        return ClassFile.of().parse(fileBytes);
    }

    // Finds a matching method in a class model, or null if none match.
    public static MethodModel findMethod(ClassModel classModel, Executable method) {
        MethodTypeDesc methodType = MethodType.methodType(
                method instanceof Method m ? m.getReturnType() : void.class,
                method.getParameterTypes()).describeConstable().orElseThrow();
        String methodName = method instanceof Method m ? m.getName() : ConstantDescs.INIT_NAME;

        for (var methodModel : classModel.methods()) {
            if (methodModel.methodName().equalsString(methodName)
                    && methodModel.methodType().isMethodType(methodType)) {
                return methodModel;
            }
        }
        return null;
    }
}
