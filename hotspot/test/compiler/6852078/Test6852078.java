/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 *
 */

/**
 * @test
 * @bug 6852078
 * @summary Disable SuperWord optimization for unsafe read/write
 *
 * @run main/othervm Test6852078
 */

import java.util.*;
import java.nio.ByteBuffer;
import com.sun.corba.se.impl.encoding.ByteBufferWithInfo;
import com.sun.jndi.toolkit.corba.CorbaUtils;

public class Test6852078 {

    public Test6852078(String [] args) {

        int capacity = 128;
        ByteBuffer bb = ByteBuffer.allocateDirect(capacity);
        ByteBufferWithInfo bbwi = new ByteBufferWithInfo( CorbaUtils.getOrb(null, -1, new Hashtable()), bb);
        byte[] tmpBuf;
        tmpBuf = new byte[bbwi.buflen];

        for (int i = 0; i < capacity; i++)
            tmpBuf[i] = bbwi.byteBuffer.get(i);
    }

    public static void main(String [] args) {
        for (int i=0; i<2000; i++) {
            Test6852078 t = new Test6852078(args);
        }
    }
}
