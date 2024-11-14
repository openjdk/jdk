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
package java.lang.classfile.constantpool;

import java.io.DataInput;

import jdk.internal.classfile.impl.AbstractPoolEntry;
import jdk.internal.javac.PreviewFeature;

/**
 * Models a {@code CONSTANT_UTF8_info} constant, representing strings, in the
 * constant pool of a {@code class} file.  This describes strings in the
 * {@linkplain DataInput##modified-utf-8 Modified UTF-8} format.
 * <p>
 * Conceptually, a UTF8 entry is a record:
 * {@snippet lang=text :
 * // @link substring="Utf8Entry" target="ConstantPoolBuilder#utf8Entry(String)" :
 * Utf8Entry(String) // @link substring="String" target="#stringValue()"
 * }
 * where the encoded data length must be no more than 65535 bytes.
 * <p>
 * Physically, a UTF8 entry is of flexible length: it is represented as an array
 * structure, with an {@code u2} for the data length in bytes, followed by that
 * number of bytes of Modified UTF-8 data.  It can represent at most 65535 bytes
 * of data due to the physical restrictions.
 *
 * @jvms 4.4.7 The {@code CONSTANT_Utf8_info} Structure
 * @see DataInput##modified-utf-8 Modified UTF-8
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public sealed interface Utf8Entry
        extends CharSequence, AnnotationConstantValueEntry
        permits AbstractPoolEntry.Utf8EntryImpl {

    /**
     * {@return the string value for this entry}
     *
     * @see ConstantPoolBuilder#utf8Entry(String) ConstantPoolBuilder::utf8Entry
     */
    String stringValue();

    /**
     * {@return whether this entry describes the same string as the provided string}
     *
     * @param s the string to compare to
     */
    boolean equalsString(String s);
}
