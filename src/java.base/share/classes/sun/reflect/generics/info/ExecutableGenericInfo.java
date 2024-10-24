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

import java.lang.classfile.MethodSignature;
import java.lang.classfile.Signature;
import java.lang.reflect.Executable;
import java.lang.reflect.GenericSignatureFormatError;
import java.lang.reflect.Type;
import java.util.List;

public final class ExecutableGenericInfo<T extends Executable> extends GenericDeclInfo<T> {
    private final MethodSignature signature;
    private volatile @Stable Type[] parameters;
    private volatile @Stable Type result;
    private volatile @Stable Type[] exceptions;

    public ExecutableGenericInfo(T owner, String signatureString) {
        super(owner);
        MethodSignature signature;
        try {
            signature = MethodSignature.parseFrom(signatureString);
        } catch (IllegalArgumentException ex) {
            throw new GenericSignatureFormatError(ex.getMessage());
        }
        this.signature = signature;
    }

    public Type[] getExceptions() {
        return exceptions().clone();
    }

    Type[] exceptions() {
        var exceptions = this.exceptions;
        if (exceptions != null)
            return exceptions;
        return this.exceptions = resolve(signature.throwableSignatures());
    }

    public Type getResult() {
        var result = this.result;
        if (result != null)
            return result;
        return this.result = resolve(signature.result());
    }

    public Type[] getParameters() {
        return parameters().clone();
    }

    Type[] parameters() {
        var parameters = this.parameters;
        if (parameters != null)
            return parameters;
        return this.parameters = resolve(signature.arguments());
    }

    @Override
    List<Signature.TypeParam> typeParams() {
        return signature.typeParameters();
    }
}
