/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.test;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.test.GraalTest;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import jdk.vm.ci.meta.JavaKind;

public final class SubWordTestUtil implements Opcodes {

    private SubWordTestUtil() {
    }

    static void convertToKind(MethodVisitor snippet, JavaKind kind) {
        switch (kind) {
            case Boolean:
                snippet.visitInsn(ICONST_1);
                snippet.visitInsn(IAND);
                break;
            case Byte:
                snippet.visitInsn(I2B);
                break;
            case Short:
                snippet.visitInsn(I2S);
                break;
            case Char:
                snippet.visitInsn(I2C);
                break;
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    static void testEqual(MethodVisitor snippet) {
        Label label = new Label();
        snippet.visitJumpInsn(IF_ICMPNE, label);
        snippet.visitInsn(ICONST_1);
        snippet.visitInsn(IRETURN);
        snippet.visitLabel(label);
        snippet.visitInsn(ICONST_0);
        snippet.visitInsn(IRETURN);
    }

    static void getUnsafe(MethodVisitor snippet) {
        snippet.visitFieldInsn(GETSTATIC, GraalTest.class.getName().replace('.', '/'), "UNSAFE", "Lsun/misc/Unsafe;");
    }

    static String getUnsafePutMethodName(JavaKind kind) {
        String name = kind.getJavaName();
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

}
