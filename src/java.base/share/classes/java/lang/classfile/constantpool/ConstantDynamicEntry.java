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
package java.lang.classfile.constantpool;

import java.lang.classfile.TypeKind;
import jdk.internal.classfile.impl.Util;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.DynamicConstantDesc;

import jdk.internal.classfile.impl.AbstractPoolEntry;
import jdk.internal.javac.PreviewFeature;

/**
 * Models a {@code CONSTANT_Dynamic_info} constant in the constant pool of a
 * classfile.
 * @jvms 4.4.10 The CONSTANT_Dynamic_info and CONSTANT_InvokeDynamic_info Structures
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public sealed interface ConstantDynamicEntry
        extends DynamicConstantPoolEntry, LoadableConstantEntry
        permits AbstractPoolEntry.ConstantDynamicEntryImpl {

    /**
     * {@return a symbolic descriptor for the dynamic constant's type}
     */
    default ClassDesc typeSymbol() {
        return Util.fieldTypeSymbol(nameAndType());
    }

    @Override
    default ConstantDesc constantValue() {
        return asSymbol();
    }

    /**
     * {@return the symbolic descriptor for the {@code invokedynamic} constant}
     */
    default DynamicConstantDesc<?> asSymbol() {
        return DynamicConstantDesc.ofNamed(bootstrap().bootstrapMethod().asSymbol(),
                                           name().stringValue(),
                                           typeSymbol(),
                                           bootstrap().arguments().stream()
                                                      .map(LoadableConstantEntry::constantValue)
                                                      .toArray(ConstantDesc[]::new));
    }

    /**
     * {@return the type of the constant}
     */
    @Override
    default TypeKind typeKind() {
        return TypeKind.fromDescriptor(type().stringValue());
    }
}
