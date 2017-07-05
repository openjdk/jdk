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

package jdk.nashorn.internal.codegen;

import static jdk.internal.org.objectweb.asm.Opcodes.IFEQ;
import static jdk.internal.org.objectweb.asm.Opcodes.IFGE;
import static jdk.internal.org.objectweb.asm.Opcodes.IFGT;
import static jdk.internal.org.objectweb.asm.Opcodes.IFLE;
import static jdk.internal.org.objectweb.asm.Opcodes.IFLT;
import static jdk.internal.org.objectweb.asm.Opcodes.IFNE;
import static jdk.internal.org.objectweb.asm.Opcodes.IF_ACMPEQ;
import static jdk.internal.org.objectweb.asm.Opcodes.IF_ACMPNE;
import static jdk.internal.org.objectweb.asm.Opcodes.IF_ICMPEQ;
import static jdk.internal.org.objectweb.asm.Opcodes.IF_ICMPGE;
import static jdk.internal.org.objectweb.asm.Opcodes.IF_ICMPGT;
import static jdk.internal.org.objectweb.asm.Opcodes.IF_ICMPLE;
import static jdk.internal.org.objectweb.asm.Opcodes.IF_ICMPLT;
import static jdk.internal.org.objectweb.asm.Opcodes.IF_ICMPNE;

/**
 * Condition enum used for all kinds of jumps, regardless of type
 */
enum Condition {
    EQ,
    NE,
    LE,
    LT,
    GE,
    GT;

    static int toUnary(final Condition c) {
        switch (c) {
        case EQ:
            return IFEQ;
        case NE:
            return IFNE;
        case LE:
            return IFLE;
        case LT:
            return IFLT;
        case GE:
            return IFGE;
        case GT:
            return IFGT;
        default:
            throw new UnsupportedOperationException("toUnary:" + c.toString());
        }
    }

    static int toBinary(final Condition c, final boolean isObject) {
        switch (c) {
        case EQ:
            return isObject ? IF_ACMPEQ : IF_ICMPEQ;
        case NE:
            return isObject ? IF_ACMPNE : IF_ICMPNE;
        case LE:
            return IF_ICMPLE;
        case LT:
            return IF_ICMPLT;
        case GE:
            return IF_ICMPGE;
        case GT:
            return IF_ICMPGT;
        default:
            throw new UnsupportedOperationException("toBinary:" + c.toString());
        }
    }
}
