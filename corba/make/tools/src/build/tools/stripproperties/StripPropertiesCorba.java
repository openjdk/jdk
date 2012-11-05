/*
 * Copyright (c) 2001, 2012, Oracle and/or its affiliates. All rights reserved.
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

package build.tools.stripproperties;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

/**
 * Reads a properties file from standard input and writes an equivalent
 * properties file without comments to standard output.
 */
public class StripPropertiesCorba {

    private static void error(String msg, Exception e) {
        System.err.println("ERROR: stripproperties: " + msg);
        if ( e != null ) {
            System.err.println("EXCEPTION: " + e.toString());
            e.printStackTrace();
        }
    }

    private static List<String> infiles = new ArrayList<String>();
    private static List<String> outfiles = new ArrayList<String>();

    private static boolean parseOptions(String args[]) {
        boolean ok = true;

        for ( int i = 0; i < args.length ; i++ ) {
            if ( "-clean".equals(args[i]) && i+2 < args.length ) {
                infiles.add(args[++i]);
                outfiles.add(args[++i]);
            } else if ( args[i].charAt(0)=='@') {
                String filename = args[i].substring(1);
                FileInputStream finput = null;
                byte contents[] = null;
                try {
                    finput = new FileInputStream(filename);
                    int byteCount = finput.available();
                    if ( byteCount <= 0 ) {
                        error("The @file is empty", null);
                        ok = false;
                    } else {
                        contents = new byte[byteCount];
                        int bytesRead = finput.read(contents);
                        if ( byteCount != bytesRead ) {
                            error("Cannot read all of @file", null);
                            ok = false;
                        }
                    }
                } catch ( IOException e ) {
                    error("cannot open " + filename, e);
                    ok = false;
                }
                if ( finput != null ) {
                    try {
                        finput.close();
                    } catch ( IOException e ) {
                        ok = false;
                        error("cannot close " + filename, e);
                    }
                }
                if ( ok && contents != null ) {
                    String tokens[] = (new String(contents)).split("\\s+");
                    if ( tokens.length > 0 ) {
                        ok = parseOptions(tokens);
                    }
                }
                if ( !ok ) {
                    break;
                }
            } else {
                infiles.add(args[i]);
                outfiles.add(args[i]);
            }
        }
        return ok;
    }

    private static boolean stripFiles(List<String> infiles, List<String> outfiles) {
        boolean ok = true;
        Iterator<String> inIter  = infiles.iterator();
        Iterator<String> outIter = outfiles.iterator();

        for (; inIter.hasNext(); ) {
            String infile = inIter.next();
            String outfile = outIter.next();

            Properties prop = new Properties();
            InputStream in = null;
            try {
                in = new BufferedInputStream(new FileInputStream(infile));
                prop.load(in);
            } catch ( FileNotFoundException e ) {
                error("Cannot access file " + infile, e);
                ok = false;
            } catch ( IOException e ) {
                error("IO exception processing file " + infile, e);
                ok = false;
            }
            if ( in != null ) {
                try {
                    in.close();
                } catch ( IOException e ) {
                    error("IO exception closing file " + infile, e);
                    ok = false;
                }
            }
            if ( !ok ) {
                break;
            }

            OutputStream out = null;
            try {
                out = new FileOutputStream(outfile);
                storeProperties(prop, out);
                out.flush();
            } catch ( IOException e ) {
                error("IO exception processing file " + outfile, e);
                ok = false;
            }
            if ( out != null ) {
                try {
                    out.close();
                } catch ( IOException e ) {
                    error("IO exception closing file " + outfile, e);
                    ok = false;
                }
            }
            if ( !ok ) {
                break;
            }

        }
        return ok;
    }

    /**
     * Strip the properties filenames supplied, replacing their contents.
     * @param args Names of properties files to process and replace contents
     */
    public static void main(String args[]) {
        boolean ok = parseOptions(args);
        if ( !ok || !stripFiles(infiles, outfiles) ) {
            System.exit(1);
        }
    }

    // --- code below here is adapted from java.util.Properties ---

    private static final String specialSaveChars = "=: \t\r\n\f#!";

    /*
     * Converts unicodes to encoded &#92;uxxxx
     * and writes out any of the characters in specialSaveChars
     * with a preceding slash
     */
    private static String saveConvert(String theString, boolean escapeSpace) {
        int len = theString.length();
        StringBuffer outBuffer = new StringBuffer(len*2);

        for(int x=0; x<len; x++) {
            char aChar = theString.charAt(x);
            switch(aChar) {
                case ' ':
                    if (x == 0 || escapeSpace) {
                        outBuffer.append('\\');
                    }
                    outBuffer.append(' ');
                    break;
                case '\\':
                    outBuffer.append('\\');
                    outBuffer.append('\\');
                    break;
                case '\t':
                    outBuffer.append('\\');
                    outBuffer.append('t');
                    break;
                case '\n':
                    outBuffer.append('\\');
                    outBuffer.append('n');
                    break;
                case '\r':
                    outBuffer.append('\\');
                    outBuffer.append('r');
                    break;
                case '\f':
                    outBuffer.append('\\');
                    outBuffer.append('f');
                    break;
                default:
                    if ((aChar < 0x0020) || (aChar == 0x007e) || (aChar > 0x00ff)) {
                        outBuffer.append('\\');
                        outBuffer.append('u');
                        outBuffer.append(toHex((aChar >> 12) & 0xF));
                        outBuffer.append(toHex((aChar >>  8) & 0xF));
                        outBuffer.append(toHex((aChar >>  4) & 0xF));
                        outBuffer.append(toHex( aChar        & 0xF));
                    } else {
                        if (specialSaveChars.indexOf(aChar) != -1) {
                            outBuffer.append('\\');
                        }
                        outBuffer.append(aChar);
                    }
            }
        }
        return outBuffer.toString();
    }

    /**
     * Writes the content of <code>properties</code> to <code>out</code>.
     * The format is that of Properties.store with the following modifications:
     * <ul>
     * <li>No header or date is written
     * <li>Latin-1 characters are written as single bytes, not escape sequences
     * <li>Line breaks are indicated by a single \n independent of platform
     * <ul>
     */
    private static void storeProperties(Properties properties, OutputStream out)
    throws IOException {
        BufferedWriter awriter;
        awriter = new BufferedWriter(new OutputStreamWriter(out, "8859_1"));
        for (Enumeration<Object> e = properties.keys(); e.hasMoreElements();) {
            String key = (String)e.nextElement();
            String val = (String)properties.get(key);
            key = saveConvert(key, true);

            /* No need to escape embedded and trailing spaces for value, hence
             * pass false to flag.
             */
            val = saveConvert(val, false);
            writeln(awriter, key + "=" + val);
        }
        awriter.flush();
    }

    private static void writeln(BufferedWriter bw, String s) throws IOException {
        bw.write(s);
        bw.write("\n");
    }

    /**
     * Convert a nibble to a hex character
     * @param   nibble  the nibble to convert.
     */
    private static char toHex(int nibble) {
        return hexDigit[(nibble & 0xF)];
    }

    /** A table of hex digits */
    private static final char[] hexDigit = {
        '0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'
    };
}
