/*
 * Copyright (c) 2008, 2009, Oracle and/or its affiliates. All rights reserved.
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

package sun.jkernel;

/**
 * TODO: The JRE and deploy build code (SplitJRE) can be made a bit smarter
 * then cryto hashcode byte arrays can be used directly, eliminating the need
 * for this class altogether. So just leave this alone until it can be removed.
 * TODO: Change "Digits" to "String" for uniformity and more intuitive names.
 * A lightweight class to provide convert between hex digits and
 * <code>byte[]</code>.
 *<p>
 * TODO: Try to get this built without the -source 1.3 -target -1.3 options,
 * which prevent use of java.text.Format, assuming this wouldn't bloat the
 * JK rt.jar. Also, there still might be equivalent code hiding in the JDK
 * already, but preliminary searches havn't found it.
 */

public final class ByteArrayToFromHexDigits {

    private static final char [] chars = new char[]
        {'0','1','2','3','4','5','6','7','8','9', 'A','B','C','D','E','F'};

    private static final boolean debug = false;

    /**
     * Converts the <code>byte[] b</code> into a <code>String</code> of
     * hex digits representing the integer values of all the bytes.
     *
     * @param b byte array to be converted
     * @return String representing <code>b</code> in hexadecimal
     * @throws IllegalArgumentException if <code>b</code> is null or zero length
     */
    public static String bytesToHexString(byte[] b) {
        if (debug ) {
            System.out.print("I: ");
            for(int i=0;i<b.length;i++) {
                System.out.format("%02X",b[i]);
            }
            System.out.println();
        }
        if ((b == null) || (b.length == 0)) {
            throw new IllegalArgumentException("argument null or zero length");
        }
        StringBuffer buff = new StringBuffer(b.length * 2);
        for (int i = 0; i < b.length; i++ ) {
            buff.insert(i*2,chars[(b[i] >> 4) & 0xf]);
            buff.insert(i*2+1,chars[b[i] & 0xf]);
        }
        if (debug ) {
            System.out.println("O: " + buff.toString());
        }
        return buff.toString();
    }

    // Convert one hex character to a 4 bit byte value

    private static byte hexCharToByte(char c) throws IllegalArgumentException {
        if ((c < '0') ||
            ( ((c < 'A') && (c > 'F')) && ((c < 'a') && (c > 'f'))) ) {

            throw new IllegalArgumentException("not a hex digit");
        }

        if (c > '9') {
            if (c > 'F') {
                return (byte) ((c - 'a' + 10) & 0xf);
            } else {
                return (byte) ((c - 'A' + 10) & 0xf);
            }
        } else {
            return (byte) ((c - '0') & 0xf);
        }

    }

    /**
     * Converts the <code>String d</code> assumed to contain a sequence
     * of hexadecimal digit characters into a <code>byte[]</code>.
     *
     * @param d String to be converted
     * @return  byte array representing the hex string
     * @throws IllegalArgumentException if <code>d</code> is odd length,
     *     contains a character outside the ranges of 0-9, a-f, and A-F,
     *     or is zero length or null
     */

    public static byte[] hexStringToBytes(String d) throws IllegalArgumentException {
        if (d == null) {
            throw new IllegalArgumentException(
                "parameter cannot be null");
        }

        if (d.length() == 0) {
            throw new IllegalArgumentException(
                "parameter cannot be zero length");
        }

        if ((d.length() & 1) != 0) {
            throw new IllegalArgumentException(
                "odd length string");
        }

        byte[] b = new byte[d.length() / 2];

        // TODO Might be code in the JK initial bundle to do this better (i.e.
        // method that tests for a hex char?)

        for (int i=0;i<d.length();i+=2) {
            b[i/2] =  (byte) (( (byte) (hexCharToByte(d.charAt(i))) << 4) +
                (byte) hexCharToByte(d.charAt(i+1)));
        }
        return b;
    }
}
