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
package java.lang.classfile.instruction;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.Label;
import java.lang.classfile.PseudoInstruction;
import java.lang.classfile.attribute.CharacterRangeInfo;
import java.lang.classfile.attribute.CharacterRangeTableAttribute;

import jdk.internal.classfile.impl.AbstractPseudoInstruction;
import jdk.internal.classfile.impl.BoundCharacterRange;

/**
 * A pseudo-instruction which models a single entry in the {@link
 * CharacterRangeTableAttribute CharacterRangeTable} attribute.  Delivered as a
 * {@link CodeElement} during traversal of the elements of a {@link CodeModel},
 * according to the setting of the {@link ClassFile.DebugElementsOption} option.
 * <p>
 * A character range entry is composite:
 * {@snippet lang=text :
 * // @link substring="CharacterRange" target="#of":
 * CharacterRange(
 *     Label startScope, // @link substring="startScope" target="#startScope"
 *     Label endScope, // @link substring="endScope" target="#endScope"
 *     int characterRangeStart, // @link substring="characterRangeStart" target="#characterRangeStart"
 *     int characterRangeEnd, // @link substring="characterRangeEnd" target="#characterRangeEnd"
 *     int flags // @link substring="flags" target="#flags"
 * )
 * }
 * <p>
 * Another model, {@link CharacterRangeInfo}, also models a character range
 * entry;  it has no dependency on a {@code CodeModel} and represents of bci
 * values as {@code int}s instead of {@code Label}s, and is used as components
 * of a {@link CharacterRangeTableAttribute}.
 *
 * @see CharacterRangeInfo
 * @see CodeBuilder#characterRange CodeBuilder::characterRange
 * @see ClassFile.DebugElementsOption
 * @since 24
 */
public sealed interface CharacterRange extends PseudoInstruction
        permits AbstractPseudoInstruction.UnboundCharacterRange, BoundCharacterRange {

    /** The bit mask of STATEMENT {@link CharacterRangeInfo} kind. */
    int FLAG_STATEMENT = 0x0001;

    /** The bit mask of BLOCK {@link CharacterRangeInfo} kind. */
    int FLAG_BLOCK = 0x0002;

    /** The bit mask of ASSIGNMENT {@link CharacterRangeInfo} kind. */
    int FLAG_ASSIGNMENT = 0x0004;

    /** The bit mask of FLOW_CONTROLLER {@link CharacterRangeInfo} kind. */
    int FLAG_FLOW_CONTROLLER = 0x0008;

    /** The bit mask of FLOW_TARGET {@link CharacterRangeInfo} kind. */
    int FLAG_FLOW_TARGET = 0x0010;

    /** The bit mask of INVOKE {@link CharacterRangeInfo} kind. */
    int FLAG_INVOKE = 0x0020;

    /** The bit mask of CREATE {@link CharacterRangeInfo} kind. */
    int FLAG_CREATE = 0x0040;

    /** The bit mask of BRANCH_TRUE {@link CharacterRangeInfo} kind. */
    int FLAG_BRANCH_TRUE = 0x0080;

    /** The bit mask of BRANCH_FALSE {@link CharacterRangeInfo} kind. */
    int FLAG_BRANCH_FALSE = 0x0100;

    /**
     * {@return the start of the instruction range}
     */
    Label startScope();

    /**
     * {@return the end of the instruction range}
     */
    Label endScope();

    /**
     * {@return the encoded start of the character range region (inclusive)}
     * The value is constructed from the line_number/column_number pair as given
     * by {@code line_number << 10 + column_number}, where the source file is
     * viewed as an array of (possibly multi-byte) characters.
     */
    int characterRangeStart();

    /**
     * {@return the encoded end of the character range region (exclusive)}.
     * The value is constructed from the line_number/column_number pair as given
     * by {@code line_number << 10 + column_number}, where the source file is
     * viewed as an array of (possibly multi-byte) characters.
     */
    int characterRangeEnd();

    /**
     * A flags word, indicating the kind of range.  Multiple flag bits
     * may be set.  Valid flags include:
     * <ul>
     * <li>{@link #FLAG_STATEMENT}
     * <li>{@link #FLAG_BLOCK}
     * <li>{@link #FLAG_ASSIGNMENT}
     * <li>{@link #FLAG_FLOW_CONTROLLER}
     * <li>{@link #FLAG_FLOW_TARGET}
     * <li>{@link #FLAG_INVOKE}
     * <li>{@link #FLAG_CREATE}
     * <li>{@link #FLAG_BRANCH_TRUE}
     * <li>{@link #FLAG_BRANCH_FALSE}
     * </ul>
     *
     * @see CharacterRangeInfo#flags()
     *
     * @return the flags
     */
    int flags();

    /**
     * {@return a character range pseudo-instruction}
     *
     * @param startScope the start of the instruction range
     * @param endScope the end of the instruction range
     * @param characterRangeStart the encoded start of the character range region (inclusive)
     * @param characterRangeEnd the encoded end of the character range region (exclusive)
     * @param flags a flags word, indicating the kind of range
     */
    static CharacterRange of(Label startScope, Label endScope, int characterRangeStart, int characterRangeEnd, int flags) {
        return new AbstractPseudoInstruction.UnboundCharacterRange(startScope, endScope, characterRangeStart, characterRangeEnd, flags);
    }
}
