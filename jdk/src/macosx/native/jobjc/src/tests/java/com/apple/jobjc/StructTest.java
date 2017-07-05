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

import java.io.StringWriter;

import com.apple.jobjc.coreaudio.AudioBuffer;
import com.apple.jobjc.foundation.NSPoint;
import com.apple.jobjc.foundation.NSRect;

public class StructTest extends PooledTestCase {
    public void testSimpleStruct(){
        AudioBuffer b = JObjC.getInstance().CoreAudio().makeAudioBuffer();
        assertEquals(0, b.mNumberChannels());
        assertEquals(0, b.mDataByteSize());
        b.setMNumberChannels(1);
        b.setMDataByteSize(3);
        assertEquals(1, b.mNumberChannels());
        assertEquals(3, b.mDataByteSize());
    }

    public void testNestedStruct(){
        NSRect r = JObjC.getInstance().Foundation().makeNSRect();
        assertTrue(0f == r.size().width());
        r.size().setWidth(3f);
        assertTrue(3f == r.size().width());
        assertTrue(r.size() == r.size());
    }

    public void testSTRET(){
        NSPoint point = JObjC.getInstance().Foundation().NSMakePoint(3, 4);
        assertTrue(point.x() == 3);
        assertTrue(point.y() == 4);

        NSRect rect = JObjC.getInstance().Foundation().NSMakeRect(0, 1, 2, 3);
        assertTrue(rect.origin().x() == 0);
        assertTrue(rect.origin().y() == 1);
        assertTrue(rect.size().width() == 2);
        assertTrue(rect.size().height() == 3);
    }

    //

    private char halfByteToHex(int b){
        return (b >= 0x0 && b < 0xA) ? (char) ('0' + b) : (char) ('A' + (b-0xA));
    }

    private String byteToHexString(Byte b){
        StringWriter sw = new StringWriter();
        sw.append(halfByteToHex(b & 0xF));
        sw.append(halfByteToHex((b & 0xF0) >> 4));
        return sw.toString();
    }

    String print(Struct st){
        StringWriter sw = new StringWriter();
        st.raw.position(0);
        sw.append(st.getClass().getSimpleName() + ":" + st.raw.limit() + " @ " + Long.toHexString(st.raw.bufferPtr) + " : ");
        for(int i = 0; i < st.raw.limit(); i++){
            sw.append(byteToHexString(st.raw.get()) + " ");
            if((i+1) % 4 == 0)
                sw.append(" ");
        }
        System.out.println(sw.toString().trim());
        return sw.toString().trim();
    }

    public static void main(String[] args){
        junit.textui.TestRunner.run(StructTest.class);
    }
}
