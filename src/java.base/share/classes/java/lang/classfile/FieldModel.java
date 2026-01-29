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

package java.lang.classfile;

import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.constant.ClassDesc;
import java.lang.reflect.AccessFlag;
import java.util.Optional;
import java.util.function.Consumer;

import jdk.internal.classfile.impl.BufferedFieldBuilder;
import jdk.internal.classfile.impl.FieldImpl;
import jdk.internal.classfile.impl.Util;

/**
 * Models a field.  A field can be viewed as a {@linkplain CompoundElement
 * composition} of {@link FieldElement}s, or by random access via accessor
 * methods if only specific parts of the field is needed.
 * <p>
 * Fields can be obtained from {@link ClassModel#fields()}, or in the traversal
 * of member elements of a class.
 * <p>
 * {@link ClassBuilder#withField(String, ClassDesc, Consumer)} is the main way
 * to construct fields.  {@link ClassBuilder#transformField} allows creating a
 * new field by selectively processing the original field elements and directing
 * the results to a field builder.
 * <p>
 * All field attributes are accessible as member elements.
 *
 * @see ClassModel#fields()
 * @see FieldTransform
 * @jvms 4.5 Fields
 * @since 24
 */
public sealed interface FieldModel
        extends CompoundElement<FieldElement>, AttributedElement, ClassElement
        permits BufferedFieldBuilder.Model, FieldImpl {

    /**
     * {@return the access flags}
     *
     * @see AccessFlag.Location#FIELD
     */
    AccessFlags flags();

    /** {@return the class model this field is a member of, if known} */
    Optional<ClassModel> parent();

    /** {@return the name of this field} */
    Utf8Entry fieldName();

    /** {@return the field descriptor string of this field} */
    Utf8Entry fieldType();

    /** {@return the field type, as a symbolic descriptor} */
    default ClassDesc fieldTypeSymbol() {
        return Util.fieldTypeSymbol(fieldType());
    }
}
