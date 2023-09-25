/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
import jdk.internal.javac.PreviewFeature;

/**
 * Models metadata about a {@link CodeAttribute}, such as entries in the
 * exception table, line number table, local variable table, or the mapping
 * between instructions and labels.  Pseudo-instructions are delivered as part
 * of the element stream of a {@link CodeModel}.  Delivery of some
 * pseudo-instructions can be disabled by modifying the value of classfile
 * options (e.g., {@link ClassFile.DebugElementsOption}).
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public sealed interface PseudoInstruction
        extends CodeElement
        permits CharacterRange, ExceptionCatch, LabelTarget, LineNumber, LocalVariable, LocalVariableType, AbstractPseudoInstruction {
}
