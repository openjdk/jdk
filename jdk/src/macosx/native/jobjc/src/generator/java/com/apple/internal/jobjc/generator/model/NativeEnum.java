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
package com.apple.internal.jobjc.generator.model;

import java.util.Arrays;

import org.w3c.dom.Node;

import com.apple.internal.jobjc.generator.model.types.Type;
import com.apple.internal.jobjc.generator.model.types.JType.JPrimitive;
import com.apple.internal.jobjc.generator.model.types.NType.NPrimitive;
import com.apple.internal.jobjc.generator.utils.Fp;
import com.apple.jobjc.JObjCRuntime;

public class NativeEnum extends ElementWType<Framework> {
    public final String value, value64, le_value, be_value;
    public boolean ignore;
    public String suggestion;
    public NativeEnum(final Node node, final Framework parent) {
        super(node, typeForEnum(getAttr(node, "name"),
                getAttr(node, "value"), getAttr(node, "value64"),
                getAttr(node, "le_value"), getAttr(node, "be_value"),
                getAttr(node, "ignore")), parent);
        this.value = getAttr(node, "value");
        this.value64 = getAttr(node, "value64");
        this.le_value = getAttr(node, "le_value");
        this.be_value = getAttr(node, "be_value");
        String ignoreS = getAttr(node, "ignore");
        this.ignore = ignoreS == null ? false : Boolean.parseBoolean(ignoreS);
        this.suggestion = getAttr(node, "suggestion");
        assert valueToString() != null;
    }

    private static Type typeForEnum(String name, String value32, String value64, String le_value, String be_value, String ignore){
        if("true".equals(ignore)) return Type.getType(null, NPrimitive.inst('i'), null);

        NumTest[] tests = new NumTest[]{new IntTest(), new LongTest(), new FloatTest(), new DoubleTest()};
        for(NumTest t : tests)
            if(t.confirm(value32, value64, le_value, be_value))
                return t.getType();

        throw new NumberFormatException(String.format("Failed to parse type for enum: %1$s = 32: %2$s / 64: %3$s / le: %4$s / be: %5$s\n",
                name, value32, value64, le_value, be_value));
    }

    public String valueToString(){
        if(ignore == true) return "0";
        JPrimitive jprim = (JPrimitive) type.getJType();
        if(le_value == null && be_value == null){
            if(value == null && value64 != null)
                return value64 + jprim.getLiteralSuffix();
            else if(value != null && value64 == null)
                return value + jprim.getLiteralSuffix();
            else
                return String.format("(%1$s.IS64 ? %2$s%4$s : %3$s%4$s)", JObjCRuntime.class.getName(),
                        value64, value, jprim.getLiteralSuffix());
        }
        else if(value == null && value64 == null){
            return String.format("(%1$s.IS_BIG_ENDIAN ? %2$s%4$s : %3$s%4$s)",
                    JObjCRuntime.class.getName(), be_value, le_value, jprim.getLiteralSuffix());
        }

        throw new RuntimeException("Unable to produce a value for enum " + name);
    }

    // Used to find the best type to use for the enum.

    static abstract class NumTest{
        public boolean confirm(String... values){
            return Fp.all(new Fp.Map1<String,Boolean>(){
                public Boolean apply(String a) {
                    try{ return a == null || confirm(a); }
                    catch(Exception x){ return false; }
                }},
                Arrays.asList(values));
        }

        public abstract boolean confirm(String v);
        public abstract Type getType();
    }

    static class IntTest extends NumTest{
        @Override public boolean confirm(String v) {
            Integer.parseInt(v);
            return true;
        }

        @Override public Type getType() { return Type.getType(null, NPrimitive.inst('i'), null); }
    }

    static class LongTest extends NumTest{
        @Override public boolean confirm(String v) {
            Long.parseLong(v);
            return true;
        }

        @Override public Type getType() { return Type.getType(null, NPrimitive.inst('l'), null); }
    }

    static class FloatTest extends NumTest{
        @Override public boolean confirm(String v) {
            return Float.parseFloat(v) == Double.parseDouble(v);
        }

        @Override public Type getType() { return Type.getType(null, NPrimitive.inst('f'), null); }
    }

    static class DoubleTest extends NumTest{
        @Override public boolean confirm(String v) {
            double d = Double.parseDouble(v);
            return !Double.isInfinite(d) && !Double.isNaN(d);
        }

        @Override public Type getType() { return Type.getType(null, NPrimitive.inst('d'), null); }
    }
}
