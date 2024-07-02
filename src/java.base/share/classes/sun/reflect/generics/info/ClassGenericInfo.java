/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package sun.reflect.generics.info;

import jdk.internal.vm.annotation.Stable;

import java.lang.classfile.ClassSignature;
import java.lang.classfile.Signature;
import java.lang.reflect.GenericSignatureFormatError;
import java.lang.reflect.Type;
import java.util.List;

import static java.lang.constant.ConstantDescs.CD_Object;

public final class ClassGenericInfo<T> extends GenericDeclInfo<Class<T>> {
    private static final ClassGenericInfo<Object> DUMMY = new ClassGenericInfo<>(Object.class,
            CD_Object.descriptorString());

    @SuppressWarnings("unchecked")
    public static <T> ClassGenericInfo<T> dummy() {
        return (ClassGenericInfo<T>) DUMMY;
    }

    private final ClassSignature signature;
    private volatile @Stable Type superclass;
    private volatile @Stable Type[] superInterfaces;

    public ClassGenericInfo(Class<T> owner, String signatureString) {
        super(owner);
        ClassSignature classSignature;
        try {
            classSignature = ClassSignature.parseFrom(signatureString);
        } catch (IllegalArgumentException ex) {
            throw new GenericSignatureFormatError(ex.getMessage());
        }
        this.signature = classSignature;
    }

    @Override
    List<Signature.TypeParam> typeParams() {
        return signature.typeParameters();
    }

    Type[] superInterfaces() {
        var superInterfaces = this.superInterfaces;
        if (superInterfaces != null)
            return superInterfaces;
        return this.superInterfaces = resolve(signature.superinterfaceSignatures());
    }

    public Type[] getSuperInterfaces() {
        return superInterfaces().clone();
    }

    public Type getSuperclass() {
        var superclass = this.superclass;
        if (superclass != null)
            return superclass;
        return this.superclass = resolve(signature.superclassSignature());
    }
}
