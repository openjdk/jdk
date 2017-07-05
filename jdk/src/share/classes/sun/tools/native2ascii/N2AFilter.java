/*
 * Copyright (c) 2001, 2008, Oracle and/or its affiliates. All rights reserved.
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


/**
 * This FilterWriter class takes an existing Writer and uses
 * the 'back-tick U' escape notation to escape characters which are
 * encountered within the input character based stream which
 * are outside the 7-bit ASCII range. The native platforms linefeed
 * character is emitted for each line of processed input
 */

package sun.tools.native2ascii;
import java.io.*;
import java.nio.BufferOverflowException;

class N2AFilter extends FilterWriter {

    public N2AFilter(Writer out) { super(out); }

    public void write(char b) throws IOException {
        char[] buf = new char[1];
        buf[0] = b;
        write(buf, 0, 1);
    }

    public void write(char[] buf, int off, int len) throws IOException {

        String lineBreak = System.getProperty("line.separator");

        //System.err.println ("xx Out buffer length is " + buf.length );
        for (int i = 0; i < len; i++) {
            if ((buf[i] > '\u007f')) {
                // write \udddd
                out.write('\\');
                out.write('u');
                String hex =
                    Integer.toHexString(buf[i]);
                StringBuffer hex4 = new StringBuffer(hex);
                hex4.reverse();
                int length = 4 - hex4.length();
                for (int j = 0; j < length; j++) {
                    hex4.append('0');
                }
                for (int j = 0; j < 4; j++) {
                    out.write(hex4.charAt(3 - j));
                }
            } else
                out.write(buf[i]);
        }
    }
}
