/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.invoke;

import java.util.Map;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Opcodes;

/**
 * Helper class to assist the GenerateJLIClassesPlugin to get access to
 * generate classes ahead of time.
 */
class GenerateJLIClassesHelper {

    static byte[] generateDMHClassBytes(String className,
            MethodType[] methodTypes, int[] types) {
        LambdaForm[] forms = new LambdaForm[methodTypes.length];
        for (int i = 0; i < forms.length; i++) {
            forms[i] = DirectMethodHandle.makePreparedLambdaForm(methodTypes[i],
                                                                 types[i]);
            methodTypes[i] = forms[i].methodType();
        }
        return generateCodeBytesForLFs(className, forms, methodTypes);
    }

    /*
     * Generate customized code for a set of LambdaForms of specified types into
     * a class with a specified name.
     */
    private static byte[] generateCodeBytesForLFs(String className,
            LambdaForm[] forms, MethodType[] types) {
        assert(forms.length == types.length);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS + ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PRIVATE + Opcodes.ACC_FINAL + Opcodes.ACC_SUPER,
                className, null, InvokerBytecodeGenerator.INVOKER_SUPER_NAME, null);
        cw.visitSource(className.substring(className.lastIndexOf('/') + 1), null);
        for (int i = 0; i < forms.length; i++) {
            InvokerBytecodeGenerator g
                    = new InvokerBytecodeGenerator(className, forms[i], types[i]);
            g.setClassWriter(cw);
            g.addMethod();
        }
        return cw.toByteArray();
    }

    static Map.Entry<String, byte[]> generateConcreteBMHClassBytes(
            final String types) {
        for (char c : types.toCharArray()) {
            if ("LIJFD".indexOf(c) < 0) {
                throw new IllegalArgumentException("All characters must "
                        + "correspond to a basic field type: LIJFD");
            }
        }
        String shortTypes = LambdaForm.shortenSignature(types);
        final String className =
                BoundMethodHandle.Factory.speciesInternalClassName(shortTypes);
        return Map.entry(className,
                         BoundMethodHandle.Factory.generateConcreteBMHClassBytes(
                                 shortTypes, types, className));
    }
}
