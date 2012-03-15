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

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import com.apple.internal.jobjc.generator.model.types.NType.NObject;
import com.apple.internal.jobjc.generator.model.types.NType.NPointer;
import com.apple.internal.jobjc.generator.model.types.NType.NPrimitive;
import com.apple.internal.jobjc.generator.model.types.NType.NVoid;
import com.apple.internal.jobjc.generator.model.types.NType.NStruct;
import com.apple.internal.jobjc.generator.model.types.NType.NField;
import com.apple.internal.jobjc.generator.utils.NTypeMerger;
import com.apple.internal.jobjc.generator.utils.QA;
import com.apple.internal.jobjc.generator.utils.Fp.Pair;
import com.apple.internal.jobjc.generator.utils.NTypeMerger.MergeFailed;

public class Type implements Comparable<Type>{
    public static Type VOID = Type.getType("void", NVoid.inst(), null);
    public static Type VOID_PTR = Type.getType("void*", new NPointer(NVoid.inst()), null);

    final public String name;
    final public NType type32;
    final public NType type64;

    // HACK BS bug where some types have inconsistent definitions in the metadata,
    // e.g. (l / i) in some places and just (l) (and thus (l / l)) in others.
    // This is a mapping from the type name to a Type object with the correct definition.
    private static final Map<String, Type> exceptions;
    static {
        exceptions = new HashMap<String, Type>();
        exceptions.put("OSStatus", getType("OSStatus", new NPrimitive('l'), new NPrimitive('i'))); // (l / i) vs. (l)
        exceptions.put("CGFloat", getType("CGFloat", new NPrimitive('f'), new NPrimitive('d'))); // (f / d) vs. (f)
        exceptions.put("NSRect", getType("NSRect", getNSRectType(), getCGRectType())); // ({{_NSPoint}{_NSSize}} / {{CGPoint}{CGSize}}) vs. ({{_NSPoint}{_NSSize}})
        exceptions.put("NSPoint", getType("NSPoint", getNSPointType(), getCGPointType())); // (_NSPoint / CGPoint) vs. (_NSPoint)
        exceptions.put("NSSize", getType("NSSize", getNSSizeType(), getCGSizeType())); // (_NSSize / CGSize) vs. (_NSSize)
        exceptions.put("NSInteger", getType("NSInteger", new NPrimitive('i'), new NPrimitive('q'))); // (i / q) vs. (i)
        exceptions.put("NSPointArray", getType("NSPointArray", new NPointer(getNSPointType()), new NPointer(getCGPointType()))); // (^_NSPoint / ^CGPoint) vs. (^_NSPoint)
        exceptions.put("NSMultibyteGlyphPacking", getType("NSMultibyteGlyphPacking", new NPrimitive('I'), new NPrimitive('Q'))); // (I / Q) vs. (I)
        exceptions.put("CFTypeRef", getType("CFTypeRef", new NPointer(NVoid.inst()), new NPointer(NVoid.inst()))); // (^v, ^v) vs. (@, @)
    }

    public static Type getType(final String name, final NType t32, final NType t64){
        return TypeCache.inst().pingType(new Type(name, t32, t64));
    }

    private Type(final String name, final NType t32, final NType t64) {
        QA.nonNull(t32);
        this.name = cleanName(name);
        this.type32 = t32;
        this.type64 = t64 == null || t32.equals(t64) ? t32 : t64;
    }

    private JType _getJType;
    public JType getJType() {
        return _getJType!=null ? _getJType : (_getJType = TypeToJType.inst().getJTypeFor(TypeCache.inst().pingType(this)));
    }

    private String _toString;
    @Override public String toString() {
        return _toString != null ? _toString : (_toString = name + " " + new Pair(type32, type64).toString());
    }

    @Override public boolean equals(Object o){
        if(o==null || !(o instanceof Type)) return false;
        Type t = (Type) o;
        return QA.bothNullOrEquals(t.name, this.name)
        && t.type32.equals(this.type32)
        && t.type64.equals(this.type64);
    }

    @Override public int hashCode(){
        return (name == null ? 0 : name.hashCode())
        + type32.hashCode() + type64.hashCode();
    }

    public int compareTo(Type o) { return toString().compareTo(o.toString()); }

    public static Type merge(Type a, Type b) throws MergeFailed{
        if(a!=null && b==null) return a;
        if(a==null && b!=null) return b;
        if(QA.bothNullOrEquals(a, b)) return a;
        if (exceptions.containsKey(a.name)) return exceptions.get(a.name); // HACK BS bug
        if(a.name != null && b.name != null && !a.name.equals(b.name)){
            System.out.println("Merging:");
            System.out.println("\ta.....: " + a.toString());
            System.out.println("\tb.....: " + b.toString());
        }
        final Type merged = new Type(NTypeMerger.inst().mergeName(a.name, b.name),
                NTypeMerger.inst().merge(a.type32, b.type32),
                NTypeMerger.inst().merge(a.type64, b.type64));
        if(a.name != null && b.name != null && !a.name.equals(b.name)){
            System.out.println("\tmerged: " + merged.toString());
        }
        return merged;
    }

    // HACK BS bug where sometimes the name is declared as "id <A, B..." and sometimes it's "id<A,B..."
    public static String cleanName(String name){ return name == null ? null : name.replaceAll("\\s+", ""); }

    // HACK BS bug where NSRect has inconsistent definitions in the metadata
    // Methods return NTypes created according to the correct definitions below:
    //
    // {_NSRect="origin"{_NSPoint="x"f"y"f}"size"{_NSSize="width"f"height"f}} *** 32-bit
    // {CGRect="origin"{CGPoint="x"d"y"d}"size"{CGSize="width"d"height"d}} *** 64-bit

    private static NType getCGRectType() {
        List<NField> fields = new ArrayList<NField>();
        fields.add(new NField("origin", getCGPointType()));
        fields.add(new NField("size", getCGSizeType()));
        return new NStruct("CGRect", fields);
    }

    private static NType getNSRectType() {
        List<NField> fields = new ArrayList<NField>();
        fields.add(new NField("origin", getNSPointType()));
        fields.add(new NField("size", getNSSizeType()));
        return new NStruct("_NSRect", fields);
    }

    private static NType getCGPointType() {
        List<NField> fields = new ArrayList<NField>();
        fields.add(new NField("x", new NPrimitive('d')));
        fields.add(new NField("y", new NPrimitive('d')));
        return new NStruct("CGPoint", fields);
    }

    private static NType getNSPointType() {
        List<NField> fields = new ArrayList<NField>();
        fields.add(new NField("x", new NPrimitive('f')));
        fields.add(new NField("y", new NPrimitive('f')));
        return new NStruct("_NSPoint", fields);
    }

    private static NType getCGSizeType() {
        List<NField> fields = new ArrayList<NField>();
        fields.add(new NField("width", new NPrimitive('d')));
        fields.add(new NField("height", new NPrimitive('d')));
        return new NStruct("CGSize", fields);
    }

    private static NType getNSSizeType() {
        List<NField> fields = new ArrayList<NField>();
        fields.add(new NField("width", new NPrimitive('f')));
        fields.add(new NField("height", new NPrimitive('f')));
        return new NStruct("_NSSize", fields);
    }
}
