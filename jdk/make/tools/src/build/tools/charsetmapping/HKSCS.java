/*
 * Copyright 2010 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package build.tools.charsetmapping;

import java.io.*;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.Formatter;
import java.util.regex.*;
import java.nio.charset.*;
import static build.tools.charsetmapping.Utils.*;

public class HKSCS {

    // HKSCS2001.map has the third column for "UnicodeAlternate", which
    // is for c->b non-roundtrip mapping.
    // For HKSCS2008, those non-roundtrip mappings are in .nr file
    private static Pattern hkscs =
        Pattern.compile("(?:0x)?+(\\p{XDigit}++)\\s++(?:0x|U\\+)?+(\\p{XDigit}++)?\\s*+(?:0x|U\\+)?(\\p{XDigit}++)?\\s*+.*");

    static void genClass(String args[]) throws Exception {

        // hkscs2008
        genClass0(new FileInputStream(new File(args[0], "HKSCS2008.map")),
                  new FileInputStream(new File(args[0], "HKSCS2008.c2b")),
                  new PrintStream(new File(args[1], "HKSCSMapping.java"),
                                  "ISO-8859-1"),
                  "HKSCSMapping",
                  getCopyright(new File(args[3])));


        // xp2001
        genClass0(new FileInputStream(new File(args[0], "HKSCS_XP.map")),
                  null,
                  new PrintStream(new File(args[1], "HKSCS_XPMapping.java"),
                                  "ISO-8859-1"),
                  "HKSCS_XPMapping",
                  getCopyright(new File(args[3])));

        // hkscs2001
        genClass0(new FileInputStream(new File(args[0], "HKSCS2001.map")),
                  new FileInputStream(new File(args[0], "HKSCS2001.c2b")),
                  new PrintStream(new File(args[1], "HKSCS2001Mapping.java"),
                                  "ISO-8859-1"),
                  "HKSCS2001Mapping",
                  getCopyright(new File(args[3])));
    }

    static void genClass0(InputStream isB2C,
                          InputStream isC2B,
                          PrintStream ps,
                          String clzName,
                          String copyright)
        throws Exception
    {
        // ranges of byte1 and byte2, something should come from a "config" file
        int b1Min = 0x87;
        int b1Max = 0xfe;
        int b2Min = 0x40;
        int b2Max = 0xfe;

        try {
            char[] bmp = new char[0x10000];
            char[] supp = new char[0x10000];

            boolean[] b2cBmp = new boolean[0x100];
            boolean[] b2cSupp = new boolean[0x100];
            // pua should be in range of e000-f8ff. Expand
            // it to 0xf93b becase the hkscs2001.c2b has
            // the f920-f93b filled
            //char[] pua = new char[0xF8FF - 0xE000 + 1];
            char[] pua = new char[0xF93b - 0xE000 + 1];
            boolean hasSupp = false;
            boolean hasPua = false;

            Arrays.fill(bmp, UNMAPPABLE_DECODING);
            Arrays.fill(supp, UNMAPPABLE_DECODING);
            Arrays.fill(pua, UNMAPPABLE_DECODING);

            Parser p = new Parser(isB2C, hkscs);
            Entry  e = null;
            while ((e = p.next()) != null) {
                if (e.cp >= 0x10000) {
                    supp[e.bs] = (char)e.cp;
                    b2cSupp[e.bs>>8] = true;
                    hasSupp = true;
                } else {
                    bmp[e.bs] = (char)e.cp;
                    b2cBmp[e.bs>>8] = true;
                }
                if (e.cp2 != 0 && e.cp2 >= 0xe000 && e.cp2 <= 0xf8ff) {
                    hasPua = true;
                    pua[e.cp2 - 0xE000] = (char)e.bs;
                }
            }

            if (isC2B != null) {
                p = new Parser(isC2B, hkscs);
                e = null;
                while ((e = p.next()) != null) {
                    pua[e.cp - 0xE000] = (char)e.bs;
                }
                hasPua = true;
            }

            StringBuilder sb = new StringBuilder();
            Output out = new Output(new Formatter(sb));

            out.format(copyright);
            out.format("%n// -- This file was mechanically generated: Do not edit! -- //%n");
            out.format("package sun.nio.cs.ext;%n%n");
            out.format("class %s {%n%n", clzName);

            /* hardcoded in sun.nio.cs.ext.HKSCS.java
            out.format("    final static int b1Min = 0x%x;%n", b1Min);
            out.format("    final static int b1Max = 0x%x;%n", b1Max);
            out.format("    final static int b2Min = 0x%x;%n", b2Min);
            out.format("    final static int b2Max = 0x%x;%n", b2Max);
            */

            // bmp tables
            out.format("%n    static final String[] b2cBmpStr = new String[] {%n");
            for (int i = 0; i < 0x100; i++) {
                if (b2cBmp[i])
                    out.format(bmp, i, b2Min, b2Max, ",");
                else
                    out.format("        null,%n");  //unmappable segments
            }
            out.format("        };%n");

            // supp tables
            out.format("%n    static final String[] b2cSuppStr =");
            if (hasSupp) {
                out.format(" new String[] {%n");
                for (int i = 0; i < 0x100; i++) {
                    if (b2cSupp[i])
                        out.format(supp, i, b2Min, b2Max, ",");
                    else
                        out.format("        null,%n");  //unmappable segments
                }
                out.format("        };%n");
            } else {
                out.format(" null;%n");
            }

            // private area tables
            out.format("%n    final static String pua =");
            if (hasPua) {
                out.format("%n");
                out.format(pua, 0, pua.length, ";");
            } else {
                out.format(" null;%n");
            }
            out.format("%n");
            out.format("}");

            out.close();

            ps.println(sb.toString());
            ps.close();

        } catch (Exception x) {
            x.printStackTrace();
        }
    }
}
