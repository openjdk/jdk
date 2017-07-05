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
package com.apple.jobjc;

import com.apple.internal.jobjc.generator.model.types.NType;
import com.apple.internal.jobjc.generator.utils.NTypeMerger;
import com.apple.internal.jobjc.generator.utils.NTypeParser;

public class NativeTypeTest extends PooledTestCase{

    private NType doParse(String type){
        NType nt = NTypeParser.parseFrom(type);
        String printed = nt.toString();
        System.out.println("Original: " + type);
        System.out.println("Printed.: " + printed);
        assertEquals(type, printed);
        return nt;
    }

    // {_NSRect=
    //   "origin"{_NSPoint="x"f"y"f}
    //   "size"{_NSSize="width"f"height"f}}
    public void testStruct(){
        doParse("{_NSRect=\"origin\"{_NSPoint=\"x\"f\"y\"f}\"size\"{_NSSize=\"width\"f\"height\"f}}");
    }

    // {IOBluetoothL2CAPChannelEvent=
    //   "eventType"i
    //   "u"(?=
    //     "data"{IOBluetoothL2CAPChannelDataBlock=
    //       "dataPtr"^v
    //       "dataSize"I}
    //     "writeRefCon"^v
    //     "padding"[32C])
    //   "status"i}
    public void testUnion(){
        doParse("{IOBluetoothL2CAPChannelEvent=\"eventType\"i\"u\"(?=\"data\"{IOBluetoothL2CAPChannelDataBlock=\"dataPtr\"^v\"dataSize\"I}\"writeRefCon\"^v\"padding\"[32C])\"status\"i}");
    }

    public void testUnknown(){
        doParse("{_CFSocketContext=\"version\"i\"info\"^v\"retain\"^?\"release\"^?\"copyDescription\"^?}");
    }

    public void testEmptyStruct(){
        doParse("{_CFSocketSignature=\"protocolFamily\"i\"socketType\"i\"protocol\"i\"address\"^{__CFData}}");
    }

    public void testCharPtr(){
        doParse("^*");
    }

    public void doEquals(final String s){
        assertEquals(doParse(s), doParse(s));
    }

    public void testEquals(){
        doEquals("{_NSRect=\"origin\"{_NSPoint=\"x\"f\"y\"f}\"size\"{_NSSize=\"width\"f\"height\"f}}");
        doEquals("{IOBluetoothL2CAPChannelEvent=\"eventType\"i\"u\"(?=\"data\"{IOBluetoothL2CAPChannelDataBlock=\"dataPtr\"^v\"dataSize\"I}\"writeRefCon\"^v\"padding\"[32C])\"status\"i}");
        doEquals("{_CFSocketContext=\"version\"i\"info\"^v\"retain\"^?\"release\"^?\"copyDescription\"^?}");
        doEquals("{_CFSocketSignature=\"protocolFamily\"i\"socketType\"i\"protocol\"i\"address\"^{__CFData}}");
    }

    public void testMerge(){
        NType a = doParse("{_NSRect={_NSPoint=\"x\"f\"y\"f}\"size\"{_NSSize=ff}}");
        NType b = doParse("{_NSRect=\"origin\"{_NSPoint=ff}{_NSSize=\"width\"f\"height\"f}}");
        NType c = NTypeMerger.inst().merge(a, b);
        NType expected = doParse("{_NSRect=\"origin\"{_NSPoint=\"x\"f\"y\"f}\"size\"{_NSSize=\"width\"f\"height\"f}}");
        System.out.println("Merge results:");
        System.out.println("\ta: " + a.toString());
        System.out.println("\tb: " + b.toString());
        System.out.println("\tc: " + c.toString());
        System.out.println("\tx: " + expected.toString());
        assertEquals(expected, c);
    }
}
