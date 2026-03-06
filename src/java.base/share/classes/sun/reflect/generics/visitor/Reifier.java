/*
 * Copyright (c) 2003, 2026, Oracle and/or its affiliates. All rights reserved.
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

package sun.reflect.generics.visitor;


import java.lang.classfile.Signature;
import java.lang.constant.ConstantDescs;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.List;
import java.util.Optional;

import sun.invoke.util.Wrapper;
import sun.reflect.generics.factory.*;
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl;


/**
 * Visitor that converts AST to reified types.
 */
public final class Reifier {
    private final GenericsFactory factory;

    private Reifier(GenericsFactory f){
        factory = f;
    }

    private GenericsFactory getFactory(){ return factory;}

    /**
     * Factory method. The resulting visitor will convert an AST
     * representing generic signatures into corresponding reflective
     * objects, using the provided factory, {@code f}.
     * @param f - a factory that can be used to manufacture reflective
     * objects returned by this visitor
     * @return A visitor that can be used to reify ASTs representing
     * generic type information into reflective objects
     */
    public static Reifier make(GenericsFactory f){
        return new Reifier(f);
    }

    public Type reify(Signature type) {
        return switch (type) {
            case Signature.BaseTypeSig base -> Wrapper.forBasicType(base.baseType()).primitiveType();
            case Signature.ArrayTypeSig array -> factory.makeArrayType(reify(array.componentSignature()));
            case Signature.TypeVarSig typeVar -> factory.findTypeVariable(typeVar.identifier());
            case Signature.ClassTypeSig clazz -> recurClassConstruction(clazz);
        };
    }

    public Type reify(Signature.TypeArg arg) {
        return switch (arg) {
            case Signature.TypeArg.Unbounded _ ->
                    factory.makeWildcard(new Signature[] { Signature.of(ConstantDescs.CD_Object) }, new Signature[0]);
            case Signature.TypeArg.Bounded b -> switch (b.wildcardIndicator()) {
                case NONE -> reify(b.boundType());
                case EXTENDS -> factory.makeWildcard(new Signature[] {b.boundType()}, new Signature[0]);
                case SUPER -> factory.makeWildcard(new Signature[0], new Signature[] {b.boundType()});
            };
        };
    }

    public TypeVariable<?> reify(Signature.TypeParam param) {
        Optional<Signature.RefTypeSig> superclass = param.classBound();
        List<Signature.RefTypeSig> superinterfaces = param.interfaceBounds();
        int count = (superclass.isEmpty() ? 0 : 1) + superinterfaces.size();
        Signature[] array;
        if (superclass.isPresent()){
            array = new Signature[count];
            array[0] = superclass.get();
            for (int i = 1; i < array.length; i++) {
                array[i] = superinterfaces.get(i - 1);
            }
        } else {
            array = superinterfaces.toArray(new Signature[0]);
        }
        return factory.makeTypeVariable(param.identifier(), array);
    }

    // Helper method. Visits an array of TypeArgument and produces
    // reified Type array.
    private Type[] reifyTypeArguments(List<Signature.TypeArg> tas) {
        Type[] ts = new Type[tas.size()];
        for (int i = 0; i < ts.length; i++) {
            ts[i] = reify(tas.get(i));
        }
        return ts;
    }

    private Type recurClassConstruction(Signature.ClassTypeSig ct) {
        Type outer;
        Class<?> erasedOuter;
        if (ct.outerType().isPresent()) {
            outer = recurClassConstruction(ct.outerType().get());
            erasedOuter = outer instanceof Class<?> c ? c : ((ParameterizedTypeImpl) outer).getRawType();
        } else {
            outer = null;
            erasedOuter = null;
        }

        String erasedName;
        if (erasedOuter == null) {
            erasedName = ct.className().replace('/', '.');
        } else {
            erasedName = erasedOuter.getName() + '$' + ct.className(); // No more / in nested names
        }

        Type c = getFactory().makeNamedType(erasedName);

        if (!(outer instanceof ParameterizedTypeImpl) && ct.typeArgs().isEmpty()) {
            // No type args, return plain class
            return c;
        }

        Type[] pts = reifyTypeArguments(ct.typeArgs());
        return factory.makeParameterizedType(c, pts, outer);
    }

}
