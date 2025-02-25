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
import sun.reflect.generics.reflectiveObjects.TypeVariableImpl;

import java.lang.classfile.Signature;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.TypeVariable;
import java.util.List;

/**
 * A generic declaration info is a generic info that itself
 * declares type variables.
 *
 * @param <T> the owner type
 */
public abstract sealed class GenericDeclInfo<T extends GenericDeclaration> extends GenericInfo<T>
        permits ClassGenericInfo, ExecutableGenericInfo {
    private volatile @Stable TypeVariable<T>[] typeVars;

    GenericDeclInfo(T owner) {
        super(owner);
    }

    public TypeVariable<T>[] getTypeVariables() {
        return typeVars().clone();
    }

    TypeVariable<T>[] typeVars() {
        var ret = typeVars;
        if (ret != null)
            return ret;

        var params = typeParams();
        @SuppressWarnings("unchecked")
        TypeVariable<T>[] array = (TypeVariable<T>[])
                new TypeVariable<?>[params.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = resolve(params.get(i));
        }

        return typeVars = array;
    }

    abstract List<Signature.TypeParam> typeParams();

    public final TypeVariable<T> resolve(Signature.TypeParam typeParam) {
        var classBound = typeParam.classBound();
        var interfaceBounds = typeParam.interfaceBounds();
        Signature[] signatures = new Signature[(classBound.isPresent() ? 1 : 0)
                + interfaceBounds.size()];
        int i = 0;
        if (classBound.isPresent()) {
            signatures[i++] = classBound.get();
        }

        for (var sig : interfaceBounds) {
            signatures[i++] = sig;
        }

        return TypeVariableImpl.make(closestDeclaration, typeParam.identifier(),
                signatures, this);
    }

    // Prevents recursive initialization
    @Override
    TypeVariable<?> findTypeVariable(String name) {
        for (var tv : typeVars()) {
            if (tv.getName().equals(name)) {
                return tv;
            }
        }
        for (var decl = findParent(closestDeclaration);
                decl != null; decl = findParent(decl)) {
            var tv = findDeclaredTypeVariable(decl, name);
            if (tv != null)
                return tv;
        }
        throw new TypeNotPresentException(name, null);
    }
}
