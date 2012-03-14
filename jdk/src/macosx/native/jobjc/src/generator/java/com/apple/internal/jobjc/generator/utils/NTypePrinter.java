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

import java.io.StringWriter;

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

/**
 * Print an NType to the BridgeSupport encoding.
 */
public class NTypePrinter{
    private static NTypePrinter INST = new NTypePrinter();
    public static NTypePrinter inst(){ return INST; }

    public String print(NType nt){
        try {
            return Dispatcher.dispatch(getClass(), this, "accept", nt);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    protected String accept(NBitfield nt) {
        return "b" + nt.length;
    }

    protected String accept(NPrimitive nt) {
        return Character.toString(nt.type);
    }

    protected String accept(NPointer nt) {
        if(nt.subject instanceof NPrimitive && ((NPrimitive) nt.subject).type == 'C')
            return "*";
        else
            return "^" + print(nt.subject);
    }

    protected String printStruct(NStruct nt, char open, char close){
        StringWriter sw = new StringWriter();
        sw.append(open);
        sw.append(nt.name);
        if(nt.fields.size() > 0){
            sw.append('=');
            for(NField f : nt.fields){
                if(f.name != null && f.name.length() > 0)
                    sw.append("\"" + f.name + "\"");
                sw.append(print(f.type));
            }
        }
        sw.append(close);
        return sw.toString();
    }

    protected String accept(NStruct nt) {
        return printStruct(nt, '{', '}');
    }

    protected String accept(NUnion nt) {
        return printStruct(nt, '(', ')');
    }

    protected String accept(NArray nt) {
        return "[" + nt.length + print(nt.type) + "]";
    }

    protected String accept(NObject nt) { return "@"; }
    protected String accept(NVoid nt) { return "v"; }
    protected String accept(NClass nt) { return "#"; }
    protected String accept(NSelector nt) { return ":"; }
    protected String accept(NUnknown nt) { return "?"; }
}
