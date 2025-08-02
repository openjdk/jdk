/*
 * Copyright (c) 1998, 2025, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4131169 8191963
 * @summary Test compareTo and equals methods
 * @library .. /test/lib
 * @build jdk.test.lib.Platform
 * @run main CompareTo
 */

import java.io.File;
import jdk.test.lib.Platform;

public class CompareTo {

    private static void testWin32() throws Exception {
        File f1 = new File("a");
        File f2 = new File("B");
        if (!(f1.compareTo(f2) < 0))
            throw new Exception("compareTo incorrect");

        // U+0131 = ı 'LATIN SMALL LETTER DOTLESS I'
        File smallDotlessI = new File("\u0131");
        // U+0130 = İ 'LATIN CAPITAL LETTER I WITH DOT ABOVE'
        File largeDotfullI = new File("\u0130");
        File latinCapitalI = new File("I");

        boolean shouldBeEqual= smallDotlessI.equals(latinCapitalI);
        if (!shouldBeEqual)
            throw new Exception("Small dotless \"i\" does not equal \"I\"");
        boolean shouldNotBeEqual = largeDotfullI.equals(latinCapitalI);
        if (shouldNotBeEqual)
            throw new Exception("Large dotted \"I\" equals \"I\"");
    }

    private static void testUnix() throws Exception {
        File f1 = new File("a");
        File f2 = new File("B");
        if (!(f1.compareTo(f2) > 0))
            throw new Exception("compareTo incorrect");
    }

    public static void main(String[] args) throws Exception {
        if (Platform.isWindows())
            testWin32();
        else
            testUnix();
    }

}
