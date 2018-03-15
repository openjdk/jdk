/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
/*
 * @test
 * @bug 8196869
 * @summary Make sure we deal with internal Key data being cleared properly
 * @run main/othervm -Xms16m -Xmx16m -esa SoftKeys
 * @ignore This test aims to provoke NPEs, but due to the need to constrain
 *         memory usage it fails intermittently with OOME on various systems
 *         with no way to ignore such failures.
 */
import java.util.*;

public class SoftKeys {

    private static final char[] CHARS = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

    public static void main(String[] args) {
        try {
            // With 4 characters in "language", we'll fill up a 16M heap quickly,
            // causing full GCs and SoftReference reclamation. Repeat at least two
            // times to verify no NPEs appear when looking up Locale's whose
            // softly referenced data in sun.util.locale.BaseLocale$Key might have
            // been cleared.
            for (int i = 0; i < 2; i++) {
                for (int j = 0; j < 512*1024; j++) {
                    new Locale(langForInt(j), "", "");
                }
            }
        } catch (OutOfMemoryError e) {
            // Can happen on some system configurations, and while increasing heap
            // size would allow GC to keep up, it also makes it impractically hard
            // to reproduce NPE issues that could arise when references are being
            // cleared.

            // Do a System.gc() to not throw an OOME again in the jtreg wrapper.
            System.gc();
        }
    }

    private static String langForInt(int val) {
        StringBuilder buf = new StringBuilder(4);
        buf.append(CHARS[(val >> 12) & 0xF]);
        buf.append(CHARS[(val >>  8) & 0xF]);
        buf.append(CHARS[(val >>  4) & 0xF]);
        buf.append(CHARS[(val >>  0) & 0xF]);
        return buf.toString();
    }
}

