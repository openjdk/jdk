/*
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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * ASM: a very small and fast Java bytecode manipulation framework
 * Copyright (c) 2000-2011 INRIA, France Telecom
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holders nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */
package jdk.internal.org.objectweb.asm.tree.analysis;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.tree.AbstractInsnNode;
import jdk.internal.org.objectweb.asm.tree.FieldInsnNode;
import jdk.internal.org.objectweb.asm.tree.InvokeDynamicInsnNode;
import jdk.internal.org.objectweb.asm.tree.LdcInsnNode;
import jdk.internal.org.objectweb.asm.tree.MethodInsnNode;

/**
 * An {@link Interpreter} for {@link SourceValue} values.
 *
 * @author Eric Bruneton
 */
public class SourceInterpreter extends Interpreter<SourceValue> implements
        Opcodes {

    public SourceInterpreter() {
        super(ASM5);
    }

    protected SourceInterpreter(final int api) {
        super(api);
    }

    @Override
    public SourceValue newValue(final Type type) {
        if (type == Type.VOID_TYPE) {
            return null;
        }
        return new SourceValue(type == null ? 1 : type.getSize());
    }

    @Override
    public SourceValue newOperation(final AbstractInsnNode insn) {
        int size;
        switch (insn.getOpcode()) {
        case LCONST_0:
        case LCONST_1:
        case DCONST_0:
        case DCONST_1:
            size = 2;
            break;
        case LDC:
            Object cst = ((LdcInsnNode) insn).cst;
            size = cst instanceof Long || cst instanceof Double ? 2 : 1;
            break;
        case GETSTATIC:
            size = Type.getType(((FieldInsnNode) insn).desc).getSize();
            break;
        default:
            size = 1;
        }
        return new SourceValue(size, insn);
    }

    @Override
    public SourceValue copyOperation(final AbstractInsnNode insn,
            final SourceValue value) {
        return new SourceValue(value.getSize(), insn);
    }

    @Override
    public SourceValue unaryOperation(final AbstractInsnNode insn,
            final SourceValue value) {
        int size;
        switch (insn.getOpcode()) {
        case LNEG:
        case DNEG:
        case I2L:
        case I2D:
        case L2D:
        case F2L:
        case F2D:
        case D2L:
            size = 2;
            break;
        case GETFIELD:
            size = Type.getType(((FieldInsnNode) insn).desc).getSize();
            break;
        default:
            size = 1;
        }
        return new SourceValue(size, insn);
    }

    @Override
    public SourceValue binaryOperation(final AbstractInsnNode insn,
            final SourceValue value1, final SourceValue value2) {
        int size;
        switch (insn.getOpcode()) {
        case LALOAD:
        case DALOAD:
        case LADD:
        case DADD:
        case LSUB:
        case DSUB:
        case LMUL:
        case DMUL:
        case LDIV:
        case DDIV:
        case LREM:
        case DREM:
        case LSHL:
        case LSHR:
        case LUSHR:
        case LAND:
        case LOR:
        case LXOR:
            size = 2;
            break;
        default:
            size = 1;
        }
        return new SourceValue(size, insn);
    }

    @Override
    public SourceValue ternaryOperation(final AbstractInsnNode insn,
            final SourceValue value1, final SourceValue value2,
            final SourceValue value3) {
        return new SourceValue(1, insn);
    }

    @Override
    public SourceValue naryOperation(final AbstractInsnNode insn,
            final List<? extends SourceValue> values) {
        int size;
        int opcode = insn.getOpcode();
        if (opcode == MULTIANEWARRAY) {
            size = 1;
        } else {
            String desc = (opcode == INVOKEDYNAMIC) ? ((InvokeDynamicInsnNode) insn).desc
                    : ((MethodInsnNode) insn).desc;
            size = Type.getReturnType(desc).getSize();
        }
        return new SourceValue(size, insn);
    }

    @Override
    public void returnOperation(final AbstractInsnNode insn,
            final SourceValue value, final SourceValue expected) {
    }

    @Override
    public SourceValue merge(final SourceValue d, final SourceValue w) {
        if (d.insns instanceof SmallSet && w.insns instanceof SmallSet) {
            Set<AbstractInsnNode> s = ((SmallSet<AbstractInsnNode>) d.insns)
                    .union((SmallSet<AbstractInsnNode>) w.insns);
            if (s == d.insns && d.size == w.size) {
                return d;
            } else {
                return new SourceValue(Math.min(d.size, w.size), s);
            }
        }
        if (d.size != w.size || !d.insns.containsAll(w.insns)) {
            HashSet<AbstractInsnNode> s = new HashSet<AbstractInsnNode>();
            s.addAll(d.insns);
            s.addAll(w.insns);
            return new SourceValue(Math.min(d.size, w.size), s);
        }
        return d;
    }
}
