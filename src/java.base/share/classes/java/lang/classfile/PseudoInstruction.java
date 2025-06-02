/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
package java.lang.classfile;

import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.instruction.CharacterRange;
import java.lang.classfile.instruction.ExceptionCatch;
import java.lang.classfile.instruction.LabelTarget;
import java.lang.classfile.instruction.LineNumber;
import java.lang.classfile.instruction.LocalVariable;
import java.lang.classfile.instruction.LocalVariableType;

import jdk.internal.classfile.impl.AbstractPseudoInstruction;

/**
 * Models metadata about a {@link CodeModel}, derived from the {@link
 * CodeAttribute Code} attribute itself or its attributes.
 * <p>
 * Order is significant for some pseudo-instructions relative to {@link
 * Instruction}s, such as {@link LabelTarget} or {@link LineNumber}.  Some
 * pseudo-instructions can be omitted in reading and writing according to
 * certain {@link ClassFile.Option}s.  These are specified in the corresponding
 * modeling interfaces.
 *
 * @sealedGraph
 * @since 24
 */
public sealed interface PseudoInstruction
        extends CodeElement
        permits CharacterRange, ExceptionCatch, LabelTarget, LineNumber, LocalVariable, LocalVariableType, AbstractPseudoInstruction {
}
