/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
import java.util.ArrayList;
import java.util.Scanner;
import java.util.Formatter;
import java.util.regex.*;
import java.nio.charset.*;
import static build.tools.charsetmapping.CharsetMapping.*;

public class GenerateSBCS {

    public static void genSBCS(String args[]) throws Exception {

        Scanner s = new Scanner(new File(args[0], args[2]));
        while (s.hasNextLine()) {
            String line = s.nextLine();
            if (line.startsWith("#") || line.length() == 0)
                continue;
            String[] fields = line.split("\\s+");
            if (fields.length < 5) {
                System.err.println("Misconfiged sbcs line <" + line + ">?");
                continue;
            }
            String clzName = fields[0];
            String csName  = fields[1];
            String hisName = fields[2];
            boolean isASCII = Boolean.valueOf(fields[3]);
            String pkgName  = fields[4];
            System.out.printf("%s,%s,%s,%b,%s%n", clzName, csName, hisName, isASCII, pkgName);

            StringBuilder b2c = new StringBuilder();
            int c2bLen = genB2C(
                new FileInputStream(new File(args[0], clzName+".map")), b2c);

            String b2cNR = null;
            File nrF = new File(args[0], clzName+".nr");
            if (nrF.exists()) {
                b2cNR = genNR(new FileInputStream(nrF));
            }

            String c2bNR = null;
            File c2bF = new File(args[0], clzName+".c2b");
            if (c2bF.exists()) {
                c2bNR = genC2BNR(new FileInputStream(c2bF));
            }

            genSBCSClass(args[0], args[1], "SingleByte-X.java",
                         clzName, csName, hisName, pkgName, isASCII,
                         b2c.toString(), b2cNR, c2bNR, c2bLen);
        }
    }

    private static void toString(char[] sb, int off, int end,
                                 Formatter out, String closure) {
        while (off < end) {
            out.format("        \"");
            for (int j = 0; j < 8; j++) {
                char c = sb[off++];
                switch (c) {
                case '\b':
                    out.format("\\b"); break;
                case '\t':
                    out.format("\\t"); break;
                case '\n':
                    out.format("\\n"); break;
                case '\f':
                    out.format("\\f"); break;
                case '\r':
                    out.format("\\r"); break;
                case '\"':
                    out.format("\\\""); break;
                case '\'':
                    out.format("\\'"); break;
                case '\\':
                    out.format("\\\\"); break;
                default:
                    out.format("\\u%04X", c & 0xffff);
                }
            }
            if (off == end)
               out.format("\" %s      // 0x%02x - 0x%02x%n", closure, off-8, off-1);
            else
               out.format("\" +      // 0x%02x - 0x%02x%n", off-8, off-1);
        }
    }

    static Pattern sbmap = Pattern.compile("0x(\\p{XDigit}++)\\s++U\\+(\\p{XDigit}++)(\\s++#.*)?");
    private static int genB2C(InputStream in, StringBuilder out)
        throws Exception
    {
        char[] sb = new char[0x100];
        int[] indexC2B = new int[0x100];

        for (int i = 0; i < sb.length; i++)
            sb[i] = UNMAPPABLE_DECODING;

        // parse the b2c mapping table
        Parser p = new Parser(in, sbmap);
        Entry  e = null;
        int    off = 0;
        while ((e = p.next()) != null) {
            sb[e.bs] = (char)e.cp;
            if (indexC2B[e.cp>>8] == 0) {
                off += 0x100;
                indexC2B[e.cp>>8] = 1;
            }
        }

        Formatter fm = new Formatter(out);
        fm.format("%n");

        // vm -server shows cc[byte + 128] access is much faster than
        // cc[byte&0xff] so we output the upper segment first
        toString(sb, 0x80, 0x100, fm, "+");
        toString(sb, 0x00, 0x80,  fm, ";");

        fm.close();
        return off;
    }

    // generate non-roundtrip entries from xxx.nr file
    private static String genNR(InputStream in) throws Exception
    {
        StringBuilder sb = new StringBuilder();
        Formatter fm = new Formatter(sb);
        Parser p = new Parser(in, sbmap);
        Entry  e = null;
        fm.format("// remove non-roundtrip entries%n");
        fm.format("        b2cMap = b2cTable.toCharArray();%n");
        while ((e = p.next()) != null) {
            fm.format("        b2cMap[%d] = UNMAPPABLE_DECODING;%n",
                      (e.bs>=0x80)?(e.bs-0x80):(e.bs+0x80));
        }
        fm.close();
        return sb.toString();
    }

    // generate c2b only entries from xxx.c2b file
    private static String genC2BNR(InputStream in) throws Exception
    {
        StringBuilder sb = new StringBuilder();
        Formatter fm = new Formatter(sb);
        Parser p = new Parser(in, sbmap);
        ArrayList<Entry> es = new ArrayList<Entry>();
        Entry  e = null;
        while ((e = p.next()) != null) {
            es.add(e);
        }

        fm.format("// non-roundtrip c2b only entries%n");
        fm.format("        c2bNR = new char[%d];%n", es.size() * 2);
        int i = 0;
        for (Entry entry: es) {
            fm.format("        c2bNR[%d] = 0x%x; c2bNR[%d] = 0x%x;%n",
                      i++, entry.bs, i++, entry.cp);
        }
        fm.close();
        return sb.toString();
    }

    private static void genSBCSClass(String srcDir,
                                     String dstDir,
                                     String template,
                                     String clzName,
                                     String csName,
                                     String hisName,
                                     String pkgName,
                                     boolean isASCII,
                                     String b2c,
                                     String b2cNR,
                                     String c2bNR,
                                     int    c2blen)
        throws Exception
    {
        Scanner s = new Scanner(new File(srcDir, template));
        PrintStream out = new PrintStream(new FileOutputStream(
                              new File(dstDir, clzName + ".java")));

        while (s.hasNextLine()) {
            String line = s.nextLine();
            int i = line.indexOf("$");
            if (i == -1) {
                out.println(line);
                continue;
            }
            if (line.indexOf("$PACKAGE$", i) != -1) {
                line = line.replace("$PACKAGE$", pkgName);
            }
            if (line.indexOf("$NAME_CLZ$", i) != -1) {
                line = line.replace("$NAME_CLZ$", clzName);
            }
            if (line.indexOf("$NAME_CS$", i) != -1) {
                line = line.replace("$NAME_CS$", csName);
            }
            if (line.indexOf("$NAME_ALIASES$", i) != -1) {
                if ("sun.nio.cs".equals(pkgName))
                    line = line.replace("$NAME_ALIASES$",
                                        "StandardCharsets.aliases_" + clzName);
                else
                    line = line.replace("$NAME_ALIASES$",
                                        "ExtendedCharsets.aliasesFor(\"" + csName + "\")");
            }
            if (line.indexOf("$NAME_HIS$", i) != -1) {
                line = line.replace("$NAME_HIS$", hisName);
            }
            if (line.indexOf("$CONTAINS$", i) != -1) {
                if (isASCII)
                    line = "        return ((cs.name().equals(\"US-ASCII\")) || (cs instanceof " + clzName + "));";
                else
                    line = "        return (cs instanceof " + clzName + ");";
            }
            if (line.indexOf("$B2CTABLE$") != -1) {
                line = line.replace("$B2CTABLE$", b2c);
            }
            if (line.indexOf("$C2BLENGTH$") != -1) {
                line = line.replace("$C2BLENGTH$", "0x" + Integer.toString(c2blen, 16));
            }
            if (line.indexOf("$NONROUNDTRIP_B2C$") != -1) {
                if (b2cNR == null)
                    continue;
                line = line.replace("$NONROUNDTRIP_B2C$", b2cNR);
            }

            if (line.indexOf("$NONROUNDTRIP_C2B$") != -1) {
                if (c2bNR == null)
                    continue;
                line = line.replace("$NONROUNDTRIP_C2B$", c2bNR);
            }
            out.println(line);
        }
        out.close();
    }
}
