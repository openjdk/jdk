/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Alibaba Group Holding Limited. All Rights Reserved.
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

package sun.nio.cs;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.UnmappableCharacterException;

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;

public final class StreamEncoderUTF8 extends StreamEncoder {
    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();

    StreamEncoderUTF8(OutputStream out, Object lock) {
        super(out, lock, UTF_8.INSTANCE);
    }

    public void write(String str, int off, int len) throws IOException {
        /* Check the len before creating a char buffer */
        if (len < 0)
            throw new IndexOutOfBoundsException();
        if (haveLeftoverChar) {
            super.write(str, off, len);
            return;
        }

        int utf8Size = (int) JLA.computeSizeUTF8(str, off, len);
        if (utf8Size >= maxBufferCapacity) {
            byte[] utf8 = new byte[utf8Size];
            JLA.encodeUTF8(str, off, len, utf8, 0);
                /* If the request length exceeds the max size of the output buffer,
                   flush the buffer and then write the data directly.  In this
                   way buffered streams will cascade harmlessly. */
            implFlushBuffer();
            out.write(utf8, 0, utf8.length);
            return;
        }


        int cap = bb.capacity();
        int newCap = bb.position() + utf8Size;
        if (newCap >= maxBufferCapacity) {
            implFlushBuffer();
        }

        if (newCap > cap) {
            implFlushBuffer();
            bb = ByteBuffer.allocate(newCap);
        }

        byte[] cb = bb.array();
        int lim = bb.limit();
        int pos = bb.position();

        pos = JLA.encodeUTF8(str, off, len, cb, pos);
        bb.position(pos);
    }
}
