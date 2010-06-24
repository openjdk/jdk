/*
 * Copyright (c) 2001, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.corba.se.idl.toJavaPortable ;

import com.sun.tools.corba.se.idl.toJavaPortable.NameModifier ;

public class NameModifierImpl implements NameModifier {
    private String prefix ;
    private String suffix ;

    public NameModifierImpl( )
    {
        this.prefix = null ;
        this.suffix = null ;
    }

    public NameModifierImpl( String prefix, String suffix )
    {
        this.prefix = prefix ;
        this.suffix = suffix ;
    }

    /** Construct a NameModifier from a pattern of the form xxx%xxx.
    * The pattern must consist of characters chosen from the
    * set [A-Za-z0-9%$_]. In addition, the pattern must contain
    * exactly one % character.  Finally, if % is not the first char in
    * the pattern, the pattern must not start with a number.
    * <p>
    * The semantics of makeName are very simply: just replace the
    * % character with the base in the pattern and return the result.
    */
    public NameModifierImpl( String pattern )
    {
        int first = pattern.indexOf( '%' ) ;
        int last  = pattern.lastIndexOf( '%' ) ;

        if (first != last)
            throw new IllegalArgumentException(
                Util.getMessage( "NameModifier.TooManyPercent" ) ) ;

        if (first == -1)
            throw new IllegalArgumentException(
                Util.getMessage( "NameModifier.NoPercent" ) ) ;

        for (int ctr = 0; ctr<pattern.length(); ctr++) {
            char ch = pattern.charAt( ctr ) ;
            if (invalidChar( ch, ctr==0 )) {
                char[] chars = new char[] { ch } ;
                throw new IllegalArgumentException(
                    Util.getMessage( "NameModifier.InvalidChar",
                        new String( chars )) ) ;
            }
        }

        // at this point, 0 <= first && first < pattern.length()
        prefix = pattern.substring( 0, first ) ;
        suffix = pattern.substring( first+1 ) ;
    }

    /** Return true if ch is invalid as a character in an
    * identifier.  If ch is a number, it is invalid only if
    * isFirst is true.
    */
    private boolean invalidChar( char ch, boolean isFirst )
    {
        if (('A'<=ch) && (ch<='Z'))
            return false ;
        else if (('a'<=ch) && (ch<='z'))
            return false ;
        else if (('0'<=ch) && (ch<='9'))
            return isFirst ;
        else if (ch=='%')
            return false ;
        else if (ch=='$')
            return false ;
        else if (ch=='_')
            return false ;
        else
            return true ;
    }

    public String makeName( String base )
    {
        StringBuffer sb = new StringBuffer() ;

        if (prefix != null)
            sb.append( prefix ) ;

        sb.append( base ) ;

        if (suffix != null)
            sb.append( suffix ) ;

        return sb.toString() ;
    }
}
