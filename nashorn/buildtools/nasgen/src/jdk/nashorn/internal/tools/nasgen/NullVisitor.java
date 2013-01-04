/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.tools.nasgen;

import jdk.internal.org.objectweb.asm.AnnotationVisitor;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.FieldVisitor;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

/**
 * A visitor that does nothing on visitXXX calls.
 *
 */
public class NullVisitor extends ClassVisitor {
    NullVisitor() {
        super(Opcodes.ASM4);
    }

    @Override
    public MethodVisitor visitMethod(
        final int access,
        final String name,
        final String desc,
        final String signature,
        final String[] exceptions) {
        return new MethodVisitor(Opcodes.ASM4) {
            @Override
            public AnnotationVisitor visitAnnotationDefault() {
                return new NullAnnotationVisitor();
            }

            @Override
            public AnnotationVisitor visitAnnotation(final String descriptor, final boolean visible) {
                return new NullAnnotationVisitor();
            }
        };
    }

    @Override
    public FieldVisitor visitField(
        final int access,
        final String name,
        final String desc,
        final String signature,
        final Object value) {
        return new FieldVisitor(Opcodes.ASM4) {
            @Override
            public AnnotationVisitor visitAnnotation(final String descriptor, final boolean visible) {
                return new NullAnnotationVisitor();
            }
        };
    }

    @Override
    public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
        return new NullAnnotationVisitor();
    }

    private static class NullAnnotationVisitor extends AnnotationVisitor {
        NullAnnotationVisitor() {
            super(Opcodes.ASM4);
        }
    }
}
