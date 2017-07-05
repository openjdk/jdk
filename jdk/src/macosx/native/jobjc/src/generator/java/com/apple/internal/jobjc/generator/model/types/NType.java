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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.apple.internal.jobjc.generator.Utils;
import com.apple.internal.jobjc.generator.model.coders.PrimitiveCoderDescriptor;
import com.apple.internal.jobjc.generator.utils.Fp;
import com.apple.internal.jobjc.generator.utils.NTypePrinter;
import com.apple.internal.jobjc.generator.utils.QA;
import com.apple.jobjc.JObjCRuntime.Width;

/**
 * NType (Native Type) bridges the type and type64 attributes in BridgeSupport.
 *
 * For example:
 *
 * <pre>
 *   type="c"                 // BridgeSupport attribute
 *   (NPrimitive type: 'c')   // Java object (in sexp form here, for readability)
 *
 *   type="^v"
 *   (NPointer subject: (NVoid))
 *
 *   type="{foo_t="a"c"b"b8"c"[32^v]}"
 *   (NStruct
 *     name: "foo_t"
 *     fields:
 *       (List<NField>
 *         0: (NField name:"a" type: (NPrimitive type: 'c'))
 *         1: (NField name:"b" type: (NBitfield size: 8))
 *         2: (NField name:"c" type:
 *              (NArray size: 32
 *                      type: (NPointer subject: (NVoid))))))
 * </pre>
 */
public abstract class NType implements Comparable<NType>{
    public final Map<Width, Integer> sizeof;

    public NType(Map<Width, Integer> sizeof) {
        this.sizeof = sizeof;
    }

    public NType(){
        this(new HashMap<Width, Integer>());
    }

    public NType(int sz32, int sz64){
        this();
        this.sizeof.put(Width.W32, sz32);
        this.sizeof.put(Width.W64, sz32);
    }

    public int sizeof32(){ return sizeof.get(Width.W32); }
    public int sizeof64(){ return sizeof.get(Width.W64); }

    protected abstract boolean equals2(NType nt);

    private String _toString;
    @Override public String toString(){ return _toString != null ? _toString : (_toString = NTypePrinter.inst().print(this)); }
    @Override public boolean equals(Object o) {
        return o!=null && (o==this || (getClass().isInstance(o)
                && this.sizeof.equals(((NType) o).sizeof)
                && equals2((NType) o)));
    }
    public int compareTo(NType o){ return toString().compareTo(o.toString()); }

    // ** NType subclasses
    // -------------------

    public static class NBitfield extends NType{
        public final int length;

        public NBitfield(int length){
            super(-1, -1);
            this.length = length;
        }

        @Override protected boolean equals2(NType nt) { return ((NBitfield) nt).length == length; }
        @Override public int hashCode() { return Integer.valueOf(length).hashCode(); }
    }

    public static class NPrimitive extends NType{
        public static Collection<Character> CODES = Arrays.asList(
                'B', 'c', 'C', 's', 'S', 'i', 'I', 'l', 'L', 'q', 'Q', 'f', 'd');

        public final char type;

        protected NPrimitive(char c){
            super(PrimitiveCoderDescriptor.createCoderDescriptorFor(c).getCoder().sizeof(Width.W32),
                    PrimitiveCoderDescriptor.createCoderDescriptorFor(c).getCoder().sizeof(Width.W64));
            type = c;
        }
        private static final Map<Character, NPrimitive> cache = new HashMap<Character, NPrimitive>();
        public static final NPrimitive inst(final char c){
            if(!cache.containsKey(c)) cache.put(c, new NPrimitive(c));
            return cache.get(c);
        }

        @Override protected boolean equals2(NType nt) { return ((NPrimitive)nt).type == type; }
        @Override public int hashCode() { return Character.valueOf(type).hashCode(); }
    }

    public static class NVoid extends NType{
        protected NVoid(){ super(); }
        private final static NVoid INST = new NVoid();
        public static NVoid inst() { return INST; }

        @Override protected boolean equals2(NType nt) { return true; }
    }

    public static class NPointer extends NType{
        public final NType subject;

        public NPointer(NType subject){
            super(4, 8);
            QA.nonNull(subject);
            this.subject = subject;
        }

        @Override protected boolean equals2(NType nt) { return ((NPointer)nt).subject.equals(subject); }
        @Override public int hashCode() { return subject.hashCode(); }
    }

    public static class NObject extends NType{
        protected NObject(){ super(4, 8); }
        private final static NObject INST = new NObject();
        public static NObject inst() { return INST; }

        @Override protected boolean equals2(NType nt) { return true; }
    }

    public static class NClass extends NType{
        protected NClass(){ super(4, 8); }
        private final static NClass INST = new NClass();
        public static NClass inst() { return INST; }

        @Override protected boolean equals2(NType nt) { return true; }
    }

    public static class NSelector extends NType{
        protected NSelector(){ super(4, 8); }
        private final static NSelector INST = new NSelector();
        public static NSelector inst() { return INST; }

        @Override protected boolean equals2(NType nt) { return true;}
    }

    public static class NField{
        public final Map<Width,Integer> offset;
        public final String name;
        public final NType type;

        public NField(String name, NType type, Map<Width,Integer> offset) {
            QA.nonNull(name, type, offset);
            this.name = name;
            this.type = type;
            this.offset = offset;
        }

        public NField(String name, NType type) {
            this(name, type, new HashMap());
        }

        public int offset32(){ return offset.get(Width.W32); }
        public int offset64(){ return offset.get(Width.W64); }

        @Override public int hashCode() { return name.hashCode() + type.hashCode(); }
        @Override public boolean equals(Object o) {
            return o!=null && (o==this ||
                    (o instanceof NField
                            && this.offset.equals(((NField) o).offset)
                            && ((NField) o).name.equals(this.name)
                            && ((NField) o).type.equals(this.type)));
        }
    }

    public static class NStruct extends NType{
        public final String name;
        public final List<NField> fields;

        public NStruct(String name, List<NField> fields, Map<Width,Integer> sizeof){
            super(sizeof);
            QA.nonNull(name, fields);
            this.name = name;
            this.fields = fields;
        }

        public NStruct(String name, List<NField> fields){
            super();
            QA.nonNull(name, fields);
            this.name = name;
            this.fields = fields;
        }

        @Override protected boolean equals2(NType nt) {
            return ((NStruct)nt).name.equals(name) && ((NStruct)nt).fields.equals(fields);
        }

        @Override public int hashCode() { return name.hashCode() + fields.hashCode(); }
    }

    // A Union is like a Struct, but the offset of every field is 0.
    public static class NUnion extends NStruct{
        public NUnion(String concreteName, List<NField> fields){
            super(concreteName, fields);
            assert Fp.all(hasZeroOffsets, fields) : Utils.joinWComma(fields);
        }

        public NUnion(String name, List<NField> fields, Map<Width,Integer> sizeof) {
            super(name, fields, sizeof);
            assert Fp.all(hasZeroOffsets, fields) : Utils.joinWComma(fields);
        }

        public static final Fp.Map1<NField,Boolean> hasZeroOffsets = new Fp.Map1<NField,Boolean>(){
            public Boolean apply(NField a) {
                for(int i : a.offset.values())
                    if(i != 0)
                        return false;
                return true;
            }};
        public static final Fp.Map1<NField,NField> zeroOffsets = new Fp.Map1<NField,NField>(){
            public NField apply(NField a) {
                Map<Width,Integer> off = new HashMap();
                for(Width w : a.offset.keySet())
                    off.put(w, 0);
                return new NField(a.name, a.type, off);
            }};
    }

    public static class NArray extends NType{
        public final int length;
        public final NType type;

        public NArray(int length, NType type){
            QA.nonNull(type);
            this.length = length;
            this.type = type;
        }

        @Override protected boolean equals2(NType nt) { return ((NArray)nt).length == length && ((NArray)nt).type.equals(type); }
        @Override public int hashCode(){ return Long.valueOf(length).hashCode() + type.hashCode(); }
    }

    // Seems to be used for callbacks
    public static class NUnknown extends NType{
        protected NUnknown(){ super(); }
        private final static NUnknown INST = new NUnknown();
        public static NUnknown inst() { return INST; }

        @Override protected boolean equals2(NType nt) { return true;}
    }
}
