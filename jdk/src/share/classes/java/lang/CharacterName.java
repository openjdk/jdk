/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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

package java.lang;

import java.io.DataInputStream;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.util.Arrays;
import java.util.zip.InflaterInputStream;
import java.security.AccessController;
import java.security.PrivilegedAction;

class CharacterName {

    private static SoftReference<byte[]> refStrPool;
    private static int[][] lookup;

    private static synchronized byte[] initNamePool() {
        byte[] strPool = null;
        if (refStrPool != null && (strPool = refStrPool.get()) != null)
            return strPool;
        DataInputStream dis = null;
        try {
            dis = new DataInputStream(new InflaterInputStream(
                AccessController.doPrivileged(new PrivilegedAction<InputStream>()
                {
                    public InputStream run() {
                        return getClass().getResourceAsStream("uniName.dat");
                    }
                })));

            lookup = new int[(Character.MAX_CODE_POINT + 1) >> 8][];
            int total = dis.readInt();
            int cpEnd = dis.readInt();
            byte ba[] = new byte[cpEnd];
            dis.readFully(ba);

            int nameOff = 0;
            int cpOff = 0;
            int cp = 0;
            do {
                int len = ba[cpOff++] & 0xff;
                if (len == 0) {
                    len = ba[cpOff++] & 0xff;
                    // always big-endian
                    cp = ((ba[cpOff++] & 0xff) << 16) |
                         ((ba[cpOff++] & 0xff) <<  8) |
                         ((ba[cpOff++] & 0xff));
                }  else {
                    cp++;
                }
                int hi = cp >> 8;
                if (lookup[hi] == null) {
                    lookup[hi] = new int[0x100];
                }
                lookup[hi][cp&0xff] = (nameOff << 8) | len;
                nameOff += len;
            } while (cpOff < cpEnd);
            strPool = new byte[total - cpEnd];
            dis.readFully(strPool);
            refStrPool = new SoftReference<>(strPool);
        } catch (Exception x) {
            throw new InternalError(x.getMessage());
        } finally {
            try {
                if (dis != null)
                    dis.close();
            } catch (Exception xx) {}
        }
        return strPool;
    }

    public static String get(int cp) {
        byte[] strPool = null;
        if (refStrPool == null || (strPool = refStrPool.get()) == null)
            strPool = initNamePool();
        int off = 0;
        if (lookup[cp>>8] == null ||
            (off = lookup[cp>>8][cp&0xff]) == 0)
            return null;
        return new String(strPool, 0, off >>> 8, off & 0xff);  // ASCII
    }
}
