/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.apple.internal.jobjc.generator.model.CFType;
import com.apple.internal.jobjc.generator.model.Clazz;
import com.apple.internal.jobjc.generator.model.Framework;
import com.apple.internal.jobjc.generator.model.Opaque;
import com.apple.internal.jobjc.generator.model.Struct;
import com.apple.internal.jobjc.generator.utils.Fp.Pair;

/**
 * Central store for types found in the frameworks.
 */
public class TypeCache {
    private static TypeCache INST = new TypeCache();
    public static TypeCache inst(){ return INST; }
    protected TypeCache(){}

    /**
     * When a new Type is discovered, pass it through here to
     * hit the cache, potentially merge with other types, etc.
     *
     * Always do:
     *
     *   Type type = TypeCache.inst().pingType(new Type(a,b,c));
     *
     * because this should return a better merge for you.
     */
    public Type pingType(final Type type_){
        Type typex = type_;

        // XXX Exception for void* clashes: void* (^{OpaqueCMProfileRef}), void* (^{X}), etc
        if("void*".equals(typex.name) && getTypeByName(typex.name)!=null)
            return getTypeByName(typex.name);

        if(typex.name != null)
            typex = Type.merge(typex, getTypeByName(typex.name));
        else // type.name == null
            typex = Type.merge(typex, getTypeByNTypes(new Pair(typex.type32, typex.type64)));
        putTypeByName(typex.name, typex);
        putTypeByNTypes(new Pair(typex.type32, typex.type64), typex);
        return typex;
    }

    public final Map<String, Type> typesByName = new HashMap<String, Type>();
    public Type getTypeByName(final String name) { return typesByName.get(Type.cleanName(name)); }
    public void putTypeByName(String name, Type type) { if(name!=null) typesByName.put(name, type);    }

    public final Map<Pair<NType,NType>, Type> typesByNTypes = new HashMap<Pair<NType,NType>, Type>();
    public Type getTypeByNTypes(Pair<NType,NType> pair) { return typesByNTypes.get(pair); }
    public void putTypeByNTypes(Pair<NType,NType> pair, Type type) { if(pair!=null) typesByNTypes.put(pair, type); }

    private final Map<String, Clazz> classesByName = new HashMap<String, Clazz>();
    private final Map<String, Struct> structsByName = new HashMap<String, Struct>();
    private final Map<String, CFType> cfTypesByName = new HashMap<String, CFType>();
    private final Map<String, Opaque> opaquesByName = new HashMap<String, Opaque>();

    public void load(final List<Framework> frameworks) {
        for (final Framework framework : frameworks) {
            for (final Clazz obj : framework.classes) {
                final Clazz previous = classesByName.put(obj.name, obj);
                if(previous != null)
                    throw new RuntimeException(String.format(
                            "TypeCache: naming collision: class name: %1$-10s -- framework1: %2$-10s -- framework2: %3$-10s \n",
                            obj.name, obj.parent.name, previous.parent.name));
            }

            for (final Struct obj : framework.structs) {
                final Struct previous = structsByName.put(obj.name, obj);
                if(previous != null)
                    throw new RuntimeException(String.format(
                            "TypeCache: naming collision: name: %1$-10s -- type1: %2$-10s -- type2: %3$-10s \n",
                            obj.name, obj.type, previous.type));
            }

            for (final CFType obj : framework.cfTypes) {
                final CFType previous = cfTypesByName.put(obj.name, obj);
                if(previous != null)
                    throw new RuntimeException(String.format(
                            "TypeCache: naming collision: name: %1$-10s -- type1: %2$-10s -- type2: %3$-10s \n",
                            obj.name, obj.type, previous.type));
            }

            for (final Opaque obj : framework.opaques) {
                final Opaque previous = opaquesByName.put(obj.name, obj);
                if(previous != null)
                    throw new RuntimeException(String.format(
                            "TypeCache: naming collision: name: %1$-10s -- type1: %2$-10s -- type2: %3$-10s \n",
                            obj.name, obj.type, previous.type));
            }
        }
    }

    public Collection<Clazz> getAllClasses() { return classesByName.values(); }
    public Clazz getClassForName(final String className) { return classesByName.get(className); }
    public Struct getStructForName(final String declaredType) { return structsByName.get(declaredType); }
    public CFType getCFTypeForName(final String declaredType) { return cfTypesByName.get(declaredType); }
    public Opaque getOpaqueForName(final String declaredType) { return opaquesByName.get(declaredType); }

    final Set<Type> unknownTypes = new TreeSet<Type>();
    public Set<Type> getUnknownTypes() { return unknownTypes; }
}
