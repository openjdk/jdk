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
package jdk.classfile.attribute;

import jdk.classfile.impl.UnboundAttribute;

/**
 * Models a single character range in the {@link CharacterRangeTableAttribute}.
 */
sealed public interface CharacterRangeInfo
        permits UnboundAttribute.UnboundCharacterRangeInfo {

    /**
     * {@return the start of the character range region (inclusive)}  This is
     * the index into the code array at which the code for this character range
     * begins.
     */
    int startPc();

    /**
     * {@return the end of the character range region (exclusive)}  This is the
     * index into the code array after which the code for this character range
     * ends.
     */
    int endPc();

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
     * may be set.  Valid flags include {@link jdk.classfile.Classfile#CRT_STATEMENT},
     * {@link jdk.classfile.Classfile#CRT_BLOCK},
     * {@link jdk.classfile.Classfile#CRT_ASSIGNMENT},
     * {@link jdk.classfile.Classfile#CRT_FLOW_CONTROLLER},
     * {@link jdk.classfile.Classfile#CRT_FLOW_TARGET},
     * {@link jdk.classfile.Classfile#CRT_INVOKE},
     * {@link jdk.classfile.Classfile#CRT_CREATE},
     * {@link jdk.classfile.Classfile#CRT_BRANCH_TRUE},
     * {@link jdk.classfile.Classfile#CRT_BRANCH_FALSE}.
     *
     * @@@ Need reference for interpretation of flags.
     *
     * @return the flags
     */
    int flags();

    /**
     * {@return a character range description}
     * @param startPc the start of the bytecode range, inclusive
     * @param endPc the end of the bytecode range, exclusive
     * @param characterRangeStart the start of the character range, inclusive,
     *                            encoded as {@code line_number << 10 + column_number}
     * @param characterRangeEnd the end of the character range, exclusive,
     *                          encoded as {@code line_number << 10 + column_number}
     * @param flags the range flags
     */
    static CharacterRangeInfo of(int startPc,
                                 int endPc,
                                 int characterRangeStart,
                                 int characterRangeEnd,
                                 int flags) {
        return new UnboundAttribute.UnboundCharacterRangeInfo(startPc, endPc,
                                                              characterRangeStart, characterRangeEnd,
                                                              flags);
    }
}
