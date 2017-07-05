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

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.*;

public class CharsetMapping {
    public final static char UNMAPPABLE_DECODING = '\uFFFD';
    public final static int  UNMAPPABLE_ENCODING = 0xFFFD;

    public static class Entry {
        public int bs;   //byte sequence reps
        public int cp;   //Unicode codepoint
        public int cp2;  //CC of composite

        public Entry () {}
        public Entry (int bytes, int cp, int cp2) {
            this.bs = bytes;
            this.cp = cp;
            this.cp2 = cp2;
        }
    }

    static Comparator<Entry> comparatorCP =
        new Comparator<Entry>() {
            public int compare(Entry m1, Entry m2) {
                return m1.cp - m2.cp;
            }
            public boolean equals(Object obj) {
                return this == obj;
            }
    };

    public static class Parser {
        static final Pattern basic = Pattern.compile("(?:0x)?(\\p{XDigit}++)\\s++(?:0x)?(\\p{XDigit}++)?\\s*+.*");
        static final int gBS = 1;
        static final int gCP = 2;
        static final int gCP2 = 3;

        BufferedReader reader;
        boolean closed;
        Matcher matcher;
        int gbs, gcp, gcp2;

        public Parser (InputStream in, Pattern p, int gbs, int gcp, int gcp2)
            throws IOException
        {
            this.reader = new BufferedReader(new InputStreamReader(in));
            this.closed = false;
            this.matcher = p.matcher("");
            this.gbs = gbs;
            this.gcp = gcp;
            this.gcp2 = gcp2;
        }

        public Parser (InputStream in, Pattern p) throws IOException {
            this(in, p, gBS, gCP, gCP2);
        }

        public Parser (InputStream in) throws IOException {
            this(in, basic, gBS, gCP, gCP2);
        }

        protected boolean isDirective(String line) {
            return line.startsWith("#");
        }

        protected Entry parse(Matcher matcher, Entry mapping) {
            mapping.bs = Integer.parseInt(matcher.group(gbs), 16);
            mapping.cp = Integer.parseInt(matcher.group(gcp), 16);
            if (gcp2 <= matcher.groupCount() &&
                matcher.group(gcp2) != null)
                mapping.cp2 = Integer.parseInt(matcher.group(gcp2), 16);
            else
                mapping.cp2 = 0;
            return mapping;
        }

        public Entry next() throws Exception {
            return next(new Entry());
        }

        // returns null and closes the input stream if the eof has beenreached.
        public Entry next(Entry mapping) throws Exception {
            if (closed)
                return null;
            String line;
            while ((line = reader.readLine()) != null) {
                if (isDirective(line))
                    continue;
                matcher.reset(line);
                if (!matcher.lookingAt()) {
                    //System.out.println("Missed: " + line);
                    continue;
                }
                return parse(matcher, mapping);
            }
            reader.close();
            closed = true;
            return null;
        }
    }

    // tags of different charset mapping tables
    private final static int MAP_SINGLEBYTE      = 0x1; // 0..256  : c
    private final static int MAP_DOUBLEBYTE1     = 0x2; // min..max: c
    private final static int MAP_DOUBLEBYTE2     = 0x3; // min..max: c [DB2]
    private final static int MAP_SUPPLEMENT      = 0x5; //           db,c
    private final static int MAP_SUPPLEMENT_C2B  = 0x6; //           c,db
    private final static int MAP_COMPOSITE       = 0x7; //           db,base,cc
    private final static int MAP_INDEXC2B        = 0x8; // index table of c->bb

    private static final void writeShort(OutputStream out, int data)
        throws IOException
    {
        out.write((data >>> 8) & 0xFF);
        out.write((data      ) & 0xFF);
    }

    private static final void writeShortArray(OutputStream out,
                                              int type,
                                              int[] array,
                                              int off,
                                              int size)   // exclusive
        throws IOException
    {
        writeShort(out, type);
        writeShort(out, size);
        for (int i = off; i < size; i++) {
            writeShort(out, array[off+i]);
        }
    }

    public static final void writeSIZE(OutputStream out, int data)
        throws IOException
    {
        out.write((data >>> 24) & 0xFF);
        out.write((data >>> 16) & 0xFF);
        out.write((data >>>  8) & 0xFF);
        out.write((data       ) & 0xFF);
    }

    public static void writeINDEXC2B(OutputStream out, int[] indexC2B)
        throws IOException
    {
        writeShort(out, MAP_INDEXC2B);
        writeShort(out, indexC2B.length);
        int off = 0;
        for (int i = 0; i < indexC2B.length; i++) {
            if (indexC2B[i] != 0) {
                writeShort(out, off);
                off += 256;
            } else {
                writeShort(out, -1);
            }
        }
    }

    public static void writeSINGLEBYTE(OutputStream out, int[] sb)
        throws IOException
    {
        writeShortArray(out, MAP_SINGLEBYTE, sb, 0, 256);
    }

    private static void writeDOUBLEBYTE(OutputStream out,
                                        int type,
                                        int[] db,
                                        int b1Min, int b1Max,
                                        int b2Min, int b2Max)
        throws IOException
    {
        writeShort(out, type);
        writeShort(out, b1Min);
        writeShort(out, b1Max);
        writeShort(out, b2Min);
        writeShort(out, b2Max);
        writeShort(out, (b1Max - b1Min + 1) * (b2Max - b2Min + 1));

        for (int b1 = b1Min; b1 <= b1Max; b1++) {
            for (int b2 = b2Min; b2 <= b2Max; b2++) {
                writeShort(out, db[b1 * 256 + b2]);
            }
        }
    }
    public static void writeDOUBLEBYTE1(OutputStream out,
                                        int[] db,
                                        int b1Min, int b1Max,
                                        int b2Min, int b2Max)
        throws IOException
    {
        writeDOUBLEBYTE(out, MAP_DOUBLEBYTE1, db, b1Min, b1Max, b2Min, b2Max);
    }

    public static void writeDOUBLEBYTE2(OutputStream out,
                                        int[] db,
                                        int b1Min, int b1Max,
                                        int b2Min, int b2Max)
        throws IOException
    {
        writeDOUBLEBYTE(out, MAP_DOUBLEBYTE2, db, b1Min, b1Max, b2Min, b2Max);
    }

    // the c2b table is output as well
    public static void writeSUPPLEMENT(OutputStream out, Entry[] supp, int size)
        throws IOException
    {
        writeShort(out, MAP_SUPPLEMENT);
        writeShort(out, size * 2);
        // db at first half, cc at the low half
        for (int i = 0; i < size; i++) {
            writeShort(out, supp[i].bs);
        }
        for (int i = 0; i < size; i++) {
            writeShort(out, supp[i].cp);
        }

        //c2b
        writeShort(out, MAP_SUPPLEMENT_C2B);
        writeShort(out, size*2);
        Arrays.sort(supp, 0, size, comparatorCP);
        for (int i = 0; i < size; i++) {
            writeShort(out, supp[i].cp);
        }
        for (int i = 0; i < size; i++) {
            writeShort(out, supp[i].bs);
        }
    }

    public static void writeCOMPOSITE(OutputStream out, Entry[] comp, int size)
        throws IOException
    {
        writeShort(out, MAP_COMPOSITE);
        writeShort(out, size*3);
        // comp is sorted already
        for (int i = 0; i < size; i++) {
            writeShort(out, (char)comp[i].bs);
            writeShort(out, (char)comp[i].cp);
            writeShort(out, (char)comp[i].cp2);
        }
    }
}
