/*
 * Copyright (c) 2010, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4821213
 * @summary Unit test for CharsetEncoder.canEncode methods
 */

import java.io.*;
import java.nio.*;
import java.nio.charset.*;


public class CanEncode {

    private static int errors = 0;
    private static PrintStream out = System.err;

    private static void wrong(CharsetEncoder ce, boolean can, String what) {
        out.println(ce.charset().name()
                    + ": Wrong answer for " + what
                    + ": " + !can);
        errors++;
    }

    private static void ck(CharsetEncoder ce, char c, boolean can)
        throws Exception
    {
        if (ce.canEncode(c) != can)
            wrong(ce, can,
                  ("'" + c + "' (0x"
                   + Integer.toHexString(c & 0xffff) + ")"));
    }

    private static void ck(CharsetEncoder ce, String s, boolean can)
        throws Exception
    {
        if (ce.canEncode(CharBuffer.wrap(s.toCharArray())) != can)
            wrong(ce, can, "array \"" + s + "\"");
        if (ce.canEncode(CharBuffer.wrap(s)) != can)
            wrong(ce, can, "buffer  \"" + s + "\"");
    }

    private static void test(String csn) throws Exception {

        Charset cs = Charset.forName(csn);
        CharsetEncoder ce = cs.newEncoder();

        // Basic multilingual plane
        boolean utf = csn.startsWith("UTF-");
        ck(ce, 'x', true);
        ck(ce, '\u00B6', utf);
        ck(ce, "", true);
        ck(ce, "x", true);
        ck(ce, "\u00B6", utf);
        ck(ce, "xyzzy", true);
        ck(ce, "xy\u00B6", utf);

        // Paired surrogates
        ck(ce, "\uD83D\uDE00", utf);
        ck(ce, "XX\uD83D\uDE00", utf);
        ck(ce, "\uD83D\uDE00XX", utf);
        ck(ce, "X\uD83D\uDE00X", utf);

        // Unpaired surrogates should never be encodable
        ck(ce, '\ud800', false);
        ck(ce, '\ud801', false);
        ck(ce, '\udffe', false);
        ck(ce, '\udfff', false);
        ck(ce, "\ud800", false);
        ck(ce, "XX\ud800", false);
        ck(ce, "\ud800XX", false);
        ck(ce, "X\ud800X", false);
        ck(ce, "\ud801", false);
        ck(ce, "XX\ud801", false);
        ck(ce, "\ud801XX", false);
        ck(ce, "X\ud801X", false);
        ck(ce, "\udffe", false);
        ck(ce, "XX\udffe", false);
        ck(ce, "\udffeXX", false);
        ck(ce, "X\udffeX", false);
        ck(ce, "\udfff", false);
        ck(ce, "XX\udfff", false);
        ck(ce, "\udfffXX", false);
        ck(ce, "X\udfffX", false);

        if (errors > 0) {
            throw new RuntimeException(errors + " errors for Charset " + csn);
        }
    }

    public static void main(String[] args) throws Exception {
        test("US-ASCII");
        test("UTF-8");
        test("UTF-16");
        test("UTF-16LE");
        test("UTF-16BE");
        test("UTF-32");
        test("UTF-32LE");
        test("UTF-32BE");
    }

}
