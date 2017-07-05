/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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
 * A collection of useful global utilities commonly used.
 */
package sun.tools.pack.verify;

import java.io.*;
import java.util.*;

/*
 * @author ksrini
 */

class Globals {

    private static int errors = 0;
    private static PrintWriter _pw = null;
    private static String _logFileName = null;
    private static final String DEFAULT_LOG_FILE = "verifier.log";
    private static boolean _verbose = true;
    private static boolean _ignoreJarDirectories = false;
    private static boolean _checkJarClassOrdering = true;
    private static boolean _bitWiseClassCompare = false;
    // Ignore Deprecated, SourceFile and Synthetic
    private static boolean _ignoreCompileAttributes = false;
    // Ignore Debug Attributes LocalVariableTable, LocalVariableType,LineNumberTable
    private static boolean _ignoreDebugAttributes = false;
    private static boolean _ignoreUnknownAttributes = false;
    private static boolean _validateClass = true;
    private static Globals _instance = null;

    static Globals getInstance() {
        if (_instance == null) {
            _instance = new Globals();
            _verbose = (System.getProperty("sun.tools.pack.verify.verbose") == null)
                    ? false : true;
            _ignoreJarDirectories = (System.getProperty("ignoreJarDirectories") == null)
                    ? false : true;
        }
        return _instance;
    }

    static boolean ignoreCompileAttributes() {
        return _ignoreCompileAttributes;
    }

    static boolean ignoreDebugAttributes() {
        return _ignoreDebugAttributes;
    }

    static boolean ignoreUnknownAttributes() {
        return _ignoreUnknownAttributes;
    }

    static boolean ignoreJarDirectories() {
        return _ignoreJarDirectories;
    }

    static boolean validateClass() {
        return _validateClass;
    }

    static void setCheckJarClassOrdering(boolean flag) {
        _checkJarClassOrdering = flag;
    }

    static boolean checkJarClassOrdering() {
        return _checkJarClassOrdering;
    }

    static boolean bitWiseClassCompare() {
        return _bitWiseClassCompare;
    }

    static boolean setBitWiseClassCompare(boolean flag) {
        return _bitWiseClassCompare = flag;
    }

    public static boolean setIgnoreCompileAttributes(boolean flag) {
        return _ignoreCompileAttributes = flag;
    }

    static boolean setIgnoreDebugAttributes(boolean flag) {
        return _ignoreDebugAttributes = flag;
    }

    static boolean setIgnoreUnknownAttributes(boolean flag) {
        return _ignoreUnknownAttributes = flag;
    }

    static boolean setValidateClass(boolean flag) {
        return _validateClass = flag;
    }

    static int getErrors() {
        return errors;
    }

    static void trace(String s) {
        if (_verbose) {
            println(s);
        }
    }

    static void print(String s) {
        _pw.print(s);
    }

    static void println(String s) {
        _pw.println(s);
    }

    static void log(String s) {
        errors++;
        _pw.println("ERROR:" + s);
    }

    static void lognoln(String s) {
        errors++;
        _pw.print(s);
    }

    private static PrintWriter openFile(String fileName) {
        //Lets create the directory if it does not exist.
        File f = new File(fileName);
        File baseDir = f.getParentFile();
        if (baseDir != null && baseDir.exists() == false) {
            baseDir.mkdirs();
        }
        try {
            return new PrintWriter(new FileWriter(f), true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void closeFile() {
        _pw.flush();
        _pw.close();
    }

    static void printPropsToLog() {
        println("Log started " + new Date(System.currentTimeMillis()));
        print(System.getProperty("java.vm.version"));
        println("\t" + System.getProperty("java.vm.name"));

        println("System properties");
        println("\tjava.home=" + System.getProperty("java.home"));
        println("\tjava.class.version=" + System.getProperty("java.class.version"));
        println("\tjava.class.path=" + System.getProperty("java.class.path"));
        println("\tos.name=" + System.getProperty("os.name"));
        println("\tos.arch=" + System.getProperty("os.arch"));
        println("\tos.version=" + System.getProperty("os.version"));
        println("\tuser.name=" + System.getProperty("user.name"));
        println("\tuser.home=" + System.getProperty("user.home"));
        println("\tuser.dir=" + System.getProperty("user.dir"));
        println("\tLocale.getDefault=" + Locale.getDefault());
        println("System properties end");
    }

    static void openLog(String s) {
        _logFileName = (s != null) ? s : "." + File.separator + DEFAULT_LOG_FILE;
        _logFileName = (new File(_logFileName).isDirectory())
                ? _logFileName + File.separator + DEFAULT_LOG_FILE : _logFileName;
        _pw = openFile(_logFileName);
        printPropsToLog();
    }

    static void closeLog() {
        closeFile();
    }

    static String getLogFileName() {
        return _logFileName;
    }

    static void diffCharData(String s1, String s2) {
        boolean diff = false;
        char[] c1 = s1.toCharArray();
        char[] c2 = s2.toCharArray();
        if (c1.length != c2.length) {
            diff = true;
            Globals.log("Length differs: " + (c1.length - c2.length));
        }
        // Take the smaller of the two arrays to prevent Array...Exception
        int minlen = (c1.length < c2.length) ? c1.length : c2.length;
        for (int i = 0; i < c1.length; i++) {
            if (c1[i] != c2[i]) {
                diff = true;
                Globals.lognoln("\t idx[" + i + "] 0x" + Integer.toHexString(c1[i]) + "<>" + "0x" + Integer.toHexString(c2[i]));
                Globals.log(" -> " + c1[i] + "<>" + c2[i]);
            }
        }
    }

    static void diffByteData(String s1, String s2) {
        boolean diff = false;
        byte[] b1 = s1.getBytes();
        byte[] b2 = s2.getBytes();

        if (b1.length != b2.length) {
            diff = true;
            //(+) b1 is greater, (-) b2 is greater
            Globals.log("Length differs diff: " + (b1.length - b2.length));
        }
        // Take the smaller of the two array to prevent Array...Exception
        int minlen = (b1.length < b2.length) ? b1.length : b2.length;
        for (int i = 0; i < b1.length; i++) {
            if (b1[i] != b2[i]) {
                diff = true;
                Globals.log("\t" + "idx[" + i + "] 0x" + Integer.toHexString(b1[i]) + "<>" + "0x" + Integer.toHexString(b2[i]));
            }
        }
    }

    static void dumpToHex(String s) {
        try {
            dumpToHex(s.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException uce) {
            throw new RuntimeException(uce);
        }
    }

    static void dumpToHex(byte[] buffer) {
        int linecount = 0;
        byte[] b = new byte[16];
        for (int i = 0; i < buffer.length; i += 16) {
            if (buffer.length - i > 16) {
                System.arraycopy(buffer, i, b, 0, 16);
                print16Bytes(b, linecount);
                linecount += 16;
            } else {
                System.arraycopy(buffer, i, b, 0, buffer.length - i);
                for (int n = buffer.length - (i + 1); n < 16; n++) {
                    b[n] = 0;
                }
                print16Bytes(b, linecount);
                linecount += 16;
            }
        }
        Globals.log("-----------------------------------------------------------------");
    }

    static void print16Bytes(byte[] buffer, int linecount) {
        final int MAX = 4;
        Globals.lognoln(paddedHexString(linecount, 4) + " ");

        for (int i = 0; i < buffer.length; i += 2) {
            int iOut = pack2Bytes2Int(buffer[i], buffer[i + 1]);
            Globals.lognoln(paddedHexString(iOut, 4) + " ");
        }

        Globals.lognoln("| ");

        StringBuilder sb = new StringBuilder(new String(buffer));

        for (int i = 0; i < buffer.length; i++) {
            if (Character.isISOControl(sb.charAt(i))) {
                sb.setCharAt(i, '.');
            }
        }
        Globals.log(sb.toString());
    }

    static int pack2Bytes2Int(byte b1, byte b2) {
        int out = 0x0;
        out += b1;
        out <<= 8;
        out &= 0x0000ffff;
        out |= 0x000000ff & b2;
        return out;
    }

    static String paddedHexString(int n, int max) {
        char[] c = Integer.toHexString(n).toCharArray();
        char[] out = new char[max];

        for (int i = 0; i < max; i++) {
            out[i] = '0';
        }
        int offset = (max - c.length < 0) ? 0 : max - c.length;
        for (int i = 0; i < c.length; i++) {
            out[offset + i] = c[i];
        }
        return new String(out);
    }
}
