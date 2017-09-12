/*
 * Copyright (c) 2010, 2017, Oracle and/or its affiliates. All rights reserved.
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
 */

/*
 * Regression test class run by SJISMappingPropTest.java to verify
 * that sun.nio.cs.map property is correctly interpreted in
 * multibyte Japanese locales
 */

public class SJISPropTest {
    public static void main(String[] args) throws Exception {
        boolean sjisIsMS932 = false;

        if (args[0].equals("MS932"))
                sjisIsMS932 = true;
        byte[] testBytes = { (byte)0x81, (byte)0x60 };

        // JIS X based Shift_JIS and Windows-31J differ
        // in a number of mappings including this one.

        String expectedMS932 = new String("\uFF5E");
        String expectedSJIS = new String("\u301C");

        // Alias "shift_jis" will map to Windows-31J
        // if the sun.nio.cs.map system property is defined as
        // "Windows-31J/Shift_JIS". This should work in all
        // multibyte (especially Japanese) locales.

        String s = new String(testBytes, "shift_jis");

        if (sjisIsMS932 && !s.equals(expectedMS932))
            throw new Exception("not MS932");
        else if (!sjisIsMS932 && !s.equals(expectedSJIS))
            throw new Exception("not SJIS");
    }
}
