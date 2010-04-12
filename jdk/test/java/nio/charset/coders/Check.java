/*
 * Copyright 2010 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/* @test
 * @bug 4712786
 * @summary Check charsets against reference files
 *
 * @build Util
 * @run main Check shift_jis ref.shift_jis
 * @run main/othervm -Dsun.nio.cs.map=Windows-31J/Shift_JIS Check shift_jis ref.windows-31j
 */

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;
import java.util.regex.*;


public class Check {

    private static PrintStream log = System.err;

    private static final int UNICODE_SIZE = (1 << 16);

    private final String csName;
    private final String refName;
    private byte[][] bytes = new byte[UNICODE_SIZE][]; // Indexed by char

    private int errors = 0;

    private Check(String csn, String refn) {
        csName = csn;
        refName = refn;
    }

    private Check load()
        throws IOException
    {
        File fn = new File(System.getProperty("test.src", "."), refName);
        FileChannel fc = new FileInputStream(fn).getChannel();
        ByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
        CharBuffer cb = Charset.forName("US-ASCII").decode(bb);
        Pattern p = Pattern.compile("^(\\p{XDigit}+) +(\\p{XDigit}+)$",
                                    Pattern.MULTILINE);
        Matcher m = p.matcher(cb);
        while (m.find()) {
            char c = (char)Integer.parseInt(m.group(1), 16);
            String v = m.group(2);
            int nb = v.length() >> 1;
            byte[] ba = new byte[nb];
            for (int i = 0; i < nb; i++) {
                ba[i] = (byte)Integer.parseInt(v.substring(i << 1, (i << 1) + 2),
                                               16);
            }
            bytes[c] = ba;
        }
        return this;
    }

    private void error() {
        if (++errors >= 100)
            throw new RuntimeException("100 errors occurred (there might be more)");
    }

    private void mismatch(String s, byte[] expected, byte[] got) {
        log.println("Encoding mismatch on \""
                    + Util.toString(s)
                    + "\": Expected {"
                    + Util.toString(expected)
                    + "}, got {"
                    + Util.toString(got)
                    + "}");
        error();
    }

    private void mismatch(int i, byte[] ba, String expected, String got) {
        log.println("Decoding mismatch on \""
                    + Util.toString((char)i)
                    + "\", input {"
                    + Util.toString(ba)
                    + "}: Expected \""
                    + Util.toString(expected)
                    + "\", got \""
                    + Util.toString(got)
                    + "\"");
        error();
    }

    private void check()
        throws IOException
    {

        // String.getBytes(String csn)
        for (int i = 0; i < UNICODE_SIZE; i++) {
            if (bytes[i] == null)
                continue;
            String s = new String(new char[]{ (char)i });
            byte[] ba = s.getBytes(csName);
            if (Util.cmp(ba, bytes[i]) >= 0)
                mismatch(s, bytes[i], ba);
        }
        log.println("String.getBytes(\"" + csName + "\") okay");

        // String(byte[] ba, String csn)
        for (int i = 0; i < UNICODE_SIZE; i++) {
            if (bytes[i] == null)
                continue;
            String r = new String(new char[]{ (char)i });
            String s = new String(bytes[i], csName);
            if (!r.equals(s))
                mismatch(i, bytes[i], r, s);
        }
        log.println("String(byte[] ba, \"" + csName + "\") okay");

        // To be really thorough we should test OutputStreamWriter,
        // InputStreamReader, and Charset{De,En}Coder here also,
        // but the above will do for now.

        if (errors > 0) {
            throw new RuntimeException(errors + " error(s) occurred");
        }

    }

    // Usage: Check charsetName referenceFileName
    public static void main(String[] args) throws Exception {
        new Check(args[0], args[1]).load().check();
    }

}
