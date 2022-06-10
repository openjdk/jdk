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

import java.util.List;

import jdk.classfile.Attribute;
import jdk.classfile.impl.BoundAttribute;
import jdk.classfile.impl.UnboundAttribute;

/**
 * Models the {@code CharacterRangeTable} attribute (@@@ need spec reference),
 * which appears on {@code Code} attributes.  It is used by debuggers to
 * determine the correspondence between the code array and the source file.
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

