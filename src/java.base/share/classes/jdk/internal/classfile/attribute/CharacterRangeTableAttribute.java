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

package jdk.internal.classfile.attribute;

import java.util.List;

import jdk.internal.classfile.Attribute;
import jdk.internal.classfile.impl.BoundAttribute;
import jdk.internal.classfile.impl.UnboundAttribute;

/**
 * The CharacterRangeTable attribute is an optional variable-length attribute in
 * the attributes table of a {@code Code} attribute. It may be used by debuggers
 * to determine which part of the Java virtual machine code array corresponds to
 * a given position in the source file or to determine what section of source
 * code corresponds to a given index into the code array. The
 * CharacterRangeTable attribute consists of an array of character range entries.
 * Each character range entry within the table associates a range of indices in
 * the code array with a range of character indices in the source file. If the
 * source file is viewed as an array of characters, a character index is the
 * corresponding index into this array. Note that character indices are not the
 * same as byte indices as multi-byte characters may be present in the source
 * file. Each character range entry includes a flag which indicates what kind of
 * range is described: statement, assignment, method call, etc. Both code index
 * ranges and character ranges may nest within other ranges, but they may not
 * partially overlap. Thus, a given code index may correspond to several
 * character range entries and in turn several character ranges, but there will
 * be a smallest character range, and for each kind of range in which it is
 * enclosed there will be a smallest character range. Similarly, a given
 * character index may correspond to several character range entries and in turn
 * several code index ranges, but there will be a smallest code index range, and
 * for each kind of range in which it is enclosed there will be a smallest code
 * index range. The character range entries may appear in any order.
 * <p>
 * The attribute permits multiple instances in a given location.
 */
public sealed interface CharacterRangeTableAttribute
        extends Attribute<CharacterRangeTableAttribute>
        permits BoundAttribute.BoundCharacterRangeTableAttribute,
                UnboundAttribute.UnboundCharacterRangeTableAttribute {

    /**
     * {@return the entries of the character range table}
     */
    List<CharacterRangeInfo> characterRangeTable();

    /**
     * {@return a {@code CharacterRangeTable} attribute}
     * @param ranges the descriptions of the character ranges
     */
    static CharacterRangeTableAttribute of(List<CharacterRangeInfo> ranges) {
        return new UnboundAttribute.UnboundCharacterRangeTableAttribute(ranges);
    }
}

