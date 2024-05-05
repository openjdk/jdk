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

package java.lang.classfile;

import java.lang.constant.ClassDesc;
import java.util.Optional;

import java.lang.classfile.constantpool.Utf8Entry;
import jdk.internal.classfile.impl.BufferedFieldBuilder;
import jdk.internal.classfile.impl.FieldImpl;
import jdk.internal.javac.PreviewFeature;

/**
 * Models a field.  The contents of the field can be traversed via
 * a streaming view (e.g., {@link #elements()}), or via random access (e.g.,
 * {@link #flags()}), or by freely mixing the two.
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public sealed interface FieldModel
        extends WritableElement<FieldModel>, CompoundElement<FieldElement>, AttributedElement, ClassElement
        permits BufferedFieldBuilder.Model, FieldImpl {

    /** {@return the access flags} */
    AccessFlags flags();

    /** {@return the class model this field is a member of, if known} */
    Optional<ClassModel> parent();

    /** {@return the name of this field} */
    Utf8Entry fieldName();

    /** {@return the field descriptor of this field} */
    Utf8Entry fieldType();

    /** {@return the field descriptor of this field, as a symbolic descriptor} */
    default ClassDesc fieldTypeSymbol() {
        return ClassDesc.ofDescriptor(fieldType().stringValue());
    }
}
