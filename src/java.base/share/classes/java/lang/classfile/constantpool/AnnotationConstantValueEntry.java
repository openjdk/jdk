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
package java.lang.classfile.constantpool;

import java.lang.classfile.AnnotationValue;
import java.lang.constant.ConstantDesc;

/**
 * Marker interface for constant pool entries that can represent constant values
 * associated with elements of annotations.  They are also the only entries that
 * do not refer to other constant pool entries.
 *
 * @apiNote
 * An annotation constant value entry alone is not sufficient to determine
 * the annotation constant; for example, an {@link IntegerEntry} of {@code 1}
 * can mean {@code true} in {@link AnnotationValue.OfBoolean} or {@code 1}
 * in {@link AnnotationValue.OfInt}.
 *
 * @see AnnotationValue.OfConstant
 * @jvms 4.7.16.1 The {@code element_value} structure
 * @sealedGraph
 * @since 24
 */
public sealed interface AnnotationConstantValueEntry extends PoolEntry
        permits DoubleEntry, FloatEntry, IntegerEntry, LongEntry, Utf8Entry {

    /**
     * {@return the constant value}  The constant value will be an {@link
     * Integer}, {@link Long}, {@link Float}, {@link Double} for the primitive
     * constants, or {@link String} for UTF8 constants.
     */
    ConstantDesc constantValue();
}
