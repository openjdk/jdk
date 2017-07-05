/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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

public abstract class StringUtil {
    /** Take a string containing underscores, and return a string
    * with the underscore removed, and all characters exception in lower
    * case except the characters after the underscores.
    */
    public static String toMixedCase( String str )
    {
        StringBuffer sbuf = new StringBuffer( str.length() ) ;
        boolean uppercaseNext = false ;
        for (int ctr=0; ctr<str.length(); ctr++) {
            char ch = str.charAt( ctr ) ;

            if (ch == '_') {
                uppercaseNext = true ;
            } else if (uppercaseNext) {
                sbuf.append( Character.toUpperCase( ch ) ) ;
                uppercaseNext = false ;
            } else {
                sbuf.append( Character.toLowerCase( ch ) ) ;
            }
        }

        return sbuf.toString() ;
    }

    public static int countArgs( String str )
    {
        int result = 0 ;
        for( int ctr = 0; ctr<str.length(); ctr++ )
            if (str.charAt(ctr) == '{')
                result++ ;

        return result ;
    }
}
