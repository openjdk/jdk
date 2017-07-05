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
package com.apple.internal.jobjc.generator.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.apple.internal.jobjc.generator.model.types.NType;
import com.apple.internal.jobjc.generator.model.types.NType.NArray;
import com.apple.internal.jobjc.generator.model.types.NType.NBitfield;
import com.apple.internal.jobjc.generator.model.types.NType.NClass;
import com.apple.internal.jobjc.generator.model.types.NType.NObject;
import com.apple.internal.jobjc.generator.model.types.NType.NPointer;
import com.apple.internal.jobjc.generator.model.types.NType.NPrimitive;
import com.apple.internal.jobjc.generator.model.types.NType.NSelector;
import com.apple.internal.jobjc.generator.model.types.NType.NStruct;
import com.apple.internal.jobjc.generator.model.types.NType.NUnion;
import com.apple.internal.jobjc.generator.model.types.NType.NUnknown;
import com.apple.internal.jobjc.generator.model.types.NType.NVoid;

/**
 * NTypeParser (Native Type Parser) parses type & type64 attributes from BridgeSupport.
 *
 * See Obj-C Language: Type Encodings
 */
public abstract class NTypeParser {
    // ** Parser entry point

    private static Map<String, NType> cached = new HashMap<String, NType>();
    public static NType parseFrom(String s) {
        if(!cached.containsKey(s)) cached.put(s, parseFrom(new StringStream(s)));
        return cached.get(s);
    }

    // ** Parser driver

    private static List<NTypeParser> PARSERS = new ArrayList<NTypeParser>(
            Arrays.asList(new NBitfieldParser(), new NPrimitiveParser(), new NVoidParser(),
            new NPointerParser(), new NStructParser(), new NUnionParser(),
            new NObjectParser(), new NClassParser(), new NSelectorParser(),
            new NArrayParser(), new NUnknownParser(), new NSpecifierParser()));

    protected static NType parseFrom(StringStream ss) {
        if(ss.left() == 0)
            return null;
        try{
            for(NTypeParser nt : PARSERS)
                if(nt.parsePossible(ss))
                    return nt.parse(ss);
        }
        catch(RuntimeException x){
            throw new RuntimeException("Exception while parsing '" + ss.remainingToString()
                    + "' from '" + ss.toString() + "'", x);
        }
        throw new RuntimeException("Found no parser for '" + ss.remainingToString()
                + "' from '" + ss.toString() + "'");
    }

    // ** Methods for parsers

    protected abstract boolean parsePossible(StringStream ss);
    protected abstract NType parse(StringStream ss);

    // ** Individual parsers

    public static class NBitfieldParser extends NTypeParser{
        @Override protected boolean parsePossible(StringStream ss) {
            return ss.left() >= 2 && ss.peekAt(0) == 'b' && Character.isDigit(ss.peekAt(1));
        }

        @Override protected NType parse(StringStream ss) {
            assert parsePossible(ss);
            ss.eat('b');
            return new NBitfield(Integer.parseInt(ss.readWhileDigits()));
        }
    }

    public static class NPrimitiveParser extends NTypeParser{
        @Override protected boolean parsePossible(StringStream ss) {
            return NPrimitive.CODES.contains(ss.peek());
        }

        @Override protected NType parse(StringStream ss) {
            assert parsePossible(ss);
            return NPrimitive.inst(ss.read());
        }
    }

    public static class NVoidParser extends NTypeParser{
        @Override protected boolean parsePossible(StringStream ss) {
            return ss.peek() == 'v';
        }

        @Override protected NType parse(StringStream ss) {
            ss.eat('v');
            return NVoid.inst();
        }
    }

    public static class NPointerParser extends NTypeParser{
        private static NPointer CHAR_PTR = new NPointer(NPrimitive.inst('C'));

        @Override protected boolean parsePossible(StringStream ss) {
            return (ss.left() >= 2 && ss.peek() == '^') || (ss.peek() == '*');
        }

        @Override protected NType parse(StringStream ss) {
            if(ss.peek() == '*'){
                ss.eat('*');
                return CHAR_PTR;
            }
            else{
                ss.eat('^');
                return new NPointer(NTypeParser.parseFrom(ss));
            }
        }
    }

    public static class NStructParser extends NTypeParser{
        protected char getOpen(){ return '{'; };
        protected char getClose(){ return '}'; };

        @Override protected boolean parsePossible(StringStream ss) {
            return ss.left() >= 2 && ss.peek() == getOpen();
        }

        @Override protected NType parse(StringStream ss) {
            assert parsePossible(ss);
            // {_NSRect=
            //   "origin"{_NSPoint="x"f"y"f}
            //   "size"{_NSSize="width"f"height"f}}
            ss.eat(getOpen());
            String cname = ss.readUntilEither("=" + getClose());
            List<NStruct.NField> fields = new ArrayList<NStruct.NField>();
            if(ss.peek() == '='){
                ss.eat('=');
                while(ss.peek() != getClose()){
                    String fname = "";
                    if(ss.peek() == '"'){
                        ss.eat('"');
                        fname = ss.readUntil('"');
                        ss.eat('"');
                    }
                    NType type = NTypeParser.parseFrom(ss);
                    fields.add(new NStruct.NField(fname, type));
                }
            }
            ss.eat(getClose());
            return getNew(cname, fields);
        }

        protected NType getNew(String cname, List<NStruct.NField> fields){
            return new NStruct(cname, fields);
        }
    }

    // A Union is very much like a Struct.
    public static class NUnionParser extends NStructParser{
        @Override protected char getOpen(){ return '('; };

        @Override protected char getClose(){ return ')'; };

        @Override protected NType getNew(String cname, List<NStruct.NField> fields){
            return new NUnion(cname, Fp.map(NUnion.zeroOffsets, fields));
        }
    }

    public static class NArrayParser extends NTypeParser{
        @Override protected boolean parsePossible(StringStream ss) {
            return ss.peek() == '[';
        }

        @Override protected NType parse(StringStream ss) {
            ss.eat('[');
            int size = Integer.parseInt(ss.readWhileDigits());
            NType type = NTypeParser.parseFrom(ss);
            ss.eat(']');
            return new NArray(size, type);
        }
    }

    public static class NObjectParser extends NTypeParser{
        @Override protected boolean parsePossible(StringStream ss) {
            return ss.peek() == '@';
        }

        @Override protected NType parse(StringStream ss) {
            ss.eat('@');
            return NObject.inst();
        }
    }

    public static class NClassParser extends NTypeParser{
        @Override protected boolean parsePossible(StringStream ss) {
            return ss.peek() == '#';
        }

        @Override protected NType parse(StringStream ss) {
            ss.eat('#');
            return NClass.inst();
        }
    }

    public static class NSelectorParser extends NTypeParser{
        @Override protected boolean parsePossible(StringStream ss) {
            return ss.peek() == ':';
        }

        @Override protected NType parse(StringStream ss) {
            ss.eat(':');
            return NSelector.inst();
        }
    }

    public static class NUnknownParser extends NTypeParser{
        @Override protected boolean parsePossible(StringStream ss) {
            return ss.peek() == '?';
        }

        @Override protected NType parse(StringStream ss) {
            ss.eat('?');
            return NUnknown.inst();
        }
    }

    /**
     * Specifier     Encoding
     * const         r
     * in             n
     * inout         N
     * out             o
     * bycopy         O
     * oneway         V
     */
    public static class NSpecifierParser extends NTypeParser{
        private static Collection<Character> SPECS = Arrays.asList('r', 'n', 'N', 'o', 'O', 'V');
        @Override protected boolean parsePossible(StringStream ss) {
            return SPECS.contains(ss.peek());
        }

        @Override protected NType parse(StringStream ss) {
            assert parsePossible(ss);
            ss.seek(); // XXX Just ignore specs for now and return the affected type.
            return NTypeParser.parseFrom(ss);
        }
    }
}
