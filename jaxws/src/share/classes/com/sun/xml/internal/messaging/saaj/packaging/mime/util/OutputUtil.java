/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
/*
 * @(#)OutputUtil.java  1.6 02/03/27
 */



package com.sun.xml.internal.messaging.saaj.packaging.mime.util;

import com.sun.xml.internal.messaging.saaj.util.ByteOutputStream;

import java.io.OutputStream;
import java.io.IOException;

/**
 * This class is to support writing out Strings as a sequence of bytes
 * terminated by a CRLF sequence. The String must contain only US-ASCII
 * characters.<p>
 *
 * The expected use is to write out RFC822 style headers to an output
 * stream. <p>
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class OutputUtil {
    private static byte[] newline = {'\r','\n'};

    public static void writeln(String s,OutputStream out) throws IOException {
        writeAsAscii(s,out);
        writeln(out);
    }

    /**
     * Writes a string as ASCII string.
     */
    public static void writeAsAscii(String s,OutputStream out) throws IOException {
        int len = s.length();
        for( int i=0; i<len; i++ )
            out.write((byte)s.charAt(i));
    }

    public static void writeln(OutputStream out) throws IOException {
        out.write(newline);
    }
}
