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
package java.lang.classfile.instruction;

import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.Instruction;
import jdk.internal.classfile.impl.AbstractInstruction;
import jdk.internal.javac.PreviewFeature;

/**
 * Models a {@code multianewarray} invocation instruction in the {@code code}
 * array of a {@code Code} attribute.  Delivered as a {@link CodeElement}
 * when traversing the elements of a {@link CodeModel}.
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public sealed interface NewMultiArrayInstruction extends Instruction
        permits AbstractInstruction.BoundNewMultidimensionalArrayInstruction,
                AbstractInstruction.UnboundNewMultidimensionalArrayInstruction {

    /**
     * {@return the type of the array, as a symbolic descriptor}
     */
    ClassEntry arrayType();

    /**
     * {@return the number of dimensions of the array}
     */
    int dimensions();

    /**
     * {@return a new multi-dimensional array instruction}
     *
     * @param arrayTypeEntry the type of the array
     * @param dimensions the number of dimensions of the array
     */
    static NewMultiArrayInstruction of(ClassEntry arrayTypeEntry,
                                       int dimensions) {
        return new AbstractInstruction.UnboundNewMultidimensionalArrayInstruction(arrayTypeEntry, dimensions);
    }
}
