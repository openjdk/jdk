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
package com.sun.codemodel.internal.util;

import java.io.FilterWriter;
import java.io.IOException;
import java.io.Writer;

/**
 * {@link Writer} that escapes non US-ASCII characters into
 * Java Unicode escape \\uXXXX.
 *
 * This process is necessary if the method names or field names
 * contain non US-ASCII characters.
 *
 * @author
 *      Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public class UnicodeEscapeWriter extends FilterWriter {

    public UnicodeEscapeWriter( Writer next ) {
        super(next);
    }

    public final void write(int ch) throws IOException {
        if(!requireEscaping(ch))  out.write(ch);
        else {
            // need to escape
            out.write("\\u");
            String s = Integer.toHexString(ch);
            for( int i=s.length(); i<4; i++ )
                out.write('0');
            out.write(s);
        }
    }

    /**
     * Can be overrided. Return true if the character
     * needs to be escaped.
     */
    protected boolean requireEscaping(int ch) {
        if(ch>=128)     return true;

        // control characters
        if( ch<0x20 && " \t\r\n".indexOf(ch)==-1 )  return true;

        return false;
    }

    public final void write(char[] buf, int off, int len) throws IOException {
        for( int i=0; i<len; i++ )
            write(buf[off+i]);
    }

    public final void write(char[] buf) throws IOException {
        write(buf,0,buf.length);
    }

    public final void write(String buf, int off, int len) throws IOException {
        write( buf.toCharArray(), off, len );
    }

    public final void write(String buf) throws IOException {
        write( buf.toCharArray(), 0, buf.length() );
    }

}
