/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.apple.internal.jobjc.generator.model.types;

import com.apple.internal.jobjc.generator.model.CFType;
import com.apple.internal.jobjc.generator.model.Clazz;
import com.apple.internal.jobjc.generator.model.Opaque;
import com.apple.internal.jobjc.generator.model.Struct;
import com.apple.internal.jobjc.generator.model.coders.ComplexCoderDescriptor;
import com.apple.internal.jobjc.generator.model.types.JType.JCFType;
import com.apple.internal.jobjc.generator.model.types.JType.JClass;
import com.apple.internal.jobjc.generator.model.types.JType.JObject;
import com.apple.internal.jobjc.generator.model.types.JType.JOpaque;
import com.apple.internal.jobjc.generator.model.types.JType.JPointer;
import com.apple.internal.jobjc.generator.model.types.JType.JPrimitive;
import com.apple.internal.jobjc.generator.model.types.JType.JSelector;
import com.apple.internal.jobjc.generator.model.types.JType.JStruct;
import com.apple.internal.jobjc.generator.model.types.JType.JUnknown;
import com.apple.internal.jobjc.generator.model.types.JType.JVoid;
import com.apple.internal.jobjc.generator.model.types.NType.NClass;
import com.apple.internal.jobjc.generator.model.types.NType.NObject;
import com.apple.internal.jobjc.generator.model.types.NType.NPointer;
import com.apple.internal.jobjc.generator.model.types.NType.NPrimitive;
import com.apple.internal.jobjc.generator.model.types.NType.NSelector;
import com.apple.internal.jobjc.generator.model.types.NType.NStruct;
import com.apple.internal.jobjc.generator.model.types.NType.NVoid;
import com.apple.internal.jobjc.generator.utils.Fp.CacheMap;
import com.apple.internal.jobjc.generator.utils.Fp.Dispatcher;
import com.apple.internal.jobjc.generator.utils.Fp.Map0;

public class TypeToJType {
    private static TypeToJType INST = new TypeToJType();
    public static TypeToJType inst(){ return INST; }

    private CacheMap<Type,JType> cache = new CacheMap<Type,JType>();
    public JType getJTypeFor(final Type type){
        return cache.get(type, new Map0<JType>(){
            public JType apply() {
                try {
                    return Dispatcher.dispatch(TypeToJType.this.getClass(), TypeToJType.this, "accept", type, type.type32, type.type64);
                } catch (NoSuchMethodException e) {
                    return new JUnknown(type);
                }
            }});
    }

    protected JType accept(Type type, NObject nt32, NObject nt64){
        if ("id".equals(type.name)) return JObject.ID_TYPE;

        final String className = type.name.replaceAll("\\*$", "");
        assert !className.endsWith("*");

        final Clazz clazz = TypeCache.inst().getClassForName(className);
        if (clazz == null) return new JUnknown(type);
        // TODO Instead of JUnknown, ID_TYPE might be more appropriate. Investigate.

        return new JObject(type, clazz);
    }

    protected JType accept(Type type, NPointer nt32, NPointer nt64){
        final CFType cfType = TypeCache.inst().getCFTypeForName(type.name);
        if(cfType != null) return new JCFType(cfType);

        final Opaque opaque = TypeCache.inst().getOpaqueForName(type.name);
        if(opaque != null) return new JOpaque(opaque);

        if("void*".equals(type.name)) return JPointer.VOID_PTR;

        if(type.name != null && type.name.endsWith("*")){
            final String subDeclaredType = type.name.substring(0, type.name.length() - 1);
            final Type subType = TypeCache.inst().getTypeByName(subDeclaredType);
            if (subType == null) return new JUnknown(type);
            // TODO Instead of JUnknown, VOID_PTR might be a good fallback. Investigate.

            final JType javaType = TypeToJType.inst().getJTypeFor(subType).getParameterizableType();

            final JPointer pointer = new JPointer(javaType);
            return pointer;
        }

        return new JUnknown(type);
    }

    protected JType accept(Type type, NPrimitive nt32, NPrimitive nt64){
        final ComplexCoderDescriptor coderDesc = ComplexCoderDescriptor.getCoderDescriptorFor(type.type32, type.type64);
        if (coderDesc == null) return null;
        return new JPrimitive(type, coderDesc);
    }

    protected JType accept(Type type, NVoid nt32, NVoid nt64){
        return JVoid.INST;
    }

    protected JType accept(Type type, NSelector nt32, NSelector nt64){
        return JSelector.INST;
    }

    protected JType accept(Type type, NClass nt32, NClass nt64){
        return JClass.INST;
    }

    protected JType accept(Type type, NStruct nt32, NStruct nt64){
        Struct st = TypeCache.inst().getStructForName(type.name);
        return st != null ? new JStruct(st) : new JUnknown(type);
        // TODO We could probably generate a struct here based on the type. But we need access to its parent framework.
        // Maybe we could use a fallback anonymous struct, but we need the SIZEOF.
    }
}
