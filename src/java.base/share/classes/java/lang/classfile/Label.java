/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.classfile.instruction.LabelTarget;
import java.util.ListIterator;

import jdk.internal.classfile.impl.LabelImpl;

/**
 * A marker for a position within the instructions of a method body.  The
 * position is a cursor position in the list of instructions, similar to that
 * of a {@link ListIterator}.
 *
 * <h2 id="reading">Reading Labels</h2>
 * Labels read from {@code class} files represent positions in the {@code code}
 * array of a {@link CodeAttribute Code} attribute.  It is associated with a
 * <dfn>{@index bci}</dfn> (bytecode index), also known as <dfn>{@index pc}</dfn>
 * (program counter), the index into the {@code code} array; the actual cursor
 * position is immediately before the given index, so a label at the beginning
 * of the instructions has bci {@code 0}, and a label at the end of the
 * instructions has bci {@link CodeAttribute#codeLength codeLength() + 1}.  The
 * bci can be inspected through {@link CodeAttribute#labelToBci
 * CodeAttribute::labelToBci}.
 * <p>
 * In generic {@link CodeModel}s, a label may not have a bci value; the position
 * of a label can be found by searching for the corresponding {@link LabelTarget}
 * within that model.
 *
 * <h2 id="writing">Writing Labels</h2>
 * Many models in {@link java.lang.classfile} refer to labels.  To write a
 * label, a label must be obtained, it must be bound to a {@link CodeBuilder}.
 * <p>
 * To obtain a label:
 * <ul>
 * <li>Use a label read from other models.
 * <li>Use pre-defined labels from a {@link CodeBuilder}, such as {@link
 *     CodeBuilder#startLabel() CodeBuilder::startLabel}, {@link CodeBuilder#endLabel
 *     CodeBuilder::endLabel}, or {@link CodeBuilder.BlockCodeBuilder#breakLabel
 *     BlockCodeBuilder::breakLabel}.  They are already bound.
 * <li>Create labels with {@link CodeBuilder#newLabel CodeBuilder::newLabel} or
 *     {@link CodeBuilder#newBoundLabel CodeBuilder::newBoundLabel}.
 * </ul>
 * <p>
 * A label must be bound exactly once in the {@code CodeBuilder} where it is
 * used; otherwise, writing fails.  To bind an unbound label:
 * <ul>
 * <li>Send a read {@link LabelTarget} to a {@code CodeBuilder}.
 * <li>Use {@link CodeBuilder#labelBinding CodeBuilder::labelBinding}.
 * </ul>
 * Note that a label read from another model is not automatically bound in a
 * {@code CodeBuilder}; they are separate entities and the label is bound to
 * different positions in them.
 *
 * @see CodeAttribute#labelToBci CodeAttribute::labelToBci
 * @see CodeBuilder#newLabel CodeBuilder::newLabel
 * @see CodeBuilder#labelBinding CodeBuilder::labelBinding
 * @since 24
 */
public sealed interface Label
        permits LabelImpl {
}
