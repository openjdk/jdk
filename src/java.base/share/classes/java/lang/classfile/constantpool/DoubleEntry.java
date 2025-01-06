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

import java.lang.classfile.TypeKind;

import jdk.internal.classfile.impl.AbstractPoolEntry;

/**
 * Models a {@code CONSTANT_Double_info} structure, representing a {@code
 * double} constant, in the constant pool of a {@code class} file.
 * <p>
 * The use of a {@code DoubleEntry} is modeled by a {@code double}.  Conversions
 * are through {@link ConstantPoolBuilder#doubleEntry} and {@link #doubleValue()}.
 * In the conversions, all NaN values of the {@code double} may be collapsed
 * into a single {@linkplain Double#NaN "canonical" NaN value}.
 * <p>
 * A double entry has a {@linkplain #width() width} of {@code 2}, making its
 * subsequent constant pool index valid and unusable.
 *
 * @see ConstantPoolBuilder#doubleEntry ConstantPoolBuilder::doubleEntry
 * @jvms 4.4.5 The {@code CONSTANT_Long_info} and {@code CONSTANT_Double_info}
 *             Structures
 * @since 24
 */
public sealed interface DoubleEntry
        extends AnnotationConstantValueEntry, ConstantValueEntry
        permits AbstractPoolEntry.DoubleEntryImpl {

    /**
     * {@return the {@code double} value}
     *
     * @see ConstantPoolBuilder#doubleEntry(double)
     *      ConstantPoolBuilder::doubleEntry(double)
     */
    double doubleValue();

    @Override
    default TypeKind typeKind() {
        return TypeKind.DOUBLE;
    }
}
