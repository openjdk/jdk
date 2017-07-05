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
package com.apple.internal.jobjc.generator.utils;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.apple.internal.jobjc.generator.model.types.NType;
import com.apple.internal.jobjc.generator.model.types.NType.NArray;
import com.apple.internal.jobjc.generator.model.types.NType.NBitfield;
import com.apple.internal.jobjc.generator.model.types.NType.NClass;
import com.apple.internal.jobjc.generator.model.types.NType.NField;
import com.apple.internal.jobjc.generator.model.types.NType.NObject;
import com.apple.internal.jobjc.generator.model.types.NType.NPointer;
import com.apple.internal.jobjc.generator.model.types.NType.NPrimitive;
import com.apple.internal.jobjc.generator.model.types.NType.NSelector;
import com.apple.internal.jobjc.generator.model.types.NType.NStruct;
import com.apple.internal.jobjc.generator.model.types.NType.NUnion;
import com.apple.internal.jobjc.generator.model.types.NType.NUnknown;
import com.apple.internal.jobjc.generator.model.types.NType.NVoid;
import com.apple.internal.jobjc.generator.utils.Fp.Dispatcher;
import com.apple.internal.jobjc.generator.utils.Fp.Map2;
import com.apple.jobjc.JObjCRuntime.Width;

/**
 * Merges two NTypes. All merge does is fill out missing information. It doesn't choose the larger primitive when there's a conflict or anything like that.
 *
 * Example:
 *<pre>
 * a: {_NSRect={_NSPoint="x"f"y"f}"size"{_NSSize=ff}}
 * b: {_NSRect="origin"{_NSPoint=ff}{_NSSize="width"f"height"f}}
 * c: {_NSRect="origin"{_NSPoint="x"f"y"f}"size"{_NSSize="width"f"height"f}}
 *</pre>
 */
public class NTypeMerger {
    public static class MergeFailed extends RuntimeException{
        public MergeFailed(String reason, Object a, Object b){
            super(reason
                    + " -- (" + a.getClass().getSimpleName() + ") a: " + a
                    + " -- (" + b.getClass().getSimpleName() + ") b: " + b);
        }
    }

    private static NTypeMerger INST = new NTypeMerger();
    public static NTypeMerger inst(){ return INST; }

    /**
     * Merge a and b.
     */
    public NType merge(NType a, NType b) throws MergeFailed{
        if(a!=null && b==null) return a;
        if(a==null && b!=null) return b;
        if(a==null && b==null) return null;
        if(a.equals(b)) return a;
        try {
            return Dispatcher.dispatch(getClass(), this, "accept", a, b);
        } catch (NoSuchMethodException e) {
            throw new MergeFailed("a and b are of different NType", a, b);
        }
    }

    private static Collection<String> emptyNames = Arrays.asList(null, "", "?");
    /**
     * Merge two identifiers:
     *  - If they're equal, return one.
     *  - If one is null, "", "?", return the other one.
     *  - else throw MergeFailed
     *
     *  Exception: Due to a bug in BridgeSupport, this will return
     *  a (the first arg) instead of throwing MergeFailed.
     */
    public String mergeName(String a, String b) throws MergeFailed{
        if(QA.bothNullOrEquals(a, b)) return a;
        if(emptyNames.contains(a) && !emptyNames.contains(b)) return b;
        if(emptyNames.contains(b) && !emptyNames.contains(a)) return a;
        return a; // HACK BS bug #5954843
//        throw new MergeFailed("a and b have different names");
    }

    private Map mergeMap(Map a, Map b) throws MergeFailed{
        if(a.equals(b)) return a;
        Map ret = new HashMap();
        Set keys = new HashSet(Fp.append(a.keySet(), b.keySet()));
        for(Object key : keys){
            Object ai = a.get(key);
            Object bi = b.get(key);
            if(ai != null && bi == null) ret.put(key, ai);
            else if(ai == null && bi != null) ret.put(key, bi);
            else if(ai.equals(bi)) ret.put(key, ai);
            else throw new MergeFailed("a and b are different", ai, bi);
        }
        return ret;
    }

    public Map<Width,Integer> mergeSizeOf(Map<Width,Integer> a, Map<Width,Integer> b) throws MergeFailed{
        return mergeMap(a, b);
    }

    public Map<Width,Integer> mergeOffset(Map<Width,Integer> a, Map<Width,Integer> b) throws MergeFailed{
        return mergeMap(a, b);
    }

    //

    private void mustEqual(NType a, NType b){
        if(!a.equals(b)) throw new MergeFailed("a must equal b", a, b);
    }

    protected NType accept(NBitfield a, NBitfield b) {
        mustEqual(a, b);
        return a;
    }

    protected NType accept(NPrimitive a, NPrimitive b) {
        mustEqual(a, b);
        return a;
    }

    protected NType accept(NPointer a, NPointer b) {
        return new NPointer(NTypeMerger.inst().merge(a.subject, b.subject));
    }

    // Merge structs

    protected NField mergeNFields(NField a, NField b) {
        return new NField(mergeName(a.name, b.name),
                NTypeMerger.inst().merge(a.type, b.type),
                mergeOffset(a.offset, b.offset));
    }

    protected NStruct mergeStructs(NStruct a, NStruct b){
        if(a.fields.size() != b.fields.size())
            throw new MergeFailed("a and b have different numbers of fields", a, b);

        List<NField> fields = Fp.map2(new Map2<NField,NField,NField>(){
            public NField apply(NField f32, NField f64) { return mergeNFields(f32, f64); }
        }, a.fields, b.fields);

        return new NStruct(mergeName(a.name, b.name),
                fields, mergeSizeOf(a.sizeof, b.sizeof));
    }

    protected NType accept(NStruct a, NStruct b) {
        return mergeStructs(a, b);
    }

    protected NType accept(NUnion a, NUnion b) {
        NStruct nst = mergeStructs(a, b);
        return new NUnion(nst.name, Fp.map(NUnion.zeroOffsets, nst.fields), nst.sizeof);
    }

    protected NType accept(NArray a, NArray b) {
        if(a.length != b.length)
            throw new MergeFailed("a and b are of different sizes", a, b);
        return new NArray(a.length, NTypeMerger.inst().merge(a.type, b.type));
    }

    protected NType accept(NVoid a, NVoid b) { return NVoid.inst(); }
    protected NType accept(NObject a, NObject b) { return NObject.inst(); }
    protected NType accept(NClass a, NClass b) { return NClass.inst(); }
    protected NType accept(NSelector a, NSelector b) { return NSelector.inst(); }
    protected NType accept(NUnknown a, NUnknown b) { return NUnknown.inst(); }
}
