/*
 * Copyright (c) 2003, 2009, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.corba.se.logutil;

import java.io.PrintWriter ;
import java.io.Writer ;
import java.io.OutputStream ;
import java.io.BufferedWriter ;
import java.io.OutputStreamWriter ;
import java.util.StringTokenizer ;

public class IndentingPrintWriter extends PrintWriter {
    private int level = 0 ;
    private int indentWidth = 4 ;
    private String indentString = "" ;

    public void printMsg( String msg, Object... data )
    {
        // System.out.println( "printMsg called with msg=" + msg + " data=" + data ) ;
        StringTokenizer st = new StringTokenizer( msg, "@", true ) ;
        StringBuffer result = new StringBuffer() ;
        String token = null ;
        int pos = 0;

        while (st.hasMoreTokens()) {
            token = st.nextToken() ;
            if (token.equals("@")) {
                if (pos < data.length) {
                    result.append( data[pos] );
                    ++pos;
                } else {
                    throw new Error( "List too short for message" ) ;
                }
            } else {
                result.append( token ) ;
            }
        }

        // System.out.println( "Printing result " + result + " to file" ) ;
        print( result ) ;
        println() ;
    }

    public IndentingPrintWriter (Writer out) {
        super( out, true ) ;
        // System.out.println( "Constructing a new IndentingPrintWriter with Writer " + out ) ;
    }

    public IndentingPrintWriter(Writer out, boolean autoFlush) {
        super( out, autoFlush ) ;
        // System.out.println( "Constructing a new IndentingPrintWriter with Writer " + out ) ;
    }

    public IndentingPrintWriter(OutputStream out) {
        super(out, true);
        // System.out.println( "Constructing a new IndentingPrintWriter with OutputStream " + out ) ;
    }

    public IndentingPrintWriter(OutputStream out, boolean autoFlush) {
        super(new BufferedWriter(new OutputStreamWriter(out)), autoFlush);
        // System.out.println( "Constructing a new IndentingPrintWriter with OutputStream " + out ) ;
    }

    public void setIndentWidth( int indentWidth )
    {
        this.indentWidth = indentWidth ;
        updateIndentString() ;
    }

    public void indent()
    {
        level++ ;
        updateIndentString() ;
    }

    public void undent()
    {
        if (level > 0) {
            level-- ;
            updateIndentString() ;
        }
    }

    private void updateIndentString()
    {
        int size = level * indentWidth ;
        StringBuffer sbuf = new StringBuffer( size ) ;
        for (int ctr = 0; ctr<size; ctr++ )
            sbuf.append( " " ) ;
        indentString = sbuf.toString() ;
    }

    // overridden from PrintWriter
    public void println()
    {
        super.println() ;

        print( indentString ) ;
    }
}
