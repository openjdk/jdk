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
import jdk.classfile.PseudoInstruction;
import jdk.classfile.attribute.CharacterRangeTableAttribute;
import jdk.classfile.attribute.LineNumberTableAttribute;
import jdk.classfile.impl.LineNumberImpl;

/**
 * A pseudo-instruction which models a single entry in the
 * {@link LineNumberTableAttribute}.  Delivered as a {@link CodeElement}
 * during traversal of the elements of a {@link CodeModel}, according to
 * the setting of the {@link Classfile.Option.Key#PROCESS_LINE_NUMBERS} option.
 *
 * @see PseudoInstruction
 */
public sealed interface LineNumber extends PseudoInstruction
        permits LineNumberImpl {
    int line();
}
