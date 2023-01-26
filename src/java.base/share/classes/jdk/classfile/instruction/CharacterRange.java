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
package jdk.classfile.instruction;

import jdk.classfile.Classfile;
import jdk.classfile.CodeElement;
import jdk.classfile.CodeModel;
import jdk.classfile.Label;
import jdk.classfile.PseudoInstruction;
import jdk.classfile.attribute.CharacterRangeTableAttribute;
import jdk.classfile.impl.AbstractPseudoInstruction;
import jdk.classfile.impl.BoundCharacterRange;

/**
 * A pseudo-instruction which models a single entry in the
 * {@link CharacterRangeTableAttribute}.  Delivered as a {@link CodeElement}
 * during traversal of the elements of a {@link CodeModel}, according to
 * the setting of the {@link Classfile.Option.Key#PROCESS_DEBUG} option.
 */
public sealed interface CharacterRange extends PseudoInstruction
        permits AbstractPseudoInstruction.UnboundCharacterRange, BoundCharacterRange {
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
     * may be set.  Valid flags include
     * {@link jdk.classfile.Classfile#CRT_STATEMENT},
     * {@link jdk.classfile.Classfile#CRT_BLOCK},
     * {@link jdk.classfile.Classfile#CRT_ASSIGNMENT},
     * {@link jdk.classfile.Classfile#CRT_FLOW_CONTROLLER},
     * {@link jdk.classfile.Classfile#CRT_FLOW_TARGET},
     * {@link jdk.classfile.Classfile#CRT_INVOKE},
     * {@link jdk.classfile.Classfile#CRT_CREATE},
     * {@link jdk.classfile.Classfile#CRT_BRANCH_TRUE},
     * {@link jdk.classfile.Classfile#CRT_BRANCH_FALSE}.
     *
     * @see jdk.classfile.attribute.CharacterRangeInfo#flags()
     *
     * @return the flags
     */
    int flags();
}
