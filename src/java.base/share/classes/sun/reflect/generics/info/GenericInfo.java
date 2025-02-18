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

import sun.invoke.util.Wrapper;
import sun.reflect.generics.reflectiveObjects.GenericArrayTypeImpl;
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl;
import sun.reflect.generics.reflectiveObjects.WildcardTypeImpl;

import java.lang.classfile.Signature;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.List;
import java.util.Objects;

import static java.lang.constant.ConstantDescs.CD_Object;

/**
 * A generic info is a context for signature resolution.
 * Its subclasses resolve different signatures on demand.
 *
 * @param <T> the enclosing generic declaration type
 */
public abstract sealed class GenericInfo<T extends GenericDeclaration>
        permits FieldGenericInfo, GenericDeclInfo {
    final T closestDeclaration;

    GenericInfo(T closestDecl) {
        this.closestDeclaration = Objects.requireNonNull(closestDecl);
    }

    public final Type resolve(Signature signature) {
        return switch (signature) {
            case Signature.BaseTypeSig baseTypeSig -> Wrapper
                    .forBasicType(baseTypeSig.baseType())
                    .primitiveType();
            case Signature.ArrayTypeSig arrayTypeSig -> {
                var component = resolve(arrayTypeSig.componentSignature());
                yield component instanceof Class<?> cl
                        ? cl.arrayType()
                        : GenericArrayTypeImpl.make(component);
            }
            case Signature.TypeVarSig typeVarSig -> findTypeVariable(typeVarSig.identifier());
            case Signature.ClassTypeSig classTypeSig -> resolve(classTypeSig);
        };
    }

    public final Type[] resolve(List<? extends Signature> signatures) {
        Type[] ret = new Type[signatures.size()];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = resolve(signatures.get(i));
        }
        return ret;
    }

    private Type resolve(Signature.ClassTypeSig classTypeSig) {
        var outerSig = classTypeSig.outerType().orElse(null);
        Type owner;
        String rawName;
        if (outerSig != null) {
            owner = resolve(outerSig);
            rawName = (owner instanceof Class<?> cl ? cl.getName()
                    : ((Class<?>) ((ParameterizedType) owner).getRawType()).getName())
                    + "$" + classTypeSig.className();
        } else {
            owner = null;
            rawName = classTypeSig.className().replace('/', '.');
        }

        Class<?> raw;
        try {
            raw = Class.forName(rawName, false, loader());
        } catch (ClassNotFoundException ex) {
            throw new TypeNotPresentException(rawName, ex);
        }

        var typeArgs = classTypeSig.typeArgs();
        if (typeArgs.isEmpty() && !(owner instanceof ParameterizedType)) {
            return raw;
        }
        return ParameterizedTypeImpl.make(raw, resolveTypeArgs(typeArgs), owner);
    }

    @SuppressWarnings("unchecked")
    public static <T extends GenericDeclaration> TypeVariable<T>[] emptyTypeVars() {
        return (TypeVariable<T>[]) EMPTY_TYPE_VARS;
    }

    private static final TypeVariable<?>[] EMPTY_TYPE_VARS = new TypeVariable<?>[0];
    public static final Type[] EMPTY_TYPE_ARRAY = new Type[0];
    public static final Class<?>[] EMPTY_CLASS_ARRAY = new Class<?>[0];
    private static final Signature[] SIG_OBJECT_ARRAY = new Signature[] { Signature.of(CD_Object) };
    private static final Signature[] EMPTY_SIG_ARRAY = new Signature[0];

    private Type[] resolveTypeArgs(List<Signature.TypeArg> typeArgs) {
        if (typeArgs.isEmpty()) {
            return EMPTY_TYPE_ARRAY;
        }
        int n = typeArgs.size();
        Type[] ret = new Type[n];
        for (int i = 0; i < n; i++) {
            ret[i] = resolve(typeArgs.get(i));
        }
        return ret;
    }

    private Type resolve(Signature.TypeArg typeArg) {
        return switch (typeArg) {
            case Signature.TypeArg.Bounded b -> switch (b.wildcardIndicator()) {
                case NONE -> resolve(b.boundType());
                case SUPER -> WildcardTypeImpl.make(SIG_OBJECT_ARRAY, new Signature[]{b.boundType()}, this);
                case EXTENDS -> WildcardTypeImpl.make(new Signature[]{b.boundType()}, EMPTY_SIG_ARRAY, this);
            };
            case Signature.TypeArg.Unbounded _ -> WildcardTypeImpl.make(SIG_OBJECT_ARRAY, EMPTY_SIG_ARRAY, this);
        };
    }

    ClassLoader loader() {
        if (closestDeclaration instanceof Executable exec) {
            return exec.getDeclaringClass().getClassLoader();
        }
        return ((Class<?>) closestDeclaration).getClassLoader();
    }

    TypeVariable<?> findTypeVariable(String name) {
        for (GenericDeclaration decl = closestDeclaration; decl != null; decl = findParent(decl)) {
            var tv = findDeclaredTypeVariable(decl, name);
            if (tv != null) {
                return tv;
            }
        }
        throw new TypeNotPresentException(name, null);
    }

    static GenericDeclaration findParent(GenericDeclaration decl) {
        if (decl instanceof Class<?> cl) {
            Method m = cl.getEnclosingMethod();
            if (m != null)
                return m;

            Constructor<?> ctor = cl.getEnclosingConstructor();
            if (ctor != null)
                return ctor;

            return cl.getEnclosingClass(); // may return null
        }
        return ((Executable) decl).getDeclaringClass();
    }

    static TypeVariable<?> findDeclaredTypeVariable(GenericDeclaration decl, String name) {
        for (var tv : decl.getTypeParameters()) {
            if (tv.getName().equals(name)) {
                return tv;
            }
        }
        return null;
    }
}
